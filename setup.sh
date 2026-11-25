#!/usr/bin/env bash
set -euo pipefail

echo "==> Purani files saaf kar rahe hain"
rm -f app/src/main/java/com/poollines/PoolOverlayService.java
rm -rf app/build build .gradle
mkdir -p app/src/main/java/com/poollines/android \
         app/src/main/res/values app/src/main/res/xml \
         gradle/wrapper .github/workflows server

echo "==> Gradle files generate kar rahe hain"
cat > settings.gradle <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "8ball-overlay"
include ':app'
EOF

cat > build.gradle <<'EOF'
plugins {
    id 'com.android.application' version '8.2.2' apply false
}
EOF

cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
EOF

cat > .gitignore <<'EOF'
.gradle/
build/
app/build/
local.properties
*.iml
.idea/
.DS_Store
__pycache__/
*.pyc
EOF

cat > app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
}

android {
    namespace 'com.poollines.android'
    compileSdk 34

    defaultConfig {
        applicationId "com.poollines.android"
        minSdk 26
        targetSdk 34
        versionCode 4
        versionName "3.1"
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            minifyEnabled false
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
EOF

cat > app/proguard-rules.pro <<'EOF'
-keep class com.poollines.android.** { *; }
EOF

echo "==> AndroidManifest + resources"
cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="@string/app_name"
        android:icon="@android:drawable/ic_menu_compass"
        android:allowBackup="false"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".PoolOverlayService"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <service
            android:name=".ScreenCaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />

        <service
            android:name=".PoolAccessibilityService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
EOF

cat > app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Pool Lines</string>
    <string name="acc_label">Pool Lines Auto Aim</string>
    <string name="acc_desc">Table detect karke khud aim aur shoot karta hai.</string>
</resources>
EOF

cat > app/src/main/res/xml/accessibility_service_config.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="false"
    android:description="@string/acc_desc"
    android:notificationTimeout="100" />
EOF

echo "==> Java sources generate kar rahe hain"
cat > app/src/main/java/com/poollines/android/Config.java <<'EOF'
package com.poollines.android;

public final class Config {
    public static final String PREFS = "pool_cfg";
    public static final String K_LEFT = "l", K_TOP = "t", K_RIGHT = "r",
            K_BOTTOM = "b", K_W = "w", K_H = "h", K_CAL = "cal";
    public static final String K_AUTO = "auto", K_MAXDRAG = "maxdrag", K_INVERT = "invert";

    /** Railway brain URL (example: "https://your-app.up.railway.app/solve"). Khaali chhodo to sirf local solver chalega. */
    public static final String BRAIN_URL = "";

    public static final int CAPTURE_DIV = 2;
    public static final int TARGET_FPS = 8;
    public static final float BALL_R_RATIO = 0.0208f;
    public static final int STILL_MS = 900;
    public static final long COOLDOWN_MS = 3500;
    public static final long GESTURE_MS = 700;
    public static final long FAIL_BACKOFF_MS = 1500;
    public static final int BRAIN_TIMEOUT_MS = 1200;

    private Config() {}
}
EOF

cat > app/src/main/java/com/poollines/android/Ball.java <<'EOF'
package com.poollines.android;

public class Ball {
    public static final int CUE = 0, YELLOW = 1, BLUE = 2, RED = 3, PURPLE = 4,
            ORANGE = 5, GREEN = 6, MAROON = 7, BLACK = 8, UNKNOWN = 9;

    public final int id;
    public final int color;
    public float x, y, vx, vy;

    public Ball(int id, int color, float x, float y) {
        this.id = id;
        this.color = color;
        this.x = x;
        this.y = y;
    }

    public Ball copy() {
        Ball b = new Ball(id, color, x, y);
        b.vx = vx;
        b.vy = vy;
        return b;
    }
}
EOF

cat > app/src/main/java/com/poollines/android/PoolDetector.java <<'EOF'
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
EOF

cat > app/src/main/java/com/poollines/android/Physics.java <<'EOF'
package com.poollines.android;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class Physics {
    public static class Out {
        public int firstHit = -1;
        public boolean scratch = false;
        public final List<Integer> potted = new ArrayList<>();
    }

    private static final float DT = 1f / 120f;
    private static final float FRICTION = 0.992f;
    private static final float WALL = 0.88f;
    private static final float REST = 0.96f;

    public static Out run(List<Ball> balls, Ball cue, Rect table, float angleDeg,
                          float power, float r) {
        List<Ball> sim = new ArrayList<>();
        for (Ball b : balls) if (b.id != 0) sim.add(b.copy());
        Ball c = cue.copy();
        float sp = 950f * power;
        c.vx = (float) Math.cos(Math.toRadians(angleDeg)) * sp;
        c.vy = (float) Math.sin(Math.toRadians(angleDeg)) * sp;

        List<float[]> pockets = Solver.pockets(table, r);
        Out o = new Out();
        boolean cueLive = true;

        for (int step = 0; step < 900; step++) {
            if (cueLive) move(c, table, r);
            for (Ball b : sim) move(b, table, r);

            if (cueLive) {
                for (Ball b : sim) {
                    if (overlap(c, b, r)) {
                        if (o.firstHit < 0) o.firstHit = b.id;
                        resolve(c, b, r);
                    }
                }
            }
            for (int i = 0; i < sim.size(); i++) {
                for (int j = i + 1; j < sim.size(); j++) {
                    if (overlap(sim.get(i), sim.get(j), r)) resolve(sim.get(i), sim.get(j), r);
                }
            }

            if (cueLive) {
                for (float[] p : pockets) {
                    if (near(c, p, r * 1.35f)) {
                        cueLive = false;
                        o.scratch = true;
                        break;
                    }
                }
            }
            for (int i = sim.size() - 1; i >= 0; i--) {
                Ball b = sim.get(i);
                boolean dropped = false;
                for (float[] p : pockets) {
                    if (near(b, p, r * 1.35f)) { dropped = true; break; }
                }
                if (dropped) {
                    o.potted.add(b.id);
                    sim.remove(i);
                }
            }

            boolean moving = cueLive && speed(c) > 4f;
            if (!moving) for (Ball b : sim) if (speed(b) > 4f) { moving = true; break; }
            if (!moving) break;
        }
        return o;
    }

    private static float speed(Ball b) {
        return (float) Math.hypot(b.vx, b.vy);
    }

    private static void move(Ball b, Rect t, float r) {
        b.x += b.vx * DT;
        b.y += b.vy * DT;
        b.vx *= FRICTION;
        b.vy *= FRICTION;
        if (b.x < t.left + r) { b.x = t.left + r; b.vx = Math.abs(b.vx) * WALL; }
        if (b.x > t.right - r) { b.x = t.right - r; b.vx = -Math.abs(b.vx) * WALL; }
        if (b.y < t.top + r) { b.y = t.top + r; b.vy = Math.abs(b.vy) * WALL; }
        if (b.y > t.bottom - r) { b.y = t.bottom - r; b.vy = -Math.abs(b.vy) * WALL; }
    }

    private static boolean overlap(Ball a, Ball b, float r) {
        float dx = b.x - a.x, dy = b.y - a.y;
        return dx * dx + dy * dy < (2 * r) * (2 * r);
    }

    private static void resolve(Ball a, Ball b, float r) {
        float dx = b.x - a.x, dy = b.y - a.y;
        float d = (float) Math.hypot(dx, dy);
        if (d < 0.001f) d = 0.001f;
        float nx = dx / d, ny = dy / d;

        // Positional separation to prevent multi-frame overlap lock
        float pen = (2f * r) - d;
        if (pen > 0) {
            a.x -= nx * pen * 0.5f;
            a.y -= ny * pen * 0.5f;
            b.x += nx * pen * 0.5f;
            b.y += ny * pen * 0.5f;
        }

        float p = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
        if (p <= 0) return;
        float imp = p * REST;
        a.vx -= imp * nx; a.vy -= imp * ny;
        b.vx += imp * nx; b.vy += imp * ny;
    }

    private static boolean near(Ball b, float[] p, float d) {
        return Math.hypot(b.x - p[0], b.y - p[1]) < d;
    }

    private Physics() {}
}
EOF

cat > app/src/main/java/com/poollines/android/Solver.java <<'EOF'
package com.poollines.android;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class Solver {
    public static class Shot {
        public float angleDeg, power, score;
        public String why = "";
        public boolean valid;
    }

    public static List<float[]> pockets(Rect t, float r) {
        float in = r * 1.4f;
        List<float[]> p = new ArrayList<>();
        p.add(new float[]{t.left + in, t.top + in});
        p.add(new float[]{(t.left + t.right) / 2f, t.top + in * 0.6f});
        p.add(new float[]{t.right - in, t.top + in});
        p.add(new float[]{t.left + in, t.bottom - in});
        p.add(new float[]{(t.left + t.right) / 2f, t.bottom - in * 0.6f});
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
                if (cd < r * 1.2f) continue;

                float dot = (cdx * pdx + cdy * pdy) / cd;
                float cut = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
                if (cut > 75) continue;

                if (!clear(balls, cue, target, cue.x, cue.y, gx, gy, r)) continue;
                if (!clear(balls, cue, target, target.x, target.y, p[0], p[1], r)) continue;

                float ang = (float) Math.toDegrees(Math.atan2(cdy, cdx));
                float power = clamp(0.32f + (cd + pd) / 1400f, 0.3f, 1f);

                Physics.Out o = Physics.run(balls, cue, table, ang, power, r);
                if (o.firstHit != target.id || o.scratch) continue;
                if (!o.potted.contains(target.id)) continue;

                float score = 1000f - cut * 5f - cd * 0.35f - pd * 0.2f;
                if (top == null || score > top.score) {
                    top = new Shot();
                    top.angleDeg = ang;
                    top.power = power;
                    top.score = score;
                    top.valid = true;
                    top.why = "pot #" + target.id + " (" + Math.round(cut) + "°)";
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
        if (nearest == null) { s.valid = false; s.why = "no target"; return s; }
        s.angleDeg = (float) Math.toDegrees(Math.atan2(nearest.y - cue.y, nearest.x - cue.x));
        s.power = clamp(0.3f + nd / 900f, 0.35f, 0.9f);
        s.valid = true;
        s.why = "safety hit #" + nearest.id;
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
            if (Math.hypot(b.x - px, b.y - py) < 2 * r * 0.96f) return false;
        }
        return true;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private Solver() {}
}
EOF

cat > app/src/main/java/com/poollines/android/RailwayBrain.java <<'EOF'
package com.poollines.android;

import android.graphics.Rect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RailwayBrain {

    /** Null return karne pe caller local solver pe chala jata hai. */
    public static Solver.Shot solve(List<Ball> balls, Ball cue, Rect table, float r) {
        if (Config.BRAIN_URL == null || Config.BRAIN_URL.trim().isEmpty()) return null;
        HttpURLConnection con = null;
        try {
            String endpoint = Config.BRAIN_URL.trim();
            if (!endpoint.endsWith("/solve")) {
                endpoint = endpoint.replaceAll("/+$", "") + "/solve";
            }

            JSONArray arr = new JSONArray();
            for (Ball b : balls) {
                JSONArray one = new JSONArray();
                one.put(b.id).put(b.color).put(b.x).put(b.y);
                arr.put(one);
            }
            JSONObject body = new JSONObject();
            body.put("balls", arr);
            body.put("cue", new JSONArray().put(cue.x).put(cue.y));
            body.put("table", new JSONArray().put(table.left).put(table.top)
                    .put(table.right).put(table.bottom));
            body.put("r", r);

            con = (HttpURLConnection) new URL(endpoint).openConnection();
            con.setRequestMethod("POST");
            con.setConnectTimeout(Config.BRAIN_TIMEOUT_MS);
            con.setReadTimeout(Config.BRAIN_TIMEOUT_MS);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (con.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject res = new JSONObject(sb.toString());
            if (!res.optBoolean("valid", false)) return null;
            Solver.Shot s = new Solver.Shot();
            s.angleDeg = (float) res.getDouble("angle");
            s.power = (float) res.getDouble("power");
            s.valid = true;
            s.why = res.optString("why", "brain");
            return s;
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private RailwayBrain() {}
}
EOF

cat > app/src/main/java/com/poollines/android/PoolAccessibilityService.java <<'EOF'
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
EOF

cat > app/src/main/java/com/poollines/android/AutoPlayController.java <<'EOF'
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
EOF

cat > app/src/main/java/com/poollines/android/ScreenCaptureService.java <<'EOF'
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
EOF

cat > app/src/main/java/com/poollines/android/PoolOverlayService.java <<'EOF'
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
EOF

cat > app/src/main/java/com/poollines/android/MainActivity.java <<'EOF'
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
        title.setText("POOL LINES\nAuto-play");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        log = new TextView(this);
        log.setTextColor(Color.rgb(175, 175, 175));
        log.setTextSize(12);
        log.setPadding(0, 40, 0, 30);
        log.setText("1. Overlay\n2. Accessibility\n3. Screen capture");
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
EOF

echo "==> GitHub Actions Workflow + Railway Server"
cat > .github/workflows/build.yml <<'EOF'
name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: gradle
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.4'
      - name: Build Debug APK
        run: gradle assembleDebug --stacktrace --no-daemon
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: pool-lines-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
EOF

cat > server/main.py <<'EOF'
import math
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class Req(BaseModel):
    balls: list[list[float]]
    cue: list[float]
    table: list[int]
    r: float

@app.get("/health")
def health():
    return {"ok": True}

def clear(balls, tid, x1, y1, x2, y2, r):
    dx, dy = x2 - x1, y2 - y1
    ln = math.hypot(dx, dy)
    if ln < 0.001:
        return True
    for bid, _c, bx, by in balls:
        if bid in (0, tid):
            continue
        u = max(0.0, min(1.0, ((bx - x1) * dx + (by - y1) * dy) / (ln * ln)))
        if math.hypot(bx - (x1 + dx * u), by - (y1 + dy * u)) < 2 * r * 0.96:
            return False
    return True

@app.post("/solve")
def solve(q: Req):
    l, t, rr, bb = q.table
    r = q.r
    cx, cy = q.cue
    pockets = [
        (l + r * 1.4, t + r * 1.4),
        ((l + rr) / 2, t + r * 0.6),
        (rr - r * 1.4, t + r * 1.4),
        (l + r * 1.4, bb - r * 1.4),
        ((l + rr) / 2, bb - r * 0.6),
        (rr - r * 1.4, bb - r * 1.4),
    ]
    best = None
    for bid, col, bx, by in q.balls:
        if bid == 0 or int(col) == 8:
            continue
        for px, py in pockets:
            pdx, pdy = px - bx, py - by
            pd = math.hypot(pdx, pdy)
            if pd < r:
                continue
            pdx /= pd
            pdy /= pd
            gx, gy = bx - pdx * 2 * r, by - pdy * 2 * r
            if not (l + r <= gx <= rr - r and t + r <= gy <= bb - r):
                continue
            cdx, cdy = gx - cx, gy - cy
            cd = math.hypot(cdx, cdy)
            if cd < r * 1.2:
                continue
            dot = max(-1.0, min(1.0, (cdx * pdx + cdy * pdy) / cd))
            cut = math.degrees(math.acos(dot))
            if cut > 75:
                continue
            if not clear(q.balls, bid, cx, cy, gx, gy, r):
                continue
            if not clear(q.balls, bid, bx, by, px, py, r):
                continue
            score = 1000 - cut * 5 - cd * 0.35 - pd * 0.2
            if best is None or score > best[0]:
                best = (score, math.degrees(math.atan2(cdy, cdx)),
                        min(1.0, max(0.3, 0.32 + (cd + pd) / 1400)))
    if best is not None:
        return {"valid": True, "angle": best[1], "power": best[2], "why": "pot"}
    cand = [(math.hypot(bx - cx, by - cy), bx, by)
            for bid, _c, bx, by in q.balls if bid != 0]
    if not cand:
        return {"valid": False}
    d, bx, by = min(cand)
    return {
        "valid": True,
        "angle": math.degrees(math.atan2(by - cy, bx - cx)),
        "power": min(0.9, max(0.35, 0.3 + d / 900)),
        "why": "fallback",
    }
EOF

cat > server/requirements.txt <<'EOF'
fastapi==0.115.0
uvicorn==0.30.6
pydantic==2.9.2
EOF

cat > server/Dockerfile <<'EOF'
FROM python:3.11-slim
WORKDIR /app
COPY server/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server/main.py .
EXPOSE 8080
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8080}"]
EOF

cat > railway.json <<'EOF'
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "DOCKERFILE",
    "dockerfilePath": "server/Dockerfile"
  },
  "deploy": {
    "startCommand": "sh -c 'uvicorn main:app --host 0.0.0.0 --port ${PORT:-8080}'",
    "healthcheckPath": "/health",
    "restartPolicyType": "ON_FAILURE",
    "restartPolicyMaxRetries": 5
  }
}
EOF

cat > README.md <<'EOF'
# Pool Lines — 8 Ball Pool Auto Aim (Non-Root)

Screen capture + on-device CV + physics solver + accessibility gesture injection.
Koi root nahi chahiye.

## Build (GitHub Actions)
GitHub pe push karo -> Actions -> **Build APK** -> `pool-lines-apk` artifact download karo.

## Setup (Order Important)
1. Install APK, app kholo, **START** dabao.
2. Overlay permission allow karo.
3. Settings -> Accessibility -> **Pool Lines** ON karo.
4. START dobara dabao -> Screen Capture allow karo.
5. 8 Ball Pool practice table kholo.
6. Panel se **CALIBRAR MESA** -> 4 yellow corners felt ke andar wale corners pe drag karo -> **GUARDAR MESA**.
7. **SHOOT NOW** se ek shot test karo.
8. Angle ulti taraf ja raha ho to `Config.K_INVERT` true karke rebuild karo.
9. Sahi lage to **AUTO: ON**.
EOF

echo "==> Gradle Wrapper check"
if command -v gradle >/dev/null 2>&1; then
  gradle wrapper --gradle-version 8.4
  chmod +x gradlew
fi

echo "==> Git Commit Setup"
if [ ! -d .git ]; then
  git init
  git branch -M main
fi
git add -A
git commit -m "fix: full auto-play android overlay + physics solver + railway server" || true
echo "DONE. Repo ready to push to GitHub!"
