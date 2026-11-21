package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@SuppressWarnings("all") @SuppressLint("SetTextI18n")
public class FlappyBirdActivity extends AppCompatActivity {

    private int themeState, highScore = 0, classicPlayed, classicBest, gooPlayed, gooBest, currentBestScore = 0, iconColor;
    private boolean isVibrationEnabled = true, isSoundEnabled = true, isMusicEnabled = true, isLaunchLoading = true;
    private SharedPreferences prefs;
    private ValueAnimator hudWidthAnimator, hudTextAnimator;
    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner, tvClassicStats, tvGooStats;
    private LinearLayout scoreContainer, btnPause;
    private RelativeLayout pauseOverlay, gameOverOverlay, characterSelectionOverlay, launchLoadingOverlay;
    private FlappyGameEngine gameEngine;
    private FlappyAudioEngine audioEngine;
    private FrameLayout pauseIconContainer, snapshotContainer;
    private GameIconView pauseIconView;
    private ImageView ivDeathSnapshot, ivLoadingIcon;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_flappy_bird);

        ViewCompat.setOnApplyWindowInsetsListener(getWindow().getDecorView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            View contentView = findViewById(android.R.id.content); if (contentView != null) contentView.setPadding(0, 0, 0, 0);
            View root = findViewById(R.id.flappyRoot); if (root != null) root.setPadding(insets.left, 0, insets.right, insets.bottom);
            float density = getResources().getDisplayMetrics().density;
            View topHud = findViewById(R.id.topHud); if (topHud != null) topHud.setPadding((int)(24*density), insets.top + (int)(16*density), (int)(16*density), (int)(12*density));
            TextView[] headers = { findViewById(R.id.tvSelectTitle), findViewById(R.id.tvGameOverTitle), findViewById(R.id.tvPauseTitle) };
            for (TextView tv : headers) if (tv != null) tv.setPadding(0, insets.top + (int)(32 * density), 0, (int)(24 * density));
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true) ? 1 : 0;

        highScore = prefs.getInt("flappy_high_score", 0); classicPlayed = prefs.getInt("classic_played", 0); classicBest = prefs.getInt("classic_best", 0);
        gooPlayed = prefs.getInt("goo_played", 0); gooBest = prefs.getInt("goo_best", 0);
        isVibrationEnabled = prefs.getBoolean("flappy_vibration_enabled", true);
        isSoundEnabled = prefs.getBoolean("flappy_sound_enabled", true);
        isMusicEnabled = prefs.getBoolean("flappy_music_enabled", true);
        currentBestScore = classicBest;

        audioEngine = new FlappyAudioEngine(); audioEngine.setSoundEnabled(isSoundEnabled); audioEngine.setMusicEnabled(isMusicEnabled);

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        btnPause = findViewById(R.id.btnPause); pauseIconContainer = findViewById(R.id.pauseIconContainer);
        tvCurrentScore = findViewById(R.id.tvCurrentScore); tvHighScore = findViewById(R.id.tvHighScore); tvFinalScore = findViewById(R.id.tvFinalScore); tvTapToStart = findViewById(R.id.tvTapToStart);
        scoreContainer = findViewById(R.id.scoreContainer); tvGameOverTitle = findViewById(R.id.tvGameOverTitle); tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner);
        pauseOverlay = findViewById(R.id.pauseOverlay); gameOverOverlay = findViewById(R.id.gameOverOverlay); characterSelectionOverlay = findViewById(R.id.characterSelectionOverlay);
        launchLoadingOverlay = findViewById(R.id.launchLoadingOverlay); ivLoadingIcon = findViewById(R.id.ivLoadingIcon);
        if (characterSelectionOverlay != null) { characterSelectionOverlay.setClickable(true); characterSelectionOverlay.setFocusable(true); }

        LinearLayout pauseCard = findViewById(R.id.pauseCard), gameOverCard = findViewById(R.id.gameOverCard), characterSelectionCard = findViewById(R.id.characterSelectionCard);
        Button btnResume = findViewById(R.id.btnResume), btnRestart = findViewById(R.id.btnRestart), btnQuit = findViewById(R.id.btnQuit), btnQuitFromPause = findViewById(R.id.btnQuitFromPause), btnToggleVibration = findViewById(R.id.btnToggleVibration), btnToggleSound = findViewById(R.id.btnToggleSound), btnToggleMusic = findViewById(R.id.btnToggleMusic);
        LinearLayout btnSelectClassic = findViewById(R.id.btnSelectClassic), btnSelectGoo = findViewById(R.id.btnSelectGoo);
        TextView tvSelectTitle = findViewById(R.id.tvSelectTitle), tvClassicTitle = findViewById(R.id.tvClassicTitle), tvGooTitle = findViewById(R.id.tvGooTitle);
        tvClassicStats = findViewById(R.id.tvClassicStats); tvGooStats = findViewById(R.id.tvGooStats);
        if (tvGooTitle != null) tvGooTitle.setText("Clov Bird");

        ImageView ivClassicPreview = findViewById(R.id.ivClassicPreview), ivGooPreview = findViewById(R.id.ivGooPreview);
        snapshotContainer = findViewById(R.id.snapshotContainer); ivDeathSnapshot = findViewById(R.id.ivDeathSnapshot);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Button[] flatBtns = { btnResume, btnRestart, btnQuit, btnQuitFromPause, btnToggleVibration, btnToggleSound, btnToggleMusic };
            for (Button b : flatBtns) if (b != null) { b.setStateListAnimator(null); b.setElevation(0); }
        }

        int bgColor, cardColor, textColor, subTextColor, quitBtnColor, pillBgColor, boxBgColor;
        if (themeState == 0) { bgColor = Color.parseColor("#FFFFFF"); cardColor = Color.parseColor("#F3F4F6"); textColor = Color.parseColor("#1C1C1E"); subTextColor = Color.parseColor("#6B7280"); quitBtnColor = Color.parseColor("#E5E5EA"); pillBgColor = Color.parseColor("#E5E5EA"); boxBgColor = Color.parseColor("#FFFFFF"); iconColor = Color.parseColor("#333333");
        } else if (themeState == 1) { bgColor = Color.parseColor("#1C1C1E"); cardColor = Color.parseColor("#2C2C2E"); textColor = Color.WHITE; subTextColor = Color.parseColor("#8E8E93"); quitBtnColor = Color.parseColor("#3A3A3C"); pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A");
        } else { bgColor = Color.parseColor("#000000"); cardColor = Color.parseColor("#111827"); textColor = Color.WHITE; subTextColor = Color.parseColor("#9CA3AF"); quitBtnColor = Color.parseColor("#2C2C2E"); pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A"); }

        View root = findViewById(R.id.flappyRoot); root.setBackgroundColor(bgColor);
        if (tvCurrentScore != null) tvCurrentScore.setTextColor(textColor);
        if (tvHighScore != null) { tvHighScore.setTextColor(subTextColor); tvHighScore.setText("Best: " + highScore); }
        if (tvTapToStart != null) tvTapToStart.setTextColor(textColor);
        TextView tvPauseTitle = findViewById(R.id.tvPauseTitle);
        if (tvPauseTitle != null) tvPauseTitle.setTextColor(textColor);
        if (tvGameOverTitle != null) tvGameOverTitle.setTextColor(textColor);
        if (tvFinalScore != null) tvFinalScore.setTextColor(textColor);
        if (tvSelectTitle != null) tvSelectTitle.setTextColor(textColor);
        if (tvClassicTitle != null) tvClassicTitle.setTextColor(textColor);
        if (tvGooTitle != null) tvGooTitle.setTextColor(textColor);
        if (tvClassicStats != null) { tvClassicStats.setTextColor(subTextColor); tvClassicStats.setText("Played: " + classicPlayed + "\nBest: " + classicBest); }
        if (tvGooStats != null) { tvGooStats.setTextColor(subTextColor); tvGooStats.setText("Played: " + gooPlayed + "\nBest: " + gooBest); }

        try {
            if (ivClassicPreview != null) ivClassicPreview.setImageResource(getResources().getIdentifier("flappy_bird_blue", "drawable", getPackageName()));
            if (ivGooPreview != null) ivGooPreview.setImageResource(getResources().getIdentifier("goobird5", "drawable", getPackageName()));
        } catch (Exception ignored) {}

        if (btnQuit != null) { btnQuit.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuit.setTextColor(textColor); }
        if (btnQuitFromPause != null) { btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuitFromPause.setTextColor(textColor); }

        if (btnPause != null) {
            btnPause.setBackground(createPillShape(pillBgColor)); pauseIconContainer.setBackground(createBoxShape(boxBgColor));
            pauseIconView = new GameIconView(this); pauseIconContainer.addView(pauseIconView); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
        }

        float density = getResources().getDisplayMetrics().density;
        setupTopHeader(tvSelectTitle, characterSelectionOverlay, themeState, density);
        setupTopHeader(tvGameOverTitle, gameOverOverlay, themeState, density);
        setupTopHeader(tvPauseTitle, pauseOverlay, themeState, density);

        GradientDrawable gdCard = new GradientDrawable(); gdCard.setColor(cardColor); gdCard.setCornerRadius(60f);
        if (pauseCard != null) { pauseCard.setBackground(gdCard); pauseCard.setPadding((int)(24*density), (int)(32*density), (int)(24*density), (int)(32*density)); }
        if (gameOverCard != null) { gameOverCard.setBackground(gdCard); gameOverCard.setPadding((int)(24*density), (int)(32*density), (int)(24*density), (int)(32*density)); }
        if (characterSelectionCard != null) characterSelectionCard.setBackground(gdCard);

        GradientDrawable btnGd = new GradientDrawable(); btnGd.setColor(pillBgColor); btnGd.setCornerRadius(40f);
        if (btnSelectClassic != null) btnSelectClassic.setBackground(btnGd);
        if (btnSelectGoo != null) btnSelectGoo.setBackground(btnGd);

        if (snapshotContainer != null) {
            ViewGroup.LayoutParams slp = snapshotContainer.getLayoutParams();
            if (slp != null) { slp.width = (int)(100*density); slp.height = (int)(100*density); snapshotContainer.setLayoutParams(slp); }
            GradientDrawable snapBg = new GradientDrawable(); snapBg.setColor(bgColor); snapBg.setCornerRadius(40f); snapBg.setStroke(8, quitBtnColor);
            snapshotContainer.setBackground(snapBg); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) snapshotContainer.setClipToOutline(true);
        }

        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                isVibrationEnabled = !isVibrationEnabled; prefs.edit().putBoolean("flappy_vibration_enabled", isVibrationEnabled).apply();
                btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setVibrationEnabled(isVibrationEnabled);
            });
        }

        if (btnToggleSound != null) {
            btnToggleSound.setText("Sound: " + (isSoundEnabled ? "ON" : "OFF"));
            btnToggleSound.setOnClickListener(v -> {
                isSoundEnabled = !isSoundEnabled; prefs.edit().putBoolean("flappy_sound_enabled", isSoundEnabled).apply();
                btnToggleSound.setText("Sound: " + (isSoundEnabled ? "ON" : "OFF"));
                if (audioEngine != null) audioEngine.setSoundEnabled(isSoundEnabled);
            });
        }

        if (btnToggleMusic != null) {
            btnToggleMusic.setText("Music: " + (isMusicEnabled ? "ON" : "OFF"));
            btnToggleMusic.setOnClickListener(v -> {
                isMusicEnabled = !isMusicEnabled; prefs.edit().putBoolean("flappy_music_enabled", isMusicEnabled).apply();
                btnToggleMusic.setText("Music: " + (isMusicEnabled ? "ON" : "OFF"));
                if (audioEngine != null) audioEngine.setMusicEnabled(isMusicEnabled);
            });
        }

        gameEngine = new FlappyGameEngine(this, themeState, audioEngine); gameEngine.setVibrationEnabled(isVibrationEnabled); gameContainer.addView(gameEngine);

        if (btnSelectClassic != null) {
            btnSelectClassic.setOnClickListener(v -> {
                if (characterSelectionOverlay != null) characterSelectionOverlay.setVisibility(View.GONE);
                scoreContainer.setVisibility(View.GONE); tvTapToStart.setText("Tap to Fly"); tvTapToStart.setVisibility(View.VISIBLE);
                currentBestScore = classicBest; if (tvHighScore != null) tvHighScore.setText("Best: " + currentBestScore);
                if (gameEngine != null) gameEngine.setBirdType(0);
            });
        }

        if (btnSelectGoo != null) {
            btnSelectGoo.setOnClickListener(v -> {
                if (characterSelectionOverlay != null) characterSelectionOverlay.setVisibility(View.GONE);
                scoreContainer.setVisibility(View.GONE); tvTapToStart.setText("Tap to Fly"); tvTapToStart.setVisibility(View.VISIBLE);
                currentBestScore = gooBest; if (tvHighScore != null) tvHighScore.setText("Best: " + currentBestScore);
                if (gameEngine != null) gameEngine.setBirdType(1);
            });
        }

        gameEngine.setGameListener(new FlappyGameEngine.GameListener() {
            @Override public void onScoreUpdated(int score) { if (tvCurrentScore != null) tvCurrentScore.setText("Score: " + score); }
            @Override public void onGameOver(int finalScore, Bitmap snapshot, int type) {
                int cBest = 0;
                if (type == 0) {
                    classicPlayed++; prefs.edit().putInt("classic_played", classicPlayed).apply();
                    if (finalScore > classicBest) { classicBest = finalScore; prefs.edit().putInt("classic_best", classicBest).apply(); if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.VISIBLE); }
                    else if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE);
                    cBest = classicBest; if (tvClassicStats != null) tvClassicStats.setText("Played: " + classicPlayed + "\nBest: " + classicBest);
                } else {
                    gooPlayed++; prefs.edit().putInt("goo_played", gooPlayed).apply();
                    if (finalScore > gooBest) { gooBest = finalScore; prefs.edit().putInt("goo_best", gooBest).apply(); if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.VISIBLE); }
                    else if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE);
                    cBest = gooBest; if (tvGooStats != null) tvGooStats.setText("Played: " + gooPlayed + "\nBest: " + gooBest);
                }
                currentBestScore = cBest; if (tvHighScore != null) tvHighScore.setText("Best: " + cBest);
                if (snapshot != null && ivDeathSnapshot != null && snapshotContainer != null) { ivDeathSnapshot.setImageBitmap(snapshot); snapshotContainer.setVisibility(View.VISIBLE); }
                else if (snapshotContainer != null) snapshotContainer.setVisibility(View.GONE);
                if (tvFinalScore != null) tvFinalScore.setText("Score: " + finalScore);
                showOverlaySmoothly(gameOverOverlay); fadeOutHudSmoothly(btnPause);
            }
            @Override public void onGameStarted() {
                if (hudWidthAnimator != null) hudWidthAnimator.cancel();
                if (hudTextAnimator != null) hudTextAnimator.cancel();
                if (tvTapToStart != null && tvTapToStart.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); tvCurrentScore.setText("Score: 0"); tvHighScore.setText("Best: " + currentBestScore);
                    ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams(); tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(tempLp);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    tempLp.width = startWidth; btnPause.setLayoutParams(tempLp);
                    tvTapToStart.setVisibility(View.VISIBLE); scoreContainer.setVisibility(View.GONE); scoreContainer.setAlpha(1f);

                    String tapStr = "Tap to Fly", curScoreStr = "Score: 0", bestScoreStr = "Best: " + currentBestScore;
                    hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    hudWidthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                    hudWidthAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); } });
                    hudWidthAnimator.setDuration(400); hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                    hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                    hudTextAnimator.addUpdateListener(anim -> {
                        float fraction = anim.getAnimatedFraction();
                        if (fraction < 0.4f) { int tapLen = (int)(tapStr.length() * (1f - (fraction / 0.4f))); tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min(tapLen, tapStr.length())))); }
                        else {
                            if (tvTapToStart.getVisibility() == View.VISIBLE) { tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); }
                            float tf = (fraction - 0.4f) / 0.6f;
                            tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min((int)(curScoreStr.length() * tf), curScoreStr.length()))));
                            tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min((int)(bestScoreStr.length() * tf), bestScoreStr.length()))));
                        }
                    });
                    hudTextAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { tvCurrentScore.setText(curScoreStr); tvHighScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); } });
                    hudTextAnimator.setDuration(400); hudWidthAnimator.start(); hudTextAnimator.start();
                }
            }
        });

        if (btnPause != null) btnPause.setOnClickListener(v -> { gameEngine.pauseGame(); pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor); showOverlaySmoothly(pauseOverlay); fadeOutHudSmoothly(btnPause); });
        if (btnResume != null) btnResume.setOnClickListener(v -> { hideOverlaySmoothly(pauseOverlay); fadeInHudSmoothly(btnPause); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); gameEngine.resumeGame(); });
        if (btnRestart != null) btnRestart.setOnClickListener(v -> {
            hideOverlaySmoothly(gameOverOverlay);
            if (tvNewHighScoreBanner != null) tvNewHighScoreBanner.setVisibility(View.GONE);
            if (hudWidthAnimator != null) hudWidthAnimator.cancel();
            if (hudTextAnimator != null) hudTextAnimator.cancel();
            if (scoreContainer != null && scoreContainer.getVisibility() == View.VISIBLE) {
                final int startWidth = btnPause.getWidth();
                scoreContainer.setVisibility(View.GONE); tvTapToStart.setText("Tap to Fly"); tvTapToStart.setVisibility(View.VISIBLE);
                ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams(); tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(tempLp);
                btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(((View)btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                final int targetWidth = btnPause.getMeasuredWidth();
                tempLp.width = startWidth; btnPause.setLayoutParams(tempLp); scoreContainer.setVisibility(View.VISIBLE); tvTapToStart.setVisibility(View.GONE);

                final String tapStr = "Tap to Fly", rawCurScore = tvCurrentScore.getText().toString(), curScoreStr = rawCurScore.isEmpty() ? "Score: 0" : rawCurScore, bestScoreStr = "Best: " + currentBestScore;
                hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                hudWidthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                hudWidthAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); } });
                hudWidthAnimator.setDuration(400); hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                hudTextAnimator.addUpdateListener(anim -> {
                    float fraction = anim.getAnimatedFraction();
                    if (fraction < 0.4f) {
                        float ef = 1f - (fraction / 0.4f);
                        tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min((int)(curScoreStr.length() * ef), curScoreStr.length()))));
                        tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min((int)(bestScoreStr.length() * ef), bestScoreStr.length()))));
                    } else {
                        if (scoreContainer.getVisibility() == View.VISIBLE) { scoreContainer.setVisibility(View.GONE); tvTapToStart.setVisibility(View.VISIBLE); }
                        float tf = (fraction - 0.4f) / 0.6f;
                        tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min((int)(tapStr.length() * tf), tapStr.length()))));
                    }
                });
                hudTextAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { tvCurrentScore.setText("Score: 0"); tvHighScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); scoreContainer.setVisibility(View.GONE); tvTapToStart.setVisibility(View.VISIBLE); } });
                hudTextAnimator.setDuration(400); hudWidthAnimator.start(); hudTextAnimator.start();
            } else {
                if (scoreContainer != null) scoreContainer.setVisibility(View.GONE);
                if (tvTapToStart != null) { tvTapToStart.setText("Tap to Fly"); tvTapToStart.setVisibility(View.VISIBLE); }
                if (tvCurrentScore != null) tvCurrentScore.setText("Score: 0");
            }
            fadeInHudSmoothly(btnPause); if (pauseIconView != null) pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); gameEngine.resetGame();
        });
        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());

        startLaunchLoadingAnimation(bgColor);
    }

    private void startLaunchLoadingAnimation(int splashBgColor) {
        if (launchLoadingOverlay == null || ivLoadingIcon == null) { isLaunchLoading = false; applySeamlessStatusBar(); if (audioEngine != null) audioEngine.onIntroFinished(); return; }
        isLaunchLoading = true; launchLoadingOverlay.setBackgroundColor(splashBgColor); launchLoadingOverlay.setAlpha(1f); launchLoadingOverlay.setVisibility(View.VISIBLE); applyLoadingStatusBar(splashBgColor);
        try { int resId = getResources().getIdentifier("flappy_preview", "drawable", getPackageName()); if (resId == 0) resId = getResources().getIdentifier("flappy_bird_blue", "drawable", getPackageName()); if (resId != 0) ivLoadingIcon.setImageResource(resId); } catch (Exception ignored) {}
        ivLoadingIcon.setScaleX(0.70f); ivLoadingIcon.setScaleY(0.70f); ivLoadingIcon.setAlpha(0f);
        ivLoadingIcon.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(new OvershootInterpolator(1.35f)).withEndAction(() -> {
            if (isFinishing() || isDestroyed()) return;
            ivLoadingIcon.animate().scaleX(1.06f).scaleY(1.06f).setDuration(240).withEndAction(() -> {
                if (isFinishing() || isDestroyed()) return;
                ivLoadingIcon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(220).withEndAction(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    ivLoadingIcon.animate().scaleX(1.14f).scaleY(1.14f).alpha(0f).setDuration(260).start();
                    launchLoadingOverlay.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                        launchLoadingOverlay.setVisibility(View.GONE); isLaunchLoading = false; applySeamlessStatusBar();
                        if (audioEngine != null) audioEngine.onIntroFinished();
                    }).start();
                }).start();
            }).start();
        }).start();
    }

    private void applyLoadingStatusBar(int bgColor) {
        android.view.Window window = getWindow(); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(Color.TRANSPARENT); window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        if (themeState == 0) window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void applySeamlessStatusBar() {
        if (isLaunchLoading) return;
        getWindow().getDecorView().postDelayed(() -> {
            if (isLaunchLoading || isFinishing() || isDestroyed()) return;
            android.view.Window window = getWindow(); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS); window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS); window.setStatusBarColor(Color.TRANSPARENT);
            if (themeState == 0) { window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#6DD5FA"))); window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); }
            else if (themeState == 1) { window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#0F2027"))); window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); }
            else { window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.parseColor("#000000"))); window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN); }
        }, 100);
    }

    private void setupTopHeader(TextView tv, RelativeLayout overlay, int themeState, float density) {
        if (tv == null || overlay == null) return;
        ViewGroup parent = (ViewGroup) tv.getParent(); if (parent != null) parent.removeView(tv);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP); tv.setLayoutParams(params);
        int bgColor = (themeState == 0) ? Color.parseColor("#6DD5FA") : (themeState == 1 ? Color.parseColor("#0F2027") : Color.parseColor("#000000"));
        int textColor = (themeState == 0) ? Color.parseColor("#1C1C1E") : Color.WHITE;
        GradientDrawable bg = new GradientDrawable(); bg.setColor(bgColor); bg.setCornerRadii(new float[]{0,0, 0,0, 60f,60f, 60f,60f});
        tv.setBackground(bg); tv.setTextColor(textColor); tv.setPadding(0, (int)(55 * density), 0, (int)(24 * density));
        tv.setGravity(android.view.Gravity.CENTER); tv.setTextSize(26f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) tv.setElevation(10f * density);
        overlay.addView(tv);
    }

    @Override protected void onResume() { super.onResume(); if (!isLaunchLoading) applySeamlessStatusBar(); if (audioEngine != null) audioEngine.onHostResume(); }
    @Override protected void onPause() {
        super.onPause(); if (audioEngine != null) audioEngine.onHostPause();
        if (pauseOverlay != null) { boolean isPauseMenuVisible = (pauseOverlay.getVisibility() == View.VISIBLE); if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseMenuVisible) { if (btnPause != null) btnPause.performClick(); } }
    }
    @Override protected void onDestroy() { super.onDestroy(); if (audioEngine != null) audioEngine.release(); }

    @Override public void onBackPressed() {
        if (launchLoadingOverlay != null && launchLoadingOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (characterSelectionOverlay != null && characterSelectionOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE) { finish(); return; }
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver()) { if (btnPause != null) { btnPause.performClick(); return; } }
        super.onBackPressed();
    }

    private void showOverlaySmoothly(View overlay) { if (overlay == null) return; overlay.setAlpha(0f); overlay.setVisibility(View.VISIBLE); overlay.animate().alpha(1f).setDuration(250).start(); }
    private void hideOverlaySmoothly(View overlay) { if (overlay == null) return; overlay.animate().alpha(0f).setDuration(250).withEndAction(() -> overlay.setVisibility(View.GONE)).start(); }
    private void fadeOutHudSmoothly(View hudView) { if (hudView != null) hudView.animate().alpha(0f).setDuration(250).withEndAction(() -> hudView.setVisibility(View.INVISIBLE)).start(); }
    private void fadeInHudSmoothly(View hudView) { if (hudView != null) { hudView.setAlpha(0f); hudView.setVisibility(View.VISIBLE); hudView.animate().alpha(1f).setDuration(250).start(); } }
    @Override public void finish() { super.finish(); overridePendingTransition(0, android.R.anim.fade_out); }
    private GradientDrawable createPillShape(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(1000f); return shape; }
    private GradientDrawable createBoxShape(int color) { GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(30f); return shape; }
    @Override public void onConfigurationChanged(@NonNull Configuration newConfig) { super.onConfigurationChanged(newConfig); applySeamlessStatusBar(); }

    public static class FlappyAudioEngine {
        private static final int SAMPLE_RATE = 44100;
        private volatile boolean soundEnabled = true, musicEnabled = true, hostActive = false, introFinished = false, released = false, pcmReady = false;
        private volatile short[] classicHappyPcm, clovHappyPcm, classicCryPcm, clovCryPcm, powerUpPcm, musicLoopPcm;
        private final Object voiceLock = new Object();
        private final short[][] voicePcms = new short[6][];
        private final int[] voicePos = new int[6];
        private int nextVoiceSlot = 0, musicPos = 0;
        private boolean[] currentStopFlag = null;
        private AudioTrack activeTrack = null;

        public FlappyAudioEngine() {
            Thread initThread = new Thread(() -> {
                if (released) return;
                short[] cHappy = buildClassicHappyPcm(); if (released) return;
                short[] vHappy = buildClovHappyPcm(); if (released) return;
                short[] cCry = buildClassicCryPcm(); if (released) return;
                short[] vCry = buildClovCryPcm(); if (released) return;
                short[] pUp = buildPowerUpPcm(); if (released) return;
                short[] mLoop = buildMusicLoopPcm(); if (released) return;
                classicHappyPcm = cHappy; clovHappyPcm = vHappy; classicCryPcm = cCry; clovCryPcm = vCry; powerUpPcm = pUp; musicLoopPcm = mLoop;
                pcmReady = true;
                updateAudioState();
            });
            initThread.setDaemon(true); initThread.start();
        }

        public synchronized void setSoundEnabled(boolean enabled) {
            this.soundEnabled = enabled;
            if (!enabled) { synchronized (voiceLock) { Arrays.fill(voicePcms, null); } }
            updateAudioState();
        }

        public synchronized void setMusicEnabled(boolean enabled) { this.musicEnabled = enabled; if (!enabled) musicPos = 0; updateAudioState(); }
        public synchronized void onIntroFinished() { introFinished = true; updateAudioState(); }
        public synchronized void onHostResume() { hostActive = true; updateAudioState(); }
        public synchronized void onHostPause() { hostActive = false; stopAudioLoop(); }
        public synchronized void release() { released = true; hostActive = false; stopAudioLoop(); }

        private synchronized void updateAudioState() {
            boolean needStream = hostActive && !released && introFinished && pcmReady && (musicEnabled || soundEnabled);
            if (needStream) { if (currentStopFlag == null) startAudioLoop(); } else { stopAudioLoop(); }
        }

        private AudioTrack createStreamTrack(int bufferBytes) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return new AudioTrack(
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                        new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                        bufferBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            }
            return new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes, AudioTrack.MODE_STREAM);
        }

        private short[] buildClassicHappyPcm() {
            int total = (int) (SAMPLE_RATE * 0.25f); short[] pcm = new short[total]; double ph1 = 0.0, ph2 = 0.0;
            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE, sample = 0.0;
                if (t < 0.095) {
                    double u = t / 0.095, env = Math.sin(u * Math.PI) * Math.pow(1.0 - 0.25 * u, 0.7), freq = 1850.0 + 1150.0 * Math.pow(u, 0.76) + Math.sin(2.0 * Math.PI * 56.0 * t) * 60.0;
                    ph1 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph1) * 0.74 + Math.sin(ph1 * 2.0) * 0.20 + Math.sin(ph1 * 3.0) * 0.06) * env * 0.85;
                }
                if (t >= 0.11 && t < 0.245) {
                    double u = (t - 0.11) / 0.135, env = Math.min(1.0, u / 0.12) * Math.pow(1.0 - u, 1.10), freq = 2300.0 + 1350.0 * Math.sin(u * Math.PI * 0.65) + Math.sin(2.0 * Math.PI * 64.0 * t) * 75.0;
                    ph2 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph2) * 0.72 + Math.sin(ph2 * 2.0) * 0.22 + Math.sin(ph2 * 3.0) * 0.06) * env * 0.96;
                }
                pcm[i] = (short) Math.max(-32767, Math.min(32767, (int) (sample * 24500.0)));
            }
            return pcm;
        }

        private short[] buildClovHappyPcm() {
            int total = (int) (SAMPLE_RATE * 0.28f); short[] pcm = new short[total]; double ph1 = 0.0, ph2 = 0.0, ph3 = 0.0;
            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE, sample = 0.0;
                if (t < 0.075) {
                    double u = t / 0.075, env = Math.sin(u * Math.PI), freq = 920.0 + 480.0 * u + Math.sin(2.0 * Math.PI * 36.0 * t) * 52.0;
                    ph1 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph1) * 0.65 + Math.sin(ph1 * 2.0) * 0.25 + Math.sin(ph1 * 3.0) * 0.10) * env * 0.82;
                }
                if (t >= 0.08 && t < 0.17) {
                    double u = (t - 0.08) / 0.09, env = Math.sin(u * Math.PI), freq = 1240.0 + 540.0 * u + Math.sin(2.0 * Math.PI * 44.0 * t) * 68.0;
                    ph2 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph2) * 0.64 + Math.sin(ph2 * 2.0) * 0.26 + Math.sin(ph2 * 3.0) * 0.10) * env * 0.88;
                }
                if (t >= 0.17 && t < 0.275) {
                    double u = (t - 0.17) / 0.105, env = Math.min(1.0, u / 0.14) * Math.pow(1.0 - u, 1.20), freq = 1580.0 + 660.0 * Math.sin(u * Math.PI * 0.6) + Math.sin(2.0 * Math.PI * 48.0 * t) * 88.0;
                    ph3 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph3) * 0.62 + Math.sin(ph3 * 2.0) * 0.28 + Math.sin(ph3 * 3.0) * 0.10) * env * 0.94;
                }
                pcm[i] = (short) Math.max(-32767, Math.min(32767, (int) (sample * 24500.0)));
            }
            return pcm;
        }

        private short[] buildClassicCryPcm() {
            int total = (int) (SAMPLE_RATE * 0.52f); short[] pcm = new short[total]; double ph1 = 0.0, ph2 = 0.0, phThud = 0.0;
            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE, sample = 0.0;
                if (t < 0.055) { double env = Math.exp(-t * 50.0); phThud += 2.0 * Math.PI * (160.0 - 95.0 * (t / 0.055)) / SAMPLE_RATE; sample += (Math.sin(phThud) * 0.68 + (Math.random() - 0.5) * 0.24) * env; }
                if (t >= 0.025 && t < 0.31) {
                    double u = (t - 0.025) / 0.285, env = Math.min(1.0, u / 0.07) * Math.pow(1.0 - u, 1.25), freq = 2720.0 * Math.pow(0.44, u) + Math.sin(2.0 * Math.PI * 40.0 * t) * (195.0 * (1.0 - 0.35 * u));
                    ph1 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph1) * 0.72 + Math.sin(ph1 * 2.0) * 0.22 + Math.sin(ph1 * 3.0) * 0.06) * env * 0.90;
                }
                if (t >= 0.33 && t < 0.50) {
                    double u = (t - 0.33) / 0.17, env = Math.min(1.0, u / 0.12) * Math.pow(1.0 - u, 1.50), freq = 1620.0 - 700.0 * u + Math.sin(2.0 * Math.PI * 32.0 * t) * 90.0;
                    ph2 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph2) * 0.78 + Math.sin(ph2 * 2.0) * 0.18) * env * 0.58;
                }
                pcm[i] = (short) Math.max(-32767, Math.min(32767, (int) (sample * 27500.0)));
            }
            return pcm;
        }

        private short[] buildClovCryPcm() {
            int total = (int) (SAMPLE_RATE * 0.56f); short[] pcm = new short[total]; double ph1 = 0.0, ph2 = 0.0, phClang = 0.0, phThud = 0.0;
            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE, sample = 0.0;
                if (t < 0.075) {
                    double env = Math.exp(-t * 40.0); phThud += 2.0 * Math.PI * (125.0 - 72.0 * (t / 0.075)) / SAMPLE_RATE; phClang += 2.0 * Math.PI * (540.0 - 150.0 * (t / 0.075)) / SAMPLE_RATE;
                    sample += (Math.sin(phThud) * 0.62 + Math.sin(phClang) * 0.26 + (Math.random() - 0.5) * 0.26) * env;
                }
                if (t >= 0.03 && t < 0.34) {
                    double u = (t - 0.03) / 0.31, env = Math.min(1.0, u / 0.07) * Math.pow(1.0 - u, 1.15), freq = 1520.0 * Math.pow(0.37, u) + Math.sin(2.0 * Math.PI * 24.0 * t) * (150.0 * (1.0 - 0.25 * u));
                    ph1 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += Math.tanh((Math.sin(ph1) * 0.56 + Math.sin(ph1 * 2.0) * 0.28 + Math.sin(ph1 * 3.0) * 0.16) * 1.45) * env * 0.88;
                }
                if (t >= 0.35 && t < 0.54) {
                    double u = (t - 0.35) / 0.19, env = Math.min(1.0, u / 0.10) * Math.pow(1.0 - u, 1.40), freq = 780.0 - 400.0 * u + Math.sin(2.0 * Math.PI * 19.0 * t) * 60.0;
                    ph2 += 2.0 * Math.PI * freq / SAMPLE_RATE; sample += (Math.sin(ph2) * 0.65 + Math.sin(ph2 * 2.0) * 0.25 + Math.sin(ph2 * 3.0) * 0.10) * env * 0.68;
                }
                pcm[i] = (short) Math.max(-32767, Math.min(32767, (int) (sample * 27000.0)));
            }
            return pcm;
        }

        private short[] buildPowerUpPcm() {
            int total = (int) (SAMPLE_RATE * 0.96f); short[] pcm = new short[total];
            double[] arpFreqs = {523.25, 659.25, 783.99, 1046.50, 1318.51, 1567.98, 2093.00, 2637.02};
            double[] arpStart = {0.00, 0.055, 0.11, 0.165, 0.22, 0.275, 0.33, 0.40};
            double phSweep = 0.0;
            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE, sample = 0.0;
                double padEnv = Math.min(1.0, t / 0.10) * Math.exp(-Math.max(0.0, t - 0.22) * 3.0);
                double pad = Math.sin(2.0 * Math.PI * 261.63 * t) * 0.34 + Math.sin(2.0 * Math.PI * 392.00 * t) * 0.28 + Math.sin(2.0 * Math.PI * 523.25 * t) * 0.22 + Math.sin(2.0 * Math.PI * 659.25 * t) * 0.16;
                sample += Math.tanh(pad * 1.15) * padEnv * 0.44;
                if (t < 0.48) { double u = t / 0.48, swEnv = Math.sin(u * Math.PI) * 0.24, swFreq = 320.0 * Math.pow(5.8, u); phSweep += 2.0 * Math.PI * swFreq / SAMPLE_RATE; sample += (Math.sin(phSweep) * 0.72 + Math.sin(phSweep * 1.5) * 0.28) * swEnv; }
                for (int n = 0; n < arpFreqs.length; n++) {
                    double dt = t - arpStart[n];
                    if (dt >= 0.0) {
                        double env = Math.min(1.0, dt / 0.008) * Math.exp(-dt * (n == arpFreqs.length - 1 ? 3.2 : 6.2)), f = arpFreqs[n] * (1.0 + 0.004 * Math.sin(2.0 * Math.PI * 7.5 * dt));
                        sample += (Math.sin(2.0 * Math.PI * f * dt) * 0.68 + Math.sin(2.0 * Math.PI * f * 2.0 * dt) * 0.22 + Math.sin(2.0 * Math.PI * f * 3.0 * dt) * 0.10) * env * (n >= 6 ? 0.34 : 0.24);
                    }
                }
                pcm[i] = (short) Math.max(-32767, Math.min(32767, (int) (Math.tanh(sample * 1.12) * 27500.0)));
            }
            return pcm;
        }

        private short[] buildMusicLoopPcm() {
            double beatSec = 0.40;
            double[] melody = {523.25, 659.25, 783.99, 659.25, 587.33, 783.99, 880.00, 783.99, 659.25, 783.99, 1046.50, 880.00, 783.99, 659.25, 587.33, 659.25};
            double[] bass   = {261.63, 261.63, 261.63, 261.63, 293.66, 293.66, 293.66, 293.66, 220.00, 220.00, 220.00, 220.00, 196.00, 196.00, 196.00, 196.00};
            int stepSamples = (int) (SAMPLE_RATE * beatSec), total = stepSamples * melody.length; short[] pcm = new short[total];
            for (int n = 0; n < melody.length; n++) {
                double fM = melody[n], fB = bass[n];
                for (int j = 0; j < stepSamples; j++) {
                    double t = j / (double) SAMPLE_RATE, envM = Math.min(1.0, t / 0.015) * Math.exp(-t * 5.8), envB = Math.min(1.0, t / 0.025) * Math.exp(-t * 2.4);
                    double toneM = (Math.sin(2.0 * Math.PI * fM * t) + 0.25 * Math.sin(4.0 * Math.PI * fM * t)) * envM, toneB = Math.sin(2.0 * Math.PI * fB * t) * envB;
                    pcm[n * stepSamples + j] = (short) Math.max(-32767, Math.min(32767, (int) ((toneM * 0.52 + toneB * 0.42) * 5200.0)));
                }
            }
            return pcm;
        }

        private void queueVoice(final short[] pcm) {
            if (!soundEnabled || released || pcm == null) return;
            synchronized (voiceLock) {
                int slot = -1;
                for (int i = 0; i < voicePcms.length; i++) { if (voicePcms[i] == null) { slot = i; break; } }
                if (slot == -1) { slot = nextVoiceSlot; nextVoiceSlot = (nextVoiceSlot + 1) % voicePcms.length; }
                voicePcms[slot] = pcm; voicePos[slot] = 0;
            }
            updateAudioState();
        }

        public void playBirdHappy(int birdType) { queueVoice(birdType == 1 ? clovHappyPcm : classicHappyPcm); }
        public void playBirdCry(int birdType) { queueVoice(birdType == 1 ? clovCryPcm : classicCryPcm); }
        public void playBirdCry() { playBirdCry(0); }
        public void playPowerUp() { queueVoice(powerUpPcm); }

        private synchronized void startAudioLoop() {
            if (currentStopFlag != null || released || !pcmReady) return;
            final boolean[] stopFlag = { false }; currentStopFlag = stopFlag;
            Thread mixerThread = new Thread(() -> {
                AudioTrack track = null;
                try {
                    int bufBytes = Math.max(AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
                    track = createStreamTrack(bufBytes);
                    synchronized (FlappyAudioEngine.this) { if (stopFlag[0] || released) { track.release(); return; } activeTrack = track; }
                    if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) track.setVolume(1.0f);
                        track.play(); short[] mixBuf = new short[512];
                        while (!stopFlag[0] && hostActive && !released) {
                            short[] mLoop = musicLoopPcm;
                            if (musicEnabled && introFinished && mLoop != null) { for (int i = 0; i < mixBuf.length; i++) { mixBuf[i] = mLoop[musicPos]; if (++musicPos >= mLoop.length) musicPos = 0; } }
                            else { Arrays.fill(mixBuf, (short) 0); }
                            if (soundEnabled) {
                                synchronized (voiceLock) {
                                    for (int v = 0; v < voicePcms.length; v++) {
                                        short[] vp = voicePcms[v];
                                        if (vp != null) {
                                            int p = voicePos[v], len = Math.min(mixBuf.length, vp.length - p);
                                            for (int i = 0; i < len; i++) { int s = mixBuf[i] + vp[p + i]; mixBuf[i] = (short) (s > 32767 ? 32767 : (s < -32767 ? -32767 : s)); }
                                            p += len; if (p >= vp.length) voicePcms[v] = null; else voicePos[v] = p;
                                        }
                                    }
                                }
                            }
                            if (track.write(mixBuf, 0, mixBuf.length) < 0) break;
                        }
                    }
                } catch (Throwable ignored) {}
                finally {
                    synchronized (FlappyAudioEngine.this) { if (activeTrack == track) activeTrack = null; if (currentStopFlag == stopFlag) currentStopFlag = null; }
                    try { if (track != null) { track.pause(); track.flush(); track.stop(); track.release(); } } catch (Throwable ignored) {}
                }
            });
            mixerThread.setDaemon(true); mixerThread.start();
        }

        private synchronized void stopAudioLoop() {
            if (currentStopFlag != null) { currentStopFlag[0] = true; currentStopFlag = null; }
            try { if (activeTrack != null) { activeTrack.pause(); activeTrack.flush(); } } catch (Throwable ignored) {}
        }
    }

    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0, ICON_PLAY = 1;
        private int iconType = ICON_PAUSE; private Paint paint;
        public GameIconView(Context context) { super(context); init(); }
        private void init() { paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        public void setIcon(int type, int color) { this.iconType = type; paint.setColor(color); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (iconType == ICON_PAUSE) { paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(cx - 7f, cy - 8f, cx - 2f, cy + 8f, 3f, 3f, paint); canvas.drawRoundRect(cx + 2f, cy - 8f, cx + 7f, cy + 8f, 3f, 3f, paint); }
            else if (iconType == ICON_PLAY) { paint.setStyle(Paint.Style.FILL); Path p = new Path(); p.moveTo(cx - 4f, cy - 9f); p.lineTo(cx + 7f, cy); p.lineTo(cx - 4f, cy + 9f); p.close(); canvas.drawPath(p, paint); }
        }
    }

    public static class FlappyGameEngine extends View implements Choreographer.FrameCallback {

        private float screenW, screenH, refW, refH, birdX, birdY, birdVelocity, birdRadius, wormRadius, gravity, jumpStrength, pipeWidth, pipeGap, terminalVelocity, pipeSpeed, basePipeSpeed, maxPipeSpeed, parallaxScroll = 0f;
        private long lastFrameTime = 0, accumulator = 0, immunityEndTime = 0, shieldStartTime = 0;
        private static final long TIME_STEP_NS = 1000000000L / 60L;
        private final List<Pipe> pipes = new ArrayList<>();
        private boolean playing = false, paused = false, gameOver = false, vibrationEnabled = true, isImmune = false, isReady = false, gooDying = false, shootingStarActive = false;
        private volatile boolean detached = false, cityBuildPending = false;
        private volatile int bgBuildGen = 0;
        private int score = 0, pointsSinceLastWorm = 0, birdType = 0, currentFrameIndex = 0, sequenceIndex = 0, trailHead = 0, trailCount = 0, shootingStarCooldown = 140;
        private float sparkleAngle = 0f, animationTick = 0, gooDeathSpin = 0f, layerTileW = 0f, bgAmbientTick = 0f, ssX, ssY, ssVx, ssVy, ssLife, ssMaxLife;
        private Bitmap[] birdBitmaps;
        private Bitmap immuneBmp, suppressBmp, deathSnapshot = null, celestialBmp, farCloudBmp, nearCloudBmp, farMountainBmp, midMountainBmp, foreForestBmp, cityBackBmp, cityMidBmp, cityFrontBmp, goldSegBmp, goldHeadBmp, goldAuraBmp;
        private Bitmap shieldGoldHaloBmp, shieldCosmicHaloBmp, shieldCrimsonHaloBmp;
        private Bitmap classicPipeBodyBmp, classicPipeCapBmp, clovPipeBodyBmp, clovPipeCapBmp, elecFlashBmp;
        private final Matrix birdMatrix = new Matrix();
        private int[] flapSequence;

        private static final int MAX_TRAIL = 15;
        private final TrailPoint[] trail = new TrailPoint[MAX_TRAIL];
        private final DustParticle[] dustParticles = new DustParticle[10];
        private final RectF hitRect = new RectF(), tBodyRect = new RectF(), tCapRect = new RectF(), bCapRect = new RectF(), bBodyRect = new RectF(), topCollision = new RectF(), bottomCollision = new RectF(), saucerRect = new RectF(), bgTileRect = new RectF(), wormDrawRect = new RectF(), shieldOval = new RectF(), pipeTempRect = new RectF();

        private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), gooTrailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG), starPaint = new Paint(Paint.ANTI_ALIAS_FLAG), bgBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint pipeDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG), pipeShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), fallbackBirdPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wormGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormSparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormRayPaint = new Paint(Paint.ANTI_ALIAS_FLAG), wormSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shieldBloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG), shieldStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG), shieldCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG), shieldFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG), shieldNodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hudBg = new Paint(Paint.ANTI_ALIAS_FLAG), barBg = new Paint(Paint.ANTI_ALIAS_FLAG), barFill = new Paint(Paint.ANTI_ALIAS_FLAG), timeText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint elecCoronaPaint = new Paint(Paint.ANTI_ALIAS_FLAG), elecGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG), elecInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG), elecWhitePaint = new Paint(Paint.ANTI_ALIAS_FLAG), elecSparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shootingStarPaint = new Paint(Paint.ANTI_ALIAS_FLAG), fireflyPaint = new Paint(Paint.ANTI_ALIAS_FLAG), celestialGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final List<Star> stars = new ArrayList<>();
        private final List<Firefly> fireflies = new ArrayList<>();
        private final Path electricPath = new Path(), boltBranchPath = new Path(), wormStarPath = new Path(), shieldPath = new Path(), shieldInnerPath = new Path();
        private final float[] boltXs = new float[17], boltYs = new float[17];
        private final Random boltRnd = new Random();
        private final int themeState;
        private final FlappyAudioEngine audioEngine;
        private GameListener listener;

        public interface GameListener { void onScoreUpdated(int score); void onGameOver(int finalScore, Bitmap snapshot, int birdType); void onGameStarted(); }

        public FlappyGameEngine(Context context, int themeState, FlappyAudioEngine audioEngine) {
            super(context);
            this.themeState = themeState; this.audioEngine = audioEngine;
            for (int i = 0; i < MAX_TRAIL; i++) trail[i] = new TrailPoint(0, 0);
            for (int i = 0; i < 10; i++) dustParticles[i] = new DustParticle();

            trailPaint.setColor(Color.WHITE); trailPaint.setStyle(Paint.Style.FILL);
            gooTrailPaint.setColor(Color.parseColor("#888888")); gooTrailPaint.setStyle(Paint.Style.FILL);
            fallbackBirdPaint.setColor(Color.parseColor("#3498DB"));

            elecCoronaPaint.setStyle(Paint.Style.STROKE); elecCoronaPaint.setStrokeCap(Paint.Cap.ROUND); elecCoronaPaint.setStrokeJoin(Paint.Join.ROUND);
            elecGlowPaint.setStyle(Paint.Style.STROKE); elecGlowPaint.setStrokeCap(Paint.Cap.ROUND); elecGlowPaint.setStrokeJoin(Paint.Join.ROUND);
            elecInnerPaint.setStyle(Paint.Style.STROKE); elecInnerPaint.setStrokeCap(Paint.Cap.ROUND); elecInnerPaint.setStrokeJoin(Paint.Join.ROUND);
            elecWhitePaint.setStyle(Paint.Style.STROKE); elecWhitePaint.setStrokeCap(Paint.Cap.ROUND); elecWhitePaint.setStrokeJoin(Paint.Join.ROUND);
            elecSparkPaint.setStyle(Paint.Style.FILL);

            starPaint.setColor(Color.WHITE); shootingStarPaint.setStyle(Paint.Style.STROKE); shootingStarPaint.setStrokeCap(Paint.Cap.ROUND); fireflyPaint.setStyle(Paint.Style.FILL);

            wormGlowPaint.setColor(Color.parseColor("#FFE600")); wormGlowPaint.setAlpha(95);
            wormSparklePaint.setColor(Color.parseColor("#FFFCE0")); wormSparklePaint.setStyle(Paint.Style.FILL);
            wormRayPaint.setColor(Color.parseColor("#FFEA54")); wormRayPaint.setStyle(Paint.Style.FILL);
            wormSheenPaint.setColor(Color.WHITE); wormSheenPaint.setStyle(Paint.Style.FILL);

            shieldBloomPaint.setStyle(Paint.Style.STROKE); shieldBloomPaint.setStrokeCap(Paint.Cap.ROUND); shieldBloomPaint.setStrokeJoin(Paint.Join.ROUND);
            shieldStrokePaint.setStyle(Paint.Style.STROKE); shieldStrokePaint.setStrokeCap(Paint.Cap.ROUND); shieldStrokePaint.setStrokeJoin(Paint.Join.ROUND);
            shieldCorePaint.setStyle(Paint.Style.STROKE); shieldCorePaint.setStrokeCap(Paint.Cap.ROUND); shieldCorePaint.setStrokeJoin(Paint.Join.ROUND);
            shieldFillPaint.setStyle(Paint.Style.FILL); shieldNodePaint.setStyle(Paint.Style.FILL);

            hudBg.setColor(Color.parseColor("#99000000")); barBg.setColor(Color.parseColor("#40FFFFFF")); barFill.setColor(themeState == 2 ? Color.parseColor("#00FFFF") : Color.parseColor("#FFD700"));
            timeText.setColor(Color.WHITE); timeText.setTextSize(34f); timeText.setTypeface(Typeface.DEFAULT_BOLD); timeText.setTextAlign(Paint.Align.CENTER);
        }

        public void setGameListener(GameListener listener) { this.listener = listener; }
        public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }

        public void setBirdType(int type) {
            this.birdType = type;
            loadBirdSprite(); isReady = true;
            if (screenW > 0 && screenH > 0 && type == 1 && (cityBackBmp == null || cityBackBmp.isRecycled()) && !cityBuildPending) {
                buildCityLayersAsync();
            }
            invalidate();
        }

        private void loadBirdSprite() {
            int birdSize;
            if (birdType == 0) {
                birdSize = (int) (birdRadius * 2.8f); birdBitmaps = new Bitmap[3]; flapSequence = new int[]{0, 1, 2, 1};
                int[] resIds = { getResources().getIdentifier("flappy_bird_blue_up", "drawable", getContext().getPackageName()), getResources().getIdentifier("flappy_bird_blue", "drawable", getContext().getPackageName()), getResources().getIdentifier("flappy_bird_blue_down", "drawable", getContext().getPackageName()) };
                for (int i = 0; i < 3; i++) { if (resIds[i] != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resIds[i]); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true); } }
            } else {
                birdSize = (int) (birdRadius * 3.0f); birdBitmaps = new Bitmap[9]; flapSequence = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
                for (int i = 0; i < 9; i++) {
                    int resId = getResources().getIdentifier("goobird" + (i + 1), "drawable", getContext().getPackageName());
                    if (resId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resId); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true); }
                    else { int fallbackId = getResources().getIdentifier("flappy_bird_blue", "drawable", getContext().getPackageName()); if (fallbackId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), fallbackId); birdBitmaps[i] = Bitmap.createScaledBitmap(raw, birdSize, birdSize, true); } }
                }
            }
            sequenceIndex = 0; currentFrameIndex = flapSequence[0];
        }

        private void recycleBitmap(Bitmap b) { if (b != null && !b.isRecycled()) b.recycle(); }
        private void recycleBitmapArray(Bitmap[] arr) { if (arr != null) for (Bitmap b : arr) recycleBitmap(b); }
        private void recycleBackgroundBitmaps() {
            recycleBitmap(celestialBmp); celestialBmp = null; recycleBitmap(farCloudBmp); farCloudBmp = null; recycleBitmap(nearCloudBmp); nearCloudBmp = null;
            recycleBitmap(farMountainBmp); farMountainBmp = null; recycleBitmap(midMountainBmp); midMountainBmp = null; recycleBitmap(foreForestBmp); foreForestBmp = null;
            recycleBitmap(cityBackBmp); cityBackBmp = null; recycleBitmap(cityMidBmp); cityMidBmp = null; recycleBitmap(cityFrontBmp); cityFrontBmp = null;
            recycleBitmap(goldSegBmp); goldSegBmp = null; recycleBitmap(goldHeadBmp); goldHeadBmp = null; recycleBitmap(goldAuraBmp); goldAuraBmp = null;
            recycleBitmap(shieldGoldHaloBmp); shieldGoldHaloBmp = null; recycleBitmap(shieldCosmicHaloBmp); shieldCosmicHaloBmp = null; recycleBitmap(shieldCrimsonHaloBmp); shieldCrimsonHaloBmp = null;
            recycleBitmap(classicPipeBodyBmp); classicPipeBodyBmp = null; recycleBitmap(classicPipeCapBmp); classicPipeCapBmp = null;
            recycleBitmap(clovPipeBodyBmp); clovPipeBodyBmp = null; recycleBitmap(clovPipeCapBmp); clovPipeCapBmp = null; recycleBitmap(elecFlashBmp); elecFlashBmp = null;
        }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); detached = true; bgBuildGen++; recycleBackgroundBitmaps(); }

        private Bitmap[] createPipeBitmaps(float pWidth, float rW, float rH) {
            int bw = Math.max(96, (int) pWidth), capExtPx = Math.max(8, (int) (rW * 0.015f)), cw = bw + capExtPx * 2, ch = Math.max(44, (int) (rH * 0.04f));
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            int[] cCols; int cOutline, cTopHi, cBotSh, cGroove;
            if (themeState == 0) {
                cCols = new int[]{Color.parseColor("#2A5714"), Color.parseColor("#54A024"), Color.parseColor("#8FE048"), Color.parseColor("#E8FFB8"), Color.parseColor("#99E650"), Color.parseColor("#5EAD26"), Color.parseColor("#3B7A18"), Color.parseColor("#21470E"), Color.parseColor("#3E7B1C")};
                cOutline = Color.parseColor("#1D3E0D"); cTopHi = Color.parseColor("#C8FFFFFF"); cBotSh = Color.parseColor("#750F2406"); cGroove = Color.parseColor("#551B380A");
            } else if (themeState == 1) {
                cCols = new int[]{Color.parseColor("#142E16"), Color.parseColor("#2C632E"), Color.parseColor("#59A84C"), Color.parseColor("#C2F0B4"), Color.parseColor("#5FA850"), Color.parseColor("#357332"), Color.parseColor("#1E4A20"), Color.parseColor("#0F2912"), Color.parseColor("#25572C")};
                cOutline = Color.parseColor("#0B1E0D"); cTopHi = Color.parseColor("#90E0FFE6"); cBotSh = Color.parseColor("#88051407"); cGroove = Color.parseColor("#660A1E0C");
            } else {
                cCols = new int[]{Color.parseColor("#091C0E"), Color.parseColor("#1B4D24"), Color.parseColor("#3D9445"), Color.parseColor("#9EFFA6"), Color.parseColor("#429C4A"), Color.parseColor("#22612A"), Color.parseColor("#113817"), Color.parseColor("#071A0A"), Color.parseColor("#1D5229")};
                cOutline = Color.parseColor("#051208"); cTopHi = Color.parseColor("#88B8FFC4"); cBotSh = Color.parseColor("#99000000"); cGroove = Color.parseColor("#77061709");
            }
            float[] cStops = {0.00f, 0.08f, 0.20f, 0.29f, 0.37f, 0.54f, 0.76f, 0.91f, 1.00f};

            Bitmap cBody = Bitmap.createBitmap(bw, 64, Bitmap.Config.ARGB_8888); Canvas cbc = new Canvas(cBody);
            p.setShader(new LinearGradient(0, 0, bw, 0, cCols, cStops, Shader.TileMode.CLAMP)); cbc.drawRect(0, 0, bw, 64, p); p.setShader(null);
            p.setColor(Color.argb(themeState == 0 ? 135 : 95, 255, 255, 240)); cbc.drawRect(bw * 0.255f, 0, bw * 0.295f, 64, p);
            p.setColor(Color.argb(60, 255, 255, 255)); cbc.drawRect(bw * 0.34f, 0, bw * 0.365f, 64, p);
            p.setColor(cOutline); cbc.drawRect(0, 0, Math.max(3f, bw * 0.032f), 64, p); cbc.drawRect(bw - Math.max(3f, bw * 0.032f), 0, bw, 64, p);

            Bitmap cCap = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); Canvas ccc = new Canvas(cCap);
            RectF cCapR = new RectF(2f, 2f, cw - 2f, ch - 2f); float cRad = Math.min(12f, ch * 0.22f);
            p.setShader(new LinearGradient(0, 0, cw, 0, cCols, cStops, Shader.TileMode.CLAMP)); ccc.drawRoundRect(cCapR, cRad, cRad, p);
            p.setShader(new LinearGradient(0, 2f, 0, ch * 0.28f, cTopHi, Color.TRANSPARENT, Shader.TileMode.CLAMP)); ccc.drawRoundRect(cCapR, cRad, cRad, p);
            p.setShader(new LinearGradient(0, ch * 0.72f, 0, ch - 2f, Color.TRANSPARENT, cBotSh, Shader.TileMode.CLAMP)); ccc.drawRoundRect(cCapR, cRad, cRad, p); p.setShader(null);
            p.setColor(Color.argb(themeState == 0 ? 155 : 110, 255, 255, 240)); ccc.drawRect(cw * 0.255f, 4f, cw * 0.298f, ch - 4f, p);
            p.setColor(cGroove); ccc.drawRect(4f, ch * 0.24f, cw - 4f, ch * 0.24f + 2.2f, p); ccc.drawRect(4f, ch * 0.76f, cw - 4f, ch * 0.76f + 2.2f, p);
            p.setColor(Color.argb(70, 255, 255, 255)); ccc.drawRect(4f, ch * 0.24f + 2.2f, cw - 4f, ch * 0.24f + 4.0f, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3.5f, bw * 0.036f)); p.setColor(cOutline); ccc.drawRoundRect(cCapR, cRad, cRad, p); p.setStyle(Paint.Style.FILL);

            int[] mCols; int mOutline, mGlowCol, mRivetLight, mRivetDark;
            if (themeState == 0) {
                mCols = new int[]{Color.parseColor("#3B4754"), Color.parseColor("#6E7F91"), Color.parseColor("#B5C6D6"), Color.parseColor("#F5FAFF"), Color.parseColor("#C2D2E0"), Color.parseColor("#7C8FA1"), Color.parseColor("#4B5A69"), Color.parseColor("#29333D"), Color.parseColor("#5B778F")};
                mOutline = Color.parseColor("#1D252E"); mGlowCol = Color.parseColor("#00D8FF"); mRivetLight = Color.parseColor("#E6F4FF"); mRivetDark = Color.parseColor("#242F3B");
            } else if (themeState == 1) {
                mCols = new int[]{Color.parseColor("#1E2833"), Color.parseColor("#3B4C5E"), Color.parseColor("#738A9E"), Color.parseColor("#D4E7F7"), Color.parseColor("#7E95AA"), Color.parseColor("#455769"), Color.parseColor("#263340"), Color.parseColor("#131B24"), Color.parseColor("#325870")};
                mOutline = Color.parseColor("#0C1218"); mGlowCol = Color.parseColor("#00E5FF"); mRivetLight = Color.parseColor("#CBE8FF"); mRivetDark = Color.parseColor("#10171F");
            } else {
                mCols = new int[]{Color.parseColor("#111924"), Color.parseColor("#243345"), Color.parseColor("#4C6580"), Color.parseColor("#B2DCFF"), Color.parseColor("#546E8A"), Color.parseColor("#2B3C4F"), Color.parseColor("#15202E"), Color.parseColor("#080D14"), Color.parseColor("#1D4A66")};
                mOutline = Color.parseColor("#05090F"); mGlowCol = Color.parseColor("#00FFFF"); mRivetLight = Color.parseColor("#9EE8FF"); mRivetDark = Color.parseColor("#070B10");
            }
            float[] mStops = {0.00f, 0.08f, 0.21f, 0.29f, 0.36f, 0.52f, 0.76f, 0.91f, 1.00f};

            Bitmap mBody = Bitmap.createBitmap(bw, 64, Bitmap.Config.ARGB_8888); Canvas mbc = new Canvas(mBody);
            p.setShader(new LinearGradient(0, 0, bw, 0, mCols, mStops, Shader.TileMode.CLAMP)); mbc.drawRect(0, 0, bw, 64, p); p.setShader(null);
            p.setColor(Color.argb(145, 240, 250, 255)); mbc.drawRect(bw * 0.26f, 0, bw * 0.295f, 64, p);
            float condL = bw * 0.45f, condR = bw * 0.55f; p.setColor(Color.argb(160, 10, 22, 34)); mbc.drawRect(condL, 0, condR, 64, p);
            p.setShader(new LinearGradient(condL, 0, condR, 0, new int[]{Color.TRANSPARENT, mGlowCol, Color.WHITE, mGlowCol, Color.TRANSPARENT}, new float[]{0f, 0.28f, 0.5f, 0.72f, 1f}, Shader.TileMode.CLAMP)); mbc.drawRect(condL, 0, condR, 64, p); p.setShader(null);
            p.setColor(mOutline); mbc.drawRect(0, 0, Math.max(3f, bw * 0.034f), 64, p); mbc.drawRect(bw - Math.max(3f, bw * 0.034f), 0, bw, 64, p);

            Bitmap mCap = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888); Canvas mcc = new Canvas(mCap);
            RectF mCapR = new RectF(2f, 2f, cw - 2f, ch - 2f); float mRad = Math.min(10f, ch * 0.20f);
            p.setShader(new LinearGradient(0, 0, cw, 0, mCols, mStops, Shader.TileMode.CLAMP)); mcc.drawRoundRect(mCapR, mRad, mRad, p);
            p.setShader(new LinearGradient(0, 2f, 0, ch * 0.26f, Color.parseColor("#AAFFFFFF"), Color.TRANSPARENT, Shader.TileMode.CLAMP)); mcc.drawRoundRect(mCapR, mRad, mRad, p);
            p.setShader(new LinearGradient(0, ch * 0.74f, 0, ch - 2f, Color.TRANSPARENT, Color.parseColor("#99040A12"), Shader.TileMode.CLAMP)); mcc.drawRoundRect(mCapR, mRad, mRad, p); p.setShader(null);
            float bandTop = ch * 0.38f, bandBot = ch * 0.62f; p.setColor(Color.parseColor("#CC0A1826")); mcc.drawRect(6f, bandTop - 2f, cw - 6f, bandBot + 2f, p);
            p.setShader(new LinearGradient(0, bandTop, 0, bandBot, new int[]{Color.parseColor("#4400B8FF"), mGlowCol, Color.WHITE, mGlowCol, Color.parseColor("#4400B8FF")}, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP)); mcc.drawRect(8f, bandTop, cw - 8f, bandBot, p); p.setShader(null);
            for (float rx : new float[]{cw * 0.12f, cw * 0.88f}) { p.setColor(mRivetDark); mcc.drawCircle(rx + 1f, ch * 0.5f + 1f, ch * 0.14f, p); p.setColor(mRivetLight); mcc.drawCircle(rx, ch * 0.5f, ch * 0.11f, p); p.setColor(mGlowCol); mcc.drawCircle(rx, ch * 0.5f, ch * 0.055f, p); }
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3.5f, bw * 0.036f)); p.setColor(mOutline); mcc.drawRoundRect(mCapR, mRad, mRad, p); p.setStyle(Paint.Style.FILL);

            int flashSize = Math.max(64, (int) (bw * 0.65f));
            Bitmap eFlash = Bitmap.createBitmap(flashSize, flashSize, Bitmap.Config.ARGB_8888); Canvas efc = new Canvas(eFlash); float efcx = flashSize / 2f;
            p.setShader(new RadialGradient(efcx, efcx, efcx, new int[]{Color.WHITE, Color.parseColor("#CC7CF8FF"), Color.parseColor("#5500A8FF"), Color.parseColor("#001A4DFF")}, new float[]{0f, 0.24f, 0.58f, 1f}, Shader.TileMode.CLAMP)); efc.drawCircle(efcx, efcx, efcx, p);
            return new Bitmap[]{ cBody, cCap, mBody, mCap, eFlash };
        }

        private Bitmap createShieldHaloBitmap(int size, int[] colors, float[] stops) {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp); float cx = size / 2f; Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(cx, cx, cx, colors, stops, Shader.TileMode.CLAMP)); c.drawCircle(cx, cx, cx, p);
            return bmp;
        }

        private Bitmap[] createShieldBitmaps(float bRadius) {
            int size = Math.max(160, (int) (bRadius * 7.2f));
            return new Bitmap[]{
                    createShieldHaloBitmap(size, new int[]{Color.parseColor("#70FFFDF0"), Color.parseColor("#55FFE63B"), Color.parseColor("#38FFAE00"), Color.parseColor("#18FF8800"), Color.parseColor("#00FFCC00")}, new float[]{0.0f, 0.34f, 0.56f, 0.78f, 1.0f}),
                    createShieldHaloBitmap(size, new int[]{Color.parseColor("#55E0FFFF"), Color.parseColor("#4800F0FF"), Color.parseColor("#3200FF9D"), Color.parseColor("#1A5B2EFF"), Color.parseColor("#0000E5FF")}, new float[]{0.0f, 0.34f, 0.58f, 0.78f, 1.0f}),
                    createShieldHaloBitmap(size, new int[]{Color.parseColor("#66FFF3B0"), Color.parseColor("#4DFFAA00"), Color.parseColor("#3AFF3D2E"), Color.parseColor("#18B80028"), Color.parseColor("#00FF5500")}, new float[]{0.0f, 0.32f, 0.58f, 0.78f, 1.0f})
            };
        }

        private Bitmap[] createGoldenWormBitmaps(float wRadius) {
            Bitmap seg = createChromeGoldSphere(Math.max(72, (int) (wRadius * 2.6f)), false);
            Bitmap head = createChromeGoldSphere(Math.max(88, (int) (wRadius * 3.0f)), true);
            int auraSize = Math.max(96, (int) (wRadius * 5.4f));
            Bitmap aura = Bitmap.createBitmap(auraSize, auraSize, Bitmap.Config.ARGB_8888); Canvas ac = new Canvas(aura); float acx = auraSize / 2f; Paint ap = new Paint(Paint.ANTI_ALIAS_FLAG);
            ap.setShader(new RadialGradient(acx, acx, acx, new int[]{Color.parseColor("#99FFF9B0"), Color.parseColor("#66FFE600"), Color.parseColor("#28CC8800"), Color.parseColor("#00FFCC00")}, new float[]{0f, 0.34f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            ac.drawCircle(acx, acx, acx, ap);
            return new Bitmap[]{ seg, head, aura };
        }

        private Bitmap createChromeGoldSphere(int size, boolean isHead) {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float cx = size / 2f, cy = size / 2f, r = size * 0.43f, rx = isHead ? r * 1.08f : r * 0.98f, ry = isHead ? r * 0.98f : r * 0.96f;
            RectF oval = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);

            p.setShader(new RadialGradient(cx, cy, r * 1.15f, new int[]{Color.parseColor("#88FFE600"), Color.parseColor("#00FFE600")}, new float[]{0.6f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, r * 1.15f, p); p.setShader(null);
            c.save(); Path clip = new Path(); clip.addOval(oval, Path.Direction.CW); c.clipPath(clip);
            p.setShader(new LinearGradient(cx - rx * 0.25f, cy - ry, cx + rx * 0.20f, cy + ry,
                    new int[]{Color.parseColor("#FFFDF2"), Color.parseColor("#FFF700"), Color.parseColor("#FFE400"), Color.parseColor("#9C6400"), Color.parseColor("#1D1000"), Color.parseColor("#120900"), Color.parseColor("#694100"), Color.parseColor("#D69900"), Color.parseColor("#FFE838"), Color.parseColor("#543000")},
                    new float[]{0.00f, 0.14f, 0.36f, 0.47f, 0.56f, 0.64f, 0.74f, 0.84f, 0.93f, 1.00f}, Shader.TileMode.CLAMP)); c.drawOval(oval, p);
            p.setShader(new RadialGradient(cx - rx * 0.18f, cy - ry * 0.42f, rx * 0.85f, new int[]{Color.parseColor("#EEFFFFB8"), Color.parseColor("#AAFFF200"), Color.parseColor("#00FFD000")}, new float[]{0.0f, 0.42f, 1.0f}, Shader.TileMode.CLAMP)); c.drawOval(cx - rx * 0.85f, cy - ry * 0.92f, cx + rx * 0.72f, cy + ry * 0.12f, p);
            Path darkBand = new Path(); darkBand.moveTo(cx - rx * 1.05f, cy + ry * 0.08f); darkBand.quadTo(cx - rx * 0.10f, cy + ry * 0.46f, cx + rx * 1.05f, cy - ry * 0.06f); darkBand.quadTo(cx + rx * 0.15f, cy + ry * 0.78f, cx - rx * 1.05f, cy + ry * 0.32f); darkBand.close();
            p.setShader(new LinearGradient(cx, cy, cx, cy + ry * 0.65f, Color.parseColor("#D8180C00"), Color.parseColor("#224A2A00"), Shader.TileMode.CLAMP)); c.drawPath(darkBand, p);
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(2f, r * 0.13f)); p.setColor(Color.parseColor("#E6FFDF2B")); c.drawArc(cx - rx * 0.88f, cy - ry * 0.88f, cx + rx * 0.88f, cy + ry * 0.88f, 22f, 132f, false, p);
            p.setStrokeWidth(Math.max(2f, r * 0.14f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(Color.parseColor("#F5FFFDE0")); c.drawArc(cx - rx * 0.84f, cy - ry * 0.84f, cx + rx * 0.84f, cy + ry * 0.84f, 206f, 108f, false, p);
            p.setStyle(Paint.Style.FILL); p.setShader(new RadialGradient(cx - rx * 0.28f, cy - ry * 0.48f, r * 0.30f, new int[]{Color.WHITE, Color.parseColor("#99FFF9B0"), Color.TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx - rx * 0.28f, cy - ry * 0.48f, r * 0.30f, p); p.setShader(null); c.restore();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.5f, r * 0.065f)); p.setColor(Color.parseColor("#CC3A2100")); c.drawOval(oval, p);

            if (isHead) {
                Path horn = new Path(); horn.moveTo(cx + rx * 0.08f, cy - ry * 0.82f); horn.quadTo(cx - rx * 0.04f, cy - ry * 1.24f, cx - rx * 0.34f, cy - ry * 1.28f); horn.quadTo(cx + rx * 0.08f, cy - ry * 1.12f, cx + rx * 0.28f, cy - ry * 0.76f); horn.close();
                p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(cx - rx * 0.34f, cy - ry * 1.28f, cx + rx * 0.28f, cy - ry * 0.76f, Color.parseColor("#FFFCE0"), Color.parseColor("#7A4900"), Shader.TileMode.CLAMP)); c.drawPath(horn, p); p.setShader(null);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1.2f, r * 0.05f)); p.setColor(Color.parseColor("#442400")); c.drawPath(horn, p);
                float ex = cx + rx * 0.34f, ey = cy - ry * 0.16f, er = r * 0.23f;
                p.setStyle(Paint.Style.FILL); p.setColor(Color.parseColor("#FFE438")); c.drawCircle(ex, ey, er * 1.24f, p);
                p.setShader(new RadialGradient(ex - er * 0.25f, ey - er * 0.25f, er * 1.1f, new int[]{Color.parseColor("#543100"), Color.parseColor("#140900"), Color.BLACK}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(ex, ey, er, p); p.setShader(null);
                p.setColor(Color.parseColor("#FFFCE8")); c.drawCircle(ex - er * 0.30f, ey - er * 0.32f, er * 0.34f, p);
                p.setColor(Color.parseColor("#BBFFD43B")); c.drawCircle(ex + er * 0.26f, ey + er * 0.28f, er * 0.18f, p);
            }
            return bmp;
        }

        private void buildSkyAndAtmosphere() {
            if (screenW <= 0 || screenH <= 0) return;
            if (themeState == 0) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, new int[]{Color.parseColor("#3B7CC4"), Color.parseColor("#5C9ED9"), Color.parseColor("#8EC2E6"), Color.parseColor("#C7E0EF"), Color.parseColor("#E3EDF2"), Color.parseColor("#D4E3EC")}, new float[]{0f, 0.22f, 0.44f, 0.62f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                celestialGlowPaint.setShader(new RadialGradient(screenW * 0.82f, screenH * 0.14f, Math.max(1f, Math.max(screenW, screenH) * 0.48f), new int[]{Color.parseColor("#55FFFBE0"), Color.parseColor("#24FFF1BD"), Color.parseColor("#00FFFFFF")}, new float[]{0f, 0.38f, 1f}, Shader.TileMode.CLAMP));
            } else if (themeState == 1) {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, new int[]{Color.parseColor("#08131F"), Color.parseColor("#112539"), Color.parseColor("#1B3952"), Color.parseColor("#294E6B"), Color.parseColor("#1E3B52"), Color.parseColor("#122433")}, new float[]{0f, 0.24f, 0.48f, 0.66f, 0.82f, 1f}, Shader.TileMode.CLAMP));
                celestialGlowPaint.setShader(new RadialGradient(screenW * 0.78f, screenH * 0.17f, Math.max(1f, Math.max(screenW, screenH) * 0.36f), new int[]{Color.parseColor("#38C8E4FF"), Color.parseColor("#167BB0D8"), Color.parseColor("#001B3952")}, new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
            } else {
                skyPaint.setShader(new LinearGradient(0, 0, 0, screenH, new int[]{Color.parseColor("#000000"), Color.parseColor("#000000"), Color.parseColor("#030812"), Color.parseColor("#071324"), Color.parseColor("#040B16"), Color.parseColor("#000000")}, new float[]{0f, 0.42f, 0.62f, 0.78f, 0.90f, 1f}, Shader.TileMode.CLAMP));
                celestialGlowPaint.setShader(new RadialGradient(screenW * 0.78f, screenH * 0.17f, Math.max(1f, Math.max(screenW, screenH) * 0.28f), new int[]{Color.parseColor("#2468A8DB"), Color.parseColor("#0E2A5585"), Color.parseColor("#00000000")}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            }

            stars.clear();
            if (themeState == 1 || themeState == 2) {
                Random starRnd = new Random(themeState == 2 ? 777L : 333L);
                int starCount = (themeState == 2) ? 125 : 65; float maxStarY = screenH * (themeState == 2 ? 0.68f : 0.54f);
                int[] starPalette = (themeState == 2) ? new int[]{Color.WHITE, Color.parseColor("#CBE8FF"), Color.parseColor("#FFF4D2"), Color.parseColor("#A9D4FF")} : new int[]{Color.parseColor("#E6F2FF"), Color.parseColor("#BDD8F2"), Color.parseColor("#FFFFFF")};
                for (int i = 0; i < starCount; i++) {
                    float sx = starRnd.nextFloat() * screenW, sy = (float) Math.pow(starRnd.nextFloat(), 1.25) * maxStarY;
                    float sr = (themeState == 2) ? (0.9f + (float) Math.pow(starRnd.nextFloat(), 2.2) * 2.6f) : (0.8f + (float) Math.pow(starRnd.nextFloat(), 2.4) * 1.9f);
                    stars.add(new Star(sx, sy, sr, 0.25f + starRnd.nextFloat() * 0.75f, 0.008f + starRnd.nextFloat() * 0.024f, starPalette[starRnd.nextInt(starPalette.length)], themeState == 2 && sr > 2.45f && starRnd.nextFloat() < 0.6f));
                }
            }

            fireflies.clear();
            if (themeState == 1 || themeState == 2) {
                Random ffRnd = new Random(909L); int ffCount = (themeState == 2) ? 18 : 12;
                for (int i = 0; i < ffCount; i++) {
                    int col = (themeState == 2 && ffRnd.nextBoolean()) ? Color.parseColor("#7CEEFF") : Color.parseColor("#FFE787");
                    fireflies.add(new Firefly(ffRnd.nextFloat() * screenW, screenH * (0.68f + ffRnd.nextFloat() * 0.26f), ffRnd.nextFloat() * (float) (Math.PI * 2.0), 0.02f + ffRnd.nextFloat() * 0.03f, 2.2f + ffRnd.nextFloat() * 2.4f, col));
                }
            }
        }

        private Bitmap createCelestialBitmap(float sW, float sH) {
            float baseRadius = Math.min(sW, sH) * (themeState == 0 ? 0.075f : (themeState == 1 ? 0.072f : 0.062f));
            int size = Math.max(64, (int) (baseRadius * 6.0f));
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp); float cx = size / 2f, cy = size / 2f; Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            if (themeState == 0) {
                p.setShader(new RadialGradient(cx, cy, baseRadius * 2.8f, new int[]{Color.parseColor("#AAFFFBE2"), Color.parseColor("#44FFEA9E"), Color.parseColor("#00FFF6CC")}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, baseRadius * 2.8f, p);
                p.setShader(new RadialGradient(cx, cy, baseRadius, new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#FFF8D0"), Color.parseColor("#00FFF1B5")}, new float[]{0f, 0.72f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, baseRadius, p);
            } else if (themeState == 1) {
                p.setShader(new RadialGradient(cx, cy, baseRadius * 2.9f, new int[]{Color.parseColor("#70D2ECFF"), Color.parseColor("#2A7EB4DE"), Color.parseColor("#001B3952")}, new float[]{0f, 0.46f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, baseRadius * 2.9f, p);
                c.save(); Path moonClip = new Path(); moonClip.addCircle(cx, cy, baseRadius, Path.Direction.CW); c.clipPath(moonClip);
                p.setShader(new RadialGradient(cx - baseRadius * 0.25f, cy - baseRadius * 0.25f, baseRadius * 1.35f, new int[]{Color.parseColor("#F8FCFF"), Color.parseColor("#DCEBF7"), Color.parseColor("#AEC8DE")}, new float[]{0f, 0.68f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, baseRadius, p); p.setShader(null);
                float[][] craters = {{-0.30f,-0.22f,0.26f,38},{0.12f,-0.32f,0.20f,30},{0.28f,0.10f,0.30f,36},{-0.18f,0.26f,0.22f,32},{0.02f,0.04f,0.16f,26},{-0.46f,0.08f,0.14f,28},{0.42f,-0.22f,0.13f,24}};
                for (float[] cr : craters) { float crX = cx + cr[0] * baseRadius, crY = cy + cr[1] * baseRadius, crR = cr[2] * baseRadius; p.setShader(new RadialGradient(crX, crY, Math.max(1f, crR), new int[]{Color.argb((int) cr[3], 92, 120, 148), Color.argb(0, 92, 120, 148)}, new float[]{0.2f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(crX, crY, crR, p); }
                p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f); p.setColor(Color.parseColor("#88FFFFFF")); c.drawCircle(cx, cy, baseRadius - 1f, p); c.restore();
            } else {
                p.setShader(new RadialGradient(cx, cy, baseRadius * 2.5f, new int[]{Color.parseColor("#3A7AB8E8"), Color.parseColor("#14316496"), Color.parseColor("#00000000")}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, baseRadius * 2.5f, p);
                Bitmap crescentLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); Canvas cc = new Canvas(crescentLayer); Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
                cp.setShader(new RadialGradient(cx - baseRadius * 0.3f, cy - baseRadius * 0.2f, baseRadius * 1.3f, new int[]{Color.parseColor("#F5FBFF"), Color.parseColor("#CBE4FA"), Color.parseColor("#8BB8DE")}, new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP)); cc.drawCircle(cx, cy, baseRadius, cp);
                Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG); clearPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)); cc.drawCircle(cx + baseRadius * 0.42f, cy - baseRadius * 0.18f, baseRadius * 0.90f, clearPaint);
                c.drawBitmap(crescentLayer, 0, 0, null); crescentLayer.recycle();
            }
            return bmp;
        }

        private void drawSoftPuffWrapped(Canvas c, float bmpW, float x, float y, float rx, float ry, int coreColor, int edgeColor) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float r = Math.max(1f, Math.max(rx, ry));
            p.setShader(new RadialGradient(0f, 0f, r, new int[]{coreColor, coreColor, edgeColor}, new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
            float sx = rx / r, sy = ry / r; drawScaledOval(c, x, y, sx, sy, r, p);
            if (x - rx < 0) drawScaledOval(c, x + bmpW, y, sx, sy, r, p);
            if (x + rx > bmpW) drawScaledOval(c, x - bmpW, y, sx, sy, r, p);
        }

        private void drawScaledOval(Canvas c, float x, float y, float sx, float sy, float r, Paint p) { c.save(); c.translate(x, y); c.scale(sx, sy); c.drawCircle(0f, 0f, r, p); c.restore(); }

        private void drawVolumetricCloud(Canvas c, float bmpW, float cx, float cy, float cw, float ch, int shadowCol, int midCol, int highlightCol, Random rnd) {
            int puffCount = 13 + rnd.nextInt(7);
            float[] px = new float[puffCount], py = new float[puffCount], prx = new float[puffCount], pry = new float[puffCount];
            for (int i = 0; i < puffCount; i++) {
                float t = (i / (float) (puffCount - 1)) * 2f - 1f, arch = (float) Math.sqrt(Math.max(0.05f, 1f - t * t));
                px[i] = cx + t * (cw * 0.46f) + (rnd.nextFloat() - 0.5f) * cw * 0.10f; py[i] = cy - arch * (ch * 0.34f) + (rnd.nextFloat() - 0.42f) * ch * 0.28f;
                prx[i] = cw * (0.14f + arch * 0.14f + rnd.nextFloat() * 0.07f); pry[i] = ch * (0.28f + arch * 0.24f + rnd.nextFloat() * 0.10f);
            }
            int shadowEdge = Color.argb(0, Color.red(shadowCol), Color.green(shadowCol), Color.blue(shadowCol)), midEdge = Color.argb(0, Color.red(midCol), Color.green(midCol), Color.blue(midCol)), hiEdge = Color.argb(0, Color.red(highlightCol), Color.green(highlightCol), Color.blue(highlightCol));
            for (int i = 0; i < puffCount; i++) drawSoftPuffWrapped(c, bmpW, px[i] - cw * 0.03f, py[i] + ch * 0.16f, prx[i] * 1.08f, pry[i] * 0.88f, shadowCol, shadowEdge);
            for (int i = 0; i < puffCount; i++) drawSoftPuffWrapped(c, bmpW, px[i], py[i], prx[i], pry[i], midCol, midEdge);
            for (int i = 0; i < puffCount; i++) if (py[i] <= cy + ch * 0.05f) drawSoftPuffWrapped(c, bmpW, px[i] + cw * 0.035f, py[i] - ch * 0.13f, prx[i] * 0.86f, pry[i] * 0.78f, highlightCol, hiEdge);
        }

        private Bitmap[] createCloudLayers(int bmpW, float sH, float scaleY) {
            int cloudBandH = Math.max(64, (int) (sH * 0.62f * scaleY));
            Bitmap fBmp = Bitmap.createBitmap(bmpW, cloudBandH, Bitmap.Config.ARGB_8888), nBmp = Bitmap.createBitmap(bmpW, cloudBandH, Bitmap.Config.ARGB_8888);
            Canvas fc = new Canvas(fBmp), nc = new Canvas(nBmp);

            int fShadow, fMid, fHi, nShadow, nMid, nHi;
            if (themeState == 0) { fShadow = Color.argb(125,160,182,206); fMid = Color.argb(175,224,234,244); fHi = Color.argb(205,250,252,255); nShadow = Color.argb(175,128,148,174); nMid = Color.argb(225,222,230,238); nHi = Color.argb(242,255,253,246); }
            else if (themeState == 1) { fShadow = Color.argb(135,18,36,54); fMid = Color.argb(165,36,63,88); fHi = Color.argb(150,96,138,170); nShadow = Color.argb(190,14,29,44); nMid = Color.argb(205,32,58,82); nHi = Color.argb(190,140,184,214); }
            else { fShadow = Color.argb(105,4,10,20); fMid = Color.argb(125,10,20,36); fHi = Color.argb(110,28,52,84); nShadow = Color.argb(150,3,7,15); nMid = Color.argb(155,11,23,42); nHi = Color.argb(130,44,78,118); }

            Random fRnd = new Random(104L);
            for (int i = 0; i < 9; i++) drawVolumetricCloud(fc, bmpW, (i / 9f) * bmpW + (fRnd.nextFloat() - 0.5f) * (bmpW * 0.08f), cloudBandH * (0.56f + fRnd.nextFloat() * 0.30f), bmpW * (0.28f + fRnd.nextFloat() * 0.24f), cloudBandH * (0.12f + fRnd.nextFloat() * 0.08f), fShadow, fMid, fHi, fRnd);
            Random nRnd = new Random(208L);
            float[][] cloudSpecs = {{0.12f,0.24f,0.34f,0.20f},{0.44f,0.16f,0.42f,0.23f},{0.76f,0.28f,0.48f,0.25f},{0.28f,0.44f,0.44f,0.20f},{0.64f,0.48f,0.38f,0.18f},{0.93f,0.15f,0.32f,0.17f}};
            for (float[] sp : cloudSpecs) drawVolumetricCloud(nc, bmpW, sp[0] * bmpW, sp[1] * cloudBandH, sp[2] * bmpW, sp[3] * cloudBandH, nShadow, nMid, nHi, nRnd);
            return new Bitmap[]{ fBmp, nBmp };
        }

        private float sampleRidge(float t, float baseY, float amp, float[] freqs, float[] weights, float[] phases) {
            float y = baseY; for (int i = 0; i < freqs.length; i++) y += (float) Math.sin(t * Math.PI * 2.0 * freqs[i] + phases[i]) * amp * weights[i]; return y;
        }

        private void drawPineTree(Canvas c, float cx, float baseY, float h, float w, float seed, int trunkCol, int deepCol, int darkCol, int midCol, int lightCol, Paint p, Path path) {
            float lean = ((seed - 0.5f) * w * 0.07f);
            path.reset(); path.moveTo(cx + lean * 0.2f - w * 0.055f, baseY - h * 0.42f); path.lineTo(cx + lean * 0.2f + w * 0.055f, baseY - h * 0.42f);
            path.lineTo(cx + w * 0.085f, baseY + h * 0.14f); path.lineTo(cx - w * 0.085f, baseY + h * 0.14f); path.close();
            p.setShader(null); p.setColor(trunkCol); c.drawPath(path, p);

            path.reset(); path.moveTo(cx - w * 0.46f, baseY - h * 0.10f); path.quadTo(cx, baseY - h * 0.22f, cx + w * 0.46f, baseY - h * 0.10f);
            path.lineTo(cx + w * 0.38f, baseY + h * 0.12f); path.lineTo(cx - w * 0.38f, baseY + h * 0.12f); path.close();
            p.setColor(deepCol); c.drawPath(path, p);

            int tiers = 7;
            for (int i = 0; i < tiers; i++) {
                float f = i / (float) (tiers - 1), tierTop = baseY - h * (0.28f + f * 0.72f), tierBot = baseY - h * (f * 0.74f) + h * 0.03f;
                float curveW = (float) Math.pow(1.0 - f * 0.80, 0.92), jitterL = 0.88f + 0.24f * (float) Math.abs(Math.sin(seed * 19.3 + i * 2.1)), jitterR = 0.88f + 0.24f * (float) Math.abs(Math.cos(seed * 23.7 + i * 1.7));
                float leftW = w * 0.56f * curveW * jitterL, rightW = w * 0.56f * curveW * jitterR, topX = cx + lean * f, droop = h * (0.045f * (1f - f * 0.5f));

                path.reset(); path.moveTo(topX, tierTop);
                path.quadTo(topX - leftW * 0.48f, tierTop + (tierBot - tierTop) * 0.58f, topX - leftW, tierBot + droop * 0.55f);
                path.quadTo(topX - leftW * 0.55f, tierBot + droop * 1.15f, topX - leftW * 0.22f, tierBot + droop * 0.28f);
                path.quadTo(topX, tierBot + droop * 1.10f, topX + rightW * 0.22f, tierBot + droop * 0.28f);
                path.quadTo(topX + rightW * 0.55f, tierBot + droop * 1.15f, topX + rightW, tierBot + droop * 0.55f);
                path.quadTo(topX + rightW * 0.48f, tierTop + (tierBot - tierTop) * 0.58f, topX, tierTop);
                path.close(); p.setColor(deepCol); c.drawPath(path, p);

                path.reset(); path.moveTo(topX, tierTop);
                path.quadTo(topX - leftW * 0.42f, tierTop + (tierBot - tierTop) * 0.52f, topX - leftW * 0.96f, tierBot);
                path.quadTo(topX - leftW * 0.62f, tierBot + droop * 0.65f, topX - leftW * 0.42f, tierBot - droop * 0.15f);
                path.quadTo(topX - leftW * 0.18f, tierBot + droop * 0.58f, topX, tierBot - droop * 0.05f);
                path.quadTo(topX + rightW * 0.18f, tierBot + droop * 0.58f, topX + rightW * 0.42f, tierBot - droop * 0.15f);
                path.quadTo(topX + rightW * 0.62f, tierBot + droop * 0.65f, topX + rightW * 0.96f, tierBot);
                path.quadTo(topX + rightW * 0.42f, tierTop + (tierBot - tierTop) * 0.52f, topX, tierTop);
                path.close(); p.setShader(new LinearGradient(topX - leftW, tierTop, topX + rightW, tierBot, darkCol, midCol, Shader.TileMode.CLAMP)); c.drawPath(path, p); p.setShader(null);

                path.reset(); path.moveTo(topX, tierTop);
                path.quadTo(topX + rightW * 0.36f, tierTop + (tierBot - tierTop) * 0.45f, topX + rightW * 0.92f, tierBot - droop * 0.12f);
                path.quadTo(topX + rightW * 0.55f, tierBot + droop * 0.18f, topX + rightW * 0.28f, tierBot - droop * 0.38f);
                path.quadTo(topX + rightW * 0.10f, tierTop + (tierBot - tierTop) * 0.55f, topX, tierTop);
                path.close(); p.setColor(lightCol); p.setAlpha(i >= tiers - 2 ? 235 : 195); c.drawPath(path, p); p.setAlpha(255);
            }
        }

        private void drawWrappedPineTree(Canvas c, float bmpW, float cx, float baseY, float h, float w, float seed, int trunkCol, int deepCol, int darkCol, int midCol, int lightCol, Paint p, Path path) {
            drawPineTree(c, cx, baseY, h, w, seed, trunkCol, deepCol, darkCol, midCol, lightCol, p, path);
            if (cx - w < 0) drawPineTree(c, cx + bmpW, baseY, h, w, seed, trunkCol, deepCol, darkCol, midCol, lightCol, p, path);
            if (cx + w > bmpW) drawPineTree(c, cx - bmpW, baseY, h, w, seed, trunkCol, deepCol, darkCol, midCol, lightCol, p, path);
        }

        private Bitmap[] createNatureLayers(int bmpW, float sH, float scaleY) {
            int steps = Math.max(120, bmpW / 6); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Path path = new Path();

            int farH = Math.max(64, (int) (sH * 0.44f * scaleY)); Bitmap fMtn = Bitmap.createBitmap(bmpW, farH, Bitmap.Config.ARGB_8888); Canvas fc = new Canvas(fMtn);
            float[] fFreqA = {1f,2f,3f,5f}, fWgtA = {0.42f,0.30f,0.18f,0.10f}, fPhA = {0.4f,1.9f,3.1f,0.8f};
            float[] fFreqB = {1f,2f,4f,6f}, fWgtB = {0.38f,0.32f,0.20f,0.10f}, fPhB = {2.2f,0.5f,4.1f,1.7f};
            int farTopA, farBotA, farTopB, farBotB, farMistCol;
            if (themeState == 0) { farTopA = Color.parseColor("#6C95BA"); farBotA = Color.parseColor("#B5CFE2"); farTopB = Color.parseColor("#4F7B9E"); farBotB = Color.parseColor("#92B5CC"); farMistCol = Color.parseColor("#90D6E8F3"); }
            else if (themeState == 1) { farTopA = Color.parseColor("#1D394F"); farBotA = Color.parseColor("#2B4F69"); farTopB = Color.parseColor("#142B3E"); farBotB = Color.parseColor("#224259"); farMistCol = Color.parseColor("#663C6685"); }
            else { farTopA = Color.parseColor("#0B182B"); farBotA = Color.parseColor("#050C17"); farTopB = Color.parseColor("#081222"); farBotB = Color.parseColor("#030811"); farMistCol = Color.parseColor("#441C3A5E"); }

            path.reset(); path.moveTo(0, farH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; path.lineTo(t * bmpW, sampleRidge(t, farH * 0.28f, farH * 0.16f, fFreqA, fWgtA, fPhA)); }
            path.lineTo(bmpW, farH); path.close(); p.setShader(new LinearGradient(0, farH * 0.10f, 0, farH, farTopA, farBotA, Shader.TileMode.CLAMP)); fc.drawPath(path, p);

            path.reset(); path.moveTo(0, farH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; path.lineTo(t * bmpW, sampleRidge(t, farH * 0.44f, farH * 0.15f, fFreqB, fWgtB, fPhB)); }
            path.lineTo(bmpW, farH); path.close(); p.setShader(new LinearGradient(0, farH * 0.26f, 0, farH, farTopB, farBotB, Shader.TileMode.CLAMP)); fc.drawPath(path, p);
            p.setShader(new LinearGradient(0, farH * 0.45f, 0, farH, Color.TRANSPARENT, farMistCol, Shader.TileMode.CLAMP)); fc.drawRect(0, farH * 0.45f, bmpW, farH, p); p.setShader(null);

            int midH = Math.max(64, (int) (sH * 0.44f * scaleY)); Bitmap mMtn = Bitmap.createBitmap(bmpW, midH, Bitmap.Config.ARGB_8888); Canvas mc = new Canvas(mMtn);
            float[] mFreq = {1f,2f,3f,5f,8f}, mWgt = {0.36f,0.28f,0.20f,0.11f,0.05f}, mPh = {1.1f,2.8f,0.3f,4.4f,1.5f}, ridgeYs = new float[steps + 1];
            path.reset(); path.moveTo(0, midH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; ridgeYs[i] = sampleRidge(t, midH * 0.32f, midH * 0.22f, mFreq, mWgt, mPh); path.lineTo(t * bmpW, ridgeYs[i]); }
            path.lineTo(bmpW, midH); path.close();

            int midBaseTop, midBaseBot, meadowTop, meadowBot, shadeCol, midMist;
            if (themeState == 0) { midBaseTop = Color.parseColor("#47735B"); midBaseBot = Color.parseColor("#2A4B42"); meadowTop = Color.parseColor("#9EB85A"); meadowBot = Color.parseColor("#6D8D41"); shadeCol = Color.parseColor("#55244545"); midMist = Color.parseColor("#78CFE2ED"); }
            else if (themeState == 1) { midBaseTop = Color.parseColor("#1A3548"); midBaseBot = Color.parseColor("#0D1F2E"); meadowTop = Color.parseColor("#3B647C"); meadowBot = Color.parseColor("#214154"); shadeCol = Color.parseColor("#66081520"); midMist = Color.parseColor("#584C7B9E"); }
            else { midBaseTop = Color.parseColor("#0A1728"); midBaseBot = Color.parseColor("#030810"); meadowTop = Color.parseColor("#1A3656"); meadowBot = Color.parseColor("#0C1D33"); shadeCol = Color.parseColor("#88000000"); midMist = Color.parseColor("#381D4068"); }

            mc.save(); mc.clipPath(path);
            p.setShader(new LinearGradient(0, midH * 0.08f, 0, midH, midBaseTop, midBaseBot, Shader.TileMode.CLAMP)); mc.drawPath(path, p);

            Path facetPath = new Path();
            for (int i = 0; i < steps; i++) {
                float x0 = (i / (float) steps) * bmpW, x1 = ((i + 1) / (float) steps) * bmpW, y0 = ridgeYs[i], y1 = ridgeYs[i + 1], slope = y1 - y0;
                if (slope < 0.25f) {
                    float patchDepth = midH * (0.28f + Math.min(0.24f, Math.abs(slope) * 0.025f));
                    facetPath.reset(); facetPath.moveTo(x0, y0); facetPath.lineTo(x1 + 2f, y1); facetPath.lineTo(x1 + bmpW * 0.035f, y1 + patchDepth); facetPath.lineTo(x0 + bmpW * 0.025f, y0 + patchDepth); facetPath.close();
                    p.setShader(new LinearGradient(x0, y0, x0, y0 + patchDepth, meadowTop, Color.argb(0, Color.red(meadowBot), Color.green(meadowBot), Color.blue(meadowBot)), Shader.TileMode.CLAMP)); mc.drawPath(facetPath, p);
                } else {
                    float shadeDepth = midH * 0.42f;
                    facetPath.reset(); facetPath.moveTo(x0, y0); facetPath.lineTo(x1 + 2f, y1); facetPath.lineTo(x1 - bmpW * 0.03f, y1 + shadeDepth); facetPath.lineTo(x0 - bmpW * 0.04f, y0 + shadeDepth); facetPath.close();
                    p.setShader(null); p.setColor(shadeCol); mc.drawPath(facetPath, p);
                }
            }

            float[] vFreq = {1f,2f,4f}, vWgt = {0.45f,0.35f,0.20f}, vPh = {3.4f,0.9f,2.2f};
            Path valleyPath = new Path(); valleyPath.moveTo(0, midH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; valleyPath.lineTo(t * bmpW, sampleRidge(t, midH * 0.68f, midH * 0.11f, vFreq, vWgt, vPh)); }
            valleyPath.lineTo(bmpW, midH); valleyPath.close();
            p.setShader(new LinearGradient(0, midH * 0.56f, 0, midH, meadowTop, midBaseBot, Shader.TileMode.CLAMP)); mc.drawPath(valleyPath, p);
            p.setShader(new LinearGradient(0, midH * 0.66f, 0, midH, Color.TRANSPARENT, midMist, Shader.TileMode.CLAMP)); mc.drawRect(0, midH * 0.66f, bmpW, midH, p);
            p.setShader(null); mc.restore();

            int foreH = Math.max(64, (int) (sH * 0.44f * scaleY)); Bitmap fFor = Bitmap.createBitmap(bmpW, foreH, Bitmap.Config.ARGB_8888); Canvas frc = new Canvas(fFor);
            int hill1Top, hill1Bot, t1Trunk, t1Deep, t1Dark, t1Mid, t1Light, forestMist, hill2Top, hill2Bot, t2Trunk, t2Deep, t2Dark, t2Mid, t2Light;
            if (themeState == 0) {
                hill1Top = Color.parseColor("#547634"); hill1Bot = Color.parseColor("#2B471E"); t1Trunk = Color.parseColor("#3B2D1F"); t1Deep = Color.parseColor("#1F3816"); t1Dark = Color.parseColor("#2E5220"); t1Mid = Color.parseColor("#4F762F"); t1Light = Color.parseColor("#8FB449"); forestMist = Color.parseColor("#52C6DFC8");
                hill2Top = Color.parseColor("#223B18"); hill2Bot = Color.parseColor("#0E1D0A"); t2Trunk = Color.parseColor("#261B12"); t2Deep = Color.parseColor("#0B1A08"); t2Dark = Color.parseColor("#162E11"); t2Mid = Color.parseColor("#284B1C"); t2Light = Color.parseColor("#5A8233");
            } else if (themeState == 1) {
                hill1Top = Color.parseColor("#183340"); hill1Bot = Color.parseColor("#0B1C25"); t1Trunk = Color.parseColor("#0B171E"); t1Deep = Color.parseColor("#07151C"); t1Dark = Color.parseColor("#102935"); t1Mid = Color.parseColor("#1E4556"); t1Light = Color.parseColor("#52859B"); forestMist = Color.parseColor("#4C2D536B");
                hill2Top = Color.parseColor("#0A1921"); hill2Bot = Color.parseColor("#040B10"); t2Trunk = Color.parseColor("#050C11"); t2Deep = Color.parseColor("#030A0E"); t2Dark = Color.parseColor("#091C26"); t2Mid = Color.parseColor("#123140"); t2Light = Color.parseColor("#2E5D72");
            } else {
                hill1Top = Color.parseColor("#07131F"); hill1Bot = Color.parseColor("#02070D"); t1Trunk = Color.parseColor("#03070C"); t1Deep = Color.parseColor("#010509"); t1Dark = Color.parseColor("#05121E"); t1Mid = Color.parseColor("#0D2338"); t1Light = Color.parseColor("#28557A"); forestMist = Color.parseColor("#34122B47");
                hill2Top = Color.parseColor("#000000"); hill2Bot = Color.parseColor("#000000"); t2Trunk = Color.parseColor("#000000"); t2Deep = Color.parseColor("#000000"); t2Dark = Color.parseColor("#020810"); t2Mid = Color.parseColor("#071726"); t2Light = Color.parseColor("#183B5A");
            }

            float[] h1Freq = {1f,2f,3f}, h1Wgt = {0.48f,0.34f,0.18f}, h1Ph = {0.6f,2.3f,4.0f}, h1Ys = new float[steps + 1];
            path.reset(); path.moveTo(0, foreH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; h1Ys[i] = sampleRidge(t, foreH * 0.50f, foreH * 0.11f, h1Freq, h1Wgt, h1Ph); path.lineTo(t * bmpW, h1Ys[i]); }
            path.lineTo(bmpW, foreH); path.close(); p.setShader(new LinearGradient(0, foreH * 0.35f, 0, foreH, hill1Top, hill1Bot, Shader.TileMode.CLAMP)); frc.drawPath(path, p); p.setShader(null);

            Random p1Rnd = new Random(512L); int t1Rows = 3, t1Cols = 16; float[][] t1Trees = new float[t1Rows * t1Cols][5]; int t1Ptr = 0;
            for (int r = 0; r < t1Rows; r++) {
                float rowFrac = r / (float) (t1Rows - 1);
                for (int cIdx = 0; cIdx < t1Cols; cIdx++) {
                    float t = (((cIdx + ((r % 2) * 0.5f) + (p1Rnd.nextFloat() - 0.5f) * 0.55f) / t1Cols) % 1f + 1f) % 1f;
                    int idx = Math.min(steps - 1, (int) (t * steps)); float th = foreH * (0.30f + rowFrac * 0.09f + p1Rnd.nextFloat() * 0.14f), tw = th * (0.50f + p1Rnd.nextFloat() * 0.14f);
                    int span = Math.max(1, (int) ((tw * 0.50f / bmpW) * steps)), lIdx = ((idx - span) % steps + steps) % steps, rIdx = (idx + span) % steps;
                    float groundY = Math.max(h1Ys[idx], Math.max(h1Ys[lIdx], h1Ys[rIdx])), minBaseY = groundY + th * 0.14f, maxBaseY = Math.max(minBaseY, foreH * 0.88f);
                    float baseY = Math.max(minBaseY, minBaseY + (maxBaseY - minBaseY) * rowFrac + (p1Rnd.nextFloat() - 0.5f) * (foreH * 0.025f));
                    t1Trees[t1Ptr++] = new float[]{t * bmpW, baseY, th, tw, p1Rnd.nextFloat()};
                }
            }
            Arrays.sort(t1Trees, (a, b) -> Float.compare(a[1], b[1]));
            for (float[] tr : t1Trees) drawWrappedPineTree(frc, bmpW, tr[0], tr[1], tr[2], tr[3], tr[4], t1Trunk, t1Deep, t1Dark, t1Mid, t1Light, p, path);

            p.setShader(new LinearGradient(0, foreH * 0.48f, 0, foreH * 0.84f, Color.TRANSPARENT, forestMist, Shader.TileMode.CLAMP)); frc.drawRect(0, foreH * 0.48f, bmpW, foreH * 0.84f, p); p.setShader(null);

            float[] h2Freq = {1f,2f,4f}, h2Wgt = {0.50f,0.32f,0.18f}, h2Ph = {2.8f,0.4f,1.9f}, h2Ys = new float[steps + 1];
            path.reset(); path.moveTo(0, foreH);
            for (int i = 0; i <= steps; i++) { float t = i / (float) steps; h2Ys[i] = sampleRidge(t, foreH * 0.74f, foreH * 0.08f, h2Freq, h2Wgt, h2Ph); path.lineTo(t * bmpW, h2Ys[i]); }
            path.lineTo(bmpW, foreH); path.close(); p.setShader(new LinearGradient(0, foreH * 0.62f, 0, foreH, hill2Top, hill2Bot, Shader.TileMode.CLAMP)); frc.drawPath(path, p); p.setShader(null);

            Random p2Rnd = new Random(618L); int t2Rows = 3, t2Cols = 13; float[][] t2Trees = new float[t2Rows * t2Cols][5]; int t2Ptr = 0;
            for (int r = 0; r < t2Rows; r++) {
                float rowFrac = r / (float) (t2Rows - 1);
                for (int cIdx = 0; cIdx < t2Cols; cIdx++) {
                    float t = (((cIdx + ((r % 2) * 0.5f) + (p2Rnd.nextFloat() - 0.5f) * 0.55f) / t2Cols) % 1f + 1f) % 1f;
                    int idx = Math.min(steps - 1, (int) (t * steps)); float th = foreH * (0.40f + rowFrac * 0.14f + p2Rnd.nextFloat() * 0.18f), tw = th * (0.54f + p2Rnd.nextFloat() * 0.14f);
                    int span = Math.max(1, (int) ((tw * 0.50f / bmpW) * steps)), lIdx = ((idx - span) % steps + steps) % steps, rIdx = (idx + span) % steps;
                    float groundY = Math.max(h2Ys[idx], Math.max(h2Ys[lIdx], h2Ys[rIdx])), minBaseY = groundY + th * 0.12f, maxBaseY = Math.max(minBaseY, foreH + th * 0.24f);
                    float baseY = Math.max(minBaseY, minBaseY + (maxBaseY - minBaseY) * rowFrac + (p2Rnd.nextFloat() - 0.5f) * (foreH * 0.03f));
                    t2Trees[t2Ptr++] = new float[]{t * bmpW, baseY, th, tw, p2Rnd.nextFloat()};
                }
            }
            Arrays.sort(t2Trees, (a, b) -> Float.compare(a[1], b[1]));
            for (float[] tr : t2Trees) drawWrappedPineTree(frc, bmpW, tr[0], tr[1], tr[2], tr[3], tr[4], t2Trunk, t2Deep, t2Dark, t2Mid, t2Light, p, path);
            return new Bitmap[]{ fMtn, mMtn, fFor };
        }

        private void drawBuildingWrapped(Canvas c, float bmpW, float x, float top, float bw, float bh, int bodyTopCol, int bodyBotCol, int sideCol, int winCol1, int winCol2, int roofStyle, boolean addWindows, boolean addBeacon, Random rnd) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float[] offsets = (x < 0) ? new float[]{0f, bmpW} : ((x + bw > bmpW) ? new float[]{0f, -bmpW} : new float[]{0f});
            float spireH = (roofStyle == 1) ? bh * 0.16f : 0f, setbackW = (roofStyle == 2) ? bw * 0.58f : 0f, setbackH = (roofStyle == 2) ? bh * 0.10f : 0f;
            int cols = Math.max(2, (int) (bw / 16f)), rows = Math.max(3, (int) ((bh - 20f) / 22f));
            boolean[][] lit = new boolean[rows][cols], alt = new boolean[rows][cols];
            if (addWindows) for (int r = 0; r < rows; r++) for (int col = 0; col < cols; col++) { lit[r][col] = rnd.nextFloat() < (themeState == 0 ? 0.65f : 0.42f); alt[r][col] = rnd.nextFloat() < 0.30f; }

            for (float ox : offsets) {
                float bx = x + ox;
                p.setShader(new LinearGradient(bx, top, bx, top + bh, bodyTopCol, bodyBotCol, Shader.TileMode.CLAMP)); c.drawRect(bx, top, bx + bw, top + bh, p); p.setShader(null);
                p.setColor(sideCol); c.drawRect(bx + bw * 0.78f, top, bx + bw, top + bh, p);
                if (roofStyle == 1) {
                    p.setColor(bodyTopCol); c.drawRect(bx + bw * 0.25f, top - bh * 0.05f, bx + bw * 0.75f, top, p);
                    p.setStrokeWidth(3f); c.drawLine(bx + bw * 0.5f, top - bh * 0.05f, bx + bw * 0.5f, top - spireH, p);
                    if (addBeacon && themeState != 0) { p.setColor(Color.parseColor("#FF4B5C")); c.drawCircle(bx + bw * 0.5f, top - spireH, 3.5f, p); p.setColor(Color.parseColor("#44FF4B5C")); c.drawCircle(bx + bw * 0.5f, top - spireH, 9f, p); }
                } else if (roofStyle == 2) { p.setColor(bodyTopCol); c.drawRect(bx + (bw - setbackW) * 0.5f, top - setbackH, bx + (bw + setbackW) * 0.5f, top, p); }
                else if (roofStyle == 3) { Path slant = new Path(); slant.moveTo(bx, top); slant.lineTo(bx + bw, top - bw * 0.26f); slant.lineTo(bx + bw, top); slant.close(); p.setColor(bodyTopCol); c.drawPath(slant, p); }
                if (addWindows) {
                    float padX = bw * 0.14f, cellW = (bw * 0.62f) / cols, winW = Math.max(3f, cellW * 0.56f), winH = 8f, startY = top + 14f, stepY = Math.max(14f, (bh - 24f) / rows);
                    for (int r = 0; r < rows; r++) {
                        float wy = startY + r * stepY; if (wy + winH > top + bh - 8f) break;
                        for (int col = 0; col < cols; col++) { if (!lit[r][col]) continue; float wx = bx + padX + col * cellW; p.setColor(alt[r][col] ? winCol2 : winCol1); c.drawRect(wx, wy, wx + winW, wy + winH, p); }
                    }
                }
            }
        }

        private Bitmap[] createCityLayers(int bmpW, float sH, float scaleY) {
            int backH = Math.max(64, (int) (sH * 0.52f * scaleY)), midH = Math.max(64, (int) (sH * 0.58f * scaleY)), frontH = Math.max(64, (int) (sH * 0.40f * scaleY));
            Bitmap cBack = Bitmap.createBitmap(bmpW, backH, Bitmap.Config.ARGB_8888), cMid = Bitmap.createBitmap(bmpW, midH, Bitmap.Config.ARGB_8888), cFront = Bitmap.createBitmap(bmpW, frontH, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(cBack), mc = new Canvas(cMid), fc = new Canvas(cFront);

            int bTop, bBot, bSide, mTop, mBot, mSide, fTop, fBot, fSide, w1, w2, hazeCol;
            if (themeState == 0) { bTop = Color.parseColor("#8FAEC4"); bBot = Color.parseColor("#C2D8E6"); bSide = Color.parseColor("#7D9CB3"); mTop = Color.parseColor("#6E8DA3"); mBot = Color.parseColor("#97B3C4"); mSide = Color.parseColor("#57758A"); fTop = Color.parseColor("#476275"); fBot = Color.parseColor("#324756"); fSide = Color.parseColor("#2A3D4A"); w1 = Color.parseColor("#DDF2FF"); w2 = Color.parseColor("#FFF8DC"); hazeCol = Color.parseColor("#66D8EAF5"); }
            else if (themeState == 1) { bTop = Color.parseColor("#183042"); bBot = Color.parseColor("#234359"); bSide = Color.parseColor("#122433"); mTop = Color.parseColor("#132534"); mBot = Color.parseColor("#1C3447"); mSide = Color.parseColor("#0D1B26"); fTop = Color.parseColor("#0C1822"); fBot = Color.parseColor("#071017"); fSide = Color.parseColor("#050B11"); w1 = Color.parseColor("#FFE89E"); w2 = Color.parseColor("#B8E3FF"); hazeCol = Color.parseColor("#443A6482"); }
            else { bTop = Color.parseColor("#0B1626"); bBot = Color.parseColor("#050B14"); bSide = Color.parseColor("#060D17"); mTop = Color.parseColor("#08111F"); mBot = Color.parseColor("#040912"); mSide = Color.parseColor("#03070E"); fTop = Color.parseColor("#04080F"); fBot = Color.parseColor("#000000"); fSide = Color.parseColor("#010306"); w1 = Color.parseColor("#00E5FF"); w2 = Color.parseColor("#FFD166"); hazeCol = Color.parseColor("#30183A5E"); }

            Random rBack = new Random(801L); float cursor = 0f;
            while (cursor < bmpW) { float bw = bmpW * (0.065f + rBack.nextFloat() * 0.065f), bh = backH * (0.42f + rBack.nextFloat() * 0.44f); drawBuildingWrapped(bc, bmpW, cursor, backH - bh, bw, bh, bTop, bBot, bSide, w1, w2, rBack.nextInt(4), false, false, rBack); cursor += bw * (0.85f + rBack.nextFloat() * 0.20f); }
            Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG); hp.setShader(new LinearGradient(0, backH * 0.45f, 0, backH, Color.TRANSPARENT, hazeCol, Shader.TileMode.CLAMP)); bc.drawRect(0, backH * 0.45f, bmpW, backH, hp);

            Random rMid = new Random(802L); cursor = 0f;
            while (cursor < bmpW) { float bw = bmpW * (0.075f + rMid.nextFloat() * 0.08f), bh = midH * (0.38f + rMid.nextFloat() * 0.48f); int roof = rMid.nextInt(4); drawBuildingWrapped(mc, bmpW, cursor, midH - bh, bw, bh, mTop, mBot, mSide, w1, w2, roof, true, roof == 1, rMid); cursor += bw * (0.92f + rMid.nextFloat() * 0.25f); }

            Random rFront = new Random(803L); cursor = 0f;
            while (cursor < bmpW) { float bw = bmpW * (0.09f + rFront.nextFloat() * 0.09f), bh = frontH * (0.34f + rFront.nextFloat() * 0.46f); drawBuildingWrapped(fc, bmpW, cursor, frontH - bh, bw, bh, fTop, fBot, fSide, w1, w2, rFront.nextInt(3), true, false, rFront); cursor += bw * (1.02f + rFront.nextFloat() * 0.30f); }
            return new Bitmap[]{ cBack, cMid, cFront };
        }

        private void buildCityLayersAsync() {
            final int gen = bgBuildGen; final float sW = screenW, sH = screenH, tW = sW * 1.25f;
            final int bmpW = Math.max(256, Math.min((int) tW, 1440)); final float scaleY = bmpW / tW;
            cityBuildPending = true;
            Thread t = new Thread(() -> {
                if (detached || gen != bgBuildGen) return;
                final Bitmap[] city = createCityLayers(bmpW, sH, scaleY);
                post(() -> {
                    cityBuildPending = false;
                    if (detached || gen != bgBuildGen) { recycleBitmapArray(city); return; }
                    recycleBitmap(cityBackBmp); recycleBitmap(cityMidBmp); recycleBitmap(cityFrontBmp);
                    cityBackBmp = city[0]; cityMidBmp = city[1]; cityFrontBmp = city[2];
                    invalidate();
                });
            });
            t.setDaemon(true); t.start();
        }

        private void generateBackgroundLayersAsync() {
            if (screenW <= 0 || screenH <= 0) return;
            final int gen = ++bgBuildGen;
            final float sW = screenW, sH = screenH, rW = refW, rH = refH, bRad = birdRadius, wRad = wormRadius, pWid = pipeWidth;
            final float tW = sW * 1.25f; layerTileW = tW;
            final int bmpW = Math.max(256, Math.min((int) tW, 1440)); final float scaleY = bmpW / tW;
            final int initBird = birdType;
            final boolean needImmune = (immuneBmp == null), needSuppress = (suppressBmp == null);
            cityBuildPending = (initBird == 1);

            Thread bgThread = new Thread(() -> {
                if (detached || gen != bgBuildGen) return;
                final Bitmap cel = createCelestialBitmap(sW, sH); if (detached || gen != bgBuildGen) { recycleBitmap(cel); return; }
                final Bitmap[] clouds = createCloudLayers(bmpW, sH, scaleY); if (detached || gen != bgBuildGen) { recycleBitmap(cel); recycleBitmapArray(clouds); return; }
                final Bitmap[] worm = createGoldenWormBitmaps(wRad); if (detached || gen != bgBuildGen) { recycleBitmap(cel); recycleBitmapArray(clouds); recycleBitmapArray(worm); return; }
                final Bitmap[] shields = createShieldBitmaps(bRad); if (detached || gen != bgBuildGen) { recycleBitmap(cel); recycleBitmapArray(clouds); recycleBitmapArray(worm); recycleBitmapArray(shields); return; }
                final Bitmap[] pipeBmps = createPipeBitmaps(pWid, rW, rH); if (detached || gen != bgBuildGen) { recycleBitmap(cel); recycleBitmapArray(clouds); recycleBitmapArray(worm); recycleBitmapArray(shields); recycleBitmapArray(pipeBmps); return; }
                final Bitmap[] nature = (initBird == 0) ? createNatureLayers(bmpW, sH, scaleY) : null;
                final Bitmap[] city = (initBird == 1) ? createCityLayers(bmpW, sH, scaleY) : null;
                if (detached || gen != bgBuildGen) { recycleBitmap(cel); recycleBitmapArray(clouds); recycleBitmapArray(worm); recycleBitmapArray(shields); recycleBitmapArray(pipeBmps); recycleBitmapArray(nature); recycleBitmapArray(city); return; }

                Bitmap imm = null, sup = null;
                try {
                    int targetH = (int) (sH * 0.035f);
                    if (targetH > 0) {
                        if (needImmune) { int resId = getResources().getIdentifier("immune", "drawable", getContext().getPackageName()); if (resId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resId); imm = Bitmap.createScaledBitmap(raw, (int)(targetH * ((float)raw.getWidth()/raw.getHeight())), targetH, true); } }
                        if (needSuppress) { int resId = getResources().getIdentifier("suppress", "drawable", getContext().getPackageName()); if (resId != 0) { Bitmap raw = BitmapFactory.decodeResource(getResources(), resId); sup = Bitmap.createScaledBitmap(raw, (int)(targetH * ((float)raw.getWidth()/raw.getHeight())), targetH, true); } }
                    }
                } catch (Exception ignored) {}
                final Bitmap fImm = imm, fSup = sup;

                post(() -> {
                    if (detached || gen != bgBuildGen) {
                        recycleBitmap(cel); recycleBitmapArray(clouds); recycleBitmapArray(worm); recycleBitmapArray(shields); recycleBitmapArray(pipeBmps); recycleBitmapArray(nature); recycleBitmapArray(city); recycleBitmap(fImm); recycleBitmap(fSup);
                        return;
                    }
                    recycleBackgroundBitmaps();
                    celestialBmp = cel; farCloudBmp = clouds[0]; nearCloudBmp = clouds[1];
                    goldSegBmp = worm[0]; goldHeadBmp = worm[1]; goldAuraBmp = worm[2];
                    shieldGoldHaloBmp = shields[0]; shieldCosmicHaloBmp = shields[1]; shieldCrimsonHaloBmp = shields[2];
                    classicPipeBodyBmp = pipeBmps[0]; classicPipeCapBmp = pipeBmps[1]; clovPipeBodyBmp = pipeBmps[2]; clovPipeCapBmp = pipeBmps[3]; elecFlashBmp = pipeBmps[4];
                    if (nature != null) { farMountainBmp = nature[0]; midMountainBmp = nature[1]; foreForestBmp = nature[2]; }
                    if (city != null) { cityBackBmp = city[0]; cityMidBmp = city[1]; cityFrontBmp = city[2]; cityBuildPending = false; }
                    if (fImm != null && immuneBmp == null) immuneBmp = fImm; else recycleBitmap(fImm);
                    if (fSup != null && suppressBmp == null) suppressBmp = fSup; else recycleBitmap(fSup);
                    if (birdType == 1 && (cityBackBmp == null || cityBackBmp.isRecycled()) && !cityBuildPending) buildCityLayersAsync();
                    invalidate();
                });
            });
            bgThread.setDaemon(true); bgThread.start();
        }

        private void drawTiledBitmap(Canvas canvas, Bitmap bmp, float topY, float bottomY, float rawScroll, float speedMult) {
            if (bmp == null || bmp.isRecycled() || layerTileW <= 0f) return;
            float scrollOffset = (rawScroll * speedMult) % layerTileW; if (scrollOffset > 0) scrollOffset -= layerTileW;
            bgTileRect.set(scrollOffset, topY, scrollOffset + layerTileW, bottomY); canvas.drawBitmap(bmp, null, bgTileRect, bgBitmapPaint);
            bgTileRect.set(scrollOffset + layerTileW, topY, scrollOffset + 2f * layerTileW, bottomY); canvas.drawBitmap(bmp, null, bgTileRect, bgBitmapPaint);
        }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            float oldBirdYRatio = (oldHeight > 0) ? (birdY / (float) oldHeight) : 0.5f;
            screenW = w; screenH = h; refH = Math.max(screenW, screenH); refW = Math.min(screenW, screenH);
            birdRadius = refH * 0.035f; wormRadius = refH * 0.025f; birdX = screenW * 0.3f;
            gravity = refH * 0.0008f; jumpStrength = refH * -0.015f; terminalVelocity = refH * 0.018f;
            basePipeSpeed = refW * 0.006f; maxPipeSpeed = refW * 0.015f; pipeWidth = refW * 0.18f;
            pipeGap = (screenH > screenW) ? (screenH * 0.30f) : (screenH * 0.48f);

            buildSkyAndAtmosphere();
            generateBackgroundLayersAsync();
            if (birdBitmaps == null && isReady) loadBirdSprite();

            if (oldWidth == 0 || oldHeight == 0) resetGame();
            else {
                birdY = oldBirdYRatio * screenH; if (birdY < birdRadius) birdY = birdRadius + 10; if (birdY > screenH - birdRadius) birdY = screenH - birdRadius - 10;
                pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                for (Pipe p : pipes) { float maxTop = screenH - pipeGap - (screenH * 0.1f); if (p.topHeight > maxTop) p.topHeight = Math.max(screenH * 0.1f, maxTop); }
            }
        }

        public void resetGame() {
            birdX = screenW * 0.3f; birdY = screenH / 2f; birdVelocity = 0; pipes.clear(); trailHead = 0; trailCount = 0; score = 0; pipeSpeed = basePipeSpeed; pointsSinceLastWorm = 0; isImmune = false; immunityEndTime = 0; shieldStartTime = 0; playing = false; paused = false; gameOver = false;
            gooDying = false; gooDeathSpin = 0f; deathSnapshot = null;
            for (DustParticle dp : dustParticles) dp.active = false;
            if (flapSequence != null && flapSequence.length > 0) currentFrameIndex = flapSequence[0];
            sequenceIndex = 0; animationTick = 0; lastFrameTime = 0; accumulator = 0; invalidate();
        }

        public void pauseGame() { paused = true; lastFrameTime = 0; accumulator = 0; }
        public void resumeGame() { paused = false; lastFrameTime = 0; accumulator = 0; Choreographer.getInstance().postFrameCallback(this); }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }

        private void spawnPipe() {
            float minTop = screenH * 0.1f, maxTop = screenH - pipeGap - minTop;
            Pipe newPipe = new Pipe(screenW, minTop + (float) Math.random() * (maxTop - minTop));
            if (!isImmune && pointsSinceLastWorm >= 5) { newPipe.hasWorm = true; pointsSinceLastWorm = 0; }
            pipes.add(newPipe);
        }

        private void vibrateDeath() {
            if (vibrationEnabled) { Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (v != null && v.hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE)); else v.vibrate(800); } }
        }

        private void vibratePillarPass() {
            if (vibrationEnabled) { Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (v != null && v.hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(18, 45)); else v.vibrate(18); } }
        }

        @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event) {
            if (!isReady) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (gameOver || paused || gooDying) return true;
                if (!playing) { playing = true; lastFrameTime = 0; accumulator = 0; if (listener != null) listener.onGameStarted(); Choreographer.getInstance().postFrameCallback(this); }
                birdVelocity = jumpStrength; return true;
            } return super.onTouchEvent(event);
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (!playing || paused || gameOver) { lastFrameTime = 0; accumulator = 0; return; }
            if (lastFrameTime == 0) { lastFrameTime = frameTimeNanos; Choreographer.getInstance().postFrameCallback(this); return; }
            long elapsed = frameTimeNanos - lastFrameTime; lastFrameTime = frameTimeNanos; accumulator += elapsed;
            if (accumulator > TIME_STEP_NS * 5) accumulator = TIME_STEP_NS * 5;
            boolean stepped = false;
            while (accumulator >= TIME_STEP_NS) { physicsStep(); accumulator -= TIME_STEP_NS; stepped = true; }
            if (stepped) invalidate();
            if (!gameOver) Choreographer.getInstance().postFrameCallback(this);
        }

        private void physicsStep() {
            if (isImmune && System.currentTimeMillis() >= immunityEndTime) isImmune = false;
            sparkleAngle += 12f; if (sparkleAngle > 360f) sparkleAngle -= 360f;
            bgAmbientTick += 1f;

            if (themeState == 2) {
                if (shootingStarActive) { ssX += ssVx; ssY += ssVy; ssLife -= 1f; if (ssLife <= 0f) shootingStarActive = false; }
                else if (--shootingStarCooldown <= 0 && screenW > 0 && screenH > 0) {
                    shootingStarActive = true; ssX = screenW * (0.35f + (float) Math.random() * 0.60f); ssY = screenH * (0.04f + (float) Math.random() * 0.24f);
                    ssVx = -(screenW * 0.016f + (float) Math.random() * screenW * 0.008f); ssVy = screenH * 0.006f + (float) Math.random() * screenH * 0.005f;
                    ssMaxLife = 24f + (float) Math.random() * 14f; ssLife = ssMaxLife; shootingStarCooldown = 190 + (int) (Math.random() * 240);
                }
            }

            if (gooDying) { birdVelocity += gravity * 1.5f; birdY += birdVelocity; birdX -= basePipeSpeed * 0.8f; gooDeathSpin -= 15f; if (birdY > screenH + birdRadius * 2f) triggerGameOver(); return; }

            parallaxScroll -= pipeSpeed; birdVelocity += gravity; if (birdVelocity > terminalVelocity) birdVelocity = terminalVelocity; birdY += birdVelocity;
            float speedFactor = 1f; int flapSpeedThreshold = (birdType == 0) ? 8 : 4;

            if (birdType == 0) {
                if (birdVelocity < terminalVelocity * 0.4f) { animationTick += speedFactor; if (animationTick >= flapSpeedThreshold && flapSequence != null) { animationTick = 0; sequenceIndex = (sequenceIndex + 1) % flapSequence.length; currentFrameIndex = flapSequence[sequenceIndex]; } }
                else { animationTick += speedFactor; if (animationTick >= flapSpeedThreshold && flapSequence != null) { currentFrameIndex = flapSequence[0]; sequenceIndex = 0; } }
            } else {
                if (birdVelocity > terminalVelocity * 0.15f && flapSequence != null) { currentFrameIndex = flapSequence[0]; animationTick = 0; }
                else { animationTick += speedFactor; if (animationTick >= flapSpeedThreshold && flapSequence != null) { animationTick = 0; sequenceIndex = (sequenceIndex + 1) % flapSequence.length; currentFrameIndex = flapSequence[sequenceIndex]; } }
            }

            float tiltAngle = 0f, tailOffset = birdRadius * 0.75f;
            if (birdType == 0) { if (birdVelocity < 0) tiltAngle = -25f; else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; } } else tailOffset = birdRadius * 1.5f;
            float tailX = birdX - (float) Math.cos(Math.toRadians(tiltAngle)) * tailOffset, tailY = birdY - (float) Math.sin(Math.toRadians(tiltAngle)) * tailOffset;

            if (birdType == 0) {
                trailHead = (trailHead + 1) % MAX_TRAIL; trail[trailHead].x = tailX; trail[trailHead].y = tailY; if (trailCount < MAX_TRAIL) trailCount++;
                for (int i = 0; i < trailCount; i++) trail[i].x -= pipeSpeed;
            } else {
                if (Math.random() < 0.2) {
                    for (int i = 0; i < 3; i++) {
                        if (!dustParticles[i].active) {
                            dustParticles[i].active = true; dustParticles[i].x = tailX; dustParticles[i].y = tailY;
                            dustParticles[i].vx = (float) ((Math.random() - 0.5) * refW * 0.001f) - (pipeSpeed * 0.2f); dustParticles[i].vy = (float) ((Math.random() - 0.5) * refH * 0.002f);
                            dustParticles[i].maxLife = 15f + (float)(Math.random() * 10f); dustParticles[i].life = dustParticles[i].maxLife; break;
                        }
                    }
                }
                for (int i = 0; i < 3; i++) if (dustParticles[i].active) { dustParticles[i].x += dustParticles[i].vx; dustParticles[i].y += dustParticles[i].vy; dustParticles[i].life -= 1f; if (dustParticles[i].life <= 0) dustParticles[i].active = false; }
            }

            for (int i = 0; i < pipes.size(); i++) {
                Pipe p = pipes.get(i); p.x -= pipeSpeed;
                if (birdType == 1) {
                    if (p.topBoltLife > 0) p.topBoltLife--;
                    else if (--p.topBoltCooldown <= 0) { p.topBoltMax = 7 + (int)(Math.random() * 7); p.topBoltLife = p.topBoltMax; p.topBoltSeed = System.nanoTime() ^ ((long) i * 31337L); p.topBoltCooldown = 12 + (int)(Math.random() * 28); }
                    if (p.botBoltLife > 0) p.botBoltLife--;
                    else if (--p.botBoltCooldown <= 0) { p.botBoltMax = 7 + (int)(Math.random() * 7); p.botBoltLife = p.botBoltMax; p.botBoltSeed = (System.nanoTime() * 31L) ^ ((long) i * 7919L); p.botBoltCooldown = 12 + (int)(Math.random() * 28); }
                }
                if (p.hasWorm && !p.wormEaten) {
                    float dxx = birdX - (p.x + (pipeWidth / 2f)), dyy = birdY - (p.topHeight + (pipeGap / 2f));
                    if ((dxx * dxx) + (dyy * dyy) <= ((birdRadius + wormRadius) * (birdRadius + wormRadius))) {
                        p.wormEaten = true; isImmune = true; shieldStartTime = System.currentTimeMillis(); immunityEndTime = shieldStartTime + 4000;
                        if (audioEngine != null) audioEngine.playPowerUp();
                        if (vibrationEnabled) { Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE); if (vibrator != null && vibrator.hasVibrator()) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(50); } }
                    }
                }
                if (!p.passed && p.x + pipeWidth < birdX) {
                    p.passed = true; score++; if (!isImmune) pointsSinceLastWorm++;
                    pipeSpeed = basePipeSpeed + (score * (refW * 0.0001f)); if (pipeSpeed > maxPipeSpeed) pipeSpeed = maxPipeSpeed;
                    vibratePillarPass();
                    if (audioEngine != null) audioEngine.playBirdHappy(birdType);
                    if (listener != null) listener.onScoreUpdated(score);
                }
            }

            boolean canSpawnPipe = !(birdType == 1 && isImmune);
            if (!pipes.isEmpty() && pipes.get(0).x + pipeWidth < 0) pipes.remove(0);
            if (canSpawnPipe && (pipes.isEmpty() || screenW - pipes.get(pipes.size() - 1).x > (screenW * 0.55f))) spawnPipe();
            checkCollisions();
        }

        private void checkCollisions() {
            if (gooDying) return;
            boolean hitFloorOrCeil = (birdY + birdRadius >= screenH || birdY - birdRadius <= 0), hitPipe = false;
            float hitR = birdRadius * (birdType == 0 ? 0.65f : 0.55f);
            hitRect.set(birdX - hitR, birdY - hitR, birdX + hitR, birdY + hitR);

            if (!hitFloorOrCeil) {
                for (Pipe p : pipes) {
                    topCollision.set(p.x, 0, p.x + pipeWidth, p.topHeight); bottomCollision.set(p.x, p.topHeight + pipeGap, p.x + pipeWidth, screenH);
                    if (RectF.intersects(hitRect, topCollision) || RectF.intersects(hitRect, bottomCollision)) { hitPipe = true; break; }
                }
            }

            if (hitFloorOrCeil || hitPipe) {
                if (isImmune && birdType == 0) {
                    if (birdY - birdRadius <= 0) { birdY = birdRadius + 1; birdVelocity = 0; }
                    if (birdY + birdRadius >= screenH) { birdY = screenH - birdRadius - 1; birdVelocity = jumpStrength; }
                } else if (birdType == 1) {
                    if (audioEngine != null) audioEngine.playBirdCry(1);
                    deathSnapshot = getDeathSnapshot(); gooDying = true; birdVelocity = jumpStrength * 0.8f; vibrateDeath();
                } else {
                    if (audioEngine != null) audioEngine.playBirdCry(0);
                    deathSnapshot = getDeathSnapshot(); triggerGameOver();
                }
            }
        }

        private Bitmap getDeathSnapshot() {
            try {
                Bitmap fullBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888); Canvas canvas = new Canvas(fullBitmap); this.draw(canvas);
                int size = (int) (Math.min(getWidth(), getHeight()) * 0.40f); if (size <= 0) size = 300;
                int left = Math.max(0, Math.min((int) (birdX - size / 2f), getWidth() - size)), top = Math.max(0, Math.min((int) (birdY - size / 2f), getHeight() - size));
                Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, size, size); fullBitmap.recycle(); return cropped;
            } catch (Exception e) { return null; }
        }

        private void triggerGameOver() {
            if (gameOver) return; gameOver = true; playing = false; lastFrameTime = 0; accumulator = 0;
            if (birdType == 0) vibrateDeath(); if (deathSnapshot == null) deathSnapshot = getDeathSnapshot();
            if (listener != null) listener.onGameOver(score, deathSnapshot, birdType);
        }

        private void buildFractalThunderBolt(float x1, float y1, float x2, float y2, float maxDisp, long seed, float liveJitter) {
            boltRnd.setSeed(seed); boltXs[0] = x1; boltYs[0] = y1; boltXs[16] = x2; boltYs[16] = y2;
            float disp = maxDisp;
            for (int step = 8; step >= 1; step >>= 1) {
                for (int i = 0; i < 16; i += step * 2) {
                    int mid = i + step, end = i + step * 2;
                    float mx = (boltXs[i] + boltXs[end]) * 0.5f, my = (boltYs[i] + boltYs[end]) * 0.5f, dx = boltXs[end] - boltXs[i], dy = boltYs[end] - boltYs[i], len = (float) Math.hypot(dx, dy);
                    if (len < 1f) len = 1f; float nx = -dy / len, ny = dx / len, off = (boltRnd.nextFloat() - 0.5f) * 2f * disp;
                    boltXs[mid] = mx + nx * off; boltYs[mid] = my + ny * off * 0.35f;
                }
                disp *= 0.54f;
            }

            electricPath.reset(); boltBranchPath.reset(); electricPath.moveTo(boltXs[0], boltYs[0]);
            for (int i = 1; i <= 16; i++) {
                float jx = (i < 16) ? (float) Math.sin(bgAmbientTick * 2.8f + i * 2.1f + (seed & 31)) * liveJitter : 0f;
                float jy = (i < 16) ? (float) Math.cos(bgAmbientTick * 2.3f + i * 1.7f) * (liveJitter * 0.4f) : 0f;
                boltXs[i] += jx; boltYs[i] += jy; electricPath.lineTo(boltXs[i], boltYs[i]);
            }

            for (int fIdx : new int[]{ 4, 8, 12 }) {
                if (boltRnd.nextFloat() < 0.72f) {
                    float bx = boltXs[fIdx], by = boltYs[fIdx], dirX = (boltRnd.nextBoolean() ? 1f : -1f) * (pipeWidth * (0.20f + boltRnd.nextFloat() * 0.25f)), dirY = (y2 > y1 ? 1f : -1f) * (pipeWidth * (0.18f + boltRnd.nextFloat() * 0.26f));
                    boltBranchPath.moveTo(bx, by);
                    for (int seg = 1; seg <= 4; seg++) { bx += dirX * 0.28f + (boltRnd.nextFloat() - 0.5f) * (pipeWidth * 0.14f); by += dirY * 0.28f + (boltRnd.nextFloat() - 0.5f) * (pipeWidth * 0.10f); boltBranchPath.lineTo(bx, by); }
                }
            }
        }

        private void drawContactSpark(Canvas c, float x, float y, float radius, float intensity) {
            if (intensity <= 0.05f) return;
            if (elecFlashBmp != null && !elecFlashBmp.isRecycled()) {
                float r = radius * (0.85f + 0.35f * intensity); pipeTempRect.set(x - r, y - r, x + r, y + r);
                bgBitmapPaint.setAlpha((int) (235 * intensity)); c.drawBitmap(elecFlashBmp, null, pipeTempRect, bgBitmapPaint); bgBitmapPaint.setAlpha(255);
            }
            drawColoredDiamondStar(c, x, y, radius * 0.52f * intensity, Color.WHITE, (int) (255 * intensity));
        }

        private void drawRealisticThunderShocks(Canvas c, RectF bodyRect, RectF capRect, boolean isTop, int boltLife, int boltMax, long boltSeed) {
            float bodyH = bodyRect.height(), lipY = isTop ? capRect.bottom - capRect.height() * 0.50f : capRect.top + capRect.height() * 0.50f;
            float capPulse = 0.45f + 0.55f * (float) Math.abs(Math.sin(bgAmbientTick * 0.34f + capRect.left * 0.03f));
            if (capPulse > 0.65f) {
                buildFractalThunderBolt(capRect.left + capRect.width() * 0.12f, lipY, capRect.right - capRect.width() * 0.12f, lipY, capRect.height() * 0.22f, ((long) (bgAmbientTick * 0.45f)) * 97L + (isTop ? 11L : 53L), 1.8f);
                int cA = (int) ((capPulse - 0.5f) * 260f);
                elecGlowPaint.setColor(Color.parseColor("#00E5FF")); elecGlowPaint.setStrokeWidth(7f); elecGlowPaint.setAlpha(Math.min(130, cA)); c.drawPath(electricPath, elecGlowPaint);
                elecWhitePaint.setColor(Color.WHITE); elecWhitePaint.setStrokeWidth(2.0f); elecWhitePaint.setAlpha(Math.min(220, cA + 50)); c.drawPath(electricPath, elecWhitePaint);
            }

            if (boltLife <= 0 || bodyH < 24f) return;
            float lifeFrac = boltLife / (float) Math.max(1, boltMax);
            float flicker = (lifeFrac > 0.72f) ? 1.0f : (lifeFrac * (0.65f + 0.35f * (float) Math.abs(Math.sin(bgAmbientTick * 3.1f + (boltSeed & 15)))));
            if (flicker <= 0.08f) return;

            boltRnd.setSeed(boltSeed ^ 0x5A5A5A5AL);
            float startY = isTop ? bodyRect.bottom : bodyRect.top, arcLen = Math.min(bodyH, Math.max(pipeWidth * 1.4f, bodyH * (0.48f + boltRnd.nextFloat() * 0.48f)));
            float endY = isTop ? Math.max(bodyRect.top + 8f, startY - arcLen) : Math.min(bodyRect.bottom - 8f, startY + arcLen);
            float startX = bodyRect.left + bodyRect.width() * (0.14f + boltRnd.nextFloat() * 0.72f), endX = bodyRect.left + bodyRect.width() * (0.12f + boltRnd.nextFloat() * 0.76f);

            pipeTempRect.set(bodyRect.left, Math.min(startY, endY), bodyRect.right, Math.max(startY, endY));
            pipeDetailPaint.setColor(Color.parseColor("#00D8FF")); pipeDetailPaint.setAlpha((int) ((themeState == 0 ? 34 : 56) * flicker)); c.drawRect(pipeTempRect, pipeDetailPaint);

            buildFractalThunderBolt(startX, startY, endX, endY, pipeWidth * 0.34f, boltSeed, 3.6f);

            elecCoronaPaint.setColor(Color.parseColor("#2962FF")); elecCoronaPaint.setStrokeWidth(22f * flicker + 6f); elecCoronaPaint.setAlpha((int) (68 * flicker)); c.drawPath(electricPath, elecCoronaPaint);
            elecCoronaPaint.setStrokeWidth(11f * flicker + 3f); elecCoronaPaint.setAlpha((int) (46 * flicker)); c.drawPath(boltBranchPath, elecCoronaPaint);
            elecGlowPaint.setColor(Color.parseColor("#00E5FF")); elecGlowPaint.setStrokeWidth(11f * flicker + 3f); elecGlowPaint.setAlpha((int) (145 * flicker)); c.drawPath(electricPath, elecGlowPaint);
            elecGlowPaint.setStrokeWidth(5.5f * flicker + 1.8f); elecGlowPaint.setAlpha((int) (115 * flicker)); c.drawPath(boltBranchPath, elecGlowPaint);
            elecInnerPaint.setColor(Color.parseColor("#B8F8FF")); elecInnerPaint.setStrokeWidth(5.0f * flicker + 1.6f); elecInnerPaint.setAlpha((int) (220 * flicker)); c.drawPath(electricPath, elecInnerPaint);
            elecInnerPaint.setStrokeWidth(2.6f * flicker + 1.0f); elecInnerPaint.setAlpha((int) (180 * flicker)); c.drawPath(boltBranchPath, elecInnerPaint);
            elecWhitePaint.setColor(Color.WHITE); elecWhitePaint.setStrokeWidth(2.4f * flicker + 0.9f); elecWhitePaint.setAlpha((int) (255 * flicker)); c.drawPath(electricPath, elecWhitePaint);
            elecWhitePaint.setStrokeWidth(1.3f * flicker + 0.6f); elecWhitePaint.setAlpha((int) (220 * flicker)); c.drawPath(boltBranchPath, elecWhitePaint);

            drawContactSpark(c, startX, startY, pipeWidth * 0.34f, flicker);
            drawContactSpark(c, endX, endY, pipeWidth * 0.28f, flicker * 0.92f);
            drawContactSpark(c, boltXs[8], boltYs[8], pipeWidth * 0.22f, flicker * 0.75f);

            elecSparkPaint.setColor(Color.parseColor("#D8FCFF")); elecSparkPaint.setAlpha((int) (230 * flicker));
            for (int sp = 0; sp < 4; sp++) {
                float sx = boltXs[4 + sp * 3] + (float) Math.cos(boltSeed + sp * 2.3f + bgAmbientTick * 0.9f) * (pipeWidth * 0.24f * (1.2f - lifeFrac));
                float sy = boltYs[4 + sp * 3] + (float) Math.sin(boltSeed + sp * 1.9f + bgAmbientTick * 0.9f) * (pipeWidth * 0.20f * (1.2f - lifeFrac));
                c.drawCircle(sx, sy, 2.2f + 1.4f * flicker, elecSparkPaint);
            }
        }

        private void drawDetailedPipe(Canvas canvas, RectF bodyRect, RectF capRect, boolean isTop, Pipe pipe) {
            Bitmap bodyBmp = (birdType == 0) ? classicPipeBodyBmp : clovPipeBodyBmp;
            Bitmap capBmp = (birdType == 0) ? classicPipeCapBmp : clovPipeCapBmp;

            if (bodyRect.bottom > bodyRect.top + 1f && bodyBmp != null && !bodyBmp.isRecycled()) {
                canvas.drawBitmap(bodyBmp, null, bodyRect, bgBitmapPaint);
                if (birdType == 1) {
                    float seamStep = pipeWidth * 0.85f, edgeIn = Math.max(3f, pipeWidth * 0.036f);
                    if (isTop) {
                        for (float sy = bodyRect.bottom - seamStep * 0.7f; sy > bodyRect.top + 12f; sy -= seamStep) {
                            pipeDetailPaint.setColor(Color.argb(140, 12, 18, 26)); canvas.drawRect(bodyRect.left + edgeIn, sy, bodyRect.right - edgeIn, sy + 3.2f, pipeDetailPaint);
                            pipeDetailPaint.setColor(Color.argb(110, 210, 238, 255)); canvas.drawRect(bodyRect.left + edgeIn, sy + 3.2f, bodyRect.right - edgeIn, sy + 5.2f, pipeDetailPaint);
                        }
                    } else {
                        for (float sy = bodyRect.top + seamStep * 0.7f; sy < bodyRect.bottom - 12f; sy += seamStep) {
                            pipeDetailPaint.setColor(Color.argb(140, 12, 18, 26)); canvas.drawRect(bodyRect.left + edgeIn, sy, bodyRect.right - edgeIn, sy + 3.2f, pipeDetailPaint);
                            pipeDetailPaint.setColor(Color.argb(110, 210, 238, 255)); canvas.drawRect(bodyRect.left + edgeIn, sy + 3.2f, bodyRect.right - edgeIn, sy + 5.2f, pipeDetailPaint);
                        }
                    }
                }
                float shadowH = Math.min(bodyRect.height(), capRect.height() * 0.55f);
                if (shadowH > 2f) {
                    float sTop = isTop ? (bodyRect.bottom - shadowH) : bodyRect.top, sBot = isTop ? bodyRect.bottom : (bodyRect.top + shadowH);
                    pipeShadowPaint.setShader(new LinearGradient(0, sTop, 0, sBot, isTop ? Color.TRANSPARENT : Color.argb(150, 5, 12, 10), isTop ? Color.argb(150, 5, 12, 10) : Color.TRANSPARENT, Shader.TileMode.CLAMP));
                    canvas.drawRect(bodyRect.left, sTop, bodyRect.right, sBot, pipeShadowPaint); pipeShadowPaint.setShader(null);
                }
            }
            if (capBmp != null && !capBmp.isRecycled()) canvas.drawBitmap(capBmp, null, capRect, bgBitmapPaint);
            if (birdType == 1) drawRealisticThunderShocks(canvas, bodyRect, capRect, isTop, isTop ? pipe.topBoltLife : pipe.botBoltLife, isTop ? pipe.topBoltMax : pipe.botBoltMax, isTop ? pipe.topBoltSeed : pipe.botBoltSeed);
        }

        private void drawDiamondStar(Canvas canvas, float x, float y, float s, int alpha) { drawColoredDiamondStar(canvas, x, y, s, Color.parseColor("#FFFCE0"), alpha); }

        private void drawColoredDiamondStar(Canvas canvas, float x, float y, float s, int color, int alpha) {
            wormStarPath.reset(); wormStarPath.moveTo(x, y - s); wormStarPath.quadTo(x, y, x + s, y); wormStarPath.quadTo(x, y, x, y + s); wormStarPath.quadTo(x, y, x - s, y); wormStarPath.quadTo(x, y, x, y - s); wormStarPath.close();
            wormSparklePaint.setColor(color); wormSparklePaint.setAlpha(Math.max(0, Math.min(255, alpha))); canvas.drawPath(wormStarPath, wormSparklePaint);
        }

        private void buildRegularPolygon(Path out, float cx, float cy, float r, int sides, float rotDeg) {
            out.reset();
            for (int i = 0; i < sides; i++) { double a = Math.toRadians(rotDeg + (360.0 / sides) * i); float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r; if (i == 0) out.moveTo(px, py); else out.lineTo(px, py); }
            out.close();
        }

        private float getShieldFadeMultiplier() {
            long now = System.currentTimeMillis(), left = immunityEndTime - now;
            if (left < 800L && left > 0L) return 0.55f + 0.45f * (float) Math.abs(Math.sin(now * 0.022));
            return 1.0f;
        }

        private void drawMajesticShieldBack(Canvas canvas) {
            long now = System.currentTimeMillis(); float elapsed = (now - shieldStartTime) / 1000f, fade = getShieldFadeMultiplier(), pulse = 1.0f + 0.06f * (float) Math.sin(bgAmbientTick * 0.22f);

            if (elapsed >= 0f && elapsed < 0.45f) {
                float u = elapsed / 0.45f, waveR = birdRadius * (1.4f + u * 3.8f); int waveAlpha = (int) ((1f - u) * 210);
                int waveCol = (themeState == 2) ? Color.parseColor("#00FFCC") : (birdType == 1 ? Color.parseColor("#FF7A00") : Color.parseColor("#FFE600"));
                shieldBloomPaint.setColor(waveCol); shieldBloomPaint.setStrokeWidth(16f * (1f - u) + 3f); shieldBloomPaint.setAlpha(waveAlpha / 2); canvas.drawCircle(birdX, birdY, waveR, shieldBloomPaint);
                shieldCorePaint.setColor(Color.WHITE); shieldCorePaint.setStrokeWidth(4f * (1f - u) + 1f); shieldCorePaint.setAlpha(waveAlpha); canvas.drawCircle(birdX, birdY, waveR, shieldCorePaint);
            }

            if (themeState == 2) {
                if (shieldCosmicHaloBmp != null && !shieldCosmicHaloBmp.isRecycled()) {
                    float haloR = birdRadius * 3.45f * pulse; wormDrawRect.set(birdX - haloR, birdY - haloR, birdX + haloR, birdY + haloR);
                    bgBitmapPaint.setAlpha((int) (235 * fade)); canvas.drawBitmap(shieldCosmicHaloBmp, null, wormDrawRect, bgBitmapPaint); bgBitmapPaint.setAlpha(255);
                }
                if (birdType == 0) {
                    float domeR = birdRadius * 2.18f * pulse;
                    canvas.save(); canvas.translate(birdX, birdY); canvas.rotate(sparkleAngle * 0.35f);
                    wormRayPaint.setColor(Color.parseColor("#00F0FF")); wormRayPaint.setAlpha((int) (46 * fade));
                    for (int i = 0; i < 8; i++) { canvas.rotate(45f); wormStarPath.reset(); wormStarPath.moveTo(-birdRadius * 0.14f, 0f); wormStarPath.lineTo(0f, -domeR * 1.18f); wormStarPath.lineTo(birdRadius * 0.14f, 0f); wormStarPath.close(); canvas.drawPath(wormStarPath, wormRayPaint); }
                    canvas.restore();
                    shieldFillPaint.setColor(Color.parseColor("#00D8FF")); shieldFillPaint.setAlpha((int) (46 * fade)); canvas.drawCircle(birdX, birdY, domeR, shieldFillPaint);
                    shieldCorePaint.setColor(Color.parseColor("#80FFFF")); shieldCorePaint.setStrokeWidth(2.0f); shieldCorePaint.setAlpha((int) (75 * fade));
                    shieldOval.set(birdX - domeR * 0.52f, birdY - domeR, birdX + domeR * 0.52f, birdY + domeR); canvas.drawOval(shieldOval, shieldCorePaint);

                    float rx = birdRadius * 3.05f * pulse, ry = birdRadius * 0.78f * pulse; saucerRect.set(birdX - rx, birdY - ry, birdX + rx, birdY + ry);
                    shieldFillPaint.setColor(Color.parseColor("#0A2A3F")); shieldFillPaint.setAlpha((int) (135 * fade)); canvas.drawArc(saucerRect, 180f, 180f, false, shieldFillPaint);
                    shieldBloomPaint.setColor(Color.parseColor("#00FFAA")); shieldBloomPaint.setStrokeWidth(16f); shieldBloomPaint.setAlpha((int) (72 * fade)); canvas.drawArc(saucerRect, 180f, 180f, false, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#00FFD5")); shieldStrokePaint.setStrokeWidth(6f); shieldStrokePaint.setAlpha((int) (200 * fade)); canvas.drawArc(saucerRect, 180f, 180f, false, shieldStrokePaint);
                } else {
                    float polyR = birdRadius * 2.55f * pulse, rotOuter = sparkleAngle * 0.45f, rotInner = -sparkleAngle * 0.65f;
                    canvas.save(); canvas.translate(birdX, birdY); canvas.rotate(rotOuter * 0.6f);
                    wormRayPaint.setColor(Color.parseColor("#00FFA6")); wormRayPaint.setAlpha((int) (44 * fade));
                    for (int i = 0; i < 9; i++) { canvas.rotate(40f); wormStarPath.reset(); wormStarPath.moveTo(-birdRadius * 0.12f, 0f); wormStarPath.lineTo(0f, -polyR * 1.20f); wormStarPath.lineTo(birdRadius * 0.12f, 0f); wormStarPath.close(); canvas.drawPath(wormStarPath, wormRayPaint); }
                    canvas.restore();
                    buildRegularPolygon(shieldPath, birdX, birdY, polyR, 9, rotOuter); shieldFillPaint.setColor(Color.parseColor("#00E5FF")); shieldFillPaint.setAlpha((int) (34 * fade)); canvas.drawPath(shieldPath, shieldFillPaint);
                    buildRegularPolygon(shieldInnerPath, birdX, birdY, polyR * 0.78f, 9, rotInner); shieldFillPaint.setColor(Color.parseColor("#00FF9D")); shieldFillPaint.setAlpha((int) (26 * fade)); canvas.drawPath(shieldInnerPath, shieldFillPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#66FFCC")); shieldStrokePaint.setStrokeWidth(2.6f); shieldStrokePaint.setAlpha((int) (135 * fade)); canvas.drawPath(shieldInnerPath, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.parseColor("#80F8FF")); shieldCorePaint.setStrokeWidth(1.8f); shieldCorePaint.setAlpha((int) (78 * fade));
                    for (int i = 0; i < 9; i++) { double a1 = Math.toRadians(rotInner + i * 40.0), a2 = Math.toRadians(rotInner + ((i + 4) % 9) * 40.0); canvas.drawLine(birdX + (float) Math.cos(a1) * polyR * 0.78f, birdY + (float) Math.sin(a1) * polyR * 0.78f, birdX + (float) Math.cos(a2) * polyR * 0.78f, birdY + (float) Math.sin(a2) * polyR * 0.78f, shieldCorePaint); }
                }
            } else {
                Bitmap halo = (birdType == 1) ? shieldCrimsonHaloBmp : shieldGoldHaloBmp;
                if (halo != null && !halo.isRecycled()) {
                    float haloR = birdRadius * 3.35f * pulse; wormDrawRect.set(birdX - haloR, birdY - haloR, birdX + haloR, birdY + haloR);
                    bgBitmapPaint.setAlpha((int) ((themeState == 0 ? 205 : 235) * fade)); canvas.drawBitmap(halo, null, wormDrawRect, bgBitmapPaint); bgBitmapPaint.setAlpha(255);
                }
                float shieldR = birdRadius * (birdType == 1 ? 2.30f : 2.10f) * pulse;
                canvas.save(); canvas.translate(birdX, birdY); canvas.rotate(sparkleAngle * 0.38f);
                wormRayPaint.setColor(birdType == 1 ? Color.parseColor("#FFB300") : Color.parseColor("#FFF066")); wormRayPaint.setAlpha((int) ((themeState == 0 ? 55 : 72) * fade));
                int rayCount = (birdType == 1) ? 9 : 8;
                for (int i = 0; i < rayCount; i++) { canvas.rotate(360f / rayCount); wormStarPath.reset(); wormStarPath.moveTo(-birdRadius * 0.14f, 0f); wormStarPath.lineTo(0f, -shieldR * 1.24f); wormStarPath.lineTo(birdRadius * 0.14f, 0f); wormStarPath.close(); canvas.drawPath(wormStarPath, wormRayPaint); }
                canvas.restore();

                if (birdType == 0) {
                    shieldFillPaint.setColor(Color.parseColor("#FFE438")); shieldFillPaint.setAlpha((int) ((themeState == 0 ? 48 : 56) * fade)); canvas.drawCircle(birdX, birdY, shieldR, shieldFillPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#FFF4A3")); shieldStrokePaint.setStrokeWidth(2.6f); shieldStrokePaint.setAlpha((int) (140 * fade));
                    shieldOval.set(birdX - shieldR * 0.80f, birdY - shieldR * 0.80f, birdX + shieldR * 0.80f, birdY + shieldR * 0.80f);
                    for (int i = 0; i < 6; i++) canvas.drawArc(shieldOval, -sparkleAngle * 0.7f + i * 60f, 36f, false, shieldStrokePaint);
                } else {
                    buildRegularPolygon(shieldPath, birdX, birdY, shieldR, 9, sparkleAngle * 0.42f); shieldFillPaint.setColor(Color.parseColor("#FF9E00")); shieldFillPaint.setAlpha((int) ((themeState == 0 ? 42 : 52) * fade)); canvas.drawPath(shieldPath, shieldFillPaint);
                    buildRegularPolygon(shieldInnerPath, birdX, birdY, shieldR * 0.78f, 9, -sparkleAngle * 0.62f); shieldStrokePaint.setColor(Color.parseColor("#FFD54F")); shieldStrokePaint.setStrokeWidth(2.6f); shieldStrokePaint.setAlpha((int) (150 * fade)); canvas.drawPath(shieldInnerPath, shieldStrokePaint);
                }
            }
        }

        private void drawMajesticShieldFront(Canvas canvas) {
            float fade = getShieldFadeMultiplier(), pulse = 1.0f + 0.06f * (float) Math.sin(bgAmbientTick * 0.22f), t = bgAmbientTick * 0.14f;

            if (themeState == 2) {
                if (birdType == 0) {
                    float domeR = birdRadius * 2.18f * pulse;
                    shieldBloomPaint.setColor(Color.parseColor("#00E5FF")); shieldBloomPaint.setStrokeWidth(14f); shieldBloomPaint.setAlpha((int) (72 * fade)); canvas.drawCircle(birdX, birdY, domeR, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#5CFFFF")); shieldStrokePaint.setStrokeWidth(4.2f); shieldStrokePaint.setAlpha((int) (215 * fade)); canvas.drawCircle(birdX, birdY, domeR, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.WHITE); shieldCorePaint.setStrokeWidth(1.6f); shieldCorePaint.setAlpha((int) (195 * fade)); canvas.drawCircle(birdX, birdY, domeR, shieldCorePaint);
                    shieldOval.set(birdX - domeR * 0.86f, birdY - domeR * 0.86f, birdX + domeR * 0.86f, birdY + domeR * 0.86f);
                    shieldCorePaint.setColor(Color.WHITE); shieldCorePaint.setStrokeWidth(5.5f); shieldCorePaint.setAlpha((int) (165 * fade)); canvas.drawArc(shieldOval, 205f, 52f, false, shieldCorePaint);

                    float rx = birdRadius * 3.05f * pulse, ry = birdRadius * 0.78f * pulse; saucerRect.set(birdX - rx, birdY - ry, birdX + rx, birdY + ry);
                    shieldBloomPaint.setColor(Color.parseColor("#00FF9D")); shieldBloomPaint.setStrokeWidth(18f); shieldBloomPaint.setAlpha((int) (90 * fade)); canvas.drawArc(saucerRect, 0f, 180f, false, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#39FF9C")); shieldStrokePaint.setStrokeWidth(7.5f); shieldStrokePaint.setAlpha((int) (235 * fade)); canvas.drawArc(saucerRect, 0f, 180f, false, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.parseColor("#E6FFF8")); shieldCorePaint.setStrokeWidth(2.6f); shieldCorePaint.setAlpha((int) (245 * fade)); canvas.drawArc(saucerRect, 6f, 168f, false, shieldCorePaint);

                    for (int i = 0; i < 5; i++) {
                        double a = Math.toRadians((sparkleAngle * 1.15f + i * 72f) % 360f); float lx = birdX + (float) Math.cos(a) * rx, ly = birdY + (float) Math.sin(a) * ry;
                        if (Math.sin(a) >= -0.15) { shieldNodePaint.setColor(Color.parseColor("#00FFFF")); shieldNodePaint.setAlpha((int) (110 * fade)); canvas.drawCircle(lx, ly, 9.5f, shieldNodePaint); drawColoredDiamondStar(canvas, lx, ly, birdRadius * 0.30f, Color.WHITE, (int) (240 * fade)); }
                    }
                    for (int i = 0; i < 3; i++) { double a = Math.toRadians(sparkleAngle * 0.9f + i * 120f); drawColoredDiamondStar(canvas, birdX + (float) Math.cos(a) * domeR, birdY + (float) Math.sin(a) * domeR, birdRadius * (0.28f + 0.10f * (float) Math.sin(t + i)), Color.parseColor("#C2FFFF"), (int) (230 * fade)); }
                } else {
                    float polyR = birdRadius * 2.55f * pulse, rotOuter = sparkleAngle * 0.45f; buildRegularPolygon(shieldPath, birdX, birdY, polyR, 9, rotOuter);
                    shieldBloomPaint.setColor(Color.parseColor("#00FFE0")); shieldBloomPaint.setStrokeWidth(18f); shieldBloomPaint.setAlpha((int) (78 * fade)); canvas.drawPath(shieldPath, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#00FF95")); shieldStrokePaint.setStrokeWidth(6.2f); shieldStrokePaint.setAlpha((int) (235 * fade)); canvas.drawPath(shieldPath, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.parseColor("#EAFFFF")); shieldCorePaint.setStrokeWidth(2.2f); shieldCorePaint.setAlpha((int) (245 * fade)); canvas.drawPath(shieldPath, shieldCorePaint);

                    for (int i = 0; i < 9; i++) {
                        double a = Math.toRadians(rotOuter + i * 40.0); float vx = birdX + (float) Math.cos(a) * polyR, vy = birdY + (float) Math.sin(a) * polyR, tw = 0.55f + 0.45f * (float) Math.sin(t * 1.7f + i * 1.3f);
                        shieldNodePaint.setColor(Color.parseColor("#00FFD5")); shieldNodePaint.setAlpha((int) ((95 + 70 * tw) * fade)); canvas.drawCircle(vx, vy, 7.5f + 3f * tw, shieldNodePaint);
                        if (i % 3 == 0) drawColoredDiamondStar(canvas, vx, vy, birdRadius * (0.30f + 0.14f * tw), Color.WHITE, (int) (245 * fade));
                        else { shieldNodePaint.setColor(Color.WHITE); shieldNodePaint.setAlpha((int) (235 * fade)); canvas.drawCircle(vx, vy, 3.2f, shieldNodePaint); }
                    }
                    int eStart = (int) (bgAmbientTick * 0.4f) % 9; double a1 = Math.toRadians(rotOuter + eStart * 40.0), a2 = Math.toRadians(rotOuter + ((eStart + 2) % 9) * 40.0);
                    float x1 = birdX + (float) Math.cos(a1) * polyR, y1 = birdY + (float) Math.sin(a1) * polyR, x2 = birdX + (float) Math.cos(a2) * polyR, y2 = birdY + (float) Math.sin(a2) * polyR;
                    electricPath.reset(); electricPath.moveTo(x1, y1); electricPath.lineTo((x1 + x2) * 0.5f + (float) Math.sin(bgAmbientTick * 1.9f) * birdRadius * 0.35f, (y1 + y2) * 0.5f + (float) Math.cos(bgAmbientTick * 2.3f) * birdRadius * 0.35f); electricPath.lineTo(x2, y2);
                    shieldStrokePaint.setColor(Color.parseColor("#00FFFF")); shieldStrokePaint.setStrokeWidth(5f); shieldStrokePaint.setAlpha((int) (130 * fade)); canvas.drawPath(electricPath, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.WHITE); shieldCorePaint.setStrokeWidth(2f); shieldCorePaint.setAlpha((int) (220 * fade)); canvas.drawPath(electricPath, shieldCorePaint);
                }
            } else {
                float shieldR = birdRadius * (birdType == 1 ? 2.30f : 2.10f) * pulse;
                if (birdType == 0) {
                    shieldBloomPaint.setColor(Color.parseColor("#FFB300")); shieldBloomPaint.setStrokeWidth(16f); shieldBloomPaint.setAlpha((int) (85 * fade)); canvas.drawCircle(birdX, birdY, shieldR, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#FFE600")); shieldStrokePaint.setStrokeWidth(5.5f); shieldStrokePaint.setAlpha((int) (235 * fade)); canvas.drawCircle(birdX, birdY, shieldR, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.parseColor("#FFFDF0")); shieldCorePaint.setStrokeWidth(2.2f); shieldCorePaint.setAlpha((int) (240 * fade)); canvas.drawCircle(birdX, birdY, shieldR, shieldCorePaint);
                    shieldOval.set(birdX - shieldR * 0.85f, birdY - shieldR * 0.85f, birdX + shieldR * 0.85f, birdY + shieldR * 0.85f);
                    shieldCorePaint.setColor(Color.WHITE); shieldCorePaint.setStrokeWidth(5.2f); shieldCorePaint.setAlpha((int) (185 * fade)); canvas.drawArc(shieldOval, 208f, 54f, false, shieldCorePaint);

                    shieldOval.set(birdX - shieldR * 1.18f, birdY - shieldR * 0.34f, birdX + shieldR * 1.18f, birdY + shieldR * 0.34f);
                    canvas.save(); canvas.rotate(-16f, birdX, birdY);
                    shieldBloomPaint.setColor(Color.parseColor("#FFD000")); shieldBloomPaint.setStrokeWidth(9f); shieldBloomPaint.setAlpha((int) (85 * fade)); canvas.drawArc(shieldOval, 10f, 160f, false, shieldBloomPaint);
                    shieldCorePaint.setColor(Color.parseColor("#FFFCE0")); shieldCorePaint.setStrokeWidth(2.6f); shieldCorePaint.setAlpha((int) (220 * fade)); canvas.drawArc(shieldOval, 18f, 144f, false, shieldCorePaint);
                    canvas.restore();
                    for (int i = 0; i < 6; i++) { double a = Math.toRadians(sparkleAngle * 0.85f + i * 60.0); float tw = 0.55f + 0.45f * (float) Math.sin(t * 1.6f + i * 1.4f); drawColoredDiamondStar(canvas, birdX + (float) Math.cos(a) * shieldR, birdY + (float) Math.sin(a) * shieldR, birdRadius * (0.26f + 0.16f * tw), Color.parseColor("#FFFBE0"), (int) (245 * fade)); }
                } else {
                    float rotOuter = sparkleAngle * 0.42f; buildRegularPolygon(shieldPath, birdX, birdY, shieldR, 9, rotOuter);
                    shieldBloomPaint.setColor(Color.parseColor("#FF5722")); shieldBloomPaint.setStrokeWidth(16f); shieldBloomPaint.setAlpha((int) (82 * fade)); canvas.drawPath(shieldPath, shieldBloomPaint);
                    shieldStrokePaint.setColor(Color.parseColor("#FFD700")); shieldStrokePaint.setStrokeWidth(5.8f); shieldStrokePaint.setAlpha((int) (238 * fade)); canvas.drawPath(shieldPath, shieldStrokePaint);
                    shieldCorePaint.setColor(Color.parseColor("#FFFDE7")); shieldCorePaint.setStrokeWidth(2.2f); shieldCorePaint.setAlpha((int) (245 * fade)); canvas.drawPath(shieldPath, shieldCorePaint);
                    for (int i = 0; i < 9; i++) {
                        double a = Math.toRadians(rotOuter + i * 40.0); float vx = birdX + (float) Math.cos(a) * shieldR, vy = birdY + (float) Math.sin(a) * shieldR, tw = 0.55f + 0.45f * (float) Math.sin(t * 1.8f + i * 1.2f);
                        shieldNodePaint.setColor(Color.parseColor("#FFB300")); shieldNodePaint.setAlpha((int) ((110 + 75 * tw) * fade)); canvas.drawCircle(vx, vy, 7f + 2.5f * tw, shieldNodePaint);
                        if (i % 3 == 0) drawColoredDiamondStar(canvas, vx, vy, birdRadius * (0.28f + 0.15f * tw), Color.parseColor("#FFFDE0"), (int) (245 * fade));
                        else { shieldNodePaint.setColor(Color.WHITE); shieldNodePaint.setAlpha((int) (230 * fade)); canvas.drawCircle(vx, vy, 3f, shieldNodePaint); }
                    }
                }
            }
        }

        private void drawGoldenWorm(Canvas canvas, float cx, float cy, float r) {
            float waveT = bgAmbientTick * 0.16f, hoverY = cy + (float) Math.sin(waveT * 0.75f) * (r * 0.16f), pulse = 1.0f + 0.13f * (float) Math.sin(waveT * 1.15f);

            canvas.save();
            if (goldAuraBmp != null && !goldAuraBmp.isRecycled()) { float ar = r * 2.55f * pulse; wormDrawRect.set(cx - ar, hoverY - ar, cx + ar, hoverY + ar); canvas.drawBitmap(goldAuraBmp, null, wormDrawRect, bgBitmapPaint); }
            else { canvas.drawCircle(cx, hoverY, r * 2.0f * pulse, wormGlowPaint); }

            canvas.save(); canvas.translate(cx, hoverY); canvas.rotate(sparkleAngle * 0.35f);
            wormRayPaint.setColor(Color.parseColor("#FFEA54")); wormRayPaint.setAlpha(themeState == 0 ? 52 : 76);
            for (int i = 0; i < 6; i++) { canvas.rotate(60f); wormStarPath.reset(); wormStarPath.moveTo(-r * 0.10f, 0f); wormStarPath.lineTo(0f, -r * 2.25f * pulse); wormStarPath.lineTo(r * 0.10f, 0f); wormStarPath.close(); canvas.drawPath(wormStarPath, wormRayPaint); }
            canvas.restore();

            float[] segOffX = {-1.12f, -0.78f, -0.40f, 0.02f, 0.44f, 0.86f}, segBaseY = {0.34f, 0.42f, 0.22f, -0.02f, -0.18f, -0.22f}, segRad = {0.34f, 0.46f, 0.56f, 0.64f, 0.70f, 0.84f};
            for (int i = 0; i < 6; i++) {
                float sx = cx + segOffX[i] * r, sy = hoverY + segBaseY[i] * r + (float) Math.sin(waveT - i * 0.72f) * (r * 0.13f), half = segRad[i] * r * 1.18f;
                wormDrawRect.set(sx - half, sy - half, sx + half, sy + half);
                Bitmap seg = (i == 5) ? goldHeadBmp : goldSegBmp;
                if (seg != null && !seg.isRecycled()) canvas.drawBitmap(seg, null, wormDrawRect, bgBitmapPaint);
            }

            int gI = Math.min(5, (int) ((bgAmbientTick * 0.09f) % 6f));
            drawDiamondStar(canvas, cx + segOffX[gI] * r, hoverY + segBaseY[gI] * r - segRad[gI] * r * 0.32f, r * 0.42f, 235);

            for (int i = 0; i < 4; i++) {
                double ang = Math.toRadians(sparkleAngle * 0.75f + i * 90f); float orbitR = r * (1.58f + 0.18f * (float) Math.sin(waveT + i * 1.6f));
                float fx = cx + (float) Math.cos(ang) * orbitR, fy = hoverY + (float) Math.sin(ang) * orbitR * 0.76f, tw = 0.55f + 0.45f * (float) Math.sin(waveT * 1.8f + i * 1.9f);
                drawDiamondStar(canvas, fx, fy, r * (0.28f + 0.22f * tw), (int) (150 + 105 * tw));
            }
            canvas.restore();
        }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, screenW, screenH, skyPaint); canvas.drawRect(0, 0, screenW, screenH, celestialGlowPaint);

            if (themeState == 1 || themeState == 2) {
                for (Star s : stars) {
                    s.alpha += s.twinkleSpeed; if (s.alpha > 1f) { s.alpha = 1f; s.twinkleSpeed = -s.twinkleSpeed; } else if (s.alpha < 0.18f) { s.alpha = 0.18f; s.twinkleSpeed = -s.twinkleSpeed; }
                    int a = (int) (s.alpha * (themeState == 2 ? 255 : 195)); starPaint.setColor(s.color); starPaint.setAlpha(a); canvas.drawCircle(s.x, s.y, s.radius, starPaint);
                    if (s.hasGlint && a > 160) { starPaint.setAlpha(a / 2); float gl = s.radius * 3.0f; canvas.drawLine(s.x - gl, s.y, s.x + gl, s.y, starPaint); canvas.drawLine(s.x, s.y - gl, s.x, s.y + gl, starPaint); }
                }
                if (themeState == 2 && shootingStarActive) {
                    float frac = Math.max(0f, Math.min(1f, ssLife / ssMaxLife)), tailX = ssX - ssVx * 5.2f, tailY = ssY - ssVy * 5.2f;
                    int headAlpha = (int) (235 * Math.sin(frac * Math.PI)); shootingStarPaint.setStrokeWidth(2.6f);
                    shootingStarPaint.setShader(new LinearGradient(ssX, ssY, tailX, tailY, Color.argb(headAlpha, 235, 248, 255), Color.argb(0, 120, 190, 255), Shader.TileMode.CLAMP));
                    canvas.drawLine(ssX, ssY, tailX, tailY, shootingStarPaint); shootingStarPaint.setShader(null);
                }
            }

            if (celestialBmp != null && !celestialBmp.isRecycled()) {
                float cx = screenW * (themeState == 0 ? 0.82f : 0.78f), cy = screenH * (themeState == 0 ? 0.14f : 0.17f);
                canvas.drawBitmap(celestialBmp, cx - celestialBmp.getWidth() / 2f, cy - celestialBmp.getHeight() / 2f, bgBitmapPaint);
            }

            drawTiledBitmap(canvas, farCloudBmp, 0f, screenH * 0.62f, parallaxScroll, 0.05f);
            drawTiledBitmap(canvas, nearCloudBmp, 0f, screenH * 0.62f, parallaxScroll, 0.12f);

            if (birdType == 0) {
                drawTiledBitmap(canvas, farMountainBmp, screenH * 0.44f, screenH * 0.88f, parallaxScroll, 0.16f);
                drawTiledBitmap(canvas, midMountainBmp, screenH * 0.50f, screenH * 0.94f, parallaxScroll, 0.30f);
                drawTiledBitmap(canvas, foreForestBmp, screenH * 0.56f, screenH, parallaxScroll, 0.55f);
                if (themeState == 1 || themeState == 2) {
                    for (Firefly f : fireflies) {
                        float fx = (f.x + parallaxScroll * 0.45f) % screenW; if (fx < 0) fx += screenW;
                        float fy = f.y + (float) Math.sin(bgAmbientTick * f.speed + f.phase) * 14f, glow = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(bgAmbientTick * f.speed * 1.7f + f.phase));
                        fireflyPaint.setColor(f.color); fireflyPaint.setAlpha((int) (glow * (themeState == 2 ? 75 : 55))); canvas.drawCircle(fx, fy, f.radius * 3.0f, fireflyPaint);
                        fireflyPaint.setAlpha((int) (glow * (themeState == 2 ? 225 : 175))); canvas.drawCircle(fx, fy, f.radius, fireflyPaint);
                    }
                }
            } else {
                drawTiledBitmap(canvas, cityBackBmp, screenH * 0.48f, screenH, parallaxScroll, 0.16f);
                drawTiledBitmap(canvas, cityMidBmp, screenH * 0.42f, screenH, parallaxScroll, 0.30f);
                drawTiledBitmap(canvas, cityFrontBmp, screenH * 0.60f, screenH, parallaxScroll, 0.52f);
            }

            float capH = refH * 0.04f, capExt = refW * 0.015f;
            for (Pipe p : pipes) {
                tBodyRect.set(p.x, -20f, p.x + pipeWidth, p.topHeight - capH); tCapRect.set(p.x - capExt, p.topHeight - capH, p.x + pipeWidth + capExt, p.topHeight);
                bCapRect.set(p.x - capExt, p.topHeight + pipeGap, p.x + pipeWidth + capExt, p.topHeight + pipeGap + capH); bBodyRect.set(p.x, p.topHeight + pipeGap + capH, p.x + pipeWidth, screenH + 20f);
                drawDetailedPipe(canvas, tBodyRect, tCapRect, true, p);
                drawDetailedPipe(canvas, bBodyRect, bCapRect, false, p);
                if (p.hasWorm && !p.wormEaten) drawGoldenWorm(canvas, p.x + (pipeWidth / 2f), p.topHeight + (pipeGap / 2f), wormRadius);
            }

            if (birdType == 0) {
                for (int i = 0; i < trailCount; i++) {
                    TrailPoint t = trail[(trailHead - i + MAX_TRAIL) % MAX_TRAIL]; float ratio = 1f - ((float) i / trailCount);
                    trailPaint.setColor(isImmune ? (themeState == 2 ? Color.parseColor("#80FFFF") : Color.parseColor("#FFE84D")) : Color.WHITE);
                    trailPaint.setAlpha((int) (200 * ratio)); canvas.drawCircle(t.x, t.y, birdRadius * 0.35f * ratio, trailPaint);
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    DustParticle dp = dustParticles[i];
                    if (dp.active) { gooTrailPaint.setAlpha((int) (180 * (dp.life / dp.maxLife))); canvas.drawCircle(dp.x, dp.y, birdRadius * 0.02f, gooTrailPaint); }
                }
            }

            if (isImmune && !gooDying) drawMajesticShieldBack(canvas);

            float tiltAngle = 0f;
            if (gooDying) tiltAngle = gooDeathSpin;
            else if (birdType == 0) { if (birdVelocity < 0) tiltAngle = -25f; else { tiltAngle = (birdVelocity / terminalVelocity) * 90f; if (tiltAngle > 90f) tiltAngle = 90f; } }

            if (birdBitmaps != null && birdBitmaps.length > currentFrameIndex && birdBitmaps[currentFrameIndex] != null) {
                Bitmap currentFrame = birdBitmaps[currentFrameIndex]; birdMatrix.reset(); birdMatrix.postTranslate(-currentFrame.getWidth() / 2f, -currentFrame.getHeight() / 2f); birdMatrix.postRotate(tiltAngle); birdMatrix.postTranslate(birdX, birdY); canvas.drawBitmap(currentFrame, birdMatrix, null);
            } else { canvas.save(); canvas.rotate(tiltAngle, birdX, birdY); canvas.drawCircle(birdX, birdY, birdRadius, fallbackBirdPaint); canvas.restore(); }

            if (isImmune && !gooDying) drawMajesticShieldFront(canvas);

            if (isImmune && immunityEndTime > System.currentTimeMillis() && !gooDying) {
                long timeLeft = immunityEndTime - System.currentTimeMillis(); float progress = Math.max(0f, Math.min(1f, timeLeft / 4000f)), barW = screenW * 0.55f, barH = 22f, barX = (screenW - barW) / 2f, barY = screenH * 0.22f;
                Bitmap labelBmp = (birdType == 1) ? suppressBmp : immuneBmp;
                if (labelBmp != null) {
                    String timeStr = String.format(Locale.US, " %.1fs", timeLeft / 1000f); float totalW = labelBmp.getWidth() + timeText.measureText(timeStr), startX = (screenW - totalW) / 2f, bmpY = barY - labelBmp.getHeight() - 8f, boxTop = Math.min(barY - 48f, bmpY - 16f);
                    canvas.drawRoundRect(barX - 24f, boxTop, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg); canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg); canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);
                    canvas.drawBitmap(labelBmp, startX, bmpY, null); timeText.setTextAlign(Paint.Align.LEFT); canvas.drawText(timeStr, startX + labelBmp.getWidth(), barY - 12f, timeText); timeText.setTextAlign(Paint.Align.CENTER);
                } else {
                    canvas.drawRoundRect(barX - 24f, barY - 48f, barX + barW + 24f, barY + barH + 16f, 30f, 30f, hudBg); canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 11f, 11f, barBg); canvas.drawRoundRect(barX, barY, barX + (barW * progress), barY + barH, 11f, 11f, barFill);
                    canvas.drawText(String.format(Locale.US, "[ %s: %.1fs ]", (birdType == 1) ? "SUPPRESS" : "IMMUNITY", timeLeft / 1000f), screenW / 2f, barY - 14f, timeText);
                }
            }
        }

        private static class Pipe {
            float x, topHeight; boolean passed = false, hasWorm = false, wormEaten = false;
            int topBoltLife = 0, topBoltMax = 1, botBoltLife = 0, botBoltMax = 1, topBoltCooldown, botBoltCooldown;
            long topBoltSeed, botBoltSeed;
            Pipe(float x, float topHeight) {
                this.x = x; this.topHeight = topHeight;
                this.topBoltCooldown = 4 + (int)(Math.random() * 20);
                this.botBoltCooldown = 4 + (int)(Math.random() * 20);
            }
        }
        private static class TrailPoint { float x, y, vx, vy; TrailPoint(float x, float y) { this.x = x; this.y = y; } }
        private static class DustParticle { float x, y, vx, vy, life, maxLife; boolean active = false; }
        private static class Star { float x, y, radius, alpha, twinkleSpeed; int color; boolean hasGlint; Star(float x, float y, float r, float a, float ts, int c, boolean g) { this.x = x; this.y = y; this.radius = r; this.alpha = a; this.twinkleSpeed = ts; this.color = c; this.hasGlint = g; } }
        private static class Firefly { float x, y, phase, speed, radius; int color; Firefly(float x, float y, float ph, float sp, float r, int c) { this.x = x; this.y = y; this.phase = ph; this.speed = sp; this.radius = r; this.color = c; } }
    }
}