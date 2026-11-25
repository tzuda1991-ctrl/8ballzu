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
