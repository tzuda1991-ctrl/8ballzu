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
import android.graphics.Paint;
import android.graphics.PixelFormat;
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

    private boolean adjust, auto;
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
        sw = dm.widthPixels;
        sh = dm.heightPixels;

        SharedPreferences p = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
        tl = p.getInt(Config.K_LEFT, sw / 10);
        tt = p.getInt(Config.K_TOP, sh / 4);
        tr = p.getInt(Config.K_RIGHT, sw * 9 / 10);
        tb = p.getInt(Config.K_BOTTOM, sh * 3 / 4);
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
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(bg(Color.argb(242, 16, 18, 22), 16));

        TextView title = new TextView(this);
        title.setText("8 BALL LINES");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(4), 0, dp(6));
        panel.addView(title);
        makeDraggable(title);

        status = new TextView(this);
        status.setTextColor(Color.argb(190, 255, 255, 255));
        status.setTextSize(10);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(4), 0, dp(10));
        panel.addView(status);

        autoBtn = new Button(this);
        autoBtn.setAllCaps(false);
        autoBtn.setTextSize(12);
        autoBtn.setTextColor(Color.WHITE);
        autoBtn.setOnClickListener(v -> {
            auto = !auto;
            getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(Config.K_AUTO, auto).apply();
            paintAuto();
        });
        panel.addView(autoBtn, new LinearLayout.LayoutParams(-1, dp(46)));

        shootBtn = new Button(this);
        shootBtn.setText("SHOOT NOW");
        shootBtn.setTextSize(11);
        shootBtn.setAllCaps(false);
        shootBtn.setTextColor(Color.WHITE);
        shootBtn.setBackground(bg(Color.rgb(190, 120, 20), 10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(40));
        sp.topMargin = dp(8);
        panel.addView(shootBtn, sp);
        shootBtn.setOnClickListener(v -> ctl.shootNow());

        calBtn = new Button(this);
        calBtn.setText("CALIBRAR MESA");
        calBtn.setTextSize(10);
        calBtn.setAllCaps(false);
        calBtn.setTextColor(Color.WHITE);
        calBtn.setBackground(bg(Color.rgb(35, 105, 170), 10));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(38));
        cp.topMargin = dp(8);
        panel.addView(calBtn, cp);
        calBtn.setOnClickListener(v -> { if (adjust) saveCal(); else startCal(); });

        Button close = new Button(this);
        close.setText("FECHAR");
        close.setTextSize(10);
        close.setAllCaps(false);
        close.setTextColor(Color.rgb(255, 130, 130));
        close.setBackground(bg(Color.rgb(62, 30, 30), 10));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, dp(34));
        cl.topMargin = dp(8);
        panel.addView(close, cl);
        close.setOnClickListener(v -> {
            stopService(new Intent(this, ScreenCaptureService.class));
            stopSelf();
        });

        panelParams = new WindowManager.LayoutParams(dp(184), -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.LEFT;
        panelParams.x = dp(8);
        panelParams.y = dp(110);
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
        autoBtn.setBackground(bg(auto ? Color.rgb(22, 163, 74) : Color.rgb(95, 95, 95), 10));
    }

    private void startCal() {
        adjust = true;
        auto = false;
        getSharedPreferences(Config.PREFS, MODE_PRIVATE).edit()
                .putBoolean(Config.K_AUTO, false).apply();
        paintAuto();
        overlayParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        try { wm.updateViewLayout(overlay, overlayParams); } catch (Exception ignored) {}
        calBtn.setText("GUARDAR MESA");
        calBtn.setBackground(bg(Color.rgb(25, 145, 75), 10));
        overlay.invalidate();
    }

    private void saveCal() {
        adjust = false;
        int minL = Math.min(tl, tr), maxR = Math.max(tl, tr);
        int minT = Math.min(tt, tb), maxB = Math.max(tt, tb);
        tl = minL; tr = maxR; tt = minT; tb = maxB;

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
        calBtn.setBackground(bg(Color.rgb(35, 105, 170), 10));
        overlay.invalidate();
    }

    @Override public void onState(String text) {
        if (status != null) status.setText(text);
    }

    @Override public void onPreview(float x, float y, float angleRad, float power, boolean show) {
        if (overlay == null) return;
        overlay.setPreview(x, y, angleRad, power, show);
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
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int corner;
        private float downX, downY;
        private int sL, sT, sR, sB;
        private boolean previewOn;
        private float pvX, pvY, pvAngle, pvPower;

        Overlay(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            line.setStrokeWidth(dp(2.5f));
            line.setStyle(Paint.Style.STROKE);
            line.setColor(Color.WHITE);
            dot.setStyle(Paint.Style.STROKE);
            dot.setStrokeWidth(dp(2.5f));
            dot.setColor(Color.WHITE);
        }

        void setPreview(float x, float y, float a, float p, boolean show) {
            pvX = x; pvY = y; pvAngle = a; pvPower = p;
            previewOn = show;
            invalidate();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!adjust) return false;
            float x = e.getX(), y = e.getY(), h = dp(48);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = x; downY = y;
                    sL = tl; sT = tt; sR = tr; sB = tb;
                    if (Math.hypot(x - tl, y - tt) < h) corner = 1;
                    else if (Math.hypot(x - tr, y - tt) < h) corner = 2;
                    else if (Math.hypot(x - tl, y - tb) < h) corner = 3;
                    else if (Math.hypot(x - tr, y - tb) < h) corner = 4;
                    else corner = 0;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = x - downX, dy = y - downY;
                    if (corner == 1) { tl = (int) (sL + dx); tt = (int) (sT + dy); }
                    if (corner == 2) { tr = (int) (sR + dx); tt = (int) (sT + dy); }
                    if (corner == 3) { tl = (int) (sL + dx); tb = (int) (sB + dy); }
                    if (corner == 4) { tr = (int) (sR + dx); tb = (int) (sB + dy); }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                    corner = 0;
                    return true;
            }
            return false;
        }

        private float[] exit(float x, float y, float dx, float dy) {
            float t = Float.MAX_VALUE;
            int wall = -1;
            if (dx > 0) { float c = (tr - x) / dx; if (c > 0 && c < t) { t = c; wall = 0; } }
            if (dx < 0) { float c = (tl - x) / dx; if (c > 0 && c < t) { t = c; wall = 1; } }
            if (dy > 0) { float c = (tb - y) / dy; if (c > 0 && c < t) { t = c; wall = 2; } }
            if (dy < 0) { float c = (tt - y) / dy; if (c > 0 && c < t) { t = c; wall = 3; } }
            return new float[]{t, wall};
        }

        private void drawPath(Canvas c, float x, float y, float ang, float power) {
            float dx = (float) Math.cos(ang), dy = (float) Math.sin(ang);
            for (int i = 0; i < 4; i++) {
                float[] e = exit(x, y, dx, dy);
                if (e[0] == Float.MAX_VALUE) break;
                float nx = x + dx * e[0], ny = y + dy * e[0];
                line.setColor(i == 0 ? 0xFFFFFFFF : 0x88FFFFFF);
                line.setStrokeWidth(dp(i == 0 ? 2.5f : 1.6f));
                c.drawLine(x, y, nx, ny, line);
                x = nx; y = ny;
                if (e[1] <= 1) dx = -dx; else dy = -dy;
            }
            float len = dp(14) + power * dp(60);
            c.drawCircle(pvX, pvY, dp(9), dot);
            c.drawCircle(pvX + (float) Math.cos(ang) * len,
                    pvY + (float) Math.sin(ang) * len, dp(6), dot);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (adjust) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(0x3328A8FF);
                c.drawRect(tl, tt, tr, tb, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setColor(Color.YELLOW);
                c.drawRect(tl, tt, tr, tb, p);
                p.setStyle(Paint.Style.FILL);
                for (int[] pt : new int[][]{{tl, tt}, {tr, tt}, {tl, tb}, {tr, tb}}) {
                    c.drawCircle(pt[0], pt[1], dp(11), p);
                }
                return;
            }
            if (previewOn) drawPath(c, pvX, pvY, pvAngle, pvPower);
        }
    }
}
