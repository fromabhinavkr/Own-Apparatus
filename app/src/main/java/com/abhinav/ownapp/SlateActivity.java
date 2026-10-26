package com.abhinav.ownapp;

import android.annotation.SuppressLint; import android.app.Dialog; import android.content.ContentResolver; import android.content.ContentUris;
import android.content.ContentValues; import android.content.Context; import android.content.SharedPreferences; import android.content.pm.ActivityInfo;
import android.database.Cursor; import android.graphics.Bitmap; import android.graphics.Canvas;
import android.graphics.Color; import android.graphics.ComposeShader; import android.graphics.Matrix; import android.graphics.Paint; import android.graphics.Path;
import android.graphics.PixelFormat; import android.graphics.PointF; import android.graphics.PorterDuff; import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface; import android.graphics.drawable.ColorDrawable; import android.graphics.drawable.GradientDrawable;
import android.net.Uri; import android.os.Build; import android.os.Bundle; import android.os.Environment; import android.provider.MediaStore;
import android.text.Html; import android.util.DisplayMetrics; import android.util.TypedValue; import android.view.Gravity;
import android.view.MotionEvent; import android.view.ScaleGestureDetector; import android.view.SurfaceHolder; import android.view.SurfaceView;
import android.view.View; import android.view.ViewGroup; import android.view.Window; import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator; import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator; import android.view.animation.OvershootInterpolator;
import android.widget.Button; import android.widget.EditText; import android.widget.FrameLayout; import android.widget.LinearLayout;
import android.widget.RelativeLayout; import android.widget.ScrollView; import android.widget.TextView; import android.widget.Toast;
import androidx.activity.OnBackPressedCallback; import androidx.annotation.NonNull; import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets; import androidx.core.view.ViewCompat; import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat; import androidx.core.view.WindowInsetsControllerCompat; import androidx.core.widget.TextViewCompat;
import java.io.OutputStream; import java.util.ArrayList; import java.util.HashSet; import java.util.List; import java.util.Set;

@SuppressWarnings("all")
public class SlateActivity extends AppCompatActivity {
    private boolean isDarkTheme, isMenuOpen = false, isZoomEnabled = true, isFieldZoomEnabled = false;
    private SharedPreferences prefs; private SlateCanvas slateCanvas; private LinearLayout flowerMenuLayout; private ScrollView flowerMenuScroll;
    private final String indicatorOn = " <font color='#34C759'>●</font>"; private final String indicatorOff = " <font color='#FF3B30'>●</font>";
    private static String memoryDraftBackup = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        setupTrueFullScreenWindow();
        setContentView(R.layout.activity_slate);

        View root = findViewById(R.id.slateRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) root.setForceDarkAllowed(false);

        prefs = getSharedPreferences(SnakeWidget.PREFS_NAME, MODE_PRIVATE);
        isDarkTheme = prefs.getBoolean(SnakeWidget.PREF_IS_DARK, true);

        slateCanvas = new SlateCanvas(this, isDarkTheme);
        ((FrameLayout) findViewById(R.id.slateContainer)).addView(slateCanvas);

