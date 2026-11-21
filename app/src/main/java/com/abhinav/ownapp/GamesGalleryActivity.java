package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
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
    private boolean isExiting = false;

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
        final int cardStrokeColor;
        final int rippleColor;
        final int textColor;
        final int subTextColor;
        final int cardTextColor;
        final int cardHelpColor;
        final int badgeBgColor;
        final int dividerColor;
        final int retroAccentColor = Color.parseColor("#FF9F0A");   // Warm Amber for Retro
        final int specialAccentColor = Color.parseColor("#5E5CE6"); // Vibrant Indigo for Special

        if (themeState == 0) { // Light Mode (Pure White Layout + Dark Grey Cards)
            bgColor = Color.WHITE;
            cardColor = Color.parseColor("#7FA4C4");
            cardStrokeColor = Color.parseColor("#FFFFFF");
            rippleColor = Color.parseColor("#26FFFFFF");
            textColor = Color.parseColor("#1C1C1E");
            subTextColor = Color.parseColor("#6B7280");
            cardTextColor = Color.WHITE;
            cardHelpColor = Color.parseColor("#D1D1D6");
            badgeBgColor = Color.parseColor("#EBEBF0");
            dividerColor = Color.parseColor("#E5E7EB");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#161618");
            cardColor = Color.parseColor("#242426");
            cardStrokeColor = Color.parseColor("#343438");
            rippleColor = Color.parseColor("#26FFFFFF");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#98989F");
            cardTextColor = Color.WHITE;
            cardHelpColor = Color.parseColor("#98989F");
            badgeBgColor = Color.parseColor("#2C2C2E");
            dividerColor = Color.parseColor("#2C2C2E");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#141416");
            cardStrokeColor = Color.parseColor("#26262A");
            rippleColor = Color.parseColor("#26FFFFFF");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            cardTextColor = Color.WHITE;
            cardHelpColor = Color.parseColor("#8E8E93");
            badgeBgColor = Color.parseColor("#1C1C1E");
            dividerColor = Color.parseColor("#1F1F22");
        }

        root = findViewById(R.id.gamesGalleryRoot);
        TextView title = findViewById(R.id.tvGamesTitle);
        TextView subtitle = findViewById(R.id.tvGamesSubtitle);

        // Section Header Views
        View accentRetro = findViewById(R.id.accentRetro);
        TextView tvSectionRetro = findViewById(R.id.tvSectionRetro);
        TextView badgeRetro = findViewById(R.id.badgeRetro);

        View dividerSections = findViewById(R.id.dividerSections);

        View accentSpecial = findViewById(R.id.accentSpecial);
        TextView tvSectionSpecial = findViewById(R.id.tvSectionSpecial);
        TextView badgeSpecial = findViewById(R.id.badgeSpecial);

        // Apply Root & Header Theming
        if (root != null) root.setBackgroundColor(bgColor);
        if (title != null) title.setTextColor(textColor);
        if (subtitle != null) subtitle.setTextColor(subTextColor);

        styleSectionHeader(accentRetro, tvSectionRetro, badgeRetro, retroAccentColor, textColor, subTextColor, badgeBgColor);
        styleSectionHeader(accentSpecial, tvSectionSpecial, badgeSpecial, specialAccentColor, textColor, subTextColor, badgeBgColor);
        if (dividerSections != null) dividerSections.setBackgroundColor(dividerColor);

        enforceStatusBarIcons();
        setupCircularReveal(savedInstanceState);
        setupBackNavigation();

        // ==================== RETRO GAMES ====================
        bindGameCard(R.id.cardSnakeGame, R.id.textSnakeGame, cardColor, cardStrokeColor, rippleColor, cardTextColor, SnakeGameActivity.class);
        bindGameCard(R.id.cardTetris, R.id.textTetris, cardColor, cardStrokeColor, rippleColor, cardTextColor, TetrisActivity.class);
        bindGameCard(R.id.cardMazeBall, R.id.textMazeBall, cardColor, cardStrokeColor, rippleColor, cardTextColor, MazeBallActivity.class);
        bindGameCard(R.id.cardBreakout, R.id.textBreakout, cardColor, cardStrokeColor, rippleColor, cardTextColor, BreakoutActivity.class);

        // ==================== SPECIAL GAMES ====================
        // 1. Rag Thrive
        bindGameCard(R.id.cardThrive, R.id.textThrive, cardColor, cardStrokeColor, rippleColor, cardTextColor, ThriveActivity.class);
        ImageView imgThrive = findViewById(R.id.imgThrive);
        if (imgThrive != null) {
            @SuppressLint("DiscouragedApi")
            int customThriveId = getResources().getIdentifier("thrive_preview", "drawable", getPackageName());
            if (customThriveId != 0) {
                imgThrive.setImageResource(customThriveId);
            } else {
                imgThrive.setImageBitmap(createThrivePreviewBitmap());
            }
        }

        // 2. Flappy Bird
        bindGameCard(R.id.cardFlappyBird, R.id.textFlappyBird, cardColor, cardStrokeColor, rippleColor, cardTextColor, FlappyBirdActivity.class);

        // 3. DriveOn
        bindGameCard(R.id.cardDriveOn, R.id.textDriveOn, cardColor, cardStrokeColor, rippleColor, cardTextColor, DriveOnActivity.class);
        ImageView imgDriveOn = findViewById(R.id.imgDriveOn);
        if (imgDriveOn != null) {
            @SuppressLint("DiscouragedApi")
            int customDriveOnId = getResources().getIdentifier("driveonicon", "drawable", getPackageName());
            if (customDriveOnId != 0) {
                imgDriveOn.setImageResource(customDriveOnId);
            } else {
                imgDriveOn.setImageBitmap(createDriveOnPreviewBitmap());
            }
        }

        // 4. My Planet (with Help button)
        bindGameCard(R.id.cardMyPlanet, R.id.textMyPlanet, cardColor, cardStrokeColor, rippleColor, cardTextColor, GlobeGameActivity.class);
        TextView btnHelpMyPlanet = findViewById(R.id.btnHelpMyPlanet);
        if (btnHelpMyPlanet != null) {
            GradientDrawable helpBg = new GradientDrawable();
            helpBg.setShape(GradientDrawable.OVAL);
            helpBg.setStroke(dp(1.5f), cardHelpColor);
            btnHelpMyPlanet.setTextColor(cardHelpColor);
            btnHelpMyPlanet.setBackground(helpBg);
            final int finalThemeState = themeState;
            btnHelpMyPlanet.setOnClickListener(v -> showInstructionsDialog(finalThemeState));
        }
    }

    private void bindGameCard(int cardId, int titleId, int cardColor, int strokeColor, int rippleColor, int textColor, Class<?> targetActivity) {
        View card = findViewById(cardId);
        TextView label = findViewById(titleId);
        if (card != null) {
            card.setBackground(createCardBg(cardColor, strokeColor, rippleColor));
            card.setOnClickListener(v -> startActivity(new Intent(GamesGalleryActivity.this, targetActivity)));
        }
        if (label != null) {
            label.setTextColor(textColor);
        }
    }

    private void styleSectionHeader(View accentBar, TextView titleView, TextView badgeView,
                                    int accentColor, int textColor, int subTextColor, int badgeBgColor) {
        if (accentBar != null) {
            GradientDrawable barBg = new GradientDrawable();
            barBg.setColor(accentColor);
            barBg.setCornerRadius(dp(4));
            accentBar.setBackground(barBg);
        }
        if (titleView != null) {
            titleView.setTextColor(textColor);
        }
        if (badgeView != null) {
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(badgeBgColor);
            pill.setCornerRadius(dp(12));
            badgeView.setBackground(pill);
            badgeView.setTextColor(subTextColor);
        }
    }

    private Drawable createCardBg(int cardColor, int strokeColor, int rippleColor) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(cardColor);
        content.setCornerRadius(dp(16));
        content.setStroke(dp(1), strokeColor);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(Color.WHITE);
            mask.setCornerRadius(dp(16));
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
        }
        return content;
    }

    private void setupCircularReveal(Bundle savedInstanceState) {
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
                            if (isFinishing() || isDestroyed() || !root.isAttachedToWindow()) return;
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
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isExiting) return;
                isExiting = true;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                        && root != null && root.isAttachedToWindow()
                        && root.getWidth() > 0 && root.getHeight() > 0) {
                    int exitX = (revealX > 0 && revealX <= root.getWidth()) ? revealX : root.getWidth() / 2;
                    int exitY = (revealY > 0 && revealY <= root.getHeight()) ? revealY : root.getHeight() / 2;
                    float startRadius = (float) Math.hypot(root.getWidth(), root.getHeight());
                    Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, exitX, exitY, startRadius, 0);
                    circularReveal.setDuration(320);
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
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private Bitmap createThrivePreviewBitmap() {
        int w = 400, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        RectF cardRect = new RectF(0, 0, w, h);
        Path clipPath = new Path();
        clipPath.addRoundRect(cardRect, 44f, 44f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        paint.setColor(Color.parseColor("#112218"));
        canvas.drawRect(cardRect, paint);
        paint.setColor(Color.parseColor("#1A3023"));
        paint.setStrokeWidth(3f);
        for (int gx = 80; gx < w; gx += 80) canvas.drawLine(gx, 0, gx, h, paint);
        for (int gy = 80; gy < h; gy += 80) canvas.drawLine(0, gy, w, gy, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1D9A50"));
        canvas.drawCircle(35f, h * 0.22f, 68f, paint);
        canvas.drawCircle(w * 0.82f, 28f, 64f, paint);

        canvas.save();
        canvas.translate(w * 0.42f, h * 0.58f);
        stroke.setColor(Color.parseColor("#0D1B2A"));
        stroke.setStrokeWidth(4.5f);
        paint.setColor(Color.parseColor("#1D4ED8"));
        canvas.drawCircle(0f, 0f, 28f, paint);
        canvas.drawCircle(0f, 0f, 28f, stroke);
        canvas.restore();
        return bmp;
    }

    private Bitmap createDriveOnPreviewBitmap() {
        int w = 400, h = 300;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF cardRect = new RectF(0, 0, w, h);
        Path clipPath = new Path();
        clipPath.addRoundRect(cardRect, 44f, 44f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        paint.setColor(Color.parseColor("#88C478"));
        canvas.drawRect(cardRect, paint);
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
        boolean isLight = (themeState == 0);
        controller.setAppearanceLightStatusBars(isLight);
        controller.setAppearanceLightNavigationBars(isLight);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(bgColor);
        }
    }

    private void showInstructionsDialog(int themeState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        int dialogBgColor, dialogTextColor, dialogSubTextColor;
        if (themeState == 0) {
            dialogBgColor = Color.WHITE;
            dialogTextColor = Color.parseColor("#1C1C1E");
            dialogSubTextColor = Color.parseColor("#666666");
        } else {
            dialogBgColor = Color.parseColor("#242426");
            dialogTextColor = Color.WHITE;
            dialogSubTextColor = Color.parseColor("#B0B0B8");
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        layout.setPadding(pad, pad, pad, pad);
        GradientDrawable bgGd = new GradientDrawable();
        bgGd.setColor(dialogBgColor);
        bgGd.setCornerRadius(dp(22));
        layout.setBackground(bgGd);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("How to Play");
        tvTitle.setTextSize(22f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(dialogTextColor);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(16));

        TextView tvMessage = new TextView(this);
        tvMessage.setText(
                "🌍 Explore Phase:\n" +
                        "Swipe to rotate the globe and double-tap to cycle through different planets. Enjoy the relaxing view of space!\n\n" +
                        "🛸 Alien Invasion:\n" +
                        "Ready for a challenge? Long-press the planet to trigger an invasion! Tap incoming UFOs to destroy them before they crash into the surface and take your lives.\n\n" +
                        "🛡️ Divine Shield:\n" +
                        "For every 25 points you score, you will unleash a majestic celestial shockwave that instantly destroys all nearby enemies."
        );
        tvMessage.setTextSize(14.5f);
        tvMessage.setTextColor(dialogSubTextColor);
        tvMessage.setLineSpacing(0, 1.3f);
        tvMessage.setPadding(0, 0, 0, dp(22));

        Button btnGotIt = new Button(this);
        btnGotIt.setText("Got it!");
        btnGotIt.setTextColor(Color.WHITE);
        btnGotIt.setAllCaps(false);
        btnGotIt.setTextSize(16f);
        btnGotIt.setTypeface(null, Typeface.BOLD);
        GradientDrawable btnGd = new GradientDrawable();
        btnGd.setColor(Color.parseColor("#4A90E2"));
        btnGd.setCornerRadius(dp(24));
        btnGotIt.setBackground(btnGd);
        btnGotIt.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        layout.addView(tvTitle);
        layout.addView(tvMessage);
        layout.addView(btnGotIt);

        AlertDialog dialog = builder.setView(layout).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        btnGotIt.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}