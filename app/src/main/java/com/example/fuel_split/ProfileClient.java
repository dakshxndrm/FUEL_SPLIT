package com.example.fuel_split;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ProfileClient {

    private static final String BASE = Config.PROFILE_BASE;

    public static String createProfile(String address, String displayName) throws Exception {
        URL url = new URL(BASE + "/api/profile");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(60_000);
            conn.setReadTimeout(60_000);

            JSONObject body = new JSONObject();
            body.put("address", address);
            body.put("displayName", displayName);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int status = conn.getResponseCode();
            if (status != 200) {
                InputStream err = conn.getErrorStream();
                String msg = err != null ? readStream(err) : "HTTP " + status;
                throw new Exception("createProfile failed: " + msg);
            }
            return new JSONObject(readStream(conn.getInputStream())).getString("code");
        } finally {
            conn.disconnect();
        }
    }

    // Returns {address, displayName}; throws "No user with code X" on 404.
    public static String[] lookupByCode(String code) throws Exception {
        URL url = new URL(BASE + "/api/lookup?code=" + code);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(60_000);
            conn.setReadTimeout(60_000);

            int status = conn.getResponseCode();
            if (status == 404) throw new Exception("No user with code " + code);
            if (status != 200) {
                InputStream err = conn.getErrorStream();
                String msg = err != null ? readStream(err) : "HTTP " + status;
                throw new Exception("lookupByCode failed: " + msg);
            }
            JSONObject obj = new JSONObject(readStream(conn.getInputStream()));
            return new String[]{obj.getString("address"), obj.getString("displayName")};
        } finally {
            conn.disconnect();
        }
    }

    // Returns {code, displayName}; returns null on 404.
    public static String[] lookupByAddress(String address) throws Exception {
        URL url = new URL(BASE + "/api/lookup?address=" + address);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(60_000);
            conn.setReadTimeout(60_000);

            int status = conn.getResponseCode();
            if (status == 404) return null;
            if (status != 200) {
                InputStream err = conn.getErrorStream();
                String msg = err != null ? readStream(err) : "HTTP " + status;
                throw new Exception("lookupByAddress failed: " + msg);
            }
            JSONObject obj = new JSONObject(readStream(conn.getInputStream()));
            return new String[]{obj.getString("code"), obj.getString("displayName")};
        } finally {
            conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        int n;
        while ((n = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
