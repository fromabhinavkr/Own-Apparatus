package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

@SuppressWarnings("all")
public class UtilitiesGalleryActivity extends AppCompatActivity {

    private LinearLayout root;
    private int revealX;
    private int revealY;

    // --- State variables ---
    private int themeState;
    private int bgColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // FIX: Keep the underlying window PERMANENTLY transparent to avoid animation glitching
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        }

        setContentView(R.layout.activity_utilities_gallery);

        // --- 3-STATE THEME SYNC LOGIC ---
        SharedPreferences prefs = getSharedPreferences("SnakeWidgetPrefs", MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean("is_dark_theme", true);
            themeState = oldDark ? 1 : 0;
        }

        // Define colors based on the 3-state theme
        final int cardColor;
        final int textColor;
        final int subtitleColor;
        final int divColor;

        if (themeState == 0) { // Light Mode (Pure White BG, Light Grey Cards)
            bgColor = Color.WHITE;
            cardColor = Color.parseColor("#F2F2F7");
            textColor = Color.parseColor("#333333");
            subtitleColor = Color.parseColor("#555555");
            divColor = Color.parseColor("#1A000000");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            textColor = Color.WHITE;
            subtitleColor = Color.parseColor("#AAAAAA");
            divColor = Color.parseColor("#33FFFFFF");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#1C1C1E");
            textColor = Color.WHITE;
            subtitleColor = Color.parseColor("#AAAAAA");
            divColor = Color.parseColor("#33FFFFFF");
        }

        root = findViewById(R.id.utilitiesGalleryRoot);
        TextView title = findViewById(R.id.tvUtilitiesTitle);
        TextView subtitle = findViewById(R.id.tvUtilitiesSubtitle);

        // Tool 1: Browser
        LinearLayout cardBrowser = findViewById(R.id.cardPrivateBrowser);
        TextView textBrowser = findViewById(R.id.textPrivateBrowser);
        View divBrowser = findViewById(R.id.divPrivateBrowser);

        // Tool 2: Doc Reader
        LinearLayout cardDocReader = findViewById(R.id.cardDocReader);
        TextView textDocReader = findViewById(R.id.textDocReader);
        View divDocReader = findViewById(R.id.divDocReader);

        // Tool 3: Slate
        LinearLayout cardSlate = findViewById(R.id.cardSlate);
        TextView textSlate = findViewById(R.id.textSlate);
        View divSlate = findViewById(R.id.divSlate);

        // Tool 4: Text Pad
        LinearLayout cardTextPad = findViewById(R.id.cardTextPad);
        TextView textTextPad = findViewById(R.id.textTextPad);
        View divTextPad = findViewById(R.id.divTextPad);

        // Apply Global Themes
        if (root != null) root.setBackgroundColor(bgColor);
        if (title != null) title.setTextColor(textColor);
        if (subtitle != null) subtitle.setTextColor(subtitleColor);

        // Apply Themes to Individual Cards
        if (cardBrowser != null) {
            cardBrowser.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textBrowser.setTextColor(textColor);
            divBrowser.setBackgroundColor(divColor);
        }

        if (cardDocReader != null) {
            cardDocReader.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textDocReader.setTextColor(textColor);
            divDocReader.setBackgroundColor(divColor);
        }

        if (cardSlate != null) {
            cardSlate.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textSlate.setTextColor(textColor);
            divSlate.setBackgroundColor(divColor);
        }

        if (cardTextPad != null) {
            cardTextPad.setBackgroundTintList(ColorStateList.valueOf(cardColor));
            textTextPad.setTextColor(textColor);
            divTextPad.setBackgroundColor(divColor);
        }

        // Apply Status Bar Icons immediately
        enforceStatusBarIcons();

        // --- Handle Circular Reveal Entry Animation ---
        Intent intent = getIntent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setVisibility(View.INVISIBLE);

            ViewTreeObserver viewTreeObserver = root.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        revealX = intent.getIntExtra("REVEAL_X", root.getWidth() / 2);
                        revealY = intent.getIntExtra("REVEAL_Y", root.getHeight() / 2);

                        // Use hypotenuse to ensure the circle covers the entire screen perfectly
                        float finalRadius = (float) Math.hypot(Math.max(revealX, root.getWidth() - revealX), Math.max(revealY, root.getHeight() - revealY));

                        Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, 0, finalRadius);
                        circularReveal.setDuration(400);
                        circularReveal.setInterpolator(new DecelerateInterpolator());

                        root.setVisibility(View.VISIBLE);
                        circularReveal.start();
                    }
                });
            }
        }

        // --- Handle Circular Reveal Exit Animation ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && root.isAttachedToWindow()) {

                    float startRadius = (float) Math.hypot(Math.max(revealX, root.getWidth() - revealX), Math.max(revealY, root.getHeight() - revealY));
                    Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, startRadius, 0);
                    circularReveal.setDuration(400);
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

        // Help Button Logic for Browser
        TextView btnBrowserHelp = findViewById(R.id.btnBrowserHelp);
        if (btnBrowserHelp != null) {
            GradientDrawable helpGd = new GradientDrawable();
            helpGd.setShape(GradientDrawable.OVAL);
            helpGd.setColor(themeState == 0 ? Color.parseColor("#007AFF") : Color.parseColor("#4A90E2"));

            btnBrowserHelp.setBackground(helpGd);
            btnBrowserHelp.setTextColor(Color.WHITE);

            btnBrowserHelp.setOnClickListener(v -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(UtilitiesGalleryActivity.this, R.style.ModernDialogStyle);
                builder.setTitle("Privacy & Network Notice");

                LinearLayout dialogLayout = new LinearLayout(UtilitiesGalleryActivity.this);
                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                dialogLayout.setPadding(60, 40, 60, 40);

                TextView message = new TextView(UtilitiesGalleryActivity.this);
                message.setText("This tool requires an active Internet connection to function. In accordance with strict privacy standards, Own does not collect, track, or store any of your browsing history, personal data, or usage metrics.");
                message.setTextColor(textColor);
                message.setTextSize(16f);
                message.setLineSpacing(0, 1.2f);
                dialogLayout.addView(message);

                builder.setView(dialogLayout);
                builder.setPositiveButton("Understood", null);

                AlertDialog dialog = builder.create();
                dialog.setOnShowListener(d -> {
                    if (dialog.getWindow() != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setColor(cardColor);
                        gd.setCornerRadius(60f);
                        dialog.getWindow().getDecorView().setBackground(gd);

                        int titleId = UtilitiesGalleryActivity.this.getResources().getIdentifier("alertTitle", "id", "android");
                        TextView titleView = dialog.findViewById(titleId);
                        if (titleView != null) titleView.setTextColor(textColor);

                        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#4A90E2"));
                        }
                    }
                });
                dialog.show();
            });
        }

        // --- Click Listeners to Launch Activities (UPDATED for exact touch location mapping) ---
        if (cardBrowser != null) {
            cardBrowser.setOnClickListener(v -> launchWithReveal(v, PrivateBrowserActivity.class));
        }
        if (cardDocReader != null) {
            cardDocReader.setOnClickListener(v -> launchWithReveal(v, DocReaderActivity.class));
        }
        if (cardSlate != null) {
            cardSlate.setOnClickListener(v -> launchWithReveal(v, SlateActivity.class));
        }
        if (cardTextPad != null) {
            cardTextPad.setOnClickListener(v -> launchWithReveal(v, TextPadActivity.class));
        }
    }

    private void launchWithReveal(View view, Class<?> targetActivity) {
        Intent intent = new Intent(UtilitiesGalleryActivity.this, targetActivity);
        int[] location = new int[2];
        view.getLocationOnScreen(location);

        // Pass the exact center of the clicked card
        intent.putExtra("REVEAL_X", location[0] + view.getWidth() / 2);
        intent.putExtra("REVEAL_Y", location[1] + view.getHeight() / 2);

        startActivity(intent);
        overridePendingTransition(0, 0); // Kills default slide transition
    }

    // --- Smart Lifecycle Interceptor ---
    @Override
    protected void onResume() {
        super.onResume();
        // Force the window to remain transparent to override any MainApplication callbacks seamlessly
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

    // Method to forcibly correct the icons for Light/Dark/Star mode safely
    private void enforceStatusBarIcons() {
        // Keep the main window background untouched (transparent), ONLY tint the status bar area
        getWindow().setStatusBarColor(bgColor);

        View decor = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), decor);

        if (themeState == 0) { // Light Mode -> Icons MUST be Dark
            controller.setAppearanceLightStatusBars(true);
            controller.setAppearanceLightNavigationBars(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        } else { // Dark or Star Mode -> Icons MUST be White
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getWindow().setNavigationBarColor(bgColor);
            }
        }
    }
}