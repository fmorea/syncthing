package com.fmorea.syncthing.chess;

import java.util.ArrayList;

public interface ChessDelegate {
    Piece pieceAt(int col, int row);
    Boolean movePiece(int fromCol, int fromRow, int toCol, int toRow);
    Boolean movePiece(int fromCol, int fromRow, int toCol, int toRow, String promotionPiece);
    Boolean isPromotionMove(int fromCol, int fromRow, int toCol, int toRow);
    Boolean blackPointOfView();
    Boolean autoRotate();
    void setOrientation(boolean orientation);
    ArrayList<Movement> getLegalMoves();
    
    // Graphical and status info
    boolean isWhiteTurn();
    int getMaterialCount();
    String getNetworkStatus();
    boolean isConnected();
    String getStatusMessage(); // Added for human-friendly status on board
}
