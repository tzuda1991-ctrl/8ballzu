package com.poollines.android;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class Physics {
    public static class Out {
        public int firstHit = -1;
        public boolean scratch = false;
        public final List<Integer> potted = new ArrayList<>();
    }

    private static final float DT = 1f / 120f;
    private static final float FRICTION = 0.992f;
    private static final float WALL = 0.88f;
    private static final float REST = 0.96f;

    public static Out run(List<Ball> balls, Ball cue, Rect table, float angleDeg,
                          float power, float r) {
        List<Ball> sim = new ArrayList<>();
        for (Ball b : balls) if (b.id != 0) sim.add(b.copy());
        Ball c = cue.copy();
        float sp = 950f * power;
        c.vx = (float) Math.cos(Math.toRadians(angleDeg)) * sp;
        c.vy = (float) Math.sin(Math.toRadians(angleDeg)) * sp;

        List<float[]> pockets = Solver.pockets(table, r);
        Out o = new Out();
        boolean cueLive = true;

        for (int step = 0; step < 900; step++) {
            if (cueLive) move(c, table, r);
            for (Ball b : sim) move(b, table, r);

            if (cueLive) {
                for (Ball b : sim) {
                    if (overlap(c, b, r)) {
                        if (o.firstHit < 0) o.firstHit = b.id;
                        resolve(c, b, r);
                    }
                }
            }
            for (int i = 0; i < sim.size(); i++) {
                for (int j = i + 1; j < sim.size(); j++) {
                    if (overlap(sim.get(i), sim.get(j), r)) resolve(sim.get(i), sim.get(j), r);
                }
            }

            if (cueLive) {
                for (float[] p : pockets) {
                    if (near(c, p, r * 1.35f)) {
                        cueLive = false;
                        o.scratch = true;
                        break;
                    }
                }
            }
            for (int i = sim.size() - 1; i >= 0; i--) {
                Ball b = sim.get(i);
                boolean dropped = false;
                for (float[] p : pockets) {
                    if (near(b, p, r * 1.35f)) { dropped = true; break; }
                }
                if (dropped) {
                    o.potted.add(b.id);
                    sim.remove(i);
                }
            }

            boolean moving = cueLive && speed(c) > 4f;
            if (!moving) for (Ball b : sim) if (speed(b) > 4f) { moving = true; break; }
            if (!moving) break;
        }
        return o;
    }

    private static float speed(Ball b) {
        return (float) Math.hypot(b.vx, b.vy);
    }

    private static void move(Ball b, Rect t, float r) {
        b.x += b.vx * DT;
        b.y += b.vy * DT;
        b.vx *= FRICTION;
        b.vy *= FRICTION;
        if (b.x < t.left + r) { b.x = t.left + r; b.vx = Math.abs(b.vx) * WALL; }
        if (b.x > t.right - r) { b.x = t.right - r; b.vx = -Math.abs(b.vx) * WALL; }
        if (b.y < t.top + r) { b.y = t.top + r; b.vy = Math.abs(b.vy) * WALL; }
        if (b.y > t.bottom - r) { b.y = t.bottom - r; b.vy = -Math.abs(b.vy) * WALL; }
    }

    private static boolean overlap(Ball a, Ball b, float r) {
        float dx = b.x - a.x, dy = b.y - a.y;
        return dx * dx + dy * dy < (2 * r) * (2 * r);
    }

    private static void resolve(Ball a, Ball b, float r) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float d = (float) Math.hypot(dx, dy);
        if (d < 0.001f) d = 0.001f;
        float nx = dx / d, ny = dy / d;

        // Positional separation to prevent multi-frame overlap lock
        float pen = (2f * r) - d;
        if (pen > 0) {
            a.x -= nx * pen * 0.5f;
            a.y -= ny * pen * 0.5f;
            b.x += nx * pen * 0.5f;
            b.y += ny * pen * 0.5f;
        }

        float p = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
        if (p <= 0) return;
        float imp = p * REST;
        a.vx -= imp * nx; a.vy -= imp * ny;
        b.vx += imp * nx; b.vy += imp * ny;
    }

    private static boolean near(Ball b, float[] p, float d) {
        return Math.hypot(b.x - p[0], b.y - p[1]) < d;
    }

    private Physics() {}
}
