package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

public class GamesGalleryActivity extends AppCompatActivity {

    private LinearLayout root;
    private int revealX;
    private int revealY;

    private int themeState;
    private int bgColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        setContentView(R.layout.activity_games_gallery);

        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        final int cardColor;
        final int textColor;
        final int subTextColor;

        if (themeState == 0) { // Light Mode
            bgColor = Color.WHITE;
            cardColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            subTextColor = Color.parseColor("#555555");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
        }

        root = findViewById(R.id.gamesGalleryRoot);
        GridLayout gridLayout = findViewById(R.id.gamesGridLayout);
        TextView title = findViewById(R.id.tvGamesTitle);
        TextView subtitle = findViewById(R.id.tvGamesSubtitle);

        View cardSnakeGame = findViewById(R.id.cardSnakeGame);
        TextView textSnake = findViewById(R.id.textSnakeGame);

        View cardFlappyBird = findViewById(R.id.cardFlappyBird);
        TextView textFlappy = findViewById(R.id.textFlappyBird);

        View cardTetris = findViewById(R.id.cardTetris);
        TextView textTetris = findViewById(R.id.textTetris);

        View cardBreakout = findViewById(R.id.cardBreakout);
        TextView textBreakout = findViewById(R.id.textBreakout);

        View cardMazeBall = findViewById(R.id.cardMazeBall);
        TextView textMazeBall = findViewById(R.id.textMazeBall);

        View cardMyPlanet = findViewById(R.id.cardMyPlanet);
        TextView textMyPlanet = findViewById(R.id.textMyPlanet);
        TextView btnHelpMyPlanet = findViewById(R.id.btnHelpMyPlanet);

        if (root != null) root.setBackgroundColor(bgColor);
        if (title != null) title.setTextColor(textColor);
        if (subtitle != null) subtitle.setTextColor(subTextColor);

        enforceStatusBarIcons();

