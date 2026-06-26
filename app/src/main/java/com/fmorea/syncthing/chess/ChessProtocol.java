package com.fmorea.syncthing.chess;

import java.util.Locale;

/**
 * ChessProtocol handles the serialization and deserialization of game actions.
 * Following Unix philosophy: it does one thing - translating between game state and wire format.
 */
public class ChessProtocol {
    
    public enum MessageType {
        MOVE, BOARD, RESET, UNDO, REDO, CHAT, HEARTBEAT, UNKNOWN
    }

    public static String formatMove(int fromCol, int fromRow, int toCol, int toRow, String promo) {
        if (promo == null || promo.isEmpty()) {
            return String.format(Locale.ROOT, "move:%d,%d,%d,%d", fromCol, fromRow, toCol, toRow);
        } else {
            return String.format(Locale.ROOT, "move:%d,%d,%d,%d,%s", fromCol, fromRow, toCol, toRow, promo);
        }
    }

    public static String formatBoard(String boardData) {
        return "board:" + boardData;
    }

    public static String formatChat(String message) {
        return "chat:" + message;
    }

    public static String formatReset() {
        return "reset";
    }

    public static String formatUndo() {
        return "undo";
    }

    public static String formatRedo() {
        return "redo";
    }

    public static MessageType getType(String raw) {
        if (raw == null) return MessageType.UNKNOWN;
        if (raw.startsWith("rnb")) return MessageType.BOARD; // Detect raw FEN
        if (raw.equals("Heartbeat")) return MessageType.HEARTBEAT;
        if (raw.startsWith("move:")) return MessageType.MOVE;
        if (raw.startsWith("board:")) return MessageType.BOARD;
        if (raw.startsWith("chat:")) return MessageType.CHAT;
        if (raw.equals("reset")) return MessageType.RESET;
        if (raw.equals("undo")) return MessageType.UNDO;
        if (raw.equals("redo")) return MessageType.REDO;
        return MessageType.UNKNOWN;
    }

    public static Object[] parseMove(String raw) {
        try {
            String[] parts = raw.substring(5).split(",");
            if (parts.length == 4) {
                return new Object[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    null
                };
            } else if (parts.length == 5) {
                return new Object[]{
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    parts[4]
                };
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String parseBoard(String raw) {
        if (raw.startsWith("board:")) return raw.substring(6);
        return raw; // Return raw FEN
    }

    public static String parseChat(String raw) {
        return raw.substring(5);
    }
}
