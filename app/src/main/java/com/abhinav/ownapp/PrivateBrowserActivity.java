package com.abhinav.ownapp;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.TextUtils;
import android.transition.TransitionManager;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("all")
public class PrivateBrowserActivity extends AppCompatActivity {

    private FrameLayout webViewContainer;
    private RelativeLayout urlInputContainer;
    private LinearLayout searchCapsule;
    private EditText etSearchUrl;
    private ProgressBar progressBar;

    private boolean isDarkTheme;
    private int themeState;

    private ImageView btnFront, btnMenu, ivAutoScrollIcon, btnDismissSearch;
    private SearchCubeView btnGo;
    private ImageView btnVideoPlayPause, btnVideoHide, btnVideoMute;
    private FrameLayout btnAutoScroll;
    private ProgressBar autoActionIndicator;
    private ImageView btnFullscreenToggle;

    private FrameLayout btnManageTabs;
    private View tabBoxOutline;
    private TextView tvTabCount;

    private boolean isFullscreen = false;
    private boolean isVideoMode = false;
    private boolean isVideoCapsuleHidden = false;

    private boolean isVideoCurrentlyPlaying = false;
    private boolean isVideoCurrentlyMuted = false;

    private LinearLayout tabsOverlay;
    private GridLayout tabsGrid;
    private LinearLayout downloadsOverlay, downloadsList;

    private SharedPreferences prefs, browserPrefs;

    // File chooser support for gallery/image uploads (e.g., Instagram)
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    // WebRTC Permissions (Camera & Mic) support
    private ActivityResultLauncher<String[]> permissionLauncher;
    private PermissionRequest mPendingPermissionRequest;

    private static class TabInfo { WebView webView; Bitmap preview; String title = "New Tab"; boolean isPinned = false; String savedStateBase64 = null; }
    private final List<TabInfo> tabs = new ArrayList<>();
    private int currentTabIndex = 0;
    private String defaultUserAgent = null;
    private final String[] blockedDomains = {"google-analytics.com", "doubleclick.net", "facebook.net", "facebook.com/tr/", "scorecardresearch.com", "googlesyndication.com"};

    private View mCustomView;
    private WebChromeClient.CustomViewCallback mCustomViewCallback;
    private FrameLayout mFullscreenContainer;
    private int mOriginalSystemUiVisibility, mOriginalOrientation;

    private boolean isMenuOpen = false;

    private LinearLayout homeOverlay;
    private GridLayout homeShortcutList;
    private static final String BROWSER_PREFS = "private_browser_shortcuts";
    private static final String PREF_CUSTOM_LINKS = "custom_links_json";
    private static final String PREF_SAVED_SESSION = "saved_browser_session";

