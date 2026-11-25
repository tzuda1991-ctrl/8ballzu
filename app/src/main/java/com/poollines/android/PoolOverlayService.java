package com.poollines.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class PoolOverlayService extends Service
        implements AutoPlayController.Listener {

    private WindowManager wm;
    private WindowManager.LayoutParams overlayParams, panelParams;
    private Overlay overlay;
    private LinearLayout panel;
    private TextView status;
    private Button autoBtn, shootBtn, calBtn;

    private boolean adjust = false, auto = false;
    private int tl, tt, tr, tb, sw, sh;
    private AutoPlayController ctl;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (i == null || ctl == null) return;
            List<Ball> list = new ArrayList<>();
            String raw = i.getStringExtra(ScreenCaptureService.EX_BALLS);
            if (raw != null && !raw.isEmpty()) {
                for (String s : raw.split(";")) {
                    String[] f = s.split(",");
                    if (f.length < 4) continue;
                    try {
                        list.add(new Ball(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
                                Float.parseFloat(f[2]), Float.parseFloat(f[3])));
                    } catch (Exception ignored) {}
                }
            }
            Ball cue = null;
            String cs = i.getStringExtra(ScreenCaptureService.EX_CUE);
            if (cs != null && !cs.isEmpty()) {
                String[] f = cs.split(",");
                try {
                    cue = new Ball(0, Ball.CUE, Float.parseFloat(f[0]), Float.parseFloat(f[1]));
                } catch (Exception ignored) {}
            }
            ctl.onFrame(list, cue, i.getBooleanExtra(ScreenCaptureService.EX_STILL, false));
        }
    };

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private GradientDrawable bg(int c, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(c);
        d.setCornerRadius(dp(r));
        return d;
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel ch = new NotificationChannel("ov", "Overlay",
                NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "ov")
                .setContentTitle("Pool Lines")
                .setSmallIcon(android.R.drawable.btn_star)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, n);
        }

        wm = getSystemService(WindowManager.class);
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        // Always use landscape bounds so table bottom is NEVER cut off when starting from portrait MainActivity!
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);

        SharedPreferences p = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        int defL = (int) (sw * 0.18f);
        int defT = (int) (sh * 0.21f);
        int defR = (int) (sw * 0.82f);
        int defB = (int) (sh * 0.92f);

        tl = p.getInt(Config.K_LEFT, defL);
        tt = p.getInt(Config.K_TOP, defT);
        tr = p.getInt(Config.K_RIGHT, defR);
        tb = p.getInt(Config.K_BOTTOM, defB);

        // Self-heal corrupted portrait bounds (where bottom > landscape height)
        if (tb > sh || tr > sw || tr - tl < dp(180) || tb - tt < dp(120)) {
            tl = defL; tt = defT; tr = defR; tb = defB;
            p.edit().putInt(Config.K_LEFT, tl).putInt(Config.K_TOP, tt)
                    .putInt(Config.K_RIGHT, tr).putInt(Config.K_BOTTOM, tb).apply();
        } else if (!p.contains(Config.K_LEFT)) {
            p.edit().putInt(Config.K_LEFT, tl).putInt(Config.K_TOP, tt)
                    .putInt(Config.K_RIGHT, tr).putInt(Config.K_BOTTOM, tb).apply();
        }

        auto = p.getBoolean(Config.K_AUTO, false);
        ctl = new AutoPlayController(this, this);

        overlay = new Overlay(this);
        overlayParams = new WindowManager.LayoutParams(-1, -1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(overlay, overlayParams); } catch (Exception ignored) {}

        buildPanel();

        IntentFilter f = new IntentFilter(ScreenCaptureService.ACTION_FRAME);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rx, f);
        }
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(bg(Color.argb(235, 16, 18, 24), 14));

        TextView title = new TextView(this);
        title.setText("8 BALL LINES (DRAG)");
        title.setTextColor(Color.WHITE);
        title.setTextSize(11);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(2), 0, dp(4));
        panel.addView(title);
        makeDraggable(title);

        status = new TextView(this);
        status.setText("Ready - Calibrate once");
        status.setTextColor(Color.rgb(125, 249, 255));
        status.setTextSize(10);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(2), 0, dp(6));
        panel.addView(status);

        autoBtn = new Button(this);
        autoBtn.setAllCaps(false);
        autoBtn.setTextSize(11);
        autoBtn.setTextColor(Color.WHITE);
        autoBtn.setOnClickListener(v -> {
            // If user turns on AUTO while still in calibration mode, auto-save calibration so screen isn't blocked!
            if (adjust) saveCal();
            auto = !auto;
            getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(Config.K_AUTO, auto).apply();
            paintAuto();
        });
        panel.addView(autoBtn, new LinearLayout.LayoutParams(-1, dp(38)));

        shootBtn = new Button(this);
        shootBtn.setText("SHOOT NOW");
        shootBtn.setTextSize(10);
        shootBtn.setAllCaps(false);
        shootBtn.setTextColor(Color.WHITE);
        shootBtn.setBackground(bg(Color.rgb(217, 119, 6), 8));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(34));
        sp.topMargin = dp(6);
        panel.addView(shootBtn, sp);
        shootBtn.setOnClickListener(v -> {
            if (adjust) saveCal();
            ctl.shootNow();
        });

        calBtn = new Button(this);
        calBtn.setText("CALIBRAR MESA");
        calBtn.setTextSize(10);
        calBtn.setAllCaps(false);
        calBtn.setTextColor(Color.WHITE);
        calBtn.setBackground(bg(Color.rgb(37, 99, 235), 8));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(34));
        cp.topMargin = dp(6);
        panel.addView(calBtn, cp);
        calBtn.setOnClickListener(v -> { if (adjust) saveCal(); else startCal(); });

        Button resetBtn = new Button(this);
        resetBtn.setText("RESET MESA");
        resetBtn.setTextSize(9);
        resetBtn.setAllCaps(false);
        resetBtn.setTextColor(Color.rgb(200, 220, 255));
        resetBtn.setBackground(bg(Color.rgb(45, 55, 72), 8));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(30));
        rp.topMargin = dp(6);
        panel.addView(resetBtn, rp);
        resetBtn.setOnClickListener(v -> resetCal());

        Button close = new Button(this);
        close.setText("FECHAR");
        close.setTextSize(9);
        close.setAllCaps(false);
        close.setTextColor(Color.rgb(255, 140, 140));
        close.setBackground(bg(Color.rgb(69, 26, 26), 8));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(30));
        cl.topMargin = dp(6);
        panel.addView(close, cl);
        close.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            stopSelf();
        });

        panelParams = new WindowManager.LayoutParams(dp(156), -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.LEFT;
        panelParams.x = dp(6);
        panelParams.y = dp(40);
        try { wm.addView(panel, panelParams); } catch (Exception ignored) {}
        paintAuto();
    }

    private void makeDraggable(View handle) {
        final float[] down = new float[2];
        final int[] origin = new int[2];
        handle.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    origin[0] = panelParams.x;
                    origin[1] = panelParams.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    panelParams.x = origin[0] + (int) (e.getRawX() - down[0]);
                    panelParams.y = origin[1] + (int) (e.getRawY() - down[1]);
                    try { wm.updateViewLayout(panel, panelParams); } catch (Exception ignored) {}
                    return true;
            }
            return false;
        });
    }

    private void paintAuto() {
        autoBtn.setText(auto ? "AUTO: ON" : "AUTO: OFF");
        autoBtn.setBackground(bg(auto ? Color.rgb(22, 163, 74) : Color.rgb(82, 82, 91), 8));
    }

    private void resetCal() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = Math.max(dm.widthPixels, dm.heightPixels);
        sh = Math.min(dm.widthPixels, dm.heightPixels);
        tl = (int) (sw * 0.18f);
        tt = (int) (sh * 0.21f);
        tr = (int) (sw * 0.82f);
        tb = (int) (sh * 0.92f);
        saveCal();
    }

    private void startCal() {
        adjust = true;
        auto = false;
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
                .putBoolean(Config.K_AUTO, false).apply();
        paintAuto();
        // Make overlay touchable ONLY while calibrating 6 pockets
        overlayParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        try { wm.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
        calBtn.setText("GUARDAR MESA");
        calBtn.setBackground(bg(Color.rgb(22, 163, 74), 8));
        status.setText("6 Pockets set karke GUARDAR dabao");
        overlay.invalidate();
    }

    private void saveCal() {
        adjust = false;
        int minL = Math.min(tl, tr), maxR = Math.max(tl, tr);
        int minT = Math.min(tt, tb), maxB = Math.max(tt, tb);
        tl = minL; tr = maxR; tt = minT; tb = maxB;

        // Restore FLAG_NOT_TOUCHABLE so all game touches work normally!
        overlayParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        try { wm.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
                .putBoolean(Config.K_CAL, true)
                .putInt(Config.K_LEFT, tl).putInt(Config.K_TOP, tt)
                .putInt(Config.K_RIGHT, tr).putInt(Config.K_BOTTOM, tb)
                .putInt(Config.K_W, sw).putInt(Config.K_H, sh).apply();
        calBtn.setText("CALIBRAR MESA");
        calBtn.setBackground(bg(Color.rgb(37, 99, 235), 8));
        status.setText("Table Saved! Game touch active");
        overlay.invalidate();
    }

    @Override public void onState(String text) {
        if (status != null && !adjust) status.setText(text);
    }

    @Override public void onPreview(List<Ball> balls, Ball cue, Solver.Shot shot, boolean show) {
        if (overlay == null) return;
        overlay.setPreview(balls, cue, shot, show);
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(rx); } catch (Exception ignored) {}
        if (ctl != null) ctl.shutdown();
        try { if (overlay != null) wm.removeView(overlay); } catch (Exception ignored) {}
        try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private class Overlay extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint potLine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int handle = 0;
        private float downX, downY;
        private int sL, sT, sR, sB;
        private boolean previewOn;
        private List<Ball> pvBalls = new ArrayList<>();
        private Ball pvCue;
        private Solver.Shot pvShot;

        Overlay(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            line.setStrokeWidth(dp(2.5f));
            line.setStyle(Paint.Style.STROKE);
            line.setColor(Color.CYAN);
            line.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(4)}, 0));

            potLine.setStrokeWidth(dp(3f));
            potLine.setStyle(Paint.Style.STROKE);
            potLine.setColor(Color.rgb(74, 222, 128));

            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(dp(2f));
            dot.setColor(Color.WHITE);
        }

        void setPreview(List<Ball> balls, Ball cue, Solver.Shot shot, boolean show) {
            pvBalls = balls != null ? balls : new ArrayList<>();
            pvCue = cue;
            pvShot = shot;
            previewOn = show;
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!adjust) return false;
            float x = e.getX(), y = e.getY(), h = dp(44);
            int mx = (tl + tr) / 2;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y;
                    sL = tl; sT = tt; sR = tr; sB = tb;
                    // 6 Pocket Handles:
                    // 1: Top-Left, 2: Top-Center, 3: Top-Right
                    // 4: Bottom-Left, 5: Bottom-Center, 6: Bottom-Right
                    if (Math.hypot(x - tl, y - tt) < h) handle = 1;
                    else if (Math.hypot(x - mx, y - tt) < h) handle = 2;
                    else if (Math.hypot(x - tr, y - tt) < h) handle = 3;
                    else if (Math.hypot(x - tl, y - tb) < h) handle = 4;
                    else if (Math.hypot(x - mx, y - tb) < h) handle = 5;
                    else if (Math.hypot(x - tr, y - tb) < h) handle = 6;
                    else if (x > tl && x < tr && y > tt && y < tb) handle = 7; // Drag whole table
                    else handle = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - downX, dy = y - downY;
                    if (handle == 1) { tl = (int) (sL + dx); tt = (int) (sT + dy); }
                    else if (handle == 2) { tt = (int) (sT + dy); }
                    else if (handle == 3) { tr = (int) (sR + dx); tt = (int) (sT + dy); }
                    else if (handle == 4) { tl = (int) (sL + dx); tb = (int) (sB + dy); }
                    else if (handle == 5) { tb = (int) (sB + dy); }
                    else if (handle == 6) { tr = (int) (sR + dx); tb = (int) (sB + dy); }
                    else if (handle == 7) {
                        tl = (int) (sL + dx); tr = (int) (sR + dx);
                        tt = (int) (sT + dy); tb = (int) (sB + dy);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    handle = 0;
                    return true;
            }
            return false;
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int mx = (tl + tr) / 2;

            // Always draw subtle table frame & 6 pocket rings
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(dp(adjust ? 2.5f : 1.2f));
            boxPaint.setColor(adjust ? Color.YELLOW : 0x55FACC15);
            c.drawRect(tl, tt, tr, tb, boxPaint);

            int[][] sixPockets = new int[][]{
                {tl, tt}, {mx, tt}, {tr, tt},
                {tl, tb}, {mx, tb}, {tr, tb}
            };

            if (adjust) {
                boxPaint.setStyle(Paint.Style.FILL);
                boxPaint.setColor(0x2838BDF8);
                c.drawRect(tl, tt, tr, tb, boxPaint);

                for (int[] pt : sixPockets) {
                    boxPaint.setStyle(Paint.Style.FILL);
                    boxPaint.setColor(Color.YELLOW);
                    c.drawCircle(pt[0], pt[1], dp(12), boxPaint);
                    boxPaint.setStyle(Paint.Style.STROKE);
                    boxPaint.setStrokeWidth(dp(2));
                    boxPaint.setColor(Color.BLACK);
                    c.drawCircle(pt[0], pt[1], dp(12), boxPaint);
                }
                return;
            }

            // Draw 6 pocket target markers
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(dp(1.5f));
            boxPaint.setColor(0x884ADE80);
            for (int[] pt : sixPockets) {
                c.drawCircle(pt[0], pt[1], dp(10), boxPaint);
            }

            if (!previewOn) return;

            float r = Math.max(dp(9), (tr - tl) * Config.BALL_R_RATIO);

            // Highlight detected balls
            for (Ball b : pvBalls) {
                dot.setColor(b.id == 0 ? Color.WHITE : 0x9938BDF8);
                dot.setStrokeWidth(dp(b.id == 0 ? 2.5f : 1.5f));
                c.drawCircle(b.x, b.y, r, dot);
            }

            // Draw live aim lines (Cue -> Ghost Ball & Target Ball -> Pocket)
            if (pvCue != null && pvShot != null && pvShot.valid) {
                c.drawLine(pvCue.x, pvCue.y, pvShot.gx, pvShot.gy, line);
                c.drawLine(pvShot.tx, pvShot.ty, pvShot.px, pvShot.py, potLine);

                dot.setColor(Color.YELLOW);
                dot.setStrokeWidth(dp(2f));
                c.drawCircle(pvShot.gx, pvShot.gy, r, dot);
            }
        }
    }
}
