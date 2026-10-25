package com.abhinav.ownapp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Arrays;

public class StickerContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.abhinav.ownapp.stickercontentprovider";
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    private static final String[] METADATA_COLUMNS = new String[]{
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
    };

    private static final String[] STICKER_COLUMNS = new String[]{
            "sticker_file_name",
            "sticker_emoji",
            "sticker_accessibility_text"
    };

    static {
        MATCHER.addURI(AUTHORITY, "metadata", 1);
        MATCHER.addURI(AUTHORITY, "metadata/*", 2);
        MATCHER.addURI(AUTHORITY, "stickers/*", 3);
        MATCHER.addURI(AUTHORITY, "stickers_asset/*/*", 4);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    private String getDeviceId(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString("device_sticker_id", "");
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            prefs.edit().putString("device_sticker_id", deviceId).apply();
        }
        return deviceId;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                        @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        int match = MATCHER.match(uri);
        if (match == 1) {
            return getAllPacksMetadata();
        } else if (match == 2) {
            String packId = uri.getPathSegments().get(1);
            return getSinglePackMetadata(packId);
        } else if (match == 3) {
            String packId = uri.getPathSegments().get(1);
            return getStickersForPack(packId);
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor getAllPacksMetadata() {
        MatrixCursor cursor = new MatrixCursor(METADATA_COLUMNS);
        Context ctx = getContext();
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            int staticCount = prefs.getInt("static_pack_count", 3);
            int animCount = prefs.getInt("anim_pack_count", 1);
            String deviceId = getDeviceId(ctx);

            for (int i = 1; i <= staticCount; i++) {
                String packId = "ownpack_" + deviceId + "_" + i;
                File dir = new File(ctx.getFilesDir(), "stickers/" + packId);
                File[] files = getValidStickerFiles(dir, false);
                if (files != null && files.length >= 3) {
                    addPackRowToCursor(cursor, packId);
                }
            }
            for (int i = 1; i <= animCount; i++) {
                String packId = "ownanim_" + deviceId + "_" + i;
                File dir = new File(ctx.getFilesDir(), "stickers/" + packId);
                File[] files = getValidStickerFiles(dir, true);
                if (files != null && files.length >= 3) {
                    addPackRowToCursor(cursor, packId);
                }
            }
        }
        return cursor;
    }

    private Cursor getSinglePackMetadata(String packId) {
        MatrixCursor cursor = new MatrixCursor(METADATA_COLUMNS);
        Context ctx = getContext();
        if (ctx != null) {
            File dir = new File(ctx.getFilesDir(), "stickers/" + packId);
            if (!dir.exists() || !dir.isDirectory()) {
                return cursor;
            }
        }
        addPackRowToCursor(cursor, packId);
        return cursor;
    }

    private void addPackRowToCursor(MatrixCursor cursor, String packId) {
        Context ctx = getContext();
        int version = 3;
        String packName = "Pack";
        int isAnimatedPack = 0;

        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            version = prefs.getInt("sticker_version_" + packId, 3);
            packName = prefs.getString("pack_name_" + packId, "");

            boolean animFlag = packId.startsWith("ownanim_") || prefs.getBoolean("is_animated_" + packId, false);
            File dir = new File(ctx.getFilesDir(), "stickers/" + packId);
            if (!animFlag && dir.exists()) {
                animFlag = containsAnimatedWebp(dir);
            }
            isAnimatedPack = animFlag ? 1 : 0;

            if (packName == null || packName.trim().isEmpty()) {
                try {
                    String numStr = packId.substring(packId.lastIndexOf("_") + 1);
                    packName = (isAnimatedPack == 1 ? "Anim Pack " : "Pack ") + numStr;
                } catch (Exception e) {
                    packName = isAnimatedPack == 1 ? "Anim Pack" : "Pack";
                }
            }
        }

        cursor.addRow(new Object[]{
                packId,
                packName,
                "OWN app",
                "tray.png",
                "",
                "",
                "",
                "",
                "",
                "",
                String.valueOf(version),
                0,
                isAnimatedPack
        });
    }

    private boolean containsAnimatedWebp(File dir) {
        File[] files = getValidStickerFiles(dir, true);
        if (files != null && files.length > 0) {
            return isWebpFileAnimated(files[0]);
        }
        return false;
    }

    private boolean isWebpFileAnimated(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[24];
            if (fis.read(header) < 21) return false;
            if (header[0] != 'R' || header[1] != 'I' || header[2] != 'F' || header[3] != 'F') return false;
            if (header[8] != 'W' || header[9] != 'E' || header[10] != 'B' || header[11] != 'P') return false;
            if (header[12] == 'V' && header[13] == 'P' && header[14] == '8' && header[15] == 'X') {
                return (header[20] & 0x02) != 0;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private File[] getValidStickerFiles(File dir, boolean isAnim) {
        if (dir == null || !dir.exists()) return null;
        long maxBytes = isAnim ? 500000L : 100000L;
        return dir.listFiles((d, name) -> {
            if (!name.endsWith(".webp") || name.contains("_tmp") || name.contains("_thumb")) {
                return false;
            }
            long len = new File(d, name).length();
            return len > 100 && len <= maxBytes;
        });
    }

    private Cursor getStickersForPack(String packId) {
        MatrixCursor cursor = new MatrixCursor(STICKER_COLUMNS);
        Context context = getContext();
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(SnakeWidget.PREFS_NAME, Context.MODE_PRIVATE);
            boolean isAnim = packId.startsWith("ownanim_") || prefs.getBoolean("is_animated_" + packId, false);
            File dir = new File(context.getFilesDir(), "stickers/" + packId);
            File[] files = getValidStickerFiles(dir, isAnim);
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> {
                    try {
                        int n1 = Integer.parseInt(f1.getName().replace(".webp", ""));
                        int n2 = Integer.parseInt(f2.getName().replace(".webp", ""));
                        return Integer.compare(n1, n2);
                    } catch (Exception e) {
                        return f1.getName().compareTo(f2.getName());
                    }
                });
                String defaultEmojiCode = "\uD83D\uDE00";
                for (File file : files) {
                    cursor.addRow(new Object[]{file.getName(), defaultEmojiCode, ""});
                }
            }
        }
        return cursor;
    }

    @Nullable
    @Override
    public AssetFileDescriptor openAssetFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (uri.getPathSegments().size() >= 3 && uri.getPathSegments().get(0).equals("stickers_asset")) {
            String packId = uri.getPathSegments().get(1);
            String fileName = uri.getPathSegments().get(2);
            Context context = getContext();
            if (context == null) throw new FileNotFoundException("Context is null");

            File dir = new File(context.getFilesDir(), "stickers/" + packId);
            File file = new File(dir, fileName);

            if (fileName.equals("tray.png") && (!file.exists() || file.length() < 100 || file.length() > 49000)) {
                generateFallbackTrayIcon(dir, file);
            }

            if (!file.exists() || file.length() == 0) {
                throw new FileNotFoundException(file.getAbsolutePath());
            }

            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, file.length());
        }
        return super.openAssetFile(uri, mode);
    }

    private void generateFallbackTrayIcon(File dir, File trayFile) {
        try {
            File[] files = dir.listFiles((d, name) ->
                    name.endsWith(".webp") && !name.contains("_tmp") && !name.contains("_thumb"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (f1, f2) -> {
                    try {
                        return Integer.compare(
                                Integer.parseInt(f1.getName().replace(".webp", "")),
                                Integer.parseInt(f2.getName().replace(".webp", ""))
                        );
                    } catch (Exception e) {
                        return 0;
                    }
                });
                File thumb = new File(dir, files[0].getName().replace(".webp", "_thumb.png"));
                Bitmap src = thumb.exists()
                        ? BitmapFactory.decodeFile(thumb.getAbsolutePath())
                        : BitmapFactory.decodeFile(files[0].getAbsolutePath());
                if (src != null) {
                    Bitmap tray = Bitmap.createScaledBitmap(src, 96, 96, true);
                    try (FileOutputStream out = new FileOutputStream(trayFile)) {
                        tray.compress(Bitmap.CompressFormat.PNG, 80, out);
                    }
                    if (tray != src) tray.recycle();
                    src.recycle();
                }
            }
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        int match = MATCHER.match(uri);
        switch (match) {
            case 1:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".metadata";
            case 2:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".metadata";
            case 3:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + ".stickers";
            case 4:
                String path = uri.getLastPathSegment();
                if (path != null && path.endsWith(".png")) return "image/png";
                return "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        return 0;
    }
}