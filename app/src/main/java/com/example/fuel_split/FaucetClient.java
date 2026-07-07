package com.example.fuel_split;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FaucetClient {

    private static final String FAUCET_URL = Config.FAUCET_URL;

    public static void fundWallet(String address) throws Exception {
        URL url = new URL(FAUCET_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(60_000);
            conn.setReadTimeout(60_000);

            byte[] body = ("{\"address\":\"" + address + "\"}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                InputStream err = conn.getErrorStream();
                String msg = err != null ? readStream(err) : "HTTP " + code;
                if (isTreasuryEmpty(msg)) {
                    throw new Exception(
                            "Demo faucet is temporarily out of funds. Please try again later.");
                }
                throw new Exception("Faucet error: " + msg);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** True when the faucet's error indicates its treasury wallet has run dry. */
    private static boolean isTreasuryEmpty(String errorBody) {
        String s = errorBody.toLowerCase();
        return s.contains("insufficient funds")
                || s.contains("insufficient balance")
                || s.contains("treasury")
                || s.contains("out of funds");
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
