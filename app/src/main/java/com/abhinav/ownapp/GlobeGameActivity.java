package com.abhinav.ownapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class GlobeGameActivity extends AppCompatActivity {

    private UfoGameOverlayView ufoOverlay;
    private boolean isDarkTheme;
    private int themeState;

    // UI Elements
    private LinearLayout topUiBar, btnScore, btnLives;
    private TextView tvScoreText, tvLivesText;
    private FrameLayout scoreIconContainer, livesIconContainer;
    private GameIconView scoreIconView, livesIconView;
    private int iconColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Smooth opening animation for the Activity
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // Fetch Main App Theme
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
        themeState = prefs.getInt("app_theme_state", 1);

        // Apply true immersive full-screen mode before setting content view
        hideSystemUI();

        setContentView(R.layout.activity_globe_game);

        // Double check no residual padding exists from MainApplication
        View contentView = findViewById(android.R.id.content);
        if (contentView != null) {
            contentView.setPadding(0, 0, 0, 0);
        }

        ufoOverlay = findViewById(R.id.ufo_overlay);
        GlobeGLSurfaceView globeView = findViewById(R.id.globe_view_game);

        // Setup the new Top UI Bar
        setupSpaceThemedHUD();

        // Reveal the HUD when the user exits "Scenery Mode" by long-pressing
        globeView.setOnGlobeLongPressListener(() -> {
            ufoOverlay.startGame();
            showGameHUD();
        });

        // Listen for Game Over and show the themed dialog
        ufoOverlay.setOnGameOverListener(finalScore -> {
            runOnUiThread(() -> {
                hideGameHUD();
                showGameOverDialog(finalScore);
            });
        });
    }

    // --- SMOOTH CLOSING ANIMATION ---
    @Override
    public void finish() {
        super.finish();
        // Uses 0 for enter anim and fade_out for exit to completely eliminate the white flash!
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void setupSpaceThemedHUD() {
        topUiBar = findViewById(R.id.top_ui_bar);

        btnScore = findViewById(R.id.btnScore);
        scoreIconContainer = findViewById(R.id.scoreIconContainer);
        tvScoreText = findViewById(R.id.tvScoreText);

        btnLives = findViewById(R.id.btnLives);
        livesIconContainer = findViewById(R.id.livesIconContainer);
        tvLivesText = findViewById(R.id.tvLivesText);

        // Prevent UI from rendering under notches
        ViewCompat.setOnApplyWindowInsetsListener(topUiBar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            topUiBar.setPadding(
                    topUiBar.getPaddingLeft(),
                    insets.top + (int) (12 * getResources().getDisplayMetrics().density),
                    topUiBar.getPaddingRight(),
                    topUiBar.getPaddingBottom()
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // Inject Mathematical Vector Icons
        scoreIconView = new GameIconView(this);
        scoreIconContainer.addView(scoreIconView);

        livesIconView = new GameIconView(this);
        livesIconContainer.addView(livesIconView);

        // --- SPACE THEME OVERRIDE ---
        // Even if the global app is in Light Mode, the space background is always black!
        // We FORCE a premium dark translucent aesthetic for readability and immersion.
        int pillBgColor = Color.parseColor("#CC1A1A24"); // Translucent deep space grey
        int boxBgColor = Color.parseColor("#334A90E2");  // Translucent glowing blue
        iconColor = Color.parseColor("#80B4FF");         // Bright celestial blue
        int textColor = Color.parseColor("#FFFFFF");

        // Apply styles to Score (Left)
        btnScore.setBackground(createPillShape(pillBgColor));
        scoreIconContainer.setBackground(createBoxShape(boxBgColor));
        scoreIconView.setIcon(GameIconView.ICON_SCORE, iconColor);
        tvScoreText.setTextColor(textColor);

        // Apply styles to Lives (Right)
        btnLives.setBackground(createPillShape(pillBgColor));
        livesIconContainer.setBackground(createBoxShape(boxBgColor));
        livesIconView.setIcon(GameIconView.ICON_HEART, Color.parseColor("#FF4444")); // Use Red for Lives
        tvLivesText.setTextColor(textColor);
    }

    private void showGameHUD() {
        if (topUiBar.getVisibility() == View.GONE) {
            topUiBar.setAlpha(0f);
            topUiBar.setVisibility(View.VISIBLE);
            topUiBar.animate().alpha(1f).setDuration(500).start();
        }
    }

    private void hideGameHUD() {
        if (topUiBar.getVisibility() == View.VISIBLE) {
            topUiBar.animate().alpha(0f).setDuration(400).withEndAction(() -> topUiBar.setVisibility(View.GONE)).start();
        }
    }

    // --- PUBLIC METHOD FOR UFO OVERLAY TO CALL ---
    public void updateScoreAndLives(int score, int lives) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (tvScoreText != null) tvScoreText.setText("Score: " + score);
            if (tvLivesText != null) tvLivesText.setText("Lives: " + lives);
        });
    }

    private GradientDrawable createPillShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(1000f);
        return shape;
    }

    private GradientDrawable createBoxShape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(30f);
        return shape;
    }

    // Ensures the game stays in full screen even if the user swipes down the notification bar
    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    // Method to force absolute full screen and draw into the notch area
    private void hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // FIX: Aggressively force status bar color to transparent
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Hide both the status bar and the navigation bar
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // Ensure the game draws completely into the notch/cutout area to prevent the white letterbox bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (ufoOverlay != null && ufoOverlay.handleTouch(ev.getX(), ev.getY())) {
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // The custom themed Game Over Window with Best Score tracking
    private void showGameOverDialog(int score) {
        // --- 1. HANDLE BEST SCORE LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        int bestScore = prefs.getInt("globe_game_best_score", 0);

        // If the current score beats the best score, update it!
        if (score > bestScore) {
            bestScore = score;
            prefs.edit().putInt("globe_game_best_score", bestScore).apply();
        }

        // --- 2. BUILD THE UI ---
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Force a Dark/Star Dialog theme because space is always dark!
        int dialogBgColor = (themeState == 2) ? Color.parseColor("#121212") : Color.parseColor("#2C2C2E");
        int dialogTextColor = Color.WHITE;
        int dialogSubTextColor = Color.parseColor("#A0A0A5");
        int dialogQuitBtnColor = Color.parseColor("#3A3A3C");

        // Main Dialog Layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(70, 70, 70, 70);
        layout.setGravity(Gravity.CENTER);

        // Rounded background for the dialog
        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(dialogBgColor);
        bgGd.setCornerRadius(60f);
        layout.setBackground(bgGd);

        // "GAME OVER" Text
        TextView tvGameOver = new TextView(this);
        tvGameOver.setText("GAME OVER");
        tvGameOver.setTextSize(32f);
        tvGameOver.setTypeface(null, Typeface.BOLD);
        tvGameOver.setTextColor(dialogTextColor);
        tvGameOver.setGravity(Gravity.CENTER);

        // "Score" Text
        TextView tvScoreDisplay = new TextView(this);
        tvScoreDisplay.setText("Score: " + score);
        tvScoreDisplay.setTextSize(26f);
        tvScoreDisplay.setTypeface(null, Typeface.BOLD);
        tvScoreDisplay.setTextColor(dialogTextColor);
        tvScoreDisplay.setGravity(Gravity.CENTER);
        tvScoreDisplay.setPadding(0, 20, 0, 10);

        // "Best Score" Text
        TextView tvBestScore = new TextView(this);
        tvBestScore.setText("Best Score: " + bestScore);
        tvBestScore.setTextSize(16f);
        tvBestScore.setTypeface(null, Typeface.BOLD);
        tvBestScore.setTextColor(dialogSubTextColor);
        tvBestScore.setGravity(Gravity.CENTER);
        tvBestScore.setPadding(0, 0, 0, 60);

        // Play Again Button (Always Blue)
        Button btnPlayAgain = new Button(this);
        btnPlayAgain.setText("Play Again");
        btnPlayAgain.setTextColor(Color.WHITE);
        btnPlayAgain.setAllCaps(false);
        btnPlayAgain.setTextSize(18f);
        btnPlayAgain.setTypeface(null, Typeface.BOLD);
        GradientDrawable playGd = new GradientDrawable();
        playGd.setColor(Color.parseColor("#4A90E2"));
        playGd.setCornerRadius(100f);
        btnPlayAgain.setBackground(playGd);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140);
        btnPlayAgain.setLayoutParams(playLp);

        // Quit Button (Themed)
        Button btnQuit = new Button(this);
        btnQuit.setText("Quit");
        btnQuit.setTextColor(dialogTextColor);
        btnQuit.setAllCaps(false);
        btnQuit.setTextSize(18f);
        btnQuit.setTypeface(null, Typeface.BOLD);
        GradientDrawable quitGd = new GradientDrawable();
        quitGd.setColor(dialogQuitBtnColor);
        quitGd.setCornerRadius(100f);
        btnQuit.setBackground(quitGd);
        LinearLayout.LayoutParams quitLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 140);
        quitLp.setMargins(0, 30, 0, 0);
        btnQuit.setLayoutParams(quitLp);

        // Assemble Layout
        layout.addView(tvGameOver);
        layout.addView(tvScoreDisplay);
        layout.addView(tvBestScore);
        layout.addView(btnPlayAgain);
        layout.addView(btnQuit);

        builder.setView(layout);
        builder.setCancelable(false); // Prevents tapping outside to dismiss

        AlertDialog dialog = builder.create();

        // Makes the square dialog corners transparent so our rounded corners show
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Temporarily disable focus so the status bar doesn't pop down when the dialog opens
            dialog.getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

        // Button Clicks
        btnPlayAgain.setOnClickListener(v -> {
            dialog.dismiss();
            ufoOverlay.startGame();
            showGameHUD();
            // Reset local UI texts back to zero
            tvScoreText.setText("Score: 0");
            tvLivesText.setText("Lives: 3");
        });

        btnQuit.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();

        // Restore focus so buttons can be clicked
        if (dialog.getWindow() != null) {
            dialog.getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
            dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            // Ensure status bar hides again after dialog shows
            hideSystemUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (ufoOverlay != null) {
            ufoOverlay.stopGame();
        }
    }

    // --- MATHEMATICAL VECTOR ICON ENGINE ---
    public static class GameIconView extends View {
        public static final int ICON_SCORE = 1;
        public static final int ICON_HEART = 2;

        private int iconType = ICON_SCORE;
        private Paint paint;

        public GameIconView(Context context) { super(context); init(); }

        private void init() {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4.5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIcon(int type, int color) {
            this.iconType = type;
            paint.setColor(color);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            if (iconType == ICON_SCORE) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5.0f);
                canvas.drawCircle(cx, cy, 7.5f, paint);
            }
            else if (iconType == ICON_HEART) {
                paint.setStyle(Paint.Style.FILL);
                Path p = new Path();
                p.moveTo(cx, cy + 8f);
                p.cubicTo(cx - 16f, cy - 2f, cx - 8f, cy - 12f, cx, cy - 4f);
                p.cubicTo(cx + 8f, cy - 12f, cx + 16f, cy - 2f, cx, cy + 8f);
                canvas.drawPath(p, paint);
            }
        }
    }
}