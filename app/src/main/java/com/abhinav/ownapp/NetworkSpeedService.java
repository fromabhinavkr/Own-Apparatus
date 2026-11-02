package com.abhinav.ownapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Locale;

@SuppressWarnings("all")
public class NetworkSpeedService extends Service {

    private static final String CHANNEL_ID = "own_net_speed_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS_DAILY = "own_net_daily_usage";
    private static final String KEY_DAY = "saved_day_yyyymmdd";
    private static final String KEY_MOBILE_BYTES = "today_mobile_bytes";
    private static final String KEY_WIFI_BYTES = "today_wifi_bytes";

    private static final int NET_NONE = 0;
    private static final int NET_MOBILE = 1;
    private static final int NET_WIFI = 2;

    private Handler handler;
    private Runnable speedRunnable;

    private String lastActiveIface = "";
    private long lastIfaceBytes = 0;
    private long lastTimeMs = 0;
    private long prevSecondSpeedBps = -1;

    private int currentDayKey = -1;
    private long todayMobileBytes = 0;
    private long todayWifiBytes = 0;

    private String lastIconTop = "";
    private String lastIconBottom = "";
    private String lastNotifTitle = "";
    private String lastNotifContent = "";
    private IconCompat cachedIcon = null;

    private boolean isScreenInteractive = true;

    private SharedPreferences dailyPrefs;
    private NotificationManager notificationManager;
    private ConnectivityManager connectivityManager;

    private Bitmap iconBitmap;
    private Canvas iconCanvas;
    private Paint speedPaint, unitPaint;

    // Reflection cache for API < 31 per-interface TrafficStats
    private Method methodGetRxBytesIface;
    private Method methodGetTxBytesIface;

