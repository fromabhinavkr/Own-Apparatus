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

    // --- State variables ---
    private int themeState;
    private int bgColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep the underlying window permanently transparent to avoid animation glitching
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        // Automatically loads res/layout/ in Portrait and res/layout-land/ in Landscape
        setContentView(R.layout.activity_games_gallery);

        // --- 3-STATE THEME LOGIC ---
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        // Apply Theme Colors dynamically based on the 3 states
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

        // Link Views
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

        // Apply Status Bar Icons immediately
        enforceStatusBarIcons();

        // --- Handle Circular Reveal Entry Animation (Only on initial launch, not rotation) ---
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
                // On orientation change, show immediately and keep reveal coordinates centered for exit
                root.setVisibility(View.VISIBLE);
                root.post(() -> {
                    revealX = intent.getIntExtra("REVEAL_X", root.getWidth() / 2);
                    revealY = intent.getIntExtra("REVEAL_Y", root.getHeight() / 2);
                });
            }
        }

        // --- Handle Circular Reveal Exit Animation ---
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

        if (cardSnakeGame != null) {
            cardSnakeGame.setBackground(createCardBg(cardColor));
            textSnake.setTextColor(textColor);
            cardSnakeGame.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, SnakeGameActivity.class)));
        }

        if (cardFlappyBird != null) {
            cardFlappyBird.setBackground(createCardBg(cardColor));
            textFlappy.setTextColor(textColor);
            cardFlappyBird.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, FlappyBirdActivity.class)));
        }

        if (cardTetris != null) {
            cardTetris.setBackground(createCardBg(cardColor));
            textTetris.setTextColor(textColor);
            cardTetris.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, TetrisActivity.class)));
        }

        if (cardBreakout != null) {
            cardBreakout.setBackground(createCardBg(cardColor));
            textBreakout.setTextColor(textColor);
            cardBreakout.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, BreakoutActivity.class)));
        }

        if (cardMazeBall != null) {
            cardMazeBall.setBackground(createCardBg(cardColor));
            textMazeBall.setTextColor(textColor);
            cardMazeBall.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, MazeBallActivity.class)));
        }

        // Play My Planet Logic & Theme
        if (cardMyPlanet != null) {
            cardMyPlanet.setBackground(createCardBg(cardColor));
            textMyPlanet.setTextColor(textColor);

            GradientDrawable helpBg = new GradientDrawable();
            helpBg.setShape(GradientDrawable.OVAL);
            helpBg.setStroke(3, subTextColor);
            btnHelpMyPlanet.setTextColor(subTextColor);
            btnHelpMyPlanet.setBackground(helpBg);

            final int finalThemeState = themeState;
            btnHelpMyPlanet.setOnClickListener(v -> showInstructionsDialog(finalThemeState));

            cardMyPlanet.setOnClickListener(v ->
                    startActivity(new Intent(GamesGalleryActivity.this, GlobeGameActivity.class))
            );
        }

        // Play Thrive Card Logic & Dynamic Preview (Works with both Portrait and Landscape XMLs)
        if (gridLayout != null && gridLayout.getChildCount() > 6) {
            View seventhCard = gridLayout.getChildAt(6);
            if (seventhCard instanceof LinearLayout) {
                LinearLayout cardThrive = (LinearLayout) seventhCard;
                cardThrive.setBackground(createCardBg(cardColor));

                if (cardThrive.getChildCount() > 0 && cardThrive.getChildAt(0) instanceof ImageView) {
                    ImageView imgThrive = (ImageView) cardThrive.getChildAt(0);
                    @SuppressLint("DiscouragedApi")
                    int customDrawableId = getResources().getIdentifier("thrive_preview", "drawable", getPackageName());
                    if (customDrawableId != 0) {
                        imgThrive.setImageResource(customDrawableId);
                    } else {
                        imgThrive.setImageBitmap(createThrivePreviewBitmap());
                    }
                }

                if (cardThrive.getChildCount() > 1 && cardThrive.getChildAt(1) instanceof TextView) {
                    TextView textThrive = (TextView) cardThrive.getChildAt(1);
                    textThrive.setTextColor(textColor);
                }

                cardThrive.setOnClickListener(v ->
                        startActivity(new Intent(GamesGalleryActivity.this, ThriveActivity.class))
                );
            }
        }
    }

    private GradientDrawable createCardBg(int cardColor) {
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(32f);
        return cardBg;
    }

    /**
     * Generates a crisp preview thumbnail for Thrive matching the exact aspect ratio
     * of your other game preview drawables.
     */
    private Bitmap createThrivePreviewBitmap() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.breakout_preview, opts);

        int w = 400;
        int h = (opts.outWidth > 0 && opts.outHeight > 0)
                ? Math.round(400f * opts.outHeight / opts.outWidth)
                : 400;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        // Rounded preview background (#112218 dark forest green)
        RectF cardRect = new RectF(0, 0, w, h);
        Path clipPath = new Path();
        clipPath.addRoundRect(cardRect, 44f, 44f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        paint.setColor(Color.parseColor("#112218"));
        canvas.drawRect(cardRect, paint);

        // Tactical Grid lines
        paint.setColor(Color.parseColor("#1A3023"));
        paint.setStrokeWidth(3f);
        for (int gx = 80; gx < w; gx += 80) canvas.drawLine(gx, 0, gx, h, paint);
        for (int gy = 80; gy < h; gy += 80) canvas.drawLine(0, gy, w, gy, paint);

        // Bushes in top-left and top-right
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1D9A50"));
        canvas.drawCircle(35f, h * 0.22f, 68f, paint);
        canvas.drawCircle(w * 0.82f, 28f, 64f, paint);
        paint.setColor(Color.parseColor("#25B560"));
        canvas.drawCircle(25f, h * 0.20f, 48f, paint);
        canvas.drawCircle(w * 0.80f, 18f, 45f, paint);

        // Red Zone Arc at bottom-right
        paint.setColor(Color.parseColor("#B91C1C"));
        canvas.drawCircle(w * 0.92f, h * 1.05f, 85f, paint);
        stroke.setColor(Color.parseColor("#FBBF24"));
        stroke.setStrokeWidth(6f);
        canvas.drawCircle(w * 0.92f, h * 1.05f, 85f, stroke);

        // Player Character in center
        float cx = w * 0.42f;
        float cy = h * 0.58f;
        float r = 38f;

        canvas.save();
        canvas.translate(cx, cy);

        stroke.setColor(Color.parseColor("#0D1B2A"));
        stroke.setStrokeWidth(4.5f);

        // Orange Backpack
        Path pack = new Path();
        pack.moveTo(-r * 1.15f, -r * 0.9f);
        pack.lineTo(-r * 0.25f, -r * 0.65f);
        pack.lineTo(-r * 0.25f, r * 0.65f);
        pack.lineTo(-r * 1.15f, r * 0.9f);
        pack.close();
        paint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawPath(pack, paint);
        canvas.drawPath(pack, stroke);

        // Gun & Barrel
        float gunY = r * 0.35f;
        RectF rect = new RectF(0f, gunY - r * 0.16f, r * 1.15f, gunY + r * 0.16f);
        paint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawRoundRect(rect, 6f, 6f, paint);
        canvas.drawRoundRect(rect, 6f, 6f, stroke);

        rect.set(r * 1.1f, gunY - r * 0.11f, r * 2.05f, gunY + r * 0.11f);
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawRoundRect(rect, 4f, 4f, paint);
        canvas.drawRoundRect(rect, 4f, 4f, stroke);

        // Blue Arms
        paint.setColor(Color.parseColor("#1D4ED8"));
        canvas.save();
        canvas.translate(r * 0.2f, -r * 0.65f);
        canvas.rotate(28f);
        rect.set(0f, -r * 0.2f, r * 0.85f, r * 0.2f);
        canvas.drawRoundRect(rect, 8f, 8f, paint);
        canvas.drawRoundRect(rect, 8f, 8f, stroke);
        canvas.restore();

        rect.set(0f, r * 0.38f, r * 0.85f, r * 0.75f);
        canvas.drawRoundRect(rect, 8f, 8f, paint);
        canvas.drawRoundRect(rect, 8f, 8f, stroke);

        // Yellow Shoulder Pads
        paint.setColor(Color.parseColor("#FBBF24"));
        rect.set(-r * 0.32f, -r * 0.98f, r * 0.28f, -r * 0.58f);
        canvas.drawRoundRect(rect, 8f, 8f, paint);
        canvas.drawRoundRect(rect, 8f, 8f, stroke);

        rect.set(-r * 0.32f, r * 0.58f, r * 0.28f, r * 0.98f);
        canvas.drawRoundRect(rect, 8f, 8f, paint);
        canvas.drawRoundRect(rect, 8f, 8f, stroke);

        // Blue Head
        paint.setColor(Color.parseColor("#1D4ED8"));
        canvas.drawCircle(0f, 0f, r * 0.68f, paint);
        canvas.drawCircle(0f, 0f, r * 0.68f, stroke);
        canvas.restore();

        // Floating Shield (Blue) & Health (Green) Bars above player
        float barW = 92f;
        float barH = 9f;
        float barX = cx - barW / 2f;
        float shieldY = cy - r - 38f;
        float hpY = shieldY + barH + 6f;

        paint.setColor(Color.parseColor("#29B6F6"));
        canvas.drawRect(barX, shieldY, barX + barW, shieldY + barH, paint);
        paint.setColor(Color.parseColor("#4CD964"));
        canvas.drawRect(barX, hpY, barX + barW, hpY + barH, paint);

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
        if (hasFocus) {
            enforceStatusBarIcons();
        }
    }

    private void enforceStatusBarIcons() {
        getWindow().setStatusBarColor(bgColor);
        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decor);

        if (themeState == 0) {
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        } else {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        }
    }

    private void showInstructionsDialog(int themeState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        int dialogBgColor, dialogTextColor, dialogSubTextColor;

        if (themeState == 0) {
            dialogBgColor = Color.WHITE;
            dialogTextColor = Color.parseColor("#1C1C1E");
            dialogSubTextColor = Color.parseColor("#666666");
        } else if (themeState == 1) {
            dialogBgColor = Color.parseColor("#2C2C2E");
            dialogTextColor = Color.WHITE;
            dialogSubTextColor = Color.parseColor("#B0B0B8");
        } else {
            dialogBgColor = Color.parseColor("#1C1C1E");
            dialogTextColor = Color.WHITE;
            dialogSubTextColor = Color.parseColor("#B0B0B8");
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 60, 60, 60);

        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(dialogBgColor);
        bgGd.setCornerRadius(50f);
        layout.setBackground(bgGd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("How to Play");
        tvTitle.setTextSize(24f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(dialogTextColor);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, 40);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(
                "🌍 Explore Phase:\n" +
                        "Swipe to rotate the globe and double-tap to cycle through different planets. Enjoy the relaxing view of space!\n\n" +
                        "🛸 Alien Invasion:\n" +
                        "Ready for a challenge? Long-press the planet to trigger an invasion! Tap incoming UFOs to destroy them before they crash into the surface and take your lives.\n\n" +
                        "🛡️ Divine Shield:\n" +
                        "For every 25 points you score, you will unleash a majestic celestial shockwave that instantly destroys all nearby enemies."
        );
        tvMessage.setTextSize(15f);
        tvMessage.setTextColor(dialogSubTextColor);
        tvMessage.setLineSpacing(0, 1.3f);
        tvMessage.setPadding(0, 0, 0, 60);

        Button btnGotIt = new Button(this);
        btnGotIt.setText("Got it!");
        btnGotIt.setTextColor(Color.WHITE);
        btnGotIt.setAllCaps(false);
        btnGotIt.setTextSize(16f);
        btnGotIt.setTypeface(null, Typeface.BOLD);

        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setColor(Color.parseColor("#4A90E2"));
        btnGd.setCornerRadius(100f);
        btnGotIt.setBackground(btnGd);

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 130);
        btnGotIt.setLayoutParams(btnLp);

        layout.addView(tvTitle);
        layout.addView(tvMessage);
        layout.addView(btnGotIt);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}