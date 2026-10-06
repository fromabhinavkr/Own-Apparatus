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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
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
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

@SuppressWarnings("all")
@SuppressLint({"SetTextI18n", "SpellCheckingInspection", "ClickableViewAccessibility", "DrawAllocation"})
public class StickerMakerActivity extends AppCompatActivity {
    private LinearLayout btnAddWA;
    private TextView tvSubtitle, tvStatusText;
    private View statusDot;
    private EditText etTitle;
    private GridLayout stickerGrid;
    private LinearLayout packSelectorContainer, mainStickerCard;
    private HorizontalScrollView packScrollView;
    private FrameLayout btnPackOptions;

    private int stickerCount = 0;
    private boolean isDarkTheme;
    private int bgColor, textColor, cardColor, panelColor, accentColor, subTextColor, glassBorderColor;
    private SharedPreferences prefs;

    private String deviceId;
    private String currentPackId;
    private int staticPackCount = 3;
    private boolean isSwitchingPack = false;

    private final ActivityResultLauncher<Intent> pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) showVisualCropStudio(uri);
        }
    });

    private final ActivityResultLauncher<Intent> addStickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_CANCELED && result.getData() != null) {
            String validationError = result.getData().getStringExtra("validation_error");
            if (validationError != null)
                Toast.makeText(this, "WA Rejected: " + validationError, Toast.LENGTH_LONG).show();
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

        // --- MODERN COLOR PALETTES ---
        if (themeState == 0) { // Light Mode
            bgColor = Color.parseColor("#FFFFFF");
            cardColor = Color.parseColor("#F3F4F6");
            panelColor = Color.parseColor("#E5E7EB");
            textColor = Color.parseColor("#1C1C1E");
            subTextColor = Color.parseColor("#6B7280");
            accentColor = Color.parseColor("#34C759"); // Apple Green
            glassBorderColor = Color.parseColor("#D1D1D6");
        } else if (themeState == 1) { // Standard Dark Mode
            bgColor = Color.parseColor("#1C1C1E");
            cardColor = Color.parseColor("#2C2C2E");
            panelColor = Color.parseColor("#3A3A3C");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#8E8E93");
            accentColor = Color.parseColor("#32D74B"); // Lime Green
            glassBorderColor = Color.parseColor("#33FFFFFF");
        } else { // Star Mode (AMOLED Pure Black)
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#111827");
            panelColor = Color.parseColor("#1F2937");
            textColor = Color.WHITE;
            subTextColor = Color.parseColor("#9CA3AF");
            accentColor = Color.parseColor("#A3E635"); // Lime Green
            glassBorderColor = Color.parseColor("#33FFFFFF");
        }

        View root = findViewById(R.id.stickerMakerRoot);
        root.setBackgroundColor(bgColor);

        etTitle = findViewById(R.id.etStickerTitle);
        tvSubtitle = findViewById(R.id.tvStickerSubtitle);
        tvStatusText = findViewById(R.id.tvStatusText);
        statusDot = findViewById(R.id.statusDot);
        mainStickerCard = findViewById(R.id.mainStickerCard);
        stickerGrid = findViewById(R.id.stickerGrid);
        packSelectorContainer = findViewById(R.id.packSelectorContainer);
        packScrollView = findViewById(R.id.packScrollView);
        btnPackOptions = findViewById(R.id.btnPackOptions);

        btnAddWA = findViewById(R.id.btnAddWhatsApp);

        // Setup Main Card styling
        GradientDrawable gdMainCard = new GradientDrawable();
        gdMainCard.setColor(cardColor);
        gdMainCard.setCornerRadius(60f);
        mainStickerCard.setBackground(gdMainCard);

        etTitle.setTextColor(textColor);
        etTitle.setHintTextColor(subTextColor);

        // --- SUBTITLE TEXT ---
        tvSubtitle.setTextColor(subTextColor);
        tvSubtitle.setText("Long press to reorder sticker");

        // --- TRASH ICON FOR PACK OPTIONS ---
        btnPackOptions.addView(new StickerIconView(this, StickerIconView.ICON_TRASH, subTextColor));
        GradientDrawable gdDots = new GradientDrawable();
        gdDots.setColor(panelColor);
        gdDots.setShape(GradientDrawable.OVAL);
        btnPackOptions.setBackground(gdDots);
        btnPackOptions.setOnClickListener(v -> showDeletePackDialog());

        // Setup Bottom Button Styling
        setupBottomButton(btnAddWA, StickerIconView.ICON_SEND, "Publish to WhatsApp", accentColor, isDarkTheme ? Color.BLACK : Color.WHITE);

        deviceId = prefs.getString("device_sticker_id", "");
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            prefs.edit().putString("device_sticker_id", deviceId).apply();

            // Migration
            int count = prefs.getInt("static_pack_count", 3);
            for (int i = 1; i <= count; i++) {
                String oldId = "ownphoto_pack_" + i;
                String newId = "ownpack_" + deviceId + "_" + i;
                File oldDir = new File(getFilesDir(), "stickers/" + oldId);
                File newDir = new File(getFilesDir(), "stickers/" + newId);
                if (oldDir.exists()) oldDir.renameTo(newDir);
                String oldName = prefs.getString("pack_name_" + oldId, "");
                int oldVer = prefs.getInt("sticker_version_" + oldId, 3);
                prefs.edit().putString("pack_name_" + newId, oldName).putInt("sticker_version_" + newId, oldVer).remove("pack_name_" + oldId).remove("sticker_version_" + oldId).apply();
            }
        }

        staticPackCount = prefs.getInt("static_pack_count", 3);
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
                if (currentName.trim().isEmpty()) currentName = "Pack " + currentPackId.substring(currentPackId.lastIndexOf("_") + 1);

                int currentVersion = prefs.getInt("sticker_version_" + currentPackId, 3);
                prefs.edit().putInt("sticker_version_" + currentPackId, currentVersion + 1).apply();

                Intent intent = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
                intent.putExtra("sticker_pack_id", currentPackId);
                intent.putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY);
                intent.putExtra("sticker_pack_name", currentName);

                try { intent.setPackage("com.whatsapp"); addStickerLauncher.launch(intent);
                } catch (ActivityNotFoundException e) {
                    try { intent.setPackage("com.whatsapp.w4b"); addStickerLauncher.launch(intent);
                    } catch (ActivityNotFoundException e2) { Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_LONG).show(); }
                }
            }
        });
    }

    private void triggerAddStickerFlow() {
        if (stickerCount >= 30) {
            Toast.makeText(this, "Pack is full (30 max)!", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        pickMediaLauncher.launch(intent);
    }

    private void setupBottomButton(LinearLayout layout, int iconType, String text, int bgColor, int txtColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(100f);
        layout.setBackground(gd);

        FrameLayout iconFrame = (FrameLayout) layout.getChildAt(0);
        iconFrame.removeAllViews();
        iconFrame.addView(new StickerIconView(this, iconType, txtColor));

        TextView tv = (TextView) layout.getChildAt(1);
        tv.setText(text);
        tv.setTextColor(txtColor);

        FrameLayout chevronFrame = (FrameLayout) layout.getChildAt(2);
        chevronFrame.removeAllViews();
        chevronFrame.addView(new StickerIconView(this, StickerIconView.ICON_CHEVRON, txtColor));
    }

    private String getPackId(int index) {
        return "ownpack_" + deviceId + "_" + index;
    }

    private void refreshPackTabBarWithoutRebuilding() {
        try {
            int currentIdx = Integer.parseInt(currentPackId.substring(currentPackId.lastIndexOf("_") + 1)) - 1;
            if (currentIdx >= 0 && currentIdx < packSelectorContainer.getChildCount() - 1) {
                View child = packSelectorContainer.getChildAt(currentIdx);
                if (child instanceof LinearLayout) {
                    TextView tv = (TextView) ((LinearLayout) child).getChildAt(1);
                    String newName = prefs.getString("pack_name_" + currentPackId, "");
                    if (newName.trim().isEmpty()) newName = "Pack " + (currentIdx + 1);
                    if (newName.length() > 12) newName = newName.substring(0, 12) + "...";
                    tv.setText(newName);
                }
            }
        } catch (Exception ignored) {}
    }

    private void refreshPackTabBar() {
        packSelectorContainer.removeAllViews();
        for (int i = 1; i <= staticPackCount; i++) {
            String pId = getPackId(i);
            String realName = prefs.getString("pack_name_" + pId, "");
            if (realName.trim().isEmpty()) realName = "Pack " + i;
            if (realName.length() > 12) realName = realName.substring(0, 12) + "...";

            LinearLayout tab = createTabButton(realName, pId.equals(currentPackId), false);
            tab.setOnClickListener(v -> switchPack(pId));
            packSelectorContainer.addView(tab);
        }

        LinearLayout btnAddStatic = createTabButton("New", false, true);
        btnAddStatic.setOnClickListener(v -> {
            staticPackCount++;
            prefs.edit().putInt("static_pack_count", staticPackCount).apply();
            String newId = getPackId(staticPackCount);
            prefs.edit().putString("pack_name_" + newId, "Pack " + staticPackCount).apply();
            switchPack(newId);
            packScrollView.post(() -> packScrollView.fullScroll(View.FOCUS_RIGHT));
        });
        packSelectorContainer.addView(btnAddStatic);
    }

    private LinearLayout createTabButton(String text, boolean isActive, boolean isAddBtn) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(35, 0, 45, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
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
        iconFrame.addView(new StickerIconView(this, isAddBtn ? StickerIconView.ICON_ADD : StickerIconView.ICON_PACK, cTxt));
        layout.addView(iconFrame);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(cTxt);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tLp.setMargins(16, 0, 0, 0);
        tv.setLayoutParams(tLp);
        layout.addView(tv);

        return layout;
    }

    private void switchPack(String packId) {
        isSwitchingPack = true;
        currentPackId = packId;
        refreshPackTabBar();

        String savedName = prefs.getString("pack_name_" + currentPackId, "");
        if (savedName.trim().isEmpty()) {
            try {
                int num = Integer.parseInt(packId.substring(packId.lastIndexOf("_") + 1));
                savedName = "Pack " + num;
            } catch (Exception e) {
                savedName = "My Custom Pack";
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
                    File dir = new File(getFilesDir(), "stickers/" + currentPackId);
                    deleteDirectory(dir);
                    for (int m = deleteIdx + 1; m <= staticPackCount; m++) {
                        File oldDir = new File(getFilesDir(), "stickers/" + getPackId(m));
                        File newDir = new File(getFilesDir(), "stickers/" + getPackId(m - 1));
                        oldDir.renameTo(newDir);
                        prefs.edit()
                                .putString("pack_name_" + getPackId(m - 1), prefs.getString("pack_name_" + getPackId(m), "Pack " + (m - 1)))
                                .putInt("sticker_version_" + getPackId(m - 1), prefs.getInt("sticker_version_" + getPackId(m), 3))
                                .apply();
                    }
                    staticPackCount--;
                    prefs.edit().putInt("static_pack_count", staticPackCount).apply();
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
        File[] files = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb") && new File(d, name).length() > 100);
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
                    return Integer.compare(Integer.parseInt(f1.getName().replace(".webp", "")), Integer.parseInt(f2.getName().replace(".webp", "")));
                } catch (NumberFormatException e) { return 0; }
            });
            for (File file : files) {
                File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png"));
                Bitmap bmp = thumbFile.exists() ? BitmapFactory.decodeFile(thumbFile.getAbsolutePath()) : BitmapFactory.decodeFile(file.getAbsolutePath());
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
            tvStatusText.setText(stickerCount + " Stickers Ready");
            tvStatusText.setTextColor(accentColor);
            btnAddWA.setAlpha(1.0f);
            btnAddWA.setEnabled(true);
        }
        statusDot.setBackground(dotGd);
    }

    private void addStickerToGrid(File file, Bitmap bitmap, int itemSize, int itemMargin) {
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
        iv.setImageBitmap(bitmap);
        RelativeLayout.LayoutParams ivLp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        iv.setPadding(p, p, p, p);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(iv, ivLp);

        FrameLayout trashBtn = new FrameLayout(this);
        GradientDrawable trashGd = new GradientDrawable();
        trashGd.setColor(Color.parseColor("#E6111827"));
        trashGd.setShape(GradientDrawable.OVAL);
        trashGd.setStroke(2, glassBorderColor);
        trashBtn.setBackground(trashGd);

        int tSize = (int) (32 * getResources().getDisplayMetrics().density);
        RelativeLayout.LayoutParams tLp = new RelativeLayout.LayoutParams(tSize, tSize);
        tLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        tLp.addRule(RelativeLayout.ALIGN_PARENT_END);
        tLp.setMargins(0, 0, 8, 8);
        trashBtn.setLayoutParams(tLp);

        trashBtn.addView(new StickerIconView(this, StickerIconView.ICON_TRASH, Color.WHITE));

        trashBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
            builder.setTitle("Delete Sticker").setMessage("Remove this sticker from the pack?").setPositiveButton("DELETE", (dialog, which) -> {
                if (file.delete()) {
                    File thumbFile = new File(file.getParentFile(), file.getName().replace(".webp", "_thumb.png"));
                    if (thumbFile.exists()) thumbFile.delete();
                    ensureValidTrayIcon(file.getParentFile());
                    loadExistingStickers();
                }
            }).setNegativeButton("CANCEL", null);
            AlertDialog dialog = builder.create(); applyGlassDialogStyle(dialog); dialog.show();
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
        tv.setText("Add Sticker");
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
            File[] files = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> {
                    try { return Integer.compare(Integer.parseInt(f1.getName().replace(".webp", "")), Integer.parseInt(f2.getName().replace(".webp", "")));
                    } catch (Exception e) { return 0; }
                });
                File firstThumb = new File(dir, files[0].getName().replace(".webp", "_thumb.png"));
                Bitmap tb = firstThumb.exists() ? BitmapFactory.decodeFile(firstThumb.getAbsolutePath()) : BitmapFactory.decodeFile(files[0].getAbsolutePath());
                if (tb != null) {
                    Bitmap tray = Bitmap.createScaledBitmap(tb, 96, 96, true);
                    FileOutputStream out = new FileOutputStream(trayFile);
                    tray.compress(Bitmap.CompressFormat.PNG, 75, out);
                    out.close();
                    if (!tray.isRecycled()) tray.recycle();
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
        } catch (Exception e) { return null; }
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        int h = options.outHeight, w = options.outWidth, inSampleSize = 1;
        if (h > reqH || w > reqW) {
            int halfH = h / 2, halfW = w / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private void showVisualCropStudio(Uri mediaUri) {
        float density = getResources().getDisplayMetrics().density;
        int p = (int) (24 * density);

        final int[] currentCropMode = {0}; // 0=Free, 1=1:1, 2=Oval, 3=Circle
        final float[] cornerRadiusProgress = {0f}; // 0.0 to 100.0
        final int[] smoothLevelProgress = {0}; // 0 to 4 (Level 1 to 5)

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
        header.setPadding(0, 0, 0, (int)(16 * density));

        ImageView iconScissors = new ImageView(this);
        iconScissors.setLayoutParams(new LinearLayout.LayoutParams((int)(24 * density), (int)(24 * density)));
        iconScissors.setImageResource(R.drawable.ic_scissors);
        iconScissors.setColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN);
        header.addView(iconScissors);

        TextView title = new TextView(this);
        title.setText(" Grid Sticker Studio");
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(textColor);
        header.addView(title);
        layout.addView(header);

        // --- Layout setup ---
        int boxSize = (int) (300 * density);

        FrameLayout wrapper = new FrameLayout(this);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(boxSize, boxSize);
        boxLp.gravity = Gravity.CENTER;
        boxLp.setMargins(0, (int)(4 * density), 0, (int)(16 * density));
        wrapper.setLayoutParams(boxLp);
        GradientDrawable boxGd = new GradientDrawable();
        boxGd.setColor(isDarkTheme ? Color.parseColor("#141414") : Color.parseColor("#F8F8F8"));
        wrapper.setBackground(boxGd);

        // This view uses software bitmaps internally to perfectly clip and blur the image live
        FrameLayout previewBox = new FrameLayout(this) {
            Bitmap offscreen, maskBmp;
            Canvas osCanvas, maskCanvas;
            Paint dstInPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                dstInPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
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

                // Clean the software buffers
                offscreen.eraseColor(Color.TRANSPARENT);
                maskBmp.eraseColor(Color.TRANSPARENT);

                // Draw the actual image layer to our custom buffer
                super.dispatchDraw(osCanvas);

                // Build the precise shape path
                Path clipPath = new Path();
                if (currentCropMode[0] == 2 || currentCropMode[0] == 3) {
                    clipPath.addOval(new RectF(0, 0, w, h), Path.Direction.CW);
                } else {
                    float maxRadius = Math.min(w, h) / 2f;
                    float rx = maxRadius * (cornerRadiusProgress[0] / 100f);
                    clipPath.addRoundRect(new RectF(0, 0, w, h), rx, rx, Path.Direction.CW);
                }

                // Apply Mask and Optional Blur to the mask canvas
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
                    maskPaint.setMaskFilter(new android.graphics.BlurMaskFilter(baseBlur * scale, android.graphics.BlurMaskFilter.Blur.NORMAL));

                    maskCanvas.save();
                    // Clip the mask canvas so the blur fades inward from transparent edge to opaque center
                    maskCanvas.clipPath(clipPath);
                    maskCanvas.drawPath(clipPath, maskPaint);
                    maskCanvas.restore();
                }

                // Composite the mask onto the image
                osCanvas.drawBitmap(maskBmp, 0, 0, dstInPaint);

                // Draw the composited layer safely back to hardware screen
                canvas.drawBitmap(offscreen, 0, 0, null);
            }
        };
        previewBox.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        previewBox.addView(iv);
        wrapper.addView(previewBox);

        // Dynamic Grid Overlay
        View gridOverlay = new View(this) {
            final Paint paintLines = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setColor(Color.WHITE); setStrokeWidth(2f); setAlpha(160); }};
            final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG) {{ setColor(accentColor); setStyle(Paint.Style.STROKE); setStrokeWidth(6f); }};

            @Override protected void onDraw(Canvas c) {
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
        gridOverlay.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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

        // --- CORNER RADIUS SLIDER ---
        LinearLayout radiusRow = new LinearLayout(this);
        radiusRow.setOrientation(LinearLayout.HORIZONTAL);
        radiusRow.setGravity(Gravity.CENTER_VERTICAL);
        radiusRow.setPadding((int)(4*density), 0, (int)(4*density), (int)(12*density));

        TextView tvRadius = new TextView(this);
        tvRadius.setText("Corners");
        tvRadius.setTextColor(textColor);
        tvRadius.setTypeface(null, Typeface.BOLD);
        tvRadius.setTextSize(13f);
        tvRadius.setMinimumWidth((int)(60 * density));
        radiusRow.addView(tvRadius);

        SeekBar seekBarRadius = new SeekBar(this);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbLp.setMargins((int)(12 * density), 0, 0, 0);
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

        // --- SMOOTHNESS SLIDER (1 to 5 levels mapped to 0 to 4) ---
        LinearLayout smoothRow = new LinearLayout(this);
        smoothRow.setOrientation(LinearLayout.HORIZONTAL);
        smoothRow.setGravity(Gravity.CENTER_VERTICAL);
        smoothRow.setPadding((int)(4*density), 0, (int)(4*density), (int)(16*density));

        TextView tvSmooth = new TextView(this);
        tvSmooth.setText("Smooth");
        tvSmooth.setTextColor(textColor);
        tvSmooth.setTypeface(null, Typeface.BOLD);
        tvSmooth.setTextSize(13f);
        tvSmooth.setMinimumWidth((int)(60 * density));
        smoothRow.addView(tvSmooth);

        SeekBar seekBarSmooth = new SeekBar(this);
        LinearLayout.LayoutParams sbSmoothLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sbSmoothLp.setMargins((int)(12 * density), 0, 0, 0);
        seekBarSmooth.setLayoutParams(sbSmoothLp);
        seekBarSmooth.setMax(4);
        seekBarSmooth.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBarSmooth.setProgressTintList(ColorStateList.valueOf(accentColor));
            seekBarSmooth.setThumbTintList(ColorStateList.valueOf(accentColor));
        }

        // --- LEVEL MARKINGS (DOTS) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            GradientDrawable tick = new GradientDrawable();
            tick.setShape(GradientDrawable.OVAL);
            tick.setSize((int)(6 * density), (int)(6 * density));
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

        // --- RATIO / SHAPE PRESETS ---
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER);
        chipRow.setPadding(0, 0, 0, (int)(16 * density));

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

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, (int)(44 * density), 1f);
            clp.setMargins((int)(4 * density), 0, (int)(4 * density), 0);
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

                    // Re-bind touch events entirely so matrix isn't broken by the new shape constraint!
                    setupTouchAndPresets(iv, previewBmp[0], wrapper, boxSize, finalI);
                    previewBox.invalidate();
                    gridOverlay.invalidate();
                }
            });
            chipRow.addView(b);
        }
        layout.addView(chipRow);

        // --- ROTATE AND MIRROR TOOLS ROW ---
        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setGravity(Gravity.CENTER);
        toolsRow.setPadding(0, 0, 0, (int)(24 * density));
        String[] tNames = {"Rotate", "Flip H", "Flip V"};
        for (int i = 0; i < 3; i++) {
            LinearLayout bLayout = new LinearLayout(this);
            bLayout.setOrientation(LinearLayout.HORIZONTAL);
            bLayout.setGravity(Gravity.CENTER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                bLayout.setElevation(0);
            }
            GradientDrawable tGd = new GradientDrawable();
            tGd.setColor(panelColor);
            tGd.setCornerRadius(100f);
            tGd.setStroke(2, glassBorderColor);
            bLayout.setBackground(tGd);
            bLayout.setClickable(true);
            bLayout.setFocusable(true);

            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, (int)(48 * density), 1f);
            tLp.setMargins((int)(5 * density), 0, (int)(5 * density), 0);
            bLayout.setLayoutParams(tLp);

            if (i == 1 || i == 2) {
                ImageView ivIcon = new ImageView(this);
                ivIcon.setImageResource(R.drawable.ic_flip);
                ivIcon.setColorFilter(textColor, android.graphics.PorterDuff.Mode.SRC_IN);
                int icSize = (int)(18 * density);
                LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(icSize, icSize);
                icLp.setMargins(0, 0, (int)(6 * density), 0);
                ivIcon.setLayoutParams(icLp);
                if (i == 2) ivIcon.setRotation(90f);
                bLayout.addView(ivIcon);
            }

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

                        Bitmap newBmp = Bitmap.createBitmap(previewBmp[0], 0, 0, previewBmp[0].getWidth(), previewBmp[0].getHeight(), m, true);
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
        btnRow.setPadding(0, (int)(10 * density), 0, 0);

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
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(0, (int)(56 * density), 1f);
        lpC.setMargins(0, 0, (int)(10 * density), 0);
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

        LinearLayout.LayoutParams lpB = new LinearLayout.LayoutParams(0, (int)(56 * density), 1f);
        lpB.setMargins((int)(10 * density), 0, 0, 0);
        btnBuild.setLayoutParams(lpB);

        btnRow.addView(btnCancel);
        btnRow.addView(btnBuild);
        layout.addView(btnRow);

        builder.setView(layout);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

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
                    File[] existingFiles = dir.listFiles((d, name) -> name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
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
                    Bitmap.CompressFormat webpFormat = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;

                    Bitmap orig = previewBmp[0];
                    if (orig == null) throw new Exception();

                    Bitmap cropped = applyMatrixToSticker(orig, userMatrix, boxW, boxH, currentCropMode[0], cornerRadiusProgress[0], smoothLevelProgress[0]);

                    FileOutputStream outSticker = new FileOutputStream(tmpFile);
                    cropped.compress(webpFormat, 75, outSticker);
                    outSticker.close();
                    if (tmpFile.exists() && tmpFile.length() > 100) { tmpFile.renameTo(stickerFile); } else { throw new Exception("Write failed"); }
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

        // Attached to TOP container to prevent Views from intercepting the pinch gesture
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

                            // Allows the image to PAN while zooming to keep it centered under your fingers perfectly
                            PointF newMid = new PointF();
                            midPoint(newMid, event);
                            matrix.postTranslate(newMid.x - mid.x, newMid.y - mid.y);
                        }
                    }
                    break;
            }
            iv.setImageMatrix(matrix);
            iv.invalidate();
            ((View)iv.getParent()).invalidate(); // Forces the software canvas to redraw
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

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        canvas.drawBitmap(orig, userMatrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        canvas.restore();

        Path clipPath = new Path();
        float fw = boxW * scale;
        float fh = boxH * scale;

        if (cropMode == 2 || cropMode == 3) {
            clipPath.addOval(new RectF(dx, dy, dx + fw, dy + fh), Path.Direction.CW);
        } else {
            float maxRadius = Math.min(fw, fh) / 2f;
            float rx = maxRadius * (radiusProgress / 100f);
            clipPath.addRoundRect(new RectF(dx, dy, dx + fw, dy + fh), rx, rx, Path.Direction.CW);
        }

        Bitmap maskBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(maskBmp);
        Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        maskPaint.setColor(Color.BLACK);

        if (smoothLevel > 0) {
            float blurRadius = 0f;
            if (smoothLevel == 1) blurRadius = 15f;
            else if (smoothLevel == 2) blurRadius = 35f;
            else if (smoothLevel == 3) blurRadius = 60f;
            else if (smoothLevel == 4) blurRadius = 100f;

            maskPaint.setMaskFilter(new android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL));

            maskCanvas.save();
            maskCanvas.clipPath(clipPath);
            maskCanvas.drawPath(clipPath, maskPaint);
            maskCanvas.restore();
        } else {
            maskCanvas.drawPath(clipPath, maskPaint);
        }

        Paint dstInPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dstInPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        canvas.drawBitmap(maskBmp, 0, 0, dstInPaint);

        maskBmp.recycle();
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
        if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText))
            ((TextView) view).setTextColor(color);
        else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) setDialogTextColor(vg.getChildAt(i), color);
        }
    }

    public static class StickerIconView extends View {
        public static final int ICON_PACK = 0;
        public static final int ICON_ADD = 1;
        public static final int ICON_TRASH = 2;
        public static final int ICON_SEND = 3;
        public static final int ICON_IMAGE = 4;
        public static final int ICON_CHEVRON = 5;
        public static final int ICON_DOTS = 6;
        public static final int ICON_SCISSORS = 7;

        private int iconType;
        private Paint paint;

        public StickerIconView(Context context, int type, int color) {
            super(context);
            this.iconType = type;
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            Path p = new Path();

            switch (iconType) {
                case ICON_PACK:
                    paint.setStyle(Paint.Style.STROKE);
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
                    canvas.drawRoundRect(cx - 12, cy - 12, cx + 12, cy + 12, 6f, 6f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx + 5, cy - 5, 3f, paint);
                    p.moveTo(cx - 12, cy + 10);
                    p.lineTo(cx - 2, cy - 2);
                    p.lineTo(cx + 6, cy + 8);
                    p.lineTo(cx + 12, cy + 2);
                    p.lineTo(cx + 12, cy + 12);
                    p.lineTo(cx - 12, cy + 12);
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
            }
        }
    }
}