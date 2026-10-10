package com.abhinav.ownapp;

import android.annotation.SuppressLint; import android.app.AlertDialog; import android.content.Context; import android.content.res.ColorStateList; import android.graphics.Bitmap; import android.graphics.BlurMaskFilter; import android.graphics.Canvas; import android.graphics.Color; import android.graphics.LinearGradient; import android.graphics.Paint; import android.graphics.PorterDuff; import android.graphics.PorterDuffXfermode; import android.graphics.RectF; import android.graphics.Shader; import android.graphics.Typeface; import android.graphics.drawable.ColorDrawable; import android.graphics.drawable.GradientDrawable; import android.net.Uri; import android.os.Handler; import android.os.Looper; import android.text.Editable; import android.text.InputType; import android.text.Layout; import android.text.StaticLayout; import android.text.TextPaint; import android.text.TextWatcher; import android.view.Gravity; import android.view.MotionEvent; import android.view.View; import android.view.ViewGroup; import android.view.Window; import android.view.WindowManager; import android.widget.Button; import android.widget.EditText; import android.widget.FrameLayout; import android.widget.LinearLayout; import android.widget.ScrollView; import android.widget.TextView; import android.widget.Toast;
import androidx.annotation.NonNull;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;

@SuppressWarnings("all") @SuppressLint("SetTextI18n")
public class LayerSettingsUI {

    private final ImageEditorActivity activity;
    private final ImageEditorActivity.PhotoEditorView editorView;
    private final boolean isDarkTheme;
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    public Typeface currentCustomFont = null;
    public TextView activeFontLabel = null;
    public Runnable dialogUpdateRunnable = null;

    public LayerSettingsUI(ImageEditorActivity activity, ImageEditorActivity.PhotoEditorView editorView, boolean isDarkTheme) { this.activity = activity; this.editorView = editorView; this.isDarkTheme = isDarkTheme; }

    private int dpToPx(int dp) { return (int) (dp * activity.getResources().getDisplayMetrics().density); }

    public void loadCustomFont(Uri uri) {
        try {
            InputStream is = activity.getContentResolver().openInputStream(uri);
            if (is != null) {
                File tempFontFile = new File(activity.getCacheDir(), "temp_font_" + System.currentTimeMillis() + ".ttf");
                FileOutputStream fos = new FileOutputStream(tempFontFile);
                byte[] buffer = new byte[1024]; int length;
                while ((length = is.read(buffer)) > 0) fos.write(buffer, 0, length);
                fos.close(); is.close();
                currentCustomFont = Typeface.createFromFile(tempFontFile);
                if (activeFontLabel != null) activeFontLabel.setText("Custom Font Loaded!");
            }
        } catch (Exception e) { Toast.makeText(activity, "Error loading font", Toast.LENGTH_SHORT).show(); }
    }

