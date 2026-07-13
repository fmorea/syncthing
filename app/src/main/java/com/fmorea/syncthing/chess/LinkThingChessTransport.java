package com.fmorea.syncthing.chess;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;

import com.fmorea.syncthing.service.Constants;

import java.io.File;
import java.nio.charset.StandardCharsets;

import kotlin.io.FilesKt;

/**
 * LinkThing-based transport for Chess.
 * Uses a Syncthing-synced file to maintain global board state.
 * Optimized with background scanning and non-blocking I/O.
 */
public class LinkThingChessTransport extends NetworkHandler {
    private static final String TAG = "ChessTransport";
    private final File rootDir;
    private File stateFile;
    private FileObserver observer;
    private String lastContent = "";
    private volatile boolean isRunning = true;

    public LinkThingChessTransport(Context context) {
        this(context, null);
    }

    public LinkThingChessTransport(Context context, File customFile) {
        super(0);
        this.rootDir = new File(context.getFilesDir(), Constants.LINKTHING_DIR_NAME);
        this.stateFile = (customFile != null) ? customFile : new File(rootDir, "global_game.chess");
        
        Log.d(TAG, "Initialized with state file: " + stateFile.getAbsolutePath());

        if (!rootDir.exists()) rootDir.mkdirs();
        
        setupObserver();
        startPeriodicScan();
    }

    public void triggerLoad() {
        executor.execute(this::loadState);
    }

    private void startPeriodicScan() {
        executor.execute(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(20000); // 20s check as backup
                    if (isRunning) loadState();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    private void setupObserver() {
        if (observer != null) observer.stopWatching();
        
        // LinkThing stabilization: watch for CLOSE_WRITE, MOVED_TO and CREATE to handle all sync cases
        observer = new FileObserver(rootDir.getAbsolutePath(), FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO | FileObserver.CREATE) {
            @Override
            public void onEvent(int event, String path) {
                if (path != null && path.equals(stateFile.getName())) {
                    Log.d(TAG, "FileObserver event " + event + " for " + path);
                    executor.execute(() -> loadState());
                }
            }
        };
        observer.startWatching();
    }

    public void setStateFile(File file) {
        this.stateFile = file;
        setupObserver();
        executor.execute(this::loadState);
    }

    private synchronized void loadState() {
        if (!stateFile.exists()) {
            Log.w(TAG, "loadState: stateFile does not exist: " + stateFile.getAbsolutePath());
            return;
        }
        try {
            String content = FilesKt.readText(stateFile, StandardCharsets.UTF_8);
            Log.d(TAG, "loadState: Read " + content.length() + " chars from " + stateFile.getName());
            if (content.equals(lastContent)) return;
            
            lastContent = content;
            notifyMessage(content);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read chess state from " + stateFile.getAbsolutePath(), e);
        }
    }

    @Override
    public void send(String text) {
        if (text.equals(lastContent)) return;
        executor.execute(() -> {
            try {
                lastContent = text;
                FilesKt.writeText(stateFile, text, StandardCharsets.UTF_8);
                Log.d(TAG, "Sent chess state update");
            } catch (Exception e) {
                Log.e(TAG, "Failed to write chess state", e);
            }
        });
    }

    @Override
    public State getState() { return State.CONNECTED; }
    @Override
    public boolean isConnected() { return true; }
    @Override
    public void startServer() { notifyConnected(); }
    @Override
    public void connect(String ip) { notifyConnected(); }

    @Override
    public void disconnect() {
        isRunning = false;
        if (observer != null) observer.stopWatching();
    }
}
