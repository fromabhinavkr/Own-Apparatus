package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("all")
@SuppressLint({"SetTextI18n", "SpellCheckingInspection", "ClickableViewAccessibility", "DrawAllocation"})
public class StickerMakerActivity extends AppCompatActivity {
    private LinearLayout btnAddWA;
    private TextView tvSubtitle, tvStatusText, tvPackTypeBadge;
    private View statusDot;
    private EditText etTitle;
    private GridLayout stickerGrid;
    private LinearLayout packSelectorContainer, mainStickerCard;
    private LinearLayout packModeContainer, tabStaticPacks, tabAnimatedPacks;
    private FrameLayout iconStaticMode, iconAnimMode;
    private TextView tvStaticMode, tvAnimMode;
    private HorizontalScrollView packScrollView;
    private FrameLayout btnPackOptions;

    private int stickerCount = 0;
    private boolean isDarkTheme;
    private int bgColor, textColor, cardColor, panelColor, accentColor, subTextColor, glassBorderColor;
    private SharedPreferences prefs;

    private String deviceId;
    private String currentPackId;
    private int staticPackCount = 3;
    private int animPackCount = 1;
    private boolean isAnimatedMode = false;
    private boolean isSwitchingPack = false;

    private final ActivityResultLauncher<Intent> pickMediaLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        if (isAnimatedMode) {
                            showVideoTrimStudio(uri);
                        } else {
                            showVisualCropStudio(uri);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> addStickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_CANCELED && result.getData() != null) {
                    String validationError = result.getData().getStringExtra("validation_error");
                    if (validationError != null) {
                        Toast.makeText(this, "WA Rejected: " + validationError, Toast.LENGTH_LONG).show();
                    }
                } else if (result.getResultCode() == Activity.RESULT_OK) {
                    Toast.makeText(this, "Successfully added to WhatsApp!", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sticker_maker);
        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);

