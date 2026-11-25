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
