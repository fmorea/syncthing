package com.fmorea.syncthing.chess;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.*;

/**
 * Pure Transport Layer: Manages TCP Sockets and basic messaging.
 * Supports multiple listeners (Observer pattern).
 */
public class NetworkHandler {
    public enum State { IDLE, CONNECTING, CONNECTED }
    
    private final int port;
    protected final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Socket socket;
    private PrintWriter out;
    protected final List<NetworkListener> listeners = new CopyOnWriteArrayList<>();
    private ServerSocket serverSocket;
    protected volatile State state = State.IDLE;

    public interface NetworkListener {
        void onMessage(String text);
        void onConnected();
        void onDisconnected();
    }

    public NetworkHandler(int port) {
        this.port = port;
    }

    public State getState() {
        return state;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public void addListener(NetworkListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(NetworkListener listener) {
        listeners.remove(listener);
    }

    public void startServer() {
        if (state != State.IDLE) return;
        state = State.CONNECTING;
        executor.execute(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
                serverSocket = new ServerSocket(port);
                socket = serverSocket.accept();
                setupStreams();
                state = State.CONNECTED;
                notifyConnected();
                listen();
            } catch (IOException e) {
                state = State.IDLE;
                notifyDisconnected();
            }
        });
    }

    public void connect(String ip) {
        if (state != State.IDLE) return;
        state = State.CONNECTING;
        executor.execute(() -> {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 5000);
                setupStreams();
                state = State.CONNECTED;
                notifyConnected();
                listen();
            } catch (IOException e) {
                state = State.IDLE;
                notifyDisconnected();
            }
        });
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    private void listen() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                final String msg = line;
                if (!msg.equals("Heartbeat")) notifyMessage(msg);
            }
        } catch (IOException ignored) {}
        disconnect();
    }

    protected void notifyConnected() { mainHandler.post(() -> { for (NetworkListener l : listeners) l.onConnected(); }); }
    protected void notifyDisconnected() { mainHandler.post(() -> { for (NetworkListener l : listeners) l.onDisconnected(); }); }
    protected void notifyMessage(String msg) { mainHandler.post(() -> { for (NetworkListener l : listeners) l.onMessage(msg); }); }

    public void send(String text) {
        executor.execute(() -> { if (out != null) out.println(text); });
    }

    public String getMyAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLinkLocalAddress() && !addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {}
        return "127.0.0.1";
    }

    public void disconnect() {
        state = State.IDLE;
        executor.execute(() -> {
            try {
                if (socket != null) socket.close();
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {}
        });
    }
}
