package com.abhinav.ownapp;

import android.app.Activity; import android.app.AlertDialog; import android.content.Context; import android.content.Intent; import android.content.SharedPreferences;
import android.graphics.Bitmap; import android.graphics.BitmapFactory; import android.graphics.BitmapShader; import android.graphics.BlurMaskFilter; import android.graphics.Canvas;
import android.graphics.Color; import android.graphics.ComposePathEffect; import android.graphics.ComposeShader; import android.graphics.CornerPathEffect; import android.graphics.DashPathEffect;
import android.graphics.DiscretePathEffect; import android.graphics.LinearGradient; import android.graphics.Matrix; import android.graphics.Paint; import android.graphics.Path;
import android.graphics.PathDashPathEffect; import android.graphics.PathEffect; import android.graphics.PointF; import android.graphics.PorterDuff; import android.graphics.PorterDuffXfermode;
import android.graphics.RectF; import android.graphics.Shader; import android.graphics.Typeface; import android.graphics.drawable.GradientDrawable; import android.net.Uri; import android.os.Bundle;
import android.text.Editable; import android.text.TextWatcher; import android.util.DisplayMetrics; import android.util.SparseArray; import android.view.Gravity; import android.view.MotionEvent;
import android.view.ScaleGestureDetector; import android.view.View; import android.view.ViewGroup; import android.widget.Button; import android.widget.EditText; import android.widget.FrameLayout;
import android.widget.LinearLayout; import android.widget.ProgressBar; import android.widget.ScrollView; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull; import com.google.android.material.slider.Slider; import java.io.File; import java.io.FileOutputStream; import java.io.InputStream; import java.util.ArrayList; import java.util.Locale; import java.util.Random;

@SuppressWarnings("all")
public class AdvancedCanvasActivity extends Activity {

