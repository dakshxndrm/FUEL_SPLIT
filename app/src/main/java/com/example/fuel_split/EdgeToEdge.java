package com.example.fuel_split;

import android.app.Activity;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shared edge-to-edge inset handling for header/bottom-bar screens. */
public final class EdgeToEdge {

    private EdgeToEdge() {}

    /** Pad the header below the status bar and the bottom bar above the nav bar. */
    public static void apply(Activity activity, int rootId, int headerId, int bottomBarId) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        View header    = activity.findViewById(headerId);
        View bottomBar = activity.findViewById(bottomBarId);
        final int headerTop    = header.getPaddingTop();
        final int bottomBottom = bottomBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(rootId), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            header.setPadding(header.getPaddingLeft(), headerTop + bars.top,
                              header.getPaddingRight(), header.getPaddingBottom());
            bottomBar.setPadding(bottomBar.getPaddingLeft(), bottomBar.getPaddingTop(),
                                 bottomBar.getPaddingRight(), bottomBottom + bars.bottom);
            return insets;
        });
    }
}
