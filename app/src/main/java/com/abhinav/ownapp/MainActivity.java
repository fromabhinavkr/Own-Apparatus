package com.abhinav.ownapp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

@SuppressWarnings("all")
public class MainActivity extends AppCompatActivity {

    public static Bitmap sThemeSnapshot = null;
    public static int sRevealX = -1, sRevealY = -1, sPillX = -1, sPillY = -1, sPillWidth = -1, sPillHeight = -1;

    private static final int REQ_NET_NOTIF = 201;
    private static final String PREF_NET_SPEED = "net_speed_enabled";

    private TextClock tvTime;
    private Switch switchNetSpeed;
    private TextView tvNetSpeedLbl;
    private int currentThemeTextColor;
    private ValueAnimator gradientAnimator;

    private final BroadcastReceiver alarmStateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if ("com.abhinav.ownapp.ALARM_UPDATED".equals(intent.getAction())) refreshAlarmGradientState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        int tempThemeState = prefs.getInt("app_theme_state", -1);
        if (tempThemeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            tempThemeState = oldDark ? 1 : 0;
            prefs.edit().putInt("app_theme_state", tempThemeState).apply();
        }
        final int themeState = tempThemeState;

        currentThemeTextColor = (themeState == 0) ? Color.parseColor("#333333") : Color.WHITE;
        AppCompatDelegate.setDefaultNightMode(themeState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        Window window = getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, true);
        setContentView(R.layout.activity_main);

