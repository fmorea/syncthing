package com.fmorea.syncthing.chess;

import android.os.Handler;
import android.os.Looper;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkDiscovery {
    private static final String PREFIX = "CHESS_ID:";
    private final int port;
    private final long myId;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isBroadcasting = false;
    private volatile boolean isListening = false;

    public interface DiscoveryCallback {
        void onPeerFound(String ip, long peerId);
    }

    public NetworkDiscovery(int port) {
        this.port = port;
        this.myId = (long) (Math.random() * Long.MAX_VALUE);
    }

    public long getMyId() { return myId; }

    public void startBroadcast() {
        if (isBroadcasting) return;
        isBroadcasting = true;
        executor.execute(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                String msg = PREFIX + myId;
                byte[] data = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, 
                        InetAddress.getByName("255.255.255.255"), port);
                while (isBroadcasting) {
                    socket.send(packet);
                    Thread.sleep(2000);
                }
            } catch (Exception ignored) {}
        });
    }

    public void startListening(DiscoveryCallback callback) {
        if (isListening) return;
        isListening = true;
        executor.execute(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));
                socket.setSoTimeout(3000);
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                while (isListening) {
                    try {
                        socket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength());
                        if (msg.startsWith(PREFIX)) {
                            long peerId = Long.parseLong(msg.substring(PREFIX.length()));
                            if (peerId != myId) {
                                final String ip = packet.getAddress().getHostAddress();
                                mainHandler.post(() -> callback.onPeerFound(ip, peerId));
                            }
                        }
                    } catch (SocketTimeoutException ignored) {}
                }
            } catch (Exception ignored) {}
        });
    }

    public void stopBroadcast() { isBroadcasting = false; }
    public void stopListening() { isListening = false; }
    public void stopAll() { stopBroadcast(); stopListening(); }
}
