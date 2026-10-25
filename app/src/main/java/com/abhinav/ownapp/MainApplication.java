package com.abhinav.ownapp;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (isExemptedActivity(activity)) return;
                setupGlobalPadding(activity);
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (isExemptedActivity(activity)) return;
                applyGlobalStatusBarTheme(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (isExemptedActivity(activity)) return;
                applyGlobalStatusBarTheme(activity);
            }

            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    private void setupGlobalPadding(Activity activity) {
        Window window = activity.getWindow();
        if (window != null) {
            View decorView = window.getDecorView();
            ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

                ViewGroup contentView = activity.findViewById(android.R.id.content);

                // --- THE FIX: ISOLATE PADDING BEHAVIORS ---
                if (activity instanceof MainActivity) {
                    // 1. Special padding ONLY for MainActivity to allow the full-screen circular reveal
                    View appLayout = activity.findViewById(R.id.main_root);
                    if (appLayout != null) {
                        appLayout.setFitsSystemWindows(false);
                        appLayout.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    }
                    if (contentView != null) {
                        contentView.setPadding(0, 0, 0, 0); // Keep root full-screen for the wave overlay
                    }
                } else {
                    // 2. ORIGINAL PADDING BEHAVIOR for Collage Studio, Audio Studio, PDF Studio, etc.
                    if (contentView != null) {
                        contentView.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    }
                }

                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    private void applyGlobalStatusBarTheme(Activity activity) {
        Window window = activity.getWindow();
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false);

            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(layoutParams);
            }

            SharedPreferences appPrefs = activity.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            int themeState = appPrefs.getInt("app_theme_state", 1);

            int topBarBg;
            if (themeState == 0) topBarBg = Color.WHITE;
            else if (themeState == 1) topBarBg = Color.parseColor("#1C1C1E");
            else topBarBg = Color.parseColor("#000000");

            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }

            window.setBackgroundDrawable(new ColorDrawable(topBarBg));

            WindowInsetsControllerCompat windowController = WindowCompat.getInsetsController(window, window.getDecorView());
            if (windowController != null) {
                if (themeState == 0) {
                    windowController.setAppearanceLightStatusBars(true);
                    windowController.setAppearanceLightNavigationBars(true);
                } else {
                    windowController.setAppearanceLightStatusBars(false);
                    windowController.setAppearanceLightNavigationBars(false);
                }
            }
        }
    }

    private boolean isExemptedActivity(Activity activity) {
        return activity instanceof ThriveActivity ||
                activity instanceof DriveOnActivity ||
                activity instanceof GlobeGameActivity ||
                activity instanceof GamesGalleryActivity ||
                activity instanceof ToolsGalleryActivity ||
                activity instanceof UtilitiesGalleryActivity ||
                activity instanceof WidgetGalleryActivity;
    }
}