        flowerMenuScroll = findViewById(R.id.flowerMenuScroll);
        flowerMenuLayout = findViewById(R.id.flowerMenuLayout);
        TextView btnFlowerMenu = findViewById(R.id.btnFlowerMenu);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
            RelativeLayout.LayoutParams fabLp = (RelativeLayout.LayoutParams) btnFlowerMenu.getLayoutParams();
            int baseMargin = dpToPx(20);
            fabLp.setMargins(baseMargin + safe.left, baseMargin + safe.top, baseMargin + safe.right, baseMargin + safe.bottom);
            btnFlowerMenu.setLayoutParams(fabLp);
            if (flowerMenuScroll != null) flowerMenuScroll.setPadding(0, safe.top + dpToPx(8), safe.right, 0);
            root.post(this::applyFlexSizingToMenu);
            return WindowInsetsCompat.CONSUMED;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (isMenuOpen) toggleMenu(false, btnFlowerMenu); else showExitConfirmationDialog(); }
        });

        btnFlowerMenu.setOnClickListener(v -> toggleMenu(!isMenuOpen, btnFlowerMenu));

        Button menuPen = findViewById(R.id.menuPen), menuEraser = findViewById(R.id.menuEraser);
        Button menuZoom = findViewById(R.id.menuZoom), menuFieldZoom = findViewById(R.id.menuFieldZoom);

        setupFlexMenuButtons();

        menuPen.setOnClickListener(v -> { slateCanvas.setToolMode(SlateCanvas.MODE_PEN); updateToolIndicators(menuPen, menuEraser); toggleMenu(false, btnFlowerMenu); });
        menuEraser.setOnClickListener(v -> { slateCanvas.setToolMode(SlateCanvas.MODE_ERASER); updateToolIndicators(menuPen, menuEraser); toggleMenu(false, btnFlowerMenu); });
        findViewById(R.id.menuUndo).setOnClickListener(v -> { slateCanvas.undo(); toggleMenu(false, btnFlowerMenu); });
        findViewById(R.id.menuColor).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showColorPickerModal(); });
        findViewById(R.id.menuClear).setOnClickListener(v -> { slateCanvas.clearCanvas(); toggleMenu(false, btnFlowerMenu); });
        findViewById(R.id.menuSave).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showSaveDraftDialog(); });
        findViewById(R.id.menuDrafts).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showDraftsGalleryDialog(); });
        findViewById(R.id.menuTheme).setOnClickListener(v -> { isDarkTheme = !isDarkTheme; slateCanvas.setDarkTheme(isDarkTheme); updateMenuTheme(); toggleMenu(false, btnFlowerMenu); });

        menuZoom.setOnClickListener(v -> {
            isZoomEnabled = !isZoomEnabled; slateCanvas.setZoomEnabled(isZoomEnabled);
            menuZoom.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF");
            applyPillBackground(menuZoom, Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30"));
            toggleMenu(false, btnFlowerMenu);
        });

        menuFieldZoom.setOnClickListener(v -> {
            isFieldZoomEnabled = !isFieldZoomEnabled; slateCanvas.setFieldZoomEnabled(isFieldZoomEnabled);
            menuFieldZoom.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF");
            applyPillBackground(menuFieldZoom, Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30"));
            toggleMenu(false, btnFlowerMenu);
        });

        findViewById(R.id.menuExit).setOnClickListener(v -> { toggleMenu(false, btnFlowerMenu); showExitConfirmationDialog(); });

        updateMenuTheme(); updateToolIndicators(menuPen, menuEraser);
        menuZoom.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF");
        applyPillBackground(menuZoom, Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30"));
        menuFieldZoom.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF");
        applyPillBackground(menuFieldZoom, Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30"));
        root.post(this::applyFlexSizingToMenu);
    }

    private void setupTrueFullScreenWindow() {
        Window window = getWindow(); if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.setStatusBarColor(Color.TRANSPARENT); window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        hideSystemBars();
    }

    private void hideSystemBars() {
        Window window = getWindow(); if (window == null) return;
        View decor = window.getDecorView();
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars(); }

    private void setupFlexMenuButtons() {
        int[] allBtns = {R.id.menuPen, R.id.menuEraser, R.id.menuUndo, R.id.menuColor, R.id.menuClear, R.id.menuSave, R.id.menuDrafts, R.id.menuTheme, R.id.menuZoom, R.id.menuFieldZoom, R.id.menuExit};
        for (int id : allBtns) {
            Button b = findViewById(id); if (b == null) continue;
            b.setAllCaps(false); b.setSingleLine(true); b.setIncludeFontPadding(false);
            b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0);
            b.setGravity(Gravity.CENTER);
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(b, 10, 14, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private void applyFlexSizingToMenu() {
        if (flowerMenuLayout == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenH = dm.heightPixels, screenW = dm.widthPixels;
        int count = flowerMenuLayout.getChildCount(); if (count == 0) return;

        int reservedBottom = dpToPx(110);
        int availH = Math.max(dpToPx(240), screenH - reservedBottom);
        int gap = Math.max(dpToPx(3), Math.min(dpToPx(6), availH / (count * 9)));
        int targetBtnH = (availH - (gap * (count + 1))) / count;
        int btnH = Math.max(dpToPx(32), Math.min(dpToPx(44), targetBtnH));
        int btnW = Math.max(dpToPx(150), Math.min(dpToPx(210), (int) (screenW * 0.48f)));
        int padH = dpToPx(14), padV = dpToPx(2);

        for (int i = 0; i < count; i++) {
            View child = flowerMenuLayout.getChildAt(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnW, btnH);
            lp.bottomMargin = (i == count - 1) ? dpToPx(4) : gap;
            child.setLayoutParams(lp);
            child.setPadding(padH, padV, padH, padV);
        }
        flowerMenuLayout.requestLayout();
    }

    private void applyPillBackground(Button btn, int color) {
        GradientDrawable gd = new GradientDrawable(); gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dpToPx(24)); gd.setColor(color);
        btn.setBackgroundTintList(null); btn.setBackground(gd);
    }

    private int dpToPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    private void showColorPickerModal() {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.setOnDismissListener(dialog -> hideSystemBars());

        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(22), dpToPx(22), dpToPx(22), dpToPx(22));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(isDarkTheme ? Color.parseColor("#F21C1C1E") : Color.parseColor("#F2F2F2F7"));
        gd.setCornerRadius(dpToPx(24)); root.setBackground(gd);

        TextView title = new TextView(this); title.setText("Brush Settings"); title.setTextSize(20f);
        title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dpToPx(16)); root.addView(title);

        int currentColor = slateCanvas.getCurrentPenColor();

        final EditText[] hexInputHolder = new EditText[1];
        final View[] colorPreviewHolder = new View[1];

        final ColorBoxView colorBox = new ColorBoxView(this, currentColor, color -> {
            slateCanvas.setPenColor(color);
            String hex = String.format("#%06X", (0xFFFFFF & color));
            if (hexInputHolder[0] != null && !hexInputHolder[0].hasFocus()) {
                hexInputHolder[0].setText(hex);
            }
            if (colorPreviewHolder[0] != null) {
                GradientDrawable previewBg = (GradientDrawable) colorPreviewHolder[0].getBackground();
                if (previewBg != null) previewBg.setColor(color);
            }
        });
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(180));
        boxLp.bottomMargin = dpToPx(14);
        root.addView(colorBox, boxLp);

        HueBarView hueBar = new HueBarView(this, currentColor, color -> {
            colorBox.setHue(color);
            int updatedColor = colorBox.getCurrentColor();
            slateCanvas.setPenColor(updatedColor);
            String hex = String.format("#%06X", (0xFFFFFF & updatedColor));
            if (hexInputHolder[0] != null) hexInputHolder[0].setText(hex);
            if (colorPreviewHolder[0] != null) {
                GradientDrawable previewBg = (GradientDrawable) colorPreviewHolder[0].getBackground();
                if (previewBg != null) previewBg.setColor(updatedColor);
            }
        });
        LinearLayout.LayoutParams hueLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40));
        hueLp.bottomMargin = dpToPx(16);
        root.addView(hueBar, hueLp);

        LinearLayout hexRow = new LinearLayout(this); hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);

        View colorPreview = new View(this);
        GradientDrawable previewBg = new GradientDrawable(); previewBg.setShape(GradientDrawable.OVAL); previewBg.setColor(currentColor);
        colorPreview.setBackground(previewBg);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(42));
        previewLp.setMargins(0, 0, dpToPx(12), 0);
        colorPreview.setLayoutParams(previewLp);
        colorPreviewHolder[0] = colorPreview;
        hexRow.addView(colorPreview);

        EditText hexInput = new EditText(this); hexInput.setHint("#RRGGBB"); hexInput.setText(String.format("#%06X", (0xFFFFFF & currentColor)));
        hexInput.setHintTextColor(Color.GRAY); hexInput.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        hexInput.setTextSize(18f); hexInput.setTypeface(null, Typeface.BOLD);
        GradientDrawable inputBg = new GradientDrawable(); inputBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF"));
        inputBg.setCornerRadius(dpToPx(12)); hexInput.setBackground(inputBg); hexInput.setPadding(dpToPx(14), dpToPx(10), dpToPx(14), dpToPx(10));
        LinearLayout.LayoutParams hexLp = new LinearLayout.LayoutParams(0, dpToPx(46), 1f);
        hexInputHolder[0] = hexInput;
        hexRow.addView(hexInput, hexLp);

        Button hexBtn = new Button(this); hexBtn.setText("Apply"); hexBtn.setAllCaps(false);
        applyPillBackground(hexBtn, Color.parseColor("#4A90E2")); hexBtn.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnHexLp = new LinearLayout.LayoutParams(dpToPx(90), dpToPx(46)); btnHexLp.setMargins(dpToPx(10), 0, 0, 0);

        View.OnClickListener applyHexAction = v -> {
            try {
                String val = hexInput.getText().toString().trim();
                if (!val.startsWith("#")) val = "#" + val;
                int parsedColor = Color.parseColor(val);
                slateCanvas.setPenColor(parsedColor);
                colorBox.setColor(parsedColor);
                hueBar.setColor(parsedColor);
                previewBg.setColor(parsedColor);
                Toast.makeText(this, "Color applied!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Invalid HEX code", Toast.LENGTH_SHORT).show();
            }
        };

        hexBtn.setOnClickListener(applyHexAction);
        hexInput.setOnEditorActionListener((v, actionId, event) -> {
            applyHexAction.onClick(v);
            return true;
        });

        hexRow.addView(hexBtn, btnHexLp);
        root.addView(hexRow);

        d.setContentView(root); d.show();
    }

    private static class HueBarView extends View {
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float currentHue = 0f;
        private final OnHueChangedListener listener;

        interface OnHueChangedListener { void onHueChanged(int color); }

        HueBarView(Context context, int initialColor, OnHueChangedListener l) {
            super(context); this.listener = l;
            float[] hsv = new float[3];
            Color.colorToHSV(initialColor, hsv);
            currentHue = hsv[0];

            borderPaint.setStyle(Paint.Style.STROKE); borderPaint.setStrokeWidth(3f); borderPaint.setColor(Color.argb(50, 0, 0, 0));
            indicatorPaint.setStyle(Paint.Style.STROKE); indicatorPaint.setStrokeWidth(5f); indicatorPaint.setColor(Color.WHITE);
            indicatorShadowPaint.setStyle(Paint.Style.STROKE); indicatorShadowPaint.setStrokeWidth(2f); indicatorShadowPaint.setColor(Color.BLACK);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float radius = h / 2f;

            int[] rainbowColors = new int[]{
                    Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
            };
            android.graphics.LinearGradient gradient = new android.graphics.LinearGradient(
                    0, 0, w, 0, rainbowColors, null, android.graphics.Shader.TileMode.CLAMP
            );
            barPaint.setShader(gradient);

            android.graphics.RectF rect = new android.graphics.RectF(0, 0, w, h);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
            canvas.drawRoundRect(rect, radius, radius, borderPaint);

            float cx = (currentHue / 360f) * w;
            cx = Math.max(radius, Math.min(w - radius, cx));

            canvas.drawCircle(cx, h / 2f, radius - 4f, indicatorShadowPaint);
            canvas.drawCircle(cx, h / 2f, radius - 4f, indicatorPaint);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float x = Math.max(0f, Math.min(event.getX(), getWidth()));
                currentHue = (x / getWidth()) * 360f;
                invalidate();
                int color = Color.HSVToColor(new float[]{currentHue, 1f, 1f});
                if (listener != null) listener.onHueChanged(color);
                return true;
            }
            return super.onTouchEvent(event);
        }

        void setColor(int color) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            currentHue = hsv[0];
            invalidate();
        }
    }

    private static class ColorBoxView extends View {
        private final Paint spectrumPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint indicatorShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv = {0f, 1f, 1f};
        private final OnColorChangedListener listener;
        private Bitmap spectrumBitmap;
        private int bitmapWidth = -1;
        private int bitmapHeight = -1;

        interface OnColorChangedListener {
            void onColorChanged(int color);
        }

        ColorBoxView(Context context, int initialColor, OnColorChangedListener l) {
            super(context);
            this.listener = l;
            Color.colorToHSV(initialColor, hsv);

            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setStrokeWidth(5f);
            indicatorPaint.setColor(Color.WHITE);

            indicatorShadowPaint.setStyle(Paint.Style.STROKE);
            indicatorShadowPaint.setStrokeWidth(2f);
            indicatorShadowPaint.setColor(Color.BLACK);
        }

        int getCurrentColor() {
            return Color.HSVToColor(hsv);
        }

        private void rebuildBitmap(int width, int height) {
            if (width <= 0 || height <= 0) return;
            if (spectrumBitmap != null && bitmapWidth == width && bitmapHeight == height) return;

            if (spectrumBitmap != null && !spectrumBitmap.isRecycled()) {
                spectrumBitmap.recycle();
            }

            int[] pixels = new int[width * height];
            float[] tempHsv = new float[]{hsv[0], 0f, 1f};

            for (int y = 0; y < height; y++) {
                float value = height <= 1 ? 1f : 1f - (y / (float) (height - 1));
                tempHsv[2] = value;
                for (int x = 0; x < width; x++) {
                    float saturation = width <= 1 ? 0f : x / (float) (width - 1);
                    tempHsv[1] = saturation;
                    pixels[y * width + x] = Color.HSVToColor(tempHsv);
                }
            }

            spectrumBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            spectrumBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            bitmapWidth = width;
            bitmapHeight = height;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            rebuildBitmap(w, h);

            float radius = 24f;
            Path clipPath = new Path();
            android.graphics.RectF rect = new android.graphics.RectF(0f, 0f, w, h);
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(clipPath);
            if (spectrumBitmap != null) {
                canvas.drawBitmap(spectrumBitmap, 0f, 0f, spectrumPaint);
            }
            canvas.restoreToCount(save);

            float px = hsv[1] * (w - 1);
            float py = (1f - hsv[2]) * (h - 1);

            px = Math.max(0f, Math.min(w, px));
            py = Math.max(0f, Math.min(h, py));
            float ringRadius = Math.min(14f, Math.min(w, h) / 5f);
            canvas.drawCircle(px, py, ringRadius, indicatorShadowPaint);
            canvas.drawCircle(px, py, ringRadius, indicatorPaint);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE
                    || action == MotionEvent.ACTION_UP) {
                float w = getWidth();
                float h = getHeight();
                if (w <= 0f || h <= 0f) return true;

                float x = Math.max(0f, Math.min(event.getX(), w - 1f));
                float y = Math.max(0f, Math.min(event.getY(), h - 1f));

                hsv[1] = x / (w - 1f);
                hsv[2] = 1f - (y / (h - 1f));

                invalidate();
                if (listener != null) listener.onColorChanged(getCurrentColor());
                return true;
            }
            return super.onTouchEvent(event);
        }

        void setHue(int color) {
            float[] tempHsv = new float[3];
            Color.colorToHSV(color, tempHsv);
            hsv[0] = tempHsv[0];
            bitmapWidth = -1; // force bitmap rebuild with new hue
            invalidate();
        }

        void setColor(int color) {
            Color.colorToHSV(color, hsv);
            bitmapWidth = -1;
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (spectrumBitmap != null && !spectrumBitmap.isRecycled()) {
                spectrumBitmap.recycle();
                spectrumBitmap = null;
                bitmapWidth = -1;
                bitmapHeight = -1;
            }
            super.onDetachedFromWindow();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        String serialized = slateCanvas.serializeStrokes();
        memoryDraftBackup = serialized;
        if (serialized.length() < 90000) outState.putString("rot_draft", serialized);
        outState.putFloat("rot_scale", slateCanvas.getScaleFactor());
        outState.putFloat("rot_tx", slateCanvas.getTranslateX());
        outState.putFloat("rot_ty", slateCanvas.getTranslateY());
        outState.putInt("rot_mode", slateCanvas.getCurrentMode());
        outState.putBoolean("rot_zoom", isZoomEnabled);
        outState.putBoolean("rot_inf", isFieldZoomEnabled);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        String draft = savedInstanceState.getString("rot_draft", memoryDraftBackup);
        slateCanvas.loadStrokes(draft);
        slateCanvas.setCameraState(savedInstanceState.getFloat("rot_scale", 1f), savedInstanceState.getFloat("rot_tx", 0f), savedInstanceState.getFloat("rot_ty", 0f));
        slateCanvas.setToolMode(savedInstanceState.getInt("rot_mode", SlateCanvas.MODE_PEN));
        isZoomEnabled = savedInstanceState.getBoolean("rot_zoom", true);
        isFieldZoomEnabled = savedInstanceState.getBoolean("rot_inf", false);
        slateCanvas.setZoomEnabled(isZoomEnabled); slateCanvas.setFieldZoomEnabled(isFieldZoomEnabled);

        updateMenuTheme();
        updateToolIndicators(findViewById(R.id.menuPen), findViewById(R.id.menuEraser));
        Button mz = findViewById(R.id.menuZoom); mz.setText(isZoomEnabled ? "Zoom: ON" : "Zoom: OFF"); applyPillBackground(mz, Color.parseColor(isZoomEnabled ? "#34C759" : "#FF3B30"));
        Button mfz = findViewById(R.id.menuFieldZoom); mfz.setText(isFieldZoomEnabled ? "Field Zoom: ON" : "Field Zoom: OFF"); applyPillBackground(mfz, Color.parseColor(isFieldZoomEnabled ? "#34C759" : "#FF3B30"));
    }

    private void updateToolIndicators(Button penBtn, Button eraserBtn) {
        int mode = slateCanvas.getCurrentMode();
        penBtn.setText(Html.fromHtml("Pen tool" + ((mode == SlateCanvas.MODE_PEN) ? indicatorOn : indicatorOff), Html.FROM_HTML_MODE_LEGACY));
        eraserBtn.setText(Html.fromHtml("Eraser tool" + ((mode == SlateCanvas.MODE_ERASER) ? indicatorOn : indicatorOff), Html.FROM_HTML_MODE_LEGACY));
    }

    private void toggleMenu(boolean open, TextView btn) {
        if (isMenuOpen == open) return; isMenuOpen = open;
        View container = (flowerMenuScroll != null) ? flowerMenuScroll : flowerMenuLayout;

        btn.animate().cancel();
        btn.animate().scaleX(0.85f).scaleY(0.85f).setDuration(110).setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
            btn.setText(open ? "Close" : "Menu");
            btn.animate().scaleX(1.08f).scaleY(1.08f).setDuration(140).setInterpolator(new AccelerateDecelerateInterpolator()).withEndAction(() -> {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator(1.2f)).start();
            }).start();
        }).start();

        int childCount = flowerMenuLayout.getChildCount();
        if (open) {
            applyFlexSizingToMenu(); container.setVisibility(View.VISIBLE);
            for (int i = 0; i < childCount; i++) {
                View c = flowerMenuLayout.getChildAt(i); c.animate().cancel();
                c.setTranslationY(60f); c.setTranslationX(30f); c.setAlpha(0f); c.setScaleX(0.6f); c.setScaleY(0.6f);
                c.animate().translationY(0f).translationX(0f).alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
                        .setStartDelay((childCount - i - 1) * 25L).setInterpolator(new OvershootInterpolator(1.15f)).start();
            }
        } else {
            for (int i = 0; i < childCount; i++) {
                View c = flowerMenuLayout.getChildAt(i); c.animate().cancel();
                final boolean isLast = (i == childCount - 1);
                c.animate().translationY(60f).translationX(30f).alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(220)
                        .setStartDelay(i * 18L).setInterpolator(new AnticipateInterpolator(1.1f))
                        .withEndAction(() -> { if (isLast && !isMenuOpen) container.setVisibility(View.GONE); }).start();
            }
        }
    }

    private void updateMenuTheme() {
        int btnColor = isDarkTheme ? Color.parseColor("#2C2C2E") : Color.parseColor("#4A90E2");
        int textColor = Color.WHITE;
        int[] btns = {R.id.menuPen, R.id.menuEraser, R.id.menuUndo, R.id.menuColor, R.id.menuClear, R.id.menuSave, R.id.menuDrafts, R.id.menuTheme, R.id.menuExit};
        for (int id : btns) { Button b = findViewById(id); applyPillBackground(b, btnColor); b.setTextColor(textColor); }
        ((Button) findViewById(R.id.menuZoom)).setTextColor(textColor);
        ((Button) findViewById(R.id.menuFieldZoom)).setTextColor(textColor);
    }

    private void saveImageToFolder(String name) {
        try {
            Bitmap bmp = slateCanvas.getBitmap(); if (bmp == null) return;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/OWN's Slate Drafts");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) { OutputStream out = getContentResolver().openOutputStream(uri); if (out != null) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); out.close(); } }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteImageFromFolder(String name) {
        try {
            ContentResolver r = getContentResolver(); Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Cursor c = r.query(uri, new String[]{MediaStore.Images.Media._ID}, MediaStore.Images.Media.DISPLAY_NAME + "=?", new String[]{name + ".png"}, null);
            if (c != null) { while (c.moveToNext()) { r.delete(ContentUris.withAppendedId(uri, c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))), null, null); } c.close(); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showExitConfirmationDialog() {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.setOnDismissListener(dialog -> hideSystemBars());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#F21C1C1E") : Color.parseColor("#F2F2F2F7")); gd.setCornerRadius(dpToPx(24)); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Exit Slate?"); title.setTextSize(20f); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView msg = new TextView(this); msg.setText("Are you sure you want to leave? Unsaved progress will be lost."); msg.setTextColor(isDarkTheme ? Color.parseColor("#CCCCCC") : Color.parseColor("#555555")); msg.setPadding(0, dpToPx(10), 0, dpToPx(20)); root.addView(msg);
        LinearLayout btnLayout = new LinearLayout(this); btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnNo = new Button(this); btnNo.setText("No"); btnNo.setAllCaps(false); applyPillBackground(btnNo, Color.GRAY); btnNo.setTextColor(Color.WHITE); btnNo.setOnClickListener(v -> d.dismiss());
        Button btnYes = new Button(this); btnYes.setText("Yes"); btnYes.setAllCaps(false); applyPillBackground(btnYes, Color.parseColor("#FF3B30")); btnYes.setTextColor(Color.WHITE); btnYes.setOnClickListener(v -> { d.dismiss(); finish(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dpToPx(44), 1f); p.setMargins(dpToPx(6), 0, dpToPx(6), 0); btnLayout.addView(btnNo, p); btnLayout.addView(btnYes, p); root.addView(btnLayout); d.setContentView(root); d.show();
    }

    private void showSaveDraftDialog() {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.setOnDismissListener(dialog -> hideSystemBars());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#F21C1C1E") : Color.parseColor("#F2F2F2F7")); gd.setCornerRadius(dpToPx(24)); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Save Vector Draft"); title.setTextSize(20f); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        EditText input = new EditText(this); input.setHint("Enter draft name..."); input.setHintTextColor(Color.GRAY); input.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
        GradientDrawable inputBg = new GradientDrawable(); inputBg.setColor(isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")); inputBg.setCornerRadius(dpToPx(12)); input.setBackground(inputBg); input.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); ip.setMargins(0, dpToPx(14), 0, dpToPx(14)); root.addView(input, ip);
        LinearLayout btnLayout = new LinearLayout(this); btnLayout.setOrientation(LinearLayout.HORIZONTAL);
        Button btnCancel = new Button(this); btnCancel.setText("Cancel"); btnCancel.setAllCaps(false); applyPillBackground(btnCancel, Color.GRAY); btnCancel.setTextColor(Color.WHITE); btnCancel.setOnClickListener(v -> d.dismiss());
        Button btnSave = new Button(this); btnSave.setText("Save"); btnSave.setAllCaps(false); applyPillBackground(btnSave, Color.parseColor("#4A90E2")); btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> {
            String n = input.getText().toString().trim(); if (n.isEmpty()) n = "Draft " + System.currentTimeMillis();
            prefs.edit().putString("slate_draft_" + n, slateCanvas.serializeStrokes()).apply();
            Set<String> s = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>())); s.add(n);
            prefs.edit().putStringSet("slate_draft_names", s).apply(); saveImageToFolder(n);
            Toast.makeText(this, "Saved to App & Gallery!", Toast.LENGTH_SHORT).show(); d.dismiss();
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dpToPx(44), 1f); p.setMargins(dpToPx(6), 0, dpToPx(6), 0); btnLayout.addView(btnCancel, p); btnLayout.addView(btnSave, p); root.addView(btnLayout); d.setContentView(root); d.show();
    }

    private void showDraftsGalleryDialog() {
        Dialog d = new Dialog(this); d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.setOnDismissListener(dialog -> hideSystemBars());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        GradientDrawable gd = new GradientDrawable(); gd.setColor(isDarkTheme ? Color.parseColor("#F21C1C1E") : Color.parseColor("#F2F2F2F7")); gd.setCornerRadius(dpToPx(24)); root.setBackground(gd);
        TextView title = new TextView(this); title.setText("Drafts Gallery"); title.setTextSize(20f); title.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, 0, 0, dpToPx(16)); root.addView(title);
        ScrollView sv = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        Set<String> names = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>()));
        if (names.isEmpty()) { TextView empty = new TextView(this); empty.setText("No drafts saved."); empty.setTextColor(Color.GRAY); list.addView(empty); }
        else {
            for (String n : names) {
                LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); rowLp.setMargins(0, 0, 0, dpToPx(10));
                Button b = new Button(this); b.setText(n); b.setAllCaps(false); b.setSingleLine(true); applyPillBackground(b, isDarkTheme ? Color.parseColor("#3A3A3C") : Color.parseColor("#FFFFFF")); b.setTextColor(isDarkTheme ? Color.WHITE : Color.BLACK);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dpToPx(44), 1f);
                b.setOnClickListener(v -> { slateCanvas.loadStrokes(prefs.getString("slate_draft_" + n, "")); Toast.makeText(this, "Loaded " + n, Toast.LENGTH_SHORT).show(); d.dismiss(); });
                Button del = new Button(this); del.setText("X"); del.setAllCaps(false); applyPillBackground(del, Color.parseColor("#FF3B30")); del.setTextColor(Color.WHITE);
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(44)); dLp.setMargins(dpToPx(8), 0, 0, 0);
                del.setOnClickListener(v -> {
                    prefs.edit().remove("slate_draft_" + n).apply();
                    Set<String> updated = new HashSet<>(prefs.getStringSet("slate_draft_names", new HashSet<>())); updated.remove(n);
                    prefs.edit().putStringSet("slate_draft_names", updated).apply(); list.removeView(row); deleteImageFromFolder(n);
                    if (updated.isEmpty()) { TextView empty = new TextView(this); empty.setText("No drafts saved."); empty.setTextColor(Color.GRAY); list.addView(empty); }
                    Toast.makeText(this, "Draft Deleted", Toast.LENGTH_SHORT).show();
                });
                row.addView(b, lp); row.addView(del, dLp); list.addView(row, rowLp);
            }
        }
        int maxListH = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
        sv.addView(list); root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.post(() -> { if (sv.getHeight() > maxListH) { sv.getLayoutParams().height = maxListH; sv.requestLayout(); } });
        Button btnClose = new Button(this); btnClose.setText("Close"); btnClose.setAllCaps(false); applyPillBackground(btnClose, Color.parseColor("#FF3B30")); btnClose.setTextColor(Color.WHITE); btnClose.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)); clp.setMargins(0, dpToPx(16), 0, 0); root.addView(btnClose, clp); d.setContentView(root); d.show();
    }

    @Override protected void onPause() { super.onPause(); if (slateCanvas != null) slateCanvas.pauseRendering(); }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); if (slateCanvas != null) slateCanvas.resumeRendering(); }

    private static class SlateCanvas extends SurfaceView implements SurfaceHolder.Callback, Runnable {
        public static final int MODE_PEN = 0; public static final int MODE_ERASER = 1; private int currentMode = MODE_PEN;
        private Thread renderThread; private volatile boolean isRendering = false; private final SurfaceHolder holder;
        private boolean darkTheme, isZoomEnabled = true, isFieldZoomEnabled = false, isMultiTouchActive = false;
        private int bgColor, gridColor, penColor;
        private final Matrix tMatrix = new Matrix(), iMatrix = new Matrix();
        private float scaleFactor = 1.0f, translateX = 0f, translateY = 0f, lastTouchX, lastTouchY;
        private final ScaleGestureDetector scaleDetector;
        private final List<Stroke> strokes = new ArrayList<>(); private Stroke currentStroke; private Paint currentPaint;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Object paintLock = new Object();
        private float previousX, previousY; private static final float TOUCH_TOLERANCE = 1f;

        public SlateCanvas(Context context, boolean isDarkTheme) {
            super(context); holder = getHolder(); holder.addCallback(this);
            setZOrderOnTop(false); holder.setFormat(PixelFormat.OPAQUE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setForceDarkAllowed(false);
            setDarkTheme(isDarkTheme); scaleDetector = new ScaleGestureDetector(context, new ScaleListener()); setupNewPaint();
        }

        public void setZoomEnabled(boolean e) { this.isZoomEnabled = e; }
        public float getScaleFactor() { return scaleFactor; }
        public float getTranslateX() { return translateX; }
        public float getTranslateY() { return translateY; }
        public void setCameraState(float s, float tx, float ty) { this.scaleFactor = Math.max(0.001f, s); this.translateX = tx; this.translateY = ty; updateMatrix(); }

        public void setFieldZoomEnabled(boolean e) {
            this.isFieldZoomEnabled = e;
            if (!e) {
                boolean changed = false;
                if (scaleFactor < 0.5f) { scaleFactor = 0.5f; changed = true; }
                if (scaleFactor > 5.0f) { scaleFactor = 5.0f; changed = true; }
                if (changed) updateMatrix();
            }
        }

        public void setDarkTheme(boolean dark) {
            this.darkTheme = dark; bgColor = dark ? Color.parseColor("#1C1C1E") : Color.parseColor("#FFFFFF");
            gridColor = dark ? Color.parseColor("#333333") : Color.parseColor("#E0E0E0");
            penColor = dark ? Color.WHITE : Color.BLACK;
            gridPaint.setColor(gridColor); setupNewPaint();
        }

        public void setPenColor(int color) {
            this.penColor = color;
            if (currentMode == MODE_PEN) {
                currentPaint.setColor(penColor);
            }
        }

        public int getCurrentPenColor() { return this.penColor; }

        public void setToolMode(int mode) { this.currentMode = mode; setupNewPaint(); }
        public int getCurrentMode() { return this.currentMode; }
        public void clearCanvas() { synchronized (paintLock) { strokes.clear(); currentStroke = null; } }
        public void undo() { synchronized (paintLock) { if (!strokes.isEmpty()) strokes.remove(strokes.size() - 1); } }

        private void setupNewPaint() {
            currentPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            currentPaint.setStyle(Paint.Style.STROKE); currentPaint.setStrokeJoin(Paint.Join.ROUND); currentPaint.setStrokeCap(Paint.Cap.ROUND);
            if (currentMode == MODE_PEN) { currentPaint.setColor(penColor); currentPaint.setStrokeWidth(6f); currentPaint.setXfermode(null); }
            else { currentPaint.setColor(Color.TRANSPARENT); currentPaint.setStrokeWidth(40f); currentPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR)); }
        }

        public String serializeStrokes() {
            StringBuilder sb = new StringBuilder();
            synchronized (paintLock) {
                for (Stroke s : strokes) {
                    sb.append(s.isEraser ? "1" : "0").append(":").append(s.color).append(":").append(s.strokeWidth).append(":");
                    for (int i = 0; i < s.points.size(); i++) { sb.append(s.points.get(i).x).append(",").append(s.points.get(i).y); if (i < s.points.size() - 1) sb.append(";"); }
                    sb.append("|");
                }
            }
            return sb.toString();
        }

        public void loadStrokes(String data) {
            synchronized (paintLock) {
                strokes.clear(); if (data == null || data.isEmpty()) return; String[] raw = data.split("\\|");
                for (String r : raw) {
                    if (r.isEmpty()) continue; String[] p = r.split(":"); if (p.length < 4) continue;
                    try {
                        boolean isEraser = p[0].equals("1");
                        int col = Integer.parseInt(p[1]);
                        Stroke s = new Stroke(isEraser, isEraser ? Color.TRANSPARENT : col, Float.parseFloat(p[2]));
                        for (String pt : p[3].split(";")) { String[] c = pt.split(","); if (c.length == 2) s.points.add(new PointF(Float.parseFloat(c[0]), Float.parseFloat(c[1]))); }
                        s.rebuildPath(); strokes.add(s);
                    } catch (Exception ignored) {}
                }
            }
        }

        public Bitmap getBitmap() {
            int w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return null;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(bmp);
            drawScene(c, w, h); return bmp;
        }

        public void pauseRendering() {
            isRendering = false;
            if (renderThread != null) { try { renderThread.join(300); } catch (Exception ignored) {} renderThread = null; }
        }

        public void resumeRendering() {
            if (isRendering && renderThread != null && renderThread.isAlive()) return;
            isRendering = true; renderThread = new Thread(this, "SlateRenderThread"); renderThread.start();
        }

        @Override public void surfaceCreated(@NonNull SurfaceHolder h) { resumeRendering(); }
        @Override public void surfaceChanged(@NonNull SurfaceHolder h, int f, int w, int ht) { updateMatrix(); }
        @Override public void surfaceDestroyed(@NonNull SurfaceHolder h) { pauseRendering(); }

        @Override
        public void run() {
            while (isRendering) {
                long start = System.currentTimeMillis();
                if (holder.getSurface().isValid()) {
                    Canvas c = null;
                    try {
                        c = holder.lockCanvas();
                        if (c != null) { synchronized (holder) { drawScene(c, c.getWidth(), c.getHeight()); } }
                    } catch (Exception ignored) {
                    } finally {
                        if (c != null) { try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) {} }
                    }
                }
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 16) { try { Thread.sleep(16 - elapsed); } catch (InterruptedException ignored) {} }
            }
        }

        private void drawScene(Canvas c, int w, int h) {
            if (w <= 0 || h <= 0) return;
            c.drawColor(bgColor);
            float safeScale = Math.max(0.001f, scaleFactor);
            float currentScale = isFieldZoomEnabled ? safeScale : 1.0f;

            c.save(); c.concat(tMatrix);
            float sp = isFieldZoomEnabled ? (50f / safeScale) : 50f;
            while (sp * safeScale < 25f) sp *= 2f;
            float dotRadius = isFieldZoomEnabled ? (2f / safeScale) : 2f;
            if (dotRadius * safeScale < 0.75f) dotRadius = 0.75f / safeScale;

            float[] tl = {0, 0}, br = {w, h}; iMatrix.mapPoints(tl); iMatrix.mapPoints(br);
            float minX = Math.min(tl[0], br[0]), maxX = Math.max(tl[0], br[0]);
            float minY = Math.min(tl[1], br[1]), maxY = Math.max(tl[1], br[1]);
            while ((maxX - minX) / sp > 120f || (maxY - minY) / sp > 120f) sp *= 2f;

            float sx = minX - (minX % sp), sy = minY - (minY % sp);
            for (float x = sx; x < maxX; x += sp) { for (float y = sy; y < maxY; y += sp) { c.drawCircle(x, y, dotRadius, gridPaint); } }
            c.restore();

            int layer = c.saveLayer(0, 0, w, h, null);
            c.concat(tMatrix);
            synchronized (paintLock) {
                for (Stroke s : strokes) { s.paint.setStrokeWidth(s.strokeWidth / currentScale); c.drawPath(s.path, s.paint); }
                if (currentStroke != null) { currentPaint.setStrokeWidth(currentStroke.strokeWidth / currentScale); c.drawPath(currentStroke.path, currentPaint); }
            }
            c.restoreToCount(layer);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int action = e.getActionMasked();
            if (e.getPointerCount() > 1) {
                isMultiTouchActive = true;
                synchronized (paintLock) { currentStroke = null; }
                if (!isZoomEnabled) return true;
                scaleDetector.onTouchEvent(e);
                if (action == MotionEvent.ACTION_MOVE) {
                    float fx = scaleDetector.getFocusX(), fy = scaleDetector.getFocusY();
                    translateX += (fx - lastTouchX); translateY += (fy - lastTouchY);
                    updateMatrix(); lastTouchX = fx; lastTouchY = fy;
                } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                    lastTouchX = scaleDetector.getFocusX(); lastTouchY = scaleDetector.getFocusY();
                }
                return true;
            }

            if (isMultiTouchActive) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) isMultiTouchActive = false;
                return true;
            }

            float[] pts = {e.getX(), e.getY()}; iMatrix.mapPoints(pts); float cx = pts[0], cy = pts[1];
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    synchronized (paintLock) {
                        float baseWidth = (currentMode == MODE_PEN) ? 6f : 40f;
                        currentStroke = new Stroke(currentMode == MODE_ERASER, currentPaint.getColor(), baseWidth);
                        currentStroke.points.add(new PointF(cx, cy)); currentStroke.path.moveTo(cx, cy);
                        previousX = cx; previousY = cy;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (currentStroke == null) break;
                    synchronized (paintLock) {
                        for (int i = 0; i < e.getHistorySize(); i++) {
                            float[] hPts = {e.getHistoricalX(i), e.getHistoricalY(i)}; iMatrix.mapPoints(hPts);
                            processTouch(hPts[0], hPts[1]);
                        }
                        processTouch(cx, cy);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    synchronized (paintLock) {
                        if (currentStroke != null) {
                            currentStroke.path.lineTo(cx, cy); currentStroke.points.add(new PointF(cx, cy));
                            strokes.add(currentStroke); currentStroke = null;
                        }
                    }
                    break;
            }
            return true;
        }

        private void processTouch(float x, float y) {
            if (Math.abs(x - previousX) >= TOUCH_TOLERANCE || Math.abs(y - previousY) >= TOUCH_TOLERANCE) {
                currentStroke.points.add(new PointF(x, y));
                currentStroke.path.quadTo(previousX, previousY, (x + previousX) / 2f, (y + previousY) / 2f);
                previousX = x; previousY = y;
            }
        }

        private void updateMatrix() { tMatrix.reset(); tMatrix.postScale(scaleFactor, scaleFactor); tMatrix.postTranslate(translateX, translateY); tMatrix.invert(iMatrix); }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float prevScale = scaleFactor;
                float minZoom = isFieldZoomEnabled ? 0.001f : 0.5f;
                float maxZoom = 5.0f;
                scaleFactor = Math.max(minZoom, Math.min(scaleFactor * d.getScaleFactor(), maxZoom));

                float adjusted = scaleFactor / prevScale; float fx = d.getFocusX(), fy = d.getFocusY();
                translateX = fx - (fx - translateX) * adjusted; translateY = fy - (fy - translateY) * adjusted;
                updateMatrix(); return true;
            }
        }

        private static class Stroke {
            List<PointF> points = new ArrayList<>(); boolean isEraser; int color; float strokeWidth; Path path = new Path(); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
            Stroke(boolean e, int c, float w) {
                isEraser = e; color = c; strokeWidth = w;
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setColor(c); paint.setStrokeWidth(w);
                if (isEraser) paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            }
            void rebuildPath() {
                path.reset(); if (points.isEmpty()) return;
                float px = points.get(0).x, py = points.get(0).y; path.moveTo(px, py);
                for (int i = 1; i < points.size(); i++) { PointF p = points.get(i); path.quadTo(px, py, (p.x + px) / 2f, (p.y + py) / 2f); px = p.x; py = p.y; }
                path.lineTo(px, py);
            }
        }
    }
}