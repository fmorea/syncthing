package com.fmorea.syncthing.chess;

import java.util.ArrayList;

/**
 * ChessGameController coordinates the interaction between the Model, the Network, and the UI.
 * Modified to use global board state synchronization via Syncthing.
 */
public class ChessGameController implements NetworkHandler.NetworkListener, ChessDelegate {
    private final ChessModel model;
    private final NetworkHandler transport;
    private final GameUI ui;
    private boolean isNetworkSync = false;
    private String localDeviceId = "";

    public interface GameUI {
        void refreshBoard();
        void updateStatus(int material, boolean inCheck, boolean whiteTurn, Movement lastMove, int legalMovesCount);
        void onConnectionStateChanged(boolean connected);
        void updateNetworkInfo(String role, String status);
        void onMessage(String msg);
        void showPromotionDialog(int fC, int fR, int tC, int tR, boolean isWhite);
        void updatePeerInfo(String lastSenderId);
    }

    public ChessGameController(ChessModel model, NetworkHandler transport, GameUI ui) {
        this.model = model;
        this.transport = transport;
        this.ui = ui;
        this.transport.addListener(this);
    }

    public void setLocalDeviceId(String id) {
        this.localDeviceId = id;
    }

    public void syncState() {
        if (!isNetworkSync) {
            model.getGameLogic().setLastSenderId(localDeviceId);
            transport.send(ChessProtocol.formatBoard(model.getGameLogic().serializeBoard()));
        }
    }

    public void resetGame() {
        model.reset();
        syncState();
        notifyUI();
    }

    public void undo() {
        model.getGameLogic().undo();
        syncState();
        notifyUI();
    }

    public void redo() {
        model.getGameLogic().redo();
        syncState();
        notifyUI();
    }

    public void notifyUI() {
        ui.refreshBoard();
        ui.updateStatus(
            model.getGameLogic().objectiveFunction(),
            model.getGameLogic().isInCheck(),
            model.getGameLogic().toccaAlBianco(),
            model.getGameLogic().getMov(),
            model.getGameLogic().getLegalMoves().size()
        );
        ui.updatePeerInfo(model.getGameLogic().getLastSenderId());
    }

    public GameLogic getGameLogic() {
        return model.getGameLogic();
    }

    // --- NetworkHandler.NetworkListener ---

    @Override
    public void onMessage(String raw) {
        ChessProtocol.MessageType type = ChessProtocol.getType(raw);
        if (type == ChessProtocol.MessageType.BOARD) {
            isNetworkSync = true;
            model.getGameLogic().deserializeBoard(ChessProtocol.parseBoard(raw));
            notifyUI();
            isNetworkSync = false;
        } else if (type == ChessProtocol.MessageType.CHAT) {
            ui.onMessage(ChessProtocol.parseChat(raw));
        }
    }

    @Override
    public void onConnected() {
        ui.onConnectionStateChanged(true);
    }

    @Override
    public void onDisconnected() {
        ui.onConnectionStateChanged(false);
    }

    // --- ChessDelegate ---

    @Override
    public Piece pieceAt(int col, int row) {
        return model.pieceAt(col, row);
    }

    @Override
    public Boolean movePiece(int fC, int fR, int tC, int tR) {
        return movePiece(fC, fR, tC, tR, null);
    }

    @Override
    public Boolean movePiece(int fC, int fR, int tC, int tR, String promotionPiece) {
        if (!isNetworkSync && promotionPiece == null && isPromotionMove(fC, fR, tC, tR)) {
            ui.showPromotionDialog(fC, fR, tC, tR, model.getGameLogic().toccaAlBianco());
            return false;
        }

        boolean moved = model.movePiece(fC, fR, tC, tR, promotionPiece);
        if (moved) {
            syncState();
            notifyUI();
        }
        return moved;
    }

    @Override
    public Boolean isPromotionMove(int fromCol, int fromRow, int toCol, int toRow) {
        return model.isPromotionMove(fromCol, fromRow, toCol, toRow);
    }

    @Override public Boolean blackPointOfView() { return model.isBlackPointOfView(); }
    @Override public Boolean autoRotate() { return model.isAutoRotate(); }
    @Override public void setOrientation(boolean o) { model.setBlackPointOfView(o); }
    @Override public ArrayList<Movement> getLegalMoves() { return model.getGameLogic().getLegalMoves(); }
    
    @Override public boolean isWhiteTurn() { return model.getGameLogic().toccaAlBianco(); }
    @Override public int getMaterialCount() { return model.getGameLogic().objectiveFunction(); }
    @Override public String getNetworkStatus() { return transport.getState().toString(); }
    @Override public boolean isConnected() { return transport.isConnected(); }
    @Override public String getStatusMessage() { return ""; }
}
