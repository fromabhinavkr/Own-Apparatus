package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("all")
@SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
public class BackgroundRemoverUI {
    private final ImageEditorActivity activity;
    private final ImageEditorActivity.PhotoEditorView editorView;
    private final boolean isDarkTheme;
    private final int panelColor;
    private final int textColor;

    private View bgRootView;
    private LinearLayout leftPanel;
    private LinearLayout rightPanel;
    private Button btnEraser, btnRepair, btnAuto, btnZoom, btnUndo, btnRedo;
    private Button btnTBgChecker, btnTBgWhite, btnTBgBlack, btnTBgGreen, btnTBgBlue, btnTBgBrown;
    private boolean isZoomActive = false;
    private boolean isCustomAutoActive = false;
    private BgModernSlider modernSizeSlider, modernSmoothSlider;

    /* Fast LUTs for perceptual CIELAB conversion */
    private static final float[] SRGB_TO_LIN = new float[256];
    private static final float[] LAB_F_LUT = new float[1025];

    static {
        for (int i = 0; i < 256; i++) {
            float v = i / 255.0f;
            SRGB_TO_LIN[i] = (v <= 0.04045f) ? (v / 12.92f) : (float) Math.pow((v + 0.055f) / 1.055f, 2.4);
        }
        for (int i = 0; i <= 1024; i++) {
            float t = i / 1024.0f;
            LAB_F_LUT[i] = (t > 0.008856f) ? (float) Math.cbrt(t) : (7.787f * t + 16.0f / 116.0f);
        }
    }

    private static class HistoryState {
        final byte[] rleMaskAlpha;
        final byte[] rleRawAlpha;
        final float smoothLevel;

        HistoryState(byte[] rleMaskAlpha, byte[] rleRawAlpha, float smoothLevel) {
            this.rleMaskAlpha = rleMaskAlpha;
            this.rleRawAlpha = rleRawAlpha;
            this.smoothLevel = smoothLevel;
        }
    }

    private final List<HistoryState> undoStack = new ArrayList<>();
    private final List<HistoryState> redoStack = new ArrayList<>();
    private static final int MAX_HISTORY = 30;

    private HistoryState pendingManualState = null;
    private boolean isManualStrokeInProgress = false;
    private HistoryState pendingSmoothState = null;
    private boolean smoothDragCommitted = false;

    public BackgroundRemoverUI(ImageEditorActivity activity, ImageEditorActivity.PhotoEditorView editorView, boolean isDarkTheme, int panelColor, int textColor) {
        this.activity = activity;
        this.editorView = editorView;
        this.isDarkTheme = isDarkTheme;
        this.panelColor = panelColor;
        this.textColor = textColor;
    }

    public void showDialog() {
        if (editorView.isImageMissing() || editorView.isCropping) return;
        editorView.deselectLayer();
        setViewVisibility(R.id.topBar, View.GONE);
        setViewVisibility(R.id.bottomMenuBar, View.GONE);
        setViewVisibility(R.id.cropToolsBar, View.GONE);
        activity.hideLeftPanel();
        activity.hideRightPanel();

        ViewGroup root = activity.findViewById(R.id.editorRoot);
        LayoutInflater inflater = LayoutInflater.from(activity);
        bgRootView = inflater.inflate(R.layout.layout_bg_remover_fullscreen, root, false);

        undoStack.clear();
        redoStack.clear();
        pendingManualState = null;
        pendingSmoothState = null;
        isManualStrokeInProgress = false;
        smoothDragCommitted = false;
        isCustomAutoActive = false;

        setupTheming();
        setupInteractions();
        setupTouchAndHistoryInterceptor();

        root.addView(bgRootView);
        editorView.startBgEraser((int) editorView.drawingManager.currentBrushWidth, false);
        syncRawMaskFromCurrent();
        updateToolHighlight(btnEraser);
        updateHistoryButtons();
    }

    private void setViewVisibility(int id, int visibility) {
        View v = activity.findViewById(id);
        if (v != null) v.setVisibility(visibility);
    }

    private void close() {
        isZoomActive = false;
        isCustomAutoActive = false;
        editorView.setOnTouchListener(null);
        try {
            editorView.tBgColor = 0;
            editorView.customBgRemoverBgColor = 0;
            editorView.isBgZoomMode = false;
        } catch (Exception ignored) {}
        ViewGroup root = activity.findViewById(R.id.editorRoot);
        root.removeView(bgRootView);
        editorView.drawingManager.isDrawMode = false;
        editorView.isBgRemoverMode = false;
        editorView.isAutoColorRemovalMode = false;
        editorView.invalidate();
        setViewVisibility(R.id.topBar, View.VISIBLE);
        setViewVisibility(R.id.bottomMenuBar, View.VISIBLE);
        activity.updateToolButtons();
    }

    /* ================= RLE-COMPRESSED UNIFIED UNDO / REDO ================= */

