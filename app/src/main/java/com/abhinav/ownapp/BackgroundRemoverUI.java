package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.ArrayList;
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
    private Button btnEraser, btnRepair, btnAuto, btnZoom;
    private Button btnTBgChecker, btnTBgWhite, btnTBgBlack, btnTBgGreen, btnTBgBlue, btnTBgBrown;
    private boolean isZoomActive = false;
    private boolean isCustomAutoActive = false;
    private BgModernSlider modernSizeSlider, modernSmoothSlider;

    private static class HistoryState {
        final int[] maskPixels;
        final int[] rawMaskPixels;
        final float smoothLevel;

        HistoryState(int[] maskPixels, int[] rawMaskPixels, float smoothLevel) {
            this.maskPixels = maskPixels;
            this.rawMaskPixels = rawMaskPixels;
            this.smoothLevel = smoothLevel;
        }
    }

    private final List<HistoryState> undoStack = new ArrayList<>();
    private final List<HistoryState> redoStack = new ArrayList<>();
    private static final int MAX_HISTORY = 20;
    private HistoryState pendingManualState = null;
    private boolean isManualStrokeInProgress = false;

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
        isCustomAutoActive = false;

        setupTheming();
        setupInteractions();
        setupTouchAndHistoryInterceptor();

        root.addView(bgRootView);
        editorView.startBgEraser((int) editorView.drawingManager.currentBrushWidth, false);
        syncRawMaskFromCurrent();
        updateToolHighlight(btnEraser);
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

    private HistoryState captureCurrentState() {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null) return null;
        int w = mask.getWidth(), h = mask.getHeight();
        int[] maskPx = new int[w * h];
        mask.getPixels(maskPx, 0, w, 0, 0, w, h);

        int[] rawPx = null;
        if (editorView.rawEraseMask != null && !editorView.rawEraseMask.isRecycled()
                && editorView.rawEraseMask.getWidth() == w && editorView.rawEraseMask.getHeight() == h) {
            rawPx = new int[w * h];
            editorView.rawEraseMask.getPixels(rawPx, 0, w, 0, 0, w, h);
        } else {
            rawPx = maskPx.clone();
        }
        return new HistoryState(maskPx, rawPx, editorView.currentSmoothLevel);
    }

    private void pushHistoryState(HistoryState state) {
        if (state == null) return;
        undoStack.add(state);
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void restoreHistoryState(HistoryState state) {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null || state == null) return;
        int w = mask.getWidth(), h = mask.getHeight();
        if (state.maskPixels.length != w * h) return;

        mask.setPixels(state.maskPixels, 0, w, 0, 0, w, h);

        if (state.rawMaskPixels != null && state.rawMaskPixels.length == w * h) {
            if (editorView.rawEraseMask == null || editorView.rawEraseMask.isRecycled()
                    || editorView.rawEraseMask.getWidth() != w || editorView.rawEraseMask.getHeight() != h) {
                Bitmap.Config cfg = mask.getConfig() != null ? mask.getConfig() : Bitmap.Config.ARGB_8888;
                editorView.rawEraseMask = Bitmap.createBitmap(w, h, cfg);
            }
            editorView.rawEraseMask.setPixels(state.rawMaskPixels, 0, w, 0, 0, w, h);
        } else {
            syncRawMaskFromCurrent();
        }

        editorView.currentSmoothLevel = state.smoothLevel;
        if (modernSmoothSlider != null) {
            modernSmoothSlider.setValue(state.smoothLevel);
        }
        editorView.invalidate();
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

    private boolean hasMaskChanged(int[] before) {
        Bitmap mask = editorView.drawingManager.getEraseLayerBitmap();
        if (mask == null || before == null) return false;
        int w = mask.getWidth(), h = mask.getHeight();
        if (before.length != w * h) return false;
        int[] now = new int[w * h];
        mask.getPixels(now, 0, w, 0, 0, w, h);
        for (int i = 0; i < now.length; i++) {
            if (now[i] != before[i]) return true;
        }
        return false;
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
        Button btnUndo = bgRootView.findViewById(R.id.btnBgUndo);
        Button btnRedo = bgRootView.findViewById(R.id.btnBgRedo);

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
        Button btnUndo = bgRootView.findViewById(R.id.btnBgUndo);
        Button btnRedo = bgRootView.findViewById(R.id.btnBgRedo);

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
            HistoryState preSmooth = captureCurrentState();
            if (preSmooth != null) pushHistoryState(preSmooth);
        });
        modernSmoothSlider.setOnValueChangeListener(val -> {
            Bitmap currentMask = editorView.drawingManager.getEraseLayerBitmap();
            if (currentMask != null) {
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
                            removeColorAndEliminateHalo(bmpX, bmpY);
                            Toast.makeText(activity, "Color removed!", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception ignored) {}
                }
                return true;
            }

            /* Intercept Manual Eraser & Repair strokes so Undo/Redo tracks them in the exact same stack */
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
                    if (savedBefore != null && hasMaskChanged(savedBefore.maskPixels)) {
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

    private void removeColorAndEliminateHalo(int tapX, int tapY) {
        Bitmap base = editorView.baseImage;
        Bitmap eraseMask = editorView.drawingManager.getEraseLayerBitmap();
        if (base == null || eraseMask == null) return;

        int w = base.getWidth(), h = base.getHeight();
        tapX = Math.max(0, Math.min(tapX, w - 1));
        tapY = Math.max(0, Math.min(tapY, h - 1));

        HistoryState preAutoState = captureCurrentState();

        int[] basePixels = new int[w * h];
        int[] maskPixels = new int[w * h];
        base.getPixels(basePixels, 0, w, 0, 0, w, h);
        eraseMask.getPixels(maskPixels, 0, w, 0, 0, w, h);

        /* 1. Sample 5x5 patch around tap point for accurate target background color */
        long sR = 0, sG = 0, sB = 0, cnt = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                int sx = Math.max(0, Math.min(tapX + dx, w - 1));
                int sy = Math.max(0, Math.min(tapY + dy, h - 1));
                int idx = sy * w + sx;
                int c = basePixels[idx];
                if ((c >>> 24) > 10 && ((maskPixels[idx] >>> 24) & 0xFF) < 200) {
                    sR += (c >> 16) & 0xFF;
                    sG += (c >> 8) & 0xFF;
                    sB += c & 0xFF;
                    cnt++;
                }
            }
        }
        if (cnt == 0) {
            int c = basePixels[tapY * w + tapX];
            sR = (c >> 16) & 0xFF;
            sG = (c >> 8) & 0xFF;
            sB = c & 0xFF;
            cnt = 1;
        }
        int rT = (int) (sR / cnt), gT = (int) (sG / cnt), bT = (int) (sB / cnt);

        float meanT = (rT + gT + bT) / 3f;
        float cRT = rT - meanT, cGT = gT - meanT, cBT = bT - meanT;
        float chromaMagT = (float) Math.sqrt(cRT * cRT + cGT * cGT + cBT * cBT);
        float uTr = (chromaMagT > 1e-3f) ? (cRT / chromaMagT) : 0f;
        float uTg = (chromaMagT > 1e-3f) ? (cGT / chromaMagT) : 0f;
        float uTb = (chromaMagT > 1e-3f) ? (cBT / chromaMagT) : 0f;

        int maxT = Math.max(rT, Math.max(gT, bT)), minT = Math.min(rT, Math.min(gT, bT)), deltaT = maxT - minT;
        float satT = (maxT == 0) ? 0f : (deltaT / (float) maxT);
        boolean isChromaticBg = (deltaT >= 18 && satT >= 0.18f && maxT >= 35);
        boolean isBlueDominantBg = isChromaticBg && (bT >= rT && bT >= gT);
        int domChannelT = (rT >= gT && rT >= bT) ? 0 : ((gT >= rT && gT >= bT) ? 1 : 2);

        int minCoreBright = isChromaticBg ? Math.max(44, (int) (maxT * 0.42f)) : Math.max(0, maxT - 36);
        int maxCoreBright = isChromaticBg ? Math.min(255, maxT + Math.max(65, (int) (maxT * 0.50f))) : Math.min(255, maxT + 36);
        int minCoreDelta = isChromaticBg ? Math.max(22, (int) (deltaT * 0.36f)) : 0;
        float minCoreSat = isChromaticBg ? Math.max(0.22f, satT * 0.42f) : 0f;

        int[] distToBg = new int[w * h];
        float[] projTArr = new float[w * h];
        float[] cosSimArr = new float[w * h];
        float[] rgbDistArr = new float[w * h];

        /* 2. Pass 1: Strict Core Background Detection */
        for (int i = 0; i < w * h; i++) {
            int c = basePixels[i];
            if ((c >>> 24) == 0) {
                distToBg[i] = 9999;
                continue;
            }
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            int maxC = Math.max(r, Math.max(g, b)), minC = Math.min(r, Math.min(g, b)), deltaC = maxC - minC;
            float satC = (maxC == 0) ? 0f : (deltaC / (float) maxC);
            int dr = r - rT, dg = g - gT, db = b - bT;
            float rawEuclid = (float) Math.sqrt(dr * dr + dg * dg + db * db);
            float euclidDist = rawEuclid * 0.58f;
            rgbDistArr[i] = euclidDist;

            float meanC = (r + g + b) / 3f;
            float cR = r - meanC, cG = g - meanC, cB = b - meanC;
            float cMag = (float) Math.sqrt(cR * cR + cG * cG + cB * cB);
            float projT = cR * uTr + cG * uTg + cB * uTb;
            float cosSim = (isChromaticBg && cMag > 2.0f) ? (projT / cMag) : 0f;
            projTArr[i] = projT;
            cosSimArr[i] = cosSim;

            boolean isCoreMatch;
            if (isChromaticBg) {
                int domC = (r >= g && r >= b) ? 0 : ((g >= r && g >= b) ? 1 : 2);
                boolean sameDom = (domC == domChannelT) || (cosSim >= 0.93f);
                boolean passGuard = sameDom && (maxC >= minCoreBright && maxC <= maxCoreBright && deltaC >= minCoreDelta && satC >= minCoreSat);
                isCoreMatch = passGuard && ((euclidDist <= 40f && cosSim >= 0.86f) || (cosSim >= 0.91f && euclidDist <= 88f));
            } else {
                /* Achromatic (Black, White, Grey) Core Match */
                isCoreMatch = (deltaC <= Math.max(22, deltaT + 12)
                        && maxC >= minCoreBright && maxC <= maxCoreBright
                        && euclidDist <= 28f);
            }
            if (isCoreMatch) {
                maskPixels[i] = 0xFF000000;
                distToBg[i] = 0;
            } else {
                distToBg[i] = 9999;
            }
        }

        /* 3. Pass 2: Fast Distance Transform from Core Background */
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int idx = row + x;
                int d = distToBg[idx];
                if (d == 0) continue;
                if (x > 0 && distToBg[idx - 1] + 1 < d) d = distToBg[idx - 1] + 1;
                if (y > 0 && distToBg[idx - w] + 1 < d) d = distToBg[idx - w] + 1;
                if (x > 0 && y > 0 && distToBg[idx - w - 1] + 1 < d) d = distToBg[idx - w - 1] + 1;
                if (x < w - 1 && y > 0 && distToBg[idx - w + 1] + 1 < d) d = distToBg[idx - w + 1] + 1;
                distToBg[idx] = d;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            int row = y * w;
            for (int x = w - 1; x >= 0; x--) {
                int idx = row + x;
                int d = distToBg[idx];
                if (d == 0) continue;
                if (x < w - 1 && distToBg[idx + 1] + 1 < d) d = distToBg[idx + 1] + 1;
                if (y < h - 1 && distToBg[idx + w] + 1 < d) d = distToBg[idx + w] + 1;
                if (x < w - 1 && y < h - 1 && distToBg[idx + w + 1] + 1 < d) d = distToBg[idx + w + 1] + 1;
                if (x > 0 && y < h - 1 && distToBg[idx + w - 1] + 1 < d) d = distToBg[idx + w - 1] + 1;
                distToBg[idx] = d;
            }
        }

        /* 4. Pass 3: Precision Sub-Pixel Anti-Aliased Boundary Eradication (Chromatic & Achromatic) */
        int[] dirX = {-3, 3, 0, 0, -3, 3, -3, 3, -2, 2, 0, 0};
        int[] dirY = {0, 0, -3, 3, -3, -3, 3, 3, 0, 0, -2, 2};
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int i = row + x;
                int d = distToBg[i];
                if (d < 1 || d > 6) continue;

                int c = basePixels[i];
                int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                int maxC = Math.max(r, Math.max(g, b)), minC = Math.min(r, Math.min(g, b)), deltaC = maxC - minC;
                float satC = (maxC == 0) ? 0f : (deltaC / (float) maxC);
                float projT = projTArr[i], cosSim = cosSimArr[i], rDist = rgbDistArr[i];
                int eraseAlpha = 0;

                if (isChromaticBg) {
                    int domC = (r >= g && r >= b) ? 0 : ((g >= r && g >= b) ? 1 : 2);
                    boolean sameDom = (domC == domChannelT);

                    if (minC >= 70 && maxC >= 100) {
                        if (sameDom && cosSim >= 0.78f && projT >= 2.0f) {
                            float factor = (7f - d) / 6f;
                            eraseAlpha = (d <= 2 || projT >= 3.5f) ? 255 : Math.max(120, Math.round(255f * factor));
                        }
                    } else {
                        boolean passDarkBlackLock = isBlueDominantBg
                                ? (maxC >= Math.max(50, (int) (maxT * 0.35f)) && deltaC >= Math.max(18, (int) (deltaT * 0.22f)) && satC >= 0.24f && projT >= Math.max(12f, chromaMagT * 0.18f))
                                : (maxC >= 22 && deltaC >= 12 && satC >= 0.18f && projT >= 7.0f);
                        if (d <= 3 && sameDom && passDarkBlackLock && cosSim >= 0.80f) {
                            float factor = (4f - d) / 3f;
                            eraseAlpha = (d <= 1 || cosSim >= 0.88f) ? 255 : Math.max(130, Math.round(255f * factor));
                        }
                    }

                    if (eraseAlpha < 255 && d <= 3 && cosSim >= 0.60f && projT >= 5.0f && (!isBlueDominantBg || maxC >= 50)) {
                        for (int k = 0; k < dirX.length; k++) {
                            int nx = x + dirX[k], ny = y + dirY[k];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int nIdx = ny * w + nx;
                                int nd = distToBg[nIdx];
                                if (nd >= d + 1 && nd < 9000 && projTArr[nIdx] < projT * 0.5f) {
                                    int fCol = basePixels[nIdx];
                                    int rF = (fCol >> 16) & 0xFF, gF = (fCol >> 8) & 0xFF, bF = fCol & 0xFF;
                                    float dfR = r - rF, dfG = g - gF, dfB = b - bF;
                                    float distCF = (float) Math.sqrt(dfR * dfR + dfG * dfG + dfB * dfB);
                                    if (distCF <= 85f) {
                                        float tfR = rT - rF, tfG = gT - gF, tfB = bT - bF;
                                        float tfLenSq = tfR * tfR + tfG * tfG + tfB * tfB;
                                        if (tfLenSq > 300f) {
                                            float beta = (dfR * tfR + dfG * tfG + dfB * tfB) / tfLenSq;
                                            if (beta >= 0.18f && beta <= 0.98f) {
                                                float errR = dfR - beta * tfR, errG = dfG - beta * tfG, errB = dfB - beta * tfB;
                                                float resErr = (float) Math.sqrt(errR * errR + errG * errG + errB * errB);
                                                if (resErr <= 15.0f) {
                                                    eraseAlpha = (beta >= 0.38f || d <= 1) ? 255 : Math.max(140, Math.round(255f * (beta / 0.38f)));
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    /* Achromatic Boundary Eradication: Eliminates dark/white/grey anti-aliased halos around colored shapes */
                    if (deltaC <= Math.max(24, deltaT + 14) && maxC >= minCoreBright - 10 && maxC <= maxCoreBright + 10 && d <= 3 && rDist < 42f) {
                        eraseAlpha = 255;
                    } else if (d <= 4) {
                        for (int k = 0; k < dirX.length; k++) {
                            int nx = x + dirX[k], ny = y + dirY[k];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int nIdx = ny * w + nx;
                                int nd = distToBg[nIdx];
                                if (nd >= d + 1 && nd < 9000 && rgbDistArr[nIdx] > rDist + 12f) {
                                    int fCol = basePixels[nIdx];
                                    int rF = (fCol >> 16) & 0xFF, gF = (fCol >> 8) & 0xFF, bF = fCol & 0xFF;
                                    float tfR = rT - rF, tfG = gT - gF, tfB = bT - bF;
                                    float tfLenSq = tfR * tfR + tfG * tfG + tfB * tfB;
                                    if (tfLenSq > 250f) {
                                        float dfR = r - rF, dfG = g - gF, dfB = b - bF;
                                        /* beta = fraction of target background (e.g. black) mixed into foreground (e.g. green) */
                                        float beta = (dfR * tfR + dfG * tfG + dfB * tfB) / tfLenSq;
                                        if (beta >= 0.14f && beta <= 1.05f) {
                                            float errR = dfR - beta * tfR, errG = dfG - beta * tfG, errB = dfB - beta * tfB;
                                            float resErr = (float) Math.sqrt(errR * errR + errG * errG + errB * errB);
                                            if (resErr <= 22.0f) {
                                                int candidateAlpha;
                                                if (beta >= 0.30f || (d <= 2 && beta >= 0.20f)) {
                                                    candidateAlpha = 255;
                                                } else {
                                                    candidateAlpha = Math.max(160, Math.round(255f * (beta / 0.30f)));
                                                }
                                                if (candidateAlpha > eraseAlpha) eraseAlpha = candidateAlpha;
                                                if (eraseAlpha == 255) break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (eraseAlpha > 0) {
                    int oldA = (maskPixels[i] >>> 24) & 0xFF;
                    maskPixels[i] = (Math.max(oldA, Math.min(255, eraseAlpha)) << 24);
                }
            }
        }

        /* 5. Pass 4: Safe 1px Speckle & Corner Fringe Cleanup */
        int[] copyMask = maskPixels.clone();
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                if (((copyMask[idx] >>> 24) & 0xFF) < 220 && distToBg[idx] <= 3) {
                    int erasedNeighbors = 0;
                    if (((copyMask[idx - 1] >>> 24) & 0xFF) > 200) erasedNeighbors++;
                    if (((copyMask[idx + 1] >>> 24) & 0xFF) > 200) erasedNeighbors++;
                    if (((copyMask[idx - w] >>> 24) & 0xFF) > 200) erasedNeighbors++;
                    if (((copyMask[idx + w] >>> 24) & 0xFF) > 200) erasedNeighbors++;
                    boolean matchesTargetHue = isChromaticBg
                            ? (cosSimArr[idx] >= 0.75f && projTArr[idx] >= 3.0f)
                            : (rgbDistArr[idx] < 52f);
                    if (erasedNeighbors >= 2 && matchesTargetHue) {
                        maskPixels[idx] = 0xFF000000;
                    }
                }
            }
        }

        if (preAutoState != null) {
            pushHistoryState(preAutoState);
        }

        eraseMask.setPixels(maskPixels, 0, w, 0, 0, w, h);
        editorView.currentSmoothLevel = 0f;
        syncRawMaskFromCurrent();
        if (modernSmoothSlider != null) modernSmoothSlider.setValue(0f);
        editorView.invalidate();
    }

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