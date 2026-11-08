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
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@SuppressWarnings("all")
public class NetworkSpeedService extends Service {

    private static final String OLD_CHANNEL_ID = "own_net_speed_channel";
    private static final String CHANNEL_ID = "own_net_speed_channel_v2";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS_DAILY = "own_net_daily_usage";
    private static final String KEY_DAY = "saved_day_yyyymmdd";
    private static final String KEY_MOBILE_BYTES = "today_mobile_bytes";
    private static final String KEY_WIFI_BYTES = "today_wifi_bytes";

    private static final int NET_NONE = 0;
    private static final int NET_MOBILE = 1;
    private static final int NET_WIFI = 2;

    private static final String[] WIFI_CANDIDATES = {
            "wlan0"
    };

    private static final String[] CELL_CANDIDATES = {
            "rmnet_data0", "rmnet_data1", "rmnet_data2", "rmnet_data3",
            "rmnet_data4", "rmnet_data5", "rmnet_data6", "rmnet_data7",
            "ccmni0", "ccmni1", "ccmni2", "ccmni3",
            "ccmni4", "ccmni5", "ccmni6", "ccmni7",
            "rmnet0", "rmnet1", "rmnet2", "rmnet3",
            "wwan0", "wwan1", "pdp0", "seth_w0", "seth_w1"
    };

    private Handler handler;
    private Runnable speedRunnable;

    private final Set<String> activeWifiIfaces = new HashSet<>();
    private final Set<String> activeCellIfaces = new HashSet<>();
    private final Set<String> activeImsOrNonInternetIfaces = new HashSet<>();
    private final Set<String> activeTetherIfaces = new HashSet<>();
    private final Set<String> activeClatIfaces = new HashSet<>();
    private final Set<String> activeVpnIfaces = new HashSet<>();
    private final Set<String> activeAggregatorIfaces = new HashSet<>();

    private boolean needsIfaceRefresh = true;
    private int ticksSinceLastDiscovery = 0;

    private long lastWifiIfaceBytes = -1;
    private long lastCellIfaceBytes = -1;
    private long lastSdkMobileBytes = -1;
    private long lastCleanTotalBytes = -1;
    private long lastTimeMs = 0;
    private long serviceStartTimeMs = 0;
    private boolean isFirstForegroundStart = true;

    private int currentDayKey = -1;
    private long todayMobileBytes = 0;
    private long todayWifiBytes = 0;

