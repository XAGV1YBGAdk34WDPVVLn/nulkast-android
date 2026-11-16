package io.nulkast.ui;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.TextView;

/**
 * NulKast Crisp Studio OLED Design System & Component Kit (`packages/ui-theme`).
 * Implements [[android-ux-design-expert]] 4-Tier Luminance Hierarchy, 14% translucent
 * semantic badges, edge-to-edge dark system chrome, LruCache app icon loader, and
 * sw600dp foldable posture detection (Note 10+ 5G vs Pixel Fold felix).
 */
public final class NulKastTheme {
    // 4-Tier Crisp Studio OLED Surface Hierarchy
    public static final int BG_PRIMARY = Color.parseColor("#070A12");   // Level 0: Abyss Root Canvas
    public static final int BG_SECONDARY = Color.parseColor("#0E1422"); // Level 1: Pinned Decks & Nav Bar
    public static final int BG_CARD = Color.parseColor("#121A2B");      // Level 2: Elevated Cards
    public static final int BG_INPUT = Color.parseColor("#172238");     // Level 3: Interactive Rows, Tiles & Inputs
    public static final int BORDER_COLOR = Color.parseColor("#1E2B45"); // 1dp Structural Hairline
    public static final int BORDER_ACTIVE = Color.parseColor("#00E5FF");// 1.5dp Active Focus Border

    // Typography Hierarchy (WCAG 2.2 AA/AAA)
    public static final int TEXT_PRIMARY = Color.parseColor("#F8FAFC");
    public static final int TEXT_SECONDARY = Color.parseColor("#94A3B8");
    public static final int TEXT_MUTED = Color.parseColor("#64748B");

    // Semantic Accents & 14% Alpha Translucent Fills
    public static final int ACCENT_CYAN = Color.parseColor("#00E5FF");
    public static final int ACCENT_CYAN_DIM = 0x2400E5FF;

    public static final int ACCENT_GREEN = Color.parseColor("#10B981");
    public static final int ACCENT_GREEN_DIM = 0x2410B981;

    public static final int ACCENT_AMBER = Color.parseColor("#F59E0B");
    public static final int ACCENT_AMBER_DIM = 0x24F59E0B;

    public static final int ACCENT_RED = Color.parseColor("#F43F5E");
    public static final int ACCENT_RED_DIM = 0x24F43F5E;

    private static final LruCache<String, Drawable> ICON_CACHE = new LruCache<String, Drawable>(180);

    private NulKastTheme() {}

    public static void applyWindowChrome(Activity activity) {
        if (activity == null) return;
        try {
            Window w = activity.getWindow();
            if (w == null) return;
            w.getDecorView().setBackgroundColor(BG_PRIMARY);
            w.setStatusBarColor(BG_PRIMARY);
            w.setNavigationBarColor(BG_SECONDARY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController ic = w.getInsetsController();
                if (ic != null) {
                    ic.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    );
                }
            }
        } catch (Throwable ignored) {}
    }

    public static int dp(Context ctx, float dpVal) {
        return Math.round(
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dpVal,
                ctx.getResources().getDisplayMetrics()
            )
        );
    }

    public static GradientDrawable cardDrawable(Context ctx, int fillColor, int strokeColor, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setStroke(Math.max(1, dp(ctx, 1f)), strokeColor);
        return gd;
    }

    public static GradientDrawable pillDrawable(Context ctx, int fillColor, int strokeColor) {
        return cardDrawable(ctx, fillColor, strokeColor, 999f);
    }

    public static TextView createSemanticBadge(Context ctx, String text, int fgColor, int bgDimColor) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(fgColor);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(ctx, 8), dp(ctx, 3), dp(ctx, 8), dp(ctx, 3));
        tv.setBackground(pillDrawable(ctx, bgDimColor, fgColor));
        return tv;
    }

    public static Drawable loadAppIcon(Context ctx, String packageName) {
        if (ctx == null || packageName == null || packageName.isEmpty()) return null;
        Drawable cached = ICON_CACHE.get(packageName);
        if (cached != null) return cached;
        try {
            PackageManager pm = ctx.getPackageManager();
            Drawable d = pm.getApplicationIcon(packageName);
            if (d != null) {
                ICON_CACHE.put(packageName, d);
                return d;
            }
        } catch (Throwable ignored) {}
        return ctx.getResources().getDrawable(android.R.drawable.sym_def_app_icon, null);
    }

    public static String resolveAppLabel(Context ctx, String packageName) {
        if (ctx == null || packageName == null || packageName.isEmpty()) return "System";
        if ("system-dns".equals(packageName)) return "Android OS Resolver";
        if (packageName.contains("[IPC]")) return packageName;
        try {
            PackageManager pm = ctx.getPackageManager();
            CharSequence cs = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
            if (cs != null && cs.length() > 0) return cs.toString();
        } catch (Throwable ignored) {}
        return packageName;
    }

    public static boolean isUnfoldedDualPane(Context ctx) {
        Configuration cfg = ctx.getResources().getConfiguration();
        return cfg.smallestScreenWidthDp >= 600 || cfg.screenWidthDp >= 600;
    }

    public static int recommendedGridColumns(Context ctx) {
        return isUnfoldedDualPane(ctx) ? 6 : 4;
    }
}
