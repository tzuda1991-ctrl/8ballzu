package com.poollines.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class PoolAccessibilityService extends AccessibilityService {
    private static volatile PoolAccessibilityService instance;

    public static boolean ready() { return instance != null; }

    @Override public void onServiceConnected() { instance = this; }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}

    public static boolean swipe(float x1, float y1, float x2, float y2, long ms) {
        PoolAccessibilityService s = instance;
        if (s == null) return false;
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo((x1 + x2) * 0.5f, (y1 + y2) * 0.5f);
        p.lineTo(x2, y2);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, ms))
                .build();
        return s.dispatchGesture(g, null, null);
    }
}
