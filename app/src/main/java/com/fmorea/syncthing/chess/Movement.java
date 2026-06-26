package com.fmorea.syncthing.chess;

import java.util.Objects;

public class Movement {
    private int x;
    private int y;

    private int x0;
    private int y0;

    public Movement(int y0, int x0, int y, int x){
        this.y = y;
        this.x0 = x0;
        this.x = x;
        this.y0 = y0;
    }

    public int getX0() {
        return x0;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getY0() {
        return y0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Movement movement = (Movement) o;
        return x == movement.x && y == movement.y && x0 == movement.x0 && y0 == movement.y0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, x0, y0);
    }

    // Overriding toString() method of String class
    @Override
    public String toString() {
        return "["+x0+","+y0+"] --> ["+x+","+y+"]\n";
    }
}
