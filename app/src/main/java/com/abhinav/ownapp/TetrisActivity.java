package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@SuppressWarnings("all")
@SuppressLint("SetTextI18n")
public class TetrisActivity extends AppCompatActivity {

    private int highScore = 0, currentBestScore = 0;
    private SharedPreferences prefs;
    private TetrisEngine gameEngine;
    private TetrisSoundEngine soundEngine;
    private ValueAnimator hudWidthAnimator, hudTextAnimator;
    private TextView tvCurrentScore, tvHighScore, tvFinalScore, tvTapToStart, tvGameOverTitle, tvNewHighScoreBanner;
    private LinearLayout scoreContainer, btnPause, nextBlockPill;
    private RelativeLayout pauseOverlay, gameOverOverlay;
    private GameIconView pauseIconView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(R.layout.activity_tetris);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        int themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true) ? 1 : 0;

        highScore = prefs.getInt("tetris_high_score", 0);
        currentBestScore = highScore;
        boolean isVibrationEnabled = prefs.getBoolean("tetris_vibration_enabled", true);
        soundEngine = new TetrisSoundEngine(this);

        View rootLayout = findViewById(R.id.tetrisRoot);
        RelativeLayout topHUD = findViewById(R.id.topHUD);
        if (topHUD != null) {
            int padTop = (int) (8 * getResources().getDisplayMetrics().density);
            topHUD.setPadding(topHUD.getPaddingLeft(), padTop, topHUD.getPaddingRight(), topHUD.getPaddingBottom());
        }

        FrameLayout gameContainer = findViewById(R.id.gameContainer);
        FrameLayout nextShapeContainer = findViewById(R.id.nextShapeContainer);
        tvCurrentScore = findViewById(R.id.tvCurrentScore); tvHighScore = findViewById(R.id.tvHighScore);
        tvFinalScore = findViewById(R.id.tvFinalScore); tvTapToStart = findViewById(R.id.tvTapToStart);
        scoreContainer = findViewById(R.id.scoreContainer); tvGameOverTitle = findViewById(R.id.tvGameOverTitle);
        tvNewHighScoreBanner = findViewById(R.id.tvNewHighScoreBanner); TextView tvNextLabel = findViewById(R.id.tvNextLabel);
        pauseOverlay = findViewById(R.id.pauseOverlay); gameOverOverlay = findViewById(R.id.gameOverOverlay);
        LinearLayout pauseCard = findViewById(R.id.pauseCard); LinearLayout gameOverCard = findViewById(R.id.gameOverCard);
        btnPause = findViewById(R.id.btnPause); nextBlockPill = findViewById(R.id.nextBlockPill);
        FrameLayout pauseIconContainer = findViewById(R.id.pauseIconContainer);

        pauseIconView = new GameIconView(this);
        pauseIconContainer.addView(pauseIconView);

        Button btnResume = findViewById(R.id.btnResume), btnRestart = findViewById(R.id.btnRestart);
        Button btnQuit = findViewById(R.id.btnQuit), btnQuitFromPause = findViewById(R.id.btnQuitFromPause);
        Button btnToggleVibration = findViewById(R.id.btnToggleVibration);

        int bgColor, cardColor, cardStroke, textColor, quitBtnColor, pillBgColor, boxBgColor, pauseTextColor, iconColor;
        if (themeState == 0) {
            bgColor = Color.WHITE; cardColor = Color.parseColor("#F6F7FA"); cardStroke = Color.parseColor("#E3E6EE");
            textColor = Color.parseColor("#333333"); quitBtnColor = Color.parseColor("#E5E5EA");
            pillBgColor = Color.parseColor("#F0F0F0"); boxBgColor = Color.WHITE; iconColor = Color.parseColor("#333333"); pauseTextColor = Color.parseColor("#333333");
        } else if (themeState == 1) {
            bgColor = Color.parseColor("#1C1C1E"); cardColor = Color.parseColor("#26262A"); cardStroke = Color.parseColor("#383840");
            textColor = Color.WHITE; quitBtnColor = Color.parseColor("#3A3A3C");
            pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A"); pauseTextColor = Color.parseColor("#E3E2E6");
        } else {
            bgColor = Color.parseColor("#000000"); cardColor = Color.parseColor("#141418"); cardStroke = Color.parseColor("#26262E");
            textColor = Color.WHITE; quitBtnColor = Color.parseColor("#2C2C2E");
            pillBgColor = Color.parseColor("#2D313A"); boxBgColor = Color.parseColor("#D8E2FF"); iconColor = Color.parseColor("#001C3A"); pauseTextColor = Color.parseColor("#E3E2E6");
        }

        rootLayout.setBackgroundColor(bgColor);
        tvCurrentScore.setTextColor(textColor); tvNextLabel.setTextColor(pauseTextColor); tvTapToStart.setTextColor(textColor);
        tvHighScore.setTextColor(themeState == 0 ? Color.parseColor("#888888") : Color.parseColor("#A0A0A5"));
        tvHighScore.setText("Best: " + currentBestScore);
        ((TextView) findViewById(R.id.tvPauseTitle)).setTextColor(textColor);
        tvGameOverTitle.setTextColor(textColor); tvFinalScore.setTextColor(textColor);

        btnQuit.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuit.setTextColor(textColor);
        if (btnQuitFromPause != null) { btnQuitFromPause.setBackgroundTintList(ColorStateList.valueOf(quitBtnColor)); btnQuitFromPause.setTextColor(textColor); }

        GradientDrawable gdCard = new GradientDrawable(); gdCard.setColor(cardColor); gdCard.setCornerRadius(60f); gdCard.setStroke(2, cardStroke);
        pauseCard.setBackground(gdCard); gameOverCard.setBackground(gdCard.getConstantState().newDrawable().mutate());

        if (btnPause != null) { btnPause.setBackground(createPillShape(pillBgColor)); pauseIconContainer.setBackground(createBoxShape(boxBgColor)); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); }
        if (nextBlockPill != null) nextBlockPill.setBackground(createPillShape(pillBgColor));

        if (btnToggleVibration != null) {
            btnToggleVibration.setText("Vibration: " + (isVibrationEnabled ? "ON" : "OFF"));
            btnToggleVibration.setOnClickListener(v -> {
                soundEngine.playUi();
                boolean current = prefs.getBoolean("tetris_vibration_enabled", true);
                prefs.edit().putBoolean("tetris_vibration_enabled", !current).apply();
                btnToggleVibration.setText("Vibration: " + (!current ? "ON" : "OFF"));
                if (gameEngine != null) gameEngine.setVibrationEnabled(!current);
            });
        }

        NextShapeView nextShapeView = new NextShapeView(this, themeState);
        nextShapeContainer.addView(nextShapeView);

        gameEngine = new TetrisEngine(this, themeState, nextShapeView, soundEngine);
        gameEngine.setVibrationEnabled(isVibrationEnabled);
        gameContainer.addView(gameEngine);

        if (topHUD != null) {
            topHUD.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() { if (gameEngine != null) gameEngine.setTopHUDHeight(topHUD.getHeight()); }
            });
        }

        gameEngine.setGameListener(new TetrisEngine.GameListener() {
            @Override public void onScoreUpdated(int score) { if (tvCurrentScore != null) tvCurrentScore.setText("Score: " + score); }

            @Override
            public void onGameOver(int finalScore) {
                tvGameOverTitle.setText("GAME OVER"); tvGameOverTitle.setTextColor(textColor);
                if (finalScore > highScore && finalScore > 0) {
                    highScore = finalScore; prefs.edit().putInt("tetris_high_score", highScore).apply();
                    tvNewHighScoreBanner.setVisibility(View.VISIBLE);
                } else tvNewHighScoreBanner.setVisibility(View.GONE);
                currentBestScore = highScore;
                if (tvHighScore != null) tvHighScore.setText("Best: " + currentBestScore);
                tvFinalScore.setText("Score: " + finalScore);
                showOverlaySmoothly(gameOverOverlay);
                fadeOutHudSmoothly(btnPause, nextBlockPill);
            }

            @Override
            public void onGameStarted() {
                if (hudWidthAnimator != null) hudWidthAnimator.cancel();
                if (hudTextAnimator != null) hudTextAnimator.cancel();
                if (tvTapToStart != null && tvTapToStart.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE);
                    tvCurrentScore.setText("Score: 0"); tvHighScore.setText("Best: " + currentBestScore);
                    ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams(); tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(tempLp);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    tempLp.width = startWidth; btnPause.setLayoutParams(tempLp);
                    tvTapToStart.setVisibility(View.VISIBLE); scoreContainer.setVisibility(View.GONE); scoreContainer.setAlpha(1f);

                    final String tapStr = "Swipe to Move", curScoreStr = "Score: 0", bestScoreStr = "Best: " + currentBestScore;
                    hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    hudWidthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                    hudWidthAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); } });
                    hudWidthAnimator.setDuration(400); hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                    hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                    hudTextAnimator.addUpdateListener(anim -> {
                        float f = anim.getAnimatedFraction();
                        if (f < 0.4f) { int l = (int) (tapStr.length() * (1f - f / 0.4f)); tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min(l, tapStr.length())))); }
                        else {
                            if (tvTapToStart.getVisibility() == View.VISIBLE) { tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); }
                            float tf = (f - 0.4f) / 0.6f;
                            tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min((int) (curScoreStr.length() * tf), curScoreStr.length()))));
                            tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min((int) (bestScoreStr.length() * tf), bestScoreStr.length()))));
                        }
                    });
                    hudTextAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { tvCurrentScore.setText(curScoreStr); tvHighScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); tvTapToStart.setVisibility(View.GONE); scoreContainer.setVisibility(View.VISIBLE); } });
                    hudTextAnimator.setDuration(400);
                    hudWidthAnimator.start(); hudTextAnimator.start();
                }
            }
        });

        if (btnPause != null) btnPause.setOnClickListener(v -> { soundEngine.playUi(); gameEngine.pauseGame(); showOverlaySmoothly(pauseOverlay); fadeOutHudSmoothly(btnPause, nextBlockPill); pauseIconView.setIcon(GameIconView.ICON_PLAY, iconColor); });
        if (btnResume != null) btnResume.setOnClickListener(v -> { soundEngine.playUi(); hideOverlaySmoothly(pauseOverlay); fadeInHudSmoothly(btnPause, nextBlockPill); gameEngine.resumeGame(); pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor); });

        if (btnRestart != null) {
            btnRestart.setOnClickListener(v -> {
                soundEngine.playUi(); hideOverlaySmoothly(gameOverOverlay); tvNewHighScoreBanner.setVisibility(View.GONE);
                if (hudWidthAnimator != null) hudWidthAnimator.cancel();
                if (hudTextAnimator != null) hudTextAnimator.cancel();
                if (scoreContainer != null && scoreContainer.getVisibility() == View.VISIBLE) {
                    final int startWidth = btnPause.getWidth();
                    scoreContainer.setVisibility(View.GONE); tvTapToStart.setText("Swipe to Move"); tvTapToStart.setVisibility(View.VISIBLE);
                    ViewGroup.LayoutParams tempLp = btnPause.getLayoutParams(); tempLp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(tempLp);
                    btnPause.measure(View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getWidth(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(((View) btnPause.getParent()).getHeight(), View.MeasureSpec.AT_MOST));
                    final int targetWidth = btnPause.getMeasuredWidth();
                    tempLp.width = startWidth; btnPause.setLayoutParams(tempLp);
                    scoreContainer.setVisibility(View.VISIBLE); tvTapToStart.setVisibility(View.GONE);

                    final String tapStr = "Swipe to Move", rawCur = tvCurrentScore.getText().toString();
                    final String curScoreStr = rawCur.isEmpty() ? "Score: 0" : rawCur, bestScoreStr = "Best: " + currentBestScore;
                    hudWidthAnimator = ValueAnimator.ofInt(startWidth, targetWidth);
                    hudWidthAnimator.addUpdateListener(a -> { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = (Integer) a.getAnimatedValue(); btnPause.setLayoutParams(lp); });
                    hudWidthAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { ViewGroup.LayoutParams lp = btnPause.getLayoutParams(); lp.width = ViewGroup.LayoutParams.WRAP_CONTENT; btnPause.setLayoutParams(lp); } });
                    hudWidthAnimator.setDuration(400); hudWidthAnimator.setInterpolator(new DecelerateInterpolator());

                    hudTextAnimator = ValueAnimator.ofFloat(0f, 1f);
                    hudTextAnimator.addUpdateListener(anim -> {
                        float f = anim.getAnimatedFraction();
                        if (f < 0.4f) {
                            float ef = 1f - f / 0.4f;
                            tvCurrentScore.setText(curScoreStr.substring(0, Math.max(0, Math.min((int) (curScoreStr.length() * ef), curScoreStr.length()))));
                            tvHighScore.setText(bestScoreStr.substring(0, Math.max(0, Math.min((int) (bestScoreStr.length() * ef), bestScoreStr.length()))));
                        } else {
                            if (scoreContainer.getVisibility() == View.VISIBLE) { scoreContainer.setVisibility(View.GONE); tvTapToStart.setVisibility(View.VISIBLE); }
                            float tf = (f - 0.4f) / 0.6f;
                            tvTapToStart.setText(tapStr.substring(0, Math.max(0, Math.min((int) (tapStr.length() * tf), tapStr.length()))));
                        }
                    });
                    hudTextAnimator.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator a) { tvCurrentScore.setText("Score: 0"); tvHighScore.setText(bestScoreStr); tvTapToStart.setText(tapStr); scoreContainer.setVisibility(View.GONE); tvTapToStart.setVisibility(View.VISIBLE); } });
                    hudTextAnimator.setDuration(400);
                    hudWidthAnimator.start(); hudTextAnimator.start();
                } else {
                    if (scoreContainer != null) scoreContainer.setVisibility(View.GONE);
                    if (tvTapToStart != null) { tvTapToStart.setText("Swipe to Move"); tvTapToStart.setVisibility(View.VISIBLE); }
                    if (tvCurrentScore != null) tvCurrentScore.setText("Score: 0");
                }
                fadeInHudSmoothly(btnPause, nextBlockPill);
                pauseIconView.setIcon(GameIconView.ICON_PAUSE, iconColor);
                gameEngine.resetGame();
            });
        }

        if (btnQuit != null) btnQuit.setOnClickListener(v -> finish());
        if (btnQuitFromPause != null) btnQuitFromPause.setOnClickListener(v -> finish());
    }

    @Override
    public void onBackPressed() {
        if ((gameOverOverlay != null && gameOverOverlay.getVisibility() == View.VISIBLE) || (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE)) { finish(); return; }
        if (gameEngine != null && gameEngine.isPlaying() && !gameEngine.isGameOver() && btnPause != null) { btnPause.performClick(); return; }
        super.onBackPressed();
    }

    private void showOverlaySmoothly(View o) { if (o == null) return; o.setAlpha(0f); o.setVisibility(View.VISIBLE); o.animate().alpha(1f).setDuration(250).start(); }
    private void hideOverlaySmoothly(View o) { if (o == null) return; o.animate().alpha(0f).setDuration(250).withEndAction(() -> o.setVisibility(View.GONE)).start(); }
    private void fadeOutHudSmoothly(View l, View r) {
        if (l != null) l.animate().alpha(0f).setDuration(250).withEndAction(() -> l.setVisibility(View.INVISIBLE)).start();
        if (r != null) r.animate().alpha(0f).setDuration(250).withEndAction(() -> r.setVisibility(View.INVISIBLE)).start();
    }
    private void fadeInHudSmoothly(View l, View r) {
        if (l != null) { l.setAlpha(0f); l.setVisibility(View.VISIBLE); l.animate().alpha(1f).setDuration(250).start(); }
        if (r != null) { r.setAlpha(0f); r.setVisibility(View.VISIBLE); r.animate().alpha(1f).setDuration(250).start(); }
    }
    @Override public void finish() { super.finish(); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); }
    private GradientDrawable createPillShape(int c) { GradientDrawable s = new GradientDrawable(); s.setColor(c); s.setCornerRadius(1000f); return s; }
    private GradientDrawable createBoxShape(int c) { GradientDrawable s = new GradientDrawable(); s.setColor(c); s.setCornerRadius(30f); return s; }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameEngine != null) gameEngine.startRenderLoop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        boolean isPauseVisible = (pauseOverlay != null && pauseOverlay.getVisibility() == View.VISIBLE);
        if (gameEngine != null) {
            if (gameEngine.isPlaying() && !gameEngine.isGameOver() && !isPauseVisible && btnPause != null) btnPause.performClick();
            gameEngine.stopRenderLoop();
        }
    }

    @Override
    protected void onDestroy() {
        if (hudWidthAnimator != null) hudWidthAnimator.cancel();
        if (hudTextAnimator != null) hudTextAnimator.cancel();
        if (gameEngine != null) gameEngine.stopRenderLoop();
        if (soundEngine != null) soundEngine.release();
        super.onDestroy();
    }

    public static class GameIconView extends View {
        public static final int ICON_PAUSE = 0, ICON_PLAY = 1;
        private int iconType = ICON_PAUSE;
        private Paint paint;
        public GameIconView(Context c) { super(c); paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4.5f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        public void setIcon(int t, int c) { this.iconType = t; paint.setColor(c); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            if (iconType == ICON_PAUSE) { canvas.drawRoundRect(cx - 7f, cy - 8f, cx - 2f, cy + 8f, 3f, 3f, paint); canvas.drawRoundRect(cx + 2f, cy - 8f, cx + 7f, cy + 8f, 3f, 3f, paint); }
            else { Path p = new Path(); p.moveTo(cx - 4f, cy - 9f); p.lineTo(cx + 7f, cy); p.lineTo(cx - 4f, cy + 9f); p.close(); canvas.drawPath(p, paint); }
        }
    }

    private static final int[][][] TETROMINOES = {
            {{1, 1, 1, 1}}, {{1, 1}, {1, 1}}, {{0, 1, 0}, {1, 1, 1}},
            {{1, 0, 0}, {1, 1, 1}}, {{0, 0, 1}, {1, 1, 1}}, {{0, 1, 1}, {1, 1, 0}}, {{1, 1, 0}, {0, 1, 1}}
    };

    private static final int[] COLORS_LIGHT = {
            Color.TRANSPARENT, Color.parseColor("#0EA5C6"), Color.parseColor("#EAB308"), Color.parseColor("#8B5CF6"),
            Color.parseColor("#F97316"), Color.parseColor("#3B82F6"), Color.parseColor("#10B981"), Color.parseColor("#EF4444")
    };
    private static final int[] COLORS_DARK = {
            Color.TRANSPARENT, Color.parseColor("#22D3EE"), Color.parseColor("#FACC15"), Color.parseColor("#A78BFA"),
            Color.parseColor("#FB923C"), Color.parseColor("#60A5FA"), Color.parseColor("#34D399"), Color.parseColor("#F87171")
    };
    private static int[] getPalette(int themeState) { return themeState == 0 ? COLORS_LIGHT : COLORS_DARK; }

    private static void drawMinimalTile(Canvas canvas, RectF r, int baseColor, boolean isLight, Paint fillP, Paint hlP, Paint shP, float flashAlpha) {
        float cr = Math.max(3f, r.width() * 0.21f);
        fillP.setColor(baseColor); fillP.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(r, cr, cr, fillP);

        hlP.setStyle(Paint.Style.FILL);
        hlP.setColor(Color.argb(isLight ? 58 : 48, 255, 255, 255));
        float ins = Math.max(1.5f, r.width() * 0.11f), topH = Math.max(2f, r.height() * 0.22f);
        canvas.drawRoundRect(r.left + ins, r.top + ins * 0.7f, r.right - ins, r.top + ins * 0.7f + topH, cr * 0.6f, cr * 0.6f, hlP);

        shP.setStyle(Paint.Style.STROKE); shP.setStrokeWidth(Math.max(1f, r.width() * 0.055f));
        shP.setColor(Color.argb(isLight ? 38 : 65, 0, 0, 0));
        canvas.drawRoundRect(r.left + shP.getStrokeWidth() * 0.5f, r.top + shP.getStrokeWidth() * 0.5f, r.right - shP.getStrokeWidth() * 0.5f, r.bottom - shP.getStrokeWidth() * 0.5f, cr, cr, shP);

        if (flashAlpha > 0.01f) {
            hlP.setColor(Color.argb((int) (Math.min(1f, flashAlpha) * 210), 255, 255, 255));
            canvas.drawRoundRect(r, cr, cr, hlP);
        }
    }

    private static class NextShapeView extends View {
        private int[][] shape; private int color; private final boolean isLight; private final int[] palette;
        private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG), hlP = new Paint(Paint.ANTI_ALIAS_FLAG), shP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF();
        public NextShapeView(Context c, int themeState) { super(c); this.isLight = themeState == 0; this.palette = getPalette(themeState); }
        public void updateShape(int typeId) { this.shape = TETROMINOES[typeId]; this.color = palette[typeId + 1]; invalidate(); }
        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); if (shape == null) return;
            int rows = shape.length, cols = shape[0].length;
            float cell = (float) Math.floor(Math.min(getWidth() / 4.2f, getHeight() / 4.2f));
            if (cell <= 1f) return;
            float sx = (getWidth() - cols * cell) / 2f, sy = (getHeight() - rows * cell) / 2f, pad = Math.max(1f, cell * 0.08f);
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (shape[r][c] == 1) {
                tempRect.set(sx + c * cell + pad, sy + r * cell + pad, sx + (c + 1) * cell - pad, sy + (r + 1) * cell - pad);
                drawMinimalTile(canvas, tempRect, color, isLight, fillP, hlP, shP, 0f);
            }
        }
    }

    private static class TetrisSoundEngine {
        private static final int SR = 44100;
        private SoundPool pool; private volatile boolean released = false;
        private int sMove, sRotate, sSoft, sHard, sLock, sClear1, sClear2, sClear3, sClear4, sOver, sUi;

        public TetrisSoundEngine(Context ctx) {
            AudioAttributes attr = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attr).build();
            Context appCtx = ctx.getApplicationContext();
            new Thread(() -> {
                try {
                    File dir = appCtx.getCacheDir();
                    sMove = load(dir, "m", synthClick(0.026, 600, 320, 0.32));
                    sRotate = load(dir, "r", synthGlide(0.048, 460, 760, 0.36));
                    sSoft = load(dir, "s", synthClick(0.018, 290, 190, 0.20));
                    sHard = load(dir, "h", synthThud(0.105, 150, 44, 0.72));
                    sLock = load(dir, "l", synthClick(0.050, 320, 145, 0.44));
                    sUi = load(dir, "u", synthClick(0.028, 720, 520, 0.30));
                    sClear1 = load(dir, "c1", synthChord(0.30, new double[]{523.25, 783.99}, 0.045, 0.52));
                    sClear2 = load(dir, "c2", synthChord(0.38, new double[]{523.25, 659.25, 783.99}, 0.045, 0.58));
                    sClear3 = load(dir, "c3", synthChord(0.46, new double[]{523.25, 659.25, 783.99, 987.77}, 0.045, 0.64));
                    sClear4 = load(dir, "c4", synthChord(0.64, new double[]{523.25, 659.25, 783.99, 987.77, 1046.5, 1318.5}, 0.048, 0.74));
                    sOver = load(dir, "go", synthChord(0.58, new double[]{392.0, 329.63, 261.63}, 0.13, 0.46));
                } catch (Exception ignored) {}
            }).start();
        }

        private int load(File dir, String key, short[] pcm) throws Exception {
            if (released || pool == null) return 0;
            File f = new File(dir, "own_tetris_sfx2_" + key + ".wav");
            if (!f.exists() || f.length() < 48) writeWav(f, pcm);
            return pool.load(f.getAbsolutePath(), 1);
        }

        private void writeWav(File f, short[] data) throws Exception {
            int dataLen = data.length * 2, total = dataLen + 36;
            byte[] b = new byte[44 + dataLen];
            b[0]='R'; b[1]='I'; b[2]='F'; b[3]='F'; putInt(b, 4, total);
            b[8]='W'; b[9]='A'; b[10]='V'; b[11]='E'; b[12]='f'; b[13]='m'; b[14]='t'; b[15]=' ';
            putInt(b, 16, 16); b[20]=1; b[22]=1; putInt(b, 24, SR); putInt(b, 28, SR * 2); b[32]=2; b[34]=16;
            b[36]='d'; b[37]='a'; b[38]='t'; b[39]='a'; putInt(b, 40, dataLen);
            for (int i = 0; i < data.length; i++) { b[44 + i * 2] = (byte) (data[i] & 0xFF); b[45 + i * 2] = (byte) ((data[i] >> 8) & 0xFF); }
            try (FileOutputStream fos = new FileOutputStream(f)) { fos.write(b); }
        }
        private void putInt(byte[] b, int o, int v) { b[o]=(byte)v; b[o+1]=(byte)(v>>8); b[o+2]=(byte)(v>>16); b[o+3]=(byte)(v>>24); }

        private short[] synthClick(double dur, double f0, double f1, double amp) {
            int n = (int) (SR * dur); short[] out = new short[n]; double ph = 0;
            for (int i = 0; i < n; i++) {
                double t = (double) i / n, freq = f0 + (f1 - f0) * t, env = Math.pow(1.0 - t, 2.4) * Math.min(1.0, i / 45.0);
                ph += 2.0 * Math.PI * freq / SR;
                double s = Math.sin(ph) * 0.8 + Math.sin(ph * 2.0) * 0.2;
                out[i] = (short) (Math.max(-1, Math.min(1, s * env * amp)) * 32000);
            }
            return out;
        }
        private short[] synthGlide(double dur, double f0, double f1, double amp) {
            int n = (int) (SR * dur); short[] out = new short[n]; double ph = 0;
            for (int i = 0; i < n; i++) {
                double t = (double) i / n, freq = f0 + (f1 - f0) * (1.0 - Math.pow(1.0 - t, 2.0)), env = Math.pow(1.0 - t, 1.8) * Math.min(1.0, i / 55.0);
                ph += 2.0 * Math.PI * freq / SR;
                double s = Math.sin(ph) * 0.75 + Math.sin(ph * 2.0) * 0.25;
                out[i] = (short) (Math.max(-1, Math.min(1, s * env * amp)) * 32000);
            }
            return out;
        }
        private short[] synthThud(double dur, double f0, double f1, double amp) {
            int n = (int) (SR * dur); short[] out = new short[n]; double ph = 0, cph = 0;
            for (int i = 0; i < n; i++) {
                double t = (double) i / n, freq = f1 + (f0 - f1) * Math.exp(-t * 9.0), env = Math.exp(-t * 6.5) * Math.min(1.0, i / 25.0);
                ph += 2.0 * Math.PI * freq / SR; cph += 2.0 * Math.PI * 860.0 / SR;
                double click = (i < 420) ? Math.sin(cph) * Math.pow(1.0 - i / 420.0, 2.5) * 0.35 : 0.0;
                double s = Math.sin(ph) * env + click;
                out[i] = (short) (Math.max(-1, Math.min(1, s * amp)) * 32000);
            }
            return out;
        }
        private short[] synthChord(double dur, double[] freqs, double stagger, double amp) {
            int n = (int) (SR * dur); short[] out = new short[n];
            for (int i = 0; i < n; i++) {
                double time = (double) i / SR, sum = 0;
                for (int k = 0; k < freqs.length; k++) {
                    double nt = time - k * stagger;
                    if (nt >= 0) {
                        double env = Math.exp(-nt * 5.8) * Math.min(1.0, nt / 0.005);
                        double w = 2.0 * Math.PI * freqs[k] * nt;
                        sum += (Math.sin(w) * 0.72 + Math.sin(w * 2.0) * 0.20 + Math.sin(w * 3.0) * 0.08) * env;
                    }
                }
                sum /= Math.max(1.5, freqs.length * 0.62);
                out[i] = (short) (Math.max(-1, Math.min(1, sum * amp)) * 32000);
            }
            return out;
        }

        private void play(int id, float vol, float rate) { if (!released && pool != null && id != 0) pool.play(id, vol, vol, 1, 0, rate); }
        public void playMove() { play(sMove, 0.55f, 1.0f); }
        public void playRotate() { play(sRotate, 0.65f, 1.0f); }
        public void playSoft() { play(sSoft, 0.45f, 1.0f); }
        public void playHard() { play(sHard, 0.85f, 1.0f); }
        public void playLock() { play(sLock, 0.65f, 1.0f); }
        public void playUi() { play(sUi, 0.55f, 1.0f); }
        public void playGameOver() { play(sOver, 0.75f, 1.0f); }
        public void playClear(int lines, int combo) {
            float rate = Math.min(1.32f, 1.0f + Math.max(0, combo - 1) * 0.06f);
            int id = lines >= 4 ? sClear4 : (lines == 3 ? sClear3 : (lines == 2 ? sClear2 : sClear1));
            play(id, 0.90f, rate);
        }
        public void release() { released = true; if (pool != null) { pool.release(); pool = null; } }
    }

    private static class Particle { float x, y, vx, vy, size, alpha, life, maxLife, rot, vrot; int color; }
    private static class ScorePopup { String text; float x, y, alpha, scale; int color; }

    private static class TetrisEngine extends View {
        private static final int ROWS = 20, COLS = 10;
        private static final float LOCK_DELAY_MS = 380f, CLEAR_DURATION = 0.21f;
        private final int[][] board = new int[ROWS][COLS];
        private final float[][] lockFlash = new float[ROWS][COLS];
        private final boolean[] clearingMask = new boolean[ROWS];

        private final int themeState;
        private final boolean isLight;
        private final int[] palette;
        private final NextShapeView nextShapeView;
        private final TetrisSoundEngine sound;
        private GameListener listener;

        private int[][] currentPiece;
        private int currentType, currentX, currentY, nextType;
        private final List<Integer> bag = new ArrayList<>();
        private final Random random = new Random();

        private float screenW, screenH, cellSize, boardTop, boardLeft, boardW, boardH;
        private float dynamicTopPadding = 220f, boardBumpY = 0f, bgTime = 0f;
        private float dropTrailAlpha = 0f, trailLeft, trailRight, trailTop, trailBottom;
        private int trailColor;

        private boolean playing = false, paused = false, gameOver = false, vibrationEnabled = true, clearingRows = false, loopActive = false;
        private float fallAccumMs = 0f, lockTimerMs = 0f, clearTimer = 0f;
        private int score = 0, linesTotal = 0, combo = 0, lockResets = 0;
        private long lastFrameMs = 0L;

        private int activePointerId = MotionEvent.INVALID_POINTER_ID;
        private float downX, downY, lastStepX, lastStepY;
        private boolean movedDuringTouch = false, hardDroppedThisTouch = false;
        private long touchStartTime = 0L;

        private final List<Particle> particles = new ArrayList<>();
        private final List<ScorePopup> popups = new ArrayList<>();
        private final float[] starX = new float[36], starY = new float[36], starR = new float[36], starPhase = new float[36];

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG), ambientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boardWellPaint = new Paint(Paint.ANTI_ALIAS_FLAG), borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG), fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hlPaint = new Paint(Paint.ANTI_ALIAS_FLAG), shPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ghostFill = new Paint(Paint.ANTI_ALIAS_FLAG), ghostStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fxPaint = new Paint(Paint.ANTI_ALIAS_FLAG), textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF tempRect = new RectF(), boardRect = new RectF();

        public interface GameListener { void onScoreUpdated(int score); void onGameOver(int finalScore); void onGameStarted(); }

        public TetrisEngine(Context context, int themeState, NextShapeView nextShapeView, TetrisSoundEngine sound) {
            super(context);
            this.themeState = themeState; this.isLight = themeState == 0; this.palette = getPalette(themeState);
            this.nextShapeView = nextShapeView; this.sound = sound;

            boardWellPaint.setStyle(Paint.Style.FILL);
            boardWellPaint.setColor(themeState == 0 ? Color.parseColor("#F5F7FA") : (themeState == 1 ? Color.parseColor("#141417") : Color.parseColor("#060609")));

            borderPaint.setStyle(Paint.Style.STROKE); borderPaint.setStrokeWidth(3f);
            borderPaint.setColor(themeState == 0 ? Color.parseColor("#DCE2EC") : (themeState == 1 ? Color.parseColor("#2E2E36") : Color.parseColor("#20202A")));

            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(themeState == 0 ? Color.argb(48, 90, 105, 130) : (themeState == 1 ? Color.argb(34, 255, 255, 255) : Color.argb(28, 255, 255, 255)));

            ghostFill.setStyle(Paint.Style.FILL);
            ghostStroke.setStyle(Paint.Style.STROKE); ghostStroke.setStrokeWidth(2.6f);

            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

            Random sr = new Random(42);
            for (int i = 0; i < starX.length; i++) {
                starX[i] = sr.nextFloat(); starY[i] = sr.nextFloat();
                starR[i] = 1.2f + sr.nextFloat() * 2.0f; starPhase[i] = sr.nextFloat() * 6.28f;
            }
        }

        public void setGameListener(GameListener l) { this.listener = l; }
        public void setVibrationEnabled(boolean e) { this.vibrationEnabled = e; }
        public boolean isPlaying() { return playing; }
        public boolean isGameOver() { return gameOver; }
        public void pauseGame() { paused = true; }
        public void resumeGame() { paused = false; lastFrameMs = SystemClock.uptimeMillis(); }

        public void startRenderLoop() {
            if (!loopActive) { loopActive = true; lastFrameMs = SystemClock.uptimeMillis(); postOnAnimation(frameRunnable); }
        }
        public void stopRenderLoop() { loopActive = false; removeCallbacks(frameRunnable); }

        private void triggerVibration(int ms, int amp) {
            if (!vibrationEnabled) return;
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(ms, amp));
                else v.vibrate(ms);
            }
        }

        public void setTopHUDHeight(int hPx) {
            if (Math.abs(this.dynamicTopPadding - hPx) < 1f) return;
            this.dynamicTopPadding = hPx;
            if (getWidth() > 0 && getHeight() > 0) { computeGeometry(getWidth(), getHeight()); invalidate(); }
        }

        private void computeGeometry(int w, int h) {
            screenW = w; screenH = h;
            float sidePad = Math.max(20f, w * 0.045f), bottomPad = Math.max(24f, h * 0.025f);
            float availW = w - sidePad * 2f, availH = h - dynamicTopPadding - bottomPad - 12f;
            cellSize = (float) Math.floor(Math.min(availW / COLS, availH / ROWS));
            if (cellSize < 8f) cellSize = 8f;
            boardW = COLS * cellSize; boardH = ROWS * cellSize;
            boardLeft = (float) Math.floor((w - boardW) / 2f);
            boardTop = (float) Math.floor(dynamicTopPadding + Math.max(8f, (availH - boardH) * 0.42f));
            boardRect.set(boardLeft, boardTop, boardLeft + boardW, boardTop + boardH);

            if (themeState == 0) bgPaint.setShader(new RadialGradient(w * 0.5f, h * 0.48f, Math.max(w, h) * 0.75f, Color.parseColor("#FFFFFF"), Color.parseColor("#F7F9FC"), Shader.TileMode.CLAMP));
            else if (themeState == 1) bgPaint.setShader(new RadialGradient(w * 0.5f, h * 0.45f, Math.max(w, h) * 0.72f, Color.parseColor("#232328"), Color.parseColor("#1C1C1E"), Shader.TileMode.CLAMP));
            else bgPaint.setShader(null);
        }

        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            computeGeometry(w, h);
            if (oldW == 0 && oldH == 0) resetGame();
            startRenderLoop();
        }

        private int nextFromBag() {
            if (bag.isEmpty()) { for (int i = 0; i < TETROMINOES.length; i++) bag.add(i); Collections.shuffle(bag, random); }
            return bag.remove(bag.size() - 1);
        }

        public void resetGame() {
            for (int r = 0; r < ROWS; r++) { clearingMask[r] = false; for (int c = 0; c < COLS; c++) { board[r][c] = 0; lockFlash[r][c] = 0f; } }
            particles.clear(); popups.clear(); bag.clear();
            score = 0; linesTotal = 0; combo = 0; fallAccumMs = 0f; lockTimerMs = 0f; lockResets = 0;
            boardBumpY = 0f; dropTrailAlpha = 0f; clearingRows = false; playing = false; paused = false; gameOver = false;
            nextType = nextFromBag();
            spawnPiece();
            invalidate();
        }

        private void spawnPiece() {
            currentType = nextType;
            int[][] base = TETROMINOES[currentType];
            currentPiece = new int[base.length][base[0].length];
            for (int r = 0; r < base.length; r++) System.arraycopy(base[r], 0, currentPiece[r], 0, base[r].length);
            currentX = (COLS - currentPiece[0].length) / 2;
            currentY = 0; fallAccumMs = 0f; lockTimerMs = 0f; lockResets = 0;
            nextType = nextFromBag();
            if (nextShapeView != null) nextShapeView.updateShape(nextType);

            if (!isValidMove(currentPiece, currentX, currentY)) {
                gameOver = true; playing = false; currentPiece = null;
                sound.playGameOver(); triggerVibration(320, VibrationEffect.DEFAULT_AMPLITUDE);
                if (listener != null) listener.onGameOver(score);
            }
        }

        private float getDelayMs() {
            int level = linesTotal / 8;
            return Math.max(145f, 520f - level * 32f);
        }

        private final Runnable frameRunnable = new Runnable() {
            @Override public void run() {
                if (!loopActive) return;
                long now = SystemClock.uptimeMillis();
                float dt = lastFrameMs == 0L ? 0.016f : Math.min(0.05f, (now - lastFrameMs) / 1000f);
                lastFrameMs = now;
                updateFrame(dt);
                invalidate();
                postOnAnimation(this);
            }
        };

        private void updateFrame(float dt) {
            bgTime += dt;
            if (Math.abs(boardBumpY) > 0.15f) boardBumpY += (0f - boardBumpY) * Math.min(1f, dt * 18f); else boardBumpY = 0f;
            if (dropTrailAlpha > 0f) dropTrailAlpha = Math.max(0f, dropTrailAlpha - dt * 5.2f);

            for (int r = 0; r < ROWS; r++) for (int c = 0; c < COLS; c++) if (lockFlash[r][c] > 0f) lockFlash[r][c] = Math.max(0f, lockFlash[r][c] - dt * 4.8f);

            Iterator<Particle> pit = particles.iterator();
            while (pit.hasNext()) {
                Particle p = pit.next();
                p.life += dt;
                if (p.life >= p.maxLife) { pit.remove(); continue; }
                p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 680f * dt; p.vx *= (1f - 1.8f * dt);
                p.rot += p.vrot * dt; p.alpha = 1f - (p.life / p.maxLife);
            }
            Iterator<ScorePopup> sit = popups.iterator();
            while (sit.hasNext()) {
                ScorePopup sp = sit.next();
                sp.y -= 72f * dt; sp.alpha -= dt * 1.15f;
                sp.scale += (1f - sp.scale) * Math.min(1f, dt * 14f);
                if (sp.alpha <= 0f) sit.remove();
            }

            if (!playing || paused || gameOver) return;

            if (clearingRows) {
                clearTimer -= dt;
                if (clearTimer <= 0f) finalizeClearRows();
                return;
            }
            if (currentPiece == null) return;

            if (!isValidMove(currentPiece, currentX, currentY + 1)) {
                lockTimerMs += dt * 1000f;
                if (lockTimerMs >= LOCK_DELAY_MS) lockPiece(false);
            } else {
                lockTimerMs = 0f; fallAccumMs += dt * 1000f;
                float delay = getDelayMs();
                if (fallAccumMs >= delay) { fallAccumMs -= delay; currentY++; }
            }
        }

        private boolean isValidMove(int[][] shape, int x, int y) {
            if (shape == null) return false;
            for (int r = 0; r < shape.length; r++) for (int c = 0; c < shape[0].length; c++) if (shape[r][c] != 0) {
                int bx = x + c, by = y + r;
                if (bx < 0 || bx >= COLS || by >= ROWS || (by >= 0 && board[by][bx] != 0)) return false;
            }
            return true;
        }

        private int getGhostY() {
            if (currentPiece == null) return currentY;
            int gy = currentY;
            while (isValidMove(currentPiece, currentX, gy + 1)) gy++;
            return gy;
        }

        private void hardDrop() {
            if (currentPiece == null || clearingRows) return;
            int gy = getGhostY();
            if (gy > currentY) {
                int minC = COLS, maxC = -1;
                for (int r = 0; r < currentPiece.length; r++) for (int c = 0; c < currentPiece[0].length; c++) if (currentPiece[r][c] != 0) { minC = Math.min(minC, c); maxC = Math.max(maxC, c); }
                trailLeft = boardLeft + (currentX + minC) * cellSize;
                trailRight = boardLeft + (currentX + maxC + 1) * cellSize;
                trailTop = boardTop + currentY * cellSize;
                trailBottom = boardTop + (gy + currentPiece.length) * cellSize;
                trailColor = palette[currentType + 1];
                dropTrailAlpha = 0.72f;
                currentY = gy;
            }
            boardBumpY = Math.max(7f, cellSize * 0.22f);
            spawnLandingDust(currentPiece, currentX, currentY, palette[currentType + 1]);
            sound.playHard(); triggerVibration(24, 155);
            lockPiece(true);
        }

        private void lockPiece(boolean fromHardDrop) {
            if (currentPiece == null) return;
            int colorIdx = currentType + 1;
            for (int r = 0; r < currentPiece.length; r++) for (int c = 0; c < currentPiece[0].length; c++) {
                if (currentPiece[r][c] != 0 && currentY + r >= 0 && currentY + r < ROWS) {
                    board[currentY + r][currentX + c] = colorIdx;
                    lockFlash[currentY + r][currentX + c] = 1f;
                }
            }
            if (!fromHardDrop) { boardBumpY = Math.max(2.5f, cellSize * 0.08f); sound.playLock(); triggerVibration(12, 85); }

            int fullCount = 0, firstFullRow = ROWS;
            for (int r = 0; r < ROWS; r++) {
                boolean full = true;
                for (int c = 0; c < COLS; c++) if (board[r][c] == 0) { full = false; break; }
                clearingMask[r] = full;
                if (full) { fullCount++; if (r < firstFullRow) firstFullRow = r; }
            }

            if (fullCount > 0) {
                clearingRows = true; clearTimer = CLEAR_DURATION; currentPiece = null;
                combo++; linesTotal += fullCount;
                score += 1;
                if (listener != null) listener.onScoreUpdated(score);
                sound.playClear(fullCount, combo);
                triggerVibration(fullCount >= 4 ? 75 : 42, fullCount >= 4 ? 220 : 140);
                for (int r = 0; r < ROWS; r++) if (clearingMask[r]) spawnRowParticles(r);
                String label = fullCount == 4 ? "TETRIS!" : (fullCount == 3 ? "TRIPLE" : (fullCount == 2 ? "DOUBLE" : (combo > 1 ? "COMBO x" + combo : "+1")));
                if (combo > 1 && fullCount > 1) label += "  x" + combo;
                ScorePopup sp = new ScorePopup();
                sp.text = label; sp.x = boardLeft + boardW / 2f; sp.y = boardTop + firstFullRow * cellSize;
                sp.alpha = 1.2f; sp.scale = 0.65f;
                sp.color = fullCount >= 4 ? palette[2] : (isLight ? Color.parseColor("#1E293B") : Color.WHITE);
                popups.add(sp);
            } else {
                combo = 0;
                spawnPiece();
            }
        }

        private void finalizeClearRows() {
            int writeRow = ROWS - 1;
            for (int r = ROWS - 1; r >= 0; r--) {
                if (!clearingMask[r]) {
                    if (writeRow != r) { System.arraycopy(board[r], 0, board[writeRow], 0, COLS); System.arraycopy(lockFlash[r], 0, lockFlash[writeRow], 0, COLS); }
                    writeRow--;
                }
            }
            for (int r = writeRow; r >= 0; r--) for (int c = 0; c < COLS; c++) { board[r][c] = 0; lockFlash[r][c] = 0f; }
            for (int r = 0; r < ROWS; r++) clearingMask[r] = false;
            clearingRows = false;
            spawnPiece();
        }

        private void spawnRowParticles(int row) {
            for (int c = 0; c < COLS; c++) {
                int col = palette[Math.max(1, board[row][c])];
                float cx = boardLeft + (c + 0.5f) * cellSize, cy = boardTop + (row + 0.5f) * cellSize;
                for (int k = 0; k < 3; k++) {
                    Particle p = new Particle();
                    p.x = cx + (random.nextFloat() - 0.5f) * cellSize * 0.5f; p.y = cy + (random.nextFloat() - 0.5f) * cellSize * 0.4f;
                    p.vx = (random.nextFloat() - 0.5f) * 320f; p.vy = -120f - random.nextFloat() * 240f;
                    p.size = cellSize * (0.16f + random.nextFloat() * 0.16f);
                    p.maxLife = 0.36f + random.nextFloat() * 0.28f; p.alpha = 1f;
                    p.rot = random.nextFloat() * 90f; p.vrot = (random.nextFloat() - 0.5f) * 360f; p.color = col;
                    particles.add(p);
                }
            }
        }

        private void spawnLandingDust(int[][] shape, int px, int py, int col) {
            for (int c = 0; c < shape[0].length; c++) {
                int botR = -1;
                for (int r = 0; r < shape.length; r++) if (shape[r][c] != 0) botR = r;
                if (botR >= 0) {
                    float bx = boardLeft + (px + c + 0.5f) * cellSize, by = boardTop + (py + botR + 1) * cellSize;
                    for (int k = 0; k < 2; k++) {
                        Particle p = new Particle();
                        p.x = bx + (random.nextFloat() - 0.5f) * cellSize * 0.6f; p.y = by;
                        p.vx = (random.nextFloat() - 0.5f) * 160f; p.vy = -40f - random.nextFloat() * 95f;
                        p.size = Math.max(3f, cellSize * 0.12f); p.maxLife = 0.25f + random.nextFloat() * 0.15f;
                        p.alpha = 0.85f; p.color = col;
                        particles.add(p);
                    }
                }
            }
        }

        private void rotatePiece() {
            if (currentPiece == null || clearingRows || currentType == 1) { if (currentType == 1) sound.playRotate(); return; }
            int rows = currentPiece.length, cols = currentPiece[0].length;
            int[][] rot = new int[cols][rows];
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) rot[c][rows - 1 - r] = currentPiece[r][c];
            int[][] kicks = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, -1}};
            for (int[] k : kicks) {
                if (isValidMove(rot, currentX + k[0], currentY + k[1])) {
                    currentPiece = rot; currentX += k[0]; currentY += k[1];
                    if (lockTimerMs > 0f && lockResets < 6) { lockTimerMs = 0f; lockResets++; }
                    sound.playRotate(); triggerVibration(9, 65);
                    return;
                }
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (gameOver || paused) return true;
            if (!playing) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    playing = true; lastFrameMs = SystemClock.uptimeMillis();
                    sound.playUi();
                    if (listener != null) listener.onGameStarted();
                }
                return true;
            }
            if (clearingRows || currentPiece == null) return true;

            int action = e.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    activePointerId = e.getPointerId(0);
                    downX = lastStepX = e.getX(); downY = lastStepY = e.getY();
                    movedDuringTouch = false; hardDroppedThisTouch = false; touchStartTime = SystemClock.uptimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int idx = e.findPointerIndex(activePointerId);
                    if (idx < 0 || hardDroppedThisTouch) return true;
                    float x = e.getX(idx), y = e.getY(idx);
                    float totalDx = x - downX, totalDy = y - downY;
                    long elapsed = SystemClock.uptimeMillis() - touchStartTime;

                    if (totalDy > cellSize * 2.1f && elapsed < 155 && totalDy > Math.abs(totalDx) * 2.0f) {
                        hardDroppedThisTouch = true; movedDuringTouch = true; hardDrop(); return true;
                    }

                    float stepX = cellSize * 0.85f;
                    while (x - lastStepX >= stepX) {
                        lastStepX += stepX;
                        if (isValidMove(currentPiece, currentX + 1, currentY)) {
                            currentX++; movedDuringTouch = true;
                            if (lockTimerMs > 0f && lockResets < 6) { lockTimerMs = 0f; lockResets++; }
                            sound.playMove(); triggerVibration(6, 45);
                        }
                    }
                    while (x - lastStepX <= -stepX) {
                        lastStepX -= stepX;
                        if (isValidMove(currentPiece, currentX - 1, currentY)) {
                            currentX--; movedDuringTouch = true;
                            if (lockTimerMs > 0f && lockResets < 6) { lockTimerMs = 0f; lockResets++; }
                            sound.playMove(); triggerVibration(6, 45);
                        }
                    }

                    float stepY = cellSize * 0.78f;
                    while (y - lastStepY >= stepY) {
                        lastStepY += stepY;
                        if (isValidMove(currentPiece, currentX, currentY + 1)) {
                            currentY++; fallAccumMs = 0f; movedDuringTouch = true; sound.playSoft();
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    int uidx = e.findPointerIndex(activePointerId);
                    if (uidx >= 0 && !hardDroppedThisTouch && action == MotionEvent.ACTION_UP) {
                        float ux = e.getX(uidx), uy = e.getY(uidx);
                        float tdx = ux - downX, tdy = uy - downY;
                        long dur = SystemClock.uptimeMillis() - touchStartTime;
                        if (tdy > cellSize * 2.2f && tdy > Math.abs(tdx) * 1.8f && dur < 240) hardDrop();
                        else if (!movedDuringTouch && dur < 250 && Math.hypot(tdx, tdy) < cellSize * 0.55f) rotatePiece();
                    }
                    activePointerId = MotionEvent.INVALID_POINTER_ID;
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private void drawMinimalBackground(Canvas canvas) {
            if (themeState == 2) canvas.drawColor(Color.BLACK);
            else canvas.drawRect(0, 0, screenW, screenH, bgPaint);

            float cx = screenW * 0.5f, cy = boardTop + boardH * 0.5f, drift = (float) Math.sin(bgTime * 0.45f) * 18f;
            if (themeState == 0) {
                ambientPaint.setStyle(Paint.Style.STROKE); ambientPaint.setStrokeWidth(2f);
                ambientPaint.setColor(Color.argb(42, 180, 194, 214));
                canvas.drawCircle(cx - screenW * 0.28f + drift, boardTop + boardH * 0.22f, screenW * 0.42f, ambientPaint);
                canvas.drawCircle(cx + screenW * 0.34f - drift, boardTop + boardH * 0.80f, screenW * 0.36f, ambientPaint);
                ambientPaint.setStyle(Paint.Style.FILL); ambientPaint.setColor(Color.argb(24, 148, 163, 184));
                for (int i = 0; i < 16; i++) canvas.drawCircle(starX[i] * screenW, starY[i] * screenH + (float) Math.sin(bgTime * 0.5f + starPhase[i]) * 6f, 2.2f, ambientPaint);
            } else if (themeState == 1) {
                ambientPaint.setStyle(Paint.Style.STROKE); ambientPaint.setStrokeWidth(1.8f);
                ambientPaint.setColor(Color.argb(18, 255, 255, 255));
                canvas.drawCircle(cx + screenW * 0.32f - drift, boardTop + boardH * 0.20f, screenW * 0.44f, ambientPaint);
                canvas.drawCircle(cx - screenW * 0.34f + drift, boardTop + boardH * 0.82f, screenW * 0.38f, ambientPaint);
                ambientPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 18; i++) {
                    int a = (int) (14 + 12 * Math.sin(bgTime * 0.7f + starPhase[i]));
                    ambientPaint.setColor(Color.argb(Math.max(4, a), 210, 220, 240));
                    canvas.drawCircle(starX[i] * screenW, starY[i] * screenH, starR[i], ambientPaint);
                }
            } else {
                ambientPaint.setStyle(Paint.Style.STROKE); ambientPaint.setStrokeWidth(1.4f);
                ambientPaint.setColor(Color.argb(16, 170, 190, 255));
                canvas.drawCircle(cx, cy + drift * 0.5f, screenW * 0.58f, ambientPaint);
                ambientPaint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < starX.length; i++) {
                    int a = (int) (28 + 38 * (0.5 + 0.5 * Math.sin(bgTime * 1.15f + starPhase[i])));
                    ambientPaint.setColor(Color.argb(a, 235, 242, 255));
                    canvas.drawCircle(starX[i] * screenW, starY[i] * screenH, starR[i] * 0.85f, ambientPaint);
                }
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            if (cellSize <= 0f) return;

            drawMinimalBackground(canvas);

            canvas.save();
            canvas.translate(0f, boardBumpY);

            float frameR = Math.max(14f, cellSize * 0.42f);
            float framePad = Math.max(6f, cellSize * 0.16f);
            tempRect.set(boardLeft - framePad, boardTop - framePad, boardLeft + boardW + framePad, boardTop + boardH + framePad);
            canvas.drawRoundRect(tempRect, frameR, frameR, boardWellPaint);
            canvas.drawRoundRect(tempRect, frameR, frameR, borderPaint);

            float dotR = Math.max(1.2f, cellSize * 0.038f);
            for (int r = 1; r < ROWS; r++) for (int c = 1; c < COLS; c++) canvas.drawCircle(boardLeft + c * cellSize, boardTop + r * cellSize, dotR, dotPaint);

            if (dropTrailAlpha > 0.01f) {
                fxPaint.setStyle(Paint.Style.FILL);
                fxPaint.setShader(new LinearGradient(0, trailTop, 0, trailBottom, Color.TRANSPARENT, Color.argb((int) (dropTrailAlpha * (isLight ? 55 : 70)), Color.red(trailColor), Color.green(trailColor), Color.blue(trailColor)), Shader.TileMode.CLAMP));
                canvas.drawRect(trailLeft + 2f, trailTop, trailRight - 2f, trailBottom, fxPaint);
                fxPaint.setShader(null);
            }

            float tilePad = Math.max(1.5f, cellSize * 0.055f);
            for (int r = 0; r < ROWS; r++) {
                boolean rowClearing = clearingRows && clearingMask[r];
                float clearProg = rowClearing ? (1f - Math.max(0f, clearTimer / CLEAR_DURATION)) : 0f;
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] != 0) {
                        float shrink = rowClearing ? (clearProg * cellSize * 0.42f) : 0f;
                        tempRect.set(boardLeft + c * cellSize + tilePad + shrink, boardTop + r * cellSize + tilePad + shrink, boardLeft + (c + 1) * cellSize - tilePad - shrink, boardTop + (r + 1) * cellSize - tilePad - shrink);
                        if (tempRect.width() > 1f) drawMinimalTile(canvas, tempRect, palette[board[r][c]], isLight, fillPaint, hlPaint, shPaint, rowClearing ? 1f : lockFlash[r][c]);
                    }
                }
                if (rowClearing) {
                    fxPaint.setStyle(Paint.Style.FILL);
                    fxPaint.setColor(Color.argb((int) ((1f - clearProg) * 165), 255, 255, 255));
                    canvas.drawRoundRect(boardLeft + 2f, boardTop + r * cellSize + 2f, boardLeft + boardW - 2f, boardTop + (r + 1) * cellSize - 2f, 8f, 8f, fxPaint);
                }
            }

            if (currentPiece != null && !clearingRows) {
                int col = palette[currentType + 1], gy = getGhostY();
                if (gy > currentY) {
                    float cr = Math.max(3f, cellSize * 0.21f);
                    ghostFill.setColor(Color.argb(isLight ? 24 : 28, Color.red(col), Color.green(col), Color.blue(col)));
                    ghostStroke.setColor(Color.argb(isLight ? 105 : 115, Color.red(col), Color.green(col), Color.blue(col)));
                    for (int r = 0; r < currentPiece.length; r++) for (int c = 0; c < currentPiece[0].length; c++) if (currentPiece[r][c] != 0 && gy + r >= 0) {
                        tempRect.set(boardLeft + (currentX + c) * cellSize + tilePad + 1f, boardTop + (gy + r) * cellSize + tilePad + 1f, boardLeft + (currentX + c + 1) * cellSize - tilePad - 1f, boardTop + (gy + r + 1) * cellSize - tilePad - 1f);
                        canvas.drawRoundRect(tempRect, cr, cr, ghostFill);
                        canvas.drawRoundRect(tempRect, cr, cr, ghostStroke);
                    }
                }
                for (int r = 0; r < currentPiece.length; r++) for (int c = 0; c < currentPiece[0].length; c++) if (currentPiece[r][c] != 0 && currentY + r >= 0) {
                    tempRect.set(boardLeft + (currentX + c) * cellSize + tilePad, boardTop + (currentY + r) * cellSize + tilePad, boardLeft + (currentX + c + 1) * cellSize - tilePad, boardTop + (currentY + r + 1) * cellSize - tilePad);
                    drawMinimalTile(canvas, tempRect, col, isLight, fillPaint, hlPaint, shPaint, 0f);
                }
            }

            fxPaint.setStyle(Paint.Style.FILL);
            for (Particle p : particles) {
                fxPaint.setColor(Color.argb((int) (Math.max(0f, Math.min(1f, p.alpha)) * 235), Color.red(p.color), Color.green(p.color), Color.blue(p.color)));
                canvas.save(); canvas.translate(p.x, p.y); canvas.rotate(p.rot);
                float hs = p.size * 0.5f;
                canvas.drawRoundRect(-hs, -hs, hs, hs, hs * 0.45f, hs * 0.45f, fxPaint);
                canvas.restore();
            }

            textPaint.setTextSize(Math.max(30f, cellSize * 0.66f));
            for (ScorePopup sp : popups) {
                int a = (int) (Math.max(0f, Math.min(1f, sp.alpha)) * 255);
                textPaint.setColor(Color.argb(a, Color.red(sp.color), Color.green(sp.color), Color.blue(sp.color)));
                canvas.save(); canvas.translate(sp.x, sp.y); canvas.scale(sp.scale, sp.scale);
                canvas.drawText(sp.text, 0f, 0f, textPaint);
                canvas.restore();
            }

            canvas.restore();
        }
    }
}