        Intent intent = getIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && root != null) {
            if (savedInstanceState == null) {
                root.setVisibility(View.INVISIBLE);
                ViewTreeObserver viewTreeObserver = root.getViewTreeObserver();
                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            revealX = intent.getIntExtra("REVEAL_X", root.getWidth() / 2);
                            revealY = intent.getIntExtra("REVEAL_Y", root.getHeight() / 2);
                            float finalRadius = (float) Math.hypot(root.getWidth(), root.getHeight());
                            Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, 0, finalRadius);
                            circularReveal.setDuration(350);
                            circularReveal.setInterpolator(new DecelerateInterpolator());
                            root.setVisibility(View.VISIBLE);
                            circularReveal.start();
                        }
                    });
                }
            } else {
                root.setVisibility(View.VISIBLE);
                root.post(() -> {
                    revealX = intent.getIntExtra("REVEAL_X", root.getWidth() / 2);
                    revealY = intent.getIntExtra("REVEAL_Y", root.getHeight() / 2);
                });
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && root != null && root.isAttachedToWindow()) {
                    int exitX = (revealX > 0 && revealX <= root.getWidth()) ? revealX : root.getWidth() / 2;
                    int exitY = (revealY > 0 && revealY <= root.getHeight()) ? revealY : root.getHeight() / 2;
                    float startRadius = (float) Math.hypot(root.getWidth(), root.getHeight());
                    Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, exitX, exitY, startRadius, 0);
                    circularReveal.setDuration(350);
                    circularReveal.setInterpolator(new DecelerateInterpolator());
                    circularReveal.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            root.setVisibility(View.INVISIBLE);
                            finish();
                            overridePendingTransition(0, 0);
                        }
                    });
                    circularReveal.start();
                } else {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });

        if (cardSnakeGame != null) { cardSnakeGame.setBackground(createCardBg(cardColor)); textSnake.setTextColor(textColor); cardSnakeGame.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, SnakeGameActivity.class))); }
        if (cardFlappyBird != null) { cardFlappyBird.setBackground(createCardBg(cardColor)); textFlappy.setTextColor(textColor); cardFlappyBird.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, FlappyBirdActivity.class))); }
        if (cardTetris != null) { cardTetris.setBackground(createCardBg(cardColor)); textTetris.setTextColor(textColor); cardTetris.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, TetrisActivity.class))); }
        if (cardBreakout != null) { cardBreakout.setBackground(createCardBg(cardColor)); textBreakout.setTextColor(textColor); cardBreakout.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, BreakoutActivity.class))); }
        if (cardMazeBall != null) { cardMazeBall.setBackground(createCardBg(cardColor)); textMazeBall.setTextColor(textColor); cardMazeBall.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, MazeBallActivity.class))); }

        if (cardMyPlanet != null) {
            cardMyPlanet.setBackground(createCardBg(cardColor)); textMyPlanet.setTextColor(textColor);
            GradientDrawable helpBg = new GradientDrawable(); helpBg.setShape(GradientDrawable.OVAL); helpBg.setStroke(3, subTextColor);
            btnHelpMyPlanet.setTextColor(subTextColor); btnHelpMyPlanet.setBackground(helpBg);
            final int finalThemeState = themeState;
            btnHelpMyPlanet.setOnClickListener(v -> showInstructionsDialog(finalThemeState));
            cardMyPlanet.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, GlobeGameActivity.class)));
        }

        // Thrive Card (Index 6)
        if (gridLayout != null && gridLayout.getChildCount() > 6) {
            View seventhCard = gridLayout.getChildAt(6);
            if (seventhCard instanceof LinearLayout) {
                LinearLayout cardThrive = (LinearLayout) seventhCard;
                cardThrive.setBackground(createCardBg(cardColor));
                if (cardThrive.getChildCount() > 0 && cardThrive.getChildAt(0) instanceof ImageView) {
                    ImageView imgThrive = (ImageView) cardThrive.getChildAt(0);
                    @SuppressLint("DiscouragedApi") int customId = getResources().getIdentifier("thrive_preview", "drawable", getPackageName());
                    if (customId != 0) imgThrive.setImageResource(customId); else imgThrive.setImageBitmap(createThrivePreviewBitmap());
                }
                if (cardThrive.getChildCount() > 1 && cardThrive.getChildAt(1) instanceof TextView) {
                    ((TextView) cardThrive.getChildAt(1)).setTextColor(textColor);
                }
                cardThrive.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, ThriveActivity.class)));
            }
        }

        // DriveOn Car Game Card (Index 7) - Explicitly hooked up to XML and themed!
        View cardDriveOn = findViewById(R.id.cardDriveOn);
        if (cardDriveOn instanceof LinearLayout) {
            LinearLayout linDrive = (LinearLayout) cardDriveOn;
            linDrive.setBackground(createCardBg(cardColor));
            if (linDrive.getChildCount() > 0 && linDrive.getChildAt(0) instanceof ImageView) {
                ((ImageView) linDrive.getChildAt(0)).setImageBitmap(createDriveOnPreviewBitmap());
            }
            if (linDrive.getChildCount() > 1 && linDrive.getChildAt(1) instanceof TextView) {
                ((TextView) linDrive.getChildAt(1)).setTextColor(textColor);
            }
            linDrive.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, DriveOnActivity.class)));
        }
    }

    private GradientDrawable createCardBg(int cardColor) {
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(32f);
        return cardBg;
    }

    private Bitmap createThrivePreviewBitmap() {
        int w = 400, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE); stroke.setStrokeCap(Paint.Cap.ROUND); stroke.setStrokeJoin(Paint.Join.ROUND);

        RectF cardRect = new RectF(0, 0, w, h);
        Path clipPath = new Path(); clipPath.addRoundRect(cardRect, 44f, 44f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        paint.setColor(Color.parseColor("#112218")); canvas.drawRect(cardRect, paint);
        paint.setColor(Color.parseColor("#1A3023")); paint.setStrokeWidth(3f);
        for (int gx = 80; gx < w; gx += 80) canvas.drawLine(gx, 0, gx, h, paint);
        for (int gy = 80; gy < h; gy += 80) canvas.drawLine(0, gy, w, gy, paint);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#1D9A50"));
        canvas.drawCircle(35f, h * 0.22f, 68f, paint); canvas.drawCircle(w * 0.82f, 28f, 64f, paint);

        canvas.save(); canvas.translate(w * 0.42f, h * 0.58f);
        stroke.setColor(Color.parseColor("#0D1B2A")); stroke.setStrokeWidth(4.5f);
        paint.setColor(Color.parseColor("#1D4ED8")); canvas.drawCircle(0f, 0f, 28f, paint); canvas.drawCircle(0f, 0f, 28f, stroke);
        canvas.restore();
        return bmp;
    }

    private Bitmap createDriveOnPreviewBitmap() {
        @SuppressLint("DiscouragedApi")
        int customId = getResources().getIdentifier("driveonicon", "drawable", getPackageName());
        if (customId != 0) {
            return BitmapFactory.decodeResource(getResources(), customId);
        }

        int w = 400, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF cardRect = new RectF(0, 0, w, h);
        Path clipPath = new Path(); clipPath.addRoundRect(cardRect, 44f, 44f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        paint.setColor(Color.parseColor("#88C478")); canvas.drawRect(cardRect, paint);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRect(w * 0.35f, 0, w * 0.65f, h, paint);
        canvas.drawRect(0, h * 0.35f, w, h * 0.65f, paint);
        return bmp;
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        enforceStatusBarIcons();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enforceStatusBarIcons();
    }

    private void enforceStatusBarIcons() {
        getWindow().setStatusBarColor(bgColor);
        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decor);
        if (themeState == 0) {
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getWindow().setNavigationBarColor(bgColor);
        } else {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getWindow().setNavigationBarColor(bgColor);
        }
    }

    private void showInstructionsDialog(int themeState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        int dialogBgColor, dialogTextColor, dialogSubTextColor;
        if (themeState == 0) {
            dialogBgColor = Color.WHITE; dialogTextColor = Color.parseColor("#1C1C1E"); dialogSubTextColor = Color.parseColor("#666666");
        } else {
            dialogBgColor = Color.parseColor("#2C2C2E"); dialogTextColor = Color.WHITE; dialogSubTextColor = Color.parseColor("#B0B0B8");
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(60, 60, 60, 60);
        GradientDrawable bgGd = new GradientDrawable(); bgGd.setColor(dialogBgColor); bgGd.setCornerRadius(50f); layout.setBackground(bgGd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("How to Play"); tvTitle.setTextSize(24f); tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(dialogTextColor); tvTitle.setGravity(Gravity.CENTER); tvTitle.setPadding(0, 0, 0, 40);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(
                "🌍 Explore Phase:\n" +
                        "Swipe to rotate the globe and double-tap to cycle through different planets. Enjoy the relaxing view of space!\n\n" +
                        "🛸 Alien Invasion:\n" +
                        "Ready for a challenge? Long-press the planet to trigger an invasion! Tap incoming UFOs to destroy them before they crash into the surface and take your lives.\n\n" +
                        "🛡️ Divine Shield:\n" +
                        "For every 25 points you score, you will unleash a majestic celestial shockwave that instantly destroys all nearby enemies."
        );
        tvMessage.setTextSize(15f); tvMessage.setTextColor(dialogSubTextColor); tvMessage.setLineSpacing(0, 1.3f); tvMessage.setPadding(0, 0, 0, 60);

        Button btnGotIt = new Button(this);
        btnGotIt.setText("Got it!"); btnGotIt.setTextColor(Color.WHITE); btnGotIt.setAllCaps(false); btnGotIt.setTextSize(16f); btnGotIt.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnGd = new GradientDrawable(); btnGd.setColor(Color.parseColor("#4A90E2")); btnGd.setCornerRadius(100f); btnGotIt.setBackground(btnGd);
        btnGotIt.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130));

        layout.addView(tvTitle); layout.addView(tvMessage); layout.addView(btnGotIt);
        AlertDialog dialog = builder.setView(layout).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}