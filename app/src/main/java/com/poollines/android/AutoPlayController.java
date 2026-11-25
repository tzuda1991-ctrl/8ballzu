package com.poollines.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoPlayController {
    public interface Listener {
        void onState(String text);
        void onPreview(float x, float y, float angleRad, float power, boolean show);
    }

    private final Context ctx;
    private final Listener ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private List<Ball> balls;
    private Ball cue;
    private boolean still;
    private boolean inFlight;
    private boolean forceOnce;
    private long lastShot;
    private long lastFail;
    private String lastState = "";

    public AutoPlayController(Context c, Listener l) {
        this.ctx = c;
        this.ui = l;
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE);
    }

    public void onFrame(List<Ball> b, Ball c, boolean s) {
        balls = b;
        cue = c;
        still = s;
        tick();
    }

    public void shootNow() {
        if (inFlight) return;
        forceOnce = true;
        lastShot = 0;
        lastFail = 0;
        tick();
    }

    public void shutdown() {
        bg.shutdownNow();
    }

    private void state(String s) {
        if (s.equals(lastState)) return;
        lastState = s;
        main.post(() -> ui.onState(s));
    }

    private void preview(float x, float y, float angRad, float p, boolean show) {
        main.post(() -> ui.onPreview(x, y, angRad, p, show));
    }

    private void tick() {
        if (inFlight) { state("Ball chal rahi..."); return; }
        boolean forced = forceOnce;
        forceOnce = false;

        if (!forced && !prefs().getBoolean(Config.K_AUTO, false)) {
            state("AUTO OFF");
            return;
        }
        if (!PoolAccessibilityService.ready()) { state("Accessibility OFF"); return; }
        if (balls == null) { state("Frame nahi"); return; }
        if (cue == null) { state("Cue ball nahi mili"); return; }
        if (!still && !forced) { state("Balls moving..."); return; }

        long now = SystemClock.uptimeMillis();
        if (!forced && now - lastShot < Config.COOLDOWN_MS) { state("Cooldown"); return; }
        if (!forced && now - lastFail < Config.FAIL_BACKOFF_MS) { state("Retry..."); return; }

        Rect table = new Rect(
                prefs().getInt(Config.K_LEFT, 0),
                prefs().getInt(Config.K_TOP, 0),
                prefs().getInt(Config.K_RIGHT, 0),
                prefs().getInt(Config.K_BOTTOM, 0));
        if (table.width() < 200 || table.height() < 200) {
            state("Pehle table calibrate karo");
            return;
        }

        float r = table.width() * Config.BALL_R_RATIO;
        List<Ball> snap = new ArrayList<>(balls);
        Ball cueSnap = cue.copy();
        inFlight = true;
        state("Solving...");

        bg.execute(() -> {
            Solver.Shot shot = RailwayBrain.solve(snap, cueSnap, table, r);
            if (shot == null) shot = Solver.best(snap, cueSnap, table, r);
            Solver.Shot result = shot;
            main.post(() -> fire(result, cueSnap, table, r));
        });
    }

    private void fire(Solver.Shot shot, Ball cueSnap, Rect table, float r) {
        if (shot == null || !shot.valid || cueSnap == null) {
            inFlight = false;
            lastFail = SystemClock.uptimeMillis();
            state("Shot nahi mila");
            return;
        }
        float rad = (float) Math.toRadians(shot.angleDeg);
        float dirX = (float) Math.cos(rad), dirY = (float) Math.sin(rad);
        boolean invert = prefs().getBoolean(Config.K_INVERT, false);
        float sign = invert ? 1f : -1f;
        float maxDrag = prefs().getInt(Config.K_MAXDRAG, (int) (table.width() * 0.28f));
        float dist = r * 2.2f + shot.power * maxDrag;

        float sx = cueSnap.x, sy = cueSnap.y;
        float ex = clamp(sx + dirX * sign * dist, table.left + 4, table.right - 4);
        float ey = clamp(sy + dirY * sign * dist, table.top + 4, table.bottom - 4);

        preview(sx, sy, rad, shot.power, true);
        lastShot = SystemClock.uptimeMillis();
        state("SHOOT " + Math.round(shot.angleDeg) + "° p" + Math.round(shot.power * 100));

        PoolAccessibilityService.swipe(sx, sy, ex, ey, Config.GESTURE_MS);

        main.postDelayed(() -> {
            inFlight = false;
            lastShot = SystemClock.uptimeMillis();
            preview(0, 0, 0, 0, false);
        }, Config.GESTURE_MS + 900);
    }

    private float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
