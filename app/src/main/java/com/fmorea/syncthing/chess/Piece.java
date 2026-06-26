package com.fmorea.syncthing.chess;

import java.util.Objects;

public class Piece {
    private final int col;
    private final int row;
    private final Player player;
    private final Rank rank;
    private final int resID;

    public Piece(int col, int row, Player player, Rank rank, int resID) {
        this.col = col;
        this.row = row;
        this.player = player;
        this.rank = rank;
        this.resID = resID;
    }

    public int getCol() { return col; }
    public int getRow() { return row; }
    public Player getPlayer() { return player; }
    public Rank getRank() { return rank; }
    public int getResID() { return resID; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Piece that = (Piece) o;
        return col == that.col && row == that.row && resID == that.resID && player == that.player && rank == that.rank;
    }

    @Override
    public int hashCode() {
        return Objects.hash(col, row, player, rank, resID);
    }
}
