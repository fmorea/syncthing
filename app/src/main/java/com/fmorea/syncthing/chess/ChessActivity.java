package com.fmorea.syncthing.chess;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fmorea.syncthing.R;
import com.fmorea.syncthing.activities.SyncthingActivity;
import com.fmorea.syncthing.databinding.ActivityChessBinding;
import com.fmorea.syncthing.model.Device;
import com.fmorea.syncthing.util.ConfigRouter;
import com.fmorea.syncthing.util.FileUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class ChessActivity extends SyncthingActivity implements ChessGameController.GameUI, SensorEventListener {

    private static final String TAG = "ChessActivity";
    private ActivityChessBinding binding;
    private final ChessModel model = new ChessModel();
    private LinkThingChessTransport transport;
    private ChessGameController controller;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gravitySensor;
    private int drawMode = 0; // 0: OFF, 1: PEN, 2: ERASER

    private final Handler joystickHandler = new Handler(Looper.getMainLooper());
    private Runnable joystickRunnable;
    private static final int INITIAL_DELAY = 400;
    private static final int REPEAT_INTERVAL = 100;

    // Timer logic
    private long moveStartTime = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimerUI();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate starting");
        try {
            binding = ActivityChessBinding.inflate(getLayoutInflater());
            if (binding == null) throw new RuntimeException("Binding is null after inflation");
            setContentView(binding.getRoot());
        } catch (Exception e) {
            Log.e(TAG, "Binding/Layout inflation failed", e);
            Toast.makeText(this, "Errore caricamento interfaccia scacchi", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (getSupportActionBar() != null) getSupportActionBar().hide();

        File initialFile = resolveIntentFile();
        Log.d(TAG, "Initial file: " + (initialFile != null ? initialFile.getName() : "null"));
        
        transport = new LinkThingChessTransport(this, initialFile);
        controller = new ChessGameController(model, transport, this);
        
        if (binding.chessBoardView != null) {
            binding.chessBoardView.setChessDelegate(controller);
        }
        model.setChessDelegate(controller);

        setupUI();
        makeNavControlsDraggable();
        setupSensors();
        
        transport.startServer();
        controller.notifyUI();

        if (initialFile != null) {
            Toast.makeText(this, "Caricato: " + initialFile.getName(), Toast.LENGTH_SHORT).show();
        }
        
        timerHandler.post(timerRunnable);
        Log.d(TAG, "onCreate finished");
    }

    @Override
    public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
        super.onServiceConnected(name, service);
        new Thread(() -> {
            try {
                // Wait for config to load if needed
                int retries = 10;
                while (retries-- > 0 && (getApi() == null || !getApi().isConfigLoaded())) {
                    Thread.sleep(500);
                }
                
                if (getApi() != null && getApi().isConfigLoaded()) {
                    String localId = getApi().getLocalDevice().deviceID;
                    runOnUiThread(() -> {
                        controller.setLocalDeviceId(localId);
                        binding.playerName.setText(getDeviceDisplayName(localId) + " (Tu)");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "onServiceConnected task failed", e);
            }
        }).start();
    }

    private String getDeviceDisplayName(String deviceId) {
        if (getApi() == null) return deviceId.substring(0, 8);
        ConfigRouter config = new ConfigRouter(this);
        List<Device> devices = config.getDevices(getApi(), true);
        for (Device d : devices) {
            if (d.deviceID.equals(deviceId)) return d.getDisplayName();
        }
        return deviceId.substring(0, 8);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        File newFile = resolveIntentFile();
        if (newFile != null) {
            transport.setStateFile(newFile);
            Toast.makeText(this, "Aperto: " + newFile.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private File resolveIntentFile() {
        if (getIntent() == null || getIntent().getData() == null) return null;
        Uri uri = getIntent().getData();
        Log.d(TAG, "Resolving intent file from URI: " + uri.toString());
        try {
            if ("file".equals(uri.getScheme())) {
                return new File(uri.getPath());
            } else if ("content".equals(uri.getScheme())) {
                // Try using our FileUtils which handles some content URIs
                String path = FileUtils.getAbsolutePathFromSAFUri(this, uri);
                if (path != null) return new File(path);
                
                // Fallback for FileProvider if SAF resolution fails
                // FileProvider URIs for this app usually point to the LinkThing folder
                if (uri.getAuthority().contains(".provider")) {
                    // Try to extract filename and look in LinkThing dir
                    String fileName = uri.getLastPathSegment();
                    if (fileName != null) {
                        File linkThingDir = new File(android.os.Environment.getExternalStorageDirectory(), com.fmorea.syncthing.service.Constants.LINKTHING_DIR_NAME);
                        File candidate = new File(linkThingDir, fileName);
                        if (candidate.exists()) return candidate;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve intent file", e);
        }
        return null;
    }

    private void setupSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (gravitySensor == null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    private void setupUI() {
        binding.switch2.setOnCheckedChangeListener((v, c) -> { 
            if (c != model.isBlackPointOfView()) {
                model.setBlackPointOfView(c);
                binding.chessBoardView.setBoardOrientation(c);
            }
        });
        binding.switch3.setOnCheckedChangeListener((v, c) -> { model.setAutoRotate(c); binding.chessBoardView.invalidate(); });
        
        binding.switchLoopback.setVisibility(View.GONE);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.button.setOnClickListener(v -> controller.resetGame());
        binding.btnRecenter.setOnClickListener(v -> binding.chessBoardView.recenter());

        // Enhanced Joystick Setup
        setupJoystickButton(binding.btnLeft, -1, 0);
        setupJoystickButton(binding.btnUp, 0, 1);
        setupJoystickButton(binding.btnDown, 0, -1);
        setupJoystickButton(binding.btnRight, 1, 0);
        
        binding.btnSelect.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            binding.chessBoardView.selectCursor();
        });

        // Bottom Menu handling
        binding.bottomAppBar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_zoom_in) {
                binding.chessBoardView.zoomIn();
                return true;
            } else if (id == R.id.action_zoom_out) {
                binding.chessBoardView.zoomOut();
                return true;
            } else if (id == R.id.action_draw_tool) {
                drawMode = (drawMode + 1) % 3;
                if (drawMode == 0) {
                    binding.chessBoardView.setPenMode(false);
                    binding.chessBoardView.setEraserMode(false);
                    Toast.makeText(this, "Disegno: OFF", Toast.LENGTH_SHORT).show();
                    item.setIcon(android.R.drawable.ic_menu_edit);
                } else if (drawMode == 1) {
                    binding.chessBoardView.setPenMode(true);
                    Toast.makeText(this, "Modalità PENNA", Toast.LENGTH_SHORT).show();
                    item.setIcon(android.R.drawable.ic_menu_edit);
                } else {
                    binding.chessBoardView.setEraserMode(true);
                    Toast.makeText(this, "Modalità GOMMA", Toast.LENGTH_SHORT).show();
                    item.setIcon(android.R.drawable.ic_menu_delete);
                }
                return true;
            } else if (id == R.id.action_undo) {
                controller.undo();
                return true;
            } else if (id == R.id.action_redo) {
                controller.redo();
                return true;
            }
            return false;
        });
    }

    private void updateTimerUI() {
        if (moveStartTime == 0) return;
        long elapsed = (System.currentTimeMillis() - moveStartTime) / 1000;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60, elapsed % 60);
        
        boolean whiteTurn = controller.isWhiteTurn();
        boolean localIsWhite = !model.isBlackPointOfView(); 
        
        if (whiteTurn == localIsWhite) {
            binding.timerPlayer.setText(timeStr);
            binding.timerOpponent.setText("00:00");
        } else {
            binding.timerOpponent.setText(timeStr);
            binding.timerPlayer.setText("00:00");
        }
    }

    private void setupJoystickButton(View btn, final int dx, final int dy) {
        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start();
                    binding.chessBoardView.moveCursor(dx, dy);
                    
                    joystickRunnable = new Runnable() {
                        @Override
                        public void run() {
                            binding.chessBoardView.moveCursor(dx, dy);
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            joystickHandler.postDelayed(this, REPEAT_INTERVAL);
                        }
                    };
                    joystickHandler.postDelayed(joystickRunnable, INITIAL_DELAY);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                    joystickHandler.removeCallbacks(joystickRunnable);
                    return true;
            }
            return false;
        });
    }

    private void makeNavControlsDraggable() {
        binding.navDragHandle.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = binding.navControls.getX() - event.getRawX();
                        dY = binding.navControls.getY() - event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        binding.navControls.setX(event.getRawX() + dX);
                        binding.navControls.setY(event.getRawY() + dY);
                        break;
                    default:
                        return false;
                }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        } else if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        if (transport != null) transport.disconnect();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY || event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];

            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            float screenX, screenY;

            switch (rotation) {
                case Surface.ROTATION_90:
                    screenX = -y;
                    screenY = x;
                    break;
                case Surface.ROTATION_180:
                    screenX = -x;
                    screenY = -y;
                    break;
                case Surface.ROTATION_270:
                    screenX = y;
                    screenY = -x;
                    break;
                case Surface.ROTATION_0:
                default:
                    screenX = x;
                    screenY = y;
                    break;
            }

            float multiplier = 3.2f;
            binding.chessBoardView.setGravityTilt(-screenX * multiplier, screenY * multiplier);

            if (model.isAutoRotate()) {
                if (screenY < -4.5f && !model.isBlackPointOfView()) {
                    model.setBlackPointOfView(true);
                    binding.chessBoardView.setBoardOrientation(true);
                } else if (screenY > 4.5f && model.isBlackPointOfView()) {
                    model.setBlackPointOfView(false);
                    binding.chessBoardView.setBoardOrientation(false);
                }
                binding.chessBoardView.invalidate();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void refreshBoard() {
        binding.chessBoardView.invalidate();
    }

    @Override
    public void updateStatus(int mode, boolean isChecked, boolean whitesTurn, Movement lastMove, int lastMoveCount) {
        String turn = whitesTurn ? getString(R.string.white_turn) : getString(R.string.black_turn);
        if (isChecked) turn += getString(R.string.check_label);
        binding.textView3.setText(turn);

        // Highlight active player based on POV (simplified assumption: Me is bottom, Opponent is top)
        boolean isMyTurn = (whitesTurn && !model.isBlackPointOfView()) || (!whitesTurn && model.isBlackPointOfView());
        
        if (isMyTurn) {
            binding.playerInfo.setBackgroundColor(ContextCompat.getColor(this, R.color.chess_green_dark));
            binding.opponentInfo.setBackgroundColor(Color.TRANSPARENT);
        } else {
            binding.opponentInfo.setBackgroundColor(ContextCompat.getColor(this, R.color.chess_green_dark));
            binding.playerInfo.setBackgroundColor(Color.TRANSPARENT);
        }
        
        if (lastMove != null) {
            binding.textView2.setText(String.format(Locale.getDefault(), "Move %d: %s", lastMoveCount, lastMove.toString()));
            moveStartTime = System.currentTimeMillis(); // Reset timer on move
        } else {
            binding.textView2.setText(getString(R.string.new_game));
            moveStartTime = 0;
        }
    }

    @Override
    public void onConnectionStateChanged(boolean connected) {
    }

    @Override
    public void updateNetworkInfo(String role, String status) {
        binding.textViewNetworkRole.setText(role);
        binding.textViewNetworkStatus.setText(status);
    }

    @Override
    public void onMessage(String msg) {
        // No chat anymore, but we can show system messages via Toast
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void updatePeerInfo(String lastSenderId) {
        if (lastSenderId != null && !lastSenderId.isEmpty() && getApi() != null && getApi().isConfigLoaded()) {
            String localId = getApi().getLocalDevice().deviceID;
            if (!lastSenderId.equals(localId)) {
                binding.opponentName.setText(getDeviceDisplayName(lastSenderId));
            }
        }
    }

    @Override
    public void showPromotionDialog(int fromCol, int fromRow, int toCol, int toRow, boolean isWhite) {
        String[] items = {"Queen", "Rook", "Bishop", "Knight"};
        new AlertDialog.Builder(this)
                .setTitle("Select Promotion")
                .setItems(items, (dialog, which) -> {
                    String suffix = isWhite ? "B" : "N";
                    String promo;
                    switch (which) {
                        case 1: promo = "tor" + suffix; break;
                        case 2: promo = "alf" + suffix; break;
                        case 3: promo = "cav" + suffix; break;
                        default: promo = "don" + suffix; break;
                    }
                    controller.movePiece(fromCol, fromRow, toCol, toRow, promo);
                })
                .setCancelable(false)
                .show();
    }
}