    private static final int REQ_PICK_REF_IMAGE = 9401;
    private AdvancedDrawingView drawingView; private int themeState; private boolean isDarkTheme; private int panelColor, textColor, bgColor, btnBgColor;
    private ColorPickerView colorPicker; private EditText etHexCode; private boolean isUpdatingColor = false; private FrameLayout loadingOverlay;
    private ShadingPreviewView activePanelShadePreview; private Button btnRefToggle;
    public int paintBrushSubStyle = 0; public int currentShadingMode = 0; public int activeBrushIndex = 0;
    public static final String[] SHADE_LABELS = {
            "Shading: OFF (Normal)", "1. Blended / Tonal Shading", "2. Circulism (Circular Scribble)",
            "3. Hatching (Directional Lines)", "4. Cross-Hatching (Weave Mesh)", "5. Contour Hatching (3D Curve)",
            "6. Stippling (Organic Dots)", "7. Scribble Lines (Chaotic Sketch)"
    };
    public interface OnColorChangeListener { void onColorChanged(int color); }
    public int getCanvasBgColor() { return getIntent().getIntExtra("bg_color", Color.WHITE); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_advanced_canvas);
        SharedPreferences prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) { boolean oldDark = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true); themeState = oldDark ? 1 : 0; }
        isDarkTheme = (themeState != 0);
        if (themeState == 0) { bgColor = Color.parseColor("#F2F2F7"); panelColor = Color.parseColor("#E6FFFFFF"); textColor = Color.parseColor("#333333"); btnBgColor = Color.parseColor("#E5E5EA"); }
        else if (themeState == 1) { bgColor = Color.parseColor("#1C1C1E"); panelColor = Color.parseColor("#D92C2C2E"); textColor = Color.WHITE; btnBgColor = Color.parseColor("#3A3A3C"); }
        else { bgColor = Color.parseColor("#000000"); panelColor = Color.parseColor("#E6121214"); textColor = Color.WHITE; btnBgColor = Color.parseColor("#242426"); }

        View canvasRoot = findViewById(R.id.canvasRoot); if (canvasRoot != null) canvasRoot.setBackgroundColor(bgColor);
        View rightPanel = findViewById(R.id.rightBrushesPanel); View leftPanel = findViewById(R.id.leftSettingsPanel);
        setModernMenuBackground(leftPanel, panelColor, true); setModernMenuBackground(rightPanel, panelColor, false);
        snapDrawerFlushToScreenEdge(leftPanel, true); snapDrawerFlushToScreenEdge(rightPanel, false);
        if (leftPanel != null) applyTextColor(leftPanel, textColor);

        Button btnToggleSettings = findViewById(R.id.btnToggleSettings); Button btnToggleTools = findViewById(R.id.btnToggleTools);
        int bottomBtnBg = themeState == 0 ? Color.parseColor("#D1D1D6") : (themeState == 1 ? Color.parseColor("#2C2C2E") : Color.parseColor("#1A1A1C"));
        int bottomBtnText = themeState == 0 ? Color.parseColor("#333333") : Color.WHITE;
        makeButtonRounded(btnToggleSettings, bottomBtnBg, bottomBtnText, 100f); makeButtonRounded(btnToggleTools, bottomBtnBg, bottomBtnText, 100f);
        if (btnToggleSettings != null && leftPanel != null) {
            btnToggleSettings.setOnClickListener(v -> {
                leftPanel.setVisibility(leftPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (rightPanel != null) rightPanel.setVisibility(View.GONE);
                snapDrawerFlushToScreenEdge(leftPanel, true);
            });
        }
        if (btnToggleTools != null && rightPanel != null) {
            btnToggleTools.setOnClickListener(v -> {
                rightPanel.setVisibility(rightPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                if (leftPanel != null) leftPanel.setVisibility(View.GONE);
                snapDrawerFlushToScreenEdge(rightPanel, false);
            });
        }
        setupLoadingOverlay();
        String inputImagePath = getIntent().getStringExtra("image_path");
        if (inputImagePath != null && new File(inputImagePath).exists()) {
            loadingOverlay.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(inputImagePath, bounds);
                    int sample = 1; int maxDim = 2560; while ((bounds.outWidth / sample) > maxDim || (bounds.outHeight / sample) > maxDim) { sample *= 2; }
                    BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = sample; opts.inMutable = true; opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap loaded = BitmapFactory.decodeFile(inputImagePath, opts);
                    runOnUiThread(() -> initCanvas(loaded));
                } catch (OutOfMemoryError e) {
                    runOnUiThread(() -> { loadingOverlay.setVisibility(View.GONE); Toast.makeText(AdvancedCanvasActivity.this, "Image too large for device memory.", Toast.LENGTH_LONG).show(); finish(); });
                }
            }).start();
        } else { initCanvas(null); }
    }

    // Removes any unwanted vertical gap between the left/right drawers and the screen edges without overlapping or breaking layout
    private void snapDrawerFlushToScreenEdge(final View panel, final boolean isLeft) {
        if (panel == null) return;
        if (panel.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) panel.getLayoutParams();
            int parentPadL = (panel.getParent() instanceof View) ? ((View) panel.getParent()).getPaddingLeft() : 0;
            int parentPadR = (panel.getParent() instanceof View) ? ((View) panel.getParent()).getPaddingRight() : 0;
            if (isLeft) {
                lp.leftMargin = -parentPadL;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) lp.setMarginStart(-parentPadL);
            } else {
                lp.rightMargin = -parentPadR;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) lp.setMarginEnd(-parentPadR);
            }
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) lp;
                int vGrav = flp.gravity & Gravity.VERTICAL_GRAVITY_MASK;
                if (vGrav == 0) vGrav = Gravity.CENTER_VERTICAL;
                flp.gravity = vGrav | (isLeft ? Gravity.LEFT : Gravity.RIGHT);
            }
            panel.setLayoutParams(lp);
        }
        panel.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (isLeft) {
                if (left != 0) v.setTranslationX(-left); else v.setTranslationX(0f);
            } else if (v.getParent() instanceof View) {
                int pw = ((View) v.getParent()).getWidth();
                if (pw > 0 && right != pw) v.setTranslationX(pw - right); else v.setTranslationX(0f);
            }
        });
        panel.post(() -> {
            if (isLeft) {
                if (panel.getLeft() != 0) panel.setTranslationX(-panel.getLeft()); else panel.setTranslationX(0f);
            } else if (panel.getParent() instanceof View) {
                int pw = ((View) panel.getParent()).getWidth();
                if (pw > 0 && panel.getRight() != pw) panel.setTranslationX(pw - panel.getRight()); else panel.setTranslationX(0f);
            }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_REF_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            new Thread(() -> {
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
                    InputStream is1 = getContentResolver().openInputStream(uri); BitmapFactory.decodeStream(is1, null, bounds); if (is1 != null) is1.close();
                    int sample = 1; int maxDim = 2048; while ((bounds.outWidth / sample) > maxDim || (bounds.outHeight / sample) > maxDim) { sample *= 2; }
                    BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = sample; opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    InputStream is2 = getContentResolver().openInputStream(uri); Bitmap refBmp = BitmapFactory.decodeStream(is2, null, opts); if (is2 != null) is2.close();
                    runOnUiThread(() -> {
                        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                        if (refBmp != null && drawingView != null) {
                            drawingView.setReferenceBitmap(refBmp);
                            if (btnRefToggle != null) { btnRefToggle.setText("Ref: ON"); makeButtonRounded(btnRefToggle, Color.parseColor("#34C759"), Color.WHITE, 36f); }
                            Toast.makeText(AdvancedCanvasActivity.this, "Reference Image Loaded (Non-Destructive Overlay)", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception | OutOfMemoryError e) {
                    runOnUiThread(() -> { if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE); Toast.makeText(AdvancedCanvasActivity.this, "Failed to load reference image", Toast.LENGTH_SHORT).show(); });
                }
            }).start();
        }
    }

    private void makeButtonRounded(Button btn, int bgCol, int txtCol, float radius) { if (btn == null) return; GradientDrawable gd = new GradientDrawable(); gd.setColor(bgCol); gd.setCornerRadius(radius); btn.setBackgroundTintList(null); btn.setBackground(gd); btn.setTextColor(txtCol); btn.setAllCaps(false); if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) { btn.setElevation(0f); btn.setStateListAnimator(null); } }
    private void setupLoadingOverlay() { loadingOverlay = new FrameLayout(this); loadingOverlay.setBackgroundColor(Color.parseColor("#B3000000")); loadingOverlay.setClickable(true); loadingOverlay.setFocusable(true); loadingOverlay.setVisibility(View.GONE); ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge); pb.setIndeterminate(true); FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(150, 150, Gravity.CENTER); loadingOverlay.addView(pb, params); ViewGroup root = findViewById(android.R.id.content); if (root != null) root.addView(loadingOverlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); }

    private void initCanvas(Bitmap baseBitmap) {
        FrameLayout container = findViewById(R.id.drawingContainer);
        if (container != null) { drawingView = new AdvancedDrawingView(this, baseBitmap); container.addView(drawingView); }
        setupBrushButtons(); setupSettingsPanel();
        Button btnCancel = findViewById(R.id.btnCanvasCancel); Button btnSave = findViewById(R.id.btnCanvasSave); Button btnUndo = findViewById(R.id.btnCanvasUndo); Button btnRedo = findViewById(R.id.btnCanvasRedo); Button btnZoom = findViewById(R.id.btnCanvasZoom); Button btnGrid = findViewById(R.id.btnCanvasGrid);
        makeButtonRounded(btnCancel, btnBgColor, textColor, 40f); makeButtonRounded(btnSave, btnBgColor, textColor, 40f); makeButtonRounded(btnUndo, btnBgColor, textColor, 40f); makeButtonRounded(btnRedo, btnBgColor, textColor, 40f); makeButtonRounded(btnZoom, btnBgColor, textColor, 40f); makeButtonRounded(btnGrid, btnBgColor, textColor, 40f);
        if (btnCancel != null) btnCancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        if (btnUndo != null) btnUndo.setOnClickListener(v -> { if (drawingView != null) drawingView.undo(); });
        if (btnRedo != null) btnRedo.setOnClickListener(v -> { if (drawingView != null) drawingView.redo(); });
        if (btnZoom != null) btnZoom.setOnClickListener(v -> { if (drawingView == null) return; drawingView.isZoomMode = !drawingView.isZoomMode; btnZoom.setText(drawingView.isZoomMode ? "Zoom: ON" : "Zoom: OFF"); makeButtonRounded(btnZoom, drawingView.isZoomMode ? Color.parseColor("#34C759") : btnBgColor, drawingView.isZoomMode ? Color.WHITE : textColor, 40f); if (drawingView.isZoomMode) Toast.makeText(this, "Zoom Mode Active! Pinch to zoom infinitely.", Toast.LENGTH_SHORT).show(); });
        if (btnGrid != null) btnGrid.setOnClickListener(v -> { if (drawingView == null) return; drawingView.isGridMode = !drawingView.isGridMode; btnGrid.setText(drawingView.isGridMode ? "Grid: ON" : "Grid: OFF"); makeButtonRounded(btnGrid, drawingView.isGridMode ? Color.parseColor("#34C759") : btnBgColor, drawingView.isGridMode ? Color.WHITE : textColor, 40f); drawingView.invalidate(); });
        if (btnSave != null) btnSave.setOnClickListener(v -> showSaveDialog());
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
    }

    private void showSaveDialog() { AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.ModernDialogStyle); b.setTitle("Save Canvas"); b.setMessage("How would you like to save this artwork into the Studio? (Reference overlay will not be included)"); b.setPositiveButton("As Base Image", (d, w) -> saveAndReturn(true)); b.setNegativeButton("As New Layer", (d, w) -> saveAndReturn(false)); b.setNeutralButton("Cancel", null); AlertDialog dialog = b.create(); if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.setOnShowListener(di -> { if (dialog.getWindow() != null) { GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(60f); dialog.getWindow().getDecorView().setBackground(gd); applyTextColor(dialog.getWindow().getDecorView(), textColor); } }); dialog.show(); }
    private void saveAndReturn(boolean saveAsBase) {
        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE); Toast.makeText(this, "Saving High Quality Artwork...", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                Bitmap result = drawingView != null ? drawingView.getOptimizedSaveBitmap() : null; File outFile = new File(getCacheDir(), "canvas_out.png"); FileOutputStream fos = new FileOutputStream(outFile);
                if (result != null) result.compress(Bitmap.CompressFormat.PNG, 100, fos); fos.close();
                runOnUiThread(() -> { if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE); Intent data = new Intent(); data.putExtra("out_path", outFile.getAbsolutePath()); data.putExtra("save_as_base", saveAsBase); setResult(RESULT_OK, data); finish(); });
            } catch (Exception | OutOfMemoryError e) { runOnUiThread(() -> { if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE); Toast.makeText(AdvancedCanvasActivity.this, "Failed to save. Try freeing memory.", Toast.LENGTH_LONG).show(); }); }
        }).start();
    }

    private void setupBrushButtons() {
        LinearLayout list = findViewById(R.id.brushListContainer); if (list == null) return; list.removeAllViews();
        String[] brushes = { "Pencil", "Pen", "Paint Brush", "Marker", "Ink Brush", "Airbrush", "Watercolor", "Oil Brush", "Smudge", "Eraser", "Fill (Bucket)", "Charcoal Stick", "Chalk Pastel", "Calligraphy Nib", "Fan Bristle", "Palette Knife" };
        for (int i = 0; i < brushes.length; i++) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(8, 16, 8, 16);
            BrushPreviewView preview = new BrushPreviewView(this, i); preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 80));
            TextView tv = new TextView(this); tv.setText(brushes[i]); tv.setTextColor(textColor); tv.setTextSize(12f); tv.setGravity(Gravity.CENTER); tv.setPadding(0, 8, 0, 0);
            row.addView(preview); row.addView(tv);
            GradientDrawable normalBg = new GradientDrawable(); normalBg.setColor(i == activeBrushIndex ? btnBgColor : Color.TRANSPARENT); normalBg.setCornerRadius(20f); row.setBackground(normalBg);
            final int brushType = i;
            row.setOnClickListener(v -> { if (brushType == 2) { showPaintBrushSubMenu(list, row, brushType); } else { activateBrush(list, row, brushType, 0); } });
            list.addView(row);
        }
    }

    private void refreshAllBrushPreviews() {
        LinearLayout list = findViewById(R.id.brushListContainer);
        if (list != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                View child = list.getChildAt(i);
                if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0) {
                    View prev = ((ViewGroup) child).getChildAt(0);
                    if (prev instanceof BrushPreviewView) ((BrushPreviewView) prev).refreshProfile();
                }
            }
        }
        if (activePanelShadePreview != null) activePanelShadePreview.invalidate();
    }

    private void showPaintBrushSubMenu(LinearLayout list, LinearLayout row, int brushType) { String[] styles = {"Round Brush", "Flat Canvas Brush", "Wet Bristle"}; AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.ModernDialogStyle); b.setTitle("Select Paint Style"); b.setItems(styles, (dialog, which) -> activateBrush(list, row, brushType, which)); AlertDialog dialog = b.create(); if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.setOnShowListener(di -> { if (dialog.getWindow() != null) { GradientDrawable gd = new GradientDrawable(); gd.setColor(panelColor); gd.setCornerRadius(40f); dialog.getWindow().getDecorView().setBackground(gd); applyTextColor(dialog.getWindow().getDecorView(), textColor); } }); dialog.show(); }

    private void activateBrush(LinearLayout list, LinearLayout row, int brushType, int subStyle) {
        if (list != null) { for (int j = 0; j < list.getChildCount(); j++) { list.getChildAt(j).setBackgroundColor(Color.TRANSPARENT); } }
        GradientDrawable activeBg = new GradientDrawable(); activeBg.setColor(btnBgColor); activeBg.setCornerRadius(20f);
        if (row != null) row.setBackground(activeBg);
        activeBrushIndex = brushType; paintBrushSubStyle = subStyle;
        if (drawingView != null) drawingView.setBrushType(brushType);
        refreshAllBrushPreviews();
        View rightBrushes = findViewById(R.id.rightBrushesPanel); View leftSettings = findViewById(R.id.leftSettingsPanel);
        if (rightBrushes != null) rightBrushes.setVisibility(View.GONE);
        if (leftSettings != null) { leftSettings.setVisibility(View.VISIBLE); snapDrawerFlushToScreenEdge(leftSettings, true); }
    }

    private void showShadingSelectionDialog(Button btnShadingMode) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, R.style.ModernDialogStyle);
        LinearLayout rootLayout = new LinearLayout(this); rootLayout.setOrientation(LinearLayout.VERTICAL);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density); int dp10 = (int) (10 * getResources().getDisplayMetrics().density);
        rootLayout.setPadding(dp16, dp16, dp16, dp16);
        GradientDrawable dialogBg = new GradientDrawable(); dialogBg.setColor(panelColor); dialogBg.setCornerRadius(48f); rootLayout.setBackground(dialogBg);

        TextView tvHeader = new TextView(this); tvHeader.setText("Select Shading Mode & Live Preview");
        tvHeader.setTextColor(textColor); tvHeader.setTextSize(16f); tvHeader.setTypeface(null, Typeface.BOLD); tvHeader.setGravity(Gravity.CENTER);
        tvHeader.setPadding(0, 0, 0, dp10); rootLayout.addView(tvHeader);

        ScrollView scroll = new ScrollView(this);
        LinearLayout listContainer = new LinearLayout(this); listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (getResources().getDisplayMetrics().heightPixels * 0.62f));
        rootLayout.addView(scroll, scrollLp);

        b.setView(rootLayout);
        AlertDialog dialog = b.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        int previewColor = drawingView != null ? drawingView.getBrushColor() : Color.BLACK;
        for (int i = 0; i < SHADE_LABELS.length; i++) {
            final int modeIdx = i;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp10, dp10, dp10, dp10);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, dp10); card.setLayoutParams(cardLp);

            GradientDrawable cardBg = new GradientDrawable();
            boolean isSelected = (currentShadingMode == modeIdx);
            cardBg.setColor(isSelected ? Color.parseColor("#335856D6") : btnBgColor);
            cardBg.setCornerRadius(28f);
            if (isSelected) cardBg.setStroke(4, Color.parseColor("#5856D6"));
            card.setBackground(cardBg);

            TextView tvLabel = new TextView(this); tvLabel.setText(SHADE_LABELS[modeIdx]);
            tvLabel.setTextColor(textColor); tvLabel.setTextSize(13f); tvLabel.setTypeface(null, Typeface.BOLD);
            tvLabel.setPadding(8, 0, 8, 6);
            card.addView(tvLabel);

            ShadingPreviewView spv = new ShadingPreviewView(this, modeIdx, previewColor);
            LinearLayout.LayoutParams spvLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (62 * getResources().getDisplayMetrics().density));
            card.addView(spv, spvLp);

            card.setOnClickListener(v -> {
                currentShadingMode = modeIdx;
                if (drawingView != null) drawingView.setShadingMode(modeIdx);
                btnShadingMode.setText(SHADE_LABELS[modeIdx]);
                if (modeIdx == 0) makeButtonRounded(btnShadingMode, btnBgColor, textColor, 36f);
                else makeButtonRounded(btnShadingMode, Color.parseColor("#5856D6"), Color.WHITE, 36f);
                refreshAllBrushPreviews();
                dialog.dismiss();
            });
            listContainer.addView(card);
        }
        dialog.show();
    }

    private ModernPrecisionSlider replaceSliderWithModernPill(View oldSlider, float min, float max, float initialVal, boolean showDecimals, ModernPrecisionSlider.OnValueChangeListener listener) {
        if (oldSlider == null || !(oldSlider.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) oldSlider.getParent();
        int idx = parent.indexOfChild(oldSlider);
        float density = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        lp.setMargins(0, (int) (4 * density), 0, (int) (10 * density));
        parent.removeView(oldSlider);
        ModernPrecisionSlider modern = new ModernPrecisionSlider(this, isDarkTheme, min, max, initialVal, showDecimals);
        modern.setLayoutParams(lp);
        modern.setOnValueChangeListener(listener);
        parent.addView(modern, idx);
        return modern;
    }

    private void setupSettingsPanel() {
        View slSizeOld = findViewById(R.id.slSize); View slOpacityOld = findViewById(R.id.slOpacity); View slHardnessOld = findViewById(R.id.slHardness);
        View slSmoothing = findViewById(R.id.slSmoothing); if (slSmoothing != null) slSmoothing.setVisibility(View.GONE);
        View tvSmoothingLabel = findViewById(R.id.tvSmoothingLabel); if (tvSmoothingLabel != null) tvSmoothingLabel.setVisibility(View.GONE);
        FrameLayout colorWheelContainer = findViewById(R.id.colorWheelContainer); etHexCode = findViewById(R.id.etCanvasHexCode);
        if (etHexCode != null) etHexCode.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        if (colorWheelContainer != null) {
            colorPicker = new ColorPickerView(this);
            FrameLayout.LayoutParams pickerLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            colorPicker.setLayoutParams(pickerLp); colorWheelContainer.removeAllViews(); colorWheelContainer.addView(colorPicker);
            colorPicker.setOnColorChangeListener(color -> {
                if (drawingView != null) drawingView.setBrushColor(color);
                if (activePanelShadePreview != null) activePanelShadePreview.setPreviewColor(color);
                if (!isUpdatingColor && etHexCode != null) { isUpdatingColor = true; etHexCode.setText(String.format("#%06X", (0xFFFFFF & color))); isUpdatingColor = false; }
            });
        }
        if (etHexCode != null) {
            etHexCode.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isUpdatingColor) return;
                    if (s.length() == 7 && s.toString().startsWith("#")) {
                        try { int newC = Color.parseColor(s.toString()); isUpdatingColor = true; if (colorPicker != null) colorPicker.setColor(newC); if (drawingView != null) drawingView.setBrushColor(newC); isUpdatingColor = false; } catch (Exception ignored) {}
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Replace standard sliders with ModernPrecisionSlider (0 to max with precision)
        replaceSliderWithModernPill(slSizeOld, 0f, 150f, 12f, true, val -> { if (drawingView != null) drawingView.setBrushSize(Math.max(0.5f, val)); });
        replaceSliderWithModernPill(slOpacityOld, 0f, 100f, 100f, false, val -> { if (drawingView != null) drawingView.setBrushOpacity(Math.round((val / 100f) * 255f)); });
        ModernPrecisionSlider modernHardness = replaceSliderWithModernPill(slHardnessOld, 0f, 100f, 100f, false, val -> {
            if (drawingView != null) drawingView.setBrushHardness(val);
            if (activePanelShadePreview != null) activePanelShadePreview.invalidate();
        });

        if (modernHardness != null && modernHardness.getParent() instanceof ViewGroup) {
            ViewGroup settingsContainer = (ViewGroup) modernHardness.getParent();
            float density = getResources().getDisplayMetrics().density;
            int dp8 = (int) (8 * density); int dp10 = (int) (10 * density);

            // 1. Professional Sketch Shading Section
            TextView tvShadeTitle = new TextView(this); tvShadeTitle.setText("Professional Sketch Shading"); tvShadeTitle.setTextColor(textColor); tvShadeTitle.setTextSize(13f); tvShadeTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); titleLp.setMargins(0, dp8, 0, dp8 / 2); tvShadeTitle.setLayoutParams(titleLp);

            Button btnShadingMode = new Button(this); btnShadingMode.setText(SHADE_LABELS[0]);
            makeButtonRounded(btnShadingMode, btnBgColor, textColor, 36f);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); btnLp.setMargins(0, 0, 0, dp8 / 2); btnShadingMode.setLayoutParams(btnLp);
            btnShadingMode.setOnClickListener(v -> showShadingSelectionDialog(btnShadingMode));

            activePanelShadePreview = new ShadingPreviewView(this, -1, Color.RED);
            LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (50 * density));
            prevLp.setMargins(0, 0, 0, dp10); activePanelShadePreview.setLayoutParams(prevLp);
            activePanelShadePreview.setOnClickListener(v -> showShadingSelectionDialog(btnShadingMode));

            // 2. Reference Image Overlay Section (Non-Destructive 5% - 80% Opacity + ON/OFF)
            TextView tvRefTitle = new TextView(this); tvRefTitle.setText("Reference Image Overlay (5% - 80%)"); tvRefTitle.setTextColor(textColor); tvRefTitle.setTextSize(13f); tvRefTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams refTitleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); refTitleLp.setMargins(0, dp8 / 2, 0, dp8 / 2); tvRefTitle.setLayoutParams(refTitleLp);

            LinearLayout refBtnRow = new LinearLayout(this); refBtnRow.setOrientation(LinearLayout.HORIZONTAL); refBtnRow.setWeightSum(2f);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); rowLp.setMargins(0, 0, 0, dp8 / 2); refBtnRow.setLayoutParams(rowLp);

            Button btnLoadRef = new Button(this); btnLoadRef.setText("Add");
            makeButtonRounded(btnLoadRef, btnBgColor, textColor, 36f);
            LinearLayout.LayoutParams halfLp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); halfLp1.setMargins(0, 0, dp8 / 2, 0); btnLoadRef.setLayoutParams(halfLp1);
            btnLoadRef.setOnClickListener(v -> {
                Intent pick = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pick.setType("image/*");
                startActivityForResult(pick, REQ_PICK_REF_IMAGE);
            });

            btnRefToggle = new Button(this); btnRefToggle.setText("Ref: OFF");
            makeButtonRounded(btnRefToggle, btnBgColor, textColor, 36f);
            LinearLayout.LayoutParams halfLp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); halfLp2.setMargins(dp8 / 2, 0, 0, 0); btnRefToggle.setLayoutParams(halfLp2);
            btnRefToggle.setOnClickListener(v -> {
                if (drawingView == null) return;
                if (!drawingView.hasReferenceBitmap()) {
                    Toast.makeText(this, "Tap 'Add / Change Ref' to pick an image first!", Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean visible = drawingView.toggleReferenceVisibility();
                btnRefToggle.setText(visible ? "Ref: ON" : "Ref: OFF");
                makeButtonRounded(btnRefToggle, visible ? Color.parseColor("#34C759") : btnBgColor, visible ? Color.WHITE : textColor, 36f);
            });
            refBtnRow.addView(btnLoadRef); refBtnRow.addView(btnRefToggle);

            ModernPrecisionSlider slRefOpacity = new ModernPrecisionSlider(this, isDarkTheme, 5f, 80f, 35f, false);
            LinearLayout.LayoutParams refSlLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
            refSlLp.setMargins(0, 0, 0, dp10); slRefOpacity.setLayoutParams(refSlLp);
            slRefOpacity.setOnValueChangeListener(val -> { if (drawingView != null) drawingView.setReferenceOpacityPercent(val); });

            int hardIdx = settingsContainer.indexOfChild(modernHardness);
            if (hardIdx >= 0) {
                settingsContainer.addView(tvShadeTitle, hardIdx + 1);
                settingsContainer.addView(btnShadingMode, hardIdx + 2);
                settingsContainer.addView(activePanelShadePreview, hardIdx + 3);
                settingsContainer.addView(tvRefTitle, hardIdx + 4);
                settingsContainer.addView(refBtnRow, hardIdx + 5);
                settingsContainer.addView(slRefOpacity, hardIdx + 6);
            }
        }
    }

    public void updateColorUI(int color) { if (colorPicker != null) { isUpdatingColor = true; colorPicker.setColor(color); if (etHexCode != null) etHexCode.setText(String.format("#%06X", (0xFFFFFF & color))); if (activePanelShadePreview != null) activePanelShadePreview.setPreviewColor(color); isUpdatingColor = false; } }
    private void setModernMenuBackground(View view, int color, boolean isLeft) { if (view == null) return; GradientDrawable gd = new GradientDrawable(); gd.setColor(color); gd.setCornerRadii(isLeft ? new float[]{0,0,60,60,60,60,0,0} : new float[]{60,60,0,0,0,0,60,60}); view.setBackground(gd); }
    private void applyTextColor(View view, int color) { if (view instanceof TextView && !(view instanceof Button) && !(view instanceof EditText)) { ((TextView) view).setTextColor(color); } else if (view instanceof ViewGroup) { ViewGroup vg = (ViewGroup) view; for (int i = 0; i < vg.getChildCount(); i++) applyTextColor(vg.getChildAt(i), color); } }

    // PILL SLIDER: Rounded capsule track, circular left cap at 0, dual-clipped precision value display
    public static class ModernPrecisionSlider extends View {
        public interface OnValueChangeListener { void onValueChanged(float value); }
        private float minVal, maxVal, currentVal; private final boolean showDecimals;
        private final int trackColor, fillColor, textOnTrackColor, textOnFillColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Path clipPath = new Path();
        private OnValueChangeListener listener;

        public ModernPrecisionSlider(Context context, boolean isDark, float min, float max, float initial, boolean decimals) {
            super(context); this.minVal = min; this.maxVal = max; this.currentVal = Math.max(min, Math.min(initial, max)); this.showDecimals = decimals;
            trackColor = isDark ? Color.parseColor("#545458") : Color.parseColor("#D1D1D6");
            fillColor = isDark ? Color.WHITE : Color.parseColor("#2C2C2E");
            textOnTrackColor = isDark ? Color.WHITE : Color.parseColor("#2C2C2E");
            textOnFillColor = isDark ? Color.BLACK : Color.WHITE;
        }
        public void setOnValueChangeListener(OnValueChangeListener l) { this.listener = l; }
        public float getValue() { return currentVal; }
        public void setValue(float v) { currentVal = Math.max(minVal, Math.min(v, maxVal)); invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            float norm = (maxVal > minVal) ? ((currentVal - minVal) / (maxVal - minVal)) : 0f;
            norm = Math.max(0f, Math.min(1f, norm));

            paint.setStyle(Paint.Style.FILL); paint.setColor(trackColor);
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);

            String valStr = showDecimals ? String.format(Locale.US, "%.2f", currentVal) : String.valueOf(Math.round(currentVal));
            paint.setTextSize(h * 0.40f); paint.setTypeface(Typeface.DEFAULT_BOLD);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = (h - fm.ascent - fm.descent) / 2f;
            float textX = w - (h * 0.45f) - paint.measureText(valStr);

            paint.setColor(textOnTrackColor);
            canvas.drawText(valStr, textX, textY, paint);

            float fillRight = h + (w - h) * norm;
            canvas.save();
            clipPath.reset(); clipPath.addRoundRect(0, 0, fillRight, h, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            paint.setColor(fillColor);
            canvas.drawRoundRect(0, 0, fillRight, h, r, r, paint);
            paint.setColor(textOnFillColor);
            canvas.drawText(valStr, textX, textY, paint);
            canvas.restore();
        }

        @android.annotation.SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true); }
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false); }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float w = getWidth(), h = getHeight();
                float usableW = Math.max(1f, w - h);
                float norm = Math.max(0f, Math.min(1f, (event.getX() - h / 2f) / usableW));
                currentVal = minVal + norm * (maxVal - minVal);
                if (!showDecimals) currentVal = Math.round(currentVal);
                if (listener != null) listener.onValueChanged(currentVal);
                invalidate();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private class ShadingPreviewView extends View {
        private final int fixedShadeMode; private int pColor;
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final RectF cardRect = new RectF(); private final Path samplePath = new Path();
        public ShadingPreviewView(Context context, int shadeMode, int color) {
            super(context); this.fixedShadeMode = shadeMode; this.pColor = color;
            setLayerType(LAYER_TYPE_SOFTWARE, null); bgPaint.setColor(Color.WHITE); bgPaint.setStyle(Paint.Style.FILL);
        }
        public void setPreviewColor(int c) { this.pColor = c; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
            cardRect.set(6, 6, w - 6, h - 6); canvas.drawRoundRect(cardRect, 20f, 20f, bgPaint);
            int mode = (fixedShadeMode >= 0) ? fixedShadeMode : currentShadingMode;
            int bType = (activeBrushIndex == 8 || activeBrushIndex == 9 || activeBrushIndex == 10) ? 0 : activeBrushIndex;
            int drawCol = (pColor == Color.WHITE) ? Color.DKGRAY : pColor;
            float curHard = (drawingView != null) ? drawingView.getBrushHardness() : 100f;
            AdvancedDrawingView.applyBrushProfileToPaint(strokePaint, bType, paintBrushSubStyle, mode, 16f, 230, drawCol, curHard, true);
            samplePath.reset();
            AdvancedDrawingView.buildShadingPreviewPath(samplePath, mode, 28f, w - 28f, h / 2f, h * 0.28f);
            canvas.drawPath(samplePath, strokePaint);
        }
    }

    private class BrushPreviewView extends View {
        private final Paint previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Path wavePath = new Path(); private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bgRect = new RectF(); private final int bType;
        public BrushPreviewView(Context context, int brushType) {
            super(context); this.bType = brushType; setLayerType(LAYER_TYPE_SOFTWARE, null);
            backgroundPaint.setColor(Color.WHITE); backgroundPaint.setStyle(Paint.Style.FILL);
            refreshProfile();
        }
        public void refreshProfile() {
            float curHard = (drawingView != null) ? drawingView.getBrushHardness() : 100f;
            if (bType != 10) { AdvancedDrawingView.applyBrushProfileToPaint(previewPaint, bType, paintBrushSubStyle, currentShadingMode, 14f, 255, Color.BLACK, curHard, true); }
            else { previewPaint.reset(); previewPaint.setAntiAlias(true); previewPaint.setColor(Color.parseColor("#4A90E2")); previewPaint.setStyle(Paint.Style.FILL); }
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(); bgRect.set(10, 10, w - 10, h - 10); canvas.drawRoundRect(bgRect, 20f, 20f, backgroundPaint);
            if (bType == 10) { canvas.drawCircle(w / 2f, h / 2f, 15f, previewPaint); }
            else if (currentShadingMode > 1 && bType != 8 && bType != 9) {
                wavePath.reset(); AdvancedDrawingView.buildShadingPreviewPath(wavePath, currentShadingMode, 22f, w - 22f, h / 2f, h * 0.25f);
                canvas.drawPath(wavePath, previewPaint);
            } else {
                wavePath.reset(); wavePath.moveTo(26, h / 2f); wavePath.cubicTo(w / 3f, 8, w * 2 / 3f, h - 8, w - 26, h / 2f); canvas.drawPath(wavePath, previewPaint);
            }
        }
    }

    private static class ColorPickerView extends View {
        private final Paint hueBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint thumbBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pHoriz = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint pVert = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF satValRect = new RectF(); private final RectF hueBarRect = new RectF();
        private float currentHue = 0f, currentSat = 1f, currentVal = 1f;
        private float satValCursorX = 0f, satValCursorY = 0f, hueCursorX = 0f;
        private OnColorChangeListener listener; private boolean isDraggingSatVal = false, isDraggingHue = false;

        public ColorPickerView(Context context) {
            super(context); setLayerType(LAYER_TYPE_SOFTWARE, null);
            thumbPaint.setStyle(Paint.Style.FILL); thumbPaint.setColor(Color.WHITE); thumbPaint.setShadowLayer(6f, 0, 2f, Color.argb(120, 0, 0, 0));
            thumbBorderPaint.setStyle(Paint.Style.STROKE); thumbBorderPaint.setStrokeWidth(4f); thumbBorderPaint.setColor(Color.parseColor("#333333"));
            borderP.setStyle(Paint.Style.STROKE); borderP.setStrokeWidth(3f); borderP.setColor(Color.argb(60, 255, 255, 255));
        }
        public void setOnColorChangeListener(OnColorChangeListener listener) { this.listener = listener; }
        public void setColor(int color) { float[] hsv = new float[3]; Color.colorToHSV(color, hsv); currentHue = hsv[0]; currentSat = hsv[1]; currentVal = hsv[2]; post(this::updateCursorsFromHSV); if (listener != null) listener.onColorChanged(color); }
        private void updateCursorsFromHSV() {
            int w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
            float pad = 18f, boxH = h * 0.68f; satValRect.set(pad, pad, w - pad, boxH); hueBarRect.set(pad, boxH + 24f, w - pad, boxH + 60f);
            satValCursorX = satValRect.left + currentSat * satValRect.width(); satValCursorY = satValRect.bottom - currentVal * satValRect.height();
            hueCursorX = hueBarRect.left + (currentHue / 360f) * hueBarRect.width(); invalidate();
        }
        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight); float pad = 18f, boxH = h * 0.68f;
            satValRect.set(pad, pad, w - pad, boxH); hueBarRect.set(pad, boxH + 24f, w - pad, boxH + 60f);
            int[] hueColors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
            hueBarPaint.setShader(new LinearGradient(hueBarRect.left, hueBarRect.top, hueBarRect.right, hueBarRect.top, hueColors, null, Shader.TileMode.CLAMP));
            pVert.setShader(new LinearGradient(satValRect.left, satValRect.top, satValRect.left, satValRect.bottom, 0x00000000, Color.BLACK, Shader.TileMode.CLAMP));
            updateCursorsFromHSV();
        }
        @Override protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas); if (satValRect.width() <= 0) return;
            int pureHue = Color.HSVToColor(new float[]{currentHue, 1f, 1f});
            pHoriz.setShader(new LinearGradient(satValRect.left, satValRect.top, satValRect.right, satValRect.top, Color.WHITE, pureHue, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(satValRect, 24f, 24f, pHoriz); canvas.drawRoundRect(satValRect, 24f, 24f, pVert); canvas.drawRoundRect(satValRect, 24f, 24f, borderP);
            canvas.drawRoundRect(hueBarRect, 18f, 18f, hueBarPaint); canvas.drawRoundRect(hueBarRect, 18f, 18f, borderP);
            canvas.drawCircle(satValCursorX, satValCursorY, 22f, thumbPaint); canvas.drawCircle(satValCursorX, satValCursorY, 22f, thumbBorderPaint);
            canvas.drawCircle(hueCursorX, hueBarRect.centerY(), 20f, thumbPaint); canvas.drawCircle(hueCursorX, hueBarRect.centerY(), 20f, thumbBorderPaint);
        }
        @android.annotation.SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
            float x = event.getX(), y = event.getY(); int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    if (hueBarRect.contains(x, y) || Math.abs(y - hueBarRect.centerY()) < 36f) { isDraggingHue = true; isDraggingSatVal = false; } else { isDraggingSatVal = true; isDraggingHue = false; }
                    updateColorFromTouch(x, y); return true;
                case MotionEvent.ACTION_MOVE: updateColorFromTouch(x, y); return true;
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    isDraggingHue = false; isDraggingSatVal = false; return true;
            }
            return super.onTouchEvent(event);
        }
        private void updateColorFromTouch(float x, float y) {
            if (isDraggingHue) {
                hueCursorX = Math.max(hueBarRect.left, Math.min(x, hueBarRect.right));
                currentHue = ((hueCursorX - hueBarRect.left) / hueBarRect.width()) * 360f; if (currentHue >= 360f) currentHue = 0f;
            } else {
                satValCursorX = Math.max(satValRect.left, Math.min(x, satValRect.right)); satValCursorY = Math.max(satValRect.top, Math.min(y, satValRect.bottom));
                currentSat = (satValCursorX - satValRect.left) / satValRect.width(); currentVal = 1f - ((satValCursorY - satValRect.top) / satValRect.height());
            }
            int color = Color.HSVToColor(new float[]{currentHue, Math.max(0f, Math.min(1f, currentSat)), Math.max(0f, Math.min(1f, currentVal))});
            if (listener != null) listener.onColorChanged(color); invalidate();
        }
    }

    private static class AdvancedDrawingView extends View {
        private static final SparseArray<Bitmap> TEXTURE_MASK_CACHE = new SparseArray<>();
        private static final SparseArray<BitmapShader> SHADER_CACHE = new SparseArray<>();
        private static final SparseArray<Integer> SHADER_COLOR_KEY = new SparseArray<>();

        private final Bitmap baseBitmap; private Bitmap paintBitmap; private Canvas paintCanvas; private Bitmap strokeBitmap; private Canvas strokeCanvas;
        private Bitmap referenceBitmap = null; private boolean isReferenceVisible = false; private int referenceAlpha = (int) (0.35f * 255f);
        private final Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); private final RectF refDestRect = new RectF();
        private Bitmap smudgeSampleBitmap; private Canvas smudgeSampleCanvas; private BitmapShader smudgeShader; private final Matrix smudgeMatrix = new Matrix();
        private final int canvasBgColor; private final ArrayList<Bitmap> rasterUndoStack = new ArrayList<>(); private final ArrayList<Bitmap> rasterRedoStack = new ArrayList<>();
        private final Path segPath = new Path(); private final Path fullStrokePath = new Path(); private final RectF dirtyStrokeRect = new RectF();
        private final Paint currentPaint; private final Paint strokeCompositePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint smudgeStampPaint; private final Paint renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path indicatorPath = new Path(); private final Paint indShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint indStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint indFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float brushSize = 12f; private int brushOpacity = 255; private int brushColor = Color.RED; private float brushHardness = 100f;
        private int brushType = 0; private int paintBrushSubStyle = 0; private int shadingMode = 0;
        private float lastX, lastY, lastMidX, lastMidY; private float circPhase = 0f, circSpin = 1f, hatchStepCarry = 0f;
        private final Random strokeRng = new Random(); private final Matrix grainRandomMatrix = new Matrix();
        private float downScreenX, downScreenY; private boolean isDrawingStroke = false;
        public boolean isZoomMode = false; public boolean isGridMode = false; private float viewZoom = 1f; private float viewPanX = 0f; private float viewPanY = 0f;
        private final ScaleGestureDetector scaleDetector; private final PointF lastTouch = new PointF(); private int activePointerId = MotionEvent.INVALID_POINTER_ID;
        private boolean isLongPressFired = false; private boolean showPickerIndicator = false; private float screenTouchX, screenTouchY; private float downImgX, downImgY;

        private final Runnable longPressRunnable = new Runnable() { @Override public void run() { if (!isZoomMode && brushType != 10) { isLongPressFired = true; showPickerIndicator = true; if (strokeBitmap != null) strokeBitmap.eraseColor(Color.TRANSPARENT); isDrawingStroke = false; pickColorAtTouch(downImgX, downImgY); } } };

        public AdvancedDrawingView(Context context, Bitmap baseImage) {
            super(context); setLayerType(LAYER_TYPE_HARDWARE, null);
            if (baseImage == null) {
                this.canvasBgColor = ((AdvancedCanvasActivity) context).getCanvasBgColor(); DisplayMetrics m = context.getResources().getDisplayMetrics();
                int w = Math.min(2048, (m.widthPixels > 0 ? m.widthPixels : 1080) * 2); int h = Math.min(2560, (m.heightPixels > 0 ? m.heightPixels : 1920) * 2);
                this.baseBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); this.baseBitmap.eraseColor(canvasBgColor);
            } else { this.baseBitmap = baseImage; this.canvasBgColor = Color.WHITE; }
            currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG); smudgeStampPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            indShadowPaint.setShadowLayer(8f, 0, 4f, Color.BLACK); indShadowPaint.setColor(Color.parseColor("#333333"));
            indStrokePaint.setStyle(Paint.Style.STROKE); indStrokePaint.setColor(Color.WHITE); indStrokePaint.setStrokeWidth(6f); indFillPaint.setStyle(Paint.Style.FILL);
            gridShadowPaint.setColor(Color.argb(120, 0, 0, 0)); gridShadowPaint.setStyle(Paint.Style.STROKE); gridShadowPaint.setStrokeWidth(4f);
            gridPaint.setColor(Color.argb(200, 255, 255, 255)); gridPaint.setStyle(Paint.Style.STROKE); gridPaint.setStrokeWidth(2f);
            scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(@NonNull ScaleGestureDetector detector) { viewZoom = Math.max(1f, Math.min(viewZoom * detector.getScaleFactor(), 50f)); invalidate(); return true; }
            });
        }

        public void setReferenceBitmap(Bitmap bmp) { this.referenceBitmap = bmp; this.isReferenceVisible = (bmp != null); invalidate(); }
        public boolean hasReferenceBitmap() { return this.referenceBitmap != null; }
        public boolean toggleReferenceVisibility() { if (referenceBitmap == null) return false; isReferenceVisible = !isReferenceVisible; invalidate(); return isReferenceVisible; }
        public void setReferenceOpacityPercent(float pct) { float clamped = Math.max(5f, Math.min(80f, pct)); this.referenceAlpha = Math.round((clamped / 100f) * 255f); invalidate(); }

        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            super.onSizeChanged(w, h, oldWidth, oldHeight);
            if (paintBitmap == null && baseBitmap != null) {
                try {
                    paintBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888); paintCanvas = new Canvas(paintBitmap);
                    strokeBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888); strokeCanvas = new Canvas(strokeBitmap);
                    saveUndoState();
                } catch (OutOfMemoryError e) { Toast.makeText(getContext(), "Canvas initialized with limited memory", Toast.LENGTH_SHORT).show(); }
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (paintBitmap == null) return;
            canvas.save();
            float wTarget = paintBitmap.getWidth(); float hTarget = paintBitmap.getHeight();
            float scale = Math.min((float) getWidth() / wTarget, (float) getHeight() / hTarget) * viewZoom;
            float dx = (getWidth() - wTarget * scale) / 2f + viewPanX; float dy = (getHeight() - hTarget * scale) / 2f + viewPanY;
            canvas.translate(dx, dy); canvas.scale(scale, scale);
            renderPaint.setFilterBitmap(viewZoom <= 5f); renderPaint.setAntiAlias(viewZoom <= 5f);
            if (baseBitmap != null) canvas.drawBitmap(baseBitmap, 0, 0, renderPaint);

            // Non-destructive Reference Image Overlay (5% - 80% opacity above base image so user can sketch over it)
            if (isReferenceVisible && referenceBitmap != null && !referenceBitmap.isRecycled()) {
                float refScale = Math.min(wTarget / referenceBitmap.getWidth(), hTarget / referenceBitmap.getHeight());
                float rw = referenceBitmap.getWidth() * refScale; float rh = referenceBitmap.getHeight() * refScale;
                float rx = (wTarget - rw) / 2f; float ry = (hTarget - rh) / 2f;
                refDestRect.set(rx, ry, rx + rw, ry + rh);
                refPaint.setAlpha(referenceAlpha);
                canvas.drawBitmap(referenceBitmap, null, refDestRect, refPaint);
            }

            if (paintBitmap != null) canvas.drawBitmap(paintBitmap, 0, 0, renderPaint);
            if (isDrawingStroke && strokeBitmap != null && !isLongPressFired && brushType != 8 && brushType != 9 && brushType != 10) {
                canvas.drawBitmap(strokeBitmap, 0, 0, strokeCompositePaint);
            }
            if (isGridMode) {
                float cellW = wTarget / 9f; float cellH = hTarget / 9f;
                for (int i = 1; i < 9; i++) {
                    canvas.drawLine((cellW * i), 0, (cellW * i), hTarget, gridShadowPaint); canvas.drawLine(0, (cellH * i), wTarget, (cellH * i), gridShadowPaint);
                    canvas.drawLine((cellW * i), 0, (cellW * i), hTarget, gridPaint); canvas.drawLine(0, (cellH * i), wTarget, (cellH * i), gridPaint);
                }
                canvas.drawRect(0, 0, wTarget, hTarget, gridShadowPaint); canvas.drawRect(0, 0, wTarget, hTarget, gridPaint);
            }
            canvas.restore();
            if (showPickerIndicator) {
                float cx = screenTouchX; float cy = screenTouchY;
                indicatorPath.reset(); indicatorPath.moveTo(cx, cy); indicatorPath.cubicTo(cx - 70f, cy - 70f, cx - 70f, cy - 180f, cx, cy - 180f); indicatorPath.cubicTo(cx + 70f, cy - 180f, cx + 70f, cy - 70f, cx, cy); indicatorPath.close();
                canvas.drawPath(indicatorPath, indShadowPaint); canvas.drawPath(indicatorPath, indStrokePaint);
                indFillPaint.setColor(brushColor); canvas.drawCircle(cx, cy - 120f, 45f, indFillPaint);
            }
        }

        private static Bitmap getOrCreateAlphaMask(int textureMode) {
            Bitmap mask = TEXTURE_MASK_CACHE.get(textureMode);
            if (mask != null && !mask.isRecycled()) return mask;
            int size = 128; mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Random rng = new Random(textureMode * 9973L);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int a = 255;
                    if (textureMode == 1) { int n = rng.nextInt(256); int wv = (int) (45 * (Math.sin(x * 0.45) + Math.cos(y * 0.45))); a = (n + wv > 110) ? Math.min(255, (int) ((n - 55) * 1.4f)) : 20; }
                    else if (textureMode == 2) { int wv = (int) (70 * Math.abs(Math.sin(x * 0.6) * Math.cos(y * 0.6))); int d = rng.nextInt(190); a = (d + wv > 100) ? Math.min(240, d + 35) : 25; }
                    else if (textureMode == 3) { int st = ((x + y / 3) % 5 == 0 || (x + y / 2) % 7 == 0) ? 245 : 45; a = Math.max(15, Math.min(255, st - rng.nextInt(50))); }
                    else if (textureMode == 4) { int rdg = (int) (135 + 105 * Math.sin((x + y * 0.5) * 0.35)); a = rng.nextInt(100) < 16 ? 25 : Math.max(25, Math.min(255, rdg)); }
                    else if (textureMode == 6) { a = rng.nextInt(100) > 20 ? (155 + rng.nextInt(100)) : 40; }
                    else if (textureMode == 101) { int grain = rng.nextInt(80); int wave = (int) (35 * Math.sin(x * 0.18) * Math.cos(y * 0.18)); a = Math.max(70, Math.min(230, 150 + wave - 35 + grain)); }
                    pixels[y * size + x] = Color.argb(a, 255, 255, 255);
                }
            }
            mask.setPixels(pixels, 0, size, 0, 0, size, size);
            TEXTURE_MASK_CACHE.put(textureMode, mask);
            return mask;
        }

        private static BitmapShader createProceduralTextureShader(int textureMode, int color) {
            Integer cachedCol = SHADER_COLOR_KEY.get(textureMode);
            BitmapShader cachedShader = SHADER_CACHE.get(textureMode);
            if (cachedShader != null && cachedCol != null && cachedCol == color) { return cachedShader; }
            Bitmap mask = getOrCreateAlphaMask(textureMode);
            Bitmap tinted = Bitmap.createBitmap(mask.getWidth(), mask.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tinted);
            c.drawBitmap(mask, 0, 0, null);
            c.drawColor(color, PorterDuff.Mode.SRC_IN);
            BitmapShader shader = new BitmapShader(tinted, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            SHADER_COLOR_KEY.put(textureMode, color);
            SHADER_CACHE.put(textureMode, shader);
            return shader;
        }

        public static void buildShadingPreviewPath(Path outPath, int shadeMode, float startX, float endX, float cy, float amp) {
            float span = endX - startX;
            if (shadeMode == 2) { // Circulism: overlapping circular scribble loops
                int steps = 95;
                for (int i = 0; i <= steps; i++) {
                    float t = i / (float) steps; float cx = startX + t * span; float ang = t * 11.5f * (float) Math.PI;
                    float rx = amp * (0.85f + 0.25f * (float) Math.sin(ang * 0.37f)); float ry = amp * (0.85f + 0.25f * (float) Math.cos(ang * 0.29f));
                    float px = cx + rx * (float) Math.cos(ang); float py = cy + ry * (float) Math.sin(ang);
                    if (i == 0) outPath.moveTo(px, py); else outPath.lineTo(px, py);
                }
            } else if (shadeMode == 3) { // Hatching: directional parallel diagonal lines
                for (float x = startX; x <= endX; x += 7f) {
                    outPath.moveTo(x - amp * 0.6f, cy + amp * 0.9f);
                    outPath.lineTo(x + amp * 0.6f, cy - amp * 0.9f);
                }
            } else if (shadeMode == 4) { // Cross-Hatching: intersecting directional mesh lines
                for (float x = startX; x <= endX; x += 8.5f) {
                    outPath.moveTo(x - amp * 0.65f, cy + amp * 0.9f); outPath.lineTo(x + amp * 0.65f, cy - amp * 0.9f);
                    outPath.moveTo(x - amp * 0.65f, cy - amp * 0.9f); outPath.lineTo(x + amp * 0.65f, cy + amp * 0.9f);
                }
            } else if (shadeMode == 5) { // Contour Hatching: curved 3D ribs
                for (float x = startX; x <= endX; x += 8.5f) {
                    outPath.moveTo(x, cy - amp * 0.95f);
                    outPath.quadTo(x + amp * 0.95f, cy, x, cy + amp * 0.95f);
                }
            } else if (shadeMode == 6) { // Stippling: organic stipple dot cluster
                Random r = new Random(4021L);
                for (int i = 0; i < 110; i++) {
                    float px = startX + r.nextFloat() * span; float py = cy + (r.nextFloat() - 0.5f) * amp * 1.8f;
                    outPath.moveTo(px, py); outPath.lineTo(px + 1.2f, py + 0.8f);
                }
            } else if (shadeMode == 7) { // Scribble Lines: chaotic sketch twitches
                Random r = new Random(7719L); float px = startX, py = cy; outPath.moveTo(px, py);
                for (int i = 1; i <= 55; i++) {
                    float nx = startX + (i / 55f) * span + (r.nextFloat() - 0.5f) * 14f;
                    float ny = cy + (r.nextFloat() - 0.5f) * amp * 1.9f;
                    outPath.quadTo(px + (r.nextFloat() - 0.5f) * 12f, py + (r.nextFloat() - 0.5f) * 12f, nx, ny);
                    px = nx; py = ny;
                }
            } else {
                outPath.moveTo(startX, cy + amp * 0.2f);
                outPath.cubicTo(startX + span * 0.33f, cy - amp, startX + span * 0.66f, cy + amp, endX, cy - amp * 0.2f);
            }
        }

        public static void applyBrushProfileToPaint(Paint p, int bType, int subStyle, int shadeMode, float size, int alpha, int color, float hardness, boolean isPreview) {
            p.reset(); p.setAntiAlias(true); p.setDither(true); p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeCap(Paint.Cap.ROUND);
            int finalColor = color; float finalBlur = 0f; int drawAlpha = isPreview ? alpha : 255;
            p.setColor(finalColor);

            float softness = Math.max(0f, Math.min(1f, (100f - hardness) / 100f));
            boolean isVectorShade = (shadeMode >= 2 && shadeMode <= 7 && bType != 8 && bType != 9 && bType != 10);
            float effectiveSize = isVectorShade ? Math.max(1.8f, Math.min(12f, size * 0.18f)) : size;

            PathEffect nativePathEffect = null;
            Shader nativeShader = null;

            switch (bType) {
                case 0: // Pencil
                    p.setStrokeWidth(Math.max(1.5f, effectiveSize * 0.52f)); p.setAlpha((int) (drawAlpha * 0.88f));
                    nativePathEffect = new DiscretePathEffect(3f, 1.5f);
                    nativeShader = createProceduralTextureShader(6, finalColor);
                    break;
                case 1: // Pen
                    p.setStrokeWidth(Math.max(1.5f, effectiveSize * 0.85f)); p.setAlpha(drawAlpha);
                    break;
                case 2: // Paint Brush
                    p.setStrokeWidth(effectiveSize); p.setAlpha((int) (drawAlpha * 0.92f));
                    if (subStyle == 1) { p.setStrokeCap(Paint.Cap.SQUARE); }
                    else if (subStyle == 2) { p.setAlpha((int) (drawAlpha * 0.75f)); nativePathEffect = new DiscretePathEffect(5f, 3.0f); finalBlur = effectiveSize * 0.08f * softness; }
                    else { finalBlur = effectiveSize * 0.1f * softness; }
                    break;
                case 3: // Marker
                    p.setStrokeWidth(effectiveSize * 1.4f); p.setStrokeCap(Paint.Cap.SQUARE); p.setAlpha(isPreview ? (int) (alpha * 0.65f) : 255);
                    break;
                case 4: // Ink Brush
                    p.setStrokeWidth(effectiveSize * 1.15f); p.setAlpha(drawAlpha);
                    nativePathEffect = new CornerPathEffect(Math.max(4f, effectiveSize));
                    break;
                case 5: // Airbrush
                    p.setStrokeWidth(effectiveSize * 1.8f); p.setAlpha(isPreview ? (int) (alpha * 0.38f) : 255);
                    finalBlur = Math.min(48f, effectiveSize * (0.20f + 0.65f * softness));
                    break;
                case 6: // Watercolor
                    p.setStrokeWidth(effectiveSize * 1.45f); p.setAlpha(isPreview ? (int) (alpha * 0.48f) : 255);
                    finalBlur = Math.min(36f, effectiveSize * (0.12f + 0.55f * softness));
                    break;
                case 7: // Oil Brush
                    p.setStrokeWidth(effectiveSize * 1.15f); p.setAlpha(drawAlpha);
                    nativePathEffect = new DiscretePathEffect(7f, 2.5f);
                    nativeShader = createProceduralTextureShader(3, finalColor);
                    break;
                case 8: // Smudge
                    p.setStrokeWidth(size); p.setAlpha((int) (drawAlpha * 0.8f)); finalBlur = Math.min(32f, size * (0.10f + 0.50f * softness));
                    break;
                case 9: // Eraser (Supports Hardness for Soft Airbrush Erasing vs Hard Crisp Erasing!)
                    p.setStrokeWidth(size);
                    if (!isPreview) { p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); p.setColor(Color.TRANSPARENT); }
                    else { p.setColor(Color.GRAY); nativePathEffect = new DashPathEffect(new float[]{20f, 20f}, 0f); }
                    p.setAlpha(255);
                    break;
                case 11: // Charcoal Stick
                    p.setStrokeWidth(effectiveSize * 1.25f); p.setAlpha(drawAlpha);
                    nativePathEffect = new DiscretePathEffect(Math.max(3f, effectiveSize * 0.22f), Math.max(1.8f, effectiveSize * 0.18f));
                    nativeShader = createProceduralTextureShader(1, finalColor);
                    finalBlur = Math.min(28f, effectiveSize * 0.08f * softness);
                    break;
                case 12: // Chalk Pastel
                    p.setStrokeWidth(effectiveSize * 1.35f); p.setAlpha(drawAlpha);
                    nativePathEffect = new DiscretePathEffect(Math.max(4f, effectiveSize * 0.25f), Math.max(1.5f, effectiveSize * 0.14f));
                    nativeShader = createProceduralTextureShader(2, finalColor);
                    finalBlur = Math.min(30f, effectiveSize * 0.14f * softness);
                    break;
                case 13: // Calligraphy Nib (40-degree angled chisel nib — preserved across all shading modes)
                    p.setStyle(Paint.Style.FILL); p.setAlpha(drawAlpha);
                    float nibScale = isVectorShade ? Math.max(3.5f, size * 0.26f) : size;
                    float hw = Math.max(3.2f, nibScale * 0.85f); float hh = Math.max(1.1f, nibScale * 0.16f);
                    Path nib = new Path(); nib.moveTo(-hw, -hh); nib.lineTo(hw, -hh); nib.lineTo(hw, hh); nib.lineTo(-hw, hh); nib.close();
                    Matrix rot = new Matrix(); rot.setRotate(-40f); nib.transform(rot);
                    nativePathEffect = new PathDashPathEffect(nib, Math.max(1.8f, nibScale * 0.14f), 0f, PathDashPathEffect.Style.TRANSLATE);
                    break;
                case 14: // Fan Bristle (Multi-tip splayed hog bristle — preserved across all shading modes)
                    p.setStyle(Paint.Style.FILL); p.setAlpha(drawAlpha);
                    float fanScale = isVectorShade ? Math.max(3.8f, size * 0.28f) : size;
                    Path fan = new Path(); float radSize = Math.max(4f, fanScale * 0.9f); float bRad = Math.max(1.2f, fanScale * 0.12f);
                    for (int a = -60; a <= 60; a += 24) {
                        double rad = Math.toRadians(a); float bx = (float) (Math.cos(rad) * radSize * 0.55f); float by = (float) (Math.sin(rad) * radSize);
                        fan.addCircle(bx, by, bRad, Path.Direction.CW);
                    }
                    nativePathEffect = new PathDashPathEffect(fan, Math.max(2.5f, fanScale * 0.22f), 0f, PathDashPathEffect.Style.MORPH);
                    nativeShader = createProceduralTextureShader(3, finalColor);
                    break;
                case 15: // Palette Knife
                    p.setStrokeWidth(effectiveSize * 1.65f); p.setStrokeCap(Paint.Cap.BUTT); p.setStrokeJoin(Paint.Join.BEVEL); p.setAlpha(drawAlpha);
                    nativePathEffect = new ComposePathEffect(new DiscretePathEffect(Math.max(6f, effectiveSize * 0.35f), Math.max(2f, effectiveSize * 0.16f)), new CornerPathEffect(4f));
                    nativeShader = createProceduralTextureShader(4, finalColor);
                    break;
            }

            Shader shadingShader = null;
            PathEffect shadingPathEffect = null;
            if (shadeMode == 1 && bType != 8 && bType != 9 && bType != 10) {
                if (bType != 13 && bType != 14) p.setStrokeWidth(Math.max(size, p.getStrokeWidth()) * 1.45f);
                finalBlur = Math.max(finalBlur, Math.min(48f, size * (0.25f + 0.55f * softness)));
                shadingShader = createProceduralTextureShader(101, finalColor);
            } else if (shadeMode == 2 && bType != 8 && bType != 9 && bType != 10) {
                shadingPathEffect = new CornerPathEffect(Math.max(4f, size * 0.22f));
            }

            if (nativeShader != null && shadingShader != null) {
                p.setShader(new ComposeShader(nativeShader, shadingShader, PorterDuff.Mode.DST_IN));
            } else if (shadingShader != null) {
                p.setShader(shadingShader);
            } else if (nativeShader != null) {
                p.setShader(nativeShader);
            }

            if (nativePathEffect != null && shadingPathEffect != null) {
                p.setPathEffect(new ComposePathEffect(nativePathEffect, shadingPathEffect));
            } else if (nativePathEffect != null) {
                p.setPathEffect(nativePathEffect);
            } else if (shadingPathEffect != null) {
                p.setPathEffect(shadingPathEffect);
            }

            // Full-range Hardness Gaussian Feathering (0 = ultra-soft feathered edge, 100 = crisp sharp edge)
            if (softness > 0.02f) {
                float maxAllowedBlur = isVectorShade ? 26f : 64f;
                float hardnessBlur = Math.min(maxAllowedBlur, Math.max(1.5f, size * softness * (isVectorShade ? 0.38f : 0.78f)));
                finalBlur = Math.max(finalBlur, hardnessBlur);
            }
            if (finalBlur > 0.4f) {
                p.setMaskFilter(new BlurMaskFilter(finalBlur, BlurMaskFilter.Blur.NORMAL));
            }
        }

        private void randomizeShaderMatrixForDirection(float dX, float dY) {
            Shader s = currentPaint.getShader();
            if (s != null) {
                float angleDeg = (float) Math.toDegrees(Math.atan2(dY, dX));
                grainRandomMatrix.reset();
                grainRandomMatrix.postTranslate(strokeRng.nextFloat() * 128f, strokeRng.nextFloat() * 128f);
                grainRandomMatrix.postRotate(angleDeg + (strokeRng.nextFloat() - 0.5f) * 25f);
                s.setLocalMatrix(grainRandomMatrix);
            }
        }

        private void configureStrokeCompositePaint() {
            strokeCompositePaint.reset(); strokeCompositePaint.setAntiAlias(true); strokeCompositePaint.setFilterBitmap(true);
            float softness = Math.max(0f, Math.min(1f, (100f - brushHardness) / 100f));
            int targetAlpha = (int) (brushOpacity * (1f - softness * 0.18f));
            if (brushType == 3) { targetAlpha = (int) (targetAlpha * 0.65f); strokeCompositePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN)); }
            else if (brushType == 5) { targetAlpha = (int) (targetAlpha * 0.42f); }
            else if (brushType == 6) { targetAlpha = (int) (targetAlpha * 0.50f); strokeCompositePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN)); }
            else if (brushType == 12) { targetAlpha = (int) (targetAlpha * 0.88f); }
            if (shadingMode == 1) { targetAlpha = (int) (targetAlpha * 0.68f); }
            strokeCompositePaint.setAlpha(Math.max(6, Math.min(255, targetAlpha)));
        }

        private int sampleColorFast(float x, float y) { int px = Math.max(0, Math.min((int) x, paintBitmap.getWidth() - 1)); int py = Math.max(0, Math.min((int) y, paintBitmap.getHeight() - 1)); int c = paintBitmap.getPixel(px, py); if (Color.alpha(c) == 0 && baseBitmap != null) { int bx = Math.max(0, Math.min((int) x, baseBitmap.getWidth() - 1)); int by = Math.max(0, Math.min((int) y, baseBitmap.getHeight() - 1)); c = baseBitmap.getPixel(bx, by); } if (Color.alpha(c) == 0) return canvasBgColor; return c; }
        private void pickColorAtTouch(float imgX, float imgY) { int picked = sampleColorFast(imgX, imgY); if (Color.alpha(picked) > 0) { brushColor = picked; ((AdvancedCanvasActivity) getContext()).updateColorUI(picked); } invalidate(); }

        private void executeFloodFill(int imgX, int imgY) {
            Toast.makeText(getContext(), "Filling Area...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    Bitmap merged = getFinalBitmap(); int width = merged.getWidth(); int height = merged.getHeight();
                    int px = Math.max(0, Math.min(imgX, width - 1)); int py = Math.max(0, Math.min(imgY, height - 1));
                    int[] mergedPixels = new int[width * height]; merged.getPixels(mergedPixels, 0, width, 0, 0, width, height);
                    int targetColor = mergedPixels[py * width + px]; if (colorMatch(targetColor, brushColor)) return;
                    int[] paintPixels = new int[width * height]; paintBitmap.getPixels(paintPixels, 0, width, 0, 0, width, height);
                    int[] stack = new int[width * height]; int stackPointer = 0; stack[stackPointer++] = py * width + px;
                    while (stackPointer > 0) {
                        int pos = stack[--stackPointer]; int cx = pos % width; int cy = pos / width;
                        if (!colorMatch(mergedPixels[cy * width + cx], targetColor)) continue;
                        int w = cx; while (w > 0 && colorMatch(mergedPixels[cy * width + (w - 1)], targetColor)) w--;
                        int e = cx; while (e < width - 1 && colorMatch(mergedPixels[cy * width + (e + 1)], targetColor)) e++;
                        boolean scanAbove = false; boolean scanBelow = false;
                        for (int xIdx = w; xIdx <= e; xIdx++) {
                            int currentIdx = cy * width + xIdx; paintPixels[currentIdx] = brushColor; mergedPixels[currentIdx] = brushColor;
                            if (cy > 0) { int upIdx = (cy - 1) * width + xIdx; boolean matchAbove = colorMatch(mergedPixels[upIdx], targetColor); if (matchAbove && !scanAbove) { stack[stackPointer++] = upIdx; scanAbove = true; } else if (!matchAbove) scanAbove = false; }
                            if (cy < height - 1) { int downIdx = (cy + 1) * width + xIdx; boolean matchBelow = colorMatch(mergedPixels[downIdx], targetColor); if (matchBelow && !scanBelow) { stack[stackPointer++] = downIdx; scanBelow = true; } else if (!matchBelow) scanBelow = false; }
                        }
                    }
                    ((Activity) getContext()).runOnUiThread(() -> { paintBitmap.setPixels(paintPixels, 0, width, 0, 0, width, height); saveUndoState(); invalidate(); Toast.makeText(getContext(), "Fill Complete!", Toast.LENGTH_SHORT).show(); });
                } catch (OutOfMemoryError e) { ((Activity) getContext()).runOnUiThread(() -> Toast.makeText(getContext(), "Fill failed: Too much memory needed.", Toast.LENGTH_SHORT).show()); }
            }).start();
        }

        private boolean colorMatch(int c1, int c2) { if (c1 == c2) return true; return Math.abs(Color.red(c1) - Color.red(c2)) <= 25 && Math.abs(Color.green(c1) - Color.green(c2)) <= 25 && Math.abs(Color.blue(c1) - Color.blue(c2)) <= 25; }

        private void expandDirtyStrokeRect(float x, float y) {
            float pad = brushSize * 2.2f + 70f;
            dirtyStrokeRect.union(x - pad, y - pad, x + pad, y + pad);
        }

        // Generates real-time, direction-dependent, overlappable vector shading along the hand's movement vector (dX, dY)
        private boolean appendSegmentToPath(float imgX, float imgY, float scale) {
            boolean isContinuousStroke = (shadingMode <= 1 && brushType != 8 && brushType != 9 && brushType != 10);
            float minTol = isContinuousStroke ? Math.max(2.0f / scale, brushSize * 0.09f) : (1.5f / scale);
            float dX = imgX - lastX; float dY = imgY - lastY;
            float dist = (float) Math.hypot(dX, dY);
            if (dist < minTol) return false;

            expandDirtyStrokeRect(imgX, imgY);

            if (brushType == 8 && smudgeShader != null) {
                float spacing = Math.max(2f, brushSize * 0.12f);
                if (dist >= spacing) {
                    int steps = Math.max(1, (int) (dist / spacing)); float stepX = dX / steps; float stepY = dY / steps;
                    for (int i = 0; i < steps; i++) {
                        float nextX = lastX + stepX; float nextY = lastY + stepY;
                        smudgeMatrix.setTranslate(lastX - nextX, lastY - nextY); smudgeShader.setLocalMatrix(smudgeMatrix);
                        paintCanvas.drawCircle(nextX, nextY, brushSize / 2f, smudgeStampPaint); smudgeSampleCanvas.drawCircle(nextX, nextY, brushSize / 2f, smudgeStampPaint);
                        lastX = nextX; lastY = nextY;
                    }
                }
                return false;
            }

            float tx = dX / dist; float ty = dY / dist;
            float nx = -ty; float ny = tx;
            float spreadR = Math.max(4f, brushSize * 0.65f);

            if (shadingMode == 2 && brushType != 9 && brushType != 10) {
                // 2. CIRCULISM: Random, direction-oriented overlapping circular scribble loops
                float advancePerLoop = Math.max(3.5f, spreadR * 0.38f);
                float dThetaPerPx = (float) (2.0 * Math.PI / advancePerLoop);
                int subSteps = Math.max(2, Math.min(48, (int) (dist / Math.max(1.2f, spreadR * 0.06f))));
                for (int i = 1; i <= subSteps; i++) {
                    float t = i / (float) subSteps;
                    float cx = lastX + t * dX; float cy = lastY + t * dY;
                    circPhase += circSpin * (dist / subSteps) * dThetaPerPx * (0.85f + 0.30f * strokeRng.nextFloat());
                    float rTang = spreadR * (0.72f + 0.42f * (float) Math.sin(circPhase * 0.41f));
                    float rNorm = spreadR * (0.72f + 0.42f * (float) Math.cos(circPhase * 0.33f));
                    float driftN = (strokeRng.nextFloat() - 0.5f) * spreadR * 0.28f;
                    float driftT = (strokeRng.nextFloat() - 0.5f) * spreadR * 0.22f;
                    float orbT = rTang * (float) Math.cos(circPhase) + driftT;
                    float orbN = rNorm * (float) Math.sin(circPhase) + driftN;
                    float px = cx + orbT * tx + orbN * nx;
                    float py = cy + orbT * ty + orbN * ny;
                    float midX = (lastMidX + px) / 2f; float midY = (lastMidY + py) / 2f;
                    segPath.quadTo(lastMidX, lastMidY, midX, midY);
                    lastMidX = px; lastMidY = py;
                }
                lastX = imgX; lastY = imgY;
                return true;
            } else if (shadingMode == 3 && brushType != 9 && brushType != 10) {
                // 3. HATCHING: Parallel lines slanted relative to the hand's drawing direction
                float spacing = Math.max(3.5f, spreadR * 0.24f);
                float total = hatchStepCarry + dist;
                int count = (int) (total / spacing);
                hatchStepCarry = total - count * spacing;
                for (int i = 0; i < count; i++) {
                    float frac = (i * spacing) / Math.max(1f, dist);
                    float cx = lastX + frac * dX + (strokeRng.nextFloat() - 0.5f) * spacing * 0.4f;
                    float cy = lastY + frac * dY + (strokeRng.nextFloat() - 0.5f) * spacing * 0.4f;
                    float len = spreadR * (0.85f + 0.30f * strokeRng.nextFloat());
                    float slant = 0.55f + (strokeRng.nextFloat() - 0.5f) * 0.15f;
                    float hx = nx + slant * tx; float hy = ny + slant * ty;
                    float hNorm = (float) Math.hypot(hx, hy); hx /= hNorm; hy /= hNorm;
                    segPath.moveTo(cx - hx * len, cy - hy * len);
                    segPath.lineTo(cx + hx * len, cy + hy * len);
                }
                lastX = imgX; lastY = imgY; lastMidX = imgX; lastMidY = imgY;
                return count > 0;
            } else if (shadingMode == 4 && brushType != 9 && brushType != 10) {
                // 4. CROSS-HATCHING: Interlocking directional weave oriented to hand trajectory
                float spacing = Math.max(4.0f, spreadR * 0.28f);
                float total = hatchStepCarry + dist;
                int count = (int) (total / spacing);
                hatchStepCarry = total - count * spacing;
                for (int i = 0; i < count; i++) {
                    float frac = (i * spacing) / Math.max(1f, dist);
                    float cx = lastX + frac * dX + (strokeRng.nextFloat() - 0.5f) * spacing * 0.5f;
                    float cy = lastY + frac * dY + (strokeRng.nextFloat() - 0.5f) * spacing * 0.5f;
                    float len = spreadR * (0.82f + 0.32f * strokeRng.nextFloat());
                    float h1x = nx + 0.58f * tx, h1y = ny + 0.58f * ty; float n1 = (float) Math.hypot(h1x, h1y); h1x /= n1; h1y /= n1;
                    float h2x = nx - 0.62f * tx, h2y = ny - 0.62f * ty; float n2 = (float) Math.hypot(h2x, h2y); h2x /= n2; h2y /= n2;
                    segPath.moveTo(cx - h1x * len, cy - h1y * len); segPath.lineTo(cx + h1x * len, cy + h1y * len);
                    float ox = (strokeRng.nextFloat() - 0.5f) * spacing * 0.6f; float oy = (strokeRng.nextFloat() - 0.5f) * spacing * 0.6f;
                    segPath.moveTo(cx + ox - h2x * len, cy + oy - h2y * len); segPath.lineTo(cx + ox + h2x * len, cy + oy + h2y * len);
                }
                lastX = imgX; lastY = imgY; lastMidX = imgX; lastMidY = imgY;
                return count > 0;
            } else if (shadingMode == 5 && brushType != 9 && brushType != 10) {
                // 5. CONTOUR HATCHING: Curved 3D ribs bowing in the direction of hand movement
                float spacing = Math.max(4.0f, spreadR * 0.26f);
                float total = hatchStepCarry + dist;
                int count = (int) (total / spacing);
                hatchStepCarry = total - count * spacing;
                for (int i = 0; i < count; i++) {
                    float frac = (i * spacing) / Math.max(1f, dist);
                    float cx = lastX + frac * dX + (strokeRng.nextFloat() - 0.5f) * 2.5f;
                    float cy = lastY + frac * dY + (strokeRng.nextFloat() - 0.5f) * 2.5f;
                    float w = spreadR * (0.88f + 0.24f * strokeRng.nextFloat());
                    float bow = w * (0.55f + 0.25f * strokeRng.nextFloat());
                    float sx = cx - nx * w, sy = cy - ny * w;
                    float ex = cx + nx * w, ey = cy + ny * w;
                    float ctrlX = cx + tx * bow, ctrlY = cy + ty * bow;
                    segPath.moveTo(sx, sy);
                    segPath.quadTo(ctrlX, ctrlY, ex, ey);
                }
                lastX = imgX; lastY = imgY; lastMidX = imgX; lastMidY = imgY;
                return count > 0;
            } else if (shadingMode == 6 && brushType != 9 && brushType != 10) {
                // 6. STIPPLING: Randomized organic stipple dots/micro-flicks along hand direction
                int dotCount = Math.max(3, Math.min(35, (int) (dist * 0.6f + spreadR * 0.25f)));
                for (int i = 0; i < dotCount; i++) {
                    float frac = strokeRng.nextFloat();
                    float cx = lastX + frac * dX; float cy = lastY + frac * dY;
                    double ang = strokeRng.nextDouble() * Math.PI * 2.0;
                    float rad = spreadR * (float) Math.sqrt(strokeRng.nextFloat());
                    float px = cx + rad * (float) Math.cos(ang); float py = cy + rad * (float) Math.sin(ang);
                    float flick = 0.8f + strokeRng.nextFloat() * 1.4f;
                    segPath.moveTo(px, py);
                    segPath.lineTo(px + tx * flick + (strokeRng.nextFloat() - 0.5f), py + ty * flick + (strokeRng.nextFloat() - 0.5f));
                }
                lastX = imgX; lastY = imgY; lastMidX = imgX; lastMidY = imgY;
                return true;
            } else if (shadingMode == 7 && brushType != 9 && brushType != 10) {
                // 7. SCRIBBLE LINES: Chaotic multi-directional figure-8 & angular sketch twitches
                int steps = Math.max(2, Math.min(24, (int) (dist / 3.0f) + 2));
                for (int i = 1; i <= steps; i++) {
                    float frac = i / (float) steps;
                    float cx = lastX + frac * dX; float cy = lastY + frac * dY;
                    float offT = (strokeRng.nextFloat() - 0.5f) * spreadR * 1.5f;
                    float offN = (strokeRng.nextFloat() - 0.5f) * spreadR * 1.8f;
                    float px = cx + offT * tx + offN * nx;
                    float py = cy + offT * ty + offN * ny;
                    float ctrlX = (lastMidX + px) / 2f + (strokeRng.nextFloat() - 0.5f) * spreadR * 0.9f;
                    float ctrlY = (lastMidY + py) / 2f + (strokeRng.nextFloat() - 0.5f) * spreadR * 0.9f;
                    segPath.quadTo(ctrlX, ctrlY, px, py);
                    lastMidX = px; lastMidY = py;
                }
                lastX = imgX; lastY = imgY;
                return true;
            } else {
                // Normal or Mode 1 (Blended Shading): append to both segPath AND fullStrokePath so BlurMaskFilter renders as a single unified soft contour without cap-stacking!
                randomizeShaderMatrixForDirection(dX, dY);
                float midX = (lastX + imgX) / 2f; float midY = (lastY + imgY) / 2f;
                segPath.quadTo(lastX, lastY, midX, midY);
                fullStrokePath.quadTo(lastX, lastY, midX, midY);
                lastX = imgX; lastY = imgY; lastMidX = midX; lastMidY = midY;
                return true;
            }
        }

        private void renderCurrentStrokeToBuffer(boolean isFinalUp) {
            if (brushType == 9) {
                paintCanvas.drawPath(segPath, currentPaint);
                return;
            }
            if (strokeCanvas == null || strokeBitmap == null) return;
            boolean useUnifiedBlurPath = (shadingMode <= 1 && currentPaint.getMaskFilter() != null);
            if (useUnifiedBlurPath) {
                strokeCanvas.save();
                strokeCanvas.clipRect(dirtyStrokeRect);
                strokeCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                strokeCanvas.drawPath(fullStrokePath, currentPaint);
                strokeCanvas.restore();
            } else {
                strokeCanvas.drawPath(segPath, currentPaint);
            }
            if (isFinalUp) {
                paintCanvas.drawBitmap(strokeBitmap, 0, 0, strokeCompositePaint);
                strokeBitmap.eraseColor(Color.TRANSPARENT);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (paintBitmap == null) return false;
            float x = event.getX(); float y = event.getY();
            if (isZoomMode) {
                scaleDetector.onTouchEvent(event);
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: lastTouch.set(x, y); activePointerId = event.getPointerId(0); break;
                    case MotionEvent.ACTION_MOVE: int pi = event.findPointerIndex(activePointerId); if (pi != -1) { float currX = event.getX(pi), currY = event.getY(pi); if (!scaleDetector.isInProgress()) { viewPanX += currX - lastTouch.x; viewPanY += currY - lastTouch.y; } lastTouch.set(currX, currY); invalidate(); } break;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: activePointerId = MotionEvent.INVALID_POINTER_ID; performClick(); break;
                }
                return true;
            }
            float scale = Math.min((float) getWidth() / paintBitmap.getWidth(), (float) getHeight() / paintBitmap.getHeight()) * viewZoom;
            float dx = (getWidth() - paintBitmap.getWidth() * scale) / 2f + viewPanX; float dy = (getHeight() - paintBitmap.getHeight() * scale) / 2f + viewPanY;
            float imgX = (x - dx) / scale; float imgY = (y - dy) / scale;
            screenTouchX = x; screenTouchY = y;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downScreenX = x; downScreenY = y;
                    if (brushType == 10) return true;
                    isLongPressFired = false; showPickerIndicator = false; downImgX = imgX; downImgY = imgY;
                    postDelayed(longPressRunnable, 500);
                    if (brushType == 8) {
                        if (smudgeSampleBitmap == null) {
                            try { smudgeSampleBitmap = Bitmap.createBitmap(baseBitmap.getWidth(), baseBitmap.getHeight(), Bitmap.Config.ARGB_8888); smudgeSampleCanvas = new Canvas(smudgeSampleBitmap); }
                            catch (OutOfMemoryError e) { Toast.makeText(getContext(), "Not enough memory for Smudge tool", Toast.LENGTH_SHORT).show(); return true; }
                        }
                        smudgeSampleBitmap.eraseColor(Color.TRANSPARENT);
                        if (baseBitmap != null) smudgeSampleCanvas.drawBitmap(baseBitmap, 0, 0, null);
                        smudgeSampleCanvas.drawBitmap(paintBitmap, 0, 0, null);
                        smudgeShader = new BitmapShader(smudgeSampleBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                        smudgeStampPaint.reset(); smudgeStampPaint.setAntiAlias(true); smudgeStampPaint.setShader(smudgeShader);
                        float smudgeSoft = Math.max(0f, Math.min(1f, (100f - brushHardness) / 100f));
                        smudgeStampPaint.setAlpha((int) (brushOpacity * 0.9f));
                        smudgeStampPaint.setMaskFilter(new BlurMaskFilter(Math.min(32f, Math.max(1.0f, brushSize * (0.12f + 0.55f * smudgeSoft))), BlurMaskFilter.Blur.NORMAL));
                    }
                    applyBrushProfileToPaint(currentPaint, brushType, paintBrushSubStyle, shadingMode, brushSize, brushOpacity, brushColor, brushHardness, false);
                    configureStrokeCompositePaint();
                    circPhase = strokeRng.nextFloat() * (float) (Math.PI * 2.0);
                    circSpin = strokeRng.nextBoolean() ? 1f : -1f;
                    hatchStepCarry = strokeRng.nextFloat() * 3f;
                    randomizeShaderMatrixForDirection(1f, 0f);
                    if (strokeBitmap != null) strokeBitmap.eraseColor(Color.TRANSPARENT);
                    float initPad = brushSize * 2.2f + 70f;
                    dirtyStrokeRect.set(imgX - initPad, imgY - initPad, imgX + initPad, imgY + initPad);
                    lastX = imgX; lastY = imgY; isDrawingStroke = true;
                    fullStrokePath.reset(); fullStrokePath.moveTo(imgX, imgY);
                    if (shadingMode == 2 && brushType != 8 && brushType != 9 && brushType != 10) {
                        float loopR = Math.max(4f, brushSize * 0.65f);
                        lastMidX = imgX + loopR * (float) Math.cos(circPhase);
                        lastMidY = imgY + loopR * (float) Math.sin(circPhase);
                    } else {
                        lastMidX = imgX; lastMidY = imgY;
                        if (shadingMode <= 1 && brushType != 8 && brushType != 9 && brushType != 10 && strokeCanvas != null) {
                            segPath.reset(); segPath.moveTo(imgX, imgY); segPath.lineTo(imgX + 0.1f, imgY + 0.1f);
                            fullStrokePath.lineTo(imgX + 0.1f, imgY + 0.1f);
                            renderCurrentStrokeToBuffer(false);
                        }
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (brushType == 10) return true;
                    if (!isLongPressFired && Math.hypot(imgX - downImgX, imgY - downImgY) > (15f / scale)) { removeCallbacks(longPressRunnable); }
                    if (isLongPressFired) { pickColorAtTouch(imgX, imgY); return true; }
                    segPath.reset(); segPath.moveTo(lastMidX, lastMidY);
                    boolean hasSegments = false;
                    int historySize = event.getHistorySize();
                    for (int h = 0; h < historySize; h++) {
                        float hx = (event.getHistoricalX(h) - dx) / scale; float hy = (event.getHistoricalY(h) - dy) / scale;
                        if (appendSegmentToPath(hx, hy, scale)) hasSegments = true;
                    }
                    if (appendSegmentToPath(imgX, imgY, scale)) hasSegments = true;
                    if (hasSegments) { renderCurrentStrokeToBuffer(false); }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    performClick();
                    if (brushType == 10) { float moved = (float) Math.hypot(x - downScreenX, event.getY() - downScreenY); if (moved < 25f) { executeFloodFill((int) imgX, (int) imgY); } return true; }
                    removeCallbacks(longPressRunnable); showPickerIndicator = false;
                    if (isLongPressFired) { isLongPressFired = false; isDrawingStroke = false; invalidate(); return true; }
                    if (brushType != 8 && brushType != 10) {
                        segPath.reset(); segPath.moveTo(lastMidX, lastMidY);
                        expandDirtyStrokeRect(imgX, imgY);
                        if (shadingMode >= 2 && brushType != 9) {
                            appendSegmentToPath(imgX, imgY, scale);
                        } else {
                            segPath.lineTo(imgX, imgY);
                            fullStrokePath.lineTo(imgX, imgY);
                        }
                        renderCurrentStrokeToBuffer(true);
                    }
                    isDrawingStroke = false; saveUndoState();
                    break;
            }
            invalidate(); return true;
        }

        @Override public boolean performClick() { return super.performClick(); }
        public void setBrushSize(float size) { this.brushSize = size; }
        public void setBrushOpacity(int alpha) { this.brushOpacity = alpha; }
        public void setBrushColor(int color) { this.brushColor = color; }
        public int getBrushColor() { return this.brushColor; }
        public void setBrushHardness(float hardness) { this.brushHardness = hardness; }
        public float getBrushHardness() { return this.brushHardness; }
        public void setShadingMode(int mode) { this.shadingMode = mode; }
        public void setBrushType(int type) { this.brushType = type; if (getContext() instanceof AdvancedCanvasActivity) { this.paintBrushSubStyle = ((AdvancedCanvasActivity) getContext()).paintBrushSubStyle; } }
        private void saveUndoState() { try { if (rasterUndoStack.size() >= 5) { Bitmap old = rasterUndoStack.remove(0); if (old != null && !old.isRecycled()) old.recycle(); } rasterUndoStack.add(Bitmap.createBitmap(paintBitmap)); for (Bitmap b : rasterRedoStack) { if (b != null && !b.isRecycled()) b.recycle(); } rasterRedoStack.clear(); } catch (OutOfMemoryError e) { if (!rasterUndoStack.isEmpty()) rasterUndoStack.remove(0); } }
        public void undo() { if (rasterUndoStack.size() > 1) { rasterRedoStack.add(rasterUndoStack.remove(rasterUndoStack.size() - 1)); Bitmap prev = rasterUndoStack.get(rasterUndoStack.size() - 1); paintBitmap = prev.copy(Bitmap.Config.ARGB_8888, true); paintCanvas = new Canvas(paintBitmap); invalidate(); } }
        public void redo() { if (!rasterRedoStack.isEmpty()) { Bitmap next = rasterRedoStack.remove(rasterRedoStack.size() - 1); rasterUndoStack.add(next); paintBitmap = next.copy(Bitmap.Config.ARGB_8888, true); paintCanvas = new Canvas(paintBitmap); invalidate(); } }
        public Bitmap getFinalBitmap() { Bitmap result = Bitmap.createBitmap(paintBitmap.getWidth(), paintBitmap.getHeight(), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(result); if (baseBitmap != null) c.drawBitmap(baseBitmap, 0, 0, null); c.drawBitmap(paintBitmap, 0, 0, null); return result; }
        public Bitmap getOptimizedSaveBitmap() { if (baseBitmap != null && paintBitmap != null) { Canvas c = new Canvas(baseBitmap); c.drawBitmap(paintBitmap, 0, 0, null); return baseBitmap; } return paintBitmap != null ? paintBitmap : baseBitmap; }
    }
}