    private String lastNotifTitle = "";
    private String lastNotifContent = "";
    private String lastIconTop = "";
    private String lastIconBottom = "";

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

    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            triggerImmediateRefresh();
        }

        @Override
        public void onLost(@NonNull Network network) {
            triggerImmediateRefresh();
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
            triggerImmediateRefresh();
        }

        @Override
        public void onLinkPropertiesChanged(@NonNull Network network, @NonNull LinkProperties linkProperties) {
            triggerImmediateRefresh();
        }
    };

    private void triggerImmediateRefresh() {
        needsIfaceRefresh = true;
        if (isScreenInteractive && handler != null) {
            handler.post(() -> {
                long speed = samplePhysicalNetwork(false);
                updateNotification(speed, true);
            });
        }
    }

    private final BroadcastReceiver systemEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                samplePhysicalNetwork(true);
                isScreenInteractive = false;
                if (handler != null) handler.removeCallbacks(speedRunnable);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                needsIfaceRefresh = true;
                samplePhysicalNetwork(true);
                isScreenInteractive = true;
                if (handler != null) {
                    handler.removeCallbacks(speedRunnable);
                    handler.post(() -> {
                        updateNotification(0, true);
                        handler.postDelayed(speedRunnable, 1000);
                    });
                }
            } else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                triggerImmediateRefresh();
            } else if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)) {
                checkMidnightReset();
                if (isScreenInteractive) updateNotification(0, true);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        serviceStartTimeMs = System.currentTimeMillis();
        dailyPrefs = getSharedPreferences(PREFS_DAILY, MODE_PRIVATE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        initPerIfaceReflection();
        initDrawingTools();
        createNotificationChannel();
        loadDailyUsage();

        discoverActiveInterfaces();
        captureBaselines();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) isScreenInteractive = pm.isInteractive();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(systemEventReceiver, filter);

        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }

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

        if (isFirstForegroundStart || !isStatusBarNotificationVisible()) {
            isFirstForegroundStart = false;
            String initialTitle = lastNotifTitle.isEmpty() ? "Speed: 0 B/s" : lastNotifTitle;
            String initialContent = "Mobile: " + formatDailyData(todayMobileBytes) + "   WiFi: " + formatDailyData(todayWifiBytes);
            lastNotifTitle = initialTitle;
            lastNotifContent = initialContent;

            Notification initialNotification = buildNotification(initialTitle, initialContent, 0);
            promoteToForeground(initialNotification);
        }

        handler.removeCallbacks(speedRunnable);
        if (isScreenInteractive) {
            handler.postDelayed(speedRunnable, 1000);
        }
        return START_STICKY;
    }

    private void promoteToForeground(Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    private boolean isStatusBarNotificationVisible() {
        if (notificationManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            StatusBarNotification[] active = notificationManager.getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) {
                    if (sbn.getId() == NOTIFICATION_ID) return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return true;
        }
    }

    private void initPerIfaceReflection() {
        try {
            methodGetRxBytesIface = TrafficStats.class.getMethod("getRxBytes", String.class);
            methodGetTxBytesIface = TrafficStats.class.getMethod("getTxBytes", String.class);
        } catch (Exception ignored) {}
    }

    private long getRawIfaceBytes(String iface) {
        if (iface == null || iface.isEmpty()) return -1L;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                long rx = TrafficStats.getRxBytes(iface);
                long tx = TrafficStats.getTxBytes(iface);
                if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED && (rx + tx) >= 0) {
                    return rx + tx;
                }
            }
            if (methodGetRxBytesIface != null && methodGetTxBytesIface != null) {
                long rx = (long) methodGetRxBytesIface.invoke(null, iface);
                long tx = (long) methodGetTxBytesIface.invoke(null, iface);
                if (rx >= 0 && tx >= 0 && (rx + tx) >= 0) {
                    return rx + tx;
                }
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    /**
     * Checks if the user's Mobile Data toggle in Android Settings is actually enabled.
     * Prevents VoLTE/IMS keep-alive traffic from being mistaken for mobile data when Mobile Data is OFF.
     */
    private boolean isMobileDataSettingEnabled() {
        try {
            return Settings.Global.getInt(getContentResolver(), "mobile_data", 1) == 1;
        } catch (Exception ignored) {
            return true;
        }
    }

    /**
     * Strictly determines whether the device has an active, connected Internet network.
     * Ignores VoLTE (IMS), MMS, Hotspot/Tethering, and disconnected interfaces.
     */
    private int getActiveNetworkType() {
        if (connectivityManager == null) return NET_NONE;
        try {
            // Legacy check: if the system reports no connected network at all, return NET_NONE immediately
            NetworkInfo activeInfo = connectivityManager.getActiveNetworkInfo();
            if (activeInfo == null || !activeInfo.isConnected()) {
                return NET_NONE;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNet = connectivityManager.getActiveNetwork();
                if (activeNet == null) return NET_NONE;

                NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNet);
                if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return NET_NONE;
                }

                // Direct physical transport on default network
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return NET_WIFI;
                }
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return isMobileDataSettingEnabled() ? NET_MOBILE : NET_NONE;
                }

                // If a VPN is active on top of the physical network, inspect the underlying physical transport
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    Network[] networks = connectivityManager.getAllNetworks();
                    if (networks != null) {
                        boolean hasCellInternet = false;
                        for (Network net : networks) {
                            NetworkCapabilities c = connectivityManager.getNetworkCapabilities(net);
                            if (c == null) continue;
                            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                            if (!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) continue;

                            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                                return NET_WIFI;
                            }
                            if (c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && isMobileDataSettingEnabled()) {
                                hasCellInternet = true;
                            }
                        }
                        if (hasCellInternet) return NET_MOBILE;
                    }
                }
                return NET_NONE;
            } else {
                if (activeInfo.getType() == ConnectivityManager.TYPE_WIFI) return NET_WIFI;
                if (activeInfo.getType() == ConnectivityManager.TYPE_MOBILE && isMobileDataSettingEnabled()) {
                    return NET_MOBILE;
                }
            }
        } catch (Exception ignored) {}
        return NET_NONE;
    }

    private void classifyAndRegisterIface(String rawName, int netTypeHint) {
        if (rawName == null || rawName.isEmpty()) return;
        String name = rawName.trim();
        if (name.equals("lo") || name.startsWith("dummy")) return;

        // 464XLAT clat virtual interface (e.g. "v4-rmnet_data0" or "v4-wlan0")
        if (name.startsWith("v4-")) {
            if (getRawIfaceBytes(name) > 0) activeClatIfaces.add(name);
            String base = name.substring(3);
            classifyAndRegisterIface(base, netTypeHint);
            return;
        }

        // VPN / IPSec / VoWiFi virtual tunnel interfaces
        if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("ipsec") || name.startsWith("epdg")) {
            if (getRawIfaceBytes(name) > 0) activeVpnIfaces.add(name);
            return;
        }

        // Hotspot / Wi-Fi Direct / USB Tethering interfaces (NEVER count as user Wi-Fi speed)
        if (name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("p2p")
                || name.startsWith("aware") || name.startsWith("rndis") || name.startsWith("ncm")
                || name.equals("wlan1") || name.equals("wlan2")) {
            if (netTypeHint != NET_WIFI) {
                if (getRawIfaceBytes(name) > 0) activeTetherIfaces.add(name);
                return;
            }
        }

        // Qualcomm IPA / MHI hardware aggregator interfaces
        if (name.startsWith("rmnet_ipa") || name.startsWith("rmnet_mhi")) {
            if (getRawIfaceBytes(name) > 0) activeAggregatorIfaces.add(name);
            return;
        }

        // Ignore interfaces explicitly identified as VoLTE/IMS/MMS-only
        if (activeImsOrNonInternetIfaces.contains(name)) {
            return;
        }

        long bytes = getRawIfaceBytes(name);
        if (bytes <= 0) return;

        if (netTypeHint == NET_WIFI || name.equals("wlan0")) {
            activeWifiIfaces.add(name);
            if (getRawIfaceBytes("v4-" + name) > 0) activeClatIfaces.add("v4-" + name);
        } else if (netTypeHint == NET_MOBILE
                || name.startsWith("rmnet")
                || name.startsWith("ccmni")
                || name.startsWith("wwan")
                || name.startsWith("pdp")
                || name.startsWith("seth_w")) {
            activeCellIfaces.add(name);
            if (getRawIfaceBytes("v4-" + name) > 0) activeClatIfaces.add("v4-" + name);
        }
    }

    /**
     * Discovers active physical Wi-Fi and Cellular internet interfaces while isolating
     * VoLTE (IMS), Hotspot/Tethering, 464XLAT, and VPN virtual interfaces.
     */
    private void discoverActiveInterfaces() {
        needsIfaceRefresh = false;
        ticksSinceLastDiscovery = 0;

        activeWifiIfaces.clear();
        activeCellIfaces.clear();
        activeImsOrNonInternetIfaces.clear();
        activeTetherIfaces.clear();

        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Network[] networks = connectivityManager.getAllNetworks();
                if (networks != null) {
                    // Pass 1: Identify non-internet cellular bearers (VoLTE IMS, MMS, SUPL, EIMS)
                    for (Network net : networks) {
                        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(net);
                        LinkProperties lp = connectivityManager.getLinkProperties(net);
                        if (caps == null || lp == null) continue;
                        String iface = lp.getInterfaceName();
                        if (iface == null || iface.isEmpty()) continue;
                        if (iface.startsWith("v4-")) iface = iface.substring(3);

                        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                        if (!hasInternet) {
                            activeImsOrNonInternetIfaces.add(iface);
                        }
                    }

                    // Pass 2: Register Internet-capable networks
                    for (Network net : networks) {
                        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(net);
                        LinkProperties lp = connectivityManager.getLinkProperties(net);
                        if (caps == null || lp == null) continue;
                        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;

                        int hint = NET_NONE;
                        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) hint = NET_WIFI;
                        else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) hint = NET_MOBILE;

                        classifyAndRegisterIface(lp.getInterfaceName(), hint);
                    }
                }
            } catch (Exception ignored) {}
        }

        for (String w : WIFI_CANDIDATES) {
            classifyAndRegisterIface(w, NET_WIFI);
        }
        for (String c : CELL_CANDIDATES) {
            classifyAndRegisterIface(c, NET_MOBILE);
        }
        classifyAndRegisterIface("rmnet_ipa0", NET_NONE);
        classifyAndRegisterIface("rmnet_mhi0", NET_NONE);
        classifyAndRegisterIface("tun0", NET_NONE);

        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            if (en != null) {
                while (en.hasMoreElements()) {
                    NetworkInterface ni = en.nextElement();
                    if (ni != null && ni.isUp()) {
                        classifyAndRegisterIface(ni.getName(), NET_NONE);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private long sumInterfaceSet(Set<String> ifaces) {
        long total = 0L;
        for (String iface : ifaces) {
            long b = getRawIfaceBytes(iface);
            if (b > 0) total += b;
        }
        return total;
    }

    private long readSdkMobileBytes() {
        long mRx = TrafficStats.getMobileRxBytes();
        long mTx = TrafficStats.getMobileTxBytes();
        if (mRx == TrafficStats.UNSUPPORTED || mTx == TrafficStats.UNSUPPORTED) return 0L;
        long rawMobile = Math.max(0L, mRx + mTx);
        long imsBytes = sumInterfaceSet(activeImsOrNonInternetIfaces);
        return Math.max(0L, rawMobile - imsBytes);
    }

    private long readCleanTotalBytes(long cellIfaceSum) {
        long tRx = TrafficStats.getTotalRxBytes();
        long tTx = TrafficStats.getTotalTxBytes();
        if (tRx == TrafficStats.UNSUPPORTED || tTx == TrafficStats.UNSUPPORTED) return 0L;
        long total = Math.max(0L, tRx + tTx);

        long loBytes = Math.max(0L, getRawIfaceBytes("lo"));
        long clatBytes = sumInterfaceSet(activeClatIfaces);
        long vpnBytes = sumInterfaceSet(activeVpnIfaces);
        long tetherBytes = sumInterfaceSet(activeTetherIfaces);
        long imsBytes = sumInterfaceSet(activeImsOrNonInternetIfaces);
        long aggBytes = (cellIfaceSum > 0) ? sumInterfaceSet(activeAggregatorIfaces) : 0L;

        return Math.max(0L, total - loBytes - clatBytes - vpnBytes - tetherBytes - imsBytes - aggBytes);
    }

    private void captureBaselines() {
        long wifiSum = sumInterfaceSet(activeWifiIfaces);
        long cellSum = sumInterfaceSet(activeCellIfaces);
        lastWifiIfaceBytes = wifiSum;
        lastCellIfaceBytes = cellSum;
        lastSdkMobileBytes = readSdkMobileBytes();
        lastCleanTotalBytes = readCleanTotalBytes(cellSum);
        lastTimeMs = SystemClock.elapsedRealtime();
    }

    /**
     * Samples physical network traffic strictly for the currently connected Internet transport.
     * Guaranteed to return 0 B/s immediately when Wi-Fi and Mobile Data are turned off.
     */
    private long samplePhysicalNetwork(boolean isScreenTransition) {
        checkMidnightReset();

        ticksSinceLastDiscovery++;
        if (needsIfaceRefresh || ticksSinceLastDiscovery >= 5) {
            Set<String> prevWifi = new HashSet<>(activeWifiIfaces);
            Set<String> prevCell = new HashSet<>(activeCellIfaces);
            discoverActiveInterfaces();
            if (!prevWifi.equals(activeWifiIfaces) || !prevCell.equals(activeCellIfaces)) {
                lastWifiIfaceBytes = sumInterfaceSet(activeWifiIfaces);
                lastCellIfaceBytes = sumInterfaceSet(activeCellIfaces);
                lastSdkMobileBytes = readSdkMobileBytes();
                lastCleanTotalBytes = readCleanTotalBytes(lastCellIfaceBytes);
            }
        }

        long currentTimeMs = SystemClock.elapsedRealtime();
        long timeDeltaMs = currentTimeMs - lastTimeMs;
        if (timeDeltaMs <= 0) timeDeltaMs = 1000;

        long curWifiIface = sumInterfaceSet(activeWifiIfaces);
        long curCellIface = sumInterfaceSet(activeCellIfaces);
        long curSdkMobile = readSdkMobileBytes();
        long curCleanTotal = readCleanTotalBytes(curCellIface);

        long deltaWifiIface = (lastWifiIfaceBytes > 0 && curWifiIface >= lastWifiIfaceBytes)
                ? (curWifiIface - lastWifiIfaceBytes) : 0L;
        long deltaCellIface = (lastCellIfaceBytes > 0 && curCellIface >= lastCellIfaceBytes)
                ? (curCellIface - lastCellIfaceBytes) : 0L;
        long deltaSdkMobile = (lastSdkMobileBytes > 0 && curSdkMobile >= lastSdkMobileBytes)
                ? (curSdkMobile - lastSdkMobileBytes) : 0L;
        long deltaCleanTotal = (lastCleanTotalBytes > 0 && curCleanTotal >= lastCleanTotalBytes)
                ? (curCleanTotal - lastCleanTotalBytes) : 0L;

        lastWifiIfaceBytes = curWifiIface;
        lastCellIfaceBytes = curCellIface;
        lastSdkMobileBytes = curSdkMobile;
        lastCleanTotalBytes = curCleanTotal;
        lastTimeMs = currentTimeMs;

        int activeType = getActiveNetworkType();

        // HARD CUTOFF: If neither Wi-Fi nor Mobile Data is connected to the Internet, return 0 immediately
        if (isScreenTransition || activeType == NET_NONE) {
            return 0L;
        }

        long activeBytesDelta = 0L;

        if (activeType == NET_WIFI) {
            activeBytesDelta = deltaWifiIface;
            if (activeBytesDelta == 0L && deltaCleanTotal > 0L) {
                // Subtract any simultaneous cellular delta so Wi-Fi fallback is pure Wi-Fi
                long cellNoise = Math.max(deltaCellIface, deltaSdkMobile);
                activeBytesDelta = Math.max(0L, deltaCleanTotal - cellNoise);
            }
            if (activeBytesDelta > 0L) {
                todayWifiBytes += activeBytesDelta;
                saveDailyUsage();
            }
        } else if (activeType == NET_MOBILE) {
            activeBytesDelta = deltaCellIface;
            if (activeBytesDelta == 0L && deltaSdkMobile > 0L) {
                activeBytesDelta = deltaSdkMobile;
            }
            if (activeBytesDelta == 0L && deltaCleanTotal > 0L) {
                activeBytesDelta = Math.max(0L, deltaCleanTotal - deltaWifiIface);
            }
            if (activeBytesDelta > 0L) {
                todayMobileBytes += activeBytesDelta;
                saveDailyUsage();
            }
        }

        return (activeBytesDelta * 1000L) / timeDeltaMs;
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

        String[] iconParts = formatStatusBarSpeedParts(bytesPerSec);
        boolean iconChanged = !iconParts[0].equals(lastIconTop) || !iconParts[1].equals(lastIconBottom);
        boolean textChanged = !title.equals(lastNotifTitle) || !content.equals(lastNotifContent);
        boolean notifMissing = !isStatusBarNotificationVisible();

        if (!forceRedraw && !iconChanged && !textChanged && !notifMissing) {
            return;
        }

        lastNotifTitle = title;
        lastNotifContent = content;
        lastIconTop = iconParts[0];
        lastIconBottom = iconParts[1];

        Notification notification = buildNotification(title, content, bytesPerSec);
        if (notifMissing) {
            promoteToForeground(notification);
        } else if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String title, String content, long bytesPerSec) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        IconCompat dynamicIcon = createStatusBarIcon(bytesPerSec);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dynamicIcon)
                .setColor(Color.parseColor("#4CD964"))
                .setContentTitle(title)
                .setContentText(content)
                .setWhen(serviceStartTimeMs)
                .setShowWhen(false)
                .setSortKey("00_net_speed")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDefaults(0)
                .setSound(null)
                .setVibrate(null)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        notification.flags |= Notification.FLAG_FOREGROUND_SERVICE
                | Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_NO_CLEAR;

        return notification;
    }

    private String[] formatStatusBarSpeedParts(long bytesPerSec) {
        if (bytesPerSec <= 0L) {
            return new String[]{"0", "KB/s"};
        } else if (bytesPerSec < 1024L) {
            return new String[]{String.valueOf(bytesPerSec), "B/s"};
        } else if (bytesPerSec < 1048576L) {
            double kb = bytesPerSec / 1024.0;
            String speedVal = kb < 10 ? String.format(Locale.US, "%.1f", kb) : String.format(Locale.US, "%.0f", kb);
            return new String[]{speedVal, "KB/s"};
        } else {
            double mb = bytesPerSec / 1048576.0;
            String speedVal = mb < 10 ? String.format(Locale.US, "%.1f", mb) : String.format(Locale.US, "%.0f", mb);
            return new String[]{speedVal, "MB/s"};
        }
    }

    private IconCompat createStatusBarIcon(long bytesPerSec) {
        String[] parts = formatStatusBarSpeedParts(bytesPerSec);
        String speedVal = parts[0];
        String unitVal = parts[1];

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
        return IconCompat.createWithBitmap(safeCopy);
    }

    private String formatNotificationSpeed(long bytesPerSec) {
        if (bytesPerSec < 1024L) {
            return bytesPerSec + " B/s";
        } else if (bytesPerSec < 1048576L) {
            double kb = bytesPerSec / 1024.0;
            return kb < 100 ? String.format(Locale.US, "%.2f KB/s", kb) : String.format(Locale.US, "%.0f KB/s", kb);
        } else {
            double mb = bytesPerSec / 1048576.0;
            return String.format(Locale.US, "%.2f MB/s", mb);
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
            try {
                notificationManager.deleteNotificationChannel(OLD_CHANNEL_ID);
            } catch (Exception ignored) {}

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Network Speed",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Displays real-time network speed and daily data usage in the status bar");
            channel.setSound(null, null);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (handler != null && speedRunnable != null) handler.removeCallbacks(speedRunnable);
        try { unregisterReceiver(systemEventReceiver); } catch (Exception ignored) {}
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}