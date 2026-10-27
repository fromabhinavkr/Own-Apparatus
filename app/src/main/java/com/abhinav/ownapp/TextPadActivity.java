package com.abhinav.ownapp;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class TextPadActivity extends AppCompatActivity {
    // 0 = Light, 1 = Dark, 2 = AMOLED
    private int themeState = 1;
    private boolean isDarkTheme = true, isMenuOpen = false, isHdlDetEnabled = false;
    private SharedPreferences prefs;
    private LinearLayout flowerMenuLayout, loadingOverlay;
    private ScrollView textScrollView;
    private EditText ideEditText;
    private TextView btnRefreshHdl;
    private VerilogHighlighter syntaxHighlighter;
    private final String indicatorOn = " <font color='#34C759'>●</font>";
    private final String indicatorOff = " <font color='#FF3B30'>●</font>";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_textpad);

        View root = findViewById(R.id.textPadRoot);
        ideEditText = findViewById(R.id.ideEditText);
        textScrollView = findViewById(R.id.textScrollView);
        btnRefreshHdl = findViewById(R.id.btnRefreshHdl);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        flowerMenuLayout = findViewById(R.id.flowerMenuLayout);

        ideEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000000)});

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        loadThemeStateFromPrefs();

        syntaxHighlighter = new VerilogHighlighter();
        ideEditText.addTextChangedListener(syntaxHighlighter);

        // Apply window, status bar, and view colors BEFORE starting the scale animation to prevent border/status bar glitching
        updateTheme();

        root.setAlpha(0f);
        root.setScaleX(0.90f);
        root.setScaleY(0.90f);
        root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        TextView btnFlowerMenu = findViewById(R.id.btnFlowerMenu);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        });
        btnFlowerMenu.setOnClickListener(v -> toggleMenu(!isMenuOpen, btnFlowerMenu));

        btnRefreshHdl.setOnClickListener(v -> syntaxHighlighter.forceRehighlight());

        TextView menuHdlDet = findViewById(R.id.menuHdlDet);
        menuHdlDet.setOnClickListener(v -> {
            isHdlDetEnabled = !isHdlDetEnabled;
            updateToolIndicators(menuHdlDet);

            btnRefreshHdl.setVisibility(isHdlDetEnabled ? View.VISIBLE : View.GONE);

            if (isHdlDetEnabled) {
                showHdlUsageDialog();
            }

            syntaxHighlighter.forceRehighlight();
            toggleMenu(false, btnFlowerMenu);
        });

        findViewById(R.id.menuClear).setOnClickListener(v -> {
            ideEditText.setText("");
            toggleMenu(false, btnFlowerMenu);
        });
        findViewById(R.id.menuSave).setOnClickListener(v -> {
            toggleMenu(false, btnFlowerMenu);
            showSaveDraftDialog();
        });
        findViewById(R.id.menuDrafts).setOnClickListener(v -> {
            toggleMenu(false, btnFlowerMenu);
            showDraftsGalleryDialog();
        });
        findViewById(R.id.menuTheme).setOnClickListener(v -> {
            // Cycle through 0 (Light) -> 1 (Dark) -> 2 (AMOLED)
            themeState = (themeState + 1) % 3;
            isDarkTheme = (themeState != 0);
            prefs.edit()
                    .putInt("app_theme_state", themeState)
                    .putBoolean(SnakeWidget.PREF_IS_DARK, isDarkTheme)
                    .apply();
            updateTheme();
            toggleMenu(false, btnFlowerMenu);
        });
        findViewById(R.id.menuExit).setOnClickListener(v -> {
            toggleMenu(false, btnFlowerMenu);
            showExitConfirmationDialog();
        });

        updateToolIndicators(menuHdlDet);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            int previousState = themeState;
            loadThemeStateFromPrefs();
            if (previousState != themeState) {
                updateTheme();
            } else {
                applyWindowAndStatusBarTheme();
            }
        }
    }

    private void loadThemeStateFromPrefs() {
        if (prefs.contains("app_theme_state")) {
            themeState = prefs.getInt("app_theme_state", 1);
        } else {
            boolean legacyDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = legacyDark ? 1 : 0;
        }
        isDarkTheme = (themeState != 0);
    }

    private int getThemeBackgroundColor() {
        if (themeState == 0) return Color.WHITE;
        if (themeState == 1) return Color.parseColor("#1C1C1E");
        return Color.BLACK; // AMOLED (#000000)
    }

    private int getDialogBackgroundColor() {
        if (themeState == 0) return Color.parseColor("#E6F2F2F7");
        if (themeState == 1) return Color.parseColor("#E61C1C1E");
        return Color.parseColor("#F20C0C0E"); // AMOLED dialog surface
    }

    private int getCardBackgroundColor() {
        if (themeState == 0) return Color.parseColor("#FFFFFF");
        if (themeState == 1) return Color.parseColor("#3A3A3C");
        return Color.parseColor("#1C1C1E"); // AMOLED input/card surface
    }

    private void applyWindowAndStatusBarTheme() {
        Window window = getWindow();
        if (window == null) return;

        int bgColor = getThemeBackgroundColor();

        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            layoutParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(layoutParams);
        }

        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        window.setBackgroundDrawable(new ColorDrawable(bgColor));

        WindowInsetsControllerCompat windowController = WindowCompat.getInsetsController(window, window.getDecorView());
        if (windowController != null) {
            boolean isLight = (themeState == 0);
            windowController.setAppearanceLightStatusBars(isLight);
            windowController.setAppearanceLightNavigationBars(isLight);
        }
    }

    // Forces a dialog to expand to 90% of the screen width for a clean, non-congested look
    private void expandDialogWidth(Dialog d) {
        if (d.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            d.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void updateToolIndicators(TextView hdlBtn) {
        hdlBtn.setText(android.text.Html.fromHtml("HDL Det" + (isHdlDetEnabled ? indicatorOn : indicatorOff), android.text.Html.FROM_HTML_MODE_LEGACY));
    }

    private void toggleMenu(boolean open, TextView btn) {
        if (isMenuOpen == open) return;
        isMenuOpen = open;
        btn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(120).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
            btn.setText(open ? "Close" : "Menu");
            btn.animate().scaleX(1.10f).scaleY(1.10f).setDuration(150).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(350).setInterpolator(new OvershootInterpolator(1.2f)).start();
            }).start();
        }).start();

        if (open) {
            flowerMenuLayout.setVisibility(View.VISIBLE);
            for (int i = 0; i < flowerMenuLayout.getChildCount(); i++) {
                View c = flowerMenuLayout.getChildAt(i);
                c.setTranslationY(100f);
                c.setTranslationX(40f);
                c.setAlpha(0f);
                c.setScaleX(0.5f);
                c.setScaleY(0.5f);
                c.animate().translationY(0f).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(350)
                        .setStartDelay((flowerMenuLayout.getChildCount() - i - 1) * 35L)
                        .setInterpolator(new OvershootInterpolator(1.2f))
                        .start();
            }
        } else {
            for (int i = 0; i < flowerMenuLayout.getChildCount(); i++) {
                View c = flowerMenuLayout.getChildAt(i);
                c.animate().translationY(100f).translationX(40f).alpha(0f).scaleX(0.5f).scaleY(0.5f)
                        .setDuration(250)
                        .setStartDelay(i * 25L)
                        .setInterpolator(new android.view.animation.AnticipateInterpolator(1.2f))
                        .withEndAction(() -> {
                            if (c == flowerMenuLayout.getChildAt(flowerMenuLayout.getChildCount() - 1)) {
                                flowerMenuLayout.setVisibility(View.GONE);
                            }
                        }).start();
            }
        }
    }

    private void updateTheme() {
        isDarkTheme = (themeState != 0);
        int bgColor = getThemeBackgroundColor();
        int textColor = isDarkTheme ? Color.parseColor("#D4D4D4") : Color.parseColor("#333333");
        int hintColor = isDarkTheme ? Color.parseColor("#6A9955") : Color.parseColor("#008000");

        int btnColor;
        if (themeState == 0) {
            btnColor = Color.parseColor("#E6F2F2F7");
        } else if (themeState == 1) {
            btnColor = Color.parseColor("#E62C2C2E");
        } else {
            btnColor = Color.parseColor("#E6161618");
        }
        int btnText = isDarkTheme ? Color.WHITE : Color.BLACK;

        // Sync Window & Status Bar first
        applyWindowAndStatusBarTheme();

        // Sync Root & Make Scroll/EditText containers transparent so the entire surface matches the status bar
        View root = findViewById(R.id.textPadRoot);
        if (root != null) {
            root.setBackgroundColor(bgColor);
        }
        if (textScrollView != null) {
            textScrollView.setBackgroundColor(Color.TRANSPARENT);
        }
        if (ideEditText != null) {
            ideEditText.setBackgroundColor(Color.TRANSPARENT);
            if (ideEditText.getParent() instanceof View) {
                ((View) ideEditText.getParent()).setBackgroundColor(Color.TRANSPARENT);
            }
            ideEditText.setTextColor(textColor);
            ideEditText.setHintTextColor(hintColor);
        }

        if (loadingOverlay != null) {
            GradientDrawable ldGd = new GradientDrawable();
            ldGd.setColor(isDarkTheme ? Color.parseColor("#CC1C1C1E") : Color.parseColor("#CC333333"));
            ldGd.setCornerRadius(40f);
            loadingOverlay.setBackground(ldGd);
        }

        if (btnRefreshHdl != null) {
            GradientDrawable refGd = new GradientDrawable();
            refGd.setColor(btnColor);
            refGd.setShape(GradientDrawable.OVAL);
            if (themeState == 2) {
                refGd.setStroke(2, Color.parseColor("#333333"));
            }
            btnRefreshHdl.setBackground(refGd);
            btnRefreshHdl.setTextColor(btnText);
        }

        if (syntaxHighlighter != null) {
            syntaxHighlighter.updateColors(isDarkTheme);
            syntaxHighlighter.forceRehighlight();
        }

        int[] btns = {R.id.menuHdlDet, R.id.menuSave, R.id.menuDrafts, R.id.menuTheme, R.id.menuClear, R.id.menuExit};
        for (int id : btns) {
            TextView b = findViewById(id);
            if (b != null) {
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(btnColor);
                gd.setCornerRadius(60f);
                if (themeState == 2) {
                    gd.setStroke(2, Color.parseColor("#333333"));
                }
                b.setBackground(gd);
                b.setTextColor(btnText);
            }
        }
    }

    private void saveTxlToFolder(String name, String content) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".txl");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/OWN's Text Pad Drafts");
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri != null) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                out.write(content.getBytes());
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteTxlFromFolder(String name) {
        try {
            ContentResolver r = getContentResolver();
            Uri uri = MediaStore.Files.getContentUri("external");
            Cursor c = r.query(uri, new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{name + ".txl"}, null);
            if (c != null) {
                while (c.moveToNext()) {
                    r.delete(android.content.ContentUris.withAppendedId(uri, c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))), null, null);
                }
                c.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showHdlUsageDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 60, 60, 60);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getDialogBackgroundColor());
        gd.setCornerRadius(60f);
        if (themeState == 2) gd.setStroke(2, Color.parseColor("#2C2C2E"));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("Usage");
        title.setTextSize(20f);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("This utility is designed specifically for detecting and dynamically highlighting Verilog and SystemVerilog HDL.\n\nCode coloring automatically tracks your screen as you type. If you scroll to a new massive section, simply tap the Circular '↻' button in the top right to instantly force a render.");
        msg.setTextColor(isDarkTheme ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555"));
        msg.setPadding(0, 20, 0, 40);
        msg.setLineSpacing(0, 1.2f);
        root.addView(msg);

        TextView btnOk = new TextView(this);
        btnOk.setText("Understood");
        btnOk.setGravity(Gravity.CENTER);
        btnOk.setPadding(0, 30, 0, 30);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#4A90E2"));
        btnBg.setCornerRadius(30f);
        btnOk.setBackground(btnBg);
        btnOk.setTextColor(Color.WHITE);
        btnOk.setOnClickListener(v -> d.dismiss());
        root.addView(btnOk);

        d.setContentView(root);
        d.show();
        expandDialogWidth(d);
    }

    private void showExitConfirmationDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 60, 60, 60);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getDialogBackgroundColor());
        gd.setCornerRadius(60f);
        if (themeState == 2) gd.setStroke(2, Color.parseColor("#2C2C2E"));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("Exit Text Pad?");
        title.setTextSize(20f);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView msg = new TextView(this);
        msg.setText("Are you sure? Unsaved code will be lost.");
        msg.setTextColor(isDarkTheme ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555"));
        msg.setPadding(0, 20, 0, 40);
        root.addView(msg);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnNo = new TextView(this);
        btnNo.setText("No");
        btnNo.setGravity(Gravity.CENTER);
        btnNo.setPadding(0, 30, 0, 30);
        GradientDrawable noBg = new GradientDrawable();
        noBg.setColor(Color.GRAY);
        noBg.setCornerRadius(30f);
        btnNo.setBackground(noBg);
        btnNo.setTextColor(Color.WHITE);
        btnNo.setOnClickListener(v -> d.dismiss());

        TextView btnYes = new TextView(this);
        btnYes.setText("Yes");
        btnYes.setGravity(Gravity.CENTER);
        btnYes.setPadding(0, 30, 0, 30);
        GradientDrawable yesBg = new GradientDrawable();
        yesBg.setColor(Color.parseColor("#FF3B30"));
        yesBg.setCornerRadius(30f);
        btnYes.setBackground(yesBg);
        btnYes.setTextColor(Color.WHITE);
        btnYes.setOnClickListener(v -> {
            d.dismiss();
            finish();
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(10, 0, 10, 0);
        btnLayout.addView(btnNo, p);
        btnLayout.addView(btnYes, p);
        root.addView(btnLayout);

        d.setContentView(root);
        d.show();
        expandDialogWidth(d);
    }

    private void showSaveDraftDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 60, 60, 60);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getDialogBackgroundColor());
        gd.setCornerRadius(60f);
        if (themeState == 2) gd.setStroke(2, Color.parseColor("#2C2C2E"));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("Save .txl Document");
        title.setTextSize(20f);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        EditText input = new EditText(this);
        input.setHint("Enter document name...");
        input.setHintTextColor(Color.GRAY);
        input.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(getCardBackgroundColor());
        inputBg.setCornerRadius(20f);
        input.setBackground(inputBg);
        input.setPadding(30, 30, 30, 30);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, 30, 0, 30);
        root.addView(input, ip);

        LinearLayout btnLayout = new LinearLayout(this);
        btnLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("Cancel");
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setPadding(0, 30, 0, 30);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.GRAY);
        cBg.setCornerRadius(30f);
        btnCancel.setBackground(cBg);
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setOnClickListener(v -> d.dismiss());

        TextView btnSave = new TextView(this);
        btnSave.setText("Save");
        btnSave.setGravity(Gravity.CENTER);
        btnSave.setPadding(0, 30, 0, 30);
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.parseColor("#4A90E2"));
        sBg.setCornerRadius(30f);
        btnSave.setBackground(sBg);
        btnSave.setTextColor(Color.WHITE);

        btnSave.setOnClickListener(v -> {
            String n = input.getText().toString().trim();
            if (n.isEmpty()) n = "Doc_" + System.currentTimeMillis();
            String code = ideEditText.getText().toString();
            prefs.edit().putString("textpad_draft_" + n, code).apply();
            Set<String> s = new HashSet<>(prefs.getStringSet("textpad_draft_names", new HashSet<>()));
            s.add(n);
            prefs.edit().putStringSet("textpad_draft_names", s).apply();
            saveTxlToFolder(n, code);
            Toast.makeText(this, "Saved .txl to Storage!", Toast.LENGTH_SHORT).show();
            d.dismiss();
        });

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(10, 0, 10, 0);
        btnLayout.addView(btnCancel, p);
        btnLayout.addView(btnSave, p);
        root.addView(btnLayout);

        d.setContentView(root);
        d.show();
        expandDialogWidth(d);
    }

    private void showDraftsGalleryDialog() {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 60, 60, 60);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getDialogBackgroundColor());
        gd.setCornerRadius(60f);
        if (themeState == 2) gd.setStroke(2, Color.parseColor("#2C2C2E"));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("Documents (.txl)");
        title.setTextSize(20f);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, 40);
        root.addView(title);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Set<String> names = new HashSet<>(prefs.getStringSet("textpad_draft_names", new HashSet<>()));
        if (names.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No .txl documents saved.");
            empty.setTextColor(Color.GRAY);
            list.addView(empty);
        } else {
            for (String n : names) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, 20);

                TextView b = new TextView(this);
                b.setText(n + ".txl");
                b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                b.setPadding(40, 30, 20, 30);
                b.setSingleLine(true);
                b.setEllipsize(TextUtils.TruncateAt.END);

                GradientDrawable bBg = new GradientDrawable();
                bBg.setColor(getCardBackgroundColor());
                bBg.setCornerRadius(30f);
                b.setBackground(bBg);
                b.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMarginEnd(16);
                b.setOnClickListener(v -> {
                    ideEditText.setText(prefs.getString("textpad_draft_" + n, ""));
                    Toast.makeText(this, "Loaded " + n + ".txl", Toast.LENGTH_SHORT).show();
                    d.dismiss();
                });

                TextView del = new TextView(this);
                del.setText("X");
                del.setGravity(Gravity.CENTER);
                del.setPadding(0, 30, 0, 30);
                GradientDrawable dBg = new GradientDrawable();
                dBg.setColor(Color.parseColor("#FF3B30"));
                dBg.setCornerRadius(30f);
                del.setBackground(dBg);
                del.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(120, LinearLayout.LayoutParams.WRAP_CONTENT);
                del.setOnClickListener(v -> {
                    prefs.edit().remove("textpad_draft_" + n).apply();
                    Set<String> updated = new HashSet<>(prefs.getStringSet("textpad_draft_names", new HashSet<>()));
                    updated.remove(n);
                    prefs.edit().putStringSet("textpad_draft_names", updated).apply();
                    list.removeView(row);
                    deleteTxlFromFolder(n);
                    if (updated.isEmpty()) {
                        TextView empty = new TextView(this);
                        empty.setText("No documents saved.");
                        empty.setTextColor(Color.GRAY);
                        list.addView(empty);
                    }
                    Toast.makeText(this, "Document Deleted", Toast.LENGTH_SHORT).show();
                });
                row.addView(b, lp);
                row.addView(del, dLp);
                list.addView(row, rowLp);
            }
        }
        sv.addView(list);
        root.addView(sv);

        TextView btnClose = new TextView(this);
        btnClose.setText("Close");
        btnClose.setGravity(Gravity.CENTER);
        btnClose.setPadding(0, 30, 0, 30);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(Color.parseColor("#FF3B30"));
        cBg.setCornerRadius(30f);
        btnClose.setBackground(cBg);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 40, 0, 0);
        root.addView(btnClose, clp);

        d.setContentView(root);
        d.show();
        expandDialogWidth(d);
    }

    // --- DYNAMIC SCROLL VIEWPORT HIGHLIGHTER ---
    private class VerilogHighlighter implements TextWatcher {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private Runnable pendingTask;
        private boolean isFormatting = false;
        private int parseId = 0;
        private int colorKeyword, colorType, colorNumber, colorComment, colorString;

        private final Pattern PATTERN_COMMENTS = Pattern.compile("//[^\\n]*|/\\*[\\s\\S]*?\\*/");
        private final Pattern PATTERN_STRINGS = Pattern.compile("\".*?\"");
        private final Pattern PATTERN_NUMBERS = Pattern.compile("\\b\\d+'[bBoOdDhH][0-9a-fA-FxXzZ_]+\\b|\\b\\d+\\b");
        private final Pattern PATTERN_TYPES = Pattern.compile("\\b(wire|reg|integer|genvar|logic|bit|byte|int|string|time|real)\\b");
        private final Pattern PATTERN_KEYWORD = Pattern.compile("\\b(module|endmodule|input|output|inout|assign|always|always_comb|always_ff|always_latch|begin|end|if|else|case|endcase|parameter|localparam|for|generate|endgenerate|initial|posedge|negedge|or|and|class|endclass|function|endfunction|task|endtask|package|endpackage|import|export|enum|typedef|struct|union|virtual|extends|implements|interface|endinterface|clocking|endclocking|property|endproperty|assert|assume|cover|sequence|rand|randc|constraint)\\b");

        private class SpanDef {
            int start, end, color;
            SpanDef(int s, int e, int c) { start = s; end = e; color = c; }
        }

        public void updateColors(boolean isDark) {
            colorKeyword = isDark ? Color.parseColor("#C586C0") : Color.parseColor("#AF00DB");
            colorType = isDark ? Color.parseColor("#569CD6") : Color.parseColor("#0000FF");
            colorNumber = isDark ? Color.parseColor("#B5CEA8") : Color.parseColor("#098658");
            colorComment = isDark ? Color.parseColor("#6A9955") : Color.parseColor("#008000");
            colorString = isDark ? Color.parseColor("#CE9178") : Color.parseColor("#A31515");
        }

        public void forceRehighlight() {
            triggerHighlighting();
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            triggerHighlighting();
        }

        public void triggerHighlighting() {
            Editable s = ideEditText.getText();
            if (isFormatting || s == null) return;
            if (pendingTask != null) handler.removeCallbacks(pendingTask);

            if (!isHdlDetEnabled || s.length() == 0) {
                isFormatting = true;
                clearSpans(s, 0, s.length());
                isFormatting = false;
                return;
            }

            int winStart = 0;
            int winEnd = s.length();

            Layout layout = ideEditText.getLayout();
            if (layout != null) {
                int scrollY = textScrollView.getScrollY();
                int height = textScrollView.getHeight();
                int firstLine = layout.getLineForVertical(scrollY);
                int lastLine = layout.getLineForVertical(scrollY + height);

                firstLine = Math.max(0, firstLine - 80);
                lastLine = Math.min(layout.getLineCount() - 1, lastLine + 80);

                winStart = layout.getLineStart(firstLine);
                winEnd = layout.getLineEnd(lastLine);
            } else {
                int cursorPosition = Math.max(0, ideEditText.getSelectionStart());
                winStart = Math.max(0, cursorPosition - 15000);
                winEnd = Math.min(s.length(), cursorPosition + 15000);
            }

            final int finalStart = winStart;
            final int finalEnd = winEnd;
            final String textToParse = s.subSequence(finalStart, finalEnd).toString();
            final int currentParseId = ++parseId;

            pendingTask = () -> executor.execute(() -> {
                if (currentParseId != parseId) return;

                List<SpanDef> spansToApply = new ArrayList<>();
                BitSet isProtected = new BitSet(textToParse.length());

                Matcher mc = PATTERN_COMMENTS.matcher(textToParse);
                while (mc.find()) {
                    spansToApply.add(new SpanDef(mc.start() + finalStart, mc.end() + finalStart, colorComment));
                    isProtected.set(mc.start(), mc.end());
                }

                Matcher ms = PATTERN_STRINGS.matcher(textToParse);
                while (ms.find()) {
                    spansToApply.add(new SpanDef(ms.start() + finalStart, ms.end() + finalStart, colorString));
                    isProtected.set(ms.start(), ms.end());
                }

                Matcher mk = PATTERN_KEYWORD.matcher(textToParse);
                while (mk.find()) {
                    if (!isProtected.get(mk.start())) {
                        spansToApply.add(new SpanDef(mk.start() + finalStart, mk.end() + finalStart, colorKeyword));
                    }
                }

                Matcher mt = PATTERN_TYPES.matcher(textToParse);
                while (mt.find()) {
                    if (!isProtected.get(mt.start())) {
                        spansToApply.add(new SpanDef(mt.start() + finalStart, mt.end() + finalStart, colorType));
                    }
                }

                Matcher mn = PATTERN_NUMBERS.matcher(textToParse);
                while (mn.find()) {
                    if (!isProtected.get(mn.start())) {
                        spansToApply.add(new SpanDef(mn.start() + finalStart, mn.end() + finalStart, colorNumber));
                    }
                }

                handler.post(() -> {
                    if (currentParseId != parseId || !isHdlDetEnabled) return;

                    int prevScrollY = textScrollView.getScrollY();
                    View horizScroll = (View) ideEditText.getParent();
                    int prevScrollX = horizScroll.getScrollX();

                    isFormatting = true;
                    Editable currentEditable = ideEditText.getText();

                    clearSpans(currentEditable, finalStart, finalEnd);

                    for (SpanDef d : spansToApply) {
                        try {
                            currentEditable.setSpan(new ForegroundColorSpan(d.color), d.start, d.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } catch (Exception ignored) {}
                    }
                    isFormatting = false;

                    textScrollView.scrollTo(textScrollView.getScrollX(), prevScrollY);
                    horizScroll.scrollTo(prevScrollX, horizScroll.getScrollY());
                });
            });

            handler.postDelayed(pendingTask, 150);
        }

        private void clearSpans(Editable s, int start, int end) {
            ForegroundColorSpan[] spans = s.getSpans(start, end, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                s.removeSpan(span);
            }
        }
    }
}