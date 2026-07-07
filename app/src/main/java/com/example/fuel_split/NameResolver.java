package com.example.fuel_split;

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