    private static byte[] encodeAlphaRle(int[] pixels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(256, pixels.length / 16));
        int n = pixels.length;
        int i = 0;
        while (i < n) {
            int val = (pixels[i] >>> 24) & 0xFF;
            int run = 1;
            while (i + run < n && run < 255 && (((pixels[i + run] >>> 24) & 0xFF) == val)) {
                run++;
            }
            out.write(run);
            out.write(val);
            i += run;
        }
        return out.toByteArray();
    }

    private static int[] decodeAlphaRle(byte[] rle, int expectedLen) {
        int[] pixels = new int[expectedLen];
        int outIdx = 0;
        for (int i = 0; i + 1 < rle.length && outIdx < expectedLen; i += 2) {
            int run = rle[i] & 0xFF;
            int argb = (rle[i + 1] & 0xFF) << 24;
            int end = Math.min(expectedLen, outIdx + run);
            Arrays.fill(pixels, outIdx, end, argb);
            outIdx = end;
        }
        return pixels;
    }

    private HistoryState captureCurrentState() {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null) return null;
        int w = mask.getWidth(), h = mask.getHeight();
        int[] maskPx = new int[w * h];
        mask.getPixels(maskPx, 0, w, 0, 0, w, h);
        byte[] rleMask = encodeAlphaRle(maskPx);

        byte[] rleRaw;
        if (editorView.currentSmoothLevel > 0.01f
                && editorView.rawEraseMask != null && !editorView.rawEraseMask.isRecycled()
                && editorView.rawEraseMask.getWidth() == w && editorView.rawEraseMask.getHeight() == h) {
            int[] rawPx = new int[w * h];
            editorView.rawEraseMask.getPixels(rawPx, 0, w, 0, 0, w, h);
            rleRaw = encodeAlphaRle(rawPx);
        } else {
            rleRaw = rleMask;
        }
        return new HistoryState(rleMask, rleRaw, editorView.currentSmoothLevel);
    }

    private void pushHistoryState(HistoryState state) {
        if (state == null) return;
        undoStack.add(state);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
        redoStack.clear();
        updateHistoryButtons();
    }

    private void restoreHistoryState(HistoryState state) {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null || state == null) return;
        int w = mask.getWidth(), h = mask.getHeight();
        int total = w * h;

        int[] restoredMask = decodeAlphaRle(state.rleMaskAlpha, total);
        mask.setPixels(restoredMask, 0, w, 0, 0, w, h);

        int[] restoredRaw = (state.rleRawAlpha == state.rleMaskAlpha)
                ? restoredMask
                : decodeAlphaRle(state.rleRawAlpha, total);

        if (editorView.rawEraseMask == null || editorView.rawEraseMask.isRecycled()
                || editorView.rawEraseMask.getWidth() != w || editorView.rawEraseMask.getHeight() != h) {
            Bitmap.Config cfg = mask.getConfig() != null ? mask.getConfig() : Bitmap.Config.ARGB_8888;
            editorView.rawEraseMask = Bitmap.createBitmap(w, h, cfg);
        }
        editorView.rawEraseMask.setPixels(restoredRaw, 0, w, 0, 0, w, h);

        editorView.currentSmoothLevel = state.smoothLevel;
        if (modernSmoothSlider != null) {
            modernSmoothSlider.setValue(state.smoothLevel);
        }
        editorView.invalidate();
        updateHistoryButtons();
    }

    private void syncRawMaskFromCurrent() {
        Bitmap currentMask = editorView.drawingManager.getEraseLayerBitmap();
        if (currentMask == null) return;
        int w = currentMask.getWidth(), h = currentMask.getHeight();
        Bitmap.Config cfg = currentMask.getConfig() != null ? currentMask.getConfig() : Bitmap.Config.ARGB_8888;
        if (editorView.rawEraseMask == null || editorView.rawEraseMask.isRecycled()
                || editorView.rawEraseMask.getWidth() != w || editorView.rawEraseMask.getHeight() != h) {
            editorView.rawEraseMask = currentMask.copy(cfg, true);
        } else {
            int[] px = new int[w * h];
            currentMask.getPixels(px, 0, w, 0, 0, w, h);
            editorView.rawEraseMask.setPixels(px, 0, w, 0, 0, w, h);
        }
    }

    private boolean hasMaskChanged(byte[] beforeRle) {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null || beforeRle == null) return false;
        int w = mask.getWidth(), h = mask.getHeight();
        int[] now = new int[w * h];
        mask.getPixels(now, 0, w, 0, 0, w, h);
        byte[] nowRle = encodeAlphaRle(now);
        return !Arrays.equals(beforeRle, nowRle);
    }

    private void updateHistoryButtons() {
        if (btnUndo != null) btnUndo.setAlpha(undoStack.isEmpty() ? 0.45f : 1.0f);
        if (btnRedo != null) btnRedo.setAlpha(redoStack.isEmpty() ? 0.45f : 1.0f);
    }

    private void performUndo() {
        if (!undoStack.isEmpty()) {
            HistoryState current = captureCurrentState();
            if (current != null) redoStack.add(current);
            HistoryState prev = undoStack.remove(undoStack.size() - 1);
            restoreHistoryState(prev);
        } else {
            Toast.makeText(activity, "Nothing to undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void performRedo() {
        if (!redoStack.isEmpty()) {
            HistoryState current = captureCurrentState();
            if (current != null) undoStack.add(current);
            HistoryState next = redoStack.remove(redoStack.size() - 1);
            restoreHistoryState(next);
        } else {
            Toast.makeText(activity, "Nothing to redo", Toast.LENGTH_SHORT).show();
        }
    }

    /* ================= THEMING & INTERACTIONS ================= */

    private void setupTheming() {
        TextView tvTitle = bgRootView.findViewById(R.id.tvBgTitle);
        tvTitle.setTextColor(textColor);
        TextView tvBrushLabel = bgRootView.findViewById(R.id.tvBrushSizeLabel);
        tvBrushLabel.setTextColor(textColor);
        TextView tvSmoothLabel = bgRootView.findViewById(R.id.tvSmoothLabel);
        tvSmoothLabel.setTextColor(textColor);
        TextView tvTBgLabel = bgRootView.findViewById(R.id.tvTBgLabel);
        if (tvTBgLabel != null) tvTBgLabel.setTextColor(textColor);

        leftPanel = bgRootView.findViewById(R.id.bgLeftPanel);
        GradientDrawable leftGd = new GradientDrawable();
        leftGd.setColor(panelColor);
        leftGd.setCornerRadii(new float[]{0, 0, 60f, 60f, 60f, 60f, 0, 0});
        leftPanel.setBackground(leftGd);

        rightPanel = bgRootView.findViewById(R.id.bgRightPanel);
        GradientDrawable rightGd = new GradientDrawable();
        rightGd.setColor(panelColor);
        rightGd.setCornerRadii(new float[]{60f, 60f, 0, 0, 0, 0, 60f, 60f});
        rightPanel.setBackground(rightGd);

        Button btnSettings = bgRootView.findViewById(R.id.btnBgSettings);
        Button btnTools = bgRootView.findViewById(R.id.btnBgTools);
        btnUndo = bgRootView.findViewById(R.id.btnBgUndo);
        btnRedo = bgRootView.findViewById(R.id.btnBgRedo);

        int capsuleColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#D1D1D6");
        int capsuleTextColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");

        GradientDrawable cap1 = new GradientDrawable();
        cap1.setColor(capsuleColor);
        cap1.setCornerRadius(100f);
        btnSettings.setBackgroundTintList(null);
        btnSettings.setBackground(cap1);
        btnSettings.setTextColor(capsuleTextColor);

        GradientDrawable cap2 = new GradientDrawable();
        cap2.setColor(capsuleColor);
        cap2.setCornerRadius(100f);
        btnTools.setBackgroundTintList(null);
        btnTools.setBackground(cap2);
        btnTools.setTextColor(capsuleTextColor);

        if (btnUndo != null) {
            GradientDrawable capU = new GradientDrawable();
            capU.setColor(capsuleColor);
            capU.setCornerRadius(100f);
            btnUndo.setBackgroundTintList(null);
            btnUndo.setBackground(capU);
            btnUndo.setTextColor(capsuleTextColor);
        }
        if (btnRedo != null) {
            GradientDrawable capR = new GradientDrawable();
            capR.setColor(capsuleColor);
            capR.setCornerRadius(100f);
            btnRedo.setBackgroundTintList(null);
            btnRedo.setBackground(capR);
            btnRedo.setTextColor(capsuleTextColor);
        }

        btnEraser = bgRootView.findViewById(R.id.btnBgEraser);
        btnRepair = bgRootView.findViewById(R.id.btnBgRepair);
        btnAuto = bgRootView.findViewById(R.id.btnBgAuto);
        btnZoom = bgRootView.findViewById(R.id.btnBgZoom);

        btnTBgChecker = bgRootView.findViewById(R.id.btnTBgChecker);
        btnTBgWhite = bgRootView.findViewById(R.id.btnTBgWhite);
        btnTBgBlack = bgRootView.findViewById(R.id.btnTBgBlack);
        btnTBgGreen = bgRootView.findViewById(R.id.btnTBgGreen);
        btnTBgBlue = bgRootView.findViewById(R.id.btnTBgBlue);
        btnTBgBrown = bgRootView.findViewById(R.id.btnTBgBrown);

        if (btnZoom != null) {
            btnZoom.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")));
            btnZoom.setTextColor(textColor);
        }
        if (btnTBgChecker != null) {
            btnTBgChecker.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#888888")));
            btnTBgChecker.setTextColor(Color.WHITE);
        }
        if (btnTBgWhite != null) {
            btnTBgWhite.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            btnTBgWhite.setTextColor(Color.BLACK);
        }
        if (btnTBgBlack != null) {
            btnTBgBlack.setBackgroundTintList(ColorStateList.valueOf(Color.BLACK));
            btnTBgBlack.setTextColor(Color.WHITE);
        }
        if (btnTBgGreen != null) {
            btnTBgGreen.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#008000")));
            btnTBgGreen.setTextColor(Color.WHITE);
        }
        if (btnTBgBlue != null) {
            btnTBgBlue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#0000FF")));
            btnTBgBlue.setTextColor(Color.WHITE);
        }
        if (btnTBgBrown != null) {
            btnTBgBrown.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#8B4513")));
            btnTBgBrown.setTextColor(Color.WHITE);
        }
    }

    private void setupInteractions() {
        Button btnDone = bgRootView.findViewById(R.id.btnBgDone);
        btnDone.setOnClickListener(v -> close());
        Button btnSettings = bgRootView.findViewById(R.id.btnBgSettings);
        Button btnTools = bgRootView.findViewById(R.id.btnBgTools);

        btnSettings.setOnClickListener(v -> togglePanel(leftPanel, true));
        btnTools.setOnClickListener(v -> togglePanel(rightPanel, false));

        int sliderHeight = (int) (46 * activity.getResources().getDisplayMetrics().density);
        int dp8 = (int) (8 * activity.getResources().getDisplayMetrics().density);
        int dp16 = (int) (16 * activity.getResources().getDisplayMetrics().density);

        View oldSizeSlider = bgRootView.findViewById(R.id.slBgBrushSize);
        modernSizeSlider = new BgModernSlider(activity, isDarkTheme, 1f, 150f, 1f);
        modernSizeSlider.setValue(editorView.drawingManager.currentBrushWidth > 0 ? editorView.drawingManager.currentBrushWidth : 50f);
        modernSizeSlider.setOnValueChangeListener(val -> editorView.drawingManager.currentBrushWidth = val);
        if (oldSizeSlider != null && oldSizeSlider.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) oldSizeSlider.getParent();
            int idx = parent.indexOfChild(oldSizeSlider);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeight);
            lp.setMargins(0, dp8, 0, dp16);
            parent.removeView(oldSizeSlider);
            parent.addView(modernSizeSlider, idx, lp);
        }

        View oldSmoothSlider = bgRootView.findViewById(R.id.slBgSmooth);
        modernSmoothSlider = new BgModernSlider(activity, isDarkTheme, 0f, 10f, 0.1f);
        modernSmoothSlider.setValue(editorView.currentSmoothLevel);
        modernSmoothSlider.setOnStartTrackingListener(() -> {
            pendingSmoothState = captureCurrentState();
            smoothDragCommitted = false;
        });
        modernSmoothSlider.setOnValueChangeListener(val -> {
            Bitmap currentMask = editorView.drawingManager.getEraseLayerBitmap();
            if (currentMask != null) {
                if (!smoothDragCommitted && pendingSmoothState != null) {
                    pushHistoryState(pendingSmoothState);
                    smoothDragCommitted = true;
                }
                if (editorView.rawEraseMask == null || editorView.rawEraseMask.isRecycled()) {
                    syncRawMaskFromCurrent();
                }
                editorView.currentSmoothLevel = val;
                applySmoothToMaskInPlace(currentMask, editorView.rawEraseMask, val);
                editorView.invalidate();
            }
        });
        if (oldSmoothSlider != null && oldSmoothSlider.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) oldSmoothSlider.getParent();
            int idx = parent.indexOfChild(oldSmoothSlider);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, sliderHeight);
            lp.setMargins(0, dp8, 0, dp16);
            parent.removeView(oldSmoothSlider);
            parent.addView(modernSmoothSlider, idx, lp);
        }

        if (btnUndo != null) btnUndo.setOnClickListener(v -> performUndo());
        if (btnRedo != null) btnRedo.setOnClickListener(v -> performRedo());

        btnEraser.setOnClickListener(v -> {
            isCustomAutoActive = false;
            disableZoomIfActive();
            editorView.startBgEraser((int) editorView.drawingManager.currentBrushWidth, false);
            updateToolHighlight(btnEraser);
        });
        btnRepair.setOnClickListener(v -> {
            isCustomAutoActive = false;
            disableZoomIfActive();
            editorView.startBgEraser((int) editorView.drawingManager.currentBrushWidth, true);
            updateToolHighlight(btnRepair);
        });
        btnAuto.setOnClickListener(v -> {
            disableZoomIfActive();
            editorView.enterAutoColorRemovalMode();
            editorView.isAutoColorRemovalMode = false;
            isCustomAutoActive = true;
            editorView.isBgRemoverMode = true;
            Toast.makeText(activity, "Tap a color to remove.", Toast.LENGTH_SHORT).show();
            updateToolHighlight(btnAuto);
        });

        if (btnZoom != null) btnZoom.setOnClickListener(v -> {
            isZoomActive = !isZoomActive;
            editorView.drawingManager.isDrawMode = !isZoomActive && !isCustomAutoActive;
            try {
                editorView.isBgZoomMode = isZoomActive;
            } catch (Exception ignored) {}
            if (isZoomActive) {
                btnZoom.setText("Zoom: ON");
                btnZoom.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#34C759")));
                btnZoom.setTextColor(Color.WHITE);
            } else {
                btnZoom.setText("Zoom: OFF");
                btnZoom.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")));
                btnZoom.setTextColor(textColor);
                if (!isCustomAutoActive) editorView.drawingManager.isDrawMode = true;
            }
            editorView.invalidate();
        });

        if (btnTBgChecker != null) btnTBgChecker.setOnClickListener(v -> setTBgColor(0, btnTBgChecker));
        if (btnTBgWhite != null) btnTBgWhite.setOnClickListener(v -> setTBgColor(Color.WHITE, btnTBgWhite));
        if (btnTBgBlack != null) btnTBgBlack.setOnClickListener(v -> setTBgColor(Color.BLACK, btnTBgBlack));
        if (btnTBgGreen != null) btnTBgGreen.setOnClickListener(v -> setTBgColor(Color.parseColor("#008000"), btnTBgGreen));
        if (btnTBgBlue != null) btnTBgBlue.setOnClickListener(v -> setTBgColor(Color.parseColor("#0000FF"), btnTBgBlue));
        if (btnTBgBrown != null) btnTBgBrown.setOnClickListener(v -> setTBgColor(Color.parseColor("#8B4513"), btnTBgBrown));
    }

    private void setupTouchAndHistoryInterceptor() {
        editorView.setOnTouchListener((v, event) -> {
            if (isZoomActive || editorView.baseImage == null) return false;

            int action = event.getActionMasked();

            if (isCustomAutoActive) {
                if (action == MotionEvent.ACTION_DOWN) {
                    try {
                        java.lang.reflect.Field fRect = editorView.getClass().getDeclaredField("destRect");
                        fRect.setAccessible(true);
                        RectF destRect = (RectF) fRect.get(editorView);
                        float tx = event.getX(), ty = event.getY();
                        if (destRect != null && destRect.contains(tx, ty)) {
                            Bitmap base = editorView.baseImage;
                            int bmpX = (int) ((tx - destRect.left) * (base.getWidth() / destRect.width()));
                            int bmpY = (int) ((ty - destRect.top) * (base.getHeight() / destRect.height()));
                            boolean removed = removeColorAdvanced(bmpX, bmpY);
                            if (removed) {
                                Toast.makeText(activity, "Color removed!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return true;
            }

            /* Unified tracking for Manual Eraser & Repair strokes */
            if (!editorView.drawingManager.isDrawMode) return false;

            if (action == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1) {
                pendingManualState = captureCurrentState();
                isManualStrokeInProgress = true;
            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                isManualStrokeInProgress = false;
                pendingManualState = null;
            } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && isManualStrokeInProgress) {
                isManualStrokeInProgress = false;
                final HistoryState savedBefore = pendingManualState;
                pendingManualState = null;
                v.post(() -> {
                    if (savedBefore != null && hasMaskChanged(savedBefore.rleMaskAlpha)) {
                        pushHistoryState(savedBefore);
                        editorView.currentSmoothLevel = 0f;
                        if (modernSmoothSlider != null) modernSmoothSlider.setValue(0f);
                        syncRawMaskFromCurrent();
                    }
                });
            }
            return false;
        });
    }

    /* ================= ADVANCED PERCEPTUAL AUTO COLOR REMOVER ================= */

    private static void rgbToLab(int r, int g, int b, float[] outLab) {
        float rl = SRGB_TO_LIN[r];
        float gl = SRGB_TO_LIN[g];
        float bl = SRGB_TO_LIN[b];

        float x = (0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl) / 0.95047f;
        float y = (0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl);
        float z = (0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl) / 1.08883f;

        int ix = Math.max(0, Math.min(1024, (int) (x * 1024.0f)));
        int iy = Math.max(0, Math.min(1024, (int) (y * 1024.0f)));
        int iz = Math.max(0, Math.min(1024, (int) (z * 1024.0f)));

        float fx = LAB_F_LUT[ix];
        float fy = LAB_F_LUT[iy];
        float fz = LAB_F_LUT[iz];

        outLab[0] = 116.0f * fy - 16.0f;
        outLab[1] = 500.0f * (fx - fy);
        outLab[2] = 200.0f * (fy - fz);
    }

    private boolean removeColorAdvanced(int tapX, int tapY) {
        Bitmap base = editorView.baseImage;
        Bitmap eraseMask = editorView.drawingManager.getEraseLayerBitmap();
        if (base == null || eraseMask == null) return false;

        int w = base.getWidth(), h = base.getHeight();
        int total = w * h;
        tapX = Math.max(0, Math.min(tapX, w - 1));
        tapY = Math.max(0, Math.min(tapY, h - 1));

        int[] basePixels = new int[total];
        int[] maskPixels = new int[total];
        base.getPixels(basePixels, 0, w, 0, 0, w, h);
        eraseMask.getPixels(maskPixels, 0, w, 0, 0, w, h);

        int tapIdx = tapY * w + tapX;
        if (((basePixels[tapIdx] >>> 24) & 0xFF) < 10 || ((maskPixels[tapIdx] >>> 24) & 0xFF) >= 220) {
            Toast.makeText(activity, "Area is already transparent", Toast.LENGTH_SHORT).show();
            return false;
        }

        HistoryState preAutoState = captureCurrentState();

        /* 1. Outlier-Rejected 5x5 Seed Sampling */
        int c0 = basePixels[tapIdx];
        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        long sR = 0, sG = 0, sB = 0, cnt = 0;
        for (int dy = -2; dy <= 2; dy++) {
            int sy = tapY + dy;
            if (sy < 0 || sy >= h) continue;
            int row = sy * w;
            for (int dx = -2; dx <= 2; dx++) {
                int sx = tapX + dx;
                if (sx < 0 || sx >= w) continue;
                int idx = row + sx;
                int c = basePixels[idx];
                if (((c >>> 24) & 0xFF) > 20 && ((maskPixels[idx] >>> 24) & 0xFF) < 200) {
                    int rr = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, bb = c & 0xFF;
                    int dr = rr - r0, dg = gg - g0, db = bb - b0;
                    if (dr * dr + dg * dg + db * db <= 324) { /* within RGB dist 18 of tap center */
                        sR += rr;
                        sG += gg;
                        sB += bb;
                        cnt++;
                    }
                }
            }
        }
        int rT = (cnt > 0) ? (int) (sR / cnt) : r0;
        int gT = (cnt > 0) ? (int) (sG / cnt) : g0;
        int bT = (cnt > 0) ? (int) (sB / cnt) : b0;

        /* Target color in Perceptual CIELAB */
        float[] labTmp = new float[3];
        rgbToLab(rT, gT, bT, labTmp);
        float lT = labTmp[0], aT = labTmp[1], bLabT = labTmp[2];
        float chromaT = (float) Math.sqrt(aT * aT + bLabT * bLabT);
        boolean isChromaticBg = (chromaT >= 12.0f);
        float uAT = isChromaticBg ? (aT / chromaT) : 0f;
        float uBT = isChromaticBg ? (bLabT / chromaT) : 0f;

        /* Precompute CIELAB and RGB distance to target for all pixels */
        float[] lArr = new float[total];
        float[] aArr = new float[total];
        float[] bArr = new float[total];
        float[] chromaArr = new float[total];
        float[] rgbDistArr = new float[total];
        float[] labDistArr = new float[total];
        float[] hueCosArr = new float[total];

        for (int i = 0; i < total; i++) {
            int c = basePixels[i];
            if (((c >>> 24) & 0xFF) < 10) {
                rgbDistArr[i] = 999f;
                labDistArr[i] = 999f;
                continue;
            }
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int dr = r - rT, dg = g - gT, db = b - bT;
            rgbDistArr[i] = (float) Math.sqrt(dr * dr + dg * dg + db * db);

            rgbToLab(r, g, b, labTmp);
            float l = labTmp[0], a = labTmp[1], bl = labTmp[2];
            lArr[i] = l;
            aArr[i] = a;
            bArr[i] = bl;
            float ch = (float) Math.sqrt(a * a + bl * bl);
            chromaArr[i] = ch;

            float dL = l - lT, dA = a - aT, dB = bl - bLabT;
            if (isChromaticBg) {
                /* Weight hue/chroma strictly, allow moderate lightness variation (shadows/highlights on backdrop) */
                labDistArr[i] = (float) Math.sqrt(0.45f * dL * dL + 1.25f * dA * dA + 1.25f * dB * dB);
                hueCosArr[i] = (ch > 2.5f) ? ((a * uAT + bl * uBT) / ch) : 0f;
            } else {
                /* Achromatic (Black/White/Grey): strictly enforce both lightness and neutral chroma */
                labDistArr[i] = (float) Math.sqrt(1.1f * dL * dL + 1.4f * dA * dA + 1.4f * dB * dB);
                hueCosArr[i] = 0f;
            }
        }

        /* 2. Pass 1A: Edge-Aware Contiguous Region Growing (BFS from tap point) */
        boolean[] isCoreBg = new boolean[total];
        boolean[] visited = new boolean[total];
        int[] queue = new int[total];
        int qHead = 0, qTail = 0;

        isCoreBg[tapIdx] = true;
        visited[tapIdx] = true;
        queue[qTail++] = tapIdx;

        int[] nx4 = {-1, 1, 0, 0};
        int[] ny4 = {0, 0, -1, 1};

        float maxAchromaticChroma = Math.max(10.5f, chromaT + 7.0f);
        float minChromaticChroma = Math.max(9.0f, chromaT * 0.34f);

        while (qHead < qTail) {
            int curr = queue[qHead++];
            int cx = curr % w;
            int cy = curr / w;
            float cL = lArr[curr], cA = aArr[curr], cB = bArr[curr];

            for (int k = 0; k < 4; k++) {
                int x = cx + nx4[k];
                int y = cy + ny4[k];
                if (x < 0 || x >= w || y < 0 || y >= h) continue;
                int ni = y * w + x;
                if (visited[ni]) continue;

                if (((basePixels[ni] >>> 24) & 0xFF) < 10) {
                    visited[ni] = true;
                    continue;
                }

                float sDL = lArr[ni] - cL, sDA = aArr[ni] - cA, sDB = bArr[ni] - cB;
                float stepDeltaE = (float) Math.sqrt(sDL * sDL + sDA * sDA + sDB * sDB);

                boolean accept;
                if (isChromaticBg) {
                    boolean hueOk = hueCosArr[ni] >= 0.89f && chromaArr[ni] >= minChromaticChroma;
                    accept = hueOk && (
                            (labDistArr[ni] <= 24.0f && stepDeltaE <= 18.0f)
                                    || (labDistArr[ni] <= 36.0f && hueCosArr[ni] >= 0.92f && stepDeltaE <= 6.5f)
                    );
                } else {
                    boolean neutralOk = chromaArr[ni] <= maxAchromaticChroma;
                    accept = neutralOk && (
                            (labDistArr[ni] <= 15.0f && rgbDistArr[ni] <= 30.0f && stepDeltaE <= 14.0f)
                                    || (labDistArr[ni] <= 22.0f && rgbDistArr[ni] <= 40.0f && stepDeltaE <= 5.0f)
                    );
                }

                if (accept) {
                    visited[ni] = true;
                    isCoreBg[ni] = true;
                    queue[qTail++] = ni;
                }
            }
        }

        /* 3. Pass 1B: Strict Global Exact-Pocket Match (cleans disconnected holes of identical background color) */
        float strictNeutralChroma = Math.max(8.5f, chromaT + 5.5f);
        float strictChromaticChroma = Math.max(11.0f, chromaT * 0.44f);
        for (int i = 0; i < total; i++) {
            if (isCoreBg[i] || ((basePixels[i] >>> 24) & 0xFF) < 10) continue;
            if (isChromaticBg) {
                if (hueCosArr[i] >= 0.93f && chromaArr[i] >= strictChromaticChroma && labDistArr[i] <= 17.5f) {
                    isCoreBg[i] = true;
                }
            } else {
                if (chromaArr[i] <= strictNeutralChroma && labDistArr[i] <= 10.5f && rgbDistArr[i] <= 22.0f) {
                    isCoreBg[i] = true;
                }
            }
        }

        /* Mark Core Background in mask & initialize 3-4 Chamfer Euclidean Distance Field */
        int[] chamferDist = new int[total];
        final int INF = 100000;
        for (int i = 0; i < total; i++) {
            if (isCoreBg[i]) {
                maskPixels[i] = 0xFF000000;
                chamferDist[i] = 0;
            } else {
                chamferDist[i] = INF;
            }
        }

        /* 4. Pass 2: Accurate 3-4 Chamfer Euclidean Distance Transform */
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int idx = row + x;
                int d = chamferDist[idx];
                if (d == 0) continue;
                if (x > 0 && chamferDist[idx - 1] + 3 < d) d = chamferDist[idx - 1] + 3;
                if (y > 0 && chamferDist[idx - w] + 3 < d) d = chamferDist[idx - w] + 3;
                if (x > 0 && y > 0 && chamferDist[idx - w - 1] + 4 < d) d = chamferDist[idx - w - 1] + 4;
                if (x < w - 1 && y > 0 && chamferDist[idx - w + 1] + 4 < d) d = chamferDist[idx - w + 1] + 4;
                chamferDist[idx] = d;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            int row = y * w;
            for (int x = w - 1; x >= 0; x--) {
                int idx = row + x;
                int d = chamferDist[idx];
                if (d == 0) continue;
                if (x < w - 1 && chamferDist[idx + 1] + 3 < d) d = chamferDist[idx + 1] + 3;
                if (y < h - 1 && chamferDist[idx + w] + 3 < d) d = chamferDist[idx + w] + 3;
                if (x < w - 1 && y < h - 1 && chamferDist[idx + w + 1] + 4 < d) d = chamferDist[idx + w + 1] + 4;
                if (x > 0 && y < h - 1 && chamferDist[idx + w - 1] + 4 < d) d = chamferDist[idx + w - 1] + 4;
                chamferDist[idx] = d;
            }
        }

        /* 5. Pass 3: Inward-Normal Guided Sub-Pixel Color Unmixing & Smooth Alpha Matting */
        final float cos28 = 0.8829476f, sin28 = 0.4694716f;
        final float[] raySteps = {1.6f, 2.5f, 3.5f, 4.5f};

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int idx = row + x;
                int cd = chamferDist[idx];
                /* Strict narrow anti-aliasing band: 0 < distPx <= 2.67px (never eats deep into foreground) */
                if (cd < 1 || cd > 8) continue;

                int c = basePixels[idx];
                if (((c >>> 24) & 0xFF) < 10) continue;
                if (((maskPixels[idx] >>> 24) & 0xFF) >= 255) continue;

                float distPx = cd / 3.0f;
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                float pRgbDist = rgbDistArr[idx];

                /* Compute inward normal pointing strictly from background into foreground interior */
                int dL = (x > 0) ? chamferDist[idx - 1] : cd;
                int dR = (x < w - 1) ? chamferDist[idx + 1] : cd;
                int dU = (y > 0) ? chamferDist[idx - w] : cd;
                int dD = (y < h - 1) ? chamferDist[idx + w] : cd;
                float gx = dR - dL;
                float gy = dD - dU;
                float gLen = (float) Math.sqrt(gx * gx + gy * gy);

                float bestResErr = 9999f;
                float bestBeta = 0f;
                float bestTfLen = 0f;

                if (gLen > 0.01f) {
                    float nx = gx / gLen;
                    float ny = gy / gLen;

                    /* 3 rays: exact inward normal + fanned +/-28 deg for curved edges & corners */
                    float[] rdx = {nx, nx * cos28 - ny * sin28, nx * cos28 + ny * sin28};
                    float[] rdy = {ny, nx * sin28 + ny * cos28, -nx * sin28 + ny * cos28};

                    for (int ray = 0; ray < 3; ray++) {
                        for (int s = 0; s < raySteps.length; s++) {
                            int fx = Math.round(x + rdx[ray] * raySteps[s]);
                            int fy = Math.round(y + rdy[ray] * raySteps[s]);
                            if (fx < 0 || fx >= w || fy < 0 || fy >= h) continue;
                            int fIdx = fy * w + fx;

                            /* Candidate foreground pixel must be strictly deeper inside & clean */
                            int minRequiredDist = (s == 0) ? (cd + 2) : (cd + 3);
                            if (chamferDist[fIdx] < minRequiredDist || chamferDist[fIdx] >= INF) continue;
                            if (((maskPixels[fIdx] >>> 24) & 0xFF) >= 30) continue;
                            if (((basePixels[fIdx] >>> 24) & 0xFF) < 200) continue;
                            if (rgbDistArr[fIdx] <= pRgbDist + 10.0f) continue;

                            int fCol = basePixels[fIdx];
                            int rF = (fCol >> 16) & 0xFF, gF = (fCol >> 8) & 0xFF, bF = fCol & 0xFF;
                            float tfR = rT - rF, tfG = gT - gF, tfB = bT - bF;
                            float tfLenSq = tfR * tfR + tfG * tfG + tfB * tfB;
                            if (tfLenSq < 484.0f) continue; /* require contrast >= 22 RGB */

                            float dfR = r - rF, dfG = g - gF, dfB = b - bF;
                            /* Exact linear compositing coverage beta: P = (1 - beta)*F + beta*B */
                            float beta = (dfR * tfR + dfG * tfG + dfB * tfB) / tfLenSq;
                            if (beta >= 0.06f && beta <= 1.08f) {
                                float clampedB = Math.min(1.0f, beta);
                                float errR = dfR - clampedB * tfR;
                                float errG = dfG - clampedB * tfG;
                                float errB = dfB - clampedB * tfB;
                                float resErr = (float) Math.sqrt(errR * errR + errG * errG + errB * errB);
                                if (resErr < bestResErr) {
                                    bestResErr = resErr;
                                    bestBeta = clampedB;
                                    bestTfLen = (float) Math.sqrt(tfLenSq);
                                }
                            }
                        }
                    }
                }

                int eraseAlpha = 0;

                if (bestTfLen > 0f) {
                    boolean isOuterRim = (distPx <= 1.45f);
                    float maxAllowedErr = isOuterRim
                            ? Math.max(15.0f, bestTfLen * 0.15f)
                            : Math.max(10.0f, bestTfLen * 0.10f);

                    if (bestResErr <= maxAllowedErr) {
                        /* Inner band guard: never chew subtle foreground shading */
                        boolean passBandGuard = isOuterRim || (bestBeta >= 0.24f && pRgbDist < bestTfLen * 0.82f);
                        if (passBandGuard) {
                            float betaLow = isOuterRim ? 0.12f : 0.24f;
                            float betaHigh = isOuterRim ? 0.52f : 0.64f;
                            if (bestBeta >= betaHigh) {
                                eraseAlpha = 255;
                            } else if (bestBeta > betaLow) {
                                float t = (bestBeta - betaLow) / (betaHigh - betaLow);
                                float smooth = t * t * (3.0f - 2.0f * t);
                                eraseAlpha = Math.round(255.0f * smooth);
                            }
                        }
                    }
                }

                /* Tight 1px rim fallback for JPEG ringing directly adjacent to core background */
                if (eraseAlpha < 255 && distPx <= 1.40f) {
                    if (isChromaticBg) {
                        if (hueCosArr[idx] >= 0.88f && chromaArr[idx] >= Math.max(9.0f, chromaT * 0.28f) && labDistArr[idx] <= 29.0f) {
                            float t = Math.max(0f, Math.min(1f, (29.0f - labDistArr[idx]) / 12.0f));
                            eraseAlpha = Math.max(eraseAlpha, Math.round(255.0f * t));
                        }
                    } else {
                        if (chromaArr[idx] <= Math.max(13.0f, chromaT + 8.5f) && labDistArr[idx] <= 21.0f && pRgbDist <= 42.0f) {
                            float t = Math.max(0f, Math.min(1f, (21.0f - labDistArr[idx]) / 9.0f));
                            eraseAlpha = Math.max(eraseAlpha, Math.round(255.0f * t));
                        }
                    }
                }

                if (eraseAlpha > 0) {
                    int oldA = (maskPixels[idx] >>> 24) & 0xFF;
                    maskPixels[idx] = (Math.max(oldA, Math.min(255, eraseAlpha)) << 24);
                }
            }
        }

        /* 6. Pass 4: Safe 3-Neighbor Orphan-Fringe Cleanup (erases stray 1px boundary dots, NEVER bites corners) */
        int[] pass3Copy = maskPixels.clone();
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int curA = (pass3Copy[idx] >>> 24) & 0xFF;
                if (curA >= 235 || chamferDist[idx] > 5) continue;

                int erased4 = 0;
                if (((pass3Copy[idx - 1] >>> 24) & 0xFF) >= 230) erased4++;
                if (((pass3Copy[idx + 1] >>> 24) & 0xFF) >= 230) erased4++;
                if (((pass3Copy[idx - w] >>> 24) & 0xFF) >= 230) erased4++;
                if (((pass3Copy[idx + w] >>> 24) & 0xFF) >= 230) erased4++;

                if (erased4 == 4) {
                    /* Single 1px isolated speckle completely surrounded by transparent background */
                    maskPixels[idx] = 0xFF000000;
                } else if (erased4 == 3) {
                    /* Protruding 1px fringe dot along a flat or curved edge */
                    boolean looksLikeFringe = curA >= 90
                            || (isChromaticBg ? (hueCosArr[idx] >= 0.80f && labDistArr[idx] <= 34.0f)
                            : (rgbDistArr[idx] <= 55.0f || labDistArr[idx] <= 26.0f));
                    if (looksLikeFringe) {
                        maskPixels[idx] = 0xFF000000;
                    }
                }
            }
        }

        /* Only commit & push to Undo stack if pixels actually changed */
        byte[] afterRle = encodeAlphaRle(maskPixels);
        if (preAutoState != null && Arrays.equals(preAutoState.rleMaskAlpha, afterRle)) {
            return false;
        }

        if (preAutoState != null) {
            pushHistoryState(preAutoState);
        }

        eraseMask.setPixels(maskPixels, 0, w, 0, 0, w, h);
        editorView.currentSmoothLevel = 0f;
        syncRawMaskFromCurrent();
        if (modernSmoothSlider != null) modernSmoothSlider.setValue(0f);
        editorView.invalidate();
        return true;
    }

    /* ================= UI HELPERS & SMOOTHING ================= */

    private void disableZoomIfActive() {
        if (isZoomActive) {
            isZoomActive = false;
            editorView.drawingManager.isDrawMode = true;
            try {
                editorView.isBgZoomMode = false;
            } catch (Exception ignored) {}
            if (btnZoom != null) {
                btnZoom.setText("Zoom: OFF");
                btnZoom.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")));
                btnZoom.setTextColor(textColor);
            }
        }
    }

    private void setTBgColor(int colorCode, Button activeBtn) {
        try {
            editorView.tBgColor = colorCode;
            editorView.customBgRemoverBgColor = colorCode;
        } catch (Exception ignored) {}
        editorView.invalidate();
        Button[] tbgBtns = {btnTBgChecker, btnTBgWhite, btnTBgBlack, btnTBgGreen, btnTBgBlue, btnTBgBrown};
        for (Button b : tbgBtns) {
            if (b == null) continue;
            if (b == activeBtn) {
                b.setAlpha(1.0f);
                b.setScaleX(1.06f);
                b.setScaleY(1.06f);
            } else {
                b.setAlpha(0.55f);
                b.setScaleX(1.0f);
                b.setScaleY(1.0f);
            }
        }
    }

    private void togglePanel(View panel, boolean isLeft) {
        if (panel.getVisibility() == View.VISIBLE) {
            float targetX = isLeft ? -panel.getWidth() : panel.getWidth();
            panel.animate().translationX(targetX).alpha(0f).setDuration(250).setInterpolator(new DecelerateInterpolator(2f)).withEndAction(() -> panel.setVisibility(View.GONE)).start();
        } else {
            panel.setVisibility(View.VISIBLE);
            float startX = isLeft ? -800f : 800f;
            if (panel.getWidth() > 0) startX = isLeft ? -panel.getWidth() : panel.getWidth();
            panel.setTranslationX(startX);
            panel.setAlpha(0f);
            panel.animate().translationX(0).alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private void updateToolHighlight(Button activeBtn) {
        Button[] btns = {btnEraser, btnRepair, btnAuto};
        for (Button b : btns) {
            if (b == activeBtn) {
                b.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4A90E2")));
                b.setTextColor(Color.WHITE);
            } else {
                b.setBackgroundTintList(ColorStateList.valueOf(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")));
                b.setTextColor(textColor);
            }
        }
    }

    private void applySmoothToMaskInPlace(Bitmap mask, Bitmap originalBackup, float level) {
        if (mask == null || originalBackup == null) return;
        int width = mask.getWidth();
        int height = mask.getHeight();
        if (level <= 0.01f) {
            int[] pixels = new int[width * height];
            originalBackup.getPixels(pixels, 0, width, 0, 0, width, height);
            mask.setPixels(pixels, 0, width, 0, 0, width, height);
            return;
        }
        float dilateRadius = Math.max(0.1f, level * 3f);
        float smoothRadius = Math.max(0.1f, level * 2f);
        Bitmap alpha = originalBackup.extractAlpha();
        Bitmap temp1 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c1 = new Canvas(temp1);
        Paint blurPaint = new Paint();
        blurPaint.setColor(Color.BLACK);
        blurPaint.setMaskFilter(new android.graphics.BlurMaskFilter(dilateRadius, android.graphics.BlurMaskFilter.Blur.SOLID));
        c1.drawBitmap(alpha, 0, 0, blurPaint);
        alpha.recycle();

        Bitmap temp2 = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c2 = new Canvas(temp2);
        Paint thresholdPaint = new Paint();
        ColorMatrix cm = new ColorMatrix(new float[]{
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 50f, -1000f
        });
        thresholdPaint.setColorFilter(new ColorMatrixColorFilter(cm));
        c2.drawBitmap(temp1, 0, 0, thresholdPaint);
        temp1.recycle();

        Bitmap dilatedAlpha = temp2.extractAlpha();
        temp2.recycle();

        Bitmap finalTemp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c3 = new Canvas(finalTemp);
        Paint finalSmoothPaint = new Paint();
        finalSmoothPaint.setColor(Color.BLACK);
        finalSmoothPaint.setMaskFilter(new android.graphics.BlurMaskFilter(smoothRadius, android.graphics.BlurMaskFilter.Blur.NORMAL));
        c3.drawBitmap(dilatedAlpha, 0, 0, finalSmoothPaint);
        dilatedAlpha.recycle();

        Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        c3.drawBitmap(originalBackup, 0, 0, overPaint);

        int[] tempPixels = new int[width * height];
        finalTemp.getPixels(tempPixels, 0, width, 0, 0, width, height);
        mask.setPixels(tempPixels, 0, width, 0, 0, width, height);
        finalTemp.recycle();
    }

    public static class BgModernSlider extends View {
        public interface OnValueChangeListener { void onValueChanged(float value); }
        public interface OnStartTrackingListener { void onStartTracking(); }

        private float progress = 0f;
        private final float min, max, step;
        private final int trackColor, progressColor, coveredTextColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private OnValueChangeListener listener;
        private OnStartTrackingListener startTrackingListener;

        public BgModernSlider(Context context, boolean isDarkTheme, float min, float max, float step) {
            super(context);
            this.min = min;
            this.max = max;
            this.step = step;
            trackColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000");
            progressColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333");
            coveredTextColor = isDarkTheme ? Color.BLACK : Color.WHITE;
        }

        public void setOnValueChangeListener(OnValueChangeListener l) { this.listener = l; }
        public void setOnStartTrackingListener(OnStartTrackingListener l) { this.startTrackingListener = l; }

        public void setValue(float val) {
            float clamped = Math.max(min, Math.min(val, max));
            progress = (max > min) ? (clamped - min) / (max - min) : 0f;
            invalidate();
        }

        public float getValue() {
            float raw = min + progress * (max - min);
            float stepped = Math.round(raw / step) * step;
            return Math.max(min, Math.min(max, Math.round(stepped * 10f) / 10f));
        }

        private String formatValue(float val) {
            if (step >= 1f) return String.valueOf(Math.round(val));
            return (Math.abs(val - Math.round(val)) < 0.01f) ? String.valueOf(Math.round(val)) : String.format(Locale.US, "%.1f", val);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            paint.setColor(trackColor);
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            paint.setTextSize(h * 0.40f);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            String valStr = formatValue(getValue());
            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = (h - fm.ascent - fm.descent) / 2f;
            float textX = w - h * 0.55f - paint.measureText(valStr);
            paint.setColor(progressColor);
            canvas.drawText(valStr, textX, textY, paint);

            float right = Math.max(h, h + (w - h) * progress);
            canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(0, 0, right, h, r, r, Path.Direction.CW);
            canvas.clipPath(clipPath);
            paint.setColor(progressColor);
            canvas.drawRoundRect(0, 0, right, h, r, r, paint);
            paint.setColor(coveredTextColor);
            canvas.drawText(valStr, textX, textY, paint);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (startTrackingListener != null) startTrackingListener.onStartTracking();
            }
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float oldVal = getValue();
                float h = getHeight(), w = getWidth();
                progress = (w > h) ? Math.max(0f, Math.min((event.getX() - h / 2f) / (w - h), 1f)) : 0f;
                invalidate();
                float newVal = getValue();
                if (Math.abs(newVal - oldVal) >= 0.05f && listener != null) listener.onValueChanged(newVal);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}