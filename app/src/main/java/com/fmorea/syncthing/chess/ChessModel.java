package com.fmorea.syncthing.chess;

import com.fmorea.syncthing.R;

/**
 * ChessModel maintains the game state and handles view-to-logic mapping.
 * Following Unix philosophy: it represents the data and basic operations.
 */
public class ChessModel {
    private final GameLogic gameLogic = new GameLogic();
    private boolean blackPointOfView = false;
    private boolean autoRotate = false;
    private ChessDelegate chessDelegate;

    public ChessModel() {
        reset();
    }

    public boolean movePiece(int fromCol, int fromRow, int toCol, int toRow) {
        return movePiece(fromCol, fromRow, toCol, toRow, null);
    }

    public boolean movePiece(int fromCol, int fromRow, int toCol, int toRow, String promotionPiece) {
        // Map View coordinates to Logic coordinates (Y, X)
        boolean hasMoved = gameLogic.move(fromRow, fromCol, toRow, toCol, promotionPiece);
        if (hasMoved && chessDelegate != null && chessDelegate.autoRotate()) {
            chessDelegate.setOrientation(!chessDelegate.blackPointOfView());
        }
        return hasMoved;
    }

    public boolean isPromotionMove(int fromCol, int fromRow, int toCol, int toRow) {
        return gameLogic.isPromotion(fromRow, fromCol, toRow, toCol);
    }

    public void reset() {
        gameLogic.createStandardChessboard();
    }

    public Piece pieceAt(int col, int row) {
        if (gameLogic.getPezzo(row, col) == null) return null;
        char color = gameLogic.getColorePezzo(row, col);
        char type = gameLogic.getTipoPezzo(row, col);

        Player p = (color == 'B') ? Player.WHITE : Player.BLACK;
        int resId = 0;
        Rank rank = Rank.PAWN;

        switch (type) {
            case 'p': rank = Rank.PAWN; resId = (p == Player.WHITE) ? R.drawable.pawn_white : R.drawable.pawn_black; break;
            case 'a': rank = Rank.BISHOP; resId = (p == Player.WHITE) ? R.drawable.bishop_white : R.drawable.bishop_black; break;
            case 'c': rank = Rank.KNIGHT; resId = (p == Player.WHITE) ? R.drawable.knight_white : R.drawable.knight_black; break;
            case 'r': rank = Rank.KING; resId = (p == Player.WHITE) ? R.drawable.king_white : R.drawable.king_black; break;
            case 'd': rank = Rank.QUEEN; resId = (p == Player.WHITE) ? R.drawable.queen_white : R.drawable.queen_black; break;
            case 't': rank = Rank.ROOK; resId = (p == Player.WHITE) ? R.drawable.rook_white : R.drawable.rook_black; break;
        }
        return new Piece(col, row, p, rank, resId);
    }

    public GameLogic getGameLogic() { return gameLogic; }
    public boolean isBlackPointOfView() { return blackPointOfView; }
    public void setBlackPointOfView(boolean b) { this.blackPointOfView = b; }
    public boolean isAutoRotate() { return autoRotate; }
    public void setAutoRotate(boolean a) { this.autoRotate = a; }
    public void setChessDelegate(ChessDelegate d) { this.chessDelegate = d; }
}