        View gridScrollView = findViewById(R.id.grid_scroll_view);
        if (gridScrollView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(gridScrollView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom + 48);
                return insets;
            });
        }

        boolean isDarkTheme = (themeState != 0);
        DeviceStatsHelper.setupDashboard(this, isDarkTheme);

        ImageButton themeToggleBtn = findViewById(R.id.btn_app_theme_toggle);
        LinearLayout topCapsule = findViewById(R.id.top_capsule);
        FrameLayout themeTogglePill = findViewById(R.id.theme_toggle_pill);
        LinearLayout timePill = findViewById(R.id.time_pill);
        LinearLayout datePill = findViewById(R.id.date_pill);
        LinearLayout netSpeedPill = findViewById(R.id.net_speed_pill);

        tvTime = findViewById(R.id.tvTime);
        TextClock tvDate = findViewById(R.id.tvDate);
        switchNetSpeed = findViewById(R.id.switch_net_speed);
        tvNetSpeedLbl = findViewById(R.id.tvNetSpeedLbl);

        if (themeState == 0) {
            themeToggleBtn.setImageResource(R.drawable.ic_sun);
            themeToggleBtn.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN);
        } else if (themeState == 1) {
            themeToggleBtn.setImageResource(R.drawable.ic_moon);
            themeToggleBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } else {
            themeToggleBtn.setImageResource(android.R.drawable.star_on);
            themeToggleBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        themeToggleBtn.setOnClickListener(v -> {
            if (sThemeSnapshot != null) return;
            themeToggleBtn.setEnabled(false);

            int[] location = new int[2];
            themeTogglePill.getLocationInWindow(location);
            sPillX = location[0]; sPillY = location[1];
            sPillWidth = themeTogglePill.getWidth(); sPillHeight = themeTogglePill.getHeight();
            sRevealX = sPillX + (sPillWidth / 2); sRevealY = sPillY + (sPillHeight / 2);

            ViewGroup root = findViewById(android.R.id.content);
            sThemeSnapshot = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sThemeSnapshot);
            root.draw(canvas);

            int currentState = prefs.getInt("app_theme_state", 1);
            int nextState = (currentState + 1) % 3;
            prefs.edit().putInt("app_theme_state", nextState).putBoolean(SnakeWidget.PREF_IS_DARK, nextState != 0).apply();

            updateAllWidgets();
            AppCompatDelegate.setDefaultNightMode(nextState == 0 ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            recreate();
            overridePendingTransition(0, 0);
        });

        LinearLayout btnPlaceWidget = findViewById(R.id.btnPlaceWidget);
        LinearLayout btnGames = findViewById(R.id.btnGames);
        LinearLayout btnTools = findViewById(R.id.btnTools);
        LinearLayout btnUtilities = findViewById(R.id.btnUtilities);

        RelativeLayout ramCardBg = findViewById(R.id.ramCardBg);
        LinearLayout storageCardBg = findViewById(R.id.storageCardBg);
        LinearLayout batteryCardBg = findViewById(R.id.batteryCardBg);

        if (btnPlaceWidget != null && btnGames != null && btnTools != null && btnUtilities != null) {
            ColorStateList themeBg, innerBg, dashCardBg;
            int secondaryText, rootBg;

            if (themeState == 0) {
                rootBg = Color.WHITE;
                themeBg = ColorStateList.valueOf(Color.parseColor("#F2F2F7"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#FFFFFF"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#F2F2F7"));
                currentThemeTextColor = Color.parseColor("#333333");
                secondaryText = Color.parseColor("#666666");
            } else if (themeState == 1) {
                rootBg = Color.parseColor("#1C1C1E");
                themeBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#2C2C2E"));
                currentThemeTextColor = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            } else {
                rootBg = Color.parseColor("#000000");
                themeBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                innerBg = ColorStateList.valueOf(Color.parseColor("#000000"));
                dashCardBg = ColorStateList.valueOf(Color.parseColor("#1C1C1E"));
                currentThemeTextColor = Color.WHITE;
                secondaryText = Color.parseColor("#BBBBBB");
            }

            findViewById(R.id.main_root).setBackgroundColor(rootBg);

            GradientDrawable outerCapsuleGd = new GradientDrawable();
            outerCapsuleGd.setColor(themeBg.getDefaultColor());
            outerCapsuleGd.setCornerRadius(90f);
            topCapsule.setBackground(outerCapsuleGd);
            topCapsule.setElevation(0f);
            topCapsule.setClipToOutline(true);

            GradientDrawable innerCircleGd = new GradientDrawable();
            innerCircleGd.setShape(GradientDrawable.OVAL);
            innerCircleGd.setColor(innerBg.getDefaultColor());
            themeTogglePill.setBackground(innerCircleGd);
            themeTogglePill.setElevation(0f);
            themeTogglePill.setClipToOutline(true);

            GradientDrawable timePillGd = new GradientDrawable();
            timePillGd.setCornerRadius(200f);
            timePillGd.setColor(innerBg.getDefaultColor());
            timePill.setBackground(timePillGd);
            timePill.setElevation(0f);
            timePill.setClipToOutline(true);

            GradientDrawable datePillGd = new GradientDrawable();
            datePillGd.setCornerRadius(200f);
            datePillGd.setColor(innerBg.getDefaultColor());
            datePill.setBackground(datePillGd);
            datePill.setElevation(0f);
            datePill.setClipToOutline(true);

            // Style Network Speed Pill for Light / Dark / AMOLED
            if (netSpeedPill != null) {
                GradientDrawable netPillGd = new GradientDrawable();
                netPillGd.setCornerRadius(60f);
                netPillGd.setColor(innerBg.getDefaultColor());
                netSpeedPill.setBackground(netPillGd);
                netSpeedPill.setElevation(0f);
                netSpeedPill.setClipToOutline(true);
            }

            if (switchNetSpeed != null) {
                boolean isNetOn = prefs.getBoolean(PREF_NET_SPEED, false);
                switchNetSpeed.setOnCheckedChangeListener(null);
                switchNetSpeed.setChecked(isNetOn);
                styleNetSwitch(themeState, isNetOn);
                if (isNetOn) startNetworkSpeedService();

                switchNetSpeed.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            switchNetSpeed.setChecked(false);
                            styleNetSwitch(themeState, false);
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NET_NOTIF);
                            return;
                        }
                        prefs.edit().putBoolean(PREF_NET_SPEED, true).apply();
                        styleNetSwitch(themeState, true);
                        startNetworkSpeedService();
                    } else {
                        prefs.edit().putBoolean(PREF_NET_SPEED, false).apply();
                        styleNetSwitch(themeState, false);
                        stopNetworkSpeedService();
                    }
                });
                if (netSpeedPill != null) netSpeedPill.setOnClickListener(v -> switchNetSpeed.toggle());
            }

            tvTime.setTextColor(currentThemeTextColor);
            tvDate.setTextColor(secondaryText);

            btnPlaceWidget.setBackgroundTintList(themeBg);
            btnGames.setBackgroundTintList(themeBg);
            btnTools.setBackgroundTintList(themeBg);
            btnUtilities.setBackgroundTintList(themeBg);

            TextView tvWidgetText = findViewById(R.id.tvWidgetText), tvGamesText = findViewById(R.id.tvGamesText), tvToolsText = findViewById(R.id.tvToolsText), tvUtilitiesText = findViewById(R.id.tvUtilitiesText);
            TextView tvWidgetSub = findViewById(R.id.tvWidgetSub), tvGamesSub = findViewById(R.id.tvGamesSub), tvToolsSub = findViewById(R.id.tvToolsSub), tvUtilitiesSub = findViewById(R.id.tvUtilitiesSub);

            tvWidgetText.setTextColor(currentThemeTextColor); tvGamesText.setTextColor(currentThemeTextColor); tvToolsText.setTextColor(currentThemeTextColor); tvUtilitiesText.setTextColor(currentThemeTextColor);
            tvWidgetSub.setTextColor(secondaryText); tvGamesSub.setTextColor(secondaryText); tvToolsSub.setTextColor(secondaryText); tvUtilitiesSub.setTextColor(secondaryText);

            if (ramCardBg != null) ramCardBg.setBackgroundTintList(dashCardBg);
            if (storageCardBg != null) storageCardBg.setBackgroundTintList(dashCardBg);
            if (batteryCardBg != null) batteryCardBg.setBackgroundTintList(dashCardBg);

            TextView tvRamTitle = findViewById(R.id.tvRamTitle), tvRamUsed = findViewById(R.id.tvRamUsed), tvRamTotal = findViewById(R.id.tvRamTotal), tvRamFree = findViewById(R.id.tvRamFree);
            TextView tvStorageTitle = findViewById(R.id.tvStorageTitle), tvBatteryTitle = findViewById(R.id.tvBatteryTitle);

            if (tvRamTitle != null) tvRamTitle.setTextColor(currentThemeTextColor);
            if (tvRamUsed != null) tvRamUsed.setTextColor(currentThemeTextColor);
            if (tvStorageTitle != null) tvStorageTitle.setTextColor(currentThemeTextColor);
            if (tvBatteryTitle != null) tvBatteryTitle.setTextColor(currentThemeTextColor);
            if (tvRamTotal != null) tvRamTotal.setTextColor(secondaryText);
            if (tvRamFree != null) tvRamFree.setTextColor(secondaryText);

            RamGraphView ramGraphView = findViewById(R.id.ramGraphView);
            if (ramGraphView != null) ramGraphView.setThemeState(themeState);

            if (sThemeSnapshot != null) {
                ViewGroup content = findViewById(android.R.id.content);
                View newLayout = findViewById(R.id.main_root);

                ImageView oldUiImage = new ImageView(this);
                oldUiImage.setImageBitmap(sThemeSnapshot);
                oldUiImage.setScaleType(ImageView.ScaleType.FIT_XY);
                oldUiImage.setClickable(false); oldUiImage.setFocusable(false); oldUiImage.setEnabled(false);
                content.addView(oldUiImage, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                final int prevState = (themeState + 2) % 3;
                FrameLayout clonePill = new FrameLayout(this);
                clonePill.setClickable(false); clonePill.setFocusable(false); clonePill.setEnabled(false);
                GradientDrawable cloneGd = new GradientDrawable();
                cloneGd.setShape(GradientDrawable.OVAL);

                if (prevState == 0) cloneGd.setColor(Color.parseColor("#FFFFFF"));
                else if (prevState == 1) cloneGd.setColor(Color.parseColor("#1C1C1E"));
                else cloneGd.setColor(Color.parseColor("#000000"));
                clonePill.setBackground(cloneGd);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    clonePill.setElevation(10000f);
                    newLayout.setElevation(10f);
                }

                ImageButton cloneIcon = new ImageButton(this);
                cloneIcon.setBackgroundResource(0);
                cloneIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cloneIcon.setPadding(themeToggleBtn.getPaddingLeft(), themeToggleBtn.getPaddingTop(), themeToggleBtn.getPaddingRight(), themeToggleBtn.getPaddingBottom());
                cloneIcon.setClickable(false); cloneIcon.setFocusable(false);

                if (prevState == 0) { cloneIcon.setImageResource(R.drawable.ic_sun); cloneIcon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN); }
                else if (prevState == 1) { cloneIcon.setImageResource(R.drawable.ic_moon); cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); }
                else { cloneIcon.setImageResource(android.R.drawable.star_on); cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); }

                clonePill.addView(cloneIcon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

                FrameLayout.LayoutParams cloneParams = new FrameLayout.LayoutParams(sPillWidth, sPillHeight);
                cloneParams.leftMargin = sPillX; cloneParams.topMargin = sPillY;
                content.addView(clonePill, cloneParams);

                newLayout.setVisibility(View.INVISIBLE);
                themeTogglePill.setVisibility(View.INVISIBLE);

                android.os.Handler cleanupHandler = new android.os.Handler();
                Runnable cleanupRunnable = () -> {
                    try {
                        content.removeView(oldUiImage); content.removeView(clonePill);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) newLayout.setElevation(0f);
                    } catch (Exception ignored) {}
                    sThemeSnapshot = null;
                    themeTogglePill.setVisibility(View.VISIBLE);
                };
                cleanupHandler.postDelayed(cleanupRunnable, 400);

                newLayout.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        newLayout.getViewTreeObserver().removeOnPreDrawListener(this);
                        newLayout.setVisibility(View.VISIBLE);
                        float finalRadius = (float) Math.hypot(Math.max(sRevealX, newLayout.getWidth() - sRevealX), Math.max(sRevealY, newLayout.getHeight() - sRevealY));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Animator anim = ViewAnimationUtils.createCircularReveal(newLayout, sRevealX, sRevealY, 0f, finalRadius);
                            anim.setDuration(600);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override public void onAnimationEnd(Animator animation) {
                                    cleanupHandler.removeCallbacks(cleanupRunnable);
                                    try { content.removeView(oldUiImage); content.removeView(clonePill); newLayout.setElevation(0f); } catch (Exception ignored) {}
                                    sThemeSnapshot = null;
                                    themeTogglePill.setVisibility(View.VISIBLE);
                                }
                            });
                            anim.start();

                            clonePill.animate().rotation(180f).scaleX(0f).scaleY(0f).setDuration(300).withEndAction(() -> {
                                if (themeState == 0) { cloneIcon.setImageResource(R.drawable.ic_sun); cloneIcon.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN); cloneGd.setColor(Color.parseColor("#FFFFFF")); }
                                else if (themeState == 1) { cloneIcon.setImageResource(R.drawable.ic_moon); cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); cloneGd.setColor(Color.parseColor("#1C1C1E")); }
                                else { cloneIcon.setImageResource(android.R.drawable.star_on); cloneIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN); cloneGd.setColor(Color.parseColor("#000000")); }
                                clonePill.setBackground(cloneGd);
                                clonePill.setRotation(-180f);
                                clonePill.animate().rotation(0f).scaleX(1f).scaleY(1f).setDuration(300).start();
                            }).start();
                        } else {
                            cleanupHandler.removeCallbacks(cleanupRunnable);
                            try { content.removeView(oldUiImage); content.removeView(clonePill); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) newLayout.setElevation(0f); } catch (Exception ignored) {}
                            sThemeSnapshot = null;
                            themeTogglePill.setVisibility(View.VISIBLE);
                        }
                        return true;
                    }
                });
            }

            btnPlaceWidget.setOnClickListener(v -> launchGallery(btnPlaceWidget, WidgetGalleryActivity.class));
            btnGames.setOnClickListener(v -> launchGallery(btnGames, GamesGalleryActivity.class));
            btnTools.setOnClickListener(v -> launchGallery(btnTools, ToolsGalleryActivity.class));
            btnUtilities.setOnClickListener(v -> launchGallery(btnUtilities, UtilitiesGalleryActivity.class));
        }

        if (timePill != null) {
            timePill.setClickable(true); timePill.setFocusable(true);
            timePill.setOnClickListener(v -> showAlarmsListDialog(themeState));
        }

        if (datePill != null) {
            datePill.setClickable(true); datePill.setFocusable(true);
            datePill.setOnClickListener(v -> {
                Dialog dialog = new Dialog(MainActivity.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

                LinearLayout layout = new LinearLayout(MainActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                int pad = dp(16);
                layout.setPadding(pad, pad, pad, pad);

                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(dp(32));
                int popupBg = (themeState == 0) ? Color.parseColor("#E6FFFFFF") : ((themeState == 1) ? Color.parseColor("#E62C2C2E") : Color.parseColor("#E61C1C1E"));
                gd.setColor(popupBg);
                layout.setBackground(gd);

                TextView titleView = new TextView(MainActivity.this);
                titleView.setText("Calendar"); titleView.setTextSize(18f);
                titleView.setTypeface(null, android.graphics.Typeface.BOLD);
                titleView.setGravity(Gravity.CENTER); titleView.setTextColor(currentThemeTextColor);
                titleView.setPadding(0, 0, 0, pad);
                layout.addView(titleView);

                CalendarView calendarView = new CalendarView(MainActivity.this);
                layout.addView(calendarView);

                dialog.setContentView(layout);
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9), ViewGroup.LayoutParams.WRAP_CONTENT);
                    dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
                }
                dialog.show();
            });
        }
    }

    private void launchGallery(View card, Class<?> targetActivity) {
        int[] loc = new int[2];
        card.getLocationOnScreen(loc);
        Intent intent = new Intent(MainActivity.this, targetActivity);
        intent.putExtra("REVEAL_X", loc[0] + (card.getWidth() / 2));
        intent.putExtra("REVEAL_Y", loc[1] + (card.getHeight() / 2));
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // ======================================================================
    // NETWORK SPEED DETECTOR HELPERS (Theme-Aware Switch & Service Control)
    // ======================================================================
    private void styleNetSwitch(int themeState, boolean isChecked) {
        int activeColor = Color.parseColor("#2ECC71");
        int inactiveThumb = (themeState == 0) ? Color.parseColor("#999999") : Color.parseColor("#8E8E93");
        int inactiveTrack = (themeState == 0) ? Color.parseColor("#E0E0E5") : ((themeState == 1) ? Color.parseColor("#3A3A3C") : Color.parseColor("#222224"));

        if (switchNetSpeed != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            switchNetSpeed.setThumbTintList(ColorStateList.valueOf(isChecked ? activeColor : inactiveThumb));
            switchNetSpeed.setTrackTintList(ColorStateList.valueOf(isChecked ? Color.parseColor("#552ECC71") : inactiveTrack));
        }
        if (tvNetSpeedLbl != null) {
            tvNetSpeedLbl.setText(isChecked ? "NET ON" : "NET OFF");
            tvNetSpeedLbl.setTextColor(isChecked ? activeColor : ((themeState == 0) ? Color.parseColor("#666666") : Color.parseColor("#BBBBBB")));
        }
    }

    private void startNetworkSpeedService() {
        Intent serviceIntent = new Intent(this, NetworkSpeedService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent);
        else startService(serviceIntent);
    }

    private void stopNetworkSpeedService() {
        stopService(new Intent(this, NetworkSpeedService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NET_NOTIF) {
            SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
            int themeState = prefs.getInt("app_theme_state", 1);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prefs.edit().putBoolean(PREF_NET_SPEED, true).apply();
                if (switchNetSpeed != null) switchNetSpeed.setChecked(true);
                styleNetSwitch(themeState, true);
                startNetworkSpeedService();
            } else {
                Toast.makeText(this, "Notification permission required for status bar network speed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmStateReceiver, new IntentFilter("com.abhinav.ownapp.ALARM_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(alarmStateReceiver, new IntentFilter("com.abhinav.ownapp.ALARM_UPDATED"));
        }
        refreshAlarmGradientState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(alarmStateReceiver); } catch (IllegalArgumentException ignored) {}
    }

    private int dp(int px) { return (int) (px * getResources().getDisplayMetrics().density + 0.5f); }

    private void animateDialogEnter(View view) {
        view.setAlpha(1f); view.setTranslationY(0f); view.setScaleX(0.92f); view.setScaleY(0.92f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animateDialogExit(Dialog dialog, View view) {
        view.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(200).setInterpolator(new AccelerateInterpolator(1.5f)).withEndAction(dialog::dismiss).start();
    }

    // ======================================================================
    // 1. MULTI-ALARM UI (TRUE 0ms BLINK OVERLAY)
    // ======================================================================
    private void showAlarmsListDialog(int themeState) {
        ViewGroup content = findViewById(android.R.id.content);

        FrameLayout overlay = new FrameLayout(this) {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    content.removeView(this);
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { overlay.setElevation(100f); overlay.setTranslationZ(100f); }
        overlay.setBackgroundColor(Color.parseColor("#60000000"));
        overlay.setClickable(true); overlay.setFocusable(true); overlay.setFocusableInTouchMode(true);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, pad);

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(32));
        int popupBg = (themeState == 0) ? Color.parseColor("#F2F2F7") : ((themeState == 1) ? Color.parseColor("#1C1C1E") : Color.parseColor("#000000"));
        gd.setColor(popupBg);
        layout.setBackground(gd);
        layout.setOnClickListener(v -> {});

        TextView titleView = new TextView(this);
        titleView.setText("Active Alarms"); titleView.setTextSize(18f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER); titleView.setTextColor(currentThemeTextColor);
        titleView.setPadding(0, 0, 0, pad);
        layout.addView(titleView);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
            if (alarmsArray.length() == 0) {
                TextView noAlarmText = new TextView(this);
                noAlarmText.setText("No active alarms.");
                noAlarmText.setTextColor(themeState == 0 ? Color.DKGRAY : Color.LTGRAY);
                noAlarmText.setGravity(Gravity.CENTER);
                noAlarmText.setPadding(0, pad, 0, pad);
                listContainer.addView(noAlarmText);
            }

            int cardBg = (themeState == 0) ? Color.WHITE : Color.parseColor("#2C2C2E");
            for (int i = 0; i < alarmsArray.length(); i++) {
                JSONObject alarmObj = alarmsArray.getJSONObject(i);
                int id = alarmObj.getInt("id");
                long timeInMillis = alarmObj.getLong("time");
                String note = alarmObj.getString("note");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(pad, pad, pad, pad);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, dp(16));
                card.setLayoutParams(cardParams);

                GradientDrawable cardGd = new GradientDrawable();
                cardGd.setColor(cardBg); cardGd.setCornerRadius(dp(24));
                card.setBackground(cardGd);

                LinearLayout textStack = new LinearLayout(this);
                textStack.setOrientation(LinearLayout.VERTICAL);
                textStack.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView timeText = new TextView(this);
                boolean is24Hour = DateFormat.is24HourFormat(this);
                SimpleDateFormat sdf = new SimpleDateFormat(is24Hour ? "EEE, MMM dd • HH:mm" : "EEE, MMM dd • hh:mm a", Locale.US);
                timeText.setText(sdf.format(timeInMillis));
                timeText.setTextColor(currentThemeTextColor);
                timeText.setTextSize(16f);
                timeText.setTypeface(null, android.graphics.Typeface.BOLD);

                TextView noteText = new TextView(this);
                noteText.setText(note.isEmpty() ? "Standard Alarm" : note);
                noteText.setTextColor(themeState == 0 ? Color.DKGRAY : Color.LTGRAY);
                noteText.setTextSize(12f);

                textStack.addView(timeText); textStack.addView(noteText);
                card.addView(textStack);

                ImageView deleteBtn = new ImageView(this);
                deleteBtn.setImageResource(android.R.drawable.ic_menu_delete);
                deleteBtn.setColorFilter(Color.parseColor("#FF3B30"), PorterDuff.Mode.SRC_IN);
                deleteBtn.setPadding(16, 16, 16, 16);
                deleteBtn.setOnClickListener(v -> {
                    cancelSpecificAlarm(id);
                    content.removeView(overlay);
                    showAlarmsListDialog(themeState);
                });
                card.addView(deleteBtn);
                listContainer.addView(card);
            }
        } catch (Exception e) {}

        TextView addBtn = new TextView(this);
        addBtn.setText("+ Add New Alarm"); addBtn.setTextColor(Color.WHITE); addBtn.setTextSize(16f);
        addBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        addBtn.setGravity(Gravity.CENTER); addBtn.setPadding(0, pad, 0, pad);

        GradientDrawable addGd = new GradientDrawable();
        addGd.setColor(themeState == 0 ? Color.parseColor("#333333") : Color.parseColor("#6750A4"));
        addGd.setCornerRadius(dp(100));
        addBtn.setBackground(addGd);

        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addParams.setMargins(0, dp(16), 0, 0);
        addBtn.setLayoutParams(addParams);

        addBtn.setOnClickListener(v -> {
            content.removeView(overlay);
            showUnifiedAlarmCreationPage(themeState);
        });

        overlay.setOnClickListener(v -> content.removeView(overlay));
        layout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        layout.addView(addBtn);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.6)
        );
        cardParams.gravity = Gravity.CENTER;
        overlay.addView(layout, cardParams);
        content.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.requestFocus();
    }

    // ======================================================================
    // 2. UNIFIED FULL-SCREEN ALARM CREATION (SEAMLESS VIEW FACTORY)
    // ======================================================================
    private void showUnifiedAlarmCreationPage(int themeState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                Toast.makeText(this, "Please allow notifications to use the alarm.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        int dialogTheme = (themeState == 0) ? android.R.style.Theme_DeviceDefault_Light_NoActionBar : android.R.style.Theme_DeviceDefault_NoActionBar;
        Dialog dialog = new Dialog(this, dialogTheme);
        Context themedContext = new ContextThemeWrapper(this, dialogTheme);

        int rootBg = (themeState == 0) ? Color.parseColor("#F2F2F7") : ((themeState == 1) ? Color.parseColor("#1C1C1E") : Color.parseColor("#000000"));
        int cardBg = (themeState == 0) ? Color.WHITE : Color.parseColor("#2C2C2E");
        int textColor = (themeState == 0) ? Color.BLACK : Color.WHITE;
        int accentGreen = Color.parseColor("#15633C");
        int secondaryText = (themeState == 0) ? Color.parseColor("#666666") : Color.parseColor("#AAAAAA");

        LinearLayout root = new LinearLayout(themedContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(rootBg);

        LinearLayout header = new LinearLayout(themedContext);
        header.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(accentGreen);
        headerBg.setCornerRadii(new float[]{0, 0, 0, 0, dp(32), dp(32), dp(32), dp(32)});
        header.setBackground(headerBg);

        TextView headerLabel = new TextView(themedContext);
        headerLabel.setText("ALARM"); headerLabel.setTextColor(Color.parseColor("#A3D9B1"));
        headerLabel.setTextSize(12f); headerLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView headerTime = new TextView(themedContext);
        headerTime.setTextColor(Color.WHITE); headerTime.setTextSize(56f);
        headerTime.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView headerDate = new TextView(themedContext);
        headerDate.setTextColor(Color.WHITE); headerDate.setTextSize(16f);

        header.addView(headerLabel); header.addView(headerTime); header.addView(headerDate);
        root.addView(header);

        ScrollView scrollView = new ScrollView(themedContext);
        scrollView.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(themedContext);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(16));

        GradientDrawable calCardGd = new GradientDrawable();
        calCardGd.setColor(cardBg); calCardGd.setCornerRadius(dp(24));

        LinearLayout calCard = new LinearLayout(themedContext);
        calCard.setOrientation(LinearLayout.VERTICAL);
        calCard.setBackground(calCardGd); calCard.setClipToOutline(true); calCard.setLayoutParams(cardParams);

        CalendarView calendarView = new CalendarView(themedContext);
        calendarView.setMinDate(System.currentTimeMillis() - 1000);
        calendarView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        calCard.addView(calendarView);
        body.addView(calCard);

        GradientDrawable timeCardGd = new GradientDrawable();
        timeCardGd.setColor(cardBg); timeCardGd.setCornerRadius(dp(24));

        LinearLayout timeCard = new LinearLayout(themedContext);
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setBackground(timeCardGd); timeCard.setClipToOutline(true); timeCard.setLayoutParams(cardParams);

        TimePicker timePicker = new TimePicker(themedContext);
        boolean is24Hour = DateFormat.is24HourFormat(this);
        timePicker.setIs24HourView(is24Hour);

        timePicker.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (top != oldTop || bottom != oldBottom || right != oldRight || left != oldLeft) {
                timeCard.post(() -> { timeCard.requestLayout(); body.requestLayout(); scrollView.requestLayout(); });
            }
        });

        FrameLayout timePickerWrapper = new FrameLayout(themedContext) {
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    float x = event.getX(), y = event.getY(), width = getWidth(), height = getHeight();
                    if (y < height * 0.38f || y > height * 0.85f) return false;
                    float cx = width / 2f, cy = height * 0.61f, radius = width * 0.42f;
                    float dx = x - cx, dy = y - cy;
                    if ((dx * dx) + (dy * dy) > (radius * radius)) return true;
                }
                return super.onInterceptTouchEvent(event);
            }
            @Override public boolean onTouchEvent(MotionEvent event) { return true; }
        };

        timePicker.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        timePickerWrapper.addView(timePicker);
        timeCard.addView(timePickerWrapper, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(timeCard);

        GradientDrawable noteCardGd = new GradientDrawable();
        noteCardGd.setColor(cardBg); noteCardGd.setCornerRadius(dp(24));

        LinearLayout noteCard = new LinearLayout(themedContext);
        noteCard.setOrientation(LinearLayout.VERTICAL);
        noteCard.setBackground(noteCardGd); noteCard.setClipToOutline(true);
        noteCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        noteCard.setLayoutParams(cardParams);

        TextView noteLabel = new TextView(themedContext);
        noteLabel.setText("Alarm Note (Optional)"); noteLabel.setTextColor(secondaryText); noteLabel.setTextSize(12f);

        EditText noteInput = new EditText(themedContext);
        noteInput.setHint("e.g. Morning Run"); noteInput.setHintTextColor(secondaryText);
        noteInput.setTextColor(textColor); noteInput.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));

        noteCard.addView(noteLabel); noteCard.addView(noteInput);
        body.addView(noteCard);

        TextView saveBtn = new TextView(themedContext);
        saveBtn.setText("Set Alarm"); saveBtn.setTextColor(Color.WHITE); saveBtn.setTextSize(16f);
        saveBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        saveBtn.setGravity(Gravity.CENTER); saveBtn.setPadding(0, dp(16), 0, dp(16));

        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setColor(accentGreen); btnGd.setCornerRadius(dp(100));
        saveBtn.setBackground(btnGd);

        Calendar alarmTime = Calendar.getInstance();
        Runnable updateHeader = () -> {
            SimpleDateFormat timeFmt = new SimpleDateFormat(is24Hour ? "HH:mm" : "hh:mm a", Locale.US);
            SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US);
            headerTime.setText(timeFmt.format(alarmTime.getTime()));
            headerDate.setText(dateFmt.format(alarmTime.getTime()));
        };

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            alarmTime.set(Calendar.YEAR, year); alarmTime.set(Calendar.MONTH, month); alarmTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateHeader.run();
        });

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            alarmTime.set(Calendar.HOUR_OF_DAY, hourOfDay); alarmTime.set(Calendar.MINUTE, minute); alarmTime.set(Calendar.SECOND, 0);
            updateHeader.run();
        });

        updateHeader.run();

        saveBtn.setOnClickListener(v -> {
            if (alarmTime.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Cannot set alarm in the past!", Toast.LENGTH_SHORT).show();
                return;
            }
            saveAndScheduleAlarm(alarmTime.getTimeInMillis(), noteInput.getText().toString().trim());
            dialog.dismiss();
        });

        body.addView(saveBtn);
        scrollView.addView(body);
        root.addView(scrollView);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            header.setPadding(dp(24), systemBars.top + dp(12), dp(24), dp(32));
            body.setPadding(dp(16), dp(16), dp(16), systemBars.bottom + dp(32));
            return WindowInsetsCompat.CONSUMED;
        });

        dialog.setContentView(root);
        dialog.setOnKeyListener((dialogInterface, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) { dialog.dismiss(); return true; }
            return false;
        });

        if (dialog.getWindow() != null) {
            Window dialogWindow = dialog.getWindow();
            dialogWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialogWindow.setWindowAnimations(0);
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false);
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            dialogWindow.setStatusBarColor(accentGreen);
            dialogWindow.setNavigationBarColor(Color.TRANSPARENT);

            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(dialogWindow, dialogWindow.getDecorView());
            if (controller != null) controller.setAppearanceLightStatusBars(false);
        }
        dialog.show();
    }

    // ======================================================================
    // 3. CORE ALARM LOGIC (Schedule, Cancel, Refresh)
    // ======================================================================
    private void saveAndScheduleAlarm(long timeInMillis, String note) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
            Toast.makeText(this, "Please allow Exact Alarms so notifications trigger on time.", Toast.LENGTH_LONG).show();
            return;
        }

        int alarmId = (int) (timeInMillis % Integer.MAX_VALUE);
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
            JSONObject newAlarm = new JSONObject();
            newAlarm.put("id", alarmId); newAlarm.put("time", timeInMillis); newAlarm.put("note", note);
            alarmsArray.put(newAlarm);
            prefs.edit().putString("alarms_list", alarmsArray.toString()).apply();
        } catch (Exception e) {}

        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("com.abhinav.ownapp.TRIGGER_ALARM");
        intent.putExtra("ALARM_ID", alarmId); intent.putExtra("ALARM_NOTE", note);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(timeInMillis, pendingIntent);
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);

        refreshAlarmGradientState();
        boolean is24Hour = DateFormat.is24HourFormat(this);
        SimpleDateFormat sdf = new SimpleDateFormat(is24Hour ? "MMM dd, HH:mm" : "MMM dd, hh:mm a", Locale.US);
        Toast.makeText(this, "Alarm set for " + sdf.format(timeInMillis), Toast.LENGTH_SHORT).show();
    }

    private void animateFadeEnter(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(250).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void animateFadeExit(Dialog dialog, View view) {
        view.animate().alpha(0f).setDuration(200).setInterpolator(new AccelerateInterpolator()).withEndAction(dialog::dismiss).start();
    }

    private void cancelSpecificAlarm(int alarmId) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.setAction("com.abhinav.ownapp.TRIGGER_ALARM");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.cancel(pendingIntent);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
            JSONArray updatedArray = new JSONArray();
            for (int i = 0; i < alarmsArray.length(); i++) {
                if (alarmsArray.getJSONObject(i).getInt("id") != alarmId) updatedArray.put(alarmsArray.getJSONObject(i));
            }
            prefs.edit().putString("alarms_list", updatedArray.toString()).apply();
        } catch (Exception e) {}
        refreshAlarmGradientState();
    }

    // ======================================================================
    // 4. DYNAMIC GRADIENT MANAGER
    // ======================================================================
    private void refreshAlarmGradientState() {
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        try {
            JSONArray alarmsArray = new JSONArray(prefs.getString("alarms_list", "[]"));
            if (alarmsArray.length() > 0) startAlarmGradient();
            else stopAlarmGradient();
        } catch (Exception e) { stopAlarmGradient(); }
    }

    private void startAlarmGradient() {
        if (tvTime == null || (gradientAnimator != null && gradientAnimator.isRunning())) return;
        float textWidth = tvTime.getTextSize() * 4f;
        int colorPurple = Color.parseColor("#9B59B6"), colorYellow = Color.parseColor("#F1C40F"), colorGreen = Color.parseColor("#2ECC71");

        Shader shader = new LinearGradient(0, 0, textWidth, 0, new int[]{colorPurple, colorYellow, colorGreen, colorPurple}, new float[]{0f, 0.33f, 0.66f, 1f}, Shader.TileMode.REPEAT);
        tvTime.getPaint().setShader(shader);

        gradientAnimator = ValueAnimator.ofFloat(0, textWidth);
        gradientAnimator.setDuration(2000);
        gradientAnimator.setRepeatCount(ValueAnimator.INFINITE);
        gradientAnimator.addUpdateListener(animation -> {
            float translate = (float) animation.getAnimatedValue();
            Matrix matrix = new Matrix();
            matrix.setTranslate(translate, 0);
            shader.setLocalMatrix(matrix);
            tvTime.invalidate();
        });
        gradientAnimator.start();
    }

    private void stopAlarmGradient() {
        if (gradientAnimator != null) { gradientAnimator.cancel(); gradientAnimator = null; }
        if (tvTime != null) { tvTime.getPaint().setShader(null); tvTime.setTextColor(currentThemeTextColor); tvTime.invalidate(); }
    }

    private void updateAllWidgets() {
        Class<?>[] widgetClasses = {SnakeWidget.class, WaterWidgetProvider.class, HourglassWidget.class};
        for (Class<?> widgetClass : widgetClasses) {
            Intent intent = new Intent(this, widgetClass);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            int[] ids = AppWidgetManager.getInstance(this).getAppWidgetIds(new ComponentName(this, widgetClass));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(intent);
        }
    }
}