    private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private int currentAutoScrollSpeed = 0;
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override public void run() {
            WebView current = getCurrentWeb();
            if (current != null && currentAutoScrollSpeed > 0) {
                current.scrollBy(0, currentAutoScrollSpeed);
                autoScrollHandler.postDelayed(this, 16);
            }
        }
    };

    private boolean isAutoSwiping = false;
    private final Handler autoSwipeHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoSwipeRunnable = new Runnable() {
        @Override public void run() {
            if (isAutoSwiping) {
                simulateSwipeUp();
                autoSwipeHandler.postDelayed(this, 4000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setContentView(R.layout.activity_private_browser);

        // Native Permission Launcher for Voice Messages and Video/Audio calls
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (mPendingPermissionRequest != null) {
                List<String> grantedResources = new ArrayList<>();
                for (String resource : mPendingPermissionRequest.getResources()) {
                    if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE) && Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
                        grantedResources.add(resource);
                    } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE) && Boolean.TRUE.equals(result.get(Manifest.permission.RECORD_AUDIO))) {
                        grantedResources.add(resource);
                    }
                }
                if (!grantedResources.isEmpty()) {
                    mPendingPermissionRequest.grant(grantedResources.toArray(new String[0]));
                } else {
                    mPendingPermissionRequest.deny();
                }
                mPendingPermissionRequest = null;
            }
        });

        // FIXED: Re-added robust default parseResult to support standard Android files/cameras with manual extraction fallback
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        results = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
                        if (results == null) {
                            Intent data = result.getData();
                            if (data.getDataString() != null) {
                                results = new Uri[]{Uri.parse(data.getDataString())};
                            } else if (data.getClipData() != null) {
                                int count = data.getClipData().getItemCount();
                                results = new Uri[count];
                                for (int i = 0; i < count; i++) {
                                    results[i] = data.getClipData().getItemAt(i).getUri();
                                }
                            }
                        }
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
        );

        View rootLayout = findViewById(R.id.browserRoot);
        rootLayout.setAlpha(0f);
        rootLayout.animate().alpha(1f).setDuration(400).setInterpolator(new AccelerateDecelerateInterpolator()).start();

        try { StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().build()); } catch (Exception ignored) {}

        prefs = getSharedPreferences("SnakeWidgetPrefs", MODE_PRIVATE);
        browserPrefs = getSharedPreferences(BROWSER_PREFS, MODE_PRIVATE);
        themeState = prefs.getInt("app_theme_state", -1);
        if (themeState == -1) themeState = prefs.getBoolean("is_dark_theme", true) ? 1 : 0;
        isDarkTheme = (themeState != 0);

        webViewContainer = findViewById(R.id.webViewContainer);
        searchCapsule = findViewById(R.id.searchCapsule);

        searchCapsule.setLayoutTransition(null);

        urlInputContainer = findViewById(R.id.urlInputContainer);
        etSearchUrl = findViewById(R.id.etSearchUrl);
        progressBar = findViewById(R.id.browserProgressBar);

        btnFront = findViewById(R.id.btnBrowserFront);
        btnGo = findViewById(R.id.btnBrowserGo); // Our custom view
        btnMenu = findViewById(R.id.btnBrowserMenu);
        btnAutoScroll = findViewById(R.id.btnAutoScroll);
        ivAutoScrollIcon = findViewById(R.id.ivAutoScrollIcon);
        autoActionIndicator = findViewById(R.id.autoActionIndicator);
        btnFullscreenToggle = findViewById(R.id.btnFullscreenToggle);
        btnDismissSearch = findViewById(R.id.btnDismissSearch);

        btnVideoPlayPause = findViewById(R.id.btnVideoPlayPause);
        btnVideoHide = findViewById(R.id.btnVideoHide);
        btnVideoMute = findViewById(R.id.btnVideoMute);

        btnManageTabs = findViewById(R.id.btnManageTabs);
        tabBoxOutline = findViewById(R.id.tabBoxOutline);
        tvTabCount = findViewById(R.id.tvTabCount);

        tabsOverlay = findViewById(R.id.tabsOverlay);
        tabsGrid = findViewById(R.id.tabsGrid);
        homeOverlay = findViewById(R.id.homeOverlay);
        homeShortcutList = findViewById(R.id.homeShortcutList);

        injectAnimatedFanHeader();
        setupScrollingLandscape();

        findViewById(R.id.btnCloseTabsOverlay).setOnClickListener(v -> {
            if (tabs.isEmpty()) {
                createNewTab(null, false, true);
            } else {
                if (currentTabIndex >= tabs.size()) currentTabIndex = Math.max(0, tabs.size() - 1);
                if (currentTabIndex < 0) currentTabIndex = 0;
                switchTab(currentTabIndex);
            }
        });

        findViewById(R.id.btnAddNewTab).setOnClickListener(v -> { tabsOverlay.setVisibility(View.GONE); updateBackgroundBlur(); createNewTab(null, false, true); });

        downloadsOverlay = findViewById(R.id.downloadsOverlay);
        downloadsList = findViewById(R.id.downloadsList);
        findViewById(R.id.btnCloseDownloadsOverlay).setOnClickListener(v -> { downloadsOverlay.setVisibility(View.GONE); updateBackgroundBlur(); });

        applyTheme();
        renderHomeShortcuts();
        setupModernBackGesture();

        etSearchUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if(isVideoMode) return;

            searchCapsule.animate().translationX(0f).setDuration(350).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            beginSmoothTransition();

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
            int margin24dp = dp(24); GradientDrawable gd = (GradientDrawable) searchCapsule.getBackground(); GradientDrawable urlGd = (GradientDrawable) urlInputContainer.getBackground();

            if (hasFocus) {
                params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params.addRule(RelativeLayout.ALIGN_PARENT_TOP); params.topMargin = margin24dp; params.bottomMargin = 0; etSearchUrl.setMaxLines(6);
                btnFront.setVisibility(View.GONE); btnMenu.setVisibility(View.GONE); btnAutoScroll.setVisibility(View.GONE); btnManageTabs.setVisibility(View.GONE); btnFullscreenToggle.setVisibility(View.GONE); btnDismissSearch.setVisibility(View.VISIBLE);
                animateCornerRadius(gd, dp(100), dp(24)); animateCornerRadius(urlGd, dp(100), dp(16));
                etSearchUrl.postDelayed(() -> { etSearchUrl.requestFocus(); InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.showSoftInput(etSearchUrl, InputMethodManager.SHOW_IMPLICIT); }, 300);
            } else {
                params.removeRule(RelativeLayout.ALIGN_PARENT_TOP); params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM); params.topMargin = 0; params.bottomMargin = margin24dp; etSearchUrl.setMaxLines(1);
                int vis = isFullscreen ? View.GONE : View.VISIBLE; btnFront.setVisibility(vis); urlInputContainer.setVisibility(vis); btnMenu.setVisibility(vis); btnAutoScroll.setVisibility(vis); btnManageTabs.setVisibility(vis); btnFullscreenToggle.setVisibility(View.VISIBLE); btnDismissSearch.setVisibility(View.GONE);
                animateCornerRadius(gd, dp(24), dp(100)); animateCornerRadius(urlGd, dp(16), dp(100));
            }
            searchCapsule.setLayoutParams(params);
        });

        btnDismissSearch.setOnClickListener(v -> { etSearchUrl.clearFocus(); InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(etSearchUrl.getWindowToken(), 0); });
        btnFront.setOnClickListener(v -> { WebView current = getCurrentWeb(); if (current != null && current.canGoForward()) { current.goForward(); } });
        btnGo.setOnClickListener(v -> loadUrlOrSearch());
        btnMenu.setOnClickListener(this::showRoundedMenu);
        btnAutoScroll.setOnClickListener(this::showAutoScrollMenu);
        btnManageTabs.setOnClickListener(v -> openVisualTabSwitcher());
        btnFullscreenToggle.setOnClickListener(v -> toggleFullscreenCapsule());

        btnVideoPlayPause.setOnClickListener(v -> { WebView w = getCurrentWeb(); if(w != null) w.evaluateJavascript("if(window.activeVideo) { if(window.activeVideo.paused) window.activeVideo.play(); else window.activeVideo.pause(); }", null); });
        btnVideoMute.setOnClickListener(v -> { WebView w = getCurrentWeb(); if(w != null) w.evaluateJavascript("if(window.activeVideo) window.activeVideo.muted = !window.activeVideo.muted;", null); });
        btnVideoHide.setOnClickListener(v -> { searchCapsule.animate().alpha(0f).setDuration(200).withEndAction(() -> { searchCapsule.setVisibility(View.GONE); isVideoCapsuleHidden = true; }).start(); });

        btnFullscreenToggle.setOnTouchListener(new View.OnTouchListener() {
            private float dX; private float startX; private boolean isDragging = false;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (!isFullscreen || isVideoMode) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: dX = searchCapsule.getX() - event.getRawX(); startX = event.getRawX(); isDragging = false; return true;
                    case MotionEvent.ACTION_MOVE: float newX = event.getRawX() + dX; int screenWidth = getResources().getDisplayMetrics().widthPixels; int margin = dp(16); if (newX < margin) newX = margin; if (newX > screenWidth - searchCapsule.getWidth() - margin) newX = screenWidth - searchCapsule.getWidth() - margin; searchCapsule.setX(newX); if (Math.abs(event.getRawX() - startX) > 10) isDragging = true; return true;
                    case MotionEvent.ACTION_UP: if (!isDragging) toggleFullscreenCapsule(); return true;
                } return false;
            }
        });

        etSearchUrl.setOnEditorActionListener((v, actionId, event) -> { if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { loadUrlOrSearch(); return true; } return false; });
        restoreSession();

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String externalUrl = intent.getData().toString(); createNewTab(externalUrl, false, true);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        View decorView = getWindow().getDecorView();
        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            ViewGroup contentView = findViewById(android.R.id.content);
            View rootLayout = findViewById(R.id.browserRoot);
            if (mCustomView != null) {
                if (contentView != null) contentView.setPadding(0, 0, 0, 0);
                if (rootLayout != null) rootLayout.setPadding(0, 0, 0, 0);
            } else {
                if (contentView != null) contentView.setPadding(0, 0, 0, 0);
                if (rootLayout != null) rootLayout.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(decorView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSession();
        for (TabInfo t : tabs) {
            if (t.webView != null) t.webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (TabInfo t : tabs) {
            if (t.webView != null) t.webView.onResume();
        }
    }

    private void beginSmoothTransition() {
        android.transition.TransitionSet transition = new android.transition.TransitionSet();
        transition.setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER);
        transition.addTransition(new android.transition.ChangeBounds());
        transition.addTransition(new android.transition.ChangeTransform());
        transition.addTransition(new android.transition.Fade());
        transition.setDuration(350);
        transition.setInterpolator(new AccelerateDecelerateInterpolator());
        ViewGroup parent = findViewById(R.id.browserRoot);
        if (parent != null) TransitionManager.beginDelayedTransition(parent, transition);
    }

    private void updateVideoCapsuleOrientation(int orientation) {
        if (!isVideoMode) return;
        beginSmoothTransition();

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            searchCapsule.setOrientation(LinearLayout.VERTICAL);
            params.removeRule(RelativeLayout.CENTER_HORIZONTAL);
            params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            btnFullscreenToggle.setVisibility(View.GONE);
        } else {
            searchCapsule.setOrientation(LinearLayout.HORIZONTAL);
            params.removeRule(RelativeLayout.CENTER_VERTICAL);
            params.removeRule(RelativeLayout.ALIGN_PARENT_END);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(RelativeLayout.CENTER_HORIZONTAL);
            btnFullscreenToggle.setVisibility(View.VISIBLE);
        }
        searchCapsule.setLayoutParams(params);
    }

    private void enableVideoMode(boolean isPlaying, boolean isMuted) {
        if(!isFullscreen) return;

        if(!isVideoMode) {
            beginSmoothTransition();
            searchCapsule.setTranslationX(0f);
            isVideoMode = true;
            btnFront.setVisibility(View.GONE); urlInputContainer.setVisibility(View.GONE); btnMenu.setVisibility(View.GONE); btnAutoScroll.setVisibility(View.GONE); btnManageTabs.setVisibility(View.GONE); btnDismissSearch.setVisibility(View.GONE);
            btnVideoPlayPause.setVisibility(View.VISIBLE); btnVideoHide.setVisibility(View.VISIBLE); btnVideoMute.setVisibility(View.VISIBLE);

            btnFullscreenToggle.setImageResource(R.drawable.ic_egg);

            updateVideoCapsuleOrientation(getResources().getConfiguration().orientation);
        }
        btnVideoPlayPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
        btnVideoMute.setImageResource(isMuted ? android.R.drawable.ic_lock_silent_mode : android.R.drawable.ic_lock_silent_mode_off);
        if(!isVideoCapsuleHidden) { searchCapsule.setVisibility(View.VISIBLE); searchCapsule.setAlpha(1f); }
    }

    private void disableVideoMode() {
        if(!isVideoMode) return;

        searchCapsule.animate().translationX(0f).setDuration(350).setInterpolator(new AccelerateDecelerateInterpolator()).start();

        beginSmoothTransition();
        isVideoMode = false; isVideoCapsuleHidden = false; searchCapsule.setVisibility(View.VISIBLE); searchCapsule.setAlpha(1f);

        btnVideoPlayPause.setVisibility(View.GONE); btnVideoHide.setVisibility(View.GONE); btnVideoMute.setVisibility(View.GONE);
        btnFront.setVisibility(View.GONE); urlInputContainer.setVisibility(View.GONE); btnMenu.setVisibility(View.GONE); btnAutoScroll.setVisibility(View.GONE); btnManageTabs.setVisibility(View.GONE);
        btnFullscreenToggle.setVisibility(View.VISIBLE);
        btnFullscreenToggle.setImageResource(R.drawable.ic_egg);

        searchCapsule.setOrientation(LinearLayout.HORIZONTAL);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
        params.removeRule(RelativeLayout.CENTER_HORIZONTAL);
        params.removeRule(RelativeLayout.CENTER_VERTICAL);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        params.addRule(RelativeLayout.ALIGN_PARENT_END);
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;

        searchCapsule.setLayoutParams(params);
    }

    private void toggleFullscreenCapsule() {
        etSearchUrl.clearFocus();

        boolean expanding = isFullscreen || isVideoMode;
        if (expanding) {
            searchCapsule.animate().translationX(0f).setDuration(350).setInterpolator(new AccelerateDecelerateInterpolator()).start();
        }

        beginSmoothTransition();

        if (isVideoMode) {
            isVideoMode = false;
            isVideoCapsuleHidden = false;
            isFullscreen = false;
        } else {
            isFullscreen = !isFullscreen;
        }

        if (isFullscreen && isVideoCurrentlyPlaying) {
            btnFullscreenToggle.setImageResource(R.drawable.ic_egg);
            enableVideoMode(true, isVideoCurrentlyMuted);
            return;
        }

        int visibility = isFullscreen ? View.GONE : View.VISIBLE;
        btnFront.setVisibility(visibility); urlInputContainer.setVisibility(visibility); btnMenu.setVisibility(visibility); btnAutoScroll.setVisibility(visibility); btnManageTabs.setVisibility(visibility);
        btnVideoPlayPause.setVisibility(View.GONE); btnVideoHide.setVisibility(View.GONE); btnVideoMute.setVisibility(View.GONE);
        btnFullscreenToggle.setVisibility(View.VISIBLE);

        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) searchCapsule.getLayoutParams();
        searchCapsule.setOrientation(LinearLayout.HORIZONTAL);
        params.removeRule(RelativeLayout.CENTER_HORIZONTAL);
        params.removeRule(RelativeLayout.CENTER_VERTICAL);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        if (isFullscreen) {
            btnFullscreenToggle.setImageResource(R.drawable.ic_egg);
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
        } else {
            btnFullscreenToggle.setImageResource(R.drawable.ic_eggalt);
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.removeRule(RelativeLayout.ALIGN_PARENT_END);
        }
        searchCapsule.setLayoutParams(params);
        searchCapsule.setAlpha(1f);
        searchCapsule.setVisibility(View.VISIBLE);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isVideoMode) {
            beginSmoothTransition();
            updateVideoCapsuleOrientation(newConfig.orientation);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) { String externalUrl = intent.getData().toString(); createNewTab(externalUrl, false, true); }
    }

    @Override public void onLowMemory() { super.onLowMemory(); for (TabInfo t : tabs) { if (t.webView != null) t.webView.clearCache(false); } }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if (level >= TRIM_MEMORY_MODERATE) { for (TabInfo t : tabs) { if (t.webView != null) t.webView.clearCache(false); } } }

    private String encodeWebViewState(WebView webView) {
        try {
            Bundle bundle = new Bundle();
            WebBackForwardList list = webView.saveState(bundle);
            if (list != null && list.getSize() > 0) {
                Parcel parcel = Parcel.obtain();
                bundle.writeToParcel(parcel, 0);
                byte[] bytes = parcel.marshall();
                parcel.recycle();
                return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean restoreWebViewState(WebView webView, String base64State) {
        if (base64State == null || base64State.trim().isEmpty()) return false;
        try {
            byte[] bytes = android.util.Base64.decode(base64State, android.util.Base64.NO_WRAP);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);

            Bundle bundle = new Bundle();
            bundle.setClassLoader(PrivateBrowserActivity.class.getClassLoader());
            bundle.readFromParcel(parcel);
            parcel.recycle();

            WebBackForwardList list = webView.restoreState(bundle);
            return list != null && list.getSize() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveSession() {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < tabs.size(); i++) {
                TabInfo t = tabs.get(i);
                if (t.webView != null) {
                    String url = t.webView.getUrl();
                    if (url == null || url.trim().isEmpty()) url = "about:blank";
                    JSONObject obj = new JSONObject();
                    obj.put("url", url);
                    obj.put("isPinned", t.isPinned);

                    String encodedState = encodeWebViewState(t.webView);
                    if (encodedState != null) {
                        obj.put("webState", encodedState);
                        t.savedStateBase64 = encodedState;
                    } else if (t.savedStateBase64 != null) {
                        obj.put("webState", t.savedStateBase64);
                    }

                    arr.put(obj);
                }
            }
            browserPrefs.edit().putString(PREF_SAVED_SESSION, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void restoreSession() {
        boolean loadedTabs = false;
        try {
            String sessionData = browserPrefs.getString(PREF_SAVED_SESSION, "[]");
            JSONArray arr = new JSONArray(sessionData);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);

                String fallbackUrl = obj.optString("url", "about:blank");
                boolean isPinned = obj.optBoolean("isPinned", false);
                String webState = obj.optString("webState", null);

                createNewTab(fallbackUrl, isPinned, false, webState);
                loadedTabs = true;
            }
        } catch (Exception e) {}

        if (!loadedTabs) { createNewTab(null, false, true); }
        else {
            sortTabsByPinStatus();
            switchTab(tabs.size() - 1);
        }
    }

    private void sortTabsByPinStatus() {
        TabInfo currentActiveTab = tabs.isEmpty() ? null : tabs.get(currentTabIndex);
        Collections.sort(tabs, (t1, t2) -> Boolean.compare(t2.isPinned, t1.isPinned));
        if (currentActiveTab != null) {
            currentTabIndex = tabs.indexOf(currentActiveTab);
        }
    }

    private void injectAnimatedFanHeader() {
        TextView oldTitle = findViewById(R.id.tvHomeTitle); if (oldTitle == null) return;
        ViewGroup parent = (ViewGroup) oldTitle.getParent(); int index = parent.indexOfChild(oldTitle); parent.removeView(oldTitle);
        int fanColor = themeState == 0 ? Color.parseColor("#333333") : (themeState == 1 ? Color.parseColor("#E0E0E0") : Color.parseColor("#FFFFFF"));
        FanView fanView = new FanView(this, fanColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44)); params.gravity = Gravity.CENTER_VERTICAL; params.setMargins(0, 0, 0, 0); fanView.setLayoutParams(params); parent.addView(fanView, index);
    }

    private void setupScrollingLandscape() {
        FrameLayout container = findViewById(R.id.scrollingLandscapeContainer);
        if (container != null) {
            ScrollingLandscapeView landscapeView = new ScrollingLandscapeView(this); landscapeView.setTheme(themeState); container.addView(landscapeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            GradientDrawable gd = new GradientDrawable(); gd.setCornerRadius(dp(100));
            int strokeColor = (themeState == 0) ? Color.parseColor("#E5E5EA") : ((themeState == 1) ? Color.parseColor("#3A3A3C") : Color.parseColor("#333333"));
            gd.setStroke(dp(2), strokeColor); container.setBackground(gd); container.setClipToOutline(true);
        }
    }

    private static class FanView extends View {
        private final Paint paint; private final RectF bladeRect; private float rotation = 0f; private final float baseSpeed = 2.5f; private float currentSpeed = 2.5f; private int clickCount = 0; private boolean isBraking = false; private final Handler speedHandler = new Handler(Looper.getMainLooper());
        private final Runnable startBrakingTask = () -> { isBraking = true; };
        private final Runnable animator = new Runnable() { @Override public void run() { if (isBraking) { currentSpeed *= 0.95f; if (currentSpeed <= baseSpeed) { currentSpeed = baseSpeed; isBraking = false; clickCount = 0; } } rotation += currentSpeed; if (rotation >= 360f) rotation -= 360f; invalidate(); postDelayed(this, 16); } };
        public FanView(Context context, int color) { super(context); paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(color); paint.setStyle(Paint.Style.FILL); bladeRect = new RectF(); setClickable(true); setFocusable(true); setOnClickListener(v -> { isBraking = false; if (clickCount < 5) { clickCount++; currentSpeed = baseSpeed + (clickCount * 2.0f); } speedHandler.removeCallbacks(startBrakingTask); speedHandler.postDelayed(startBrakingTask, 5000); }); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(animator); } @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(animator); speedHandler.removeCallbacks(startBrakingTask); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float cx = getWidth() / 2f; float cy = getHeight() / 2f; float radius = Math.min(cx, cy) * 0.85f; paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6f); canvas.drawCircle(cx, cy, radius, paint); paint.setStyle(Paint.Style.FILL); canvas.drawCircle(cx, cy, radius * 0.2f, paint); canvas.save(); canvas.rotate(rotation, cx, cy); for (int i = 0; i < 4; i++) { bladeRect.set(cx - radius * 0.15f, cy - radius * 0.9f, cx + radius * 0.15f, cy - radius * 0.15f); canvas.drawRoundRect(bladeRect, 10f, 10f, paint); canvas.rotate(90f, cx, cy); } canvas.restore(); }
    }

    private static class PinView extends View {
        private boolean isPinned; private int color; private final Paint paint; private float currentRotation = 45f; private int currentAlpha = 255; private ValueAnimator animator;
        public PinView(Context context) { super(context); paint = new Paint(Paint.ANTI_ALIAS_FLAG); }
        public void setPinnedState(boolean pinned, int col) { this.isPinned = pinned; this.color = col; if (animator != null) animator.cancel(); float targetRotation = isPinned ? 0f : 45f; animator = ValueAnimator.ofFloat(0f, 1f); animator.setDuration(250); animator.setInterpolator(new AccelerateDecelerateInterpolator()); float startRotation = currentRotation; animator.addUpdateListener(a -> { float fraction = a.getAnimatedFraction(); currentRotation = startRotation + (targetRotation - startRotation) * fraction; invalidate(); }); animator.start(); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); float w = getWidth(), h = getHeight(); float cx = w / 2f, cy = h / 2f; float s = Math.min(w, h) * 0.25f; paint.setColor(color); paint.setStrokeWidth(s * 0.4f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); canvas.save(); canvas.rotate(currentRotation, cx, cy); paint.setAlpha(currentAlpha); if (isPinned) { paint.setStyle(Paint.Style.FILL_AND_STROKE); } else { paint.setStyle(Paint.Style.STROKE); } canvas.drawRoundRect(cx - s, cy - s, cx + s, cy + s * 0.2f, s * 0.4f, s * 0.4f, paint); canvas.drawRect(cx - s * 0.5f, cy + s * 0.2f, cx + s * 0.5f, cy + s * 0.5f, paint); canvas.drawLine(cx, cy + s * 0.5f, cx, cy + s * 1.5f, paint); canvas.restore(); }
    }

    public static class ScrollingLandscapeView extends View {
        private Paint skyPaint, backMountainPaint, frontMountainPaint, celestialPaint; private Path backPath, frontPath; private float scrollOffset = 0f; private int themeState = 1; private final Handler animHandler = new Handler(Looper.getMainLooper());
        private final Runnable animRunnable = new Runnable() { @Override public void run() { scrollOffset += 1.5f; if (scrollOffset > 12000f) scrollOffset -= 12000f; invalidate(); animHandler.postDelayed(this, 16); } };
        public ScrollingLandscapeView(Context context) { super(context); init(); }
        private void init() { skyPaint = new Paint(Paint.ANTI_ALIAS_FLAG); backMountainPaint = new Paint(Paint.ANTI_ALIAS_FLAG); frontMountainPaint = new Paint(Paint.ANTI_ALIAS_FLAG); celestialPaint = new Paint(Paint.ANTI_ALIAS_FLAG); backPath = new Path(); frontPath = new Path(); }
        public void setTheme(int themeState) { this.themeState = themeState; int backColor, frontColor, celestialColor; if (themeState == 0) { backColor = Color.parseColor("#90CAF9"); frontColor = Color.parseColor("#42A5F5"); celestialColor = Color.parseColor("#FFD54F"); } else if (themeState == 1) { backColor = Color.parseColor("#3949AB"); frontColor = Color.parseColor("#283593"); celestialColor = Color.parseColor("#F48FB1"); } else { backColor = Color.parseColor("#2C2C2E"); frontColor = Color.parseColor("#1C1C1E"); celestialColor = Color.parseColor("#E5E5EA"); } backMountainPaint.setColor(backColor); frontMountainPaint.setColor(frontColor); celestialPaint.setColor(celestialColor); invalidate(); }
        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); if (w <= 0 || h <= 0) return; int skyStart, skyEnd; if (themeState == 0) { skyStart = Color.parseColor("#4DA8DA"); skyEnd = Color.parseColor("#EEF5FF"); } else if (themeState == 1) { skyStart = Color.parseColor("#1A237E"); skyEnd = Color.parseColor("#1C1C1E"); } else { skyStart = Color.parseColor("#000000"); skyEnd = Color.parseColor("#0B0C10"); } skyPaint.setShader(new LinearGradient(0, 0, 0, h, skyStart, skyEnd, Shader.TileMode.CLAMP)); float chunkW = 1200f; int resolution = 120; backPath.reset(); backPath.moveTo(0, h); for(int i = 0; i <= resolution; i++) { float x = i * (chunkW / resolution); float rad = (float) (x * 2.0 * Math.PI / chunkW); float y = h * 0.45f + (float)Math.sin(rad) * (h * 0.15f) + (float)Math.sin(rad * 2.0f) * (h * 0.1f); backPath.lineTo(x, y); } backPath.lineTo(chunkW, h); backPath.close(); frontPath.reset(); frontPath.moveTo(0, h); for(int i = 0; i <= resolution; i++) { float x = i * (chunkW / resolution); float rad = (float) (x * 2.0 * Math.PI / chunkW); float y = h * 0.70f + (float)Math.cos(rad) * (h * 0.12f) + (float)Math.sin(rad * 3.0f) * (h * 0.08f); frontPath.lineTo(x, y); } frontPath.lineTo(chunkW, h); frontPath.close(); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); animHandler.post(animRunnable); } @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); animHandler.removeCallbacks(animRunnable); }
        @Override protected void onDraw(@NonNull Canvas canvas) { super.onDraw(canvas); int w = getWidth(); int h = getHeight(); if (w <= 0 || h <= 0) return; canvas.drawRect(0, 0, w, h, skyPaint); float celestialX = w * 0.8f; float celestialY = h * 0.35f; canvas.drawCircle(celestialX, celestialY, h * 0.2f, celestialPaint); float chunkW = 1200f; float backOffset = (scrollOffset * 0.5f) % chunkW; canvas.save(); canvas.translate(-backOffset, 0); canvas.drawPath(backPath, backMountainPaint); canvas.translate(chunkW, 0); canvas.drawPath(backPath, backMountainPaint); canvas.translate(chunkW, 0); canvas.drawPath(backPath, backMountainPaint); canvas.restore(); float frontOffset = scrollOffset % chunkW; canvas.save(); canvas.translate(-frontOffset, 0); canvas.drawPath(frontPath, frontMountainPaint); canvas.translate(chunkW, 0); canvas.drawPath(frontPath, frontMountainPaint); canvas.translate(chunkW, 0); canvas.drawPath(frontPath, frontMountainPaint); canvas.restore(); }
    }

    private void updateTabIconCount() { if (tvTabCount != null) tvTabCount.setText(String.valueOf(tabs.size())); }

    private void animateCornerRadius(GradientDrawable drawable, float startRadius, float endRadius) { ValueAnimator animator = ValueAnimator.ofFloat(startRadius, endRadius); animator.setDuration(300); animator.addUpdateListener(animation -> drawable.setCornerRadius((float) animation.getAnimatedValue())); animator.start(); }

    private void loadUrlOrSearch() {
        String query = etSearchUrl.getText().toString().trim(); etSearchUrl.clearFocus(); if (query.isEmpty() || getCurrentWeb() == null) return;
        InputMethodManager imm = getSystemService(InputMethodManager.class); if (imm != null) imm.hideSoftInputFromWindow(etSearchUrl.getWindowToken(), 0);
        hideHomePage();
        if (!query.contains(" ") && (query.contains(".") || query.startsWith("http"))) { if (!query.startsWith("http://") && !query.startsWith("https://")) query = "https://" + query; getCurrentWeb().loadUrl(query); } else { getCurrentWeb().loadUrl("https://www.google.com/search?q=" + query); }
    }

    private TabInfo getTabForWeb(WebView web) { for (TabInfo t : tabs) if (t.webView == web) return t; return null; }

    private void showHomePage() { etSearchUrl.clearFocus(); etSearchUrl.setText(""); if (homeOverlay != null) { renderHomeShortcuts(); homeOverlay.setVisibility(View.VISIBLE); } updateBackgroundBlur(); }
    private void hideHomePage() { if (homeOverlay != null) homeOverlay.setVisibility(View.GONE); }
    private void openShortcut(String url) { if (url == null || url.trim().isEmpty() || getCurrentWeb() == null) return; hideHomePage(); getCurrentWeb().loadUrl(url); }

    private void renderHomeShortcuts() {
        if (homeShortcutList == null) return; homeShortcutList.removeAllViews();

        int cardBg, textColor, secondaryColor, squircleBgColor, iconColor;
        if (themeState == 0) {
            cardBg = Color.parseColor("#E5E5EA"); textColor = Color.parseColor("#000000"); secondaryColor = Color.parseColor("#555555"); squircleBgColor = Color.parseColor("#FFFFFF"); iconColor = Color.parseColor("#000000");
        } else if (themeState == 1) {
            cardBg = Color.parseColor("#2C2C2E"); textColor = Color.parseColor("#FFFFFF"); secondaryColor = Color.parseColor("#AAAAAA"); squircleBgColor = Color.parseColor("#D4E4FF"); iconColor = Color.parseColor("#000000");
        } else {
            cardBg = Color.parseColor("#1C1C1E"); textColor = Color.parseColor("#FFFFFF"); secondaryColor = Color.parseColor("#888888"); squircleBgColor = Color.parseColor("#D4E4FF"); iconColor = Color.parseColor("#000000");
        }

        addHomeShortcut("Google", "Secure Search", "https://www.google.com/", "G", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("DuckDuckGo", "Secure Search", "https://duckduckgo.com/", "D", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("Yahoo", "Secure Search", "https://search.yahoo.com/", "Y", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("Instagram", "Social Media", "https://www.instagram.com/", "IG", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("LinkedIn", "Professional", "https://www.linkedin.com/", "in", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("GitHub", "Development", "https://github.com/", "GH", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("YouTube", "Video", "https://www.youtube.com/", "YT", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);
        addHomeShortcut("Bing", "Secure Search", "https://search.bing.com/", "B", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, false, -1);

        try { JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]")); for (int i = 0; i < arr.length(); i++) { JSONObject obj = arr.getJSONObject(i); addHomeShortcut(obj.getString("name"), "Custom Link", obj.getString("url"), "C", cardBg, textColor, secondaryColor, squircleBgColor, iconColor, true, i); } } catch (Exception e) {}
        addCustomShortcutCard(cardBg, textColor, secondaryColor, squircleBgColor, iconColor);
    }

    private void addHomeShortcut(String name, String subtitle, String url, String iconText, int cardBg, int textColor, int secondaryColor, int squircleBgColor, int iconColor, boolean isCustom, int customIndex) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(6), dp(16), dp(6));

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0; cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(cardParams);

        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(cardBg);
        cardDrawable.setCornerRadius(dp(100));
        card.setBackground(cardDrawable);

        RelativeLayout iconContainer = new RelativeLayout(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.setMarginEnd(dp(8));
        iconContainer.setLayoutParams(iconParams);

        ImageView squircleBg = new ImageView(this);
        squircleBg.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        squircleBg.setImageResource(R.drawable.ic_squircle);
        squircleBg.setScaleType(ImageView.ScaleType.FIT_XY);
        squircleBg.setColorFilter(squircleBgColor, PorterDuff.Mode.SRC_IN);
        iconContainer.addView(squircleBg);

        TextView iconTextTv = new TextView(this);
        iconTextTv.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iconTextTv.setText(iconText);
        iconTextTv.setTextColor(iconColor);
        iconTextTv.setTextSize(iconText.length() > 1 ? 12f : 14f);
        iconTextTv.setGravity(Gravity.CENTER);
        iconTextTv.setTypeface(null, android.graphics.Typeface.BOLD);
        iconContainer.addView(iconTextTv);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextColor(textColor);
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);

        TextView sub = new TextView(this);
        sub.setText(subtitle);
        sub.setTextColor(secondaryColor);
        sub.setTextSize(11f);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);

        textBox.addView(title); textBox.addView(sub);
        card.addView(iconContainer); card.addView(textBox);
        card.setOnClickListener(v -> openShortcut(url));
        if (isCustom) { card.setOnLongClickListener(v -> { showDeleteCustomShortcutDialog(customIndex, name); return true; }); }
        homeShortcutList.addView(card);
    }

    private void addCustomShortcutCard(int cardBg, int textColor, int secondaryColor, int squircleBgColor, int iconColor) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8), dp(6), dp(16), dp(6));

        GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
        cardParams.width = 0; cardParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        cardParams.setMargins(dp(6), dp(6), dp(6), dp(6));
        card.setLayoutParams(cardParams);

        GradientDrawable cardDrawable = new GradientDrawable();
        cardDrawable.setColor(cardBg);
        cardDrawable.setCornerRadius(dp(100));
        card.setBackground(cardDrawable);

        RelativeLayout iconContainer = new RelativeLayout(this);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        iconParams.setMarginEnd(dp(8));
        iconContainer.setLayoutParams(iconParams);

        ImageView squircleBg = new ImageView(this);
        squircleBg.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        squircleBg.setImageResource(R.drawable.ic_squircle);
        squircleBg.setScaleType(ImageView.ScaleType.FIT_XY);
        squircleBg.setColorFilter(squircleBgColor, PorterDuff.Mode.SRC_IN);
        iconContainer.addView(squircleBg);

        ImageView plusIcon = new ImageView(this);
        plusIcon.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        plusIcon.setImageResource(R.drawable.ic_add_new);
        plusIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        plusIcon.setColorFilter(iconColor);
        plusIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        iconContainer.addView(plusIcon);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);
        textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText("Add Shortcut");
        title.setTextColor(textColor);
        title.setTextSize(14f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView sub = new TextView(this);
        sub.setText("Custom URL");
        sub.setTextColor(secondaryColor);
        sub.setTextSize(11f);

        textBox.addView(title); textBox.addView(sub);
        card.addView(iconContainer); card.addView(textBox);
        card.setOnClickListener(v -> showCustomShortcutDialog());
        homeShortcutList.addView(card);
    }

    private void showCustomShortcutDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int pad = dp(4); EditText nameInput = new EditText(this); nameInput.setHint("Name (e.g. Reddit)"); nameInput.setSingleLine(true); nameInput.setPadding(pad, pad, pad, pad); EditText urlInput = new EditText(this); urlInput.setHint("Website URL"); urlInput.setSingleLine(true); urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); urlInput.setPadding(pad, pad, pad, pad); form.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55))); form.addView(urlInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle); builder.setTitle("Add Custom Shortcut"); builder.setView(form);
        builder.setPositiveButton("Save", (dialog, which) -> { String name = nameInput.getText().toString().trim(); String url = urlInput.getText().toString().trim(); if (name.isEmpty() || url.isEmpty()) { Toast.makeText(this, "Enter both a name and URL.", Toast.LENGTH_SHORT).show(); return; } if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url; saveCustomShortcut(name, url); }); builder.setNegativeButton("Cancel", null); AlertDialog dialog = builder.create(); dialog.setOnShowListener(d -> styleDialogButtons(dialog)); dialog.show();
    }

    private void saveCustomShortcut(String name, String url) { try { JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]")); JSONObject obj = new JSONObject(); obj.put("name", name); obj.put("url", url); arr.put(obj); browserPrefs.edit().putString(PREF_CUSTOM_LINKS, arr.toString()).apply(); renderHomeShortcuts(); } catch (Exception e) {} }
    private void showDeleteCustomShortcutDialog(int index, String name) { int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle); builder.setTitle("Remove Shortcut"); builder.setMessage("Remove '" + name + "' from shortcuts?"); builder.setPositiveButton("Remove", (dialog, which) -> { try { JSONArray arr = new JSONArray(browserPrefs.getString(PREF_CUSTOM_LINKS, "[]")); if (index >= 0 && index < arr.length()) { arr.remove(index); browserPrefs.edit().putString(PREF_CUSTOM_LINKS, arr.toString()).apply(); renderHomeShortcuts(); } } catch (Exception e) {} }); builder.setNegativeButton("Cancel", null); AlertDialog dialog = builder.create(); dialog.setOnShowListener(d -> styleDialogButtons(dialog)); dialog.show(); }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void updateBackgroundBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            webViewContainer.setRenderEffect(null);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (isVideoMode && isVideoCapsuleHidden) {
                isVideoCapsuleHidden = false;
                searchCapsule.setVisibility(View.VISIBLE);
                searchCapsule.setAlpha(0f);
                searchCapsule.animate().alpha(1f).setDuration(200).start();
            }
            View v = getCurrentFocus();
            if (v instanceof EditText && searchCapsule != null && !isVideoMode) {
                Rect outRect = new Rect(); searchCapsule.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus(); InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void styleDialogButtons(AlertDialog dialog) { int btnColor = isDarkTheme ? Color.parseColor("#FFB59F") : Color.parseColor("#6750A4"); if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(btnColor); if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(btnColor); if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(btnColor); }

    private void setupModernBackGesture() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (mCustomView != null) exitFullscreenVideo();
                else if (tabsOverlay.getVisibility() == View.VISIBLE) {
                    if (tabs.isEmpty()) {
                        createNewTab(null, false, true);
                    } else {
                        if (currentTabIndex >= tabs.size()) currentTabIndex = Math.max(0, tabs.size() - 1);
                        if (currentTabIndex < 0) currentTabIndex = 0;
                        switchTab(currentTabIndex);
                    }
                }
                else if (downloadsOverlay.getVisibility() == View.VISIBLE) { downloadsOverlay.setVisibility(View.GONE); updateBackgroundBlur(); }
                else if (!tabs.isEmpty() && getCurrentWeb() != null && getCurrentWeb().canGoBack()) getCurrentWeb().goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
    }

    private void enterFullscreenVideo(View view, WebChromeClient.CustomViewCallback callback) {
        if (mCustomView != null) { callback.onCustomViewHidden(); return; }
        mOriginalOrientation = getRequestedOrientation();
        mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
        mCustomView = view;
        mCustomViewCallback = callback;
        mFullscreenContainer = new FrameLayout(this);
        mFullscreenContainer.setBackgroundColor(Color.BLACK);
        mFullscreenContainer.addView(mCustomView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        RelativeLayout root = findViewById(R.id.browserRoot);
        root.addView(mFullscreenContainer, new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (searchCapsule != null) searchCapsule.bringToFront();
        if (tabsOverlay != null) tabsOverlay.bringToFront();
        if (downloadsOverlay != null) downloadsOverlay.bringToFront();

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        ViewCompat.requestApplyInsets(getWindow().getDecorView());
    }

    private void exitFullscreenVideo() {
        if (mCustomView == null) return;

        RelativeLayout root = findViewById(R.id.browserRoot);
        root.removeView(mFullscreenContainer);
        mFullscreenContainer = null;
        mCustomView = null;

        if (mCustomViewCallback != null) mCustomViewCallback.onCustomViewHidden();
        mCustomViewCallback = null;

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
        }

        setRequestedOrientation(mOriginalOrientation);

        ViewCompat.requestApplyInsets(getWindow().getDecorView());
    }

    private WebView getCurrentWeb() { if (tabs.isEmpty() || currentTabIndex < 0 || currentTabIndex >= tabs.size()) return null; return tabs.get(currentTabIndex).webView; }

    private void createNewTab(String url, boolean isPinned, boolean switchImmediately) {
        createNewTab(url, isPinned, switchImmediately, null);
    }

    private void createNewTab(String url, boolean isPinned, boolean switchImmediately, String stateBase64) {
        captureCurrentTabPreview();
        TabInfo info = new TabInfo(); info.webView = new CustomWebView(this);
        info.webView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); info.webView.setTag(false);
        info.isPinned = isPinned;
        setupSuperSecureWebView(info.webView); tabs.add(info);

        updateTabIconCount();
        webViewContainer.addView(info.webView);

        boolean restored = false;

        if (stateBase64 != null) {
            restored = restoreWebViewState(info.webView, stateBase64);
            if (restored) {
                info.savedStateBase64 = stateBase64;
            }
        }

        if (!restored) {
            if (url != null && !url.isEmpty() && !url.equals("about:blank")) { info.webView.loadUrl(url); }
            else { info.webView.loadUrl("about:blank"); }
        }

        if (switchImmediately) { switchTab(tabs.size() - 1); }
        else { info.webView.setVisibility(View.GONE); }
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        stopAutoActions(); disableVideoMode(); currentTabIndex = index; etSearchUrl.clearFocus();
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.setVisibility(i == currentTabIndex ? View.VISIBLE : View.GONE);
        WebView current = getCurrentWeb();
        if (current != null) {
            String currentUrl = current.getUrl();
            if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) showHomePage();
            else { etSearchUrl.setText(currentUrl); hideHomePage(); }
            current.evaluateJavascript("if(window.activeVideo && !window.activeVideo.paused) { OwnBrowser.onVideoPlayState(true, window.activeVideo.muted); }", null);
        }
        tabsOverlay.setVisibility(View.GONE); updateBackgroundBlur();
    }

    private void closeTabAnimated(TabInfo closingInfo, View cardView) {
        cardView.setEnabled(false);

        cardView.animate()
                .alpha(0f)
                .scaleX(0.5f)
                .scaleY(0.5f)
                .translationY(dp(40))
                .rotation((float) (Math.random() * 20 - 10))
                .setDuration(300)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    int index = tabs.indexOf(closingInfo);
                    if (index != -1) {
                        closeTabBackend(index);
                    }
                }).start();
    }

    private void closeTabBackend(int index) {
        if (index < 0 || index >= tabs.size()) return;
        TabInfo closing = tabs.get(index);

        closing.webView.setWebChromeClient(null);
        closing.webView.setWebViewClient(null);

        webViewContainer.removeView(closing.webView);

        closing.webView.clearHistory();
        closing.webView.loadUrl("about:blank");
        closing.webView.removeAllViews();

        WebView dyingWeb = closing.webView;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { dyingWeb.destroy(); } catch (Exception ignored) {}
        }, 500);

        closing.preview = null;

        tabs.remove(index);

        if (index < currentTabIndex) {
            currentTabIndex--;
        } else if (index == currentTabIndex) {
            if (currentTabIndex >= tabs.size()) {
                currentTabIndex = Math.max(0, tabs.size() - 1);
            }
        }

        updateTabIconCount();
        saveSession();

        TransitionManager.beginDelayedTransition(tabsGrid, new android.transition.ChangeBounds().setDuration(300));

        if (tabs.isEmpty()) {
            createNewTab(null, false, false);
            renderVisualTabsGrid();
        } else {
            renderVisualTabsGrid();
        }
    }

    private void captureCurrentTabPreview() {
        if (tabs.isEmpty() || currentTabIndex >= tabs.size()) return; TabInfo current = tabs.get(currentTabIndex);
        if (current.webView.getWidth() > 0 && current.webView.getHeight() > 0) {
            try {
                Bitmap bmp = Bitmap.createBitmap(current.webView.getWidth(), current.webView.getHeight(), Bitmap.Config.RGB_565);
                Canvas c = new Canvas(bmp); current.webView.draw(c);
                if (current.preview != null) current.preview.recycle(); current.preview = Bitmap.createScaledBitmap(bmp, 300, 500, true); bmp.recycle();
            } catch (OutOfMemoryError ignored) {}
        }
    }

    private void openVisualTabSwitcher() { etSearchUrl.clearFocus(); captureCurrentTabPreview(); renderVisualTabsGrid(); tabsOverlay.setVisibility(View.VISIBLE); updateBackgroundBlur(); }

    private void renderVisualTabsGrid() {
        tabsGrid.removeAllViews();
        int outerUnselectedBg, innerBg, textColor, separatorColor;

        if (themeState == 0) { outerUnselectedBg = Color.parseColor("#E5E5EA"); innerBg = Color.parseColor("#FFFFFF"); textColor = Color.parseColor("#000000"); separatorColor = Color.parseColor("#000000"); }
        else if (themeState == 1) { outerUnselectedBg = Color.parseColor("#2C2C2E"); innerBg = Color.parseColor("#1C1C1E"); textColor = Color.parseColor("#FFFFFF"); separatorColor = Color.parseColor("#555555"); }
        else { outerUnselectedBg = Color.parseColor("#1C1C1E"); innerBg = Color.parseColor("#000000"); textColor = Color.parseColor("#FFFFFF"); separatorColor = Color.parseColor("#333333"); }

        for (int i = 0; i < tabs.size(); i++) {
            final int index = i; TabInfo info = tabs.get(i);

            int outerSelectedBg;
            if (themeState == 0) { outerSelectedBg = Color.parseColor("#FFB59F"); }
            else { outerSelectedBg = Color.parseColor("#6750A4"); }

            LinearLayout outerCard = new LinearLayout(this);
            outerCard.setOrientation(LinearLayout.VERTICAL);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0; params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(8), dp(8), dp(8), dp(8));
            outerCard.setLayoutParams(params);

            GradientDrawable outerGd = new GradientDrawable();
            outerGd.setColor(index == currentTabIndex ? outerSelectedBg : outerUnselectedBg);
            outerGd.setCornerRadius(dp(16));
            outerCard.setBackground(outerGd);
            outerCard.setPadding(dp(6), dp(10), dp(6), dp(6));

            outerCard.setTag(index);

            LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(4), 0, dp(4), dp(6));

            int headerTextColor = (index == currentTabIndex) ? Color.parseColor("#FFFFFF") : textColor;

            PinView pinToggle = new PinView(this);
            LinearLayout.LayoutParams pinParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            pinParams.setMargins(0, 0, dp(8), 0);
            pinToggle.setLayoutParams(pinParams);
            pinToggle.setPinnedState(info.isPinned, headerTextColor);
            pinToggle.setOnClickListener(v -> {
                info.isPinned = !info.isPinned;
                sortTabsByPinStatus();
                saveSession();
                renderVisualTabsGrid();
            });

            TextView title = new TextView(this); title.setText(info.title); title.setTextColor(headerTextColor); title.setTextSize(13f); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END); title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            ImageView close = new ImageView(this);
            close.setImageResource(R.drawable.ic_rounded_close);
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
            closeParams.setMargins(dp(10), 0, 0, 0);
            close.setLayoutParams(closeParams);
            close.setColorFilter(headerTextColor);
            close.setPadding(dp(6), dp(6), dp(6), dp(6));
            close.setOnClickListener(v -> closeTabAnimated(info, outerCard));

            header.addView(pinToggle); header.addView(title); header.addView(close);

            View separator = new View(this); LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)); sepParams.setMargins(dp(2), 0, dp(2), dp(6)); separator.setLayoutParams(sepParams); separator.setBackgroundColor(separatorColor);
            ImageView preview = new ImageView(this); LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(160)); preview.setLayoutParams(previewParams); preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable innerGd = new GradientDrawable(); innerGd.setColor(innerBg); innerGd.setCornerRadius(dp(10)); preview.setBackground(innerGd); preview.setClipToOutline(true);
            if (info.preview != null) preview.setImageBitmap(info.preview);

            outerCard.addView(header); outerCard.addView(separator); outerCard.addView(preview);
            outerCard.setOnClickListener(v -> switchTab((int) outerCard.getTag()));

            outerCard.setOnLongClickListener(v -> {
                View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(null, shadowBuilder, v, 0);
                } else {
                    v.startDrag(null, shadowBuilder, v, 0);
                }
                return true;
            });

            outerCard.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DRAG_ENTERED:
                        v.setScaleX(1.03f); v.setScaleY(1.03f);
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                        return true;
                    case DragEvent.ACTION_DROP:
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                        View draggedView = (View) event.getLocalState();
                        if (draggedView != null && draggedView != v) {
                            int fromIdx = (int) draggedView.getTag();
                            int toIdx = (int) v.getTag();
                            if (fromIdx >= 0 && fromIdx < tabs.size() && toIdx >= 0 && toIdx < tabs.size()) {
                                TabInfo cTab = tabs.get(currentTabIndex);
                                TabInfo moved = tabs.remove(fromIdx);
                                tabs.add(toIdx, moved);

                                sortTabsByPinStatus();

                                saveSession();
                                new Handler(Looper.getMainLooper()).post(this::renderVisualTabsGrid);
                            }
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        v.setScaleX(1.0f); v.setScaleY(1.0f);
                        return true;
                }
                return false;
            });

            tabsGrid.addView(outerCard);
        }
    }

    private void simulateSwipeUp() {
        WebView web = getCurrentWeb(); if (web == null) return;
        long downTime = SystemClock.uptimeMillis(); long eventTime = downTime; float x = web.getWidth() / 2.0f; float yStart = web.getHeight() * 0.8f; float yEnd = web.getHeight() * 0.2f;
        MotionEvent downEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_DOWN, x, yStart, 0); web.dispatchTouchEvent(downEvent); downEvent.recycle();
        int steps = 15;
        for (int i = 1; i <= steps; i++) { eventTime += 10; float y = yStart - ((yStart - yEnd) * (i / (float) steps)); MotionEvent moveEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0); web.dispatchTouchEvent(moveEvent); moveEvent.recycle(); }
        eventTime += 10; MotionEvent upEvent = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, yEnd, 0); web.dispatchTouchEvent(upEvent); upEvent.recycle();
    }

    private void stopAutoActions() { currentAutoScrollSpeed = 0; isAutoSwiping = false; autoScrollHandler.removeCallbacks(autoScrollRunnable); autoSwipeHandler.removeCallbacks(autoSwipeRunnable); if(autoActionIndicator != null) autoActionIndicator.setVisibility(View.GONE); }
    private void startAutoScroll(int speedMultiplier) { stopAutoActions(); currentAutoScrollSpeed = speedMultiplier; if (speedMultiplier > 0) { if(autoActionIndicator != null) autoActionIndicator.setVisibility(View.VISIBLE); autoScrollHandler.post(autoScrollRunnable); } }
    private void startAutoSwipe() { stopAutoActions(); isAutoSwiping = true; if(autoActionIndicator != null) autoActionIndicator.setVisibility(View.VISIBLE); autoSwipeHandler.postDelayed(autoSwipeRunnable, 4000); Toast.makeText(this, "Auto Swipe Activated (4s)", Toast.LENGTH_SHORT).show(); }

    private void executeLiquidDismiss(PopupWindow popupWindow, View menuLayout, Runnable action) {
        menuLayout.animate()
                .scaleX(0.4f)
                .scaleY(0.4f)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AnticipateInterpolator(1.2f))
                .withEndAction(() -> {
                    if (popupWindow != null && popupWindow.isShowing()) {
                        popupWindow.dismiss();
                    }
                    if (action != null) action.run();
                })
                .start();
    }

    private void showAutoScrollMenu(View anchor) {
        etSearchUrl.clearFocus(); LinearLayout menuLayout = new LinearLayout(this); menuLayout.setOrientation(LinearLayout.VERTICAL); menuLayout.setMinimumWidth(dp(220));
        int bgColor, textColor;
        if (themeState == 0) { bgColor = Color.parseColor("#FFFFFF"); textColor = Color.BLACK; } else if (themeState == 1) { bgColor = Color.parseColor("#2C2C2E"); textColor = Color.WHITE; } else { bgColor = Color.parseColor("#1C1C1E"); textColor = Color.WHITE; }
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bgColor); gd.setCornerRadius(dp(40)); menuLayout.setBackground(gd); menuLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        final PopupWindow[] popupWindow = new PopupWindow[1];

        TextView tvStop = createMenuItem("Stop All", R.drawable.ic_stopcircle, textColor); tvStop.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, this::stopAutoActions));
        TextView tvSwipe = createMenuItem("Auto Swipe (4s)", R.drawable.ic_autoswipe, textColor); tvSwipe.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, this::startAutoSwipe));
        TextView tv1x = createMenuItem("1x Speed", R.drawable.ic_fastscroll, textColor); tv1x.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> startAutoScroll(1)));
        TextView tv2x = createMenuItem("2x Speed", R.drawable.ic_fastscroll, textColor); tv2x.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> startAutoScroll(3)));
        TextView tv3x = createMenuItem("3x Speed", R.drawable.ic_fastscroll, textColor); tv3x.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> startAutoScroll(6)));
        TextView tv4x = createMenuItem("4x Speed", R.drawable.ic_fastscroll, textColor); tv4x.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> startAutoScroll(12)));

        menuLayout.addView(tvStop); menuLayout.addView(tvSwipe); menuLayout.addView(tv1x); menuLayout.addView(tv2x); menuLayout.addView(tv3x); menuLayout.addView(tv4x);

        menuLayout.setAlpha(0f);
        menuLayout.setScaleX(0.3f);
        menuLayout.setScaleY(0.3f);
        menuLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                menuLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                menuLayout.setPivotX(menuLayout.getWidth() - dp(20));
                menuLayout.setPivotY(menuLayout.getHeight());

                menuLayout.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(350)
                        .setInterpolator(new OvershootInterpolator(1.2f))
                        .start();
            }
        });

        isMenuOpen = true; updateBackgroundBlur();
        popupWindow[0] = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true); popupWindow[0].setElevation(dp(30));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.transition.Transition exitTrans = new android.transition.Fade();
            exitTrans.setDuration(150);
            popupWindow[0].setExitTransition(exitTrans);
        }

        popupWindow[0].setOnDismissListener(() -> { isMenuOpen = false; updateBackgroundBlur(); });
        popupWindow[0].showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, dp(20), dp(90));
    }

    private void showRoundedMenu(View anchor) {
        etSearchUrl.clearFocus(); LinearLayout menuLayout = new LinearLayout(this); menuLayout.setOrientation(LinearLayout.VERTICAL); menuLayout.setMinimumWidth(dp(220));
        int bgColor, textColor;
        if (themeState == 0) { bgColor = Color.parseColor("#FFFFFF"); textColor = Color.BLACK; } else if (themeState == 1) { bgColor = Color.parseColor("#2C2C2E"); textColor = Color.WHITE; } else { bgColor = Color.parseColor("#1C1C1E"); textColor = Color.WHITE; }
        GradientDrawable gd = new GradientDrawable(); gd.setColor(bgColor); gd.setCornerRadius(dp(40)); menuLayout.setBackground(gd); menuLayout.setPadding(dp(16), dp(16), dp(16), dp(16));
        final PopupWindow[] popupWindow = new PopupWindow[1]; WebView current = getCurrentWeb(); if (current == null) return;

        TextView tvHome = createMenuItem("Home", R.drawable.ic_homehouse, textColor); tvHome.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> { current.loadUrl("about:blank"); showHomePage(); }));
        TextView tvReload = createMenuItem("Reload Page", android.R.drawable.ic_popup_sync, textColor); tvReload.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, current::reload));
        boolean isDesktop = false; if (current.getTag() != null) isDesktop = (boolean) current.getTag();
        TextView tvDesktop = createMenuItem(isDesktop ? "Switch to Mobile" : "Request Desktop Site", R.drawable.ic_desktop, textColor); tvDesktop.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> toggleDesktopMode(current)));
        TextView tvDownloads = createMenuItem("Downloads", android.R.drawable.stat_sys_download, textColor); tvDownloads.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, this::openVisualDownloadsManager));
        TextView tvNewTab = createMenuItem("New Tab", R.drawable.ic_newtab, textColor); tvNewTab.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, () -> createNewTab(null, false, true)));

        TextView tvWipeData = createMenuItem("Delete All Data", android.R.drawable.ic_menu_delete, Color.parseColor("#FF3B30"));
        tvWipeData.setOnClickListener(v -> executeLiquidDismiss(popupWindow[0], menuLayout, this::showWipeDataConfirmation));

        menuLayout.addView(tvHome); menuLayout.addView(tvReload); menuLayout.addView(tvDesktop); menuLayout.addView(tvDownloads); menuLayout.addView(tvNewTab); menuLayout.addView(tvWipeData);

        menuLayout.setAlpha(0f);
        menuLayout.setScaleX(0.3f);
        menuLayout.setScaleY(0.3f);
        menuLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                menuLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                menuLayout.setPivotX(menuLayout.getWidth() - dp(20));
                menuLayout.setPivotY(menuLayout.getHeight());

                menuLayout.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(350)
                        .setInterpolator(new OvershootInterpolator(1.2f))
                        .start();
            }
        });

        isMenuOpen = true; updateBackgroundBlur();
        popupWindow[0] = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true); popupWindow[0].setElevation(dp(30));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.transition.Transition exitTrans = new android.transition.Fade();
            exitTrans.setDuration(150);
            popupWindow[0].setExitTransition(exitTrans);
        }

        popupWindow[0].setOnDismissListener(() -> { isMenuOpen = false; updateBackgroundBlur(); });
        popupWindow[0].showAtLocation(anchor, Gravity.BOTTOM | Gravity.END, dp(20), dp(90));
    }

    private void showWipeDataConfirmation() {
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
        AlertDialog.Builder builder = new AlertDialog.Builder(this, dialogStyle);
        builder.setTitle("Delete Everything?");
        builder.setMessage("Are you sure you want to delete all data, cookies, history, and active tabs completely?");
        builder.setPositiveButton("Delete", (dialog, which) -> executeTotalDataWipe());
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> styleDialogButtons(dialog));
        dialog.show();
    }

    private void executeTotalDataWipe() {
        stopAutoActions();

        for (TabInfo t : tabs) {
            if (t.webView != null) {
                t.webView.setWebChromeClient(null);
                t.webView.setWebViewClient(null);
                webViewContainer.removeView(t.webView);
                t.webView.clearHistory();
                t.webView.clearCache(true);
                t.webView.clearFormData();
                t.webView.loadUrl("about:blank");
                t.webView.removeAllViews();

                WebView dyingWeb = t.webView;
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { dyingWeb.destroy(); } catch (Exception ignored) {}
                }, 500);
            }
            t.preview = null;
        }
        tabs.clear();
        browserPrefs.edit().remove(PREF_SAVED_SESSION).apply();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();

        Toast.makeText(this, "All Data Wiped.", Toast.LENGTH_SHORT).show();
        createNewTab(null, false, true);
    }

    private TextView createMenuItem(String text, int iconResId, int color) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(color); tv.setTextSize(16f); tv.setPadding(dp(16), dp(12), dp(16), dp(12)); tv.setGravity(Gravity.CENTER_VERTICAL); tv.setCompoundDrawablePadding(dp(16));
        if (iconResId != 0) { Drawable icon = androidx.core.content.ContextCompat.getDrawable(this, iconResId); if (icon != null) { icon = icon.mutate(); int iconSize = dp(24); icon.setBounds(0, 0, iconSize, iconSize); icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)); tv.setCompoundDrawables(icon, null, null, null); } }
        return tv;
    }

    private void openVisualDownloadsManager() {
        etSearchUrl.clearFocus(); downloadsList.removeAllViews(); downloadsOverlay.setVisibility(View.VISIBLE); updateBackgroundBlur();
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OWN's Browser downloads"); File[] files = dir.listFiles();
        if (!dir.exists() || files == null || files.length == 0) return;

        java.util.Arrays.sort(files, (File f1, File f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.US);
        int primaryText, secondaryText, iconBg;
        if (themeState == 0) { primaryText = Color.parseColor("#000000"); secondaryText = Color.parseColor("#555555"); iconBg = Color.parseColor("#E0E0E0"); } else if (themeState == 1) { primaryText = Color.parseColor("#FFFFFF"); secondaryText = Color.parseColor("#AAAAAA"); iconBg = Color.parseColor("#3D322F"); } else { primaryText = Color.parseColor("#FFFFFF"); secondaryText = Color.parseColor("#AAAAAA"); iconBg = Color.parseColor("#1C1C1E"); }

        for (File file : files) {
            TextView dateHeader = new TextView(this); dateHeader.setText(sdf.format(new Date(file.lastModified()))); dateHeader.setTextColor(primaryText); dateHeader.setTypeface(null, android.graphics.Typeface.BOLD); dateHeader.setPadding(0, dp(12), 0, dp(4)); downloadsList.addView(dateHeader);
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setPadding(0, dp(8), 0, dp(8)); row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView thumb = new ImageView(this); thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48))); thumb.setScaleType(ImageView.ScaleType.CENTER_CROP); String name = file.getName().toLowerCase();

            if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".jpeg")) {
                BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inSampleSize = 4; thumb.setImageBitmap(BitmapFactory.decodeFile(file.getAbsolutePath(), opts));
            } else if (name.endsWith(".pdf") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    ParcelFileDescriptor fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(fd);
                    if (renderer.getPageCount() > 0) {
                        PdfRenderer.Page page = renderer.openPage(0);
                        Bitmap bitmap = Bitmap.createBitmap(dp(64), dp(64), Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        thumb.setImageBitmap(bitmap);
                        page.close();
                    } else {
                        thumb.setImageResource(android.R.drawable.ic_menu_agenda); thumb.setColorFilter(primaryText); thumb.setBackgroundColor(iconBg);
                    }
                    renderer.close();
                    fd.close();
                } catch (Exception e) {
                    thumb.setImageResource(android.R.drawable.ic_menu_agenda); thumb.setColorFilter(primaryText); thumb.setBackgroundColor(iconBg);
                }
            } else {
                int fallbackIcon = android.R.drawable.ic_menu_info_details;
                if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) fallbackIcon = android.R.drawable.ic_media_play;
                else if (name.endsWith(".zip") || name.endsWith(".rar")) fallbackIcon = android.R.drawable.ic_menu_save;
                else if (name.endsWith(".txt") || name.endsWith(".doc")) fallbackIcon = android.R.drawable.ic_menu_agenda;

                thumb.setImageResource(fallbackIcon);
                thumb.setColorFilter(primaryText);
                thumb.setBackgroundColor(iconBg);
            }
            row.addView(thumb);

            LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(12), 0, 0, 0); details.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView title = new TextView(this); title.setText(file.getName()); title.setTextColor(primaryText); title.setTextSize(16f); title.setSingleLine(true);
            TextView sub = new TextView(this); sub.setText(String.format(Locale.US, "%.2f MB • Phone Storage", (file.length() / (1024f * 1024f)))); sub.setTextColor(secondaryText); sub.setTextSize(12f);
            details.addView(title); details.addView(sub); row.addView(details);

            ImageView menu = new ImageView(this);
            menu.setImageResource(android.R.drawable.ic_menu_more);
            menu.setColorFilter(primaryText);
            menu.setPadding(dp(12), dp(12), dp(12), dp(12));
            menu.setOnClickListener(v -> showFileActionDialog(file));
            row.setOnClickListener(v -> openFileDirectly(file)); row.addView(menu); downloadsList.addView(row);
        }
    }

    private void openFileDirectly(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) {
                uri = Uri.fromFile(file);
            }

            String mimeType = "*/*";
            String extension = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
            if (extension == null || extension.isEmpty()) {
                int dotPos = file.getName().lastIndexOf('.');
                if (dotPos >= 0) extension = file.getName().substring(dotPos + 1).toLowerCase();
            }

            if (extension != null && !extension.isEmpty()) {
                String mappedType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                if (mappedType != null) {
                    mimeType = mappedType;
                } else {
                    if (extension.equalsIgnoreCase("pdf")) mimeType = "application/pdf";
                    else if (extension.equalsIgnoreCase("mp4")) mimeType = "video/mp4";
                    else if (extension.equalsIgnoreCase("apk")) mimeType = "application/vnd.android.package-archive";
                }
            }

            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Open File..."));
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFileActionDialog(File file) { int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle); b.setTitle(file.getName()); b.setPositiveButton("Delete", (dialog, which) -> { if (file.delete()) openVisualDownloadsManager(); }); b.setNeutralButton("Open / Share", (dialog, which) -> openFileDirectly(file)); b.setNegativeButton("Cancel", null); AlertDialog dialog = b.show(); styleDialogButtons(dialog); }

    private void triggerAskBeforeDownload(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        if (fileName.endsWith(".bin")) { if (mimeType != null && mimeType.startsWith("image/")) fileName = fileName.replace(".bin", ".jpg"); else if (mimeType != null && mimeType.startsWith("video/")) fileName = fileName.replace(".bin", ".mp4"); else if (url.toLowerCase().contains(".jpg") || url.toLowerCase().contains(".jpeg")) fileName = fileName.replace(".bin", ".jpg"); else if (url.toLowerCase().contains(".png")) fileName = fileName.replace(".bin", ".png"); else if (url.toLowerCase().contains(".webp")) fileName = fileName.replace(".bin", ".webp"); else if (url.toLowerCase().contains(".mp4")) fileName = fileName.replace(".bin", ".mp4"); }
        if (mimeType != null) { if (mimeType.startsWith("image/") && !fileName.matches(".*\\.(jpg|jpeg|png|webp|gif)$")) fileName += ".jpg"; else if (mimeType.startsWith("video/") && !fileName.matches(".*\\.(mp4|mkv|webm)$")) fileName += ".mp4"; }
        int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle); b.setTitle("Download File?"); b.setMessage("Folder: OWN's Browser downloads\nFile: " + fileName); final String finalFileName = fileName;
        b.setPositiveButton("Download", (dialog, which) -> { try { DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)); request.setMimeType(mimeType); request.addRequestHeader("User-Agent", userAgent); request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "OWN's Browser downloads/" + finalFileName); DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE); if (dm != null) dm.enqueue(request); Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Download failed.", Toast.LENGTH_SHORT).show(); } }); b.setNegativeButton("Cancel", null); AlertDialog dialog = b.show(); styleDialogButtons(dialog);
    }

    private void toggleDesktopMode(WebView webView) {
        boolean isDesktop = false; if (webView.getTag() != null) isDesktop = (boolean) webView.getTag(); WebSettings settings = webView.getSettings(); if (defaultUserAgent == null) defaultUserAgent = settings.getUserAgentString();
        if (isDesktop) { settings.setUserAgentString(defaultUserAgent); settings.setUseWideViewPort(false); settings.setLoadWithOverviewMode(false); webView.setInitialScale(0); webView.setTag(false); Toast.makeText(this, "Mobile View...", Toast.LENGTH_SHORT).show(); } else { settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"); settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true); webView.setInitialScale(1); webView.setTag(true); Toast.makeText(this, "Desktop View...", Toast.LENGTH_SHORT).show(); }
        webView.reload();
    }

    private class JavascriptBridge {
        @JavascriptInterface @SuppressWarnings("unused") public void processLinkText(String text) { runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("Link Text", text)); Toast.makeText(PrivateBrowserActivity.this, "Copied Link Text!", Toast.LENGTH_SHORT).show(); } }); }
        @JavascriptInterface @SuppressWarnings("unused") public void handleVideoLongPress(String videoUrl) { if (videoUrl == null || videoUrl.isEmpty()) return; runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder b = new AlertDialog.Builder(PrivateBrowserActivity.this, dialogStyle); b.setTitle("Video Options"); String[] options = {"Copy Video Link", "Download Video (MP4)"}; b.setItems(options, (dialog, which) -> { if (which == 0) { ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (clip != null) { clip.setPrimaryClip(ClipData.newPlainText("Video URL", videoUrl)); Toast.makeText(PrivateBrowserActivity.this, "Video Link Copied!", Toast.LENGTH_SHORT).show(); } } else if (which == 1) { WebView current = getCurrentWeb(); String ua = current != null ? current.getSettings().getUserAgentString() : ""; triggerAskBeforeDownload(videoUrl, ua, null, "video/mp4", 0); } }); b.show(); }); }

        @JavascriptInterface @SuppressWarnings("unused") public void onVideoPlayState(boolean playing, boolean muted) { runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; isVideoCurrentlyPlaying = playing; isVideoCurrentlyMuted = muted; if(playing && isFullscreen) enableVideoMode(true, muted); else if(isVideoMode && !playing) btnVideoPlayPause.setImageResource(android.R.drawable.ic_media_play); }); }
        @JavascriptInterface @SuppressWarnings("unused") public void onVideoVolumeState(boolean muted) { runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; isVideoCurrentlyMuted = muted; if(btnVideoMute != null) btnVideoMute.setImageResource(muted ? android.R.drawable.ic_lock_silent_mode : android.R.drawable.ic_lock_silent_mode_off); }); }
        @JavascriptInterface @SuppressWarnings("unused") public void onVideoEnded() { runOnUiThread(() -> { if (isFinishing() || isDestroyed()) return; isVideoCurrentlyPlaying = false; disableVideoMode(); }); }
    }

    private void handleLongPress(WebView.HitTestResult result) {
        if (result.getExtra() == null) return; String url = result.getExtra(); int type = result.getType(); int dialogStyle = isDarkTheme ? android.R.style.Theme_DeviceDefault_Dialog_Alert : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert; AlertDialog.Builder b = new AlertDialog.Builder(this, dialogStyle);
        if (type == WebView.HitTestResult.IMAGE_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) { b.setTitle("Image Options"); String[] options = {"Copy Image Link", "Download Image", "Open Full View"}; b.setItems(options, (dialog, which) -> { if (which == 0) { ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (clip != null) { clip.setPrimaryClip(ClipData.newPlainText("Image URL", url)); Toast.makeText(this, "Image Link Copied!", Toast.LENGTH_SHORT).show(); } } else if (which == 1) { WebView current = getCurrentWeb(); String ua = current != null ? current.getSettings().getUserAgentString() : ""; triggerAskBeforeDownload(url, ua, null, "image/*", 0); } else if (which == 2) { createNewTab(url, false, true); } }); b.show(); } else if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE) { b.setTitle("Link Options"); String[] options = {"Copy Link Address", "Go To Link (New Tab)", "Copy Link Text"}; b.setItems(options, (dialog, which) -> { if (which == 0) { ClipboardManager clip = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE); if (clip != null) { clip.setPrimaryClip(ClipData.newPlainText("Link URL", url)); Toast.makeText(this, "Link Copied!", Toast.LENGTH_SHORT).show(); } } else if (which == 1) { createNewTab(url, false, true); } else if (which == 2) { WebView current = getCurrentWeb(); if (current != null) { String js = "(function(){ var links = document.getElementsByTagName('a'); for(var i=0; i<links.length; i++){ if(links[i].href === '" + url + "'){ OwnBrowser.processLinkText(links[i].innerText); return; } } })();"; current.evaluateJavascript(js, null); } } }); b.show(); }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private void setupSuperSecureWebView(WebView web) {
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setGeolocationEnabled(false);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        settings.setSaveFormData(false);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); }
        if (defaultUserAgent == null) defaultUserAgent = settings.getUserAgentString();
        String safeMobileAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(safeMobileAgent);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.addJavascriptInterface(new JavascriptBridge(), "OwnBrowser");
        web.addJavascriptInterface(new JavascriptBridge(), "control");
        web.setOnLongClickListener(v -> { WebView.HitTestResult result = ((WebView) v).getHitTestResult(); if (result.getType() == WebView.HitTestResult.IMAGE_TYPE || result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE || result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) { handleLongPress(result); return true; } return false; });
        web.setDownloadListener(this::triggerAskBeforeDownload);

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) { enterFullscreenVideo(view, callback); }
            @Override public void onHideCustomView() { exitFullscreenVideo(); }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                List<String> permissionsToRequest = new ArrayList<>();
                for (String resource : request.getResources()) {
                    if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(PrivateBrowserActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.CAMERA);
                        }
                    } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        if (ContextCompat.checkSelfPermission(PrivateBrowserActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
                        }
                    }
                }

                if (permissionsToRequest.isEmpty()) {
                    request.grant(request.getResources());
                } else {
                    mPendingPermissionRequest = request;
                    permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
                }
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                mPendingPermissionRequest = null;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (PrivateBrowserActivity.this.filePathCallback != null) {
                    PrivateBrowserActivity.this.filePathCallback.onReceiveValue(null);
                }
                PrivateBrowserActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    fallbackIntent.setType("*/*");
                    try {
                        fileChooserLauncher.launch(fallbackIntent);
                    } catch (Exception ex) {
                        PrivateBrowserActivity.this.filePathCallback = null;
                        return false;
                    }
                }
                return true;
            }

            @Override public void onProgressChanged(WebView view, int newProgress) {
                if (view == getCurrentWeb()) {
                    if (newProgress == 100) {
                        progressBar.setVisibility(View.GONE);
                        if(btnGo != null) btnGo.setLoading(false); // Triggers liquid morph out
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                        if(btnGo != null) btnGo.setLoading(true); // Triggers liquid morph in
                    }
                }
            }

            @Override public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                TabInfo info = getTabForWeb(view);
                if (info != null) {
                    info.title = title != null ? title : "New Tab";
                    saveSession();
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runOnUiThread(() -> {
                        TabInfo crashedTab = getTabForWeb(view);
                        if (crashedTab != null) {
                            ViewGroup parent = (ViewGroup) view.getParent();
                            if (parent != null) parent.removeView(view);
                            view.destroy();

                            WebView newWeb = new CustomWebView(PrivateBrowserActivity.this);
                            newWeb.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                            newWeb.setTag(false);
                            setupSuperSecureWebView(newWeb);

                            crashedTab.webView = newWeb;
                            webViewContainer.addView(newWeb);

                            if (tabs.indexOf(crashedTab) != currentTabIndex) {
                                newWeb.setVisibility(View.GONE);
                            }

                            boolean recovered = false;
                            if (crashedTab.savedStateBase64 != null) {
                                recovered = restoreWebViewState(newWeb, crashedTab.savedStateBase64);
                            }

                            if (!recovered) {
                                String lastUrl = view.getUrl();
                                newWeb.loadUrl(lastUrl != null ? lastUrl : "about:blank");
                            }

                            Toast.makeText(PrivateBrowserActivity.this, "Site recovered from memory limit.", Toast.LENGTH_SHORT).show();
                        }
                    });

                    return true;
                }
                return super.onRenderProcessGone(view, detail);
            }

            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) { super.onPageStarted(view, url, favicon); isVideoCurrentlyPlaying = false; disableVideoMode(); if (view == getCurrentWeb()) { if(btnGo != null) btnGo.setLoading(true); } if (view == getCurrentWeb() && !isFullscreen) { if (url == null || url.equals("about:blank") || url.startsWith("http://startpage") || url.isEmpty()) { etSearchUrl.setText(""); showHomePage(); } else { hideHomePage(); etSearchUrl.setText(url); } } }

            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                TabInfo info = getTabForWeb(view);
                if (info != null) {
                    String snapshot = encodeWebViewState(view);
                    if (snapshot != null) {
                        info.savedStateBase64 = snapshot;
                    }
                }

                saveSession();
                if (view == getCurrentWeb()) { if(btnGo != null) btnGo.setLoading(false); } Boolean isDesktop = (Boolean) view.getTag(); if (isDesktop != null && isDesktop) { view.evaluateJavascript("try { var meta = document.querySelector('meta[name=\"viewport\"]'); if (meta) { meta.setAttribute('content', 'width=1024'); } else { var m = document.createElement('meta'); m.name = 'viewport'; m.content = 'width=1024'; document.head.appendChild(m); } } catch(e) {}", null); } view.evaluateJavascript("document.addEventListener('contextmenu', function(e) { if(e.target.tagName === 'VIDEO') { OwnBrowser.handleVideoLongPress(e.target.src || e.target.currentSrc); } });", null);
                String videoJs = "document.addEventListener('play', function(e){ if(e.target.tagName==='VIDEO'){ window.activeVideo=e.target; OwnBrowser.onVideoPlayState(true, e.target.muted); } }, true); document.addEventListener('pause', function(e){ if(e.target.tagName==='VIDEO' && window.activeVideo===e.target){ OwnBrowser.onVideoPlayState(false, e.target.muted); } }, true); document.addEventListener('volumechange', function(e){ if(e.target===window.activeVideo){ OwnBrowser.onVideoVolumeState(e.target.muted || e.target.volume === 0); } }, true); document.addEventListener('ended', function(e){ if(e.target.tagName==='VIDEO' && window.activeVideo===e.target){ window.activeVideo=null; OwnBrowser.onVideoEnded(); } }, true);";
                view.evaluateJavascript(videoJs, null);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) { String url = request.getUrl().toString(); for (String domain : blockedDomains) { if (url.contains(domain)) { return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes())); } } return super.shouldInterceptRequest(view, request); }
        });
    }

    private void applyTheme() {
        int bgColor, textColor, hintColor, buttonBgColor, accentBgColor, accentTextColor, capsuleGlassColor, overlayGlassColor, urlInnerColor;
        if (themeState == 0) { bgColor = Color.parseColor("#FFFFFF"); textColor = Color.parseColor("#333333"); hintColor = Color.parseColor("#A0A0A0"); buttonBgColor = Color.parseColor("#FFFFFF"); accentBgColor = Color.parseColor("#6750A4"); accentTextColor = Color.parseColor("#FFFFFF"); capsuleGlassColor = Color.parseColor("#FFFFFF"); overlayGlassColor = Color.parseColor("#FFFFFF"); urlInnerColor = Color.parseColor("#E5E5EA"); }
        else if (themeState == 1) { bgColor = Color.parseColor("#1C1C1E"); textColor = Color.parseColor("#FFFFFF"); hintColor = Color.parseColor("#888888"); buttonBgColor = Color.parseColor("#332D2B"); accentBgColor = Color.parseColor("#FFB59F"); accentTextColor = Color.parseColor("#000000"); capsuleGlassColor = Color.parseColor("#2C2C2E"); overlayGlassColor = Color.parseColor("#1C1C1E"); urlInnerColor = Color.parseColor("#141415"); }
        else { bgColor = Color.parseColor("#000000"); textColor = Color.parseColor("#FFFFFF"); hintColor = Color.parseColor("#888888"); buttonBgColor = Color.parseColor("#1C1C1E"); accentBgColor = Color.parseColor("#FFB59F"); accentTextColor = Color.parseColor("#000000"); capsuleGlassColor = Color.parseColor("#1C1C1E"); overlayGlassColor = Color.parseColor("#000000"); urlInnerColor = Color.parseColor("#0A0A0A"); }

        getWindow().setStatusBarColor(bgColor);
        findViewById(R.id.browserRoot).setBackgroundColor(bgColor);

        GradientDrawable gd = new GradientDrawable(); gd.setColor(capsuleGlassColor); gd.setCornerRadius(dp(100));
        searchCapsule.setBackground(gd); searchCapsule.setClipToOutline(true);

        if (urlInputContainer != null) { GradientDrawable urlGd = new GradientDrawable(); urlGd.setColor(urlInnerColor); urlGd.setCornerRadius(dp(100)); urlInputContainer.setBackground(urlGd); urlInputContainer.setClipToOutline(true); }

        tabsOverlay.setBackgroundColor(overlayGlassColor); downloadsOverlay.setBackgroundColor(overlayGlassColor); homeOverlay.setBackgroundColor(bgColor);

        etSearchUrl.setTextColor(textColor); etSearchUrl.setHintTextColor(hintColor);
        btnFront.setColorFilter(textColor); btnGo.setColor(textColor);
        btnMenu.setColorFilter(textColor); ivAutoScrollIcon.setColorFilter(textColor); btnDismissSearch.setColorFilter(textColor);
        btnFullscreenToggle.setColorFilter(textColor);
        btnVideoPlayPause.setColorFilter(textColor); btnVideoHide.setColorFilter(textColor); btnVideoMute.setColorFilter(textColor);

        tvTabCount.setTextColor(textColor);
        GradientDrawable boxGd = new GradientDrawable();
        boxGd.setShape(GradientDrawable.RECTANGLE);
        boxGd.setCornerRadius(dp(6));
        boxGd.setStroke(dp(2), textColor);
        boxGd.setColor(Color.TRANSPARENT);
        tabBoxOutline.setBackground(boxGd);

        LinearLayout btnAddNewTab = findViewById(R.id.btnAddNewTab);
        TextView tvAddNewTabText = findViewById(R.id.tvAddNewTabText);
        ImageView ivAddNewTabIcon = findViewById(R.id.ivAddNewTabIcon);
        ImageView ivAddNewTabBg = findViewById(R.id.ivAddNewTabBg);
        ImageView btnCloseTabsOverlay = findViewById(R.id.btnCloseTabsOverlay);

        TextView tvDownloadsTitle = findViewById(R.id.tvDownloadsTitle);
        ImageView btnCloseDownloadsOverlay = findViewById(R.id.btnCloseDownloadsOverlay);
        TextView btnAllDownloads = findViewById(R.id.btnAllDownloads);

        int newTabBg, newTabTextCol, newTabIconCol, squircleCol;
        if (themeState == 0) {
            newTabBg = Color.parseColor("#E5E5EA");
            newTabTextCol = Color.parseColor("#000000");
            newTabIconCol = Color.parseColor("#000000");
            squircleCol = Color.parseColor("#FFFFFF");
        } else {
            newTabBg = Color.parseColor("#2C2C2E");
            newTabTextCol = Color.parseColor("#FFFFFF");
            newTabIconCol = Color.parseColor("#000000");
            squircleCol = Color.parseColor("#D4E4FF");
        }

        GradientDrawable newTabGd = new GradientDrawable();
        newTabGd.setColor(newTabBg);
        newTabGd.setCornerRadius(dp(100));
        if (btnAddNewTab != null) btnAddNewTab.setBackground(newTabGd);

        if (tvAddNewTabText != null) tvAddNewTabText.setTextColor(newTabTextCol);
        if (ivAddNewTabIcon != null) ivAddNewTabIcon.setColorFilter(newTabIconCol);

        if (ivAddNewTabBg != null) {
            ivAddNewTabBg.clearColorFilter();
            ivAddNewTabBg.setColorFilter(squircleCol, PorterDuff.Mode.SRC_IN);
        }

        int closeBgCol, closeIconCol;
        if (themeState == 0) {
            closeBgCol = Color.parseColor("#E5E5EA");
            closeIconCol = Color.parseColor("#000000");
        } else {
            closeBgCol = Color.parseColor("#3A3A3C");
            closeIconCol = Color.parseColor("#FFFFFF");
        }

        GradientDrawable closeGd = new GradientDrawable();
        closeGd.setShape(GradientDrawable.OVAL);
        closeGd.setColor(closeBgCol);

        btnCloseTabsOverlay.setBackground(closeGd);
        btnCloseTabsOverlay.setColorFilter(closeIconCol);

        tvDownloadsTitle.setTextColor(textColor);
        btnCloseDownloadsOverlay.setBackground(closeGd);
        btnCloseDownloadsOverlay.setColorFilter(closeIconCol);
        btnAllDownloads.setTextColor(textColor);
        btnAllDownloads.setBackgroundTintList(ColorStateList.valueOf(buttonBgColor));
    }

    private class CustomWebView extends WebView {
        public CustomWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection ic = super.onCreateInputConnection(outAttrs);
            if (ic == null) return null;

            String[] mimeTypes = new String[]{"image/png", "image/gif", "image/jpeg", "image/webp", "image/*"};
            EditorInfoCompat.setContentMimeTypes(outAttrs, mimeTypes);

            return InputConnectionCompat.createWrapper(ic, outAttrs, new InputConnectionCompat.OnCommitContentListener() {
                @Override
                public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                        try {
                            inputContentInfo.requestPermission();
                        } catch (Exception e) {
                            return false;
                        }
                    }

                    Uri uri = inputContentInfo.getContentUri();
                    injectImageToWebView(CustomWebView.this, uri, inputContentInfo);
                    return true;
                }
            });
        }
    }

    private void injectImageToWebView(WebView webView, Uri uri, InputContentInfoCompat inputContentInfo) {
        new Thread(() -> {
            try {
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) return;
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] imageBytes = buffer.toByteArray();

                String base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) mimeType = "image/jpeg";

                String js = "javascript:(function() {" +
                        "try {" +
                        "var byteStr = atob('" + base64 + "');" +
                        "var arr = new Uint8Array(byteStr.length);" +
                        "for (var i = 0; i < byteStr.length; i++) {" +
                        "  arr[i] = byteStr.charCodeAt(i);" +
                        "}" +
                        "var mime = '" + mimeType + "';" +
                        "var ext = mime.split('/')[1] || 'jpg';" +
                        "var blob = new Blob([arr], {type: mime});" +
                        "var file = new File([blob], 'pasted_image.' + ext, {type: mime});" +
                        "var dt = new DataTransfer();" +
                        "dt.items.add(file);" +
                        "var ev = new ClipboardEvent('paste', {" +
                        "  clipboardData: dt," +
                        "  bubbles: true," +
                        "  cancelable: true" +
                        "});" +
                        "document.activeElement.dispatchEvent(ev);" +
                        "} catch(e) { console.error(e); }" +
                        "})();";

                new Handler(Looper.getMainLooper()).post(() -> {
                    webView.evaluateJavascript(js, null);
                });

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && inputContentInfo != null) {
                    try { inputContentInfo.releasePermission(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        stopAutoActions();
        super.onDestroy();
    }
}