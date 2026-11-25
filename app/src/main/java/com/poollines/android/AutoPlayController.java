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
        void onPreview(List<Ball> balls, Ball cue, Solver.Shot shot, boolean show);
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

    private void preview(List<Ball> b, Ball c, Solver.Shot shot, boolean show) {
        main.post(() -> ui.onPreview(b, c, shot, show));
    }

    private void tick() {
        if (inFlight) { return; }
        boolean forced = forceOnce;
        forceOnce = false;

        Rect table = new Rect(
                prefs().getInt(Config.K_LEFT, 0),
                prefs().getInt(Config.K_TOP, 0),
                prefs().getInt(Config.K_RIGHT, 0),
                prefs().getInt(Config.K_BOTTOM, 0));
        if (table.width() < 200 || table.height() < 150) {
            state("Table calibrate karo");
            return;
        }

        if (balls == null) { state("Frame wait..."); return; }
        if (cue == null) {
            state("Balls: " + balls.size() + " | Cue wait...");
            preview(balls, null, null, true);
            return;
        }

        float r = table.width() * Config.BALL_R_RATIO;
        List<Ball> snap = new ArrayList<>(balls);
        Ball cueSnap = cue.copy();

        // Always compute best shot live so aim lines render continuously on screen!
        Solver.Shot liveShot = Solver.best(snap, cueSnap, table, r);
        preview(snap, cueSnap, liveShot, true);

        boolean autoOn = prefs().getBoolean(Config.K_AUTO, false);
        if (!forced && !autoOn) {
            state(liveShot != null && liveShot.valid
                    ? "READY: " + liveShot.why
                    : "AUTO OFF | Balls: " + snap.size());
            return;
        }

        if (!PoolAccessibilityService.ready()) {
            state("Accessibility ON karo!");
            return;
        }
        if (!still && !forced) {
            state("Balls moving...");
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (!forced && now - lastShot < Config.COOLDOWN_MS) {
            state("Cooldown...");
            return;
        }
        if (!forced && now - lastFail < Config.FAIL_BACKOFF_MS) {
            return;
        }

        inFlight = true;
        state("Aiming...");

        bg.execute(() -> {
            Solver.Shot shot = RailwayBrain.solve(snap, cueSnap, table, r);
            if (shot == null) shot = liveShot != null ? liveShot : Solver.best(snap, cueSnap, table, r);
            Solver.Shot result = shot;
            main.post(() -> fire(snap, cueSnap, result, table, r));
        });
    }

    private void fire(List<Ball> snap, Ball cueSnap, Solver.Shot shot, Rect table, float r) {
        if (shot == null || !shot.valid || cueSnap == null) {
            inFlight = false;
            lastFail = SystemClock.uptimeMillis();
            state("Shot nahi mila");
            return;
        }

        preview(snap, cueSnap, shot, true);
        float rad = (float) Math.toRadians(shot.angleDeg);
        float dirX = (float) Math.cos(rad), dirY = (float) Math.sin(rad);

        // Step 1: Aim gesture on table (tap/drag near target point so 8 Ball Pool aims cue toward shot angle)
        float aimDist = Math.min(table.width() * 0.22f, 240f);
        float aimX = clamp(cueSnap.x + dirX * aimDist, table.left + 30, table.right - 30);
        float aimY = clamp(cueSnap.y + dirY * aimDist, table.top + 30, table.bottom - 30);

        state("AIM " + Math.round(shot.angleDeg) + "° (" + shot.why + ")");
        // Small drag at target point rotates cue stick in 8 Ball Pool
        PoolAccessibilityService.swipe(aimX, aimY, aimX + dirX * 14f, aimY + dirY * 14f, Config.AIM_MS);

        // Step 2: Pull power meter on the left side OR pull cue back
        main.postDelayed(() -> {
            state("SHOOT p" + Math.round(shot.power * 100) + "%");
            // In 8 Ball Pool, the power meter is on the left side of the screen (x ~ table.left * 0.42f)
            float meterX = Math.max(35f, table.left * 0.42f);
            float meterTop = table.top + table.height() * 0.18f;
            float meterDrag = table.height() * 0.55f * clamp(shot.power, 0.35f, 1.0f);
            float meterBottom = Math.min(table.bottom - 10f, meterTop + meterDrag);

            PoolAccessibilityService.swipe(meterX, meterTop, meterX, meterBottom, Config.POWER_MS);

            main.postDelayed(() -> {
                inFlight = false;
                lastShot = SystemClock.uptimeMillis();
            }, Config.POWER_MS + 900);
        }, Config.AIM_MS + 220);
    }

    private float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
