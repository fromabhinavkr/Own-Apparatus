package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class DriveOnActivity extends AppCompatActivity {

    private static final String TAP_TO_DRIVE_TEXT = "Tap to drive";
    private int themeState, highScore = 0, currentHandling = 1;
    private boolean isVibrationEnabled = true, isMusicEnabled = true;
    private SharedPreferences prefs;

    private TextView tvScore, tvBestScore, tvTapToStart;
    private LinearLayout scoreContainer, btnPause;
    private RelativeLayout pauseOverlay;
    private FrameLayout pauseIconContainer;
    private ThriveActivity.GameIconView pauseIconView;
    private DriveOnEngine gameEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        EdgeToEdge.enable(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_drive_on);
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView != null) contentView.setPadding(0, 0, 0, 0);
        hideSystemBars();

        final float dp = getResources().getDisplayMetrics().density;
        View root = findViewById(R.id.driveOnRoot);
        RelativeLayout topHUD = findViewById(R.id.topHud);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (topHUD != null) {
                int dp12 = (int) (12 * dp);
                topHUD.setPadding(dp12 + cutout.left, dp12, dp12 + cutout.right, dp12);
            }
            if (gameEngine != null) gameEngine.setSafeInsets(cutout.left, cutout.right);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true) ? 1 : 0;

        highScore = prefs.getInt("driveon_high_score", 0);
        isVibrationEnabled = prefs.getBoolean("driveon_vibration_enabled", true);
        isMusicEnabled = prefs.getBoolean("driveon_music_enabled", true);
        currentHandling = prefs.getInt("driveon_handling", 1);

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        tvScore = findViewById(R.id.tvScore);
        tvBestScore = findViewById(R.id.tvBestScore);
        tvTapToStart = findViewById(R.id.tvTapToStart);
        scoreContainer = findViewById(R.id.scoreContainer);

        pauseOverlay = findViewById(R.id.pauseOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard);

        btnPause = findViewById(R.id.btnPause);
        pauseIconContainer = findViewById(R.id.pauseIconContainer);
        pauseIconView = new ThriveActivity.GameIconView(this);
        pauseIconContainer.addView(pauseIconView);

        Button btnResume = findViewById(R.id.btnResume);
        Button btnResetCar = findViewById(R.id.btnResetCar);
        Button btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);
        Button btnToggleMusic = findViewById(R.id.btnToggleMusic);
        Button btnToggleSpeed = findViewById(R.id.btnToggleSpeed);

        int bgColor, cardColor, cardStrokeColor, textColor, subTextColor, primaryBtnColor, primaryBtnText, quitBtnColor, pillBgColor, boxBgColor, iconColor;
        if (themeState == 0) {
            bgColor = Color.parseColor("#7CB86B"); cardColor = Color.parseColor("#F4F7F2"); cardStrokeColor = Color.parseColor("#C6DEC0");
            textColor = Color.parseColor("#1B3022"); subTextColor = Color.parseColor("#4A6B53");
            primaryBtnColor = Color.parseColor("#2E7D32"); primaryBtnText = Color.WHITE; quitBtnColor = Color.parseColor("#DCE7D9");
            pillBgColor = Color.parseColor("#F4F7F2"); boxBgColor = Color.parseColor("#FFFFFF"); iconColor = Color.parseColor("#1B3022");
        } else if (themeState == 1) {
            bgColor = Color.parseColor("#071324"); cardColor = Color.parseColor("#10233E"); cardStrokeColor = Color.parseColor("#234B7A");
            textColor = Color.parseColor("#F0F9FF"); subTextColor = Color.parseColor("#93C5FD");
            primaryBtnColor = Color.parseColor("#0284C7"); primaryBtnText = Color.WHITE; quitBtnColor = Color.parseColor("#1B375D");
            pillBgColor = Color.parseColor("#162E4F"); boxBgColor = Color.parseColor("#BAE6FD"); iconColor = Color.parseColor("#082F49");
        } else {
            bgColor = Color.parseColor("#030710"); cardColor = Color.parseColor("#07090F"); cardStrokeColor = Color.parseColor("#2638BDF8");
            textColor = Color.parseColor("#F8FAFC"); subTextColor = Color.parseColor("#94A3B8");
            primaryBtnColor = Color.parseColor("#38BDF8"); primaryBtnText = Color.parseColor("#041322"); quitBtnColor = Color.parseColor("#151A28");
            pillBgColor = Color.parseColor("#111624"); boxBgColor = Color.parseColor("#38BDF8"); iconColor = Color.parseColor("#001C3A");
        }

        getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));
        if (root != null) root.setBackgroundColor(bgColor);
        if (tvTapToStart != null) { tvTapToStart.setText(TAP_TO_DRIVE_TEXT); tvTapToStart.setTextColor(textColor); }
        tvScore.setTextColor(textColor);
        tvBestScore.setTextColor(subTextColor);
        tvBestScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);

        if (btnResume != null) {
            btnResume.setBackgroundTintList(ColorStateList.valueOf(primaryBtnColor));
            btnResume.setTextColor(primaryBtnText);
        }
        if (btnQuitFromPause != null) {
            btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor));
            btnQuitFromPause.setTextColor(textColor);
        }

        if (pauseCard != null) {
            GradientDrawable gdPause = new GradientDrawable();
            gdPause.setColor(cardColor); gdPause.setCornerRadius(32f * dp);
            gdPause.setStroke((int) (1.5f * dp), cardStrokeColor);
            pauseCard.setBackground(gdPause);
        }

        if (btnPause != null) {
            GradientDrawable pill = new GradientDrawable(); pill.setColor(pillBgColor); pill.setCornerRadius(1000f);
            GradientDrawable box = new GradientDrawable(); box.setColor(boxBgColor); box.setCornerRadius(30f);
            btnPause.setBackground(pill);
            pauseIconContainer.setBackground(box);
            pauseIconView.setIcon(ThriveActivity.GameIconView.ICON_PAUSE, iconColor);
        }

        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                isVibrationEnabled = !isVibrationEnabled;
                prefs.edit().putBoolean("driveon_vibration_enabled", isVibrationEnabled).apply();
                btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setVibrationEnabled(isVibrationEnabled);
            });
        }

        if (btnToggleMusic != null) {
            btnToggleMusic.setText("Sound: " + (isMusicEnabled ? "ON" : "OFF"));
            btnToggleMusic.setOnClickListener(v -> {
                isMusicEnabled = !isMusicEnabled;
                prefs.edit().putBoolean("driveon_music_enabled", isMusicEnabled).apply();
                btnToggleMusic.setText("Sound: " + (isMusicEnabled ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setMusicEnabled(isMusicEnabled);
            });
        }

        if (btnToggleSpeed != null) {
            String[] modes = {"Handling: Arcade", "Handling: Drift", "Handling: Pro Sim"};
            btnToggleSpeed.setText(modes[currentHandling]);
            btnToggleSpeed.setOnClickListener(v -> {
                currentHandling = (currentHandling + 1) % 3;
                prefs.edit().putInt("driveon_handling", currentHandling).apply();
                btnToggleSpeed.setText(modes[currentHandling]);
                if (gameEngine != null) gameEngine.setHandlingMode(currentHandling);
            });
        }

        gameEngine = new DriveOnEngine(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameEngine.setMusicEnabled(isMusicEnabled);
        gameEngine.setHandlingMode(currentHandling);
        gameContainer.addView(gameEngine);

        gameEngine.setGameListener(new DriveOnEngine.GameListener() {
            @Override
            public void onScoreUpdated(int score) {
                tvScore.setText("Score: " + score);
                if (score > highScore) {
                    highScore = score;
                    prefs.edit().putInt("driveon_high_score", highScore).apply();
                    tvBestScore.setText("Best: " + highScore);
                }
            }

            @Override
            public void onGameStarted() {
                if (tvTapToStart != null && tvTapToStart.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    tvTapToStart.setVisibility(View.GONE);
                    scoreContainer.setVisibility(View.VISIBLE);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    tvTapToStart.setVisibility(View.VISIBLE);
                    scoreContainer.setVisibility(View.GONE);

                    String tapStr = TAP_TO_DRIVE_TEXT, curScoreStr = "Score: 0", bestScoreStr = tvBestScore.getText().toString();
                    ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    widthAnimator.addUpdateListener(a -> {
                        ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                        lp.width = (Integer) a.getAnimatedValue();
                        btnPause.setLayoutParams(lp);
                    });
                    widthAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) {
                            ViewGroup.LayoutParams lp = btnPause.getLayoutParams();
                            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                            btnPause.setLayoutParams(lp);
                        }
                    });
                    widthAnimator.setDuration(400);
                    widthAnimator.setInterpolator(new DecelerateInterpolator());

                    ValueAnimator textAnimator = ValueAnimator.ofFloat(0f, 1f);
                    textAnimator.addUpdateListener(anim -> {
                        float f = anim.getAnimatedFraction();
                        if (f < 0.4f) {
                            tvTapToStart.setText(tapStr.substring(0, (int) (tapStr.length() * (1f - (f / 0.4f)))));
                        } else {
                            if (tvTapToStart.getVisibility() == View.VISIBLE) {
                                tvTapToStart.setVisibility(View.GONE);
                                scoreContainer.setVisibility(View.VISIBLE);
                            }
                            float tf = (f - 0.4f) / 0.6f;
                            tvScore.setText(curScoreStr.substring(0, (int) (curScoreStr.length() * tf)));
                            tvBestScore.setText(bestScoreStr.substring(0, (int) (bestScoreStr.length() * tf)));
                        }
                    });
                    textAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) {
                            tvScore.setText(curScoreStr); tvBestScore.setText(bestScoreStr); tvTapToStart.setText(tapStr);
                        }
                    });
                    textAnimator.setDuration(400);
                    widthAnimator.start(); textAnimator.start();
                }
            }
        });

        if (btnPause != null) {
            btnPause.setOnClickListener(v -> {
                gameEngine.pauseGame();
                pauseOverlay.setAlpha(0f);
                pauseOverlay.setVisibility(View.VISIBLE);
                pauseOverlay.animate().alpha(1f).setDuration(250).start();
                btnPause.animate().alpha(0f).setDuration(250).withEndAction(() -> btnPause.setVisibility(View.INVISIBLE)).start();
                pauseIconView.setIcon(ThriveActivity.GameIconView.ICON_PLAY, iconColor);
            });
        }

        if (btnResume != null) {
            btnResume.setOnClickListener(v -> {
                pauseOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> pauseOverlay.setVisibility(View.GONE)).start();
                btnPause.setAlpha(0f);
                btnPause.setVisibility(View.VISIBLE);
                btnPause.animate().alpha(1f).setDuration(250).start();
                pauseIconView.setIcon(ThriveActivity.GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resumeGame();
                hideSystemBars();
            });
        }

        if (btnResetCar != null) {
            btnResetCar.setOnClickListener(v -> {
                pauseOverlay.animate().alpha(0f).setDuration(250).withEndAction(() -> pauseOverlay.setVisibility(View.GONE)).start();
                btnPause.setAlpha(0f);
                btnPause.setVisibility(View.VISIBLE);
                btnPause.animate().alpha(1f).setDuration(250).start();
                pauseIconView.setIcon(ThriveActivity.GameIconView.ICON_PAUSE, iconColor);
                tvScore.setText("Score: 0");
                gameEngine.resetGame();
                hideSystemBars();
            });
        }

        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars(); }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameEngine != null && gameEngine.isPlaying() && (pauseOverlay == null || pauseOverlay.getVisibility() != View.VISIBLE)) {
            if (btnPause != null) btnPause.performClick();
        }
    }

    @Override
    public void onBackPressed() {
        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameEngine != null && gameEngine.isPlaying() && btnPause != null) { btnPause.performClick(); return; }
        super.onBackPressed();
    }

    @Override public void finish() { super.finish(); overridePendingTransition(0, android.R.anim.fade_out); }
}