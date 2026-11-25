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

        int pad = Math.max(4, Math.round(ballR * 0.65f));
        int l = Math.max(0, table.left + pad), t = Math.max(0, table.top + pad);
        int r = Math.min(w - 1, table.right - pad), bo = Math.min(h - 1, table.bottom - pad);
        if (r <= l || bo <= t) return new ArrayList<>();

        // Sample average felt color from 4 inner quadrants so blue/green felt never triggers ball mask
        int feltR = 45, feltG = 140, feltB = 185;
        long fR = 0, fG = 0, fB = 0;
        int fCnt = 0;
        int stepY = Math.max(4, (bo - t) / 10);
        int stepX = Math.max(4, (r - l) / 14);
        for (int sy = t + stepY; sy < bo - stepY; sy += stepY) {
            for (int sx = l + stepX; sx < r - stepX; sx += stepX) {
                int c = px[sy * w + sx];
                int rr = (c >> 16) & 0xff, gg = (c >> 8) & 0xff, bb = c & 0xff;
                // Blue/green felt typically has G > R + 20 and B > R + 20
                if (gg > rr + 15 && bb > rr + 15 && rr < 110) {
                    fR += rr; fG += gg; fB += bb; fCnt++;
                }
            }
        }
        if (fCnt > 8) {
            feltR = (int) (fR / fCnt);
            feltG = (int) (fG / fCnt);
            feltB = (int) (fB / fCnt);
        }

        boolean[] mask = new boolean[w * h];
        for (int y = t; y <= bo; y++) {
            int row = y * w;
            for (int x = l; x <= r; x++) {
                int c = px[row + x];
                int rr = (c >> 16) & 0xff, gg = (c >> 8) & 0xff, bb = c & 0xff;

                // Distance from felt color
                int df = Math.abs(rr - feltR) + Math.abs(gg - feltG) + Math.abs(bb - feltB);
                if (df < 68) continue;

                int mn = Math.min(rr, Math.min(gg, bb));
                int mx = Math.max(rr, Math.max(gg, bb));

                // White / Cue ball (including shaded cream/gray cue ball or hand icon border)
                if (mn > 155 && mx - mn < 45) {
                    mask[row + x] = true;
                } else if (mx < 58 && df > 110) {
                    // 8-ball black
                    mask[row + x] = true;
                } else if (mx > 85 && (mx - mn > 48 || df > 105)) {
                    // Colored object balls
                    mask[row + x] = true;
                }
            }
        }

        double expected = Math.PI * ballR * ballR;
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        List<Ball> out = new ArrayList<>();
        int nextId = 1;
        Ball bestCue = null;
        float bestCueScore = -1f;

        for (int y = t; y <= bo; y++) {
            for (int x = l; x <= r; x++) {
                int start = y * w + x;
                if (!mask[start] || seen[start]) continue;

                int sp = 0;
                stack[sp++] = start;
                seen[start] = true;

                long sumX = 0, sumY = 0, sumR = 0, sumG = 0, sumB = 0;
                int n = 0, minX = x, maxX = x, minY = y, maxY = y;
                int whitePx = 0;

                while (sp > 0) {
                    int cur = stack[--sp];
                    int cy = cur / w, cx = cur - cy * w;
                    int c = px[cur];
                    int rr = (c >> 16) & 0xff, gg = (c >> 8) & 0xff, bb = c & 0xff;
                    sumR += rr;
                    sumG += gg;
                    sumB += bb;
                    if (rr > 175 && gg > 175 && bb > 170 && Math.max(rr, Math.max(gg, bb)) - Math.min(rr, Math.min(gg, bb)) < 38) {
                        whitePx++;
                    }
                    sumX += cx;
                    sumY += cy;
                    n++;

                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;

                    for (int dy = -1; dy <= 1; dy++) {
                        int ny = cy + dy;
                        if (ny < t || ny > bo) continue;
                        for (int dx = -1; dx <= 1; dx++) {
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

                // Filter out thin long aim lines or cue sticks:
                int bw = maxX - minX + 1, bh = maxY - minY + 1;
                if (bw < 4 || bh < 4) continue;
                float ar = bw / (float) bh;
                // Aim line or cue stick has very extreme aspect ratio
                if (ar < 0.35f || ar > 2.8f) continue;
                if (n < expected * 0.22 || n > expected * 4.5) continue;

                int mr = (int) (sumR / n), mg = (int) (sumG / n), mb = (int) (sumB / n);
                int col = classify(mr, mg, mb, whitePx / (float) n);
                if (col < 0) continue;

                float cx2 = sumX / (float) n, cy2 = sumY / (float) n;
                if (col == Ball.CUE) {
                    float roundness = 1f - Math.abs(1f - ar);
                    float score = whitePx * roundness;
                    if (score > bestCueScore) {
                        bestCueScore = score;
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

    private static int classify(int r, int g, int b, float whiteRatio) {
        float[] hsv = new float[3];
        Color.RGBToHSV(r, g, b, hsv);
        if ((hsv[2] > 0.65f && hsv[1] < 0.28f) || whiteRatio > 0.48f) return Ball.CUE;
        if (hsv[2] < 0.25f) return Ball.BLACK;
        float hh = hsv[0];
        if (hh < 18 || hh >= 340) return Ball.RED;
        if (hh < 42) return Ball.ORANGE;
        if (hh < 72) return Ball.YELLOW;
        if (hh < 165) return Ball.GREEN;
        if (hh < 260) return Ball.BLUE;
        if (hh < 320) return Ball.PURPLE;
        return Ball.MAROON;
    }

    private PoolDetector() {}
}
