package com.example.fuel_split;

import android.widget.TextView;
import java.util.concurrent.ConcurrentHashMap;

public final class NameResolver {

    // address (lowercase) → display name
    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private NameResolver() {}

    /**
     * Returns the cached display name for an address, or a shortened address as fallback.
     * Never blocks — safe to call on the UI thread.
     */
    public static String nameFor(String address) {
        if (address == null || address.isEmpty()) return "Unknown";
        String cached = cache.get(address.toLowerCase());
        return cached != null ? cached : shortAddr(address);
    }

    // ARCHIVED: resolveAsync() — all call sites do inline background lookups instead, never called
    // public static void resolveAsync(String address, TextView tv) {
    //     if (address == null || address.isEmpty()) { tv.setText("Unknown"); return; }
    //     String key    = address.toLowerCase();
    //     String cached = cache.get(key);
    //     if (cached != null) { tv.setText(cached); return; }
    //     tv.setText(shortAddr(address));
    //     new Thread(() -> {
    //         try {
    //             String[] result = ProfileClient.lookupByAddress(address);
    //             if (result != null && result.length > 1 && !result[1].isEmpty()) {
    //                 cache.put(key, result[1]);
    //                 tv.post(() -> tv.setText(result[1]));
    //             }
    //         } catch (Exception ignored) {}
    //     }).start();
    // }

    /**
     * Seed the cache with a name already known (e.g. from lookupByCode results).
     * Safe to call from any thread.
     */
    public static void seed(String address, String name) {
        if (address != null && !address.isEmpty() && name != null && !name.isEmpty()) {
            cache.put(address.toLowerCase(), name);
        }
    }

    private static String shortAddr(String addr) {
        if (addr == null || addr.length() < 12) return addr != null ? addr : "";
        return addr.substring(0, 8) + "…" + addr.substring(addr.length() - 4);
    }
}
