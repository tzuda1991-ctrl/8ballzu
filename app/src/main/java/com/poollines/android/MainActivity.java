package com.poollines.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQ_PROJ = 1001;
    private static final int REQ_NOTIF = 1002;
    private TextView log;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 14, 18));
        root.setPadding(56, 90, 56, 56);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("POOL LINES v4.0\n6-Pocket Auto-Play");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        log = new TextView(this);
        log.setTextColor(Color.rgb(175, 175, 175));
        log.setTextSize(12);
        log.setPadding(0, 40, 0, 30);
        log.setText("1. Overlay Permission\n2. Accessibility ON\n3. Screen Capture Allow");
        root.addView(log);

        Button start = new Button(this);
        start.setText("START");
        start.setTextSize(16);
        start.setOnClickListener(v -> go());
        root.addView(start, new LinearLayout.LayoutParams(-1, 130));

        setContentView(root);
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
    }

    private void go() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            log.setText("Overlay permission do, phir START dobara");
            return;
        }
        if (!accOn()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            log.setText("Pool Lines accessibility ON karo, phir START dobara");
            return;
        }
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJ);
    }

    private boolean accOn() {
        String s = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        String pkg = getPackageName();
        return s.contains(pkg + "/" + PoolAccessibilityService.class.getName())
                || s.contains(pkg + "/.PoolAccessibilityService");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PROJ) return;
        if (res != RESULT_OK || data == null) {
            log.setText("Capture permission cancel ho gayi");
            return;
        }
        Intent cap = new Intent(this, ScreenCaptureService.class);
        cap.putExtra("code", res);
        cap.putExtra("data", data);
        startForegroundService(cap);
        startForegroundService(new Intent(this, PoolOverlayService.class));
        finish();
    }
}
