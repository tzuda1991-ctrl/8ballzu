package com.poollines.android;

public class Ball {
    public static final int CUE = 0, YELLOW = 1, BLUE = 2, RED = 3, PURPLE = 4,
            ORANGE = 5, GREEN = 6, MAROON = 7, BLACK = 8, UNKNOWN = 9;

    public final int id;
    public final int color;
    public float x, y, vx, vy;

    public Ball(int id, int color, float x, float y) {
        this.id = id;
        this.color = color;
        this.x = x;
        this.y = y;
    }

    public Ball copy() {
        Ball b = new Ball(id, color, x, y);
        b.vx = vx;
        b.vy = vy;
        return b;
    }
}