    private Button createToggleButton(String text, boolean active) {
        Button b = new Button(activity); b.setText(text); b.setTextSize(12f); b.setPadding(0, 0, 0, 0); b.setAllCaps(false); b.setElevation(0f); b.setStateListAnimator(null); b.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        int activeColor = isDarkTheme ? Color.parseColor("#4A4A4C") : Color.parseColor("#D1D1D6");
        int inactiveColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5EA");
        GradientDrawable gd = new GradientDrawable(); gd.setColor(active ? activeColor : inactiveColor); gd.setCornerRadius(100f); b.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(44), 1f); lp.setMargins(dpToPx(4), 0, dpToPx(4), 0); b.setLayoutParams(lp);
        return b;
    }

    private void styleModernButton(Button b, int bgColor, int txtColor) {
        b.setTextColor(txtColor); b.setAllCaps(false); b.setElevation(0f); b.setStateListAnimator(null);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bgColor); gd.setCornerRadius(100f); b.setBackground(gd);
    }

    private View createSliderWithLabel(String label, float min, float max, float current, SettingsSlider.OnChangeListener listener) {
        LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, dpToPx(8), 0, dpToPx(8));
        TextView tv = new TextView(activity); tv.setText(label); tv.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); tv.setTextSize(12f); tv.setTypeface(null, Typeface.BOLD); tv.setPadding(dpToPx(4), 0, 0, dpToPx(8));
        SettingsSlider slider = new SettingsSlider(activity, isDarkTheme); slider.setRange(min, max); slider.setValue(current); slider.listener = listener;
        LinearLayout.LayoutParams sliderLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44)); slider.setLayoutParams(sliderLp);
        row.addView(tv); row.addView(slider); return row;
    }

    public void showTextAddDialog(GraphicLayer layerToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.ModernDialogStyle);
        LinearLayout mainLayout = new LinearLayout(activity); mainLayout.setOrientation(LinearLayout.VERTICAL); mainLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#F2FFFFFF")); gd.setCornerRadii(new float[]{60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f}); mainLayout.setBackground(gd);

        TextView title = new TextView(activity); title.setText(layerToEdit == null ? "Add Text" : "Edit Text"); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, dpToPx(16)); mainLayout.addView(title);

        FrameLayout previewContainer = new FrameLayout(activity); previewContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(100))); previewContainer.setBackgroundColor(Color.parseColor(isDarkTheme ? "#66000000" : "#66FFFFFF"));
        TextPreviewView previewView = new TextPreviewView(activity); previewContainer.addView(previewView); mainLayout.addView(previewContainer);

        EditText etInput = new EditText(activity); etInput.setHint("Type your text here..."); etInput.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); etInput.setHintTextColor(Color.GRAY); etInput.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16)); etInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); etInput.setSingleLine(false); etInput.setMaxLines(6);
        GradientDrawable etBg = new GradientDrawable(); etBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")); etBg.setCornerRadius(30f); etInput.setBackground(etBg);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); etLp.setMargins(0, dpToPx(16), 0, dpToPx(16)); etInput.setLayoutParams(etLp);
        if (layerToEdit != null) { etInput.setText(layerToEdit.text); currentCustomFont = layerToEdit.typeface; } else { currentCustomFont = null; }
        mainLayout.addView(etInput);

        final Layout.Alignment[] currentAlign = {layerToEdit != null ? layerToEdit.align : Layout.Alignment.ALIGN_CENTER};
        LinearLayout alignGroup = new LinearLayout(activity); alignGroup.setOrientation(LinearLayout.HORIZONTAL); alignGroup.setGravity(Gravity.CENTER); alignGroup.setPadding(0, 0, 0, dpToPx(16));
        Button btnAlignLeft = createToggleButton("Left", currentAlign[0] == Layout.Alignment.ALIGN_NORMAL); Button btnAlignCenter = createToggleButton("Center", currentAlign[0] == Layout.Alignment.ALIGN_CENTER); Button btnAlignRight = createToggleButton("Right", currentAlign[0] == Layout.Alignment.ALIGN_OPPOSITE);
        alignGroup.addView(btnAlignLeft); alignGroup.addView(btnAlignCenter); alignGroup.addView(btnAlignRight); mainLayout.addView(alignGroup);

        LinearLayout targetGroup = new LinearLayout(activity); targetGroup.setOrientation(LinearLayout.HORIZONTAL); targetGroup.setGravity(Gravity.CENTER); targetGroup.setPadding(0, dpToPx(8), 0, dpToPx(16));
        Button btnTargetMain = createToggleButton("Text", true); Button btnTargetStroke = createToggleButton("Stroke", false); Button btnTargetShadow = createToggleButton("Shadow", false); Button btnTargetInner = createToggleButton("Inner", false);
        targetGroup.addView(btnTargetMain); targetGroup.addView(btnTargetStroke); targetGroup.addView(btnTargetShadow); targetGroup.addView(btnTargetInner); mainLayout.addView(targetGroup);

        ColorPickerView colorPicker = new ColorPickerView(activity); LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(180)); colorPicker.setLayoutParams(wheelLp); mainLayout.addView(colorPicker);

        LinearLayout hexRow = new LinearLayout(activity); hexRow.setOrientation(LinearLayout.HORIZONTAL); hexRow.setGravity(Gravity.CENTER); hexRow.setPadding(0, dpToPx(16), 0, dpToPx(16));
        EditText etHex = new EditText(activity); etHex.setText("#FFFFFF"); etHex.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); etHex.setPadding(dpToPx(16), 0, dpToPx(16), 0); etHex.setBackground(etBg);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(0, dpToPx(44), 1f); etHex.setLayoutParams(hexLp);

        int profBtnColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"); int profTextColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        Button btnEyedropper = new Button(activity); btnEyedropper.setText("🖌️ Pick"); styleModernButton(btnEyedropper, profBtnColor, profTextColor);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(44)); pickLp.setMargins(dpToPx(12), 0, 0, 0); btnEyedropper.setLayoutParams(pickLp);
        hexRow.addView(etHex); hexRow.addView(btnEyedropper); mainLayout.addView(hexRow);

        LinearLayout pageTint = new LinearLayout(activity); pageTint.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageStroke = new LinearLayout(activity); pageStroke.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageShadow = new LinearLayout(activity); pageShadow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageInner = new LinearLayout(activity); pageInner.setOrientation(LinearLayout.VERTICAL);

        final float[] stateLetter = {layerToEdit != null ? layerToEdit.letterSpacing : 0f}; final float[] stateLine = {layerToEdit != null ? layerToEdit.lineSpacing : 0f}; final float[] stateStroke = {layerToEdit != null ? layerToEdit.strokeWidth : 0f}; final float[] stateShadow = {layerToEdit != null ? layerToEdit.shadowRadius : 0f}; final float[] stateInner = {layerToEdit != null ? layerToEdit.innerShadowRadius : 0f}; final float[] stateShadX = {layerToEdit != null ? layerToEdit.shadowOffsetX : 0f}; final float[] stateShadY = {layerToEdit != null ? layerToEdit.shadowOffsetY : 0f}; final float[] stateInnerX = {layerToEdit != null ? layerToEdit.innerShadowOffsetX : 0f}; final float[] stateInnerY = {layerToEdit != null ? layerToEdit.innerShadowOffsetY : 0f};
        final int[] textColors = {layerToEdit != null ? layerToEdit.color : Color.WHITE, layerToEdit != null ? layerToEdit.strokeColor : Color.BLACK, layerToEdit != null ? layerToEdit.shadowColor : Color.BLACK, layerToEdit != null ? layerToEdit.innerShadowColor : Color.BLACK};

        pageTint.addView(createSliderWithLabel("Letter Spacing", -0.5f, 1f, stateLetter[0], (float v, boolean u) -> { if (u) { stateLetter[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageTint.addView(createSliderWithLabel("Line Spacing", 0f, 100f, stateLine[0], (float v, boolean u) -> { if (u) { stateLine[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));

        Button btnPickFont = new Button(activity); btnPickFont.setText("Choose Custom Font (.ttf / .otf)"); styleModernButton(btnPickFont, profBtnColor, profTextColor);
        LinearLayout.LayoutParams fontLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)); fontLp.setMargins(0, dpToPx(8), 0, 0); btnPickFont.setLayoutParams(fontLp); pageTint.addView(btnPickFont);
        activeFontLabel = new TextView(activity); activeFontLabel.setText(currentCustomFont != null ? "Custom Font Loaded" : "Default Font Selected"); activeFontLabel.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); activeFontLabel.setGravity(Gravity.CENTER); activeFontLabel.setPadding(0, dpToPx(12), 0, dpToPx(12)); pageTint.addView(activeFontLabel);

        pageStroke.addView(createSliderWithLabel("Stroke Thickness", 0f, 50f, stateStroke[0], (float v, boolean u) -> { if (u) { stateStroke[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Blur", 0f, 50f, stateShadow[0], (float v, boolean u) -> { if (u) { stateShadow[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset X", -100f, 100f, stateShadX[0], (float v, boolean u) -> { if (u) { stateShadX[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset Y", -100f, 100f, stateShadY[0], (float v, boolean u) -> { if (u) { stateShadY[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Shadow Blur", 0f, 50f, stateInner[0], (float v, boolean u) -> { if (u) { stateInner[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Offset X", -100f, 100f, stateInnerX[0], (float v, boolean u) -> { if (u) { stateInnerX[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Offset Y", -100f, 100f, stateInnerY[0], (float v, boolean u) -> { if (u) { stateInnerY[0]=v; debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } }));

        mainLayout.addView(pageTint); mainLayout.addView(pageStroke); mainLayout.addView(pageShadow); mainLayout.addView(pageInner);
        pageStroke.setVisibility(View.GONE); pageShadow.setVisibility(View.GONE); pageInner.setVisibility(View.GONE);

        Button btnApply = new Button(activity); btnApply.setText(layerToEdit == null ? "Add to Image" : "Update Image"); styleModernButton(btnApply, Color.parseColor("#34C759"), Color.WHITE);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50)); applyLp.setMargins(0, dpToPx(16), 0, 0); btnApply.setLayoutParams(applyLp); mainLayout.addView(btnApply);

        ScrollView scrollWrapper = new ScrollView(activity); scrollWrapper.addView(mainLayout); builder.setView(scrollWrapper);
        final AlertDialog dialog = builder.create(); if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final int[] activeColorTarget = {0}; colorPicker.setColor(textColors[0]); etHex.setText(String.format("#%06X", (0xFFFFFF & textColors[0])));
        int activeColor = isDarkTheme ? Color.parseColor("#4A4A4C") : Color.parseColor("#D1D1D6"); int inactiveColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5EA");

        View.OnClickListener alignListener = aBtn -> {
            GradientDrawable ina = new GradientDrawable(); ina.setColor(inactiveColor); ina.setCornerRadius(100f);
            GradientDrawable act = new GradientDrawable(); act.setColor(activeColor); act.setCornerRadius(100f);
            btnAlignLeft.setBackground(ina); btnAlignCenter.setBackground(ina); btnAlignRight.setBackground(ina); aBtn.setBackground(act);
            if (aBtn == btnAlignLeft) currentAlign[0] = Layout.Alignment.ALIGN_NORMAL; else if (aBtn == btnAlignCenter) currentAlign[0] = Layout.Alignment.ALIGN_CENTER; else if (aBtn == btnAlignRight) currentAlign[0] = Layout.Alignment.ALIGN_OPPOSITE;
            debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50);
        };
        btnAlignLeft.setOnClickListener(alignListener); btnAlignCenter.setOnClickListener(alignListener); btnAlignRight.setOnClickListener(alignListener);

        View.OnClickListener targetListener = tBtn -> {
            GradientDrawable ina = new GradientDrawable(); ina.setColor(inactiveColor); ina.setCornerRadius(100f);
            GradientDrawable act = new GradientDrawable(); act.setColor(activeColor); act.setCornerRadius(100f);
            btnTargetMain.setBackground(ina); btnTargetStroke.setBackground(ina); btnTargetShadow.setBackground(ina); btnTargetInner.setBackground(ina); tBtn.setBackground(act);
            if (tBtn == btnTargetMain) activeColorTarget[0] = 0; else if (tBtn == btnTargetStroke) activeColorTarget[0] = 1; else if (tBtn == btnTargetShadow) activeColorTarget[0] = 2; else if (tBtn == btnTargetInner) activeColorTarget[0] = 3;
            colorPicker.setColor(textColors[activeColorTarget[0]]); etHex.setText(String.format("#%06X", (0xFFFFFF & textColors[activeColorTarget[0]])));
            pageTint.setVisibility(activeColorTarget[0] == 0 ? View.VISIBLE : View.GONE); pageStroke.setVisibility(activeColorTarget[0] == 1 ? View.VISIBLE : View.GONE); pageShadow.setVisibility(activeColorTarget[0] == 2 ? View.VISIBLE : View.GONE); pageInner.setVisibility(activeColorTarget[0] == 3 ? View.VISIBLE : View.GONE);
        };
        btnTargetMain.setOnClickListener(targetListener); btnTargetStroke.setOnClickListener(targetListener); btnTargetShadow.setOnClickListener(targetListener); btnTargetInner.setOnClickListener(targetListener);

        dialogUpdateRunnable = () -> {
            if (activeFontLabel != null) activeFontLabel.setText(currentCustomFont != null ? "Custom Font Loaded" : "Default Font Selected");
            String input = etInput.getText().toString(); if (input.isEmpty()) input = "Preview Text";
            GraphicLayer pLayer = new GraphicLayer(input, currentCustomFont, textColors[0], currentAlign[0], stateLetter[0], stateLine[0], stateStroke[0], textColors[1], stateShadow[0], textColors[2], stateInner[0], textColors[3], 0f, 0f);
            pLayer.shadowOffsetX = stateShadX[0]; pLayer.shadowOffsetY = stateShadY[0]; pLayer.innerShadowOffsetX = stateInnerX[0]; pLayer.innerShadowOffsetY = stateInnerY[0]; pLayer.isDirty = true;
            previewView.setPreviewLayer(pLayer);
        };

        boolean[] isUpdating = {false};
        colorPicker.setOnColorChangeListener(color -> {
            textColors[activeColorTarget[0]] = color;
            if (!isUpdating[0]) { isUpdating[0] = true; etHex.setText(String.format("#%06X", (0xFFFFFF & color))); isUpdating[0] = false; }
            debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50);
        });

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) { try { int newC = Color.parseColor(s.toString()); textColors[activeColorTarget[0]] = newC; colorPicker.setColor(newC); debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); } catch (Exception ignored) {} }
            } @Override public void afterTextChanged(Editable s) {}
        });

        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnEyedropper.setOnClickListener(eyeBtn -> {
            dialog.hide();
            editorView.startEyedropper(color -> { dialog.show(); textColors[activeColorTarget[0]] = color; colorPicker.setColor(color); etHex.setText(String.format("#%06X", (0xFFFFFF & color))); debounceHandler.removeCallbacks(dialogUpdateRunnable); debounceHandler.postDelayed(dialogUpdateRunnable, 50); });
        });

        btnPickFont.setOnClickListener(fontBtn -> activity.launchFontPicker());

        btnApply.setOnClickListener(applyBtn -> {
            String input = etInput.getText().toString().trim();
            if (!input.isEmpty()) {
                if (layerToEdit != null) {
                    layerToEdit.text = input; layerToEdit.typeface = currentCustomFont; layerToEdit.color = textColors[0]; layerToEdit.align = currentAlign[0]; layerToEdit.letterSpacing = stateLetter[0]; layerToEdit.lineSpacing = stateLine[0]; layerToEdit.strokeWidth = stateStroke[0]; layerToEdit.strokeColor = textColors[1]; layerToEdit.shadowRadius = stateShadow[0]; layerToEdit.shadowColor = textColors[2]; layerToEdit.innerShadowRadius = stateInner[0]; layerToEdit.innerShadowColor = textColors[3]; layerToEdit.shadowOffsetX = stateShadX[0]; layerToEdit.shadowOffsetY = stateShadY[0]; layerToEdit.innerShadowOffsetX = stateInnerX[0]; layerToEdit.innerShadowOffsetY = stateInnerY[0];
                    if (layerToEdit.textPaint == null) layerToEdit.textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                    layerToEdit.textPaint.setTypeface(layerToEdit.typeface); layerToEdit.textPaint.setLetterSpacing(layerToEdit.letterSpacing); layerToEdit.buildStaticLayout(); layerToEdit.isDirty = true; editorView.invalidate();
                } else {
                    GraphicLayer newLayer = new GraphicLayer(input, currentCustomFont, textColors[0], currentAlign[0], stateLetter[0], stateLine[0], stateStroke[0], textColors[1], stateShadow[0], textColors[2], stateInner[0], textColors[3], 0f, 0f);
                    newLayer.shadowOffsetX = stateShadX[0]; newLayer.shadowOffsetY = stateShadY[0]; newLayer.innerShadowOffsetX = stateInnerX[0]; newLayer.innerShadowOffsetY = stateInnerY[0]; newLayer.isDirty = true;
                    editorView.deselectLayer(); editorView.getLayers().add(newLayer); editorView.setActiveLayer(newLayer); editorView.getUndoStack().add(new ImageEditorActivity.ActionRecord(newLayer)); if (editorView.getLayerListener() != null) editorView.getLayerListener().run(); editorView.invalidate();
                }
            } dialog.dismiss();
        });

        dialogUpdateRunnable.run(); dialog.show();
        Window window = dialog.getWindow(); if (window != null) { window.setGravity(Gravity.BOTTOM); int height = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.70); window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height); window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.getDecorView().setPadding(0, 0, 0, 0); }
    }

    public void showLayerEditDialog(GraphicLayer layer) {
        if (layer == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.ModernDialogStyle); final AlertDialog[] dialogHolder = new AlertDialog[1];
        LinearLayout mainLayout = new LinearLayout(activity); mainLayout.setOrientation(LinearLayout.VERTICAL); mainLayout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#E61C1C1E") : Color.parseColor("#F2FFFFFF")); gd.setCornerRadii(new float[]{60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f}); mainLayout.setBackground(gd);

        TextView title = new TextView(activity); title.setText("Edit Layer"); title.setTextSize(20f); title.setTypeface(null, Typeface.BOLD); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setGravity(Gravity.CENTER); title.setPadding(0, 0, 0, dpToPx(16)); mainLayout.addView(title);

        LinearLayout targetGroup = new LinearLayout(activity); targetGroup.setOrientation(LinearLayout.HORIZONTAL); targetGroup.setGravity(Gravity.CENTER); targetGroup.setPadding(0, dpToPx(8), 0, dpToPx(16));
        Button btnTargetMain = createToggleButton("Tint", true); Button btnTargetStroke = createToggleButton("Stroke", false); Button btnTargetShadow = createToggleButton("Shadow", false); Button btnTargetInner = createToggleButton("Inner", false);
        targetGroup.addView(btnTargetMain); targetGroup.addView(btnTargetStroke); targetGroup.addView(btnTargetShadow); targetGroup.addView(btnTargetInner); mainLayout.addView(targetGroup);

        ColorPickerView colorPicker = new ColorPickerView(activity); LinearLayout.LayoutParams wheelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(180)); colorPicker.setLayoutParams(wheelLp); mainLayout.addView(colorPicker);

        LinearLayout hexRow = new LinearLayout(activity); hexRow.setOrientation(LinearLayout.HORIZONTAL); hexRow.setGravity(Gravity.CENTER); hexRow.setPadding(0, dpToPx(16), 0, dpToPx(16));
        EditText etHex = new EditText(activity); etHex.setText(String.format("#%06X", (0xFFFFFF & layer.color))); etHex.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); etHex.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        GradientDrawable etBg = new GradientDrawable(); etBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA")); etBg.setCornerRadius(30f); etHex.setBackground(etBg);
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(0, dpToPx(44), 1f); etHex.setLayoutParams(hexLp);

        int profBtnColor = isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#E5E5EA"); int profTextColor = isDarkTheme ? Color.WHITE : Color.BLACK;
        Button btnEyedropper = new Button(activity); btnEyedropper.setText("🖌️ Pick"); styleModernButton(btnEyedropper, profBtnColor, profTextColor);
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(44)); pickLp.setMargins(dpToPx(12), 0, 0, 0); btnEyedropper.setLayoutParams(pickLp);
        hexRow.addView(etHex); hexRow.addView(btnEyedropper); mainLayout.addView(hexRow);

        LinearLayout pageTint = new LinearLayout(activity); pageTint.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageStroke = new LinearLayout(activity); pageStroke.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageShadow = new LinearLayout(activity); pageShadow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout pageInner = new LinearLayout(activity); pageInner.setOrientation(LinearLayout.VERTICAL);

        if (layer.type == 1) {
            Button btnCropLayer = new Button(activity); btnCropLayer.setText("Crop Image Layer"); styleModernButton(btnCropLayer, profBtnColor, profTextColor);
            LinearLayout.LayoutParams cropLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(48)); cropLp.setMargins(0, dpToPx(8), 0, dpToPx(16)); btnCropLayer.setLayoutParams(cropLp); pageTint.addView(btnCropLayer, 0);
            btnCropLayer.setOnClickListener(v -> { if (dialogHolder[0] != null) dialogHolder[0].dismiss(); activity.hideRightPanel(); editorView.startLayerCrop(); activity.showCropToolbar(); });
        }

        Runnable layerUpdateRunnable = () -> { layer.isDirty = true; editorView.invalidate(); };
        pageTint.addView(createSliderWithLabel("Transparency", 0, 255, layer.alpha, (float v, boolean u) -> { if (u) { layer.alpha = (int)v; editorView.invalidate(); } }));
        pageTint.addView(createSliderWithLabel("Rotation", -180, 180, layer.rotation, (float v, boolean u) -> { if (u) { layer.rotation = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));

        LinearLayout mirrorRow = new LinearLayout(activity); mirrorRow.setOrientation(LinearLayout.HORIZONTAL); mirrorRow.setGravity(Gravity.CENTER); mirrorRow.setPadding(0, dpToPx(8), 0, dpToPx(8));
        Button btnMirrorH = new Button(activity); btnMirrorH.setText("Mirror ↔"); styleModernButton(btnMirrorH, profBtnColor, profTextColor);
        Button btnMirrorV = new Button(activity); btnMirrorV.setText("Mirror ↕"); styleModernButton(btnMirrorV, profBtnColor, profTextColor);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, dpToPx(48), 1f); btnLp.setMargins(dpToPx(4), 0, dpToPx(4), 0); btnMirrorH.setLayoutParams(btnLp); btnMirrorV.setLayoutParams(btnLp);
        btnMirrorH.setOnClickListener(btnH -> { layer.flipX = !layer.flipX; layer.isDirty=true; editorView.invalidate(); }); btnMirrorV.setOnClickListener(btnV -> { layer.flipY = !layer.flipY; layer.isDirty=true; editorView.invalidate(); });
        mirrorRow.addView(btnMirrorH); mirrorRow.addView(btnMirrorV); pageTint.addView(mirrorRow);

        pageStroke.addView(createSliderWithLabel("Stroke Thickness", 0, 50, layer.strokeWidth, (float v, boolean u) -> { if (u) { layer.strokeWidth = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Blur", 0, 50, layer.shadowRadius, (float v, boolean u) -> { if (u) { layer.shadowRadius = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset X", -100f, 100f, layer.shadowOffsetX, (float v, boolean u) -> { if (u) { layer.shadowOffsetX = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageShadow.addView(createSliderWithLabel("Shadow Offset Y", -100f, 100f, layer.shadowOffsetY, (float v, boolean u) -> { if (u) { layer.shadowOffsetY = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Shadow Blur", 0, 50, layer.innerShadowRadius, (float v, boolean u) -> { if (u) { layer.innerShadowRadius = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Offset X", -100f, 100f, layer.innerShadowOffsetX, (float v, boolean u) -> { if (u) { layer.innerShadowOffsetX = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));
        pageInner.addView(createSliderWithLabel("Inner Offset Y", -100f, 100f, layer.innerShadowOffsetY, (float v, boolean u) -> { if (u) { layer.innerShadowOffsetY = v; debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } }));

        mainLayout.addView(pageTint); mainLayout.addView(pageStroke); mainLayout.addView(pageShadow); mainLayout.addView(pageInner);
        pageStroke.setVisibility(View.GONE); pageShadow.setVisibility(View.GONE); pageInner.setVisibility(View.GONE);

        Button btnApply = new Button(activity); btnApply.setText("Done"); styleModernButton(btnApply, Color.parseColor("#4A90E2"), Color.WHITE);
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50)); doneLp.setMargins(0, dpToPx(16), 0, 0); btnApply.setLayoutParams(doneLp); mainLayout.addView(btnApply);

        ScrollView scrollWrapper = new ScrollView(activity); scrollWrapper.addView(mainLayout); builder.setView(scrollWrapper);
        final AlertDialog dialog = builder.create(); dialogHolder[0] = dialog; if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final int[] activeColorTarget = {0};
        int activeColor = isDarkTheme ? Color.parseColor("#4A4A4C") : Color.parseColor("#D1D1D6"); int inactiveColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5EA");

        View.OnClickListener targetListener = tBtn -> {
            GradientDrawable ina = new GradientDrawable(); ina.setColor(inactiveColor); ina.setCornerRadius(100f);
            GradientDrawable act = new GradientDrawable(); act.setColor(activeColor); act.setCornerRadius(100f);
            btnTargetMain.setBackground(ina); btnTargetStroke.setBackground(ina); btnTargetShadow.setBackground(ina); btnTargetInner.setBackground(ina); tBtn.setBackground(act);
            if (tBtn == btnTargetMain) activeColorTarget[0] = 0; else if (tBtn == btnTargetStroke) activeColorTarget[0] = 1; else if (tBtn == btnTargetShadow) activeColorTarget[0] = 2; else if (tBtn == btnTargetInner) activeColorTarget[0] = 3;
            int displayColor = Color.WHITE; if (activeColorTarget[0] == 0) displayColor = layer.color; else if (activeColorTarget[0] == 1) displayColor = layer.strokeColor; else if (activeColorTarget[0] == 2) displayColor = layer.shadowColor; else if (activeColorTarget[0] == 3) displayColor = layer.innerShadowColor;
            colorPicker.setColor(displayColor); etHex.setText(String.format("#%06X", (0xFFFFFF & displayColor)));
            pageTint.setVisibility(activeColorTarget[0] == 0 ? View.VISIBLE : View.GONE); pageStroke.setVisibility(activeColorTarget[0] == 1 ? View.VISIBLE : View.GONE); pageShadow.setVisibility(activeColorTarget[0] == 2 ? View.VISIBLE : View.GONE); pageInner.setVisibility(activeColorTarget[0] == 3 ? View.VISIBLE : View.GONE);
        };
        btnTargetMain.setOnClickListener(targetListener); btnTargetStroke.setOnClickListener(targetListener); btnTargetShadow.setOnClickListener(targetListener); btnTargetInner.setOnClickListener(targetListener);

        boolean[] isUpdating = {false};
        colorPicker.setOnColorChangeListener(color -> {
            if (activeColorTarget[0] == 0) layer.color = color; else if (activeColorTarget[0] == 1) layer.strokeColor = color; else if (activeColorTarget[0] == 2) layer.shadowColor = color; else if (activeColorTarget[0] == 3) layer.innerShadowColor = color;
            if (!isUpdating[0]) { isUpdating[0] = true; etHex.setText(String.format("#%06X", (0xFFFFFF & color))); isUpdating[0] = false; }
            debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50);
        });

        etHex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.toString().startsWith("#")) { try { int newC = Color.parseColor(s.toString()); if (activeColorTarget[0] == 0) layer.color = newC; else if (activeColorTarget[0] == 1) layer.strokeColor = newC; else if (activeColorTarget[0] == 2) layer.shadowColor = newC; else if (activeColorTarget[0] == 3) layer.innerShadowColor = newC; colorPicker.setColor(newC); debounceHandler.removeCallbacks(layerUpdateRunnable); debounceHandler.postDelayed(layerUpdateRunnable, 50); } catch (Exception ignored) {} }
            } @Override public void afterTextChanged(Editable s) {}
        });

        btnEyedropper.setOnClickListener(eyeBtn -> {
            dialog.hide();
            editorView.startEyedropper(color -> { dialog.show(); if (activeColorTarget[0] == 0) layer.color = color; else if (activeColorTarget[0] == 1) layer.strokeColor = color; else if (activeColorTarget[0] == 2) layer.shadowColor = color; else if (activeColorTarget[0] == 3) layer.innerShadowColor = color; colorPicker.setColor(color); etHex.setText(String.format("#%06X", (0xFFFFFF & color))); layer.isDirty = true; editorView.invalidate(); });
        });

        btnApply.setOnClickListener(doneBtn -> dialog.dismiss());
        dialog.show();

        Window window = dialog.getWindow(); if (window != null) { window.setGravity(Gravity.BOTTOM); int height = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.70); window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, height); window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.getDecorView().setPadding(0, 0, 0, 0); }
    }

    public static class SettingsSlider extends View {
        public float min = 0f, max = 100f, value = 0f; public int trackColor, progressColor, textColor; private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public interface OnChangeListener { void onChanged(float val, boolean fromUser); } public OnChangeListener listener;
        public SettingsSlider(Context context, boolean isDarkTheme) { super(context); trackColor = isDarkTheme ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A000000"); progressColor = isDarkTheme ? Color.WHITE : Color.parseColor("#333333"); textColor = isDarkTheme ? Color.BLACK : Color.WHITE; }
        public void setRange(float min, float max) { this.min = min; this.max = max; }
        public void setValue(float val) { this.value = Math.max(min, Math.min(val, max)); invalidate(); if(listener != null) listener.onChanged(this.value, false); }
        @Override protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            paint.setColor(trackColor);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, paint);

            paint.setTextSize(getHeight() * 0.40f);
            paint.setTypeface(Typeface.DEFAULT_BOLD);

            String valStr;
            if (max - min <= 10f) {
                valStr = String.format(java.util.Locale.US, "%.2f", value);
            } else {
                valStr = String.valueOf((int) value);
            }

            Paint.FontMetrics fm = paint.getFontMetrics();
            float textY = (getHeight() - fm.ascent - fm.descent) / 2f;
            float textX = getWidth() - getHeight() * 0.6f - paint.measureText(valStr);

            paint.setColor(progressColor);
            canvas.drawText(valStr, textX, textY, paint);

            float progress = (value - min) / (max - min);
            float right = Math.max(getHeight(), getWidth() * progress);

            canvas.save();
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(0, 0, right, getHeight(), r, r, android.graphics.Path.Direction.CW);
            canvas.clipPath(clipPath);

            paint.setColor(progressColor);
            canvas.drawRoundRect(0, 0, right, getHeight(), r, r, paint);

            paint.setColor(textColor);
            canvas.drawText(valStr, textX, textY, paint);
            canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent event) { if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) { float progress = Math.max(0f, Math.min(event.getX() / getWidth(), 1f)); value = min + progress * (max - min); invalidate(); if(listener != null) listener.onChanged(value, true); return true; } return super.onTouchEvent(event); }
    }

    public static class TextPreviewView extends View {
        public GraphicLayer previewLayer;
        public TextPreviewView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        public void setPreviewLayer(GraphicLayer layer) { this.previewLayer = layer; if (previewLayer != null) previewLayer.bake(); invalidate(); }
        @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); if (previewLayer != null) { float viewW = getWidth(); float viewH = getHeight(); float layerW = previewLayer.bounds.width(); float layerH = previewLayer.bounds.height(); float scale = 1f; if (layerW > 0 && layerH > 0) { float scaleX = (viewW - 80) / layerW; float scaleY = (viewH - 80) / layerH; scale = Math.min(scaleX, scaleY); if (scale > 1f) scale = 1f; } canvas.save(); canvas.translate(viewW / 2f, viewH / 2f); canvas.scale(scale, scale); previewLayer.drawLayer(canvas); canvas.restore(); } }
    }

    public interface OnColorChangeListener { void onColorChanged(int color); }
    public static class ColorPickerView extends View {
        private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF mBgPickerRect = new RectF(); private float cursorX = 50, cursorY = 50; private OnColorChangeListener listener;
        public ColorPickerView(Context context) { super(context); setLayerType(LAYER_TYPE_SOFTWARE, null); cursorPaint.setStyle(Paint.Style.STROKE); cursorPaint.setStrokeWidth(5); cursorPaint.setColor(Color.WHITE); cursorPaint.setShadowLayer(4, 0, 0, Color.BLACK); }
        public void setOnColorChangeListener(OnColorChangeListener listener) { this.listener = listener; }
        public void setColor(int color) { float[] hsv = new float[3]; Color.colorToHSV(color, hsv); post(() -> { if (getWidth() > 0 && getHeight() > 0) { cursorX = (hsv[0] / 360f) * getWidth(); if (hsv[1] < 1f || hsv[2] == 1f) { cursorY = (hsv[1] / 2f) * getHeight(); } else { cursorY = (0.5f + (1f - hsv[2]) / 2f) * getHeight(); } invalidate(); } }); if (listener != null) listener.onColorChanged(color); }
        @Override protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) { super.onSizeChanged(w, h, oldWidth, oldHeight); mBgPickerRect.set(0, 0, w, h); int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED}; LinearGradient shader = new LinearGradient(0, 0, w, 0, colors, null, Shader.TileMode.CLAMP); huePaint.setShader(shader); LinearGradient whiteShader = new LinearGradient(0, 0, 0, h / 2f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP); whitePaint.setShader(whiteShader); LinearGradient blackShader = new LinearGradient(0, h / 2f, 0, h, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP); blackPaint.setShader(blackShader); }
        @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); canvas.drawRoundRect(mBgPickerRect, 24f, 24f, huePaint); canvas.drawRoundRect(mBgPickerRect, 24f, 24f, whitePaint); canvas.drawRoundRect(mBgPickerRect, 24f, 24f, blackPaint); canvas.drawCircle(cursorX, cursorY, 20f, cursorPaint); }
        @SuppressLint("ClickableViewAccessibility") @Override public boolean onTouchEvent(MotionEvent event) { int action = event.getActionMasked(); if (action == MotionEvent.ACTION_DOWN) { getParent().requestDisallowInterceptTouchEvent(true); } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { getParent().requestDisallowInterceptTouchEvent(false); } cursorX = Math.max(0, Math.min(event.getX(), getWidth())); cursorY = Math.max(0, Math.min(event.getY(), getHeight())); float hue = (cursorX / getWidth()) * 360f; float yNorm = cursorY / getHeight(); float saturation; float value; if (yNorm <= 0.5f) { saturation = yNorm * 2f; value = 1f; } else { saturation = 1f; value = 1f - ((yNorm - 0.5f) * 2f); } int color = Color.HSVToColor(new float[]{hue, saturation, value}); if (listener != null) { listener.onColorChanged(color); } invalidate(); return true; }
    }

    public static class GraphicLayer {
        public int type; public float x, y, scaleX = 1f, scaleY = 1f, rotation = 0f; public int alpha = 255; public boolean flipX = false, flipY = false; public RectF bounds = new RectF(); public String text; public Bitmap bitmap; public Typeface typeface; public int color, strokeColor, shadowColor, innerShadowColor; public Layout.Alignment align; public float letterSpacing, lineSpacing, strokeWidth, shadowRadius, innerShadowRadius; public float shadowOffsetX = 0f, shadowOffsetY = 0f; public float innerShadowOffsetX = 0f, innerShadowOffsetY = 0f; public TextPaint textPaint; public StaticLayout staticLayout; public Bitmap bakedCache; public Bitmap alphaCache; public float bakedScaleFactor = 1f; public float currentPad = 0f; public boolean isDirty = true; public Paint renderPaint;

        public GraphicLayer(GraphicLayer src) { this.type = src.type; this.x = src.x + 50f; this.y = src.y + 50f; this.scaleX = src.scaleX; this.scaleY = src.scaleY; this.rotation = src.rotation; this.bounds = new RectF(src.bounds); this.text = src.text; this.bitmap = src.bitmap; this.typeface = src.typeface; this.color = src.color; this.strokeColor = src.strokeColor; this.shadowColor = src.shadowColor; this.innerShadowColor = src.innerShadowColor; this.innerShadowRadius = src.innerShadowRadius; this.align = src.align; this.letterSpacing = src.letterSpacing; this.lineSpacing = src.lineSpacing; this.strokeWidth = src.strokeWidth; this.shadowRadius = src.shadowRadius; this.alpha = src.alpha; this.flipX = src.flipX; this.flipY = src.flipY; this.shadowOffsetX = src.shadowOffsetX; this.shadowOffsetY = src.shadowOffsetY; this.innerShadowOffsetX = src.innerShadowOffsetX; this.innerShadowOffsetY = src.innerShadowOffsetY; renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); renderPaint.setDither(true); if (src.textPaint != null) this.textPaint = new TextPaint(src.textPaint); if (type == 0) buildStaticLayout(); else updateBounds(); this.currentPad = src.currentPad; this.isDirty = true; this.bakedCache = null; if (src.alphaCache != null && !src.alphaCache.isRecycled()) { Bitmap.Config conf = src.alphaCache.getConfig(); if (conf == null) conf = Bitmap.Config.ARGB_8888; this.alphaCache = src.alphaCache.copy(conf, true); } }
        public GraphicLayer(String text, Typeface typeface, int color, Layout.Alignment align, float letterSpacing, float lineSpacing, float strokeWidth, int strokeColor, float shadowRadius, int shadowColor, float innerShadowRadius, int innerShadowColor, float x, float y) { this.type = 0; this.text = text; this.typeface = typeface; this.color = color; this.align = align; this.letterSpacing = letterSpacing; this.lineSpacing = lineSpacing; this.strokeWidth = strokeWidth; this.strokeColor = strokeColor; this.shadowRadius = shadowRadius; this.shadowColor = shadowColor; this.innerShadowRadius = innerShadowRadius; this.innerShadowColor = innerShadowColor; this.x = x; this.y = y; renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); renderPaint.setDither(true); textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG); textPaint.setTextSize(250f); textPaint.setStrokeJoin(Paint.Join.ROUND); textPaint.setStrokeCap(Paint.Cap.ROUND); if (typeface != null) textPaint.setTypeface(typeface); textPaint.setLetterSpacing(letterSpacing); buildStaticLayout(); this.isDirty = true; }
        public GraphicLayer(Bitmap bitmap, float x, float y) { this.type = 1; this.bitmap = bitmap; this.x = x; this.y = y; this.color = Color.WHITE; this.strokeColor = Color.BLACK; this.shadowColor = Color.BLACK; this.innerShadowColor = Color.BLACK; renderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); renderPaint.setDither(true); updateBounds(); this.isDirty = true; }
        public void buildStaticLayout() { float maxWidth = 0; String[] lines = text.split("\n"); for (String line : lines) { float w = textPaint.measureText(line); if (w > maxWidth) maxWidth = w; } int width = (int) maxWidth + 20; staticLayout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, Math.max(10, width)).setAlignment(align).setLineSpacing(lineSpacing, 1.0f).build(); bounds.set(-staticLayout.getWidth() / 2f, -staticLayout.getHeight() / 2f, staticLayout.getWidth() / 2f, staticLayout.getHeight() / 2f); }
        public void updateBounds() { if (type == 1 && bitmap != null) { bounds.set(-bitmap.getWidth()/2f, -bitmap.getHeight()/2f, bitmap.getWidth()/2f, bitmap.getHeight()/2f); if (alphaCache != null && !alphaCache.isRecycled()) alphaCache.recycle(); alphaCache = bitmap.extractAlpha(); } }
        public float getPadding() { if (type == 0) { return 250f; } else { float imgScaleFactor = Math.max(bitmap.getWidth(), bitmap.getHeight()) / 300f; if (imgScaleFactor < 1f) imgScaleFactor = 1f; return 250f * imgScaleFactor; } }

        public void bake() {
            if (type == 0 && staticLayout == null) return; if (type == 1 && bitmap == null) return;
            currentPad = getPadding(); float scaleFactor = 1f; int originalW = (type == 0) ? staticLayout.getWidth() : bitmap.getWidth(); int originalH = (type == 0) ? staticLayout.getHeight() : bitmap.getHeight(); int maxDim = Math.max(originalW, originalH); if (maxDim > 1000) scaleFactor = 1000f / maxDim;
            int bw = (int) ((originalW + currentPad * 2) * scaleFactor); int bh = (int) ((originalH + currentPad * 2) * scaleFactor);
            if (bw <= 0 || bh <= 0) return;
            if (bakedCache != null && (bakedCache.getWidth() != bw || bakedCache.getHeight() != bh)) { bakedCache.recycle(); bakedCache = null; }
            if (bakedCache == null) { try { bakedCache = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); } catch (OutOfMemoryError e) { scaleFactor *= 0.5f; bw = (int) ((originalW + currentPad * 2) * scaleFactor); bh = (int) ((originalH + currentPad * 2) * scaleFactor); try { bakedCache = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); } catch (OutOfMemoryError e2) { isDirty = false; return; } } } else { bakedCache.eraseColor(Color.TRANSPARENT); }
            Canvas c = new Canvas(bakedCache); c.scale(scaleFactor, scaleFactor); c.translate(currentPad, currentPad);

            if (type == 0) {
                if (shadowRadius > 0.1f || shadowOffsetX != 0 || shadowOffsetY != 0) { textPaint.setStyle(Paint.Style.FILL); float blurAmt = Math.max(0.1f, shadowRadius); textPaint.setShadowLayer(blurAmt, shadowOffsetX, shadowOffsetY, shadowColor); textPaint.setColor(color); staticLayout.draw(c); textPaint.clearShadowLayer(); }
                if (strokeWidth > 0.1f) { textPaint.setStyle(Paint.Style.STROKE); textPaint.setStrokeWidth(strokeWidth * 2f); textPaint.setColor(strokeColor); staticLayout.draw(c); }
                c.saveLayer(null, null); textPaint.setStyle(Paint.Style.FILL); textPaint.setColor(color); staticLayout.draw(c);
                if (innerShadowRadius > 0.1f || innerShadowOffsetX != 0 || innerShadowOffsetY != 0) {
                    Paint atopPaint = new Paint(Paint.ANTI_ALIAS_FLAG); atopPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)); c.saveLayer(null, atopPaint);
                    Paint shadowFill = new Paint(Paint.ANTI_ALIAS_FLAG); shadowFill.setColor(innerShadowColor); c.drawRect(-currentPad * 2, -currentPad * 2, originalW + currentPad * 2, originalH + currentPad * 2, shadowFill);
                    int oldColor = textPaint.getColor(); textPaint.setColor(Color.BLACK); textPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)); float blurAmt = Math.max(0.1f, innerShadowRadius); textPaint.setMaskFilter(new BlurMaskFilter(blurAmt, BlurMaskFilter.Blur.NORMAL));
                    c.save(); c.translate(innerShadowOffsetX, innerShadowOffsetY); staticLayout.draw(c); c.restore();
                    textPaint.setColor(oldColor); textPaint.setXfermode(null); textPaint.setMaskFilter(null); c.restore();
                } c.restore();
            } else {
                if (alphaCache == null || alphaCache.isRecycled()) { int aw = Math.max(1, (int)(bitmap.getWidth() * 0.25f)); int ah = Math.max(1, (int)(bitmap.getHeight() * 0.25f)); Bitmap small = Bitmap.createScaledBitmap(bitmap, aw, ah, true); alphaCache = small.extractAlpha(); if (small != bitmap) small.recycle(); }
                float imgScaleFactor = Math.max(bitmap.getWidth(), bitmap.getHeight()) / 300f; if (imgScaleFactor < 1f) imgScaleFactor = 1f;
                float sShadow = shadowRadius * imgScaleFactor; float sStroke = strokeWidth * imgScaleFactor; float inShadow = innerShadowRadius * imgScaleFactor;
                float sOffsetX = shadowOffsetX * imgScaleFactor; float sOffsetY = shadowOffsetY * imgScaleFactor; float inOffsetX = innerShadowOffsetX * imgScaleFactor; float inOffsetY = innerShadowOffsetY * imgScaleFactor;

                if (sShadow > 0.1f || sOffsetX != 0 || sOffsetY != 0) { c.save(); c.translate(sOffsetX, sOffsetY); c.scale(bitmap.getWidth() / (float)alphaCache.getWidth(), bitmap.getHeight() / (float)alphaCache.getHeight()); Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); shadowPaint.setColor(shadowColor); shadowPaint.setMaskFilter(new BlurMaskFilter(Math.max(0.1f, sShadow), BlurMaskFilter.Blur.NORMAL)); c.drawBitmap(alphaCache, 0, 0, shadowPaint); c.restore(); }
                if (sStroke > 0.1f) { c.save(); c.scale(bitmap.getWidth() / (float)alphaCache.getWidth(), bitmap.getHeight() / (float)alphaCache.getHeight()); Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); strokePaint.setColor(strokeColor); int steps = 12; for (int i = 0; i < steps; i++) { float angle = (float) (i * 2 * Math.PI / steps); float sdx = (float) Math.cos(angle) * sStroke * ((float)alphaCache.getWidth() / bitmap.getWidth()); float sdy = (float) Math.sin(angle) * sStroke * ((float)alphaCache.getHeight() / bitmap.getHeight()); c.drawBitmap(alphaCache, sdx, sdy, strokePaint); } c.restore(); }
                Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); c.drawBitmap(bitmap, 0, 0, fillPaint);

                if (inShadow > 0.1f || inOffsetX != 0 || inOffsetY != 0) {
                    c.saveLayer(null, null); c.drawBitmap(bitmap, 0, 0, null);
                    Paint atopPaint = new Paint(Paint.ANTI_ALIAS_FLAG); atopPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)); c.saveLayer(null, atopPaint);
                    Paint innerFill = new Paint(Paint.ANTI_ALIAS_FLAG); innerFill.setColor(innerShadowColor); c.drawRect(-currentPad * 2, -currentPad * 2, bitmap.getWidth() + currentPad * 2, bitmap.getHeight() + currentPad * 2, innerFill);
                    Paint punchPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); punchPaint.setColor(Color.BLACK); punchPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT)); punchPaint.setMaskFilter(new BlurMaskFilter(Math.max(0.1f, inShadow), BlurMaskFilter.Blur.NORMAL));
                    c.save(); c.translate(inOffsetX, inOffsetY); c.scale(bitmap.getWidth() / (float)alphaCache.getWidth(), bitmap.getHeight() / (float)alphaCache.getHeight()); c.drawBitmap(alphaCache, 0, 0, punchPaint); c.restore();
                    c.restore(); c.restore();
                }
            }
            this.bakedScaleFactor = scaleFactor; isDirty = false;
        }

        public void drawLayer(Canvas canvas) {
            if (isDirty || bakedCache == null || bakedCache.isRecycled()) bake();
            canvas.save(); canvas.scale(flipX ? -1 : 1, flipY ? -1 : 1);
            float cx = type == 0 ? staticLayout.getWidth() / 2f : bitmap.getWidth() / 2f; float cy = type == 0 ? staticLayout.getHeight() / 2f : bitmap.getHeight() / 2f;
            if (bakedCache != null && !bakedCache.isRecycled()) {
                canvas.save(); canvas.translate(-cx - currentPad, -cy - currentPad); canvas.scale(1f / bakedScaleFactor, 1f / bakedScaleFactor);
                renderPaint.setAlpha(alpha); canvas.drawBitmap(bakedCache, 0, 0, renderPaint); canvas.restore();
            }
            canvas.restore();
        }
    }
}