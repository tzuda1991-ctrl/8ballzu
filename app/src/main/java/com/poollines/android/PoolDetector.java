package com.poollines.android;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class PoolDetector {

    public static List<Ball> detect(Bitmap bmp, Rect table, float ballR) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);

        int l = Math.max(0, table.left), t = Math.max(0, table.top);
        int r = Math.min(w - 1, table.right), bo = Math.min(h - 1, table.bottom);

        boolean[] mask = new boolean[w * h];
        for (int y = t; y <= bo; y += 2) {
            int row = y * w;
            for (int x = l; x <= r; x += 2) {
                int c = px[row + x];
                int rr = (c >> 16) & 0xff, gg = (c >> 8) & 0xff, bb = c & 0xff;
                int mn = Math.min(rr, Math.min(gg, bb));
                int mx = Math.max(rr, Math.max(gg, bb));
                if (mn > 200 && mx - mn < 26) mask[row + x] = true;
                else if (mx > 85 && mx - mn > 55) mask[row + x] = true;
            }
        }

        double expected = Math.PI * ballR * ballR / 4.0;
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        List<Ball> out = new ArrayList<>();
        int nextId = 1;
        Ball bestCue = null;
        int bestCueScore = -1;

        for (int y = t; y <= bo; y += 2) {
            for (int x = l; x <= r; x += 2) {
                int start = y * w + x;
                if (!mask[start] || seen[start]) continue;

                int sp = 0;
                stack[sp++] = start;
                seen[start] = true;

                long sumX = 0, sumY = 0, sumR = 0, sumG = 0, sumB = 0;
                int n = 0, minX = x, maxX = x, minY = y, maxY = y;

                while (sp > 0) {
                    int cur = stack[--sp];
                    int cy = cur / w, cx = cur - cy * w;
                    int c = px[cur];
                    sumR += (c >> 16) & 0xff;
                    sumG += (c >> 8) & 0xff;
                    sumB += c & 0xff;
                    sumX += cx;
                    sumY += cy;
                    n++;

                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;

                    for (int dy = -2; dy <= 2; dy += 2) {
                        int ny = cy + dy;
                        if (ny < t || ny > bo) continue;
                        for (int dx = -2; dx <= 2; dx += 2) {
                            int nx = cx + dx;
                            if (nx < l || nx > r) continue;
                            int ni = ny * w + nx;
                            if (mask[ni] && !seen[ni]) {
                                seen[ni] = true;
                                stack[sp++] = ni;
                            }
                        }
                    }
                }

                if (n < expected * 0.35 || n > expected * 3.2) continue;
                int bw = maxX - minX + 1, bh = maxY - minY + 1;
                if (bw < 5 || bh < 5) continue;
                float ar = bw / (float) bh;
                if (ar < 0.55f || ar > 1.8f) continue;

                int mr = (int) (sumR / n), mg = (int) (sumG / n), mb = (int) (sumB / n);
                int col = classify(mr, mg, mb);
                if (col < 0) continue;

                float cx2 = sumX / (float) n, cy2 = sumY / (float) n;
                if (col == Ball.CUE) {
                    int brightness = mr + mg + mb;
                    if (brightness > bestCueScore) {
                        bestCueScore = brightness;
                        bestCue = new Ball(0, Ball.CUE, cx2, cy2);
                    }
                } else {
                    out.add(new Ball(nextId++, col, cx2, cy2));
                }
            }
        }
        if (bestCue != null) {
            out.add(0, bestCue);
        }
        return out;
    }

    private static int classify(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBToHSV(r, g, b, hsv);
        if (hsv[2] > 0.72f && hsv[1] < 0.22f) return Ball.CUE;
        if (hsv[2] < 0.22f) return Ball.BLACK;
        if (hsv[1] < 0.28f) return Ball.UNKNOWN;
        float hh = hsv[0];
        if (hh < 20 || hh >= 340) return Ball.RED;
        if (hh < 45) return Ball.ORANGE;
        if (hh < 70) return Ball.YELLOW;
        if (hh < 165) return Ball.GREEN;
        if (hh < 260) return Ball.BLUE;
        if (hh < 320) return Ball.PURPLE;
        return Ball.MAROON;
    }

    private PoolDetector() {}
}
