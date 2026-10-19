package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
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
public class ThriveActivity extends AppCompatActivity {

    private static final String TAP_TO_FLIGHT_TEXT = "Tap to fight";
    private int themeState, highScore = 0, currentDifficulty = 1;
    private int bestEasy = 0, bestMedium = 0, bestDifficult = 0, gamesEasy = 0, gamesMedium = 0, gamesDifficult = 0;
    private boolean isVibrationEnabled = true, isMusicEnabled = true, isEnemySoundEnabled = false;
    private SharedPreferences prefs;

    private TextView tvScore, tvBestScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private LinearLayout scoreContainer, btnPause;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private FrameLayout pauseIconContainer;
    private GameIconView pauseIconView;
    private GameOverStatsView statsDashboardView;
    private Thrive2ndActivity gameEngine;

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

        setContentView(R.layout.activity_thrive);
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView != null) contentView.setPadding(0, 0, 0, 0);
        hideSystemBars();

        final float dp = getResources().getDisplayMetrics().density;
        View root = findViewById(R.id.breakoutRoot);
        RelativeLayout topHUD = findViewById(R.id.topHud);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            if (topHUD != null) { int dp12 = (int) (12 * dp); topHUD.setPadding(dp12 + cutout.left, dp12, dp12 + cutout.right, dp12); }
            if (gameEngine != null) gameEngine.setSafeInsets(cutout.left, cutout.right);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true) ? 1 : 0;

        if (!prefs.contains("thrive_difficulty_v2")) prefs.edit().putInt("thrive_difficulty", 1).putBoolean("thrive_difficulty_v2", true).apply();
        currentDifficulty = prefs.getInt("thrive_difficulty", 1);
        isVibrationEnabled = prefs.getBoolean("thrive_vibration_enabled", true);
        isMusicEnabled = prefs.getBoolean("thrive_music_enabled", true);
        isEnemySoundEnabled = prefs.getBoolean("thrive_enemy_sound_enabled", false);

        int legacyHigh = prefs.getInt("thrive_high_score", 0);
        bestEasy = prefs.getInt("thrive_best_easy", 0);
        bestMedium = Math.max(prefs.getInt("thrive_best_medium", 0), legacyHigh);
        bestDifficult = prefs.getInt("thrive_best_difficult", 0);
        gamesEasy = prefs.getInt("thrive_games_easy", 0);
        gamesMedium = prefs.getInt("thrive_games_medium", 0);
        gamesDifficult = prefs.getInt("thrive_games_difficult", 0);
        highScore = getBestForDifficulty(currentDifficulty);

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        tvScore = findViewById(R.id.tvScore); tvBestScore = findViewById(R.id.tvBestScore);
        tvFinalScore = findViewById(R.id.tvFinalScore); tvTapToStart = findViewById(R.id.tvTapToStart);
        scoreContainer = findViewById(R.id.scoreContainer); tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);

        pauseOverlay = findViewById(R.id.pauseOverlay); gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard), gameOverCard = findViewById(R.id.gameOverCard);

        btnPause = findViewById(R.id.btnPause); pauseIconContainer = findViewById(R.id.pauseIconContainer);
        pauseIconView = new GameIconView(this); pauseIconContainer.addView(pauseIconView);

        Button btnResume = findViewById(R.id.btnResume), btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit), btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration), btnToggleMusic = findViewById(R.id.btnToggleMusic), btnToggleEnemySound = findViewById(R.id.btnToggleEnemySound), btnToggleSpeed = findViewById(R.id.btnToggleSpeed);

        // Distinct Theme Palettes: 0 = Sunny Light, 1 = Moonlight Dark, 2 = Space AMOLED
        int bgColor, cardColor, cardStrokeColor, textColor, subTextColor, primaryBtnColor, primaryBtnText, quitBtnColor, pillBgColor, boxBgColor, iconColor;
        if (themeState == 0) {
            bgColor = Color.parseColor("#88C478"); cardColor = Color.parseColor("#F4F7F2"); cardStrokeColor = Color.parseColor("#C6DEC0");
            textColor = Color.parseColor("#1B3022"); subTextColor = Color.parseColor("#4A6B53");
            primaryBtnColor = Color.parseColor("#2E7D32"); primaryBtnText = Color.WHITE; quitBtnColor = Color.parseColor("#DCE7D9");
            pillBgColor = Color.parseColor("#F4F7F2"); boxBgColor = Color.parseColor("#FFFFFF"); iconColor = Color.parseColor("#1B3022");
        } else if (themeState == 1) {
            bgColor = Color.parseColor("#0B192C"); cardColor = Color.parseColor("#10233E"); cardStrokeColor = Color.parseColor("#234B7A");
            textColor = Color.parseColor("#F0F9FF"); subTextColor = Color.parseColor("#93C5FD");
            primaryBtnColor = Color.parseColor("#0284C7"); primaryBtnText = Color.WHITE; quitBtnColor = Color.parseColor("#1B375D");
            pillBgColor = Color.parseColor("#162E4F"); boxBgColor = Color.parseColor("#BAE6FD"); iconColor = Color.parseColor("#082F49");
        } else {
            bgColor = Color.parseColor("#000000"); cardColor = Color.parseColor("#07090F"); cardStrokeColor = Color.parseColor("#2638BDF8");
            textColor = Color.parseColor("#F8FAFC"); subTextColor = Color.parseColor("#94A3B8");
            primaryBtnColor = Color.parseColor("#38BDF8"); primaryBtnText = Color.parseColor("#041322"); quitBtnColor = Color.parseColor("#151A28");
            pillBgColor = Color.parseColor("#111624"); boxBgColor = Color.parseColor("#38BDF8"); iconColor = Color.parseColor("#001C3A");
        }

        getWindow().setBackgroundDrawable(new ColorDrawable(bgColor));
        if (root != null) root.setBackgroundColor(bgColor);
        if (tvTapToStart != null) { tvTapToStart.setText(TAP_TO_FLIGHT_TEXT); tvTapToStart.setTextColor(textColor); }
        tvScore.setTextColor(textColor);
        tvBestScore.setTextColor(subTextColor);
        tvBestScore.setText("Best: " + highScore);

        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor); tvFinalScore.setTextColor(subTextColor);

        if (btnRestart != null) {
            btnRestart.setBackgroundTintList(ColorStateList.valueOf(primaryBtnColor));
            btnRestart.setTextColor(primaryBtnText);
        }
        if (btnResume != null) {
            btnResume.setBackgroundTintList(ColorStateList.valueOf(primaryBtnColor));
            btnResume.setTextColor(primaryBtnText);
        }
        btnQuit.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuit.setTextColor(textColor);
        if (btnQuitFromPause != null) { btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuitFromPause.setTextColor(textColor); }

        if (pauseCard != null) {
            GradientDrawable gdPause = new GradientDrawable();
            gdPause.setColor(cardColor); gdPause.setCornerRadius(32f * dp);
            gdPause.setStroke((int) (1.5f * dp), cardStrokeColor);
            pauseCard.setBackground(gdPause);
        }

        if (gameOverCard != null) {
            GradientDrawable gdGameOver = new GradientDrawable();
            gdGameOver.setColor(cardColor); gdGameOver.setCornerRadius(28f * dp);
            gdGameOver.setStroke((int) (1.8f * dp), cardStrokeColor);
            gameOverCard.setBackground(gdGameOver);
            int pad = (int) (14 * dp);
            gameOverCard.setPadding(pad, (int) (12 * dp), pad, pad);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            int landW = Math.max(dm.widthPixels, dm.heightPixels), landH = Math.min(dm.widthPixels, dm.heightPixels);
            int targetCardW = Math.min((int) (landW * 0.94f), (int) (820 * dp));
            int targetBodyH = Math.max((int) (232 * dp), Math.min((int) (276 * dp), landH - (int) (72 * dp)));

            if (gameOverCard.getParent() instanceof View) {
                View scrollParent = (View) gameOverCard.getParent();
                scrollParent.setVerticalScrollBarEnabled(false); scrollParent.setHorizontalScrollBarEnabled(false);
                if (scrollParent instanceof ScrollView) ((ScrollView) scrollParent).setFillViewport(true);
                ViewGroup.LayoutParams svLp = scrollParent.getLayoutParams();
                if (svLp != null) { svLp.width = targetCardW; svLp.height = ViewGroup.LayoutParams.WRAP_CONTENT; scrollParent.setLayoutParams(svLp); }
            }

            detachFromParent(tvGameOverTitle); detachFromParent(tvNewHighScoreBanner);
            detachFromParent(tvFinalScore); detachFromParent(btnRestart); detachFromParent(btnQuit);
            gameOverCard.removeAllViews();

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL); headerRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams hdrLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hdrLp.bottomMargin = (int) (10 * dp); headerRow.setLayoutParams(hdrLp);

            tvGameOverTitle.setTextSize(20f);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.rightMargin = (int) (12 * dp); tvGameOverTitle.setLayoutParams(titleLp);
            headerRow.addView(tvGameOverTitle);

            if (tvNewHighScoreBanner != null) {
                tvNewHighScoreBanner.setTextSize(12f);
                LinearLayout.LayoutParams banLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                banLp.rightMargin = (int) (12 * dp); tvNewHighScoreBanner.setLayoutParams(banLp);
                headerRow.addView(tvNewHighScoreBanner);
            }

            tvFinalScore.setTextSize(14.5f); tvFinalScore.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            tvFinalScore.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            headerRow.addView(tvFinalScore);
            gameOverCard.addView(headerRow);

            LinearLayout mainRow = new LinearLayout(this);
            mainRow.setOrientation(LinearLayout.HORIZONTAL); mainRow.setGravity(Gravity.CENTER_VERTICAL);
            mainRow.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, targetBodyH));

            statsDashboardView = new GameOverStatsView(this, themeState);
            LinearLayout.LayoutParams dashLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            dashLp.rightMargin = (int) (10 * dp); statsDashboardView.setLayoutParams(dashLp);
            mainRow.addView(statsDashboardView);

            LinearLayout rightBtnCol = new LinearLayout(this);
            rightBtnCol.setOrientation(LinearLayout.VERTICAL);
            rightBtnCol.setLayoutParams(new LinearLayout.LayoutParams((int) (138 * dp), ViewGroup.LayoutParams.MATCH_PARENT));

            if (btnRestart != null) {
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.15f);
                rLp.bottomMargin = (int) (8 * dp); btnRestart.setLayoutParams(rLp);
                btnRestart.setTextSize(15f); btnRestart.setAllCaps(false);
                mainRow.post(() -> btnRestart.setPadding(0, 0, 0, 0));
                rightBtnCol.addView(btnRestart);
            }
            if (btnQuit != null) {
                LinearLayout.LayoutParams qLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.85f);
                btnQuit.setLayoutParams(qLp); btnQuit.setTextSize(14.5f); btnQuit.setAllCaps(false);
                rightBtnCol.addView(btnQuit);
            }
            mainRow.addView(rightBtnCol);
            gameOverCard.addView(mainRow);
        }

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor));
            pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }

        if (btnToggleVibration != null) {
            btnToggleVibration.setText(getString(R.string.vib_on).replace("ON", (isVibrationEnabled ? "ON" : "OFF")));
            btnToggleVibration.setOnClickListener(v -> {
                isVibrationEnabled = !isVibrationEnabled;
                prefs.edit().putBoolean("thrive_vibration_enabled", isVibrationEnabled).apply();
                btnToggleVibration.setText(getString(R.string.vib_on).replace("ON", (isVibrationEnabled ? "ON" : "OFF")));
                if (gameEngine != null) gameEngine.setVibrationEnabled(isVibrationEnabled);
            });
        }
        if (btnToggleMusic != null) {
            btnToggleMusic.setText(getString(R.string.music_on).replace("ON", (isMusicEnabled ? "ON" : "OFF")));
            btnToggleMusic.setOnClickListener(v -> {
                isMusicEnabled = !isMusicEnabled;
                prefs.edit().putBoolean("thrive_music_enabled", isMusicEnabled).apply();
                btnToggleMusic.setText(getString(R.string.music_on).replace("ON", (isMusicEnabled ? "ON" : "OFF")));
                if (gameEngine != null) gameEngine.setMusicEnabled(isMusicEnabled);
            });
        }
        if (btnToggleEnemySound != null) {
            btnToggleEnemySound.setText("Enemy\nSound: " + (isEnemySoundEnabled ? "ON" : "OFF"));
            btnToggleEnemySound.setOnClickListener(v -> {
                isEnemySoundEnabled = !isEnemySoundEnabled;
                prefs.edit().putBoolean("thrive_enemy_sound_enabled", isEnemySoundEnabled).apply();
                btnToggleEnemySound.setText("Enemy\nSound: " + (isEnemySoundEnabled ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setEnemySoundEnabled(isEnemySoundEnabled);
            });
        }
        if (btnToggleSpeed != null) {
            updateSpeedButtonText(btnToggleSpeed);
            btnToggleSpeed.setOnClickListener(v -> {
                currentDifficulty = (currentDifficulty + 1) % 3;
                prefs.edit().putInt("thrive_difficulty", currentDifficulty).apply();
                updateSpeedButtonText(btnToggleSpeed);
                highScore = getBestForDifficulty(currentDifficulty);
                if (tvBestScore != null) tvBestScore.setText("Best: " + highScore);
                if (gameEngine != null) gameEngine.setDifficulty(currentDifficulty);
            });
        }

        gameEngine = new Thrive2ndActivity(this, themeState);
        gameEngine.setVibrationEnabled(isVibrationEnabled); gameEngine.setMusicEnabled(isMusicEnabled); gameEngine.setEnemySoundEnabled(isEnemySoundEnabled); gameEngine.setDifficulty(currentDifficulty);
        gameContainer.addView(gameEngine);

        gameEngine.setGameListener(new Thrive2ndActivity.GameListener() {
            @Override public void onScoreUpdated(int score) { tvScore.setText("Score: " + score); }

            @Override
            public void onGameOver(int finalScore, int killsDone, int[] weaponUsage, Bitmap deathSnapshot, String deathCause) {
                tvGameOverTitle.setTextColor(textColor);
                SharedPreferences.Editor editor = prefs.edit();
                if (currentDifficulty == 0) { gamesEasy++; editor.putInt("thrive_games_easy", gamesEasy); }
                else if (currentDifficulty == 1) { gamesMedium++; editor.putInt("thrive_games_medium", gamesMedium); }
                else { gamesDifficult++; editor.putInt("thrive_games_difficult", gamesDifficult); }

                int modeBest = getBestForDifficulty(currentDifficulty);
                if (finalScore > modeBest && finalScore > 0) {
                    if (currentDifficulty == 0) { bestEasy = finalScore; editor.putInt("thrive_best_easy", bestEasy); }
                    else if (currentDifficulty == 1) { bestMedium = finalScore; editor.putInt("thrive_best_medium", bestMedium); }
                    else { bestDifficult = finalScore; editor.putInt("thrive_best_difficult", bestDifficult); }
                    highScore = finalScore;
                    editor.putInt("thrive_high_score", Math.max(bestEasy, Math.max(bestMedium, bestDifficult)));
                    tvBestScore.setText("Best: " + highScore);
                    if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else if (tvNewHighScoreBanner != null) {
                    tvNewHighScoreBanner.setVisibility(View.GONE);
                }
                editor.apply();

                String[] modeNames = {"Easy", "Medium", "Difficult"};
                tvFinalScore.setText("Score: " + finalScore + "  •  Kills: " + killsDone + "  (" + modeNames[currentDifficulty] + ")");

                if (statsDashboardView != null) {
                    statsDashboardView.updateStats(bestEasy, bestMedium, bestDifficult,
                            gamesEasy, gamesMedium, gamesDifficult, killsDone, weaponUsage, currentDifficulty, deathSnapshot, deathCause);
                }
                showOverlaySmoothly(gameOverOverlay);
                fadeOutHudSmoothly(btnPause);
            }

            @Override
            public void onGameStarted() {
                if (tvTapToStart != null && tvTapToStart.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    tvTapToStart.setVisibility(View.VISIBLE); scoreContainer.setVisibility(View.GONE); scoreContainer.setAlpha(1f);

                    String tapStr = TAP_TO_FLIGHT_TEXT, curScoreStr = "Score: 0", bestScoreStr = tvBestScore.getText().toString();
                    ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    widthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                    widthAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); }
                    });
                    widthAnimator.setDuration(400); widthAnimator.setInterpolator(new DecelerateInterpolator());

                    ValueAnimator textAnimator = ValueAnimator.ofFloat(0f, 1f);
                    textAnimator.addUpdateListener(anim -> {
                        float f = anim.getAnimatedFraction();
                        if (f < 0.4f) {
                            tvTapToStart.setText(tapStr.substring(0, (int) (tapStr.length() * (1f - (f / 0.4f)))));
                        } else {
                            if (tvTapToStart.getVisibility() == View.VISIBLE) { tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); }
                            float tf = (f - 0.4f) / 0.6f;
                            tvScore.setText(curScoreStr.substring(0, (int) (curScoreStr.length() * tf)));
                            tvBestScore.setText(bestScoreStr.substring(0, (int) (bestScoreStr.length() * tf)));
                        }
                    });
                    textAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) { tvScore.setText(curScoreStr); tvBestScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); }
                    });
                    textAnimator.setDuration(400); widthAnimator.start(); textAnimator.start();
                }
            }
        });

        if (btnPause != null) {
            btnPause.setOnClickListener(v -> { gameEngine.pauseGame(); showOverlaySmoothly(pauseOverlay); fadeOutHudSmoothly(btnPause); pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor); });
        }
        if (btnResume != null) {
            btnResume.setOnClickListener(v -> { hideOverlaySmoothly(pauseOverlay); fadeInHudSmoothly(btnPause); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); gameEngine.resumeGame(); hideSystemBars(); });
        }
        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                hideOverlaySmoothly(gameOverOverlay);
                if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE);
                if (scoreContainer != null && scoreContainer.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    scoreContainer.setVisibility(View.GONE); tvTapToStart.setText(TAP_TO_FLIGHT_TEXT); tvTapToStart.setVisibility(View.VISIBLE);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST),
                            View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    scoreContainer.setVisibility(View.VISIBLE); tvTapToStart.setVisibility(View.GONE);

                    String tapStr = TAP_TO_FLIGHT_TEXT, curScoreStr = tvScore.getText().toString(), bestScoreStr = tvBestScore.getText().toString();
                    ValueAnimator widthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    widthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                    widthAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); }
                    });
                    widthAnimator.setDuration(400); widthAnimator.setInterpolator(new DecelerateInterpolator());

                    ValueAnimator textAnimator = ValueAnimator.ofFloat(0f, 1f);
                    textAnimator.addUpdateListener(anim -> {
                        float f = anim.getAnimatedFraction();
                        if (f < 0.4f) {
                            float ef = 1f - (f / 0.4f);
                            tvScore.setText(curScoreStr.substring(0, (int) (curScoreStr.length() * ef)));
                            tvBestScore.setText(bestScoreStr.substring(0, (int) (bestScoreStr.length() * ef)));
                        } else {
                            if (scoreContainer.getVisibility() == View.VISIBLE) { scoreContainer.setVisibility(View.GONE); tvTapToStart.setVisibility(View.VISIBLE); }
                            tvTapToStart.setText(tapStr.substring(0, (int) (tapStr.length() * ((f - 0.4f) / 0.6f))));
                        }
                    });
                    textAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator a) { tvScore.setText("Score: 0"); tvBestScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); }
                    });
                    textAnimator.setDuration(400); widthAnimator.start(); textAnimator.start();
                } else {
                    if (scoreContainer != null) scoreContainer.setVisibility(View.GONE);
                    if (tvTapToStart != null) { tvTapToStart.setText(TAP_TO_FLIGHT_TEXT); tvTapToStart.setVisibility(View.VISIBLE); }
                    tvScore.setText("Score: 0");
                }
                fadeInHudSmoothly(btnPause); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resetGame(); hideSystemBars();
            });
        }
        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());
    }

    private void detachFromParent(View v) { if (v != null && v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v); }
    private int getBestForDifficulty(int diff) { return (diff == 0) ? bestEasy : (diff == 1 ? bestMedium : bestDifficult); }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        ViewGroup contentView = findViewById(android.R.id.content);
        if (contentView != null) contentView.setPadding(0, 0, 0, 0);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars(); }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); }

    private void updateSpeedButtonText(Button btn) {
        String[] levels = {"Easy", "Medium", "Difficult"};
        btn.setText(getString(R.string.speed_easy).replace("Easy", levels[currentDifficulty]));
    }

    @Override
    public void onBackPressed() {
        if (gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) { if (btnPause != null) { btnPause.performClick(); return; } }
        super.onBackPressed();
    }

    private void showOverlaySmoothly(View v) { if (v != null) { v.setAlpha(0f); v.setVisibility(View.VISIBLE); v.animate().alpha(1f).setDuration(250).start(); } }
    private void hideOverlaySmoothly(View v) { if (v != null) v.animate().alpha(0f).setDuration(250).withEndAction(() -> v.setVisibility(View.GONE)).start(); }
    private void fadeOutHudSmoothly(View p) { if (p != null) p.animate().alpha(0f).setDuration(250).withEndAction(() -> p.setVisibility(View.INVISIBLE)).start(); }
    private void fadeInHudSmoothly(View p) { if (p != null) { p.setAlpha(0f); p.setVisibility(View.VISIBLE); p.animate().alpha(1f).setDuration(250).start(); } }

    @Override public void finish() { super.finish(); overridePendingTransition(0, android.R.anim.fade_out); }

    private GradientDrawable createPillShape(int color) { GradientDrawable s = new GradientDrawable(); s.setColor(color); s.setCornerRadius(1000f); return s; }
    private GradientDrawable createBoxShape(int color) { GradientDrawable s = new GradientDrawable(); s.setColor(color); s.setCornerRadius(30f); return s; }

    @Override
    protected void onPause() {
        super.onPause();
        boolean isPauseMenuVisible = (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE);
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseMenuVisible) { if (btnPause != null) btnPause.performClick(); }
    }

    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0, ICON_PLAY = 1;
        private int iconType = ICON_PAUSE;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public GameIconView(Context context) { super(context); paint.setStyle(Paint.Style.FILL); }
        public void setIcon(int type, int color) { this.iconType = type; paint.setColor(color); invalidate(); }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (iconType == ICON_PAUSE) {
                canvas.drawRoundRect(cx - 7f, cy - 8f, cx - 2f, cy + 8f, 3f, 3f, paint);
                canvas.drawRoundRect(cx + 2f, cy - 8f, cx + 7f, cy + 8f, 3f, 3f, paint);
            } else {
                Path p = new Path(); p.moveTo(cx - 4f, cy - 9f); p.lineTo(cx + 7f, cy); p.lineTo(cx - 4f, cy + 9f); p.close();
                canvas.drawPath(p, paint);
            }
        }
    }

    // =========================================================================
    // THEME-AWARE GAME OVER DASHBOARD (UNIFORM SPACING & CENTERED PIE CHART)
    // =========================================================================
    public static class GameOverStatsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Matrix shaderMatrix = new Matrix();
        private final float dp;
        private final int themeState;

        private int bestEasy, bestMed, bestDiff, gamesEasy, gamesMed, gamesDiff, killsDone, activeDiff;
        private final int[] weaponUsage = new int[9];
        private Bitmap deathSnapshot;
        private BitmapShader snapShader;
        private String deathCauseText = "Killed in Action";

        private static final String[] GUN_LABELS = { "Knife", "Eagle", "M-Gun", "Shotgun", "Rocket", "Flame", "Sniper", "Laser", "Electric" };

        public GameOverStatsView(Context context, int themeState) {
            super(context);
            this.themeState = themeState;
            this.dp = context.getResources().getDisplayMetrics().density;
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        }

        public void updateStats(int bEasy, int bMed, int bDiff, int gEasy, int gMed, int gDiff,
                                int kills, int[] usage, int currentDiff, Bitmap snapshot, String deathCause) {
            this.bestEasy = bEasy; this.bestMed = bMed; this.bestDiff = bDiff;
            this.gamesEasy = gEasy; this.gamesMed = gMed; this.gamesDiff = gDiff;
            this.killsDone = kills; this.activeDiff = currentDiff;
            if (usage != null && usage.length == 9) System.arraycopy(usage, 0, this.weaponUsage, 0, 9);
            this.deathSnapshot = snapshot;
            this.snapShader = (snapshot != null && !snapshot.isRecycled()) ? new BitmapShader(snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) : null;
            this.deathCauseText = (deathCause != null && !deathCause.isEmpty()) ? deathCause : "Killed in Action";
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            // Distinct Palettes per Mode: Light (0), Moonlight Dark (1), Space AMOLED (2)
            int panelBg, panelStroke, textMain, textSub, colEasy, colMed, colDiff, activeShotCol;
            if (themeState == 0) {
                panelBg = Color.parseColor("#E2ECE0"); panelStroke = Color.parseColor("#C5D9C1");
                textMain = Color.parseColor("#1B3022"); textSub = Color.parseColor("#4E6E58");
                colEasy = Color.parseColor("#16A34A"); colMed = Color.parseColor("#0284C7"); colDiff = Color.parseColor("#DC2626");
                activeShotCol = Color.parseColor("#0284C7");
            } else if (themeState == 1) {
                panelBg = Color.parseColor("#183258"); panelStroke = Color.parseColor("#2B5288");
                textMain = Color.parseColor("#F0F9FF"); textSub = Color.parseColor("#93C5FD");
                colEasy = Color.parseColor("#34D399"); colMed = Color.parseColor("#38BDF8"); colDiff = Color.parseColor("#FB7185");
                activeShotCol = Color.parseColor("#38BDF8");
            } else {
                panelBg = Color.parseColor("#121624"); panelStroke = Color.parseColor("#222B45");
                textMain = Color.parseColor("#F8FAFC"); textSub = Color.parseColor("#94A3B8");
                colEasy = Color.parseColor("#4ADE80"); colMed = Color.parseColor("#38BDF8"); colDiff = Color.parseColor("#F43F5E");
                activeShotCol = Color.parseColor("#A855F7");
            }

            float gap = 8f * dp;

            // 1. LEFT PANEL: Last Bullet Death Screenshot + Death Cause Pill
            float snapW = Math.min(h * 0.86f, w * 0.30f);
            rect.set(0, 0, snapW, h);
            paint.setStyle(Paint.Style.FILL); paint.setColor(panelBg);
            canvas.drawRoundRect(rect, 16f * dp, 16f * dp, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f * dp); paint.setColor(panelStroke);
            canvas.drawRoundRect(rect, 16f * dp, 16f * dp, paint);

            float imgPad = 7f * dp;
            rect.set(imgPad, imgPad, snapW - imgPad, h - imgPad);
            if (snapShader != null && deathSnapshot != null && !deathSnapshot.isRecycled()) {
                float scale = Math.max(rect.width() / deathSnapshot.getWidth(), rect.height() / deathSnapshot.getHeight());
                shaderMatrix.reset();
                shaderMatrix.postTranslate(-deathSnapshot.getWidth() / 2f, -deathSnapshot.getHeight() / 2f);
                shaderMatrix.postScale(scale, scale);
                shaderMatrix.postTranslate(rect.centerX(), rect.centerY());
                snapShader.setLocalMatrix(shaderMatrix);
                bmpPaint.setShader(snapShader);
                canvas.drawRoundRect(rect, 12f * dp, 12f * dp, bmpPaint);
                bmpPaint.setShader(null);
            } else {
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#111827"));
                canvas.drawRoundRect(rect, 12f * dp, 12f * dp, paint);
            }

            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f * dp); paint.setColor(colDiff);
            canvas.drawRoundRect(rect, 12f * dp, 12f * dp, paint);

            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#D9000000"));
            RectF tagRect = new RectF(imgPad + 6f * dp, imgPad + 6f * dp, snapW - imgPad - 6f * dp, imgPad + 26f * dp);
            canvas.drawRoundRect(tagRect, 8f * dp, 8f * dp, paint);
            textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(8.2f * dp); textPaint.setColor(Color.parseColor("#FF8787"));
            canvas.drawText(deathCauseText, tagRect.centerX(), tagRect.centerY() + 3f * dp, textPaint);

            // 2. CENTER AREA: High Scores (Top Row) + Uniformly Spaced Pie Chart & Weapon Shots Grid
            float centerX = snapW + gap, centerW = w - centerX;
            float boxW = (centerW - gap * 2f) / 3f, boxH = 44f * dp;
            String[] titles = {"EASY BEST", "MEDIUM BEST", "DIFFICULT BEST"};
            int[] bests = {bestEasy, bestMed, bestDiff}, accents = {colEasy, colMed, colDiff};

            for (int i = 0; i < 3; i++) {
                float left = centerX + i * (boxW + gap);
                rect.set(left, 0, left + boxW, boxH);
                paint.setStyle(Paint.Style.FILL); paint.setColor(panelBg);
                canvas.drawRoundRect(rect, 13f * dp, 13f * dp, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(i == activeDiff ? 2.0f * dp : 1.1f * dp);
                paint.setColor(i == activeDiff ? accents[i] : panelStroke);
                canvas.drawRoundRect(rect, 13f * dp, 13f * dp, paint);

                textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(8.2f * dp); textPaint.setColor(accents[i]);
                canvas.drawText(titles[i], left + boxW / 2f, 15f * dp, textPaint);
                textPaint.setTextSize(13f * dp); textPaint.setColor(textMain);
                canvas.drawText(String.valueOf(bests[i]), left + boxW / 2f, 34f * dp, textPaint);
            }

            float secTop = boxH + gap, secH = h - secTop;
            float pieCardW = centerW * 0.44f, shotsCardW = centerW - pieCardW - gap;

            // Center-Left Card: Games Played Donut Pie Chart with Mathematically Uniform Vertical Spacing
            rect.set(centerX, secTop, centerX + pieCardW, secTop + secH);
            paint.setStyle(Paint.Style.FILL); paint.setColor(panelBg);
            canvas.drawRoundRect(rect, 15f * dp, 15f * dp, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f * dp); paint.setColor(panelStroke);
            canvas.drawRoundRect(rect, 15f * dp, 15f * dp, paint);

            float titleH = 10f * dp, legRowStep = 13.0f * dp, legBlockH = legRowStep * 2f + 9f * dp;
            float maxPieDiam = Math.min(pieCardW * 0.62f, (secH - titleH - legBlockH) * 0.56f);
            float pieR = Math.max(18f * dp, maxPieDiam / 2f);
            // Distribute remaining vertical space into 4 equal gaps: Top, Title->Pie, Pie->Legend, Bottom!
            float uniformGap = Math.max(6f * dp, (secH - (titleH + pieR * 2f + legBlockH)) / 4f);

            float pieCX = centerX + pieCardW / 2f;
            float titleBaseY = secTop + uniformGap + titleH - 1f * dp;
            textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(9.2f * dp); textPaint.setColor(textMain);
            canvas.drawText("GAMES PLAYED", pieCX, titleBaseY, textPaint);

            float pieCY = secTop + uniformGap + titleH + uniformGap + pieR;
            int totalGames = gamesEasy + gamesMed + gamesDiff;

            rect.set(pieCX - pieR, pieCY - pieR, pieCX + pieR, pieCY + pieR);
            paint.setStyle(Paint.Style.FILL);
            if (totalGames <= 0) {
                paint.setColor(textSub); canvas.drawArc(rect, 0f, 360f, true, paint);
            } else {
                float startAngle = -90f;
                int[] counts = {gamesEasy, gamesMed, gamesDiff};
                for (int i = 0; i < 3; i++) {
                    if (counts[i] <= 0) continue;
                    float sweep = (counts[i] * 360f) / totalGames;
                    paint.setColor(accents[i]); canvas.drawArc(rect, startAngle, sweep, true, paint);
                    startAngle += sweep;
                }
            }
            paint.setColor(panelBg); canvas.drawCircle(pieCX, pieCY, pieR * 0.52f, paint);
            textPaint.setTextSize(10f * dp); textPaint.setColor(textMain);
            canvas.drawText(String.valueOf(totalGames), pieCX, pieCY + 3.6f * dp, textPaint);

            // Centered Legend Block below Pie Chart with matching bottom padding
            float legFirstRowY = pieCY + pieR + uniformGap + 8f * dp;
            int[] counts = {gamesEasy, gamesMed, gamesDiff};
            String[] modeNames = {"Easy: ", "Med: ", "Diff: "};
            textPaint.setTextSize(8.4f * dp);
            float maxLegW = 0f;
            String[] legStrings = new String[3];
            for (int i = 0; i < 3; i++) {
                int pct = totalGames > 0 ? Math.round((counts[i] * 100f) / totalGames) : 0;
                legStrings[i] = modeNames[i] + counts[i] + " (" + pct + "%)";
                maxLegW = Math.max(maxLegW, textPaint.measureText(legStrings[i]));
            }
            float blockLeft = pieCX - (maxLegW + 10f * dp) / 2f;
            textPaint.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < 3; i++) {
                float ly = legFirstRowY + i * legRowStep;
                paint.setColor(accents[i]); canvas.drawCircle(blockLeft + 3f * dp, ly - 3.0f * dp, 3.2f * dp, paint);
                textPaint.setColor(textMain);
                canvas.drawText(legStrings[i], blockLeft + 10f * dp, ly, textPaint);
            }

            // Center-Right Card: 3x3 Weapon Shots Used Grid with Uniform Vertical & Horizontal Spacing
            float rightX = centerX + pieCardW + gap;
            rect.set(rightX, secTop, rightX + shotsCardW, secTop + secH);
            paint.setStyle(Paint.Style.FILL); paint.setColor(panelBg);
            canvas.drawRoundRect(rect, 15f * dp, 15f * dp, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.1f * dp); paint.setColor(panelStroke);
            canvas.drawRoundRect(rect, 15f * dp, 15f * dp, paint);

            textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(9.2f * dp); textPaint.setColor(textMain);
            canvas.drawText("WEAPON SHOTS USED", rightX + shotsCardW / 2f, titleBaseY, textPaint);

            float gridTop = titleBaseY + uniformGap * 0.8f;
            float gridBottom = secTop + secH - uniformGap * 0.8f;
            float gridH = gridBottom - gridTop, cellW = shotsCardW / 3f, cellH = gridH / 3f;
            for (int i = 0; i < 9; i++) {
                int r = i / 3, c = i % 3;
                float cx = rightX + c * cellW + cellW / 2f, cy = gridTop + r * cellH + cellH / 2f;
                textPaint.setTextSize(8.2f * dp); textPaint.setColor(textSub);
                canvas.drawText(GUN_LABELS[i], cx, cy - 2.5f * dp, textPaint);
                textPaint.setTextSize(11.8f * dp); textPaint.setColor(weaponUsage[i] > 0 ? activeShotCol : textMain);
                canvas.drawText(String.valueOf(weaponUsage[i]), cx, cy + 11.0f * dp, textPaint);
            }
        }
    }
}