        int themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) {
            boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);
            themeState = oldDark ? 1 : 0;
        }

        isDarkTheme = (themeState != 0);

        if (themeState == 0) {
            bgColor = Color.parseColor("#FFFFFF");
            cardColor = Color.parseColor("#F3F4F6");
            panelColor = Color.parseColor("#E5E7EB");
            textColor = Color.parseColor("#1C1C1E");
            subTextColor = Color.parseColor("#6B7280");
            accentColor = Color.parseColor("#34C759");
            glassBorderColor = Color.parseColor("#D1D1D6");
        } else if (themeState == 1) {
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            panelColor = Color.parseColor("#3A3A3C");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            accentColor = Color.parseColor("#32D74B");
            glassBorderColor = Color.parseColor("#33FFFFFF");
        } else {
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#111827");
            panelColor = Color.parseColor("#1F2937");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#9CA3AF");
            accentColor = Color.parseColor("#A3E635");
            glassBorderColor = Color.parseColor("#33FFFFFF");
        }

        View root = findViewById(R.id.stickerMakerRoot);
        root.setBackgroundColor(bgColor);

        packModeContainer = findViewById(R.id.packModeContainer);
        tabStaticPacks = findViewById(R.id.tabStaticPacks);
        tabAnimatedPacks = findViewById(R.id.tabAnimatedPacks);
        iconStaticMode = findViewById(R.id.iconStaticMode);
        iconAnimMode = findViewById(R.id.iconAnimMode);
        tvStaticMode = findViewById(R.id.tvStaticMode);
        tvAnimMode = findViewById(R.id.tvAnimMode);

        etTitle = findViewById(R.id.etStickerTitle);
        tvPackTypeBadge = findViewById(R.id.tvPackTypeBadge);
        tvSubtitle = findViewById(R.id.tvStickerSubtitle);
        tvStatusText = findViewById(R.id.tvStatusText);
        statusDot = findViewById(R.id.statusDot);
        mainStickerCard = findViewById(R.id.mainStickerCard);
        stickerGrid = findViewById(R.id.stickerGrid);
        packSelectorContainer = findViewById(R.id.packSelectorContainer);
        packScrollView = findViewById(R.id.packScrollView);
        btnPackOptions = findViewById(R.id.btnPackOptions);
        btnAddWA = findViewById(R.id.btnAddWhatsApp);

        GradientDrawable gdMainCard = new GradientDrawable();
        gdMainCard.setColor(cardColor);
        gdMainCard.setCornerRadius(60f);
        mainStickerCard.setBackground(gdMainCard);

        GradientDrawable gdModeBar = new GradientDrawable();
        gdModeBar.setColor(cardColor);
        gdModeBar.setCornerRadius(100f);
        gdModeBar.setStroke(2, glassBorderColor);
        packModeContainer.setBackground(gdModeBar);

        etTitle.setTextColor(textColor);
        etTitle.setHintTextColor(subTextColor);

        tvSubtitle.setTextColor(subTextColor);
        tvSubtitle.setText("Long press to reorder sticker");

        btnPackOptions.removeAllViews();
        btnPackOptions.addView(new StickerIconView(this, StickerIconView.ICON_TRASH, subTextColor));
        GradientDrawable gdDots = new GradientDrawable();
        gdDots.setColor(panelColor);
        gdDots.setShape(GradientDrawable.OVAL);
        btnPackOptions.setBackground(gdDots);
        btnPackOptions.setOnClickListener(v -> showDeletePackDialog());

        setupBottomButton(btnAddWA, StickerIconView.ICON_SEND, "Publish to WhatsApp", accentColor, isDarkTheme ? Color.BLACK : Color.WHITE);

        deviceId = prefs.getString("device_sticker_id", "");
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            prefs.edit().putString("device_sticker_id", deviceId).apply();

            int count = prefs.getInt("static_pack_count", 3);
            for (int i = 1; i <= count; i++) {
                String oldId = "ownphoto_pack_" + i;
                String newId = "ownpack_" + deviceId + "_" + i;
                File oldDir = new File(getFilesDir(), "stickers/" + oldId);
                File newDir = new File(getFilesDir(), "stickers/" + newId);
                if (oldDir.exists()) oldDir.renameTo(newDir);
                String oldName = prefs.getString("pack_name_" + oldId, "");
                int oldVer = prefs.getInt("sticker_version_" + oldId, 3);
                prefs.edit()
                        .putString("pack_name_" + newId, oldName)
                        .putInt("sticker_version_" + newId, oldVer)
                        .remove("pack_name_" + oldId)
                        .remove("sticker_version_" + oldId)
                        .apply();
            }
        }

        staticPackCount = prefs.getInt("static_pack_count", 3);
        animPackCount = prefs.getInt("anim_pack_count", 0);
        if (animPackCount < 1) {
            animPackCount = 1;
            String firstAnimId = "ownanim_" + deviceId + "_1";
            prefs.edit()
                    .putInt("anim_pack_count", 1)
                    .putBoolean("is_animated_" + firstAnimId, true)
                    .putString("pack_name_" + firstAnimId, "Anim Pack 1")
                    .apply();
        }

        tabStaticPacks.setOnClickListener(v -> {
            if (isAnimatedMode) {
                isAnimatedMode = false;
                updateModeSwitcherUI();
                switchPack(getPackId(1));
            }
        });

        tabAnimatedPacks.setOnClickListener(v -> {
            if (!isAnimatedMode) {
                isAnimatedMode = true;
                updateModeSwitcherUI();
                switchPack(getPackId(1));
            }
        });

        updateModeSwitcherUI();
        refreshPackTabBar();
        switchPack(getPackId(1));

        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (isSwitchingPack) return;
                String newName = s.toString().trim();
                prefs.edit().putString("pack_name_" + currentPackId, newName).apply();
                refreshPackTabBarWithoutRebuilding();
            }
        });

        btnAddWA.setOnClickListener(v -> {
            if (stickerCount >= 3) {
                String currentName = prefs.getString("pack_name_" + currentPackId, "");
                if (currentName.trim().isEmpty()) {
                    currentName = (isAnimatedMode ? "Anim Pack " : "Pack ") +
                            currentPackId.substring(currentPackId.lastIndexOf("_") + 1);
                }

                ensureValidTrayIcon(new File(getFilesDir(), "stickers/" + currentPackId));

                int currentVersion = prefs.getInt("sticker_version_" + currentPackId, 3);
                prefs.edit()
                        .putInt("sticker_version_" + currentPackId, currentVersion + 1)
                        .putBoolean("is_animated_" + currentPackId, isAnimatedMode)
                        .apply();

                Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
                intent.putExtra("sticker_pack_id", currentPackId);
                intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY);
                intent.putExtra("sticker_pack_name", currentName);

                try {
                    intent.setPackage("com.whatsapp");
                    addStickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    try {
                        intent.setPackage("com.whatsapp.w4b");
                        addStickerLauncher.launch(intent);
                    } catch (ActivityNotFoundException e2) {
                        Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    private void updateModeSwitcherUI() {
        int activeText = isDarkTheme ? Color.BLACK : Color.WHITE;
        int inactiveText = subTextColor;

        GradientDrawable gdStatic = new GradientDrawable();
        gdStatic.setCornerRadius(100f);
        gdStatic.setColor(!isAnimatedMode ? accentColor : Color.TRANSPARENT);
        tabStaticPacks.setBackground(gdStatic);

        GradientDrawable gdAnim = new GradientDrawable();
        gdAnim.setCornerRadius(100f);
        gdAnim.setColor(isAnimatedMode ? accentColor : Color.TRANSPARENT);
        tabAnimatedPacks.setBackground(gdAnim);

        tvStaticMode.setTextColor(!isAnimatedMode ? activeText : inactiveText);
        tvAnimMode.setTextColor(isAnimatedMode ? activeText : inactiveText);

        iconStaticMode.removeAllViews();
        iconStaticMode.addView(new StickerIconView(this, StickerIconView.ICON_IMAGE, !isAnimatedMode ? activeText : inactiveText));

        iconAnimMode.removeAllViews();
        iconAnimMode.addView(new StickerIconView(this, StickerIconView.ICON_VIDEO, isAnimatedMode ? activeText : inactiveText));

        if (tvPackTypeBadge != null) {
            GradientDrawable badgeGd = new GradientDrawable();
            badgeGd.setCornerRadius(50f);
            badgeGd.setColor(panelColor);
            badgeGd.setStroke(2, accentColor);
            tvPackTypeBadge.setBackground(badgeGd);
            tvPackTypeBadge.setTextColor(accentColor);
            tvPackTypeBadge.setText(isAnimatedMode ? "ANIMATED" : "STATIC");
        }
    }

    private void triggerAddStickerFlow() {
        if (stickerCount >= 30) {
            Toast.makeText(this, "Pack is full (30 max)!", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(isAnimatedMode ? "video/*" : "image/*");
        pickMediaLauncher.launch(intent);
    }

    private void setupBottomButton(LinearLayout layout, int iconType, String text, int bgCol, int txtCol) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgCol);
        gd.setCornerRadius(100f);
        layout.setBackground(gd);

        FrameLayout iconFrame = (FrameLayout) layout.getChildAt(0);
        iconFrame.removeAllViews();
        iconFrame.addView(new StickerIconView(this, iconType, txtCol));

        TextView tv = (TextView) layout.getChildAt(1);
        tv.setText(text);
        tv.setTextColor(txtCol);

        FrameLayout chevronFrame = (FrameLayout) layout.getChildAt(2);
        chevronFrame.removeAllViews();
        chevronFrame.addView(new StickerIconView(this, StickerIconView.ICON_CHEVRON, txtCol));
    }

    private String getPackId(int index) {
        return (isAnimatedMode ? "ownanim_" : "ownpack_") + deviceId + "_" + index;
    }

    private int getActivePackCount() {
        return isAnimatedMode ? animPackCount : staticPackCount;
    }

    private void setActivePackCount(int count) {
        if (isAnimatedMode) {
            animPackCount = count;
            prefs.edit().putInt("anim_pack_count", animPackCount).apply();
        } else {
            staticPackCount = count;
            prefs.edit().putInt("static_pack_count", staticPackCount).apply();
        }
    }

    private void refreshPackTabBarWithoutRebuilding() {
        try {
            int currentIdx = Integer.parseInt(currentPackId.substring(currentPackId.lastIndexOf("_") + 1)) - 1;
            if (currentIdx >= 0 && currentIdx < packSelectorContainer.getChildCount() - 1) {
                View child = packSelectorContainer.getChildAt(currentIdx);
                if (child instanceof LinearLayout) {
                    TextView tv = (TextView) ((LinearLayout) child).getChildAt(1);
                    String newName = prefs.getString("pack_name_" + currentPackId, "");
                    if (newName.trim().isEmpty()) {
                        newName = (isAnimatedMode ? "Anim Pack " : "Pack ") + (currentIdx + 1);
                    }
                    if (newName.length() > 12) newName = newName.substring(0, 12) + "...";
                    tv.setText(newName);
                }
            }
        } catch (Exception ignored) {}
    }

    private void refreshPackTabBar() {
        packSelectorContainer.removeAllViews();
        int count = getActivePackCount();
        for (int i = 1; i <= count; i++) {
            String pId = getPackId(i);
            String realName = prefs.getString("pack_name_" + pId, "");
            if (realName.trim().isEmpty()) realName = (isAnimatedMode ? "Anim Pack " : "Pack ") + i;
            if (realName.length() > 12) realName = realName.substring(0, 12) + "...";

            LinearLayout tab = createTabButton(realName, pId.equals(currentPackId), false);
            tab.setOnClickListener(v -> switchPack(pId));
            packSelectorContainer.addView(tab);
        }

        LinearLayout btnAddPack = createTabButton("New", false, true);
        btnAddPack.setOnClickListener(v -> {
            int newCount = getActivePackCount() + 1;
            setActivePackCount(newCount);
            String newId = getPackId(newCount);
            prefs.edit()
                    .putString("pack_name_" + newId, (isAnimatedMode ? "Anim Pack " : "Pack ") + newCount)
                    .putBoolean("is_animated_" + newId, isAnimatedMode)
                    .apply();
            switchPack(newId);
            packScrollView.post(() -> packScrollView.fullScroll(View.FOCUS_RIGHT));
        });
        packSelectorContainer.addView(btnAddPack);
    }

    private LinearLayout createTabButton(String text, boolean isActive, boolean isAddBtn) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(35, 0, 45, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(0, 0, 16, 0);
        layout.setLayoutParams(lp);

        int cBg, cTxt;
        if (isActive) {
            cBg = accentColor;
            cTxt = isDarkTheme ? Color.BLACK : Color.WHITE;
        } else {
            cBg = panelColor;
            cTxt = isAddBtn ? accentColor : subTextColor;
        }

        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(100f);
        gd.setColor(cBg);
        if (!isActive) gd.setStroke(2, cardColor);
        layout.setBackground(gd);

        FrameLayout iconFrame = new FrameLayout(this);
        iconFrame.setLayoutParams(new LinearLayout.LayoutParams(40, 40));
        int iconType = isAddBtn ? StickerIconView.ICON_ADD
                : (isAnimatedMode ? StickerIconView.ICON_VIDEO : StickerIconView.ICON_PACK);
        iconFrame.addView(new StickerIconView(this, iconType, cTxt));
        layout.addView(iconFrame);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(cTxt);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.setMargins(16, 0, 0, 0);
        tv.setLayoutParams(tLp);
        layout.addView(tv);

        return layout;
    }

    private void switchPack(String packId) {
        isSwitchingPack = true;
        currentPackId = packId;
        prefs.edit().putBoolean("is_animated_" + currentPackId, isAnimatedMode).apply();
        refreshPackTabBar();

        String savedName = prefs.getString("pack_name_" + currentPackId, "");
        if (savedName.trim().isEmpty()) {
            try {
                int num = Integer.parseInt(packId.substring(packId.lastIndexOf("_") + 1));
                savedName = (isAnimatedMode ? "Anim Pack " : "Pack ") + num;
            } catch (Exception e) {
                savedName = isAnimatedMode ? "My Animated Pack" : "My Custom Pack";
            }
        }

        etTitle.setText(savedName);
        loadExistingStickers();
        isSwitchingPack = false;
    }

    private void showDeletePackDialog() {
        if (currentPackId.endsWith("_1")) {
            Toast.makeText(this, "Cannot delete the default pack.", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        builder.setTitle("Delete Pack")
                .setMessage("Delete this entire pack and all stickers inside it?")
                .setPositiveButton("DELETE", (dialog, which) -> {
                    int deleteIdx = Integer.parseInt(currentPackId.substring(currentPackId.lastIndexOf("_") + 1));
                    int total = getActivePackCount();
                    File dir = new File(getFilesDir(), "stickers/" + currentPackId);
                    deleteDirectory(dir);
                    for (int m = deleteIdx + 1; m <= total; m++) {
                        String oldId = getPackId(m);
                        String newId = getPackId(m - 1);
                        File oldDir = new File(getFilesDir(), "stickers/" + oldId);
                        File newDir = new File(getFilesDir(), "stickers/" + newId);
                        oldDir.renameTo(newDir);
                        prefs.edit()
                                .putString("pack_name_" + newId, prefs.getString("pack_name_" + oldId, (isAnimatedMode ? "Anim Pack " : "Pack ") + (m - 1)))
                                .putInt("sticker_version_" + newId, prefs.getInt("sticker_version_" + oldId, 3))
                                .putBoolean("is_animated_" + newId, isAnimatedMode)
                                .apply();
                    }
                    setActivePackCount(total - 1);
                    switchPack(getPackId(1));
                    Toast.makeText(this, "Pack deleted", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("CANCEL", null);
        AlertDialog dialog = builder.create();
        applyGlassDialogStyle(dialog);
        dialog.show();
    }

    private void deleteDirectory(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) deleteDirectory(child);
            }
        }
        fileOrDirectory.delete();
    }

    private void loadExistingStickers() {
        File dir = new File(getFilesDir(), "stickers/" + currentPackId);
        if (!dir.exists() && !dir.mkdirs()) return;
        File[] files = dir.listFiles((d, name) ->
                name.endsWith(".webp") &&
                        !name.contains("_tmp") &&
                        !name.contains("_thumb") &&
                        new File(d, name).length() > 100);
        stickerCount = (files != null) ? files.length : 0;
        updateUI(files);
    }

    private void updateUI(File[] files) {
        stickerGrid.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int rootPadding = (int) (80 * density);
        int itemMargin = (int) (6 * density);
        int totalMargins = itemMargin * 6;
        int itemSize = (screenWidth - rootPadding - totalMargins) / 3;

        if (stickerCount < 30) {
            addDashedAddButton(itemSize, itemMargin);
        }

        if (files != null) {
            Arrays.sort(files, (f1, f2) -> {
                try {
                    return Integer.compare(
                            Integer.parseInt(f1.getName().replace(".webp", "")),
                            Integer.parseInt(f2.getName().replace(".webp", "")));
                } catch (NumberFormatException e) {
                    return 0;
                }
            });
            for (File file : files) {
                File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png"));
                Bitmap bmp = thumbFile.exists()
                        ? BitmapFactory.decodeFile(thumbFile.getAbsolutePath())
                        : BitmapFactory.decodeFile(file.getAbsolutePath());
                addStickerToGrid(file, bmp, itemSize, itemMargin);
            }
        }

        GradientDrawable dotGd = new GradientDrawable();
        dotGd.setShape(GradientDrawable.OVAL);
        if (stickerCount < 3) {
            dotGd.setColor(Color.parseColor("#FF3B30"));
            tvStatusText.setText(stickerCount + " / 3 Stickers (Min)");
            tvStatusText.setTextColor(Color.parseColor("#FF3B30"));
            btnAddWA.setAlpha(0.4f);
            btnAddWA.setEnabled(false);
        } else {
            dotGd.setColor(accentColor);
            tvStatusText.setText(stickerCount + (isAnimatedMode ? " Animated Ready" : " Stickers Ready"));
            tvStatusText.setTextColor(accentColor);
            btnAddWA.setAlpha(1.0f);
            btnAddWA.setEnabled(true);
        }
        statusDot.setBackground(dotGd);
    }

    private void addStickerToGrid(File file, Bitmap bitmap, int itemSize, int itemMargin) {
        float density = getResources().getDisplayMetrics().density;
        RelativeLayout frame = new RelativeLayout(this);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = itemSize;
        params.height = itemSize;
        params.setMargins(itemMargin, itemMargin, itemMargin, itemMargin);
        frame.setLayoutParams(params);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(panelColor);
        gd.setCornerRadius(40f);
        frame.setBackground(gd);

        ImageView iv = new ImageView(this);
        boolean animatedLoaded = false;
        if (isAnimatedMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(file);
                Drawable drawable = ImageDecoder.decodeDrawable(source);
                iv.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable) drawable).start();
                    animatedLoaded = true;
                }
            } catch (Exception ignored) {}
        }
        if (!animatedLoaded && bitmap != null) {
            iv.setImageBitmap(bitmap);
        }

        RelativeLayout.LayoutParams ivLp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int p = (int) (12 * density);
        iv.setPadding(p, p, p, p);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(iv, ivLp);

        if (isAnimatedMode) {
            FrameLayout animBadge = new FrameLayout(this);
            GradientDrawable badgeGd = new GradientDrawable();
            badgeGd.setColor(Color.parseColor("#CC111827"));
            badgeGd.setShape(GradientDrawable.OVAL);
            animBadge.setBackground(badgeGd);
            int bSize = (int) (24 * density);
            RelativeLayout.LayoutParams bLp = new RelativeLayout.LayoutParams(bSize, bSize);
            bLp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            bLp.addRule(RelativeLayout.ALIGN_PARENT_START);
            bLp.setMargins(8, 8, 0, 0);
            animBadge.setLayoutParams(bLp);
            animBadge.addView(new StickerIconView(this, StickerIconView.ICON_PLAY, accentColor));
            frame.addView(animBadge);
        }

        FrameLayout trashBtn = new FrameLayout(this);
        GradientDrawable trashGd = new GradientDrawable();
        trashGd.setColor(Color.parseColor("#E6111827"));
        trashGd.setShape(GradientDrawable.OVAL);
        trashGd.setStroke(2, glassBorderColor);
        trashBtn.setBackground(trashGd);

        int tSize = (int) (32 * density);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(tSize, tSize);
        tLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        tLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        tLp.setMargins(0, 0, 8, 8);
        trashBtn.setLayoutParams(tLp);

        trashBtn.addView(new StickerIconView(this, StickerIconView.ICON_TRASH, Color.WHITE));

        trashBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
            builder.setTitle("Delete Sticker")
                    .setMessage("Remove this sticker from the pack?")
                    .setPositiveButton("DELETE", (dialog, which) -> {
                        if (file.delete()) {
                            File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png"));
                            if (thumbFile.exists()) thumbFile.delete();
                            ensureValidTrayIcon(file.getParentFile());
                            loadExistingStickers();
                        }
                    }).setNegativeButton("CANCEL", null);
            AlertDialog dialog = builder.create();
            applyGlassDialogStyle(dialog);
            dialog.show();
        });

        frame.addView(trashBtn);

        frame.setOnLongClickListener(v -> {
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(null, shadow, file, 0);
            } else {
                v.startDrag(null, shadow, file, 0);
            }
            return true;
        });

        frame.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.5f);
                    break;
                case DragEvent.ACTION_DRAG_EXITED:
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1.0f);
                    break;
                case DragEvent.ACTION_DROP:
                    v.setAlpha(1.0f);
                    File draggedFile = (File) event.getLocalState();
                    if (draggedFile != null && !draggedFile.equals(file)) {
                        swapStickerFiles(draggedFile, file);
                    }
                    break;
            }
            return true;
        });

        stickerGrid.addView(frame);
    }

    private void swapStickerFiles(File f1, File f2) {
        File dir = f1.getParentFile();
        File temp = new File(dir, "swap_tmp.webp");
        File thumb1 = new File(dir, f1.getName().replace(".webp", "_thumb.png"));
        File thumb2 = new File(dir, f2.getName().replace(".webp", "_thumb.png"));
        File thumbTemp = new File(dir, "swap_tmp_thumb.png");

        f1.renameTo(temp);
        f2.renameTo(f1);
        temp.renameTo(f2);

        if (thumb1.exists()) thumb1.renameTo(thumbTemp);
        if (thumb2.exists()) thumb2.renameTo(thumb1);
        if (thumbTemp.exists()) thumbTemp.renameTo(thumb2);

        ensureValidTrayIcon(dir);
        loadExistingStickers();
    }

    private void addDashedAddButton(int itemSize, int itemMargin) {
        View dashedView = new View(this) {
            final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(subTextColor);
                setStyle(Paint.Style.STROKE);
                setStrokeWidth(4f);
                setPathEffect(new DashPathEffect(new float[]{20f, 15f}, 0f));
            }};
            final Paint pIcon = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(subTextColor);
                setStyle(Paint.Style.STROKE);
                setStrokeWidth(6f);
                setStrokeCap(Paint.Cap.ROUND);
            }};
            @Override
            protected void onDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                RectF rect = new RectF(4, 4, w - 4, h - 4);
                canvas.drawRoundRect(rect, 40f, 40f, pBorder);
                canvas.drawLine(w / 2f, h / 2f - 30, w / 2f, h / 2f + 30, pIcon);
                canvas.drawLine(w / 2f - 30, h / 2f, w / 2f + 30, h / 2f, pIcon);
            }
        };

        LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = itemSize;
        params.height = itemSize;
        params.setMargins(itemMargin, itemMargin, itemMargin, itemMargin);
        frame.setLayoutParams(params);

        dashedView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        frame.addView(dashedView);

        TextView tv = new TextView(this);
        tv.setText(isAnimatedMode ? "Add Video" : "Add Sticker");
        tv.setTextColor(subTextColor);
        tv.setTextSize(12f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 12, 0, 0);
        frame.addView(tv);

        frame.setOnClickListener(v -> triggerAddStickerFlow());
        stickerGrid.addView(frame);
    }

    private void ensureValidTrayIcon(File dir) {
        try {
            File trayFile = new File(dir, "tray.png");
            File[] files = dir.listFiles((d, name) ->
                    name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> {
                    try {
                        return Integer.compare(
                                Integer.parseInt(f1.getName().replace(".webp", "")),
                                Integer.parseInt(f2.getName().replace(".webp", "")));
                    } catch (Exception e) {
                        return 0;
                    }
                });
                File firstThumb = new File(dir, files[0].getName().replace(".webp", "_thumb.png"));
                Bitmap tb = firstThumb.exists()
                        ? BitmapFactory.decodeFile(firstThumb.getAbsolutePath())
                        : BitmapFactory.decodeFile(files[0].getAbsolutePath());
                if (tb != null) {
                    Bitmap tray = Bitmap.createScaledBitmap(tb, 96, 96, true);
                    FileOutputStream out = new FileOutputStream(trayFile);
                    tray.compress(Bitmap.CompressFormat.PNG, 75, out);
                    out.close();
                    if (tray != tb && !tray.isRecycled()) tray.recycle();
                    if (!tb.isRecycled()) tb.recycle();
                }
            }
        } catch (Exception ignored) {}
    }

    private Bitmap decodeSampledBitmapFromUri(Uri uri, int reqWidth, int reqHeight) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            options.inJustDecodeBounds = false;
            is = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        int h = options.outHeight, w = options.outWidth, inSampleSize = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2, halfW = w / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    // =========================================================================================
    // STEP 1 (ANIMATED): 6-SECOND VIDEO TRIMMER STUDIO WITH FILMSTRIP TIMELINE
    // =========================================================================================
    private void showVideoTrimStudio(Uri videoUri) {
        long durationMs = 0;
        int videoWidth = 512, videoHeight = 512;
        MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
        try {
            metaRetriever.setDataSource(this, videoUri);
            String durStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String wStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String hStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotStr = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (durStr != null) durationMs = Long.parseLong(durStr);
            if (wStr != null) videoWidth = Integer.parseInt(wStr);
            if (hStr != null) videoHeight = Integer.parseInt(hStr);
            int rot = (rotStr != null) ? Integer.parseInt(rotStr) : 0;
            if (rot == 90 || rot == 270) {
                int tmp = videoWidth;
                videoWidth = videoHeight;
                videoHeight = tmp;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not read video file.", Toast.LENGTH_SHORT).show();
            try { metaRetriever.release(); } catch (Exception ignored) {}
            return;
        }
        try { metaRetriever.release(); } catch (Exception ignored) {}

        if (durationMs < 300) {
            Toast.makeText(this, "Video is too short.", Toast.LENGTH_SHORT).show();
            return;
        }

        final long totalDurationMs = durationMs;
        final long maxWindowMs = 6000L;
        final long minWindowMs = Math.min(500L, totalDurationMs);
        final long[] trimRangeMs = new long[]{0L, Math.min(totalDurationMs, maxWindowMs)};

        float density = getResources().getDisplayMetrics().density;
        int p = (int) (22 * density);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(p, p, p, p);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(cardColor);
        gd.setCornerRadius(60f);
        gd.setStroke(2, glassBorderColor);
        layout.setBackground(gd);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, (int) (14 * density));

        FrameLayout headerIcon = new FrameLayout(this);
        headerIcon.setLayoutParams(new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density)));
        headerIcon.addView(new StickerIconView(this, StickerIconView.ICON_SCISSORS, accentColor));
        header.addView(headerIcon);

        TextView title = new TextView(this);
        title.setText(" Step 1: Trim Video (Max 6s)");
        title.setTextSize(17f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        header.addView(title);
        layout.addView(header);

        // Video Player Preview Box
        int previewBoxSize = (int) (250 * density);
        FrameLayout playerWrapper = new FrameLayout(this);
        LinearLayout.LayoutParams pwLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, previewBoxSize);
        pwLp.gravity = Gravity.CENTER;
        pwLp.setMargins(0, 0, 0, (int) (14 * density));
        playerWrapper.setLayoutParams(pwLp);

        GradientDrawable pwGd = new GradientDrawable();
        pwGd.setColor(Color.parseColor("#101012"));
        pwGd.setCornerRadius(36f);
        pwGd.setStroke(2, glassBorderColor);
        playerWrapper.setBackground(pwGd);

        TextureView textureView = new TextureView(this);
        float videoRatio = (float) videoWidth / Math.max(1, videoHeight);
        int tvW = previewBoxSize;
        int tvH = previewBoxSize;
        if (videoRatio > 1f) {
            tvH = (int) (previewBoxSize / videoRatio);
        } else {
            tvW = (int) (previewBoxSize * videoRatio);
        }
        FrameLayout.LayoutParams tvLp = new FrameLayout.LayoutParams(tvW, tvH, Gravity.CENTER);
        textureView.setLayoutParams(tvLp);
        playerWrapper.addView(textureView);

        // Floating Play/Pause indicator
        FrameLayout playBadge = new FrameLayout(this);
        int badgeSize = (int) (44 * density);
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.CENTER);
        playBadge.setLayoutParams(pbLp);
        GradientDrawable pbGd = new GradientDrawable();
        pbGd.setColor(Color.parseColor("#99000000"));
        pbGd.setShape(GradientDrawable.OVAL);
        playBadge.setBackground(pbGd);
        StickerIconView playIconView = new StickerIconView(this, StickerIconView.ICON_PLAY, Color.WHITE);
        playBadge.addView(playIconView);
        playBadge.setAlpha(0f);
        playerWrapper.addView(playBadge);

        layout.addView(playerWrapper);

        // Time Readout Row
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (8 * density));

        TextView tvStart = new TextView(this);
        tvStart.setTextColor(subTextColor);
        tvStart.setTextSize(12f);
        tvStart.setTypeface(null, Typeface.BOLD);
        tvStart.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDurationBadge = new TextView(this);
        tvDurationBadge.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
        tvDurationBadge.setTextSize(12f);
        tvDurationBadge.setTypeface(null, Typeface.BOLD);
        tvDurationBadge.setPadding((int) (12 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
        GradientDrawable durGd = new GradientDrawable();
        durGd.setColor(accentColor);
        durGd.setCornerRadius(50f);
        tvDurationBadge.setBackground(durGd);

        TextView tvEnd = new TextView(this);
        tvEnd.setTextColor(subTextColor);
        tvEnd.setTextSize(12f);
        tvEnd.setTypeface(null, Typeface.BOLD);
        tvEnd.setGravity(Gravity.END);
        tvEnd.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        timeRow.addView(tvStart);
        timeRow.addView(tvDurationBadge);
        timeRow.addView(tvEnd);
        layout.addView(timeRow);

        Runnable updateTimeLabels = () -> {
            tvStart.setText(formatMs(trimRangeMs[0]));
            tvEnd.setText(formatMs(trimRangeMs[1]));
            float selSec = (trimRangeMs[1] - trimRangeMs[0]) / 1000f;
            tvDurationBadge.setText(String.format(Locale.US, "%.1fs / 6.0s", selSec));
        };
        updateTimeLabels.run();

        // Custom Interactive Timeline Trimmer View
        final MediaPlayer[] mediaPlayerHolder = new MediaPlayer[]{null};
        final boolean[] isUserDraggingTimeline = new boolean[]{false};

        VideoTrimTimelineView timelineView = new VideoTrimTimelineView(
                this, totalDurationMs, maxWindowMs, minWindowMs, accentColor, panelColor, textColor,
                (startMs, endMs, seekTargetMs, isDragging) -> {
                    trimRangeMs[0] = startMs;
                    trimRangeMs[1] = endMs;
                    isUserDraggingTimeline[0] = isDragging;
                    updateTimeLabels.run();
                    MediaPlayer mp = mediaPlayerHolder[0];
                    if (mp != null) {
                        try {
                            if (isDragging && mp.isPlaying()) {
                                mp.pause();
                            }
                            seekMediaPlayerExact(mp, seekTargetMs);
                            if (!isDragging && !mp.isPlaying()) {
                                mp.start();
                                playBadge.animate().alpha(0f).setDuration(150).start();
                            }
                        } catch (Exception ignored) {}
                    }
                });
        LinearLayout.LayoutParams tlLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (68 * density));
        tlLp.setMargins(0, (int) (4 * density), 0, (int) (18 * density));
        timelineView.setLayoutParams(tlLp);
        layout.addView(timelineView);

        // Extract Filmstrip Thumbnails Asynchronously
        final AtomicBoolean cancelThumbs = new AtomicBoolean(false);
        new Thread(() -> {
            MediaMetadataRetriever thumbRet = new MediaMetadataRetriever();
            try {
                thumbRet.setDataSource(this, videoUri);
                int count = 8;
                for (int i = 0; i < count; i++) {
                    if (cancelThumbs.get()) break;
                    long tUs = (long) (((i + 0.5f) / count) * totalDurationMs * 1000L);
                    Bitmap bmp = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        bmp = thumbRet.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 120, 120);
                    }
                    if (bmp == null) {
                        Bitmap raw = thumbRet.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        if (raw != null) {
                            bmp = Bitmap.createScaledBitmap(raw, 120, 120, true);
                            if (bmp != raw) raw.recycle();
                        }
                    }
                    if (bmp != null && !cancelThumbs.get()) {
                        final Bitmap finalBmp = bmp;
                        runOnUiThread(() -> timelineView.addThumbnail(finalBmp));
                    }
                }
            } catch (Exception ignored) {
            } finally {
                try { thumbRet.release(); } catch (Exception ignored) {}
            }
        }).start();

        // Action Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnCancel = new Button(this);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnCancel.setStateListAnimator(null);
            btnCancel.setElevation(0);
        }
        GradientDrawable gdCancel = new GradientDrawable();
        gdCancel.setColor(Color.parseColor("#FF3B30"));
        gdCancel.setCornerRadius(100f);
        btnCancel.setBackgroundTintList(null);
        btnCancel.setBackground(gdCancel);
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, (int) (54 * density), 1f);
        lpC.setMargins(0, 0, (int) (8 * density), 0);
        btnCancel.setLayoutParams(lpC);

        Button btnNext = new Button(this);
        btnNext.setText("NEXT: CROP & CURVE");
        btnNext.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
        btnNext.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnNext.setStateListAnimator(null);
            btnNext.setElevation(0);
        }
        GradientDrawable gdNext = new GradientDrawable();
        gdNext.setColor(accentColor);
        gdNext.setCornerRadius(100f);
        btnNext.setBackgroundTintList(null);
        btnNext.setBackground(gdNext);
        LinearLayout.LayoutParams lpN = new LinearLayout.LayoutParams(0, (int) (54 * density), 1.3f);
        lpN.setMargins((int) (8 * density), 0, 0, 0);
        btnNext.setLayoutParams(lpN);

        btnRow.addView(btnCancel);
        btnRow.addView(btnNext);
        layout.addView(btnRow);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Loop Watcher Handler
        Handler loopHandler = new Handler(Looper.getMainLooper());
        Runnable loopRunnable = new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = mediaPlayerHolder[0];
                if (mp != null && !isUserDraggingTimeline[0]) {
                    try {
                        if (mp.isPlaying()) {
                            long pos = mp.getCurrentPosition();
                            timelineView.setPlayheadMs(pos);
                            if (pos >= trimRangeMs[1] || pos < trimRangeMs[0] - 100) {
                                seekMediaPlayerExact(mp, trimRangeMs[0]);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                loopHandler.postDelayed(this, 35);
            }
        };

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                try {
                    MediaPlayer mp = new MediaPlayer();
                    mediaPlayerHolder[0] = mp;
                    mp.setDataSource(StickerMakerActivity.this, videoUri);
                    mp.setSurface(new Surface(surfaceTexture));
                    mp.setLooping(true);
                    mp.setOnPreparedListener(player -> {
                        seekMediaPlayerExact(player, trimRangeMs[0]);
                        player.start();
                        loopHandler.post(loopRunnable);
                    });
                    mp.prepareAsync();
                } catch (Exception ignored) {}
            }
            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return true; }
            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {}
        });

        playerWrapper.setOnClickListener(v -> {
            MediaPlayer mp = mediaPlayerHolder[0];
            if (mp != null) {
                try {
                    if (mp.isPlaying()) {
                        mp.pause();
                        playIconView.setIconType(StickerIconView.ICON_PLAY);
                        playBadge.animate().alpha(1f).setDuration(150).start();
                    } else {
                        mp.start();
                        playBadge.animate().alpha(0f).setDuration(150).start();
                    }
                } catch (Exception ignored) {}
            }
        });

        dialog.setOnDismissListener(d -> {
            cancelThumbs.set(true);
            loopHandler.removeCallbacksAndMessages(null);
            if (mediaPlayerHolder[0] != null) {
                try {
                    mediaPlayerHolder[0].stop();
                } catch (Exception ignored) {}
                try {
                    mediaPlayerHolder[0].release();
                } catch (Exception ignored) {}
                mediaPlayerHolder[0] = null;
            }
            timelineView.recycleThumbnails();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnNext.setOnClickListener(v -> {
            long start = trimRangeMs[0];
            long end = trimRangeMs[1];
            if (end - start > 6000L) end = start + 6000L;
            if (end - start < 300L) end = Math.min(totalDurationMs, start + 500L);
            long finalStart = start;
            long finalEnd = end;
            dialog.dismiss();
            showAnimatedCropStudio(videoUri, finalStart, finalEnd);
        });

        dialog.show();
    }

    private void seekMediaPlayerExact(MediaPlayer mp, long timeMs) {
        if (mp == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(timeMs, MediaPlayer.SEEK_CLOSEST);
            } else {
                mp.seekTo((int) timeMs);
            }
        } catch (Exception ignored) {}
    }

    private String formatMs(long ms) {
        float totalSec = ms / 1000f;
        int mins = (int) (totalSec / 60);
        float secs = totalSec % 60f;
        return String.format(Locale.US, "%d:%04.1f", mins, secs);
    }

    // =========================================================================================
    // STEP 2 (ANIMATED): LIVE ANIMATED CROP, CORNER CURVING & SMOOTHNESS STUDIO
    // =========================================================================================
    private void showAnimatedCropStudio(Uri videoUri, long trimStartMs, long trimEndMs) {
        float density = getResources().getDisplayMetrics().density;
        int p = (int) (24 * density);

        final int[] currentCropMode = {0}; // 0=Free, 1=1:1, 2=Oval, 3=Circle
        final float[] cornerRadiusProgress = {0f};
        final int[] smoothLevelProgress = {0};

        long clipDurationMs = Math.max(400L, Math.min(6000L, trimEndMs - trimStartMs));
        final int targetFps = (clipDurationMs <= 2500L) ? 12 : (clipDurationMs <= 4500L ? 10 : 8);
        final int frameDelayMs = 1000 / targetFps;
        final int totalFramesToExtract = Math.max(6, Math.min(48, (int) (clipDurationMs / frameDelayMs)));

        final List<Bitmap> extractedFrames = new ArrayList<>();
        final AtomicBoolean extractionFinished = new AtomicBoolean(false);
        final AtomicBoolean cancelStudio = new AtomicBoolean(false);
        final AtomicBoolean isCompiling = new AtomicBoolean(false);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(p, p, p, p);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(cardColor);
        gd.setCornerRadius(60f);
        gd.setStroke(2, glassBorderColor);
        layout.setBackground(gd);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, (int) (14 * density));

        FrameLayout headerIcon = new FrameLayout(this);
        headerIcon.setLayoutParams(new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density)));
        headerIcon.addView(new StickerIconView(this, StickerIconView.ICON_VIDEO, accentColor));
        header.addView(headerIcon);

        TextView title = new TextView(this);
        title.setText(" Step 2: Crop & Curve Animated");
        title.setTextSize(17f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        header.addView(title);
        layout.addView(header);

        int boxSize = (int) (280 * density);
        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxLp.gravity = Gravity.CENTER;
        boxLp.setMargins(0, (int) (4 * density), 0, (int) (14 * density));
        wrapper.setLayoutParams(boxLp);
        GradientDrawable boxGd = new GradientDrawable();
        boxGd.setColor(isDarkTheme ? Color.parseColor("#141414") : Color.parseColor("#F8F8F8"));
        wrapper.setBackground(boxGd);

        FrameLayout previewBox = new FrameLayout(this) {
            Bitmap offscreen, maskBmp;
            Canvas osCanvas, maskCanvas;
            final Paint dstInPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                dstInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                maskPaint.setColor(Color.BLACK);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) return;

                if (offscreen == null || offscreen.getWidth() != w || offscreen.getHeight() != h) {
                    if (offscreen != null) offscreen.recycle();
                    if (maskBmp != null) maskBmp.recycle();
                    offscreen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    osCanvas = new Canvas(offscreen);
                    maskCanvas = new Canvas(maskBmp);
                }

                offscreen.eraseColor(Color.TRANSPARENT);
                maskBmp.eraseColor(Color.TRANSPARENT);

                super.dispatchDraw(osCanvas);

                Path clipPath = new Path();
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    clipPath.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    clipPath.addRoundRect(new RectF(0, 0, w, h), rx, rx, Path.Direction.CW);
                }

                int smooth = smoothLevelProgress[0];
                if (smooth == 0) {
                    maskPaint.setMaskFilter(null);
                    maskCanvas.drawPath(clipPath, maskPaint);
                } else {
                    float baseBlur = 0f;
                    if (smooth == 1) baseBlur = 15f;
                    else if (smooth == 2) baseBlur = 35f;
                    else if (smooth == 3) baseBlur = 60f;
                    else if (smooth == 4) baseBlur = 100f;

                    float scale = (float) Math.max(w, h) / 512f;
                    maskPaint.setMaskFilter(new BlurMaskFilter(baseBlur * scale, BlurMaskFilter.Blur.NORMAL));

                    maskCanvas.save();
                    maskCanvas.clipPath(clipPath);
                    maskCanvas.drawPath(clipPath, maskPaint);
                    maskCanvas.restore();
                }

                osCanvas.drawBitmap(maskBmp, 0, 0, dstInPaint);
                canvas.drawBitmap(offscreen, 0, 0, null);
            }
        };
        previewBox.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        previewBox.addView(iv);
        wrapper.addView(previewBox);

        View gridOverlay = new View(this) {
            final Paint paintLines = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(Color.WHITE);
                setStrokeWidth(2f);
                setAlpha(160);
            }};
            final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(accentColor);
                setStyle(Paint.Style.STROKE);
                setStrokeWidth(6f);
            }};

            @Override
            protected void onDraw(Canvas c) {
                super.onDraw(c);
                int w = getWidth(), h = getHeight();
                if (w == 0 || h == 0) return;

                Path clipPath = new Path();
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    clipPath.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    clipPath.addRoundRect(new RectF(0, 0, w, h), rx, rx, Path.Direction.CW);
                }

                c.save();
                c.clipPath(clipPath);
                c.drawLine(w / 3f, 0, w / 3f, h, paintLines);
                c.drawLine(w * 2 / 3f, 0, w * 2 / 3f, h, paintLines);
                c.drawLine(0, h / 3f, w, h / 3f, paintLines);
                c.drawLine(0, h * 2 / 3f, w, h * 2 / 3f, paintLines);
                c.restore();

                float inset = border.getStrokeWidth() / 2f;
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    c.drawOval(new RectF(inset, inset, w - inset, h - inset), border);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    c.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), rx, rx, border);
                }
            }
        };
        gridOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrapper.addView(gridOverlay);
        layout.addView(wrapper);

        // Corner Radius Slider
        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.HORIZONTAL);
        radiusRow.setGravity(Gravity.CENTER_VERTICAL);
        radiusRow.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (10 * density));

        TextView tvRadius = new TextView(this);
        tvRadius.setText("Corners");
        tvRadius.setTextColor(textColor);
        tvRadius.setTypeface(null, Typeface.BOLD);
        tvRadius.setTextSize(13f);
        tvRadius.setMinimumWidth((int) (60 * density));
        radiusRow.addView(tvRadius);

        SeekBar seekBarRadius = new SeekBar(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins((int) (12 * density), 0, 0, 0);
        seekBarRadius.setLayoutParams(sbLp);
        seekBarRadius.setMax(100);
        seekBarRadius.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarRadius.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBarRadius.setThumbTintList(ColorStateList.valueOf(accentColor));
        }
        seekBarRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cornerRadiusProgress[0] = progress;
                previewBox.invalidate();
                gridOverlay.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        radiusRow.addView(seekBarRadius);
        layout.addView(radiusRow);

        // Smoothness Slider
        LinearLayout smoothRow = new LinearLayout(this);
        smoothRow.setOrientation(LinearLayout.HORIZONTAL);
        smoothRow.setGravity(Gravity.CENTER_VERTICAL);
        smoothRow.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (14 * density));

        TextView tvSmooth = new TextView(this);
        tvSmooth.setText("Smooth");
        tvSmooth.setTextColor(textColor);
        tvSmooth.setTypeface(null, Typeface.BOLD);
        tvSmooth.setTextSize(13f);
        tvSmooth.setMinimumWidth((int) (60 * density));
        smoothRow.addView(tvSmooth);

        SeekBar seekBarSmooth = new SeekBar(this);
        LinearLayout.LayoutParams sbSmoothLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbSmoothLp.setMargins((int) (12 * density), 0, 0, 0);
        seekBarSmooth.setLayoutParams(sbSmoothLp);
        seekBarSmooth.setMax(4);
        seekBarSmooth.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarSmooth.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBarSmooth.setThumbTintList(ColorStateList.valueOf(accentColor));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GradientDrawable tick = new GradientDrawable();
            tick.setShape(GradientDrawable.OVAL);
            tick.setSize((int) (6 * density), (int) (6 * density));
            tick.setColor(Color.parseColor("#888888"));
            seekBarSmooth.setTickMark(tick);
            seekBarSmooth.setTickMarkTintList(null);
        }
        seekBarSmooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                smoothLevelProgress[0] = progress;
                previewBox.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        smoothRow.addView(seekBarSmooth);
        layout.addView(smoothRow);

        // Ratio / Shape Presets
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setPadding(0, 0, 0, (int) (14 * density));

        String[] cNames = {"Free", "1:1", "Oval", "Circle"};
        final Button[] chipBtns = new Button[4];

        for (int i = 0; i < 4; i++) {
            Button b = new Button(this);
            b.setText(cNames[i]);
            b.setTextSize(11f);
            b.setAllCaps(false);
            b.setTypeface(null, Typeface.BOLD);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setStateListAnimator(null);
                b.setElevation(0);
            }

            GradientDrawable cGd = new GradientDrawable();
            cGd.setCornerRadius(100f);
            cGd.setStroke(2, glassBorderColor);
            if (i == 0) {
                cGd.setColor(accentColor);
                b.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
            } else {
                cGd.setColor(panelColor);
                b.setTextColor(textColor);
            }
            b.setBackgroundTintList(null);
            b.setBackground(cGd);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, (int) (42 * density), 1f);
            clp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
            b.setLayoutParams(clp);
            chipBtns[i] = b;

            int finalI = i;
            b.setOnClickListener(v -> {
                synchronized (extractedFrames) {
                    if (!extractedFrames.isEmpty()) {
                        currentCropMode[0] = finalI;
                        for (int j = 0; j < 4; j++) {
                            GradientDrawable gdBtn = (GradientDrawable) chipBtns[j].getBackground();
                            if (j == finalI) {
                                gdBtn.setColor(accentColor);
                                chipBtns[j].setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
                            } else {
                                gdBtn.setColor(panelColor);
                                chipBtns[j].setTextColor(textColor);
                            }
                        }
                        seekBarRadius.setEnabled(finalI < 2);
                        seekBarRadius.setAlpha((finalI < 2) ? 1.0f : 0.4f);

                        setupTouchAndPresets(iv, extractedFrames.get(0), wrapper, boxSize, finalI);
                        previewBox.invalidate();
                        gridOverlay.invalidate();
                    }
                }
            });
            chipRow.addView(b);
        }
        layout.addView(chipRow);

        // Rotate & Mirror Tools Row
        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER);
        toolsRow.setPadding(0, 0, 0, (int) (18 * density));

        String[] tNames = {"Rotate", "Flip H", "Flip V"};
        for (int i = 0; i < 3; i++) {
            LinearLayout bLayout = new LinearLayout(this);
            bLayout.setOrientation(LinearLayout.HORIZONTAL);
            bLayout.setGravity(Gravity.CENTER);
            GradientDrawable tGd = new GradientDrawable();
            tGd.setColor(panelColor);
            tGd.setCornerRadius(100f);
            tGd.setStroke(2, glassBorderColor);
            bLayout.setBackground(tGd);
            bLayout.setClickable(true);
            bLayout.setFocusable(true);

            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, (int) (46 * density), 1f);
            tLp.setMargins((int) (5 * density), 0, (int) (5 * density), 0);
            bLayout.setLayoutParams(tLp);

            FrameLayout icHolder = new FrameLayout(this);
            int icSize = (int) (18 * density);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(icSize, icSize);
            icLp.setMargins(0, 0, (int) (6 * density), 0);
            icHolder.setLayoutParams(icLp);
            StickerIconView sIcon = new StickerIconView(
                    this, i == 0 ? StickerIconView.ICON_ROTATE : StickerIconView.ICON_FLIP, textColor);
            if (i == 2) sIcon.setRotation(90f);
            icHolder.addView(sIcon);
            bLayout.addView(icHolder);

            TextView tv = new TextView(this);
            tv.setText(tNames[i]);
            tv.setTextSize(12f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(textColor);
            bLayout.addView(tv);

            int finalI = i;
            bLayout.setOnClickListener(v -> {
                if (!extractionFinished.get() || isCompiling.get()) {
                    Toast.makeText(this, "Wait a moment for frames to finish loading...", Toast.LENGTH_SHORT).show();
                    return;
                }
                new Thread(() -> {
                    Matrix m = new Matrix();
                    if (finalI == 0) m.postRotate(90);
                    else if (finalI == 1) m.preScale(-1.0f, 1.0f);
                    else if (finalI == 2) m.preScale(1.0f, -1.0f);

                    synchronized (extractedFrames) {
                        for (int k = 0; k < extractedFrames.size(); k++) {
                            Bitmap old = extractedFrames.get(k);
                            Bitmap rotated = Bitmap.createBitmap(old, 0, 0, old.getWidth(), old.getHeight(), m, true);
                            if (rotated != old) {
                                extractedFrames.set(k, rotated);
                                old.recycle();
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        synchronized (extractedFrames) {
                            if (!extractedFrames.isEmpty()) {
                                iv.setImageBitmap(extractedFrames.get(0));
                                setupTouchAndPresets(iv, extractedFrames.get(0), wrapper, boxSize, currentCropMode[0]);
                                previewBox.invalidate();
                                gridOverlay.invalidate();
                            }
                        }
                    });
                }).start();
            });
            toolsRow.addView(bLayout);
        }
        layout.addView(toolsRow);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            pb.setProgressTintList(ColorStateList.valueOf(accentColor));
        }
        layout.addView(pb);

        // Action Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, (int) (10 * density), 0, 0);

        Button btnCancel = new Button(this);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnCancel.setStateListAnimator(null);
            btnCancel.setElevation(0);
        }
        GradientDrawable gdCancel = new GradientDrawable();
        gdCancel.setColor(Color.parseColor("#FF3B30"));
        gdCancel.setCornerRadius(100f);
        btnCancel.setBackgroundTintList(null);
        btnCancel.setBackground(gdCancel);
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, (int) (54 * density), 1f);
        lpC.setMargins(0, 0, (int) (8 * density), 0);
        btnCancel.setLayoutParams(lpC);

        Button btnBuild = new Button(this);
        btnBuild.setText("COMPILE STICKER");
        btnBuild.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
        btnBuild.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnBuild.setStateListAnimator(null);
            btnBuild.setElevation(0);
        }
        GradientDrawable gdBuild = new GradientDrawable();
        gdBuild.setColor(accentColor);
        gdBuild.setCornerRadius(100f);
        btnBuild.setBackgroundTintList(null);
        btnBuild.setBackground(gdBuild);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, (int) (54 * density), 1.3f);
        lpB.setMargins((int) (8 * density), 0, 0, 0);
        btnBuild.setLayoutParams(lpB);

        btnRow.addView(btnCancel);
        btnRow.addView(btnBuild);
        layout.addView(btnRow);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Live Frame Animator Loop inside previewBox
        Handler animHandler = new Handler(Looper.getMainLooper());
        final int[] currentPreviewIdx = {0};
        Runnable animLoopRunnable = new Runnable() {
            @Override
            public void run() {
                if (cancelStudio.get()) return;
                synchronized (extractedFrames) {
                    if (!extractedFrames.isEmpty()) {
                        currentPreviewIdx[0] = (currentPreviewIdx[0] + 1) % extractedFrames.size();
                        Bitmap frame = extractedFrames.get(currentPreviewIdx[0]);
                        if (frame != null && !frame.isRecycled()) {
                            iv.setImageBitmap(frame);
                            previewBox.invalidate();
                        }
                    }
                }
                animHandler.postDelayed(this, frameDelayMs);
            }
        };

        // Background Sequential Frame Extraction (OPTION_CLOSEST for smooth non-stuck motion)
        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, videoUri);
                for (int i = 0; i < totalFramesToExtract; i++) {
                    if (cancelStudio.get()) break;
                    long timeUs = (trimStartMs + (long) i * frameDelayMs) * 1000L;
                    Bitmap raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (raw == null) {
                        raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    }
                    if (raw != null) {
                        int rw = raw.getWidth();
                        int rh = raw.getHeight();
                        float scale = 512f / Math.max(1, Math.max(rw, rh));
                        int dstW = Math.max(64, Math.round(rw * scale));
                        int dstH = Math.max(64, Math.round(rh * scale));
                        Bitmap frameBmp = Bitmap.createScaledBitmap(raw, dstW, dstH, true);
                        if (frameBmp != raw) raw.recycle();

                        synchronized (extractedFrames) {
                            extractedFrames.add(frameBmp);
                            if (extractedFrames.size() == 1) {
                                final Bitmap first = frameBmp;
                                runOnUiThread(() -> {
                                    iv.setImageBitmap(first);
                                    setupTouchAndPresets(iv, first, wrapper, boxSize, currentCropMode[0]);
                                    animHandler.postDelayed(animLoopRunnable, frameDelayMs);
                                });
                            }
                        }
                    }
                    final int prog = (int) (((i + 1) * 100f) / totalFramesToExtract);
                    runOnUiThread(() -> pb.setProgress(prog));
                }
            } catch (Exception ignored) {
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
                extractionFinished.set(true);
                runOnUiThread(() -> {
                    if (!isCompiling.get()) pb.setVisibility(View.GONE);
                });
            }
        }).start();

        dialog.setOnDismissListener(d -> {
            cancelStudio.set(true);
            animHandler.removeCallbacksAndMessages(null);
            if (!isCompiling.get()) {
                synchronized (extractedFrames) {
                    for (Bitmap b : extractedFrames) {
                        if (b != null && !b.isRecycled()) b.recycle();
                    }
                    extractedFrames.clear();
                }
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnBuild.setOnClickListener(v -> {
            isCompiling.set(true);
            pb.setVisibility(View.VISIBLE);
            pb.setProgress(0);
            btnBuild.setEnabled(false);
            btnCancel.setEnabled(false);
            btnBuild.setText("COMPILING...");

            final Matrix userMatrix = new Matrix(iv.getImageMatrix());
            final int boxW = wrapper.getWidth() > 0 ? wrapper.getWidth() : boxSize;
            final int boxH = wrapper.getHeight() > 0 ? wrapper.getHeight() : boxSize;
            final int cMode = currentCropMode[0];
            final float cRadius = cornerRadiusProgress[0];
            final int sLevel = smoothLevelProgress[0];

            new Thread(() -> {
                try {
                    while (!extractionFinished.get() && !cancelStudio.get()) {
                        Thread.sleep(40);
                    }
                    List<Bitmap> framesSnapshot;
                    synchronized (extractedFrames) {
                        framesSnapshot = new ArrayList<>(extractedFrames);
                    }
                    if (framesSnapshot.isEmpty()) throw new Exception("No frames extracted");

                    File dir = new File(getFilesDir(), "stickers/" + currentPackId);
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("Directory error");

                    File[] existingFiles = dir.listFiles((d, name) ->
                            name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
                    int nextId = 1;
                    if (existingFiles != null) {
                        for (File f : existingFiles) {
                            try {
                                int id = Integer.parseInt(f.getName().replace(".webp", ""));
                                if (id >= nextId) nextId = id + 1;
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    File tmpFile = new File(dir, nextId + "_tmp.webp");
                    File stickerFile = new File(dir, nextId + ".webp");
                    File thumbFile = new File(dir, nextId + "_thumb.png");

                    List<Bitmap> renderedFrames = new ArrayList<>(framesSnapshot.size());
                    for (int i = 0; i < framesSnapshot.size(); i++) {
                        Bitmap rendered = applyMatrixToSticker(
                                framesSnapshot.get(i), userMatrix, boxW, boxH, cMode, cRadius, sLevel);
                        renderedFrames.add(rendered);
                        final int compProg = (int) (((i + 1) * 65f) / framesSnapshot.size());
                        runOnUiThread(() -> {
                            pb.setProgress(compProg);
                            btnBuild.setText("RENDERING " + compProg + "%");
                        });
                    }

                    byte[] animatedWebpBytes = encodeGuaranteedUnder500KB(
                            renderedFrames, frameDelayMs, cMode, cRadius, sLevel, pb, btnBuild);

                    try (FileOutputStream outSticker = new FileOutputStream(tmpFile)) {
                        outSticker.write(animatedWebpBytes);
                    }

                    if (tmpFile.exists() && tmpFile.length() > 100 && tmpFile.length() <= 500000) {
                        if (stickerFile.exists()) stickerFile.delete();
                        tmpFile.renameTo(stickerFile);
                    } else {
                        throw new Exception("Animated WebP exceeded 500KB limit");
                    }

                    if (!renderedFrames.isEmpty()) {
                        Bitmap firstRendered = renderedFrames.get(0);
                        try (FileOutputStream outThumb = new FileOutputStream(thumbFile)) {
                            Bitmap thumb = Bitmap.createScaledBitmap(firstRendered, 256, 256, true);
                            thumb.compress(Bitmap.CompressFormat.PNG, 85, outThumb);
                            if (thumb != firstRendered) thumb.recycle();
                        }
                    }

                    for (Bitmap b : renderedFrames) {
                        if (b != null && !b.isRecycled()) b.recycle();
                    }
                    renderedFrames.clear();

                    synchronized (extractedFrames) {
                        for (Bitmap b : extractedFrames) {
                            if (b != null && !b.isRecycled()) b.recycle();
                        }
                        extractedFrames.clear();
                    }

                    prefs.edit().putBoolean("is_animated_" + currentPackId, true).apply();
                    ensureValidTrayIcon(dir);

                    runOnUiThread(() -> {
                        isCompiling.set(false);
                        dialog.dismiss();
                        loadExistingStickers();
                        Toast.makeText(this, "Animated Sticker Added (" + (animatedWebpBytes.length / 1024) + " KB)!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    synchronized (extractedFrames) {
                        for (Bitmap b : extractedFrames) {
                            if (b != null && !b.isRecycled()) b.recycle();
                        }
                        extractedFrames.clear();
                    }
                    runOnUiThread(() -> {
                        isCompiling.set(false);
                        dialog.dismiss();
                        Toast.makeText(this, "Failed to compile animated sticker", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        dialog.show();
    }

    private byte[] encodeGuaranteedUnder500KB(
            List<Bitmap> frames, int baseDelayMs, int cropMode, float radiusProgress, int smoothLevel,
            ProgressBar pb, Button btnBuild) throws IOException {

        final int maxAllowedBytes = 470000; // Safe margin below WhatsApp's 500,000 byte limit
        // Try full 512px first; if zoomed high-detail video + smoothing is still heavy, downscale internal texture
        int[][] attempts = new int[][]{
                {1, 512, 55, 38, 24, 14},
                {1, 384, 48, 32, 20, 10},
                {2, 384, 52, 36, 22, 12},
                {2, 288, 40, 25, 14, 6}
        };

        byte[] lastResult = null;
        for (int a = 0; a < attempts.length; a++) {
            int step = attempts[a][0];
            int internalRes = attempts[a][1];
            int[] qualities = new int[]{attempts[a][2], attempts[a][3], attempts[a][4], attempts[a][5]};

            List<Bitmap> subFrames = new ArrayList<>();
            for (int i = 0; i < frames.size(); i += step) {
                subFrames.add(frames.get(i));
            }
            if (subFrames.size() < 4 && frames.size() >= 4) {
                subFrames = frames.subList(0, 4);
            }

            int effectiveDelayMs = baseDelayMs * step;
            int perFrameBudget = (maxAllowedBytes - 2048) / Math.max(1, subFrames.size());
            List<byte[]> payloads = new ArrayList<>(subFrames.size());
            boolean[] hasAlpha = new boolean[]{false};

            for (int i = 0; i < subFrames.size(); i++) {
                Bitmap frameToEncode = subFrames.get(i);
                Bitmap tempScaled = null;
                if (internalRes < 512) {
                    // Downscale and upscale RGB detail while re-applying clean mask so 512x512 size & alpha stay exact
                    Bitmap down = Bitmap.createScaledBitmap(frameToEncode, internalRes, internalRes, true);
                    tempScaled = Bitmap.createScaledBitmap(down, 512, 512, true);
                    down.recycle();
                    reapplyAlphaFromSource(frameToEncode, tempScaled);
                    frameToEncode = tempScaled;
                }

                byte[] compressed = compressFrameWithQualities(frameToEncode, perFrameBudget, qualities);
                if (tempScaled != null) tempScaled.recycle();

                payloads.add(extractWebpFramePayload(compressed, hasAlpha));
            }

            lastResult = buildAnimatedWebpContainer(payloads, effectiveDelayMs, hasAlpha[0]);
            if (lastResult.length <= maxAllowedBytes) {
                runOnUiThread(() -> {
                    pb.setProgress(100);
                    btnBuild.setText("DONE 100%");
                });
                return lastResult;
            }
        }
        return lastResult;
    }

    private void reapplyAlphaFromSource(Bitmap alphaSrc, Bitmap target) {
        int[] srcPx = new int[512 * 512];
        int[] dstPx = new int[512 * 512];
        alphaSrc.getPixels(srcPx, 0, 512, 0, 0, 512, 512);
        target.getPixels(dstPx, 0, 512, 0, 0, 512, 512);
        for (int i = 0; i < srcPx.length; i++) {
            int a = srcPx[i] & 0xFF000000;
            if (a == 0) {
                dstPx[i] = 0;
            } else {
                dstPx[i] = a | (dstPx[i] & 0x00FFFFFF);
            }
        }
        target.setPixels(dstPx, 0, 512, 0, 0, 512, 512);
    }

    private byte[] compressFrameWithQualities(Bitmap bmp, int byteBudget, int[] qualities) {
        Bitmap.CompressFormat fmt = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;
        byte[] result = null;
        for (int q : qualities) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(fmt, q, baos);
            result = baos.toByteArray();
            if (result.length <= byteBudget) {
                break;
            }
        }
        return result;
    }

    private byte[] compressFrameWithinBudget(Bitmap bmp, int byteBudget) {
        Bitmap.CompressFormat fmt = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ? Bitmap.CompressFormat.WEBP_LOSSY
                : Bitmap.CompressFormat.WEBP;
        int[] qualities = new int[]{68, 52, 38, 26, 16};
        byte[] result = null;
        for (int q : qualities) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(fmt, q, baos);
            result = baos.toByteArray();
            if (result.length <= byteBudget) {
                break;
            }
        }
        return result;
    }


    /**
     * Parses a single-frame RIFF WEBP byte array, skipping any nested VP8X/ICCP/EXIF chunks
     * and extracting only ALPH, VP8 , or VP8L bitstream chunks with proper 2-byte RIFF padding.
     */
    private byte[] extractWebpFramePayload(byte[] webpBytes, boolean[] hasAlphaOut) throws IOException {
        if (webpBytes == null || webpBytes.length < 20) {
            throw new IOException("Invalid single WebP frame");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offset = 12;
        while (offset + 8 <= webpBytes.length) {
            String fourCC = new String(webpBytes, offset, 4, StandardCharsets.US_ASCII);
            int chunkSize = (webpBytes[offset + 4] & 0xFF)
                    | ((webpBytes[offset + 5] & 0xFF) << 8)
                    | ((webpBytes[offset + 6] & 0xFF) << 16)
                    | ((webpBytes[offset + 7] & 0xFF) << 24);
            if (chunkSize < 0 || offset + 8 + chunkSize > webpBytes.length) {
                break;
            }
            if ("ALPH".equals(fourCC) || "VP8 ".equals(fourCC) || "VP8L".equals(fourCC)) {
                if ("ALPH".equals(fourCC) || "VP8L".equals(fourCC)) {
                    hasAlphaOut[0] = true;
                }
                out.write(webpBytes, offset, 8 + chunkSize);
                if ((chunkSize & 1) == 1) {
                    out.write(0);
                }
            }
            offset += 8 + chunkSize + (chunkSize & 1);
        }
        return out.toByteArray();
    }

    private byte[] buildAnimatedWebpContainer(List<byte[]> framePayloads, int frameDelayMs, boolean hasAlpha) throws IOException {
        int safeDelayMs = Math.max(16, frameDelayMs);
        ByteArrayOutputStream anmfStream = new ByteArrayOutputStream();
        // Use 0x03 (Do not blend + Dispose to background) when alpha is present, or 0x02 (Do not blend) when opaque
        int anmfFlags = hasAlpha ? 0x03 : 0x02;

        for (byte[] payload : framePayloads) {
            int anmfPayloadSize = 16 + payload.length;
            anmfStream.write("ANMF".getBytes(StandardCharsets.US_ASCII));
            writeInt32LE(anmfStream, anmfPayloadSize);

            writeInt24LE(anmfStream, 0);
            writeInt24LE(anmfStream, 0);
            writeInt24LE(anmfStream, 511);
            writeInt24LE(anmfStream, 511);
            writeInt24LE(anmfStream, safeDelayMs);
            anmfStream.write(anmfFlags);

            anmfStream.write(payload);
            if ((anmfPayloadSize & 1) == 1) {
                anmfStream.write(0);
            }
        }

        byte[] allAnmfBytes = anmfStream.toByteArray();
        int riffPayloadSize = 36 + allAnmfBytes.length;

        ByteArrayOutputStream fileOut = new ByteArrayOutputStream(riffPayloadSize + 8);
        fileOut.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeInt32LE(fileOut, riffPayloadSize);
        fileOut.write("WEBP".getBytes(StandardCharsets.US_ASCII));

        // VP8X Chunk (18 bytes)
        fileOut.write("VP8X".getBytes(StandardCharsets.US_ASCII));
        writeInt32LE(fileOut, 10);
        int vp8xFlags = 0x02 | (hasAlpha ? 0x10 : 0x00);
        fileOut.write(vp8xFlags);
        writeInt24LE(fileOut, 0);
        writeInt24LE(fileOut, 511);
        writeInt24LE(fileOut, 511);

        // ANIM Chunk (14 bytes)
        fileOut.write("ANIM".getBytes(StandardCharsets.US_ASCII));
        writeInt32LE(fileOut, 6);
        writeInt32LE(fileOut, 0x00000000);
        fileOut.write(0);
        fileOut.write(0);

        fileOut.write(allAnmfBytes);
        return fileOut.toByteArray();
    }

    private void writeInt32LE(ByteArrayOutputStream out, int val) {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
        out.write((val >> 24) & 0xFF);
    }

    private void writeInt24LE(ByteArrayOutputStream out, int val) {
        out.write(val & 0xFF);
        out.write((val >> 8) & 0xFF);
        out.write((val >> 16) & 0xFF);
    }

    // =========================================================================================
    // STATIC IMAGE STUDIO
    // =========================================================================================
    private void showVisualCropStudio(Uri mediaUri) {
        float density = getResources().getDisplayMetrics().density;
        int p = (int) (24 * density);

        final int[] currentCropMode = {0}; // 0=Free, 1=1:1, 2=Oval, 3=Circle
        final float[] cornerRadiusProgress = {0f};
        final int[] smoothLevelProgress = {0};

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(p, p, p, p);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(cardColor);
        gd.setCornerRadius(60f);
        gd.setStroke(2, glassBorderColor);
        layout.setBackground(gd);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 0, 0, (int) (16 * density));

        FrameLayout iconScissors = new FrameLayout(this);
        iconScissors.setLayoutParams(new LinearLayout.LayoutParams((int) (24 * density), (int) (24 * density)));
        iconScissors.addView(new StickerIconView(this, StickerIconView.ICON_SCISSORS, accentColor));
        header.addView(iconScissors);

        TextView title = new TextView(this);
        title.setText(" Grid Sticker Studio");
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        header.addView(title);
        layout.addView(header);

        int boxSize = (int) (300 * density);

        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxLp.gravity = Gravity.CENTER;
        boxLp.setMargins(0, (int) (4 * density), 0, (int) (16 * density));
        wrapper.setLayoutParams(boxLp);
        GradientDrawable boxGd = new GradientDrawable();
        boxGd.setColor(isDarkTheme ? Color.parseColor("#141414") : Color.parseColor("#F8F8F8"));
        wrapper.setBackground(boxGd);

        FrameLayout previewBox = new FrameLayout(this) {
            Bitmap offscreen, maskBmp;
            Canvas osCanvas, maskCanvas;
            final Paint dstInPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                dstInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                maskPaint.setColor(Color.BLACK);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                int w = getWidth(), h = getHeight();
                if (w <= 0 || h <= 0) return;

                if (offscreen == null || offscreen.getWidth() != w || offscreen.getHeight() != h) {
                    if (offscreen != null) offscreen.recycle();
                    if (maskBmp != null) maskBmp.recycle();
                    offscreen = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    maskBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    osCanvas = new Canvas(offscreen);
                    maskCanvas = new Canvas(maskBmp);
                }

                offscreen.eraseColor(Color.TRANSPARENT);
                maskBmp.eraseColor(Color.TRANSPARENT);

                super.dispatchDraw(osCanvas);

                Path clipPath = new Path();
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    clipPath.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    clipPath.addRoundRect(new RectF(0, 0, w, h), rx, rx, Path.Direction.CW);
                }

                int smooth = smoothLevelProgress[0];
                if (smooth == 0) {
                    maskPaint.setMaskFilter(null);
                    maskCanvas.drawPath(clipPath, maskPaint);
                } else {
                    float baseBlur = 0f;
                    if (smooth == 1) baseBlur = 15f;
                    else if (smooth == 2) baseBlur = 35f;
                    else if (smooth == 3) baseBlur = 60f;
                    else if (smooth == 4) baseBlur = 100f;

                    float scale = (float) Math.max(w, h) / 512f;
                    maskPaint.setMaskFilter(new BlurMaskFilter(baseBlur * scale, BlurMaskFilter.Blur.NORMAL));

                    maskCanvas.save();
                    maskCanvas.clipPath(clipPath);
                    maskCanvas.drawPath(clipPath, maskPaint);
                    maskCanvas.restore();
                }

                osCanvas.drawBitmap(maskBmp, 0, 0, dstInPaint);
                canvas.drawBitmap(offscreen, 0, 0, null);
            }
        };
        previewBox.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        previewBox.addView(iv);
        wrapper.addView(previewBox);

        View gridOverlay = new View(this) {
            final Paint paintLines = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(Color.WHITE);
                setStrokeWidth(2f);
                setAlpha(160);
            }};
            final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG) {{
                setColor(accentColor);
                setStyle(Paint.Style.STROKE);
                setStrokeWidth(6f);
            }};

            @Override
            protected void onDraw(Canvas c) {
                super.onDraw(c);
                int w = getWidth(), h = getHeight();
                if (w == 0 || h == 0) return;

                Path clipPath = new Path();
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    clipPath.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    clipPath.addRoundRect(new RectF(0, 0, w, h), rx, rx, Path.Direction.CW);
                }

                c.save();
                c.clipPath(clipPath);
                c.drawLine(w / 3f, 0, w / 3f, h, paintLines);
                c.drawLine(w * 2 / 3f, 0, w * 2 / 3f, h, paintLines);
                c.drawLine(0, h / 3f, w, h / 3f, paintLines);
                c.drawLine(0, h * 2 / 3f, w, h * 2 / 3f, paintLines);
                c.restore();

                float inset = border.getStrokeWidth() / 2f;
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    c.drawOval(new RectF(inset, inset, w - inset, h - inset), border);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    c.drawRoundRect(new RectF(inset, inset, w - inset, h - inset), rx, rx, border);
                }
            }
        };
        gridOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrapper.addView(gridOverlay);
        layout.addView(wrapper);

        final Bitmap[] previewBmp = {null};
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                previewBmp[0] = decodeSampledBitmapFromUri(mediaUri, 1024, 1024);
                if (previewBmp[0] != null) {
                    runOnUiThread(() -> {
                        iv.setImageBitmap(previewBmp[0]);
                        setupTouchAndPresets(iv, previewBmp[0], wrapper, boxSize, currentCropMode[0]);
                    });
                }
            } catch (Exception ignored) {}
        }).start();

        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.HORIZONTAL);
        radiusRow.setGravity(Gravity.CENTER_VERTICAL);
        radiusRow.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (12 * density));

        TextView tvRadius = new TextView(this);
        tvRadius.setText("Corners");
        tvRadius.setTextColor(textColor);
        tvRadius.setTypeface(null, Typeface.BOLD);
        tvRadius.setTextSize(13f);
        tvRadius.setMinimumWidth((int) (60 * density));
        radiusRow.addView(tvRadius);

        SeekBar seekBarRadius = new SeekBar(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins((int) (12 * density), 0, 0, 0);
        seekBarRadius.setLayoutParams(sbLp);
        seekBarRadius.setMax(100);
        seekBarRadius.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarRadius.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBarRadius.setThumbTintList(ColorStateList.valueOf(accentColor));
        }
        seekBarRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                cornerRadiusProgress[0] = progress;
                previewBox.invalidate();
                gridOverlay.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        radiusRow.addView(seekBarRadius);
        layout.addView(radiusRow);

        LinearLayout smoothRow = new LinearLayout(this);
        smoothRow.setOrientation(LinearLayout.HORIZONTAL);
        smoothRow.setGravity(Gravity.CENTER_VERTICAL);
        smoothRow.setPadding((int) (4 * density), 0, (int) (4 * density), (int) (16 * density));

        TextView tvSmooth = new TextView(this);
        tvSmooth.setText("Smooth");
        tvSmooth.setTextColor(textColor);
        tvSmooth.setTypeface(null, Typeface.BOLD);
        tvSmooth.setTextSize(13f);
        tvSmooth.setMinimumWidth((int) (60 * density));
        smoothRow.addView(tvSmooth);

        SeekBar seekBarSmooth = new SeekBar(this);
        LinearLayout.LayoutParams sbSmoothLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbSmoothLp.setMargins((int) (12 * density), 0, 0, 0);
        seekBarSmooth.setLayoutParams(sbSmoothLp);
        seekBarSmooth.setMax(4);
        seekBarSmooth.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarSmooth.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBarSmooth.setThumbTintList(ColorStateList.valueOf(accentColor));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GradientDrawable tick = new GradientDrawable();
            tick.setShape(GradientDrawable.OVAL);
            tick.setSize((int) (6 * density), (int) (6 * density));
            tick.setColor(Color.parseColor("#888888"));
            seekBarSmooth.setTickMark(tick);
            seekBarSmooth.setTickMarkTintList(null);
        }
        seekBarSmooth.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                smoothLevelProgress[0] = progress;
                previewBox.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        smoothRow.addView(seekBarSmooth);
        layout.addView(smoothRow);

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setPadding(0, 0, 0, (int) (16 * density));

        String[] cNames = {"Free", "1:1", "Oval", "Circle"};
        final Button[] chipBtns = new Button[4];

        for (int i = 0; i < 4; i++) {
            Button b = new Button(this);
            b.setText(cNames[i]);
            b.setTextSize(11f);
            b.setAllCaps(false);
            b.setTypeface(null, Typeface.BOLD);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                b.setStateListAnimator(null);
                b.setElevation(0);
            }

            GradientDrawable cGd = new GradientDrawable();
            cGd.setCornerRadius(100f);
            cGd.setStroke(2, glassBorderColor);
            if (i == 0) {
                cGd.setColor(accentColor);
                b.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
            } else {
                cGd.setColor(panelColor);
                b.setTextColor(textColor);
            }
            b.setBackgroundTintList(null);
            b.setBackground(cGd);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, (int) (44 * density), 1f);
            clp.setMargins((int) (4 * density), 0, (int) (4 * density), 0);
            b.setLayoutParams(clp);
            chipBtns[i] = b;

            int finalI = i;
            b.setOnClickListener(v -> {
                if (previewBmp[0] != null) {
                    currentCropMode[0] = finalI;
                    for (int j = 0; j < 4; j++) {
                        GradientDrawable gdBtn = (GradientDrawable) chipBtns[j].getBackground();
                        if (j == finalI) {
                            gdBtn.setColor(accentColor);
                            chipBtns[j].setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
                        } else {
                            gdBtn.setColor(panelColor);
                            chipBtns[j].setTextColor(textColor);
                        }
                    }

                    seekBarRadius.setEnabled(finalI < 2);
                    seekBarRadius.setAlpha((finalI < 2) ? 1.0f : 0.4f);

                    setupTouchAndPresets(iv, previewBmp[0], wrapper, boxSize, finalI);
                    previewBox.invalidate();
                    gridOverlay.invalidate();
                }
            });
            chipRow.addView(b);
        }
        layout.addView(chipRow);

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER);
        toolsRow.setPadding(0, 0, 0, (int) (24 * density));
        String[] tNames = {"Rotate", "Flip H", "Flip V"};
        for (int i = 0; i < 3; i++) {
            LinearLayout bLayout = new LinearLayout(this);
            bLayout.setOrientation(LinearLayout.HORIZONTAL);
            bLayout.setGravity(Gravity.CENTER);
            GradientDrawable tGd = new GradientDrawable();
            tGd.setColor(panelColor);
            tGd.setCornerRadius(100f);
            tGd.setStroke(2, glassBorderColor);
            bLayout.setBackground(tGd);
            bLayout.setClickable(true);
            bLayout.setFocusable(true);

            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, (int) (48 * density), 1f);
            tLp.setMargins((int) (5 * density), 0, (int) (5 * density), 0);
            bLayout.setLayoutParams(tLp);

            FrameLayout icHolder = new FrameLayout(this);
            int icSize = (int) (18 * density);
            LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(icSize, icSize);
            icLp.setMargins(0, 0, (int) (6 * density), 0);
            icHolder.setLayoutParams(icLp);
            StickerIconView sIcon = new StickerIconView(
                    this, i == 0 ? StickerIconView.ICON_ROTATE : StickerIconView.ICON_FLIP, textColor);
            if (i == 2) sIcon.setRotation(90f);
            icHolder.addView(sIcon);
            bLayout.addView(icHolder);

            TextView tv = new TextView(this);
            tv.setText(tNames[i]);
            tv.setTextSize(12f);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextColor(textColor);
            bLayout.addView(tv);

            int finalI = i;
            bLayout.setOnClickListener(v -> {
                if (previewBmp[0] != null) {
                    pb.setVisibility(View.VISIBLE);
                    new Thread(() -> {
                        Matrix m = new Matrix();
                        if (finalI == 0) m.postRotate(90);
                        else if (finalI == 1) m.preScale(-1.0f, 1.0f);
                        else if (finalI == 2) m.preScale(1.0f, -1.0f);

                        Bitmap newBmp = Bitmap.createBitmap(
                                previewBmp[0], 0, 0, previewBmp[0].getWidth(), previewBmp[0].getHeight(), m, true);
                        if (previewBmp[0] != newBmp) {
                            previewBmp[0].recycle();
                            previewBmp[0] = newBmp;
                        }
                        runOnUiThread(() -> {
                            iv.setImageBitmap(previewBmp[0]);
                            setupTouchAndPresets(iv, previewBmp[0], wrapper, boxSize, currentCropMode[0]);
                            previewBox.invalidate();
                            gridOverlay.invalidate();
                            pb.setVisibility(View.GONE);
                        });
                    }).start();
                }
            });
            toolsRow.addView(bLayout);
        }
        layout.addView(toolsRow);

        layout.addView(pb);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, (int) (10 * density), 0, 0);

        Button btnCancel = new Button(this);
        btnCancel.setText("CANCEL");
        btnCancel.setTextColor(Color.WHITE);
        btnCancel.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnCancel.setStateListAnimator(null);
            btnCancel.setElevation(0);
        }
        GradientDrawable gdCancel = new GradientDrawable();
        gdCancel.setColor(Color.parseColor("#FF3B30"));
        gdCancel.setCornerRadius(100f);
        btnCancel.setBackgroundTintList(null);
        btnCancel.setBackground(gdCancel);
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, (int) (56 * density), 1f);
        lpC.setMargins(0, 0, (int) (10 * density), 0);
        btnCancel.setLayoutParams(lpC);

        Button btnBuild = new Button(this);
        btnBuild.setText("COMPILE STICKER");
        btnBuild.setTextColor(isDarkTheme ? Color.BLACK : Color.WHITE);
        btnBuild.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            btnBuild.setStateListAnimator(null);
            btnBuild.setElevation(0);
        }
        GradientDrawable gdBuild = new GradientDrawable();
        gdBuild.setColor(accentColor);
        gdBuild.setCornerRadius(100f);
        btnBuild.setBackgroundTintList(null);
        btnBuild.setBackground(gdBuild);
        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, (int) (56 * density), 1f);
        lpB.setMargins((int) (10 * density), 0, 0, 0);
        btnBuild.setLayoutParams(lpB);

        btnRow.addView(btnCancel);
        btnRow.addView(btnBuild);
        layout.addView(btnRow);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnBuild.setOnClickListener(v -> {
            pb.setVisibility(View.VISIBLE);
            btnBuild.setEnabled(false);
            btnCancel.setEnabled(false);
            Matrix userMatrix = new Matrix(iv.getImageMatrix());
            int boxW = wrapper.getWidth() > 0 ? wrapper.getWidth() : boxSize;
            int boxH = wrapper.getHeight() > 0 ? wrapper.getHeight() : boxSize;
            new Thread(() -> {
                try {
                    File dir = new File(getFilesDir(), "stickers/" + currentPackId);
                    if (!dir.exists() && !dir.mkdirs()) return;
                    File[] existingFiles = dir.listFiles((d, name) ->
                            name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
                    int nextId = 1;
                    if (existingFiles != null) {
                        for (File f : existingFiles) {
                            try {
                                int id = Integer.parseInt(f.getName().replace(".webp", ""));
                                if (id >= nextId) nextId = id + 1;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    File tmpFile = new File(dir, nextId + "_tmp.webp");
                    File stickerFile = new File(dir, nextId + ".webp");
                    File thumbFile = new File(dir, nextId + "_thumb.png");
                    Bitmap.CompressFormat webpFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            ? Bitmap.CompressFormat.WEBP_LOSSY
                            : Bitmap.CompressFormat.WEBP;

                    Bitmap orig = previewBmp[0];
                    if (orig == null) throw new Exception();

                    Bitmap cropped = applyMatrixToSticker(
                            orig, userMatrix, boxW, boxH, currentCropMode[0], cornerRadiusProgress[0], smoothLevelProgress[0]);

                    byte[] staticWebpBytes = compressFrameWithQualities(cropped, 95000, new int[]{80, 65, 50, 35, 20});
                    FileOutputStream outSticker = new FileOutputStream(tmpFile);
                    outSticker.write(staticWebpBytes);
                    outSticker.close();
                    if (tmpFile.exists() && tmpFile.length() > 100) {
                        tmpFile.renameTo(stickerFile);
                    } else {
                        throw new Exception("Write failed");
                    }
                    FileOutputStream outThumb = new FileOutputStream(thumbFile);
                    Bitmap thumb = Bitmap.createScaledBitmap(cropped, 256, 256, true);
                    thumb.compress(Bitmap.CompressFormat.PNG, 100, outThumb);
                    outThumb.close();
                    thumb.recycle();
                    cropped.recycle();
                    ensureValidTrayIcon(dir);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadExistingStickers();
                        Toast.makeText(this, "Sticker Added!", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(this, "Failed to compile sticker", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        dialog.show();
    }

    private void setupTouchAndPresets(ImageView iv, Bitmap bmp, FrameLayout container, int boxSize, int cropMode) {
        applyRatioChipMatrix(iv, bmp, container, boxSize, cropMode);
        final Matrix matrix = new Matrix(iv.getImageMatrix());
        final Matrix savedMatrix = new Matrix();
        final PointF start = new PointF();
        final PointF mid = new PointF();
        final float[] oldDist = {1f};
        final int[] mode = {0};

        container.setOnTouchListener((v, event) -> {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode[0] = 1;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    oldDist[0] = spacing(event);
                    if (oldDist[0] > 10f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event);
                        mode[0] = 2;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    mode[0] = 0;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mode[0] == 1) {
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                    } else if (mode[0] == 2) {
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            matrix.set(savedMatrix);
                            float scale = newDist / oldDist[0];
                            matrix.postScale(scale, scale, mid.x, mid.y);

                            PointF newMid = new PointF();
                            midPoint(newMid, event);
                            matrix.postTranslate(newMid.x - mid.x, newMid.y - mid.y);
                        }
                    }
                    break;
            }
            iv.setImageMatrix(matrix);
            iv.invalidate();
            ((View) iv.getParent()).invalidate();
            container.invalidate();
            return true;
        });
    }

    private void applyRatioChipMatrix(ImageView iv, Bitmap bmp, FrameLayout container, int maxBoxSize, int cropMode) {
        Matrix m = new Matrix();
        int bw = bmp.getWidth(), bh = bmp.getHeight();
        float ratio = (float) bw / bh;
        int targetW = maxBoxSize, targetH = maxBoxSize;

        if (cropMode == 0 || cropMode == 2) {
            if (ratio >= 1f) targetH = Math.max(200, (int) (maxBoxSize / ratio));
            else targetW = Math.max(200, (int) (maxBoxSize * ratio));
        }

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) container.getLayoutParams();
        lp.width = targetW;
        lp.height = targetH;
        container.setLayoutParams(lp);
        container.requestLayout();

        if (cropMode == 0 || cropMode == 2) {
            float scale = (float) targetW / bw;
            m.postScale(scale, scale);
        } else {
            float scale = Math.max((float) maxBoxSize / bw, (float) maxBoxSize / bh);
            m.postScale(scale, scale);
            m.postTranslate((maxBoxSize - bw * scale) / 2f, (maxBoxSize - bh * scale) / 2f);
        }
        iv.setImageMatrix(m);
    }

    private Bitmap applyMatrixToSticker(Bitmap orig, Matrix userMatrix, int boxW, int boxH, int cropMode, float radiusProgress, int smoothLevel) {
        Bitmap finalBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(finalBmp);
        int maxDim = Math.max(boxW, boxH);
        float scale = 512f / maxDim;
        float dx = (512f - (boxW * scale)) / 2f;
        float dy = (512f - (boxH * scale)) / 2f;
        float fw = boxW * scale;
        float fh = boxH * scale;

        Path clipPath = new Path();
        if (cropMode == 2 || cropMode == 3) {
            clipPath.addOval(new RectF(dx, dy, dx + fw, dy + fh), Path.Direction.CW);
        } else {
            float maxRadius = Math.min(fw, fh) / 2f;
            float rx = maxRadius * (radiusProgress / 100f);
            clipPath.addRoundRect(new RectF(dx, dy, dx + fw, dy + fh), rx, rx, Path.Direction.CW);
        }

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        canvas.drawBitmap(orig, userMatrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.restore();

        if (smoothLevel > 0) {
            // Render blur mask at 128x128 (4x4 blocks) and quantize alpha steps so lossless ALPH chunk stays tiny
            Bitmap smallMask = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Canvas smallCanvas = new Canvas(smallMask);
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setColor(Color.BLACK);

            float blurRadius = 4f;
            if (smoothLevel == 1) blurRadius = 4f;
            else if (smoothLevel == 2) blurRadius = 9f;
            else if (smoothLevel == 3) blurRadius = 15f;
            else if (smoothLevel == 4) blurRadius = 24f;

            maskPaint.setMaskFilter(new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL));

            Path smallPath = new Path();
            Matrix scaleDown = new Matrix();
            scaleDown.setScale(0.25f, 0.25f);
            clipPath.transform(scaleDown, smallPath);

            smallCanvas.save();
            smallCanvas.clipPath(smallPath);
            smallCanvas.drawPath(smallPath, maskPaint);
            smallCanvas.restore();

            int[] mPixels = new int[128 * 128];
            smallMask.getPixels(mPixels, 0, 128, 0, 0, 128, 128);
            for (int i = 0; i < mPixels.length; i++) {
                int a = (mPixels[i] >>> 24) & 0xFF;
                if (a < 16) a = 0;
                else if (a > 240) a = 255;
                else a = (a >> 5) * 36;
                mPixels[i] = (a << 24);
            }
            smallMask.setPixels(mPixels, 0, 128, 0, 0, 128, 128);

            // Upscale with nearest-neighbor (filter=false) to preserve 4x4 horizontal run-lengths for WebP LZ77
            Bitmap maskBmp = Bitmap.createScaledBitmap(smallMask, 512, 512, false);
            smallMask.recycle();

            Paint dstInPaint = new Paint();
            dstInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            canvas.drawBitmap(maskBmp, 0, 0, dstInPaint);
            maskBmp.recycle();
        }

        return finalBmp;
    }

    private float spacing(MotionEvent e) {
        float x = e.getX(0) - e.getX(1);
        float y = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF p, MotionEvent e) {
        p.set((e.getX(0) + e.getX(1)) / 2, (e.getY(0) + e.getY(1)) / 2);
    }

    private void applyGlassDialogStyle(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            GradientDrawable bgGd = new GradientDrawable();
            bgGd.setColor(cardColor);
            bgGd.setCornerRadius(60f);
            bgGd.setStroke(2, glassBorderColor);
            dialog.setOnShowListener(di -> {
                Window window = dialog.getWindow();
                if (window != null && window.getDecorView() != null) {
                    window.getDecorView().setBackground(bgGd);
                    setDialogTextColor(window.getDecorView(), textColor);
                }
                Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (btnPos != null) btnPos.setTextColor(Color.parseColor("#FF3B30"));
                Button btnNeg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (btnNeg != null) btnNeg.setTextColor(textColor);
            });
        }
    }

    private void setDialogTextColor(View view, int color) {
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) {
            ((TextView) view).setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) setDialogTextColor(vg.getChildAt(i), color);
        }
    }

    // =========================================================================================
    // CUSTOM TIMELINE TRIMMER VIEW WITH FILMSTRIP & 6-SECOND CLAMPED HANDLES
    // =========================================================================================
    public interface OnTrimChangeListener {
        void onTrimChanged(long startMs, long endMs, long seekTargetMs, boolean isDragging);
    }

    public static class VideoTrimTimelineView extends View {
        private final long totalDurationMs;
        private final long maxWindowMs;
        private final long minWindowMs;
        private long startMs = 0L;
        private long endMs;
        private long playheadMs = 0L;

        private final List<Bitmap> thumbnails = new ArrayList<>();
        private final Paint thumbPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final float handleWidthPx;
        private final float cornerRadiusPx;
        private int dragMode = 0; // 0=none, 1=left, 2=right, 3=pan window
        private float lastTouchX = 0f;
        private final OnTrimChangeListener listener;

        public VideoTrimTimelineView(Context ctx, long totalDur, long maxWin, long minWin,
                                     int accentCol, int panelCol, int txtCol, OnTrimChangeListener l) {
            super(ctx);
            this.totalDurationMs = Math.max(1L, totalDur);
            this.maxWindowMs = maxWin;
            this.minWindowMs = minWin;
            this.endMs = Math.min(totalDurationMs, maxWindowMs);
            this.listener = l;

            float d = ctx.getResources().getDisplayMetrics().density;
            handleWidthPx = 18f * d;
            cornerRadiusPx = 12f * d;

            bgPaint.setColor(panelCol);
            dimPaint.setColor(Color.parseColor("#B3000000"));

            borderPaint.setColor(accentCol);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3.5f * d);

            handlePaint.setColor(accentCol);
            handlePaint.setStyle(Paint.Style.FILL);

            gripPaint.setColor(Color.BLACK);
            gripPaint.setStrokeWidth(2f * d);
            gripPaint.setStrokeCap(Paint.Cap.ROUND);

            playheadPaint.setColor(Color.WHITE);
            playheadPaint.setStrokeWidth(3f * d);
            playheadPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        public void addThumbnail(Bitmap bmp) {
            thumbnails.add(bmp);
            invalidate();
        }

        public void setPlayheadMs(long ms) {
            this.playheadMs = ms;
            invalidate();
        }

        public void recycleThumbnails() {
            for (Bitmap b : thumbnails) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
            thumbnails.clear();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            float usableW = w - 2 * handleWidthPx;
            if (usableW <= 10) return;

            Path clipBg = new Path();
            clipBg.addRoundRect(new RectF(0, 0, w, h), cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipBg);

            canvas.drawRect(0, 0, w, h, bgPaint);

            // Draw Filmstrip Thumbnails
            if (!thumbnails.isEmpty()) {
                float slotW = (float) w / 8f;
                for (int i = 0; i < thumbnails.size(); i++) {
                    Bitmap b = thumbnails.get(i);
                    if (b != null && !b.isRecycled()) {
                        float left = i * slotW;
                        RectF dst = new RectF(left, 0, left + slotW, h);
                        canvas.drawBitmap(b, null, dst, thumbPaint);
                    }
                }
            }

            float startX = handleWidthPx + (startMs / (float) totalDurationMs) * usableW;
            float endX = handleWidthPx + (endMs / (float) totalDurationMs) * usableW;

            // Dim unselected left/right regions
            canvas.drawRect(0, 0, startX, h, dimPaint);
            canvas.drawRect(endX, 0, w, h, dimPaint);
            canvas.restore();

            // Active Selection Frame & Left/Right Handles
            RectF leftHandle = new RectF(startX - handleWidthPx, 0, startX, h);
            RectF rightHandle = new RectF(endX, 0, endX + handleWidthPx, h);

            canvas.drawRoundRect(leftHandle, cornerRadiusPx, cornerRadiusPx, handlePaint);
            canvas.drawRoundRect(rightHandle, cornerRadiusPx, cornerRadiusPx, handlePaint);

            // Top and Bottom Border Bars connecting handles
            canvas.drawLine(startX - handleWidthPx / 2f, borderPaint.getStrokeWidth() / 2f,
                    endX + handleWidthPx / 2f, borderPaint.getStrokeWidth() / 2f, borderPaint);
            canvas.drawLine(startX - handleWidthPx / 2f, h - borderPaint.getStrokeWidth() / 2f,
                    endX + handleWidthPx / 2f, h - borderPaint.getStrokeWidth() / 2f, borderPaint);

            // Handle Grip Lines
            float lcx = leftHandle.centerX();
            float rcx = rightHandle.centerX();
            canvas.drawLine(lcx, h * 0.32f, lcx, h * 0.68f, gripPaint);
            canvas.drawLine(rcx, h * 0.32f, rcx, h * 0.68f, gripPaint);

            // Playhead Line
            long clampedPlayhead = Math.max(startMs, Math.min(endMs, playheadMs));
            float playX = handleWidthPx + (clampedPlayhead / (float) totalDurationMs) * usableW;
            canvas.drawLine(playX, 4f, playX, h - 4f, playheadPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int w = getWidth();
            float usableW = w - 2 * handleWidthPx;
            if (usableW <= 10) return false;

            float startX = handleWidthPx + (startMs / (float) totalDurationMs) * usableW;
            float endX = handleWidthPx + (endMs / (float) totalDurationMs) * usableW;
            float x = event.getX();

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    getParent().requestDisallowInterceptTouchEvent(true);
                    lastTouchX = x;
                    float hitSlop = handleWidthPx * 1.6f;
                    if (Math.abs(x - (startX - handleWidthPx / 2f)) <= hitSlop) {
                        dragMode = 1;
                    } else if (Math.abs(x - (endX + handleWidthPx / 2f)) <= hitSlop) {
                        dragMode = 2;
                    } else if (x > startX && x < endX) {
                        dragMode = 3;
                    } else {
                        dragMode = 0;
                    }
                    return dragMode != 0;

                case MotionEvent.ACTION_MOVE:
                    if (dragMode == 0) return false;
                    float dx = x - lastTouchX;
                    lastTouchX = x;
                    long deltaMs = (long) ((dx / usableW) * totalDurationMs);
                    long seekTarget = startMs;

                    if (dragMode == 1) { // Left Handle
                        startMs = Math.max(0L, Math.min(startMs + deltaMs, endMs - minWindowMs));
                        if (endMs - startMs > maxWindowMs) {
                            endMs = startMs + maxWindowMs;
                        }
                        seekTarget = startMs;
                    } else if (dragMode == 2) { // Right Handle
                        endMs = Math.min(totalDurationMs, Math.max(endMs + deltaMs, startMs + minWindowMs));
                        if (endMs - startMs > maxWindowMs) {
                            startMs = endMs - maxWindowMs;
                        }
                        seekTarget = endMs;
                    } else if (dragMode == 3) { // Slide entire window
                        long winLen = endMs - startMs;
                        long newStart = Math.max(0L, Math.min(totalDurationMs - winLen, startMs + deltaMs));
                        startMs = newStart;
                        endMs = newStart + winLen;
                        seekTarget = startMs;
                    }

                    playheadMs = seekTarget;
                    invalidate();
                    if (listener != null) {
                        listener.onTrimChanged(startMs, endMs, seekTarget, true);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragMode != 0) {
                        dragMode = 0;
                        if (listener != null) {
                            listener.onTrimChanged(startMs, endMs, startMs, false);
                        }
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // =========================================================================================
    // PURE VECTOR ICON RENDERER (ZERO EMOJIS)
    // =========================================================================================
    public static class StickerIconView extends View {
        public static final int ICON_PACK = 0;
        public static final int ICON_ADD = 1;
        public static final int ICON_TRASH = 2;
        public static final int ICON_SEND = 3;
        public static final int ICON_IMAGE = 4;
        public static final int ICON_CHEVRON = 5;
        public static final int ICON_DOTS = 6;
        public static final int ICON_SCISSORS = 7;
        public static final int ICON_VIDEO = 8;
        public static final int ICON_PLAY = 9;
        public static final int ICON_ROTATE = 10;
        public static final int ICON_FLIP = 11;

        private int iconType;
        private final Paint paint;
        private final int baseColor;

        public StickerIconView(Context context, int type, int color) {
            super(context);
            this.iconType = type;
            this.baseColor = color;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        public void setIconType(int type) {
            this.iconType = type;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            Path p = new Path();
            paint.setColor(baseColor);
            paint.setStyle(Paint.Style.STROKE);

            switch (iconType) {
                case ICON_PACK:
                    p.moveTo(cx - 10, cy + 5); p.lineTo(cx, cy + 12); p.lineTo(cx + 10, cy + 5);
                    p.moveTo(cx - 10, cy - 3); p.lineTo(cx, cy + 4); p.lineTo(cx + 10, cy - 3);
                    p.moveTo(cx, cy - 12); p.lineTo(cx - 10, cy - 5); p.lineTo(cx, cy + 2); p.lineTo(cx + 10, cy - 5); p.close();
                    canvas.drawPath(p, paint);
                    break;
                case ICON_ADD:
                    canvas.drawLine(cx, cy - 12, cx, cy + 12, paint);
                    canvas.drawLine(cx - 12, cy, cx + 12, cy, paint);
                    break;
                case ICON_TRASH:
                    canvas.drawLine(cx - 10, cy - 8, cx + 10, cy - 8, paint);
                    canvas.drawLine(cx - 4, cy - 8, cx - 4, cy - 12, paint);
                    canvas.drawLine(cx + 4, cy - 8, cx + 4, cy - 12, paint);
                    canvas.drawRoundRect(cx - 8, cy - 8, cx + 8, cy + 12, 4f, 4f, paint);
                    canvas.drawLine(cx - 3, cy - 2, cx - 3, cy + 6, paint);
                    canvas.drawLine(cx + 3, cy - 2, cx + 3, cy + 6, paint);
                    break;
                case ICON_SEND:
                    paint.setStyle(Paint.Style.FILL);
                    p.moveTo(cx - 10, cy - 8);
                    p.lineTo(cx + 12, cy);
                    p.lineTo(cx - 10, cy + 8);
                    p.lineTo(cx - 6, cy);
                    p.close();
                    canvas.drawPath(p, paint);
                    break;
                case ICON_IMAGE:
                    canvas.drawRoundRect(cx - 11, cy - 11, cx + 11, cy + 11, 5f, 5f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx + 4, cy - 4, 2.5f, paint);
                    p.moveTo(cx - 10, cy + 9);
                    p.lineTo(cx - 2, cy - 1);
                    p.lineTo(cx + 5, cy + 7);
                    p.lineTo(cx + 10, cy + 2);
                    p.lineTo(cx + 10, cy + 10);
                    p.lineTo(cx - 10, cy + 10);
                    p.close();
                    canvas.drawPath(p, paint);
                    break;
                case ICON_CHEVRON:
                    p.moveTo(cx - 4, cy - 8);
                    p.lineTo(cx + 4, cy);
                    p.lineTo(cx - 4, cy + 8);
                    canvas.drawPath(p, paint);
                    break;
                case ICON_DOTS:
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy - 8, 3.5f, paint);
                    canvas.drawCircle(cx, cy, 3.5f, paint);
                    canvas.drawCircle(cx, cy + 8, 3.5f, paint);
                    break;
                case ICON_SCISSORS:
                    canvas.drawCircle(cx - 6, cy + 6, 4f, paint);
                    canvas.drawCircle(cx + 6, cy + 6, 4f, paint);
                    canvas.drawLine(cx - 4, cy + 3, cx + 8, cy - 8, paint);
                    canvas.drawLine(cx + 4, cy + 3, cx - 8, cy - 8, paint);
                    break;
                case ICON_VIDEO:
                    canvas.drawRoundRect(cx - 12, cy - 8, cx + 4, cy + 8, 4f, 4f, paint);
                    p.moveTo(cx + 4, cy - 3);
                    p.lineTo(cx + 12, cy - 7);
                    p.lineTo(cx + 12, cy + 7);
                    p.lineTo(cx + 4, cy + 3);
                    p.close();
                    canvas.drawPath(p, paint);
                    break;
                case ICON_PLAY:
                    paint.setStyle(Paint.Style.FILL);
                    p.moveTo(cx - 4, cy - 7);
                    p.lineTo(cx + 7, cy);
                    p.lineTo(cx - 4, cy + 7);
                    p.close();
                    canvas.drawPath(p, paint);
                    break;
                case ICON_ROTATE:
                    RectF arcRect = new RectF(cx - 9, cy - 9, cx + 9, cy + 9);
                    canvas.drawArc(arcRect, -60, 270, false, paint);
                    p.moveTo(cx + 2, cy - 12);
                    p.lineTo(cx + 7, cy - 7);
                    p.lineTo(cx + 1, cy - 4);
                    canvas.drawPath(p, paint);
                    break;
                case ICON_FLIP:
                    canvas.drawLine(cx, cy - 11, cx, cy + 11, paint);
                    p.moveTo(cx - 4, cy - 7);
                    p.lineTo(cx - 11, cy);
                    p.lineTo(cx - 4, cy + 7);
                    p.moveTo(cx + 4, cy - 7);
                    p.lineTo(cx + 11, cy);
                    p.lineTo(cx + 4, cy + 7);
                    canvas.drawPath(p, paint);
                    break;
            }
        }
    }
}