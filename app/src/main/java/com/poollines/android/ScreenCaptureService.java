package com.poollines.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenCaptureService extends Service {
    public static final String ACTION_FRAME = "com.poollines.FRAME";
    public static final String EX_BALLS = "balls", EX_CUE = "cue", EX_STILL = "still";

    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader reader;
    private HandlerThread thread;
    private Handler h;
    private int sw, sh, dw, dh;
    private long lastFrame;
    private List<Ball> prev = new ArrayList<>();
    private long stillSince;

    @Override public int onStartCommand(Intent it, int flags, int startId) {
        if (it == null) return START_NOT_STICKY;

        NotificationChannel ch = new NotificationChannel("cap", "Capture",
                NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "cap")
                .setContentTitle("Pool Lines capture")
                .setSmallIcon(android.R.drawable.btn_star)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(2, n);
        }

        int code = it.getIntExtra("code", 0);
        Intent data = it.getParcelableExtra("data");
        if (data == null) { stopSelf(); return START_NOT_STICKY; }

        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        mp = mpm.getMediaProjection(code, data);
        if (mp == null) { stopSelf(); return START_NOT_STICKY; }

        WindowManager wm = getSystemService(WindowManager.class);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels;
        sh = dm.heightPixels;
        dw = Math.max(1, sw / Config.CAPTURE_DIV);
        dh = Math.max(1, sh / Config.CAPTURE_DIV);

        thread = new HandlerThread("capture");
        thread.start();
        h = new Handler(thread.getLooper());

        mp.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() { stopSelf(); }
        }, h);

        reader = ImageReader.newInstance(dw, dh, PixelFormat.RGBA_8888, 2);
        vd = mp.createVirtualDisplay("pool", dw, dh, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, h);
        reader.setOnImageAvailableListener(r -> onFrame(), h);
        return START_NOT_STICKY;
    }

    private void onFrame() {
        Image img = null;
        try {
            img = reader.acquireLatestImage();
            if (img == null) return;
            long now = SystemClock.uptimeMillis();
            if (now - lastFrame < 1000 / Config.TARGET_FPS) { img.close(); return; }
            lastFrame = now;

            Bitmap bmp = toBitmap(img);
            img.close();
            img = null;
            if (bmp == null) return;

            SharedPreferences p = getSharedPreferences(Config.PREFS, MODE_PRIVATE);
            int l = p.getInt(Config.K_LEFT, sw / 10);
            int t = p.getInt(Config.K_TOP, sh / 4);
            int r = p.getInt(Config.K_RIGHT, sw * 9 / 10);
            int b = p.getInt(Config.K_BOTTOM, sh * 3 / 4);

            Rect table = new Rect(l / Config.CAPTURE_DIV, t / Config.CAPTURE_DIV,
                    r / Config.CAPTURE_DIV, b / Config.CAPTURE_DIV);
            float ballR = (r - l) * Config.BALL_R_RATIO / Config.CAPTURE_DIV;

            List<Ball> found = PoolDetector.detect(bmp, table, ballR);
            bmp.recycle();

            Ball cue = null;
            for (Ball x : found) {
                x.x *= Config.CAPTURE_DIV;
                x.y *= Config.CAPTURE_DIV;
                if (x.id == 0) cue = x;
            }
            boolean still = isStill(found, now);
            publish(found, cue, still);
        } catch (Exception e) {
            if (img != null) try { img.close(); } catch (Exception ignored) {}
        }
    }

    private boolean isStill(List<Ball> now, long ts) {
        boolean moved = now.size() != prev.size();
        if (!moved) {
            for (Ball a : now) {
                boolean matched = false;
                for (Ball b : prev) {
                    if (Math.hypot(a.x - b.x, a.y - b.y) < 8) { matched = true; break; }
                }
                if (!matched) { moved = true; break; }
            }
        }
        if (moved) {
            stillSince = ts;
            prev = now;
            return false;
        }
        prev = now;
        return ts - stillSince > Config.STILL_MS;
    }

    private void publish(List<Ball> balls, Ball cue, boolean still) {
        StringBuilder sb = new StringBuilder();
        for (Ball b : balls) {
            sb.append(b.id).append(',').append(b.color).append(',')
                    .append(Math.round(b.x)).append(',').append(Math.round(b.y)).append(';');
        }
        Intent i = new Intent(ACTION_FRAME).setPackage(getPackageName());
        i.putExtra(EX_BALLS, sb.toString());
        i.putExtra(EX_CUE, cue == null ? "" : Math.round(cue.x) + "," + Math.round(cue.y));
        i.putExtra(EX_STILL, still);
        sendBroadcast(i);
    }

    private Bitmap toBitmap(Image img) {
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int ps = plane.getPixelStride(), rs = plane.getRowStride();
        int w = dw + (rs - ps * dw) / ps;
        Bitmap raw = Bitmap.createBitmap(w, dh, Bitmap.Config.ARGB_8888);
        buf.rewind();
        raw.copyPixelsFromBuffer(buf);
        if (w != dw) {
            Bitmap cropped = Bitmap.createBitmap(raw, 0, 0, dw, dh);
            raw.recycle();
            return cropped;
        }
        return raw;
    }

    @Override public void onDestroy() {
        try { if (vd != null) vd.release(); } catch (Exception ignored) {}
        try { if (reader != null) reader.close(); } catch (Exception ignored) {}
        if (mp != null) try { mp.stop(); } catch (Exception ignored) {}
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
