package com.abhinav.ownapp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import java.io.File;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public class DeviceStatsHelper {

    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_AMOLED = 2;

    private static Handler statsHandler;
    private static Runnable statsRunnable;

    public static void setupDashboard(Activity activity, boolean isDarkTheme) {
        int resolvedTheme = resolveThemeMode(activity, isDarkTheme);
        setupDashboard(activity, resolvedTheme);
    }

    public static void setupDashboard(Activity activity, int themeMode) {
        if (activity == null || activity.isFinishing()) return;

        try {
            // --- 1. LINK VIEWS ---
            View ramCardBg = activity.findViewById(R.id.ramCardBg);
            View storageCardBg = activity.findViewById(R.id.storageCardBg);
            View batteryCardBg = activity.findViewById(R.id.batteryCardBg);
            View ramDetailsBox = activity.findViewById(R.id.ramDetailsBox);

            RamGraphView ramGraphView = activity.findViewById(R.id.ramGraphView);
            TextView tvRamTitle = activity.findViewById(R.id.tvRamTitle);
            TextView tvRamPercentLarge = activity.findViewById(R.id.tvRamPercentLarge);
            TextView tvRamPercentUnit = activity.findViewById(R.id.tvRamPercentUnit);

            TextView tvRamUsedLbl = activity.findViewById(R.id.tvRamUsedLbl);
            TextView tvRamUsed = activity.findViewById(R.id.tvRamUsed);
            TextView tvRamFreeLbl = activity.findViewById(R.id.tvRamFreeLbl);
            TextView tvRamFree = activity.findViewById(R.id.tvRamFree);
            TextView tvRamTotalLbl = activity.findViewById(R.id.tvRamTotalLbl);
            TextView tvRamTotal = activity.findViewById(R.id.tvRamTotal);

            TextView tvStorageValLarge = activity.findViewById(R.id.tvStorageValueLarge);
            TextView tvStorageUnit = activity.findViewById(R.id.tvStorageUnit);
            TextView tvStorageTitle = activity.findViewById(R.id.tvStorageTitle);
            TextView tvStorageStatus = activity.findViewById(R.id.tvStorageStatus);
            FrameLayout storageSegmentContainer = activity.findViewById(R.id.storageSegmentContainer);

            TextView tvBatteryValLarge = activity.findViewById(R.id.tvBatteryValueLarge);
            TextView tvBatteryUnit = activity.findViewById(R.id.tvBatteryUnit);
            TextView tvBatteryTitle = activity.findViewById(R.id.tvBatteryTitle);
            TextView tvBatteryStatus = activity.findViewById(R.id.tvBatteryStatus);
            FrameLayout batterySegmentContainer = activity.findViewById(R.id.batterySegmentContainer);

            if (ramCardBg == null || storageSegmentContainer == null || batterySegmentContainer == null) return;

            boolean isLight = (themeMode == THEME_LIGHT);
            boolean isAmoled = (themeMode == THEME_AMOLED);
            boolean isDarkOrAmoled = !isLight;

            // --- 2. APPLY LIGHT / DARK / AMOLED MODE COLORS ---
            int textColor = isDarkOrAmoled ? Color.WHITE : Color.parseColor("#1C1C1E");
            int subTextColor = isDarkOrAmoled ? Color.parseColor("#B0B0B8") : Color.parseColor("#8E8E93");

            int cardBgColor;
            int innerBoxBgColor;
            int innerBoxStrokeColor;
            int widgetFrameColor;
            int widgetCavityColor;

            if (isLight) {
                cardBgColor = Color.parseColor("#F2F2F7");
                innerBoxBgColor = Color.parseColor("#FFFFFF");
                innerBoxStrokeColor = Color.parseColor("#E5E5EA");
                widgetFrameColor = Color.parseColor("#D8D8DF");
                widgetCavityColor = Color.parseColor("#EAEAF0");
            } else if (isAmoled) {
                cardBgColor = Color.parseColor("#141416");
                innerBoxBgColor = Color.parseColor("#000000");
                innerBoxStrokeColor = Color.parseColor("#2A2A2D");
                widgetFrameColor = Color.parseColor("#2A2A2E");
                widgetCavityColor = Color.parseColor("#050507");
            } else {
                cardBgColor = Color.parseColor("#2C2C2E");
                innerBoxBgColor = Color.parseColor("#18181A");
                innerBoxStrokeColor = Color.parseColor("#38383A");
                widgetFrameColor = Color.parseColor("#3A3A3E");
                widgetCavityColor = Color.parseColor("#141416");
            }

            int activeAccentColor = isDarkOrAmoled ? Color.parseColor("#5AC8FA") : Color.parseColor("#007AFF");
            int criticalRedColor = isDarkOrAmoled ? Color.parseColor("#FF453A") : Color.parseColor("#FF3B30");
            int batteryChargingColor = isDarkOrAmoled ? Color.parseColor("#32D74B") : Color.parseColor("#34C759");
            int batteryDischargingColor = isDarkOrAmoled ? Color.parseColor("#5AC8FA") : Color.parseColor("#007AFF");

            ViewCompat.setBackgroundTintList(ramCardBg, ColorStateList.valueOf(cardBgColor));
            ramCardBg.setClipToOutline(true);

            if (storageCardBg != null) {
                ViewCompat.setBackgroundTintList(storageCardBg, ColorStateList.valueOf(cardBgColor));
                storageCardBg.setClipToOutline(true);
            }
            if (batteryCardBg != null) {
                ViewCompat.setBackgroundTintList(batteryCardBg, ColorStateList.valueOf(cardBgColor));
                batteryCardBg.setClipToOutline(true);
            }

            if (ramDetailsBox != null) {
                GradientDrawable boxShape = new GradientDrawable();
                boxShape.setColor(innerBoxBgColor);
                boxShape.setCornerRadius(activity.getResources().getDisplayMetrics().density * 10f);
                boxShape.setStroke(2, innerBoxStrokeColor);
                ramDetailsBox.setBackground(boxShape);
            }

            if (tvRamPercentLarge != null) tvRamPercentLarge.setTextColor(textColor);
            if (tvRamPercentUnit != null) tvRamPercentUnit.setTextColor(textColor);
            if (tvRamTitle != null) tvRamTitle.setTextColor(subTextColor);

            if (tvRamUsedLbl != null) tvRamUsedLbl.setTextColor(subTextColor);
            if (tvRamUsed != null) tvRamUsed.setTextColor(subTextColor);
            if (tvRamFreeLbl != null) tvRamFreeLbl.setTextColor(subTextColor);
            if (tvRamFree != null) tvRamFree.setTextColor(subTextColor);
            if (tvRamTotalLbl != null) tvRamTotalLbl.setTextColor(subTextColor);
            if (tvRamTotal != null) tvRamTotal.setTextColor(subTextColor);

            if (tvStorageValLarge != null) tvStorageValLarge.setTextColor(textColor);
            if (tvStorageUnit != null) tvStorageUnit.setTextColor(textColor);
            if (tvStorageTitle != null) tvStorageTitle.setTextColor(subTextColor);

            if (tvBatteryValLarge != null) tvBatteryValLarge.setTextColor(textColor);
            if (tvBatteryUnit != null) tvBatteryUnit.setTextColor(textColor);
            if (tvBatteryTitle != null) tvBatteryTitle.setTextColor(subTextColor);

            if (ramGraphView != null) ramGraphView.setTheme(isDarkOrAmoled);

            // Attach Vertical Liquid Wave View (Storage)
            storageSegmentContainer.removeAllViews();
            StorageWaveView storageWaveView = new StorageWaveView(activity);
            storageSegmentContainer.addView(storageWaveView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            // Attach Vertical 5-Pill Block View (Battery)
            batterySegmentContainer.removeAllViews();
            BatteryBlockView batteryBlockView = new BatteryBlockView(activity);
            batterySegmentContainer.addView(batteryBlockView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));

            // --- 3. STORAGE LOGIC ---
            try {
                File path = Environment.getDataDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSizeLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                long totalBlocks = stat.getBlockCountLong();

                double rawAvailStorageGb = (availableBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);
                double rawTotalStorageGb = (totalBlocks * blockSize) / (1024.0 * 1024.0 * 1024.0);

                int advertisedStorageGb = 8;
                int[] storageTiers = {8, 16, 32, 64, 128, 256, 512, 1024};
                for (int tier : storageTiers) {
                    if (rawTotalStorageGb <= tier) {
                        advertisedStorageGb = tier;
                        break;
                    }
                }
                double usedStorageGb = Math.max(0.0, advertisedStorageGb - rawAvailStorageGb);
                float storageUsedPct = (float) ((usedStorageGb / advertisedStorageGb) * 100.0);
                float storageFreePct = 100f - storageUsedPct;

                if (tvStorageValLarge != null) {
                    tvStorageValLarge.setText(String.format(Locale.US, "%.1f", usedStorageGb));
                }

                // Turn RED when remaining free storage is less than 5% (used >= 95%)
                boolean isStorageCritical = (storageFreePct < 5f || storageUsedPct >= 95f);
                int chosenStorageColor = isStorageCritical ? criticalRedColor : activeAccentColor;

                if (tvStorageStatus != null) {
                    tvStorageStatus.setText(String.format(Locale.US, "Out of %d GB", advertisedStorageGb));
                    tvStorageStatus.setTextColor(chosenStorageColor);
                }

                storageWaveView.setColors(chosenStorageColor, widgetFrameColor, widgetCavityColor);
                storageWaveView.setProgress(storageUsedPct);
            } catch (Exception e) {
                Log.e("DeviceStats", "Storage read failed", e);
            }

            // --- 4. REAL-TIME RAM & BATTERY LOGIC ---
            ActivityManager actManager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();

            BatteryBlockView finalBatteryView = batteryBlockView;

            if (statsHandler != null && statsRunnable != null) {
                statsHandler.removeCallbacks(statsRunnable);
            }

            statsHandler = new Handler(Looper.getMainLooper());
            statsRunnable = new Runnable() {
                @Override
                public void run() {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }

                    if (!ramCardBg.isAttachedToWindow()) {
                        statsHandler.postDelayed(this, 500);
                        return;
                    }

                    // RAM Updates
                    if (ramGraphView != null && actManager != null) {
                        actManager.getMemoryInfo(memInfo);
                        long totalRamMB = memInfo.totalMem / 1048576L;
                        long availRamMB = memInfo.availMem / 1048576L;
                        long usedRamMB = totalRamMB - availRamMB;

                        if (totalRamMB > 0) {
                            float percentUsed = ((float) usedRamMB / totalRamMB) * 100f;

                            if (tvRamPercentLarge != null) tvRamPercentLarge.setText(String.valueOf((int) percentUsed));
                            if (tvRamUsed != null) tvRamUsed.setText(String.format(Locale.US, "%d MB", usedRamMB));
                            if (tvRamFree != null) tvRamFree.setText(String.format(Locale.US, "%d MB", availRamMB));
                            if (tvRamTotal != null) tvRamTotal.setText(String.format(Locale.US, "%d MB", totalRamMB));

                            ramGraphView.addRamData(percentUsed);
                        }
                    }

                    // Battery Updates via Sticky Intent
                    try {
                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatusIntent = activity.registerReceiver(null, ifilter);

                        if (batteryStatusIntent != null) {
                            int level = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                            int scale = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                            int status = batteryStatusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

                            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                    status == BatteryManager.BATTERY_STATUS_FULL;

                            int batteryPct = (int) ((level / (float) scale) * 100);

                            if (batteryPct >= 0 && batteryPct <= 100) {
                                if (tvBatteryValLarge != null) {
                                    tvBatteryValLarge.setText(String.valueOf(batteryPct));
                                }

                                // Turn RED when battery is 5% or lower
                                boolean isBatteryCritical = (batteryPct <= 5);

                                if (isBatteryCritical) {
                                    if (tvBatteryStatus != null) {
                                        tvBatteryStatus.setText(isCharging ? "Charging" : "Low Battery");
                                        tvBatteryStatus.setTextColor(criticalRedColor);
                                    }
                                    finalBatteryView.setColors(criticalRedColor, widgetFrameColor, widgetCavityColor);
                                } else if (isCharging) {
                                    if (tvBatteryStatus != null) {
                                        tvBatteryStatus.setText("Charging");
                                        tvBatteryStatus.setTextColor(batteryChargingColor);
                                    }
                                    finalBatteryView.setColors(batteryChargingColor, widgetFrameColor, widgetCavityColor);
                                } else {
                                    if (tvBatteryStatus != null) {
                                        tvBatteryStatus.setText("Discharging");
                                        tvBatteryStatus.setTextColor(batteryDischargingColor);
                                    }
                                    finalBatteryView.setColors(batteryDischargingColor, widgetFrameColor, widgetCavityColor);
                                }

                                finalBatteryView.setProgress(batteryPct);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("DeviceStats", "Battery intent read error", e);
                    }

                    statsHandler.postDelayed(this, 1000);
                }
            };

            statsHandler.post(statsRunnable);

        } catch (Exception e) {
            Log.e("DeviceStats", "Dashboard setup fatal error", e);
        }
    }

    private static int resolveThemeMode(Activity activity, boolean isDarkTheme) {
        if (!isDarkTheme) return THEME_LIGHT;
        try {
            String[] candidatePrefs = {
                    "SnakeWidgetPrefs",
                    "OwnAppPrefs",
                    "AppPrefs",
                    activity.getPackageName() + "_preferences"
            };
            for (String prefName : candidatePrefs) {
                SharedPreferences prefs = activity.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                if (prefs.contains("app_theme_state")) {
                    int state = prefs.getInt("app_theme_state", THEME_DARK);
                    if (state == THEME_AMOLED) return THEME_AMOLED;
                    if (state == THEME_LIGHT) return THEME_LIGHT;
                    return THEME_DARK;
                }
            }
            Class<?> widgetClass = Class.forName(activity.getPackageName() + ".SnakeWidget");
            String prefName = (String) widgetClass.getField("PREFS_NAME").get(null);
            if (prefName != null) {
                SharedPreferences prefs = activity.getSharedPreferences(prefName, Context.MODE_PRIVATE);
                int state = prefs.getInt("app_theme_state", THEME_DARK);
                if (state == THEME_AMOLED) return THEME_AMOLED;
            }
        } catch (Exception ignored) {
        }
        return THEME_DARK;
    }

    // =========================================================================
    // VERTICAL IMAGE 2 DESIGN: FULL-VIBRANCY ANTI-ALIASED LIQUID WAVE VIEW
    // =========================================================================
    public static class StorageWaveView extends View {
        private float progress = 0f;
        private float wavePhase = 0f;
        private ValueAnimator waveAnimator;

        private int baseActiveColor = Color.parseColor("#5AC8FA");
        private final Paint paintFrame = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintCavity = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintFrontWave = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintBackWave = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintCrestLine = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        private final RectF outerRect = new RectF();
        private final RectF innerRect = new RectF();
        private final Path frontWavePath = new Path();
        private final Path backWavePath = new Path();
        private final Path crestPath = new Path();
        // SRC_ATOP preserves the mask's destination alpha so stacked waves stay 100% vibrant
        private final PorterDuffXfermode srcAtopMode = new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP);

        private int lastShaderHeight = -1;
        private int lastShaderColor = 0;

        public StorageWaveView(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_HARDWARE, null);

            paintFrame.setStyle(Paint.Style.FILL);
            paintCavity.setStyle(Paint.Style.FILL);
            paintFrontWave.setStyle(Paint.Style.FILL);
            paintBackWave.setStyle(Paint.Style.FILL);

            paintCrestLine.setStyle(Paint.Style.STROKE);
            paintCrestLine.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setColors(int activeColor, int frameColor, int cavityColor) {
            this.baseActiveColor = activeColor;
            paintFrame.setColor(frameColor);
            paintCavity.setColor(cavityColor);

            int backColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(activeColor, Color.BLACK, 0.25f), 140);
            paintBackWave.setColor(backColor);

            int crestColor = ColorUtils.setAlphaComponent(
                    ColorUtils.blendARGB(activeColor, Color.WHITE, 0.38f), 190);
            paintCrestLine.setColor(crestColor);

            lastShaderHeight = -1;
            invalidate();
        }

        public void setProgress(float progress) {
            this.progress = Math.max(0f, Math.min(100f, progress));
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            startWaveAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            stopWaveAnimation();
            super.onDetachedFromWindow();
        }

        private void startWaveAnimation() {
            if (waveAnimator != null && waveAnimator.isRunning()) return;
            waveAnimator = ValueAnimator.ofFloat(0f, (float) (2.0 * Math.PI));
            waveAnimator.setDuration(3200L);
            waveAnimator.setRepeatCount(ValueAnimator.INFINITE);
            waveAnimator.setInterpolator(new LinearInterpolator());
            waveAnimator.addUpdateListener(anim -> {
                wavePhase = (float) anim.getAnimatedValue();
                invalidate();
            });
            waveAnimator.start();
        }

        private void stopWaveAnimation() {
            if (waveAnimator != null) {
                waveAnimator.cancel();
                waveAnimator = null;
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float density = getResources().getDisplayMetrics().density;
            float outerCorner = 12f * density;
            float borderPad = 3.6f * density;
            float innerCorner = Math.max(6f * density, outerCorner - borderPad * 0.7f);

            // 1. Outer Rounded Frame
            outerRect.set(0, 0, w, h);
            canvas.drawRoundRect(outerRect, outerCorner, outerCorner, paintFrame);

            innerRect.set(borderPad, borderPad, w - borderPad, h - borderPad);
            float innerW = innerRect.width();
            float innerH = innerRect.height();
            if (innerW <= 0 || innerH <= 0) return;

            if (progress <= 0f) {
                canvas.drawRoundRect(innerRect, innerCorner, innerCorner, paintCavity);
                return;
            }

            if ((int) h != lastShaderHeight || baseActiveColor != lastShaderColor) {
                int topColor = ColorUtils.blendARGB(baseActiveColor, Color.WHITE, 0.10f);
                int bottomColor = baseActiveColor;
                paintFrontWave.setShader(new LinearGradient(
                        0, innerRect.top, 0, innerRect.bottom,
                        topColor, bottomColor, Shader.TileMode.CLAMP
                ));
                paintCrestLine.setStrokeWidth(1.3f * density);
                lastShaderHeight = (int) h;
                lastShaderColor = baseActiveColor;
            }

            // 2. Anti-Aliased Offscreen Layer using SRC_ATOP (Zero pixelation + 100% full color vibrancy)
            int layerId = canvas.saveLayer(innerRect.left, innerRect.top, innerRect.right, innerRect.bottom, null);

            // Draw opaque rounded cavity mask first (sets alpha = 1.0 inside rounded rect, 0.0 outside)
            canvas.drawRoundRect(innerRect, innerCorner, innerCorner, paintCavity);

            float visualPct = progress < 5f ? Math.max(progress, 5f) : progress;
            float waterY = innerRect.bottom - (visualPct / 100f) * innerH;

            float edgeScale = Math.min(1f, Math.min(visualPct / 12f, (100f - visualPct) / 10f));
            float amp = Math.max(2.0f * density, innerW * 0.12f * Math.max(0.25f, edgeScale));

            float left = innerRect.left;
            float right = innerRect.right;
            float bottom = innerRect.bottom + outerCorner;

            // Back Wave
            float backLeftY = waterY + (float) Math.sin(wavePhase + 1.9f) * (amp * 0.7f);
            float backRightY = waterY - (float) Math.sin(wavePhase + 1.9f) * (amp * 0.7f);
            float backCtrl1Y = waterY - (float) Math.cos(wavePhase + 1.4f) * (amp * 1.35f);
            float backCtrl2Y = waterY + (float) Math.cos(wavePhase + 1.4f) * (amp * 1.35f);

            backWavePath.reset();
            backWavePath.moveTo(left, backLeftY);
            backWavePath.cubicTo(
                    left + innerW * 0.33f, backCtrl1Y,
                    left + innerW * 0.67f, backCtrl2Y,
                    right, backRightY
            );
            backWavePath.lineTo(right, bottom);
            backWavePath.lineTo(left, bottom);
            backWavePath.close();

            paintBackWave.setXfermode(srcAtopMode);
            canvas.drawPath(backWavePath, paintBackWave);
            paintBackWave.setXfermode(null);

            // Front Primary Wave (100% opaque vibrant color)
            float frontLeftY = waterY - (float) Math.sin(wavePhase) * (amp * 0.65f);
            float frontRightY = waterY + (float) Math.sin(wavePhase) * (amp * 0.65f);
            float frontCtrl1Y = waterY - (float) Math.cos(wavePhase) * (amp * 1.45f);
            float frontCtrl2Y = waterY + (float) Math.cos(wavePhase) * (amp * 1.45f);

            frontWavePath.reset();
            frontWavePath.moveTo(left, frontLeftY);
            frontWavePath.cubicTo(
                    left + innerW * 0.35f, frontCtrl1Y,
                    left + innerW * 0.65f, frontCtrl2Y,
                    right, frontRightY
            );
            frontWavePath.lineTo(right, bottom);
            frontWavePath.lineTo(left, bottom);
            frontWavePath.close();

            paintFrontWave.setXfermode(srcAtopMode);
            canvas.drawPath(frontWavePath, paintFrontWave);
            paintFrontWave.setXfermode(null);

            // Top Crest Highlight
            crestPath.reset();
            crestPath.moveTo(left, frontLeftY);
            crestPath.cubicTo(
                    left + innerW * 0.35f, frontCtrl1Y,
                    left + innerW * 0.65f, frontCtrl2Y,
                    right, frontRightY
            );
            paintCrestLine.setXfermode(srcAtopMode);
            canvas.drawPath(crestPath, paintCrestLine);
            paintCrestLine.setXfermode(null);

            canvas.restoreToCount(layerId);
        }
    }

    // =========================================================================
    // VERTICAL IMAGE 3 DESIGN: 5-PILL STACKED BATTERY VIEW
    // =========================================================================
    public static class BatteryBlockView extends View {
        private static final int PILL_COUNT = 5;
        private float progress = 0f;

        private final Paint paintFrame = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintSlotCavity = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintActiveFull = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint paintActivePartial = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

        private final RectF outerRect = new RectF();
        private final RectF slotRect = new RectF();
        private final RectF fillRect = new RectF();

        public BatteryBlockView(Context context) {
            super(context);
            paintFrame.setStyle(Paint.Style.FILL);
            paintSlotCavity.setStyle(Paint.Style.FILL);
            paintActiveFull.setStyle(Paint.Style.FILL);
            paintActivePartial.setStyle(Paint.Style.FILL);
        }

        public void setColors(int activeColor, int frameColor, int cavityColor) {
            paintFrame.setColor(frameColor);
            paintSlotCavity.setColor(cavityColor);
            paintActiveFull.setColor(activeColor);

            int partialColor = ColorUtils.blendARGB(activeColor, Color.WHITE, 0.16f);
            paintActivePartial.setColor(partialColor);
            invalidate();
        }

        public void setProgress(float progress) {
            this.progress = Math.max(0f, Math.min(100f, progress));
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;

            float density = getResources().getDisplayMetrics().density;
            float outerCorner = 12f * density;
            float borderPad = 3.6f * density;
            float slotGap = 3.2f * density;

            // 1. Matching Outer Rounded Vertical Frame
            outerRect.set(0, 0, w, h);
            canvas.drawRoundRect(outerRect, outerCorner, outerCorner, paintFrame);

            float innerW = w - (2f * borderPad);
            float innerH = h - (2f * borderPad);
            float slotHeight = (innerH - (slotGap * (PILL_COUNT - 1))) / PILL_COUNT;
            if (innerW <= 0 || slotHeight <= 0) return;

            float slotCorner = Math.min(slotHeight * 0.44f, 6f * density);

            // 2. Draw 5 Stacked Rounded Pill Slots (Bottom = Slot 0, Top = Slot 4)
            for (int i = 0; i < PILL_COUNT; i++) {
                int rowFromTop = (PILL_COUNT - 1) - i;
                float left = borderPad;
                float top = borderPad + rowFromTop * (slotHeight + slotGap);
                float right = left + innerW;
                float bottom = top + slotHeight;

                slotRect.set(left, top, right, bottom);
                canvas.drawRoundRect(slotRect, slotCorner, slotCorner, paintSlotCavity);

                float pillStartPct = i * 20f;
                float pillFraction = (progress - pillStartPct) / 20f;

                if (pillFraction >= 1f) {
                    canvas.drawRoundRect(slotRect, slotCorner, slotCorner, paintActiveFull);
                } else if (pillFraction > 0f) {
                    float minWidth = slotCorner * 2f;
                    float fillWidth = Math.max(minWidth, innerW * Math.min(1f, pillFraction));
                    fillRect.set(left, top, Math.min(right, left + fillWidth), bottom);
                    canvas.drawRoundRect(fillRect, slotCorner, slotCorner, paintActivePartial);
                }
            }
        }
    }
}