    private final BroadcastReceiver systemEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                samplePhysicalNetwork(true);
                isScreenInteractive = false;
                if (handler != null) handler.removeCallbacks(speedRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                samplePhysicalNetwork(true);
                prevSecondSpeedBps = -1;
                isScreenInteractive = true;
                if (handler != null) {
                    handler.removeCallbacks(speedRunnable);
                    handler.post(speedRunnable);
                }
            } else if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) {
                checkMidnightReset();
                if (isScreenInteractive) updateNotification(0, true);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        dailyPrefs = getSharedPreferences(PREFS_DAILY, MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        initPerIfaceReflection();
        initDrawingTools();
        createNotificationChannel();
        loadDailyUsage();

        ActiveHardwareInterface hw = resolvePhysicalInterface();
        lastActiveIface = hw.ifaceName;
        lastIfaceBytes = getPhysicalInterfaceBytes(hw);
        lastTimeMs = SystemClock.elapsedRealtime();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) isScreenInteractive = pm.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(systemEventReceiver, filter);

        handler = new Handler(Looper.getMainLooper());
        speedRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isScreenInteractive) return;
                long speedBps = samplePhysicalNetwork(false);
                updateNotification(speedBps, false);
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        checkMidnightReset();
        String initialTitle = "Speed: 0 B/s";
        String initialContent = "Mobile: " + formatDailyData(todayMobileBytes) + "   WiFi: " + formatDailyData(todayWifiBytes);
        Notification initialNotification = buildNotification(initialTitle, initialContent, 0, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, initialNotification);
        }

        handler.removeCallbacks(speedRunnable);
        if (isScreenInteractive) handler.post(speedRunnable);
        return START_STICKY;
    }

    private void initPerIfaceReflection() {
        try {
            methodGetRxBytesIface = TrafficStats.class.getMethod("getRxBytes", String.class);
            methodGetTxBytesIface = TrafficStats.class.getMethod("getTxBytes", String.class);
        } catch (Exception ignored) {}
    }

    /**
     * Reads RX + TX bytes strictly from a single interface (e.g., "wlan0" or "rmnet_data0"),
     * matching Android SystemUI and excluding loopback ("lo"), ADB mirroring, and VPN/VoWiFi tunnels.
     */
    private long getRawIfaceBytes(String iface) {
        if (iface == null || iface.isEmpty()) return -1L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                long rx = TrafficStats.getRxBytes(iface);
                long tx = TrafficStats.getTxBytes(iface);
                if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED && (rx + tx) > 0) {
                    return rx + tx;
                }
            }
            if (methodGetRxBytesIface != null && methodGetTxBytesIface != null) {
                long rx = (long) methodGetRxBytesIface.invoke(null, iface);
                long tx = (long) methodGetTxBytesIface.invoke(null, iface);
                if (rx >= 0 && tx >= 0 && (rx + tx) > 0) {
                    return rx + tx;
                }
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    /**
     * Finds the true underlying physical hardware interface (wlan0, rmnet_dataX, ccmniX)
     * and strips out 464XLAT ("v4-") prefixes or VPN/VoWiFi ("tun", "ipsec", "epdg") virtual tunnels.
     */
    private ActiveHardwareInterface resolvePhysicalInterface() {
        if (connectivityManager == null) return new ActiveHardwareInterface(NET_NONE, "");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network[] networks = connectivityManager.getAllNetworks();
            if (networks != null) {
                String wifiIface = null;
                String cellIface = null;

                for (Network net : networks) {
                    NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(net);
                    if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                    // Ignore VPN or IMS/VoWiFi virtual networks so we only inspect physical transports
                    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;

                    LinkProperties lp = connectivityManager.getLinkProperties(net);
                    if (lp == null) continue;
                    String iface = lp.getInterfaceName();
                    if (iface == null || iface.isEmpty()) continue;

                    // If LinkProperties reports a 464XLAT clat interface like "v4-wlan0", strip "v4-"
                    if (iface.startsWith("v4-")) {
                        iface = iface.substring(3);
                    }
                    // Skip loopback or virtual tunnel interfaces
                    if (iface.startsWith("lo") || iface.startsWith("tun") || iface.startsWith("ipsec") || iface.startsWith("dummy")) {
                        continue;
                    }

                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        wifiIface = iface;
                    } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        cellIface = iface;
                    }
                }

                if (wifiIface != null) return new ActiveHardwareInterface(NET_WIFI, wifiIface);
                if (cellIface != null) return new ActiveHardwareInterface(NET_MOBILE, cellIface);
            }
        }
        return new ActiveHardwareInterface(NET_NONE, "");
    }

    /**
     * Reads exact bytes from the active physical hardware interface.
     * Falls back to total minus loopback ("lo") and mobile if a custom ROM blocks per-iface stats.
     */
    private long getPhysicalInterfaceBytes(ActiveHardwareInterface hw) {
        if (hw.netType == NET_NONE) return 0L;

        // 1. Direct hardware interface counter (e.g. "wlan0" or "rmnet_data0") — exact SystemUI method
        long directBytes = getRawIfaceBytes(hw.ifaceName);
        if (directBytes > 0) {
            return directBytes;
        }

        // 2. Fallback for Cellular: hardware mobile counter
        if (hw.netType == NET_MOBILE) {
            long mRx = TrafficStats.getMobileRxBytes();
            long mTx = TrafficStats.getMobileTxBytes();
            if (mRx != TrafficStats.UNSUPPORTED && mTx != TrafficStats.UNSUPPORTED && (mRx + mTx) > 0) {
                return mRx + mTx;
            }
        }

        // 3. Fallback for Wi-Fi: Total minus Mobile minus Localhost ("lo") minus 464XLAT ("v4-wlan0")
        long tRx = TrafficStats.getTotalRxBytes();
        long tTx = TrafficStats.getTotalTxBytes();
        if (tRx == TrafficStats.UNSUPPORTED || tTx == TrafficStats.UNSUPPORTED) return 0L;
        long total = tRx + tTx;

        long mRx = TrafficStats.getMobileRxBytes();
        long mTx = TrafficStats.getMobileTxBytes();
        long mobile = (mRx != TrafficStats.UNSUPPORTED && mTx != TrafficStats.UNSUPPORTED) ? (mRx + mTx) : 0L;

        long loBytes = Math.max(0L, getRawIfaceBytes("lo"));
        long clatBytes = Math.max(0L, getRawIfaceBytes("v4-" + hw.ifaceName));

        return Math.max(0L, total - mobile - loBytes - clatBytes);
    }

    /**
     * Samples the active physical interface and applies SystemUI's 2-second rolling window
     * so status bar readings match the built-in system meter.
     */
    private long samplePhysicalNetwork(boolean isScreenTransition) {
        checkMidnightReset();

        ActiveHardwareInterface hw = resolvePhysicalInterface();
        long currentBytes = getPhysicalInterfaceBytes(hw);
        long currentTimeMs = SystemClock.elapsedRealtime();

        long timeDeltaMs = currentTimeMs - lastTimeMs;
        if (timeDeltaMs <= 0) timeDeltaMs = 1000;

        // If user switched between Wi-Fi and Mobile Data, reset baseline cleanly without a spike
        if (!hw.ifaceName.equals(lastActiveIface)) {
            lastActiveIface = hw.ifaceName;
            lastIfaceBytes = currentBytes;
            lastTimeMs = currentTimeMs;
            prevSecondSpeedBps = -1;
            return 0L;
        }

        long byteDelta = (currentBytes >= lastIfaceBytes && lastIfaceBytes > 0) ? (currentBytes - lastIfaceBytes) : 0L;

        lastIfaceBytes = currentBytes;
        lastTimeMs = currentTimeMs;

        if (byteDelta > 0) {
            if (hw.netType == NET_MOBILE) {
                todayMobileBytes += byteDelta;
                saveDailyUsage();
            } else if (hw.netType == NET_WIFI) {
                todayWifiBytes += byteDelta;
                saveDailyUsage();
            }
        }

        if (isScreenTransition || hw.netType == NET_NONE) {
            prevSecondSpeedBps = -1;
            return 0L;
        }

        long rawSpeedBps = (byteDelta * 1000L) / timeDeltaMs;

        // SystemUI 2-second rolling average so bursty TCP segments match the native status bar meter
        long smoothedSpeedBps;
        if (rawSpeedBps == 0L) {
            smoothedSpeedBps = 0L;
        } else if (prevSecondSpeedBps >= 0L) {
            smoothedSpeedBps = (rawSpeedBps + prevSecondSpeedBps) / 2L;
        } else {
            smoothedSpeedBps = rawSpeedBps;
        }
        prevSecondSpeedBps = rawSpeedBps;

        return smoothedSpeedBps;
    }

    private void loadDailyUsage() {
        int today = getTodayDateKey();
        currentDayKey = dailyPrefs.getInt(KEY_DAY, -1);
        if (currentDayKey != today) {
            currentDayKey = today;
            todayMobileBytes = 0L;
            todayWifiBytes = 0L;
            saveDailyUsage();
        } else {
            todayMobileBytes = dailyPrefs.getLong(KEY_MOBILE_BYTES, 0L);
            todayWifiBytes = dailyPrefs.getLong(KEY_WIFI_BYTES, 0L);
        }
    }

    private void checkMidnightReset() {
        int today = getTodayDateKey();
        if (today != currentDayKey) {
            currentDayKey = today;
            todayMobileBytes = 0L;
            todayWifiBytes = 0L;
            saveDailyUsage();
        }
    }

    private int getTodayDateKey() {
        Calendar cal = Calendar.getInstance();
        return (cal.get(Calendar.YEAR) * 10000) + ((cal.get(Calendar.MONTH) + 1) * 100) + cal.get(Calendar.DAY_OF_MONTH);
    }

    private void saveDailyUsage() {
        dailyPrefs.edit()
                .putInt(KEY_DAY, currentDayKey)
                .putLong(KEY_MOBILE_BYTES, todayMobileBytes)
                .putLong(KEY_WIFI_BYTES, todayWifiBytes)
                .apply();
    }

    private void updateNotification(long bytesPerSec, boolean forceRedraw) {
        String title = "Speed: " + formatNotificationSpeed(bytesPerSec);
        String content = "Mobile: " + formatDailyData(todayMobileBytes) + "   WiFi: " + formatDailyData(todayWifiBytes);

        if (!forceRedraw && title.equals(lastNotifTitle) && content.equals(lastNotifContent)) {
            return;
        }
        lastNotifTitle = title;
        lastNotifContent = content;

        Notification notification = buildNotification(title, content, bytesPerSec, forceRedraw);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String title, String content, long bytesPerSec, boolean forceRedraw) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        IconCompat dynamicIcon = getOrCreateStatusBarIcon(bytesPerSec, forceRedraw);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dynamicIcon)
                .setColor(Color.parseColor("#4CD964"))
                .setContentTitle(title)
                .setContentText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private IconCompat getOrCreateStatusBarIcon(long bytesPerSec, boolean forceRedraw) {
        String speedVal;
        String unitVal;

        if (bytesPerSec < 1048576L) {
            long kb = Math.round(bytesPerSec / 1024.0);
            speedVal = String.valueOf(kb);
            unitVal = "KB/s";
        } else {
            double mb = bytesPerSec / 1048576.0;
            speedVal = mb < 10 ? String.format(Locale.US, "%.1f", mb) : String.format(Locale.US, "%.0f", mb);
            unitVal = "MB/s";
        }

        if (!forceRedraw && cachedIcon != null && speedVal.equals(lastIconTop) && unitVal.equals(lastIconBottom)) {
            return cachedIcon;
        }
        lastIconTop = speedVal;
        lastIconBottom = unitVal;

        iconBitmap.eraseColor(Color.TRANSPARENT);

        if (speedVal.length() <= 2) {
            speedPaint.setTextSize(58f);
        } else if (speedVal.length() == 3) {
            speedPaint.setTextSize(46f);
        } else {
            speedPaint.setTextSize(38f);
        }

        iconCanvas.drawText(speedVal, 48f, 50f, speedPaint);
        iconCanvas.drawText(unitVal, 48f, 88f, unitPaint);

        Bitmap safeCopy = iconBitmap.copy(Bitmap.Config.ARGB_8888, false);
        cachedIcon = IconCompat.createWithBitmap(safeCopy);
        return cachedIcon;
    }

    private String formatNotificationSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024L) {
            return bytesPerSec + " B/s";
        } else if (bytesPerSec < 1048576L) {
            double kb = bytesPerSec / 1024.0;
            return kb < 10 ? String.format(Locale.US, "%.1f KB/s", kb) : String.format(Locale.US, "%.0f KB/s", kb);
        } else {
            double mb = bytesPerSec / 1048576.0;
            return String.format(Locale.US, "%.1f MB/s", mb);
        }
    }

    private String formatDailyData(long bytes) {
        if (bytes < 102400L) {
            return "0 MB";
        } else if (bytes < 1073741824L) {
            double mb = bytes / 1048576.0;
            return mb < 100 ? String.format(Locale.US, "%.1f MB", mb) : String.format(Locale.US, "%.0f MB", mb);
        } else {
            double gb = bytes / 1073741824.0;
            return String.format(Locale.US, "%.2f GB", gb);
        }
    }

    private void initDrawingTools() {
        iconBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        iconCanvas = new Canvas(iconBitmap);

        speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        speedPaint.setColor(Color.WHITE);
        speedPaint.setTextSize(58f);
        speedPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        speedPaint.setTextAlign(Paint.Align.CENTER);

        unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitPaint.setColor(Color.WHITE);
        unitPaint.setTextSize(32f);
        unitPaint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        unitPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Network Speed",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Displays real-time network speed and daily data usage");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null && speedRunnable != null) handler.removeCallbacks(speedRunnable);
        try { unregisterReceiver(systemEventReceiver); } catch (Exception ignored) {}
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static class ActiveHardwareInterface {
        final int netType;
        final String ifaceName;

        ActiveHardwareInterface(int netType, String ifaceName) {
            this.netType = netType;
            this.ifaceName = ifaceName != null ? ifaceName : "";
        }
    }
}