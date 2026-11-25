package com.poollines.android;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class Solver {
    public static class Shot {
        public float angleDeg, power, score;
        public float gx, gy, tx, ty, px, py;
        public String why = "";
        public boolean valid;
    }

    public static List<float[]> pockets(Rect t, float r) {
        float in = r * 1.15f;
        List<float[]> p = new ArrayList<>();
        p.add(new float[]{t.left + in, t.top + in});
        p.add(new float[]{(t.left + t.right) / 2f, t.top + in * 0.45f});
        p.add(new float[]{t.right - in, t.top + in});
        p.add(new float[]{t.left + in, t.bottom - in});
        p.add(new float[]{(t.left + t.right) / 2f, t.bottom - in * 0.45f});
        p.add(new float[]{t.right - in, t.bottom - in});
        return p;
    }

    public static Shot best(List<Ball> balls, Ball cue, Rect table, float r) {
        Shot top = null;
        List<float[]> pockets = pockets(table, r);

        for (Ball target : balls) {
            if (target.id == 0 || target.color == Ball.BLACK) continue;
            for (float[] p : pockets) {
                float pdx = p[0] - target.x, pdy = p[1] - target.y;
                float pd = (float) Math.hypot(pdx, pdy);
                if (pd < r) continue;
                pdx /= pd; pdy /= pd;

                float gx = target.x - pdx * 2 * r;
                float gy = target.y - pdy * 2 * r;
                if (gx < table.left + r || gx > table.right - r || gy < table.top + r || gy > table.bottom - r)
                    continue;

                float cdx = gx - cue.x, cdy = gy - cue.y;
                float cd = (float) Math.hypot(cdx, cdy);
                if (cd < r * 1.1f) continue;

                float dot = (cdx * pdx + cdy * pdy) / cd;
                float cut = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                if (cut > 76) continue;

                if (!clear(balls, cue, target, cue.x, cue.y, gx, gy, r)) continue;
                if (!clear(balls, cue, target, target.x, target.y, p[0], p[1], r)) continue;

                float ang = (float) Math.toDegrees(Math.atan2(cdy, cdx));
                float power = clamp(0.38f + (cd + pd) / 1300f, 0.35f, 1f);

                float score = 1000f - cut * 5.2f - cd * 0.32f - pd * 0.22f;
                Physics.Out o = Physics.run(balls, cue, table, ang, power, r);
                if (o.scratch) continue;
                if (o.potted.contains(target.id)) {
                    score += 250f;
                } else if (o.firstHit != target.id) {
                    continue;
                }

                if (top == null || score > top.score) {
                    top = new Shot();
                    top.angleDeg = ang;
                    top.power = power;
                    top.score = score;
                    top.gx = gx; top.gy = gy;
                    top.tx = target.x; top.ty = target.y;
                    top.px = p[0]; top.py = p[1];
                    top.valid = true;
                    top.why = "POT #" + target.id + " (" + Math.round(cut) + "°)";
                }
            }
        }
        if (top != null) return top;

        Ball nearest = null;
        float nd = Float.MAX_VALUE;
        for (Ball b : balls) {
            if (b.id == 0) continue;
            float d = (float) Math.hypot(b.x - cue.x, b.y - cue.y);
            if (d < nd) { nd = d; nearest = b; }
        }
        Shot s = new Shot();
        if (nearest == null) { s.valid = false; s.why = "No target"; return s; }
        s.angleDeg = (float) Math.toDegrees(Math.atan2(nearest.y - cue.y, nearest.x - cue.x));
        s.power = clamp(0.45f + nd / 900f, 0.45f, 0.95f);
        s.gx = nearest.x; s.gy = nearest.y;
        s.tx = nearest.x; s.ty = nearest.y;
        s.px = nearest.x; s.py = nearest.y;
        s.valid = true;
        s.why = "HIT #" + nearest.id;
        return s;
    }

    private static boolean clear(List<Ball> balls, Ball cue, Ball target,
                                 float x1, float y1, float x2, float y2, float r) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return true;
        for (Ball b : balls) {
            if (b.id == cue.id || b.id == target.id) continue;
            float u = ((b.x - x1) * dx + (b.y - y1) * dy) / (len * len);
            u = Math.max(0, Math.min(1, u));
            float px = x1 + dx * u, py = y1 + dy * u;
            if (Math.hypot(b.x - px, b.y - py) < 2 * r * 0.94f) return false;
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private Solver() {}
}
