package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("all")
@SuppressLint("ViewConstructor")
public class Thrive2ndActivity extends View implements Choreographer.FrameCallback {

    public interface GameListener {
        void onScoreUpdated(int score);
        void onGameOver(int finalScore, int killsDone, int[] weaponUsage, Bitmap deathSnapshot, String deathCause);
        void onGameStarted();
    }

    public static final int WEAPON_KNIFE = -1, WEAPON_EAGLE = 0, WEAPON_MACHINE_GUN = 1, WEAPON_SHOTGUN = 2;
    public static final int WEAPON_ROCKET = 3, WEAPON_FLAMETHROWER = 4, WEAPON_SNIPER = 5, WEAPON_LASER = 6, WEAPON_ELECTRIC = 7, WEAPON_BOMB = 8;

    public static final int ENEMY_KNIFE_RUSHER = 0, ENEMY_EAGLE_SCOUT = 1, ENEMY_SHOTGUNNER = 2, ENEMY_MACHINE_GUNNER = 3, ENEMY_ROCKET_HEAVY = 4;
    public static final int ENEMY_PYRO = 5, ENEMY_SNIPER = 6, ENEMY_LASER = 7, ENEMY_ELECTRIC = 8, ENEMY_BOMBER = 9;

    private static final String[] ENEMY_DEATH_TITLES = {
            "Killed by Assassin", "Killed by Eagle Scout", "Killed by Shotgunner", "Killed by Heavy Gunner", "Killed by Rocket Heavy",
            "Killed by Pyro Trooper", "Killed by Sniper Elite", "Killed by Laser Trooper", "Killed by Tesla Trooper", "Killed by Bomber"
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scopePaint = new Paint(Paint.ANTI_ALIAS_FLAG), textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG), ambientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final RectF tempRect = new RectF();
    private final DashPathEffect scopeDash;

    private final int themeState;
    private final boolean isSunnyDay, isMoonlightMode, isSpaceMode;
    private int groundColor, gridColor, hudPillBgColor, hudBoxBgColor, hudIconColor, hudTitleColor, hudSubTextColor;

    private float screenW, screenH;
    private final float dp;
    private int safeInsetLeft = 0, safeInsetRight = 0;

    private float worldRadius = 1400f, camX = 0f, camY = 0f, camZoom = 1.0f, screenShake = 0f, starPhase = 0f, assassinCloseShieldRadius;
    private float playerX = 0f, playerY = 0f, playerVX = 0f, playerVY = 0f, playerAngle = 0f;
    private float playerRadius = 24f, playerHP = 100f, maxHP = 100f, playerShield = 100f, maxShield = 100f;
    private float playerSpeed = 4.7f, playerWalkPhase = 0f, playerRecoilOffset = 0f, playerSlashPhase = 0f;
    private long lastDamageTime = 0, lastShotTime = 0;
    private Enemy autoAimSniperTarget = null;

    private int currentWeapon = WEAPON_EAGLE, lastFirearmWeapon = WEAPON_EAGLE, lastFirearmAmmo = -1, currentAmmo = -1;
    private int killStreak = 0, killsThisGame = 0;
    private final int[] weaponShotsUsed = new int[9];
    private long lastKillTime = 0, streakBannerUntil = 0;
    private String streakBannerText = "", lastDeathCause = "Killed in Action";

    private final RectF rightWeaponCardRect = new RectF(), rightKnifeBtnRect = new RectF();
    private int movePointerId = -1, aimPointerId = -1;
    private float moveOriginX, moveOriginY, moveCurX, moveCurY, moveDX = 0f, moveDY = 0f, aimOriginX, aimOriginY, aimCurX, aimCurY;
    private boolean isShooting = false, isCapturingSnapshot = false;

    private boolean playing = false, paused = false, gameOver = false, vibrationEnabled = true, musicEnabled = true, enemySoundEnabled = false;
    private int currentDifficulty = 1, score = 0, waveCounter = 0;
    private long touchCooldown = 0, lastEnemySpawnTime = 0, lastCrateSpawnTime = 0;
    private Bitmap lastDeathSnapshot = null;

    private final List<Obstacle> obstacles = new ArrayList<>();
    private final List<MeadowGlade> meadowGlades = new ArrayList<>();
    private final List<GrassPatch> grassPatches = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<LaserBeam> laserBeams = new ArrayList<>();
    private final List<LightningBolt> lightningBolts = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<ShellCasing> shells = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<OutZoneDecoration> outZoneDecorations = new ArrayList<>();

    private ToneGenerator toneGenerator;
    private TextToSpeech announcerTts;
    private boolean ttsReady = false;
    private ExecutorService audioExecutor;
    private Vibrator vibrator;
    private GameListener listener;

    public Thrive2ndActivity(Context context, int themeState) {
        super(context);
        this.themeState = themeState;
        this.isSunnyDay = (themeState == 0); this.isMoonlightMode = (themeState == 1); this.isSpaceMode = (themeState == 2);
        this.dp = context.getResources().getDisplayMetrics().density;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.assassinCloseShieldRadius = 128f * dp;
        this.scopeDash = new DashPathEffect(new float[]{10f * dp, 7f * dp}, 0f);

        if (isSunnyDay) {
            groundColor = Color.parseColor("#88C478"); gridColor = Color.parseColor("#75B265");
            hudPillBgColor = Color.parseColor("#F2F2F7"); hudBoxBgColor = Color.parseColor("#FFFFFF");
            hudIconColor = Color.parseColor("#333333"); hudTitleColor = Color.parseColor("#333333"); hudSubTextColor = Color.parseColor("#666666");
        } else if (isMoonlightMode) {
            groundColor = Color.parseColor("#0B192C"); gridColor = Color.parseColor("#132845");
            hudPillBgColor = Color.parseColor("#2D313A"); hudBoxBgColor = Color.parseColor("#D8E2FF");
            hudIconColor = Color.parseColor("#001C3A"); hudTitleColor = Color.WHITE; hudSubTextColor = Color.parseColor("#A0A0A5");
        } else {
            groundColor = Color.parseColor("#000000"); gridColor = Color.parseColor("#0A1324");
            hudPillBgColor = Color.parseColor("#1B1F2B"); hudBoxBgColor = Color.parseColor("#38BDF8");
            hudIconColor = Color.parseColor("#001C3A"); hudTitleColor = Color.WHITE; hudSubTextColor = Color.parseColor("#A0A0A5");
        }

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND); strokePaint.setStrokeJoin(Paint.Join.ROUND);
        scopePaint.setStyle(Paint.Style.STROKE); scopePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); textPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); subTextPaint.setTextAlign(Paint.Align.LEFT);

        try { audioExecutor = Executors.newCachedThreadPool(); toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 55); }
        catch (Exception e) { toneGenerator = null; audioExecutor = null; }
        try {
            announcerTts = new TextToSpeech(context.getApplicationContext(), status -> {
                if (status == TextToSpeech.SUCCESS && announcerTts != null) {
                    announcerTts.setLanguage(Locale.US);
                    // Try to pick a low/male English voice if the device has multiple voices installed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        try {
                            for (Voice v : announcerTts.getVoices()) {
                                String vName = v.getName().toLowerCase(Locale.US);
                                if (v.getLocale().getLanguage().equals("en") && !v.isNetworkConnectionRequired()
                                        && (vName.contains("male") || vName.contains("iom") || vName.contains("tpd") || vName.contains("en-us-x-sfg"))) {
                                    announcerTts.setVoice(v);
                                    break;
                                }
                            }
                            announcerTts.setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build());
                        } catch (Exception ignored) {}
                    }
                    // 0.52f pitch drops the voice into a deep, resonant male arena announcer
                    announcerTts.setPitch(0.52f);
                    announcerTts.setSpeechRate(0.94f);
                    ttsReady = true;
                    // Pre-warm the audio hardware so the first "Double Kill" has zero startup delay
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        announcerTts.playSilentUtterance(10, TextToSpeech.QUEUE_ADD, "tts_warmup");
                    }
                }
            });
        } catch (Exception e) {
            announcerTts = null;
        }
    }

    public void setSafeInsets(int left, int right) { this.safeInsetLeft = left; this.safeInsetRight = right; invalidate(); }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(this);
        if (audioExecutor != null) { audioExecutor.shutdown(); audioExecutor = null; }
        if (toneGenerator != null) { toneGenerator.release(); toneGenerator = null; }
        if (announcerTts != null) {
            try { announcerTts.stop(); announcerTts.shutdown(); } catch (Exception ignored) {}
            announcerTts = null;
        }
    }
    private void speakKillStreakAnnouncement(final String phrase, final int streakLevel) {
        if (!musicEnabled) return;

        // 1. Fire deep male voice immediately with QUEUE_FLUSH so there is zero queueing delay
        if (ttsReady && announcerTts != null) {
            try {
                float dynamicPitch = Math.max(0.44f, 0.54f - (streakLevel * 0.015f));
                announcerTts.setPitch(dynamicPitch);
                announcerTts.setSpeechRate(0.95f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    announcerTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "streak_" + System.nanoTime());
                } else {
                    announcerTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null);
                }
            } catch (Exception ignored) {}
        }

        // 2. 0ms sub-bass vocal chest rumble on audioExecutor so the impact is felt instantaneously
        if (audioExecutor != null) {
            audioExecutor.execute(() -> {
                AudioTrack track = null;
                try {
                    final int sampleRate = 16000, durationMs = 260, numSamples = (sampleRate * durationMs) / 1000;
                    short[] pcm = new short[numSamples];
                    double ph1 = 0.0, ph2 = 0.0;
                    double baseFreq = (streakLevel >= 5) ? 68.0 : 78.0;
                    for (int i = 0; i < numSamples; i++) {
                        double p = (double) i / numSamples;
                        double env = Math.sin(p * Math.PI) * Math.pow(1.0 - p, 0.55);
                        ph1 += (2.0 * Math.PI * baseFreq) / sampleRate;
                        ph2 += (2.0 * Math.PI * (baseFreq * 1.5)) / sampleRate;
                        double wave = Math.tanh((Math.sin(ph1) * 0.85 + Math.sin(ph2) * 0.45) * 2.2) * env;
                        pcm[i] = (short) (wave * 22000);
                    }
                    int byteSize = numSamples * 2;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        track = new AudioTrack.Builder()
                                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(byteSize).setTransferMode(AudioTrack.MODE_STATIC).build();
                    } else {
                        track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, byteSize, AudioTrack.MODE_STATIC);
                    }
                    track.write(pcm, 0, numSamples);
                    track.play();
                    Thread.sleep(durationMs + 20L);
                } catch (Exception ignored) {
                } finally {
                    if (track != null) { try { track.stop(); } catch (Exception ignored) {} try { track.release(); } catch (Exception ignored) {} }
                }
            });
        }
    }

    public void setGameListener(GameListener listener) { this.listener = listener; }
    public void setVibrationEnabled(boolean enabled) { this.vibrationEnabled = enabled; }
    public void setMusicEnabled(boolean enabled) { this.musicEnabled = enabled; }
    public void setEnemySoundEnabled(boolean enabled) { this.enemySoundEnabled = enabled; }
    public boolean isPlaying() { return playing; }
    public boolean isGameOver() { return gameOver; }
    public void setDifficulty(int difficulty) { this.currentDifficulty = difficulty; for (Enemy e : enemies) e.speed = computeEnemySpeed(e.type); }

    private float getDifficultyEnemyHpMult() { return (currentDifficulty == 0) ? 0.58f : (currentDifficulty == 1 ? 1.0f : 1.38f); }
    private float getDifficultyDamageMult() { return (currentDifficulty == 0) ? 0.52f : (currentDifficulty == 1 ? 1.0f : 1.32f); }
    private float getDifficultyEnemyFireDelayMult() { return (currentDifficulty == 0) ? 1.55f : (currentDifficulty == 1 ? 1.0f : 0.72f); }
    private float getDifficultyBulletSpeedMult() { return (currentDifficulty == 0) ? 0.72f : (currentDifficulty == 1 ? 1.0f : 1.24f); }

    private float computeEnemySpeed(int enemyClass) {
        float waveScale = 0.92f + Math.min(0.48f, waveCounter * 0.012f);
        if (enemyClass == ENEMY_KNIFE_RUSHER) return 4.25f * dp * waveScale * ((currentDifficulty == 0) ? 0.58f : (currentDifficulty == 1 ? 0.96f : 1.28f));
        float gunMult = (currentDifficulty == 0) ? 0.65f : (currentDifficulty == 1 ? 1.0f : 1.25f);
        float[] baseSpds = { 4.25f, 2.70f, 2.40f, 2.25f, 1.85f, 2.50f, 2.00f, 2.35f, 2.45f, 2.15f };
        return baseSpds[Math.max(0, Math.min(9, enemyClass))] * dp * waveScale * gunMult;
    }

    private float getWeaponWeightSpeedMultiplier(int weaponType) {
        switch (weaponType) {
            case WEAPON_KNIFE: return 1.25f; case WEAPON_EAGLE: return 1.05f; case WEAPON_SHOTGUN: return 0.94f;
            case WEAPON_ELECTRIC: return 0.92f; case WEAPON_LASER: return 0.90f; case WEAPON_MACHINE_GUN: return 0.85f;
            case WEAPON_FLAMETHROWER: return 0.84f; case WEAPON_SNIPER: return 0.80f; case WEAPON_ROCKET: return 0.76f;
            default: return 1.00f;
        }
    }

    private float getWeaponScopeDistance(int weaponType) {
        switch (weaponType) {
            case WEAPON_KNIFE: return 85f * dp; case WEAPON_SHOTGUN: return 210f * dp; case WEAPON_FLAMETHROWER: return 240f * dp;
            case WEAPON_ELECTRIC: return 340f * dp; case WEAPON_EAGLE: return 380f * dp; case WEAPON_MACHINE_GUN: return 440f * dp;
            case WEAPON_ROCKET: return 510f * dp; case WEAPON_LASER: return 650f * dp; case WEAPON_SNIPER: return 880f * dp;
            default: return 380f * dp;
        }
    }

    private float getWeaponTargetCameraZoom(int weaponType) {
        switch (weaponType) {
            case WEAPON_KNIFE: case WEAPON_SHOTGUN: return 1.05f; case WEAPON_FLAMETHROWER: return 1.02f;
            case WEAPON_EAGLE: case WEAPON_ELECTRIC: return 1.00f; case WEAPON_MACHINE_GUN: return 0.95f;
            case WEAPON_ROCKET: return 0.92f; case WEAPON_LASER: return 0.86f; case WEAPON_SNIPER: return 0.76f;
            default: return 1.00f;
        }
    }

    private int getEnemyWeaponType(int enemyClass) {
        switch (enemyClass) {
            case ENEMY_KNIFE_RUSHER: return WEAPON_KNIFE; case ENEMY_EAGLE_SCOUT: return WEAPON_EAGLE;
            case ENEMY_SHOTGUNNER: return WEAPON_SHOTGUN; case ENEMY_MACHINE_GUNNER: return WEAPON_MACHINE_GUN;
            case ENEMY_ROCKET_HEAVY: return WEAPON_ROCKET; case ENEMY_PYRO: return WEAPON_FLAMETHROWER;
            case ENEMY_SNIPER: return WEAPON_SNIPER; case ENEMY_LASER: return WEAPON_LASER;
            case ENEMY_ELECTRIC: return WEAPON_ELECTRIC; case ENEMY_BOMBER: return WEAPON_BOMB;
            default: return WEAPON_EAGLE;
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w; screenH = h;
        if (oldw == 0 || oldh == 0) resetGame();
    }

    public void resetGame() {
        score = 0; waveCounter = 0; killStreak = 0; killsThisGame = 0; lastDeathCause = "Killed in Action";
        for (int i = 0; i < weaponShotsUsed.length; i++) weaponShotsUsed[i] = 0;

        playerX = 0f; playerY = 0f; playerVX = 0f; playerVY = 0f; playerAngle = 0f;
        playerHP = maxHP; playerShield = maxShield;
        playerRadius = 24f * dp; playerSpeed = 4.7f * dp; worldRadius = 1150f * dp;
        playerWalkPhase = 0f; playerRecoilOffset = 0f; playerSlashPhase = 0f;
        camZoom = 1.0f; autoAimSniperTarget = null;

        currentWeapon = WEAPON_EAGLE; lastFirearmWeapon = WEAPON_EAGLE; lastFirearmAmmo = -1; currentAmmo = -1; streakBannerText = "";
        movePointerId = -1; aimPointerId = -1; moveDX = 0f; moveDY = 0f; isShooting = false;

        bullets.clear(); laserBeams.clear(); lightningBolts.clear(); enemies.clear();
        pickups.clear(); particles.clear(); shells.clear(); floatingTexts.clear();
        shockwaves.clear(); obstacles.clear(); meadowGlades.clear(); grassPatches.clear(); stars.clear();
        outZoneDecorations.clear();

        int attempts = 0;
        while (obstacles.size() < 26 && attempts < 200) {
            attempts++;
            float angle = (float) (Math.random() * Math.PI * 2), dist = (175f * dp) + (float) Math.random() * (worldRadius - 265f * dp);
            float ox = (float) Math.cos(angle) * dist, oy = (float) Math.sin(angle) * dist, or = (40f + (float) Math.random() * 26f) * dp;
            boolean overlaps = false;
            for (Obstacle existing : obstacles) if (Math.hypot(ox - existing.x, oy - existing.y) < (or + existing.radius + 35f * dp)) { overlaps = true; break; }
            if (!overlaps) obstacles.add(new Obstacle(ox, oy, or));
        }

        if (isSunnyDay) {
            for (int i = 0; i < 42; i++) {
                float angle = (float) (Math.random() * Math.PI * 2), dist = (float) Math.sqrt(Math.random()) * (worldRadius - 120f * dp);
                boolean isStone = (i % 5 == 0);
                float gr = (isStone ? (12f + (float) Math.random() * 10f) : (55f + (float) Math.random() * 85f)) * dp;
                meadowGlades.add(new MeadowGlade((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist, gr, isStone, i % 2 == 0));
            }
        }

        if (!isSpaceMode) {
            int[] flowerPalette = { Color.parseColor("#FFFFFF"), Color.parseColor("#FEF08A"), Color.parseColor("#FBCFE8"), Color.parseColor("#BAE6FD") };
            for (int i = 0; i < 200; i++) {
                float angle = (float) (Math.random() * Math.PI * 2), dist = (float) Math.sqrt(Math.random()) * (worldRadius - 40f * dp);
                grassPatches.add(new GrassPatch((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist,
                        (9f + (float) Math.random() * 8f) * dp, isSunnyDay && (Math.random() < 0.34), flowerPalette[i % flowerPalette.length]));
            }
        }

        for (int kind = 2; kind <= 8; kind++) {
            float angle = (float) ((kind - 2) * (Math.PI * 2 / 7.0)), dist = (210f + (float) Math.random() * 240f) * dp;
            float[] safePos = pushOutOfObstacles((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist, 26f * dp);
            pickups.add(new Pickup(safePos[0], safePos[1], kind));
        }

        if (isSpaceMode) {
            int[] starColors = { Color.WHITE, Color.parseColor("#93C5FD"), Color.parseColor("#FDE047"), Color.parseColor("#C4B5FD"), Color.parseColor("#67E8F9") };
            for (int i = 0; i < 220; i++) {
                stars.add(new Star((float) ((Math.random() - 0.5) * worldRadius * 2.4f), (float) ((Math.random() - 0.5) * worldRadius * 2.4f),
                        (1.0f + (float) Math.random() * 2.2f) * dp, starColors[i % starColors.length], (float) (Math.random() * Math.PI * 2)));
            }
        } else if (isMoonlightMode) {
            int[] fireflyColors = { Color.parseColor("#BAE6FD"), Color.parseColor("#FEF08A"), Color.parseColor("#7DD3FC"), Color.parseColor("#A7F3D0") };
            for (int i = 0; i < 95; i++) {
                stars.add(new Star((float) ((Math.random() - 0.5) * worldRadius * 2.0f), (float) ((Math.random() - 0.5) * worldRadius * 2.0f),
                        (2.0f + (float) Math.random() * 2.5f) * dp, fireflyColors[i % fireflyColors.length], (float) (Math.random() * Math.PI * 2)));
            }
        }

        for (int i = 0; i < 60; i++) {
            float angle = (float) (Math.random() * Math.PI * 2);
            float dist = worldRadius + (40f * dp) + (float) Math.random() * (700f * dp);
            float ox = (float) Math.cos(angle) * dist;
            float oy = (float) Math.sin(angle) * dist;
            float rot = (float) (Math.random() * 360f);
            int type = isSpaceMode ? (Math.random() < 0.5 ? 2 : 3) : (Math.random() < 0.5 ? 0 : 1);
            outZoneDecorations.add(new OutZoneDecoration(ox, oy, rot, type));
        }

        playing = false; paused = false; gameOver = false;
        touchCooldown = System.currentTimeMillis() + 400; lastCrateSpawnTime = System.currentTimeMillis();
        Choreographer.getInstance().removeFrameCallback(this); Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    private float[] pushOutOfObstacles(float x, float y, float entityRadius) {
        for (Obstacle ob : obstacles) {
            float solidR = ob.radius * 0.85f, dx = x - ob.x, dy = y - ob.y, dist = (float) Math.hypot(dx, dy), minDist = entityRadius + solidR;
            if (dist < minDist) {
                if (dist < 0.001f) { dx = 1f; dy = 0f; dist = 1f; }
                float push = minDist - dist + 2f * dp;
                x += (dx / dist) * push; y += (dy / dist) * push;
            }
        }
        return new float[]{x, y};
    }

    public void pauseGame() { paused = true; movePointerId = -1; aimPointerId = -1; moveDX = 0f; moveDY = 0f; isShooting = false; }
    public void resumeGame() { paused = false; touchCooldown = System.currentTimeMillis() + 300; Choreographer.getInstance().removeFrameCallback(this); Choreographer.getInstance().postFrameCallback(this); }

    private void playBeep(int toneType, int durationMs) {
        if (musicEnabled && toneGenerator != null && audioExecutor != null) {
            audioExecutor.execute(() -> { try { toneGenerator.startTone(toneType, durationMs); } catch (Exception ignored) {} });
        }
    }

    private void playSynthesizedElectricZap(final int chainHops) {
        if (!musicEnabled || audioExecutor == null) return;
        audioExecutor.execute(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 16000, durationMs = 155 + Math.max(1, chainHops) * 28, numSamples = (sampleRate * durationMs) / 1000;
                short[] pcm = new short[numSamples];
                double phase1 = 0.0, phase2 = 0.0;
                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate, progress = (double) i / numSamples;
                    double hopPulse = 0.65 + 0.35 * Math.sin(progress * Math.PI * 2.0 * Math.max(2, chainHops + 1));
                    double decay = (1.0 - progress * 0.75) * hopPulse;
                    double freq1 = 240.0 + 680.0 * Math.abs(Math.sin(t * 95.0)), freq2 = 1350.0 + 950.0 * Math.cos(t * 140.0);
                    phase1 += (2.0 * Math.PI * freq1) / sampleRate; phase2 += (2.0 * Math.PI * freq2) / sampleRate;

                    double saw = (phase1 % (2.0 * Math.PI)) / Math.PI - 1.0, ringMod = Math.sin(phase1) * Math.signum(Math.sin(phase2));
                    double crackle = (Math.random() > 0.72) ? ((Math.random() * 2.0 - 1.0) * 0.95) : 0.0;
                    double sample = Math.max(-1.0, Math.min(1.0, (saw * 0.38 + ringMod * 0.34 + crackle * 0.45) * decay));
                    pcm[i] = (short) (sample * 24000);
                }
                int byteSize = numSamples * 2;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(byteSize).setTransferMode(AudioTrack.MODE_STATIC).build();
                } else {
                    track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, byteSize, AudioTrack.MODE_STATIC);
                }
                track.write(pcm, 0, numSamples); track.play(); Thread.sleep(durationMs + 25L);
            } catch (Exception ignored) {
            } finally {
                if (track != null) { try { track.stop(); } catch (Exception ignored) {} try { track.release(); } catch (Exception ignored) {} }
            }
        });
    }

    private long[] lastSoundTimeMs = new long[100];

    private void playSynthesizedWeaponSound(final int soundType, final int extraParam) {
        if (!musicEnabled || audioExecutor == null) return;

        long now = System.currentTimeMillis();
        int throttleMs = 0;
        switch (soundType) {
            case WEAPON_FLAMETHROWER: throttleMs = 60; break;
            case WEAPON_MACHINE_GUN: throttleMs = 50; break;
            case WEAPON_LASER: throttleMs = 120; break;
            case 99: throttleMs = 60; break;
        }
        int index = (soundType >= 0 && soundType < 100) ? soundType : 0;
        if (throttleMs > 0 && now - lastSoundTimeMs[index] < throttleMs) {
            return;
        }
        lastSoundTimeMs[index] = now;

        audioExecutor.execute(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 16000;
                int durationMs;
                switch (soundType) {
                    case WEAPON_KNIFE: durationMs = 85; break;
                    case WEAPON_EAGLE: durationMs = 115; break;
                    case WEAPON_MACHINE_GUN: durationMs = 68; break;
                    case WEAPON_SHOTGUN: durationMs = 195; break;
                    case WEAPON_FLAMETHROWER: durationMs = 70; break;
                    case WEAPON_LASER: durationMs = 155; break;
                    case WEAPON_ELECTRIC: durationMs = 150 + Math.max(1, extraParam) * 26; break;
                    case WEAPON_SNIPER: durationMs = 340; break;
                    case WEAPON_ROCKET: durationMs = 280; break;
                    default: durationMs = 390; break;
                }
                final int numSamples = (sampleRate * durationMs) / 1000;
                short[] pcm = new short[numSamples];
                double ph1 = 0.0, ph2 = 0.0, noiseFilter = 0.0;

                for (int i = 0; i < numSamples; i++) {
                    double t = (double) i / sampleRate, p = (double) i / numSamples;
                    double white = Math.random() * 2.0 - 1.0, s = 0.0;

                    if (soundType == WEAPON_SNIPER) {
                        double crack = (p < 0.14) ? (white * 1.35 * (1.0 - p / 0.14)) : 0.0;
                        double fSub = 145.0 * Math.exp(-p * 5.2) + 32.0;
                        ph1 += (2.0 * Math.PI * fSub) / sampleRate;
                        ph2 += (2.0 * Math.PI * 520.0 * Math.exp(-p * 3.5)) / sampleRate;
                        noiseFilter = noiseFilter * 0.86 + white * 0.14;
                        double subWave = Math.sin(ph1) * 0.85 + Math.tanh(Math.sin(ph1) * 2.6) * 0.45;
                        double tail = (subWave + noiseFilter * 0.55 + Math.sin(ph2) * 0.20) * Math.pow(1.0 - p, 1.35);
                        s = crack + tail;
                    } else if (soundType == WEAPON_ROCKET || soundType == 99) {
                        double fBass = (soundType == 99 ? 95.0 : 125.0) * Math.exp(-p * 3.8) + 26.0;
                        ph1 += (2.0 * Math.PI * fBass) / sampleRate;
                        ph2 += (2.0 * Math.PI * (fBass * 0.52)) / sampleRate;
                        noiseFilter = noiseFilter * 0.90 + white * 0.10;
                        double subGrowl = Math.sin(ph1) * 0.75 + Math.sin(ph2) * 0.55;
                        double roar = Math.tanh((subGrowl + noiseFilter * 0.95) * 2.4);
                        s = roar * Math.pow(1.0 - p, soundType == 99 ? 0.95 : 1.25);
                    } else if (soundType == WEAPON_ELECTRIC) {
                        double hopPulse = 0.65 + 0.35 * Math.sin(p * Math.PI * 2.0 * Math.max(2, extraParam + 1));
                        double decay = (1.0 - p * 0.75) * hopPulse;
                        double f1 = 240.0 + 680.0 * Math.abs(Math.sin(t * 95.0)), f2 = 1350.0 + 950.0 * Math.cos(t * 140.0);
                        ph1 += (2.0 * Math.PI * f1) / sampleRate; ph2 += (2.0 * Math.PI * f2) / sampleRate;
                        double saw = (ph1 % (2.0 * Math.PI)) / Math.PI - 1.0, ringMod = Math.sin(ph1) * Math.signum(Math.sin(ph2));
                        double crackle = (Math.random() > 0.72) ? (white * 0.95) : 0.0;
                        s = (saw * 0.38 + ringMod * 0.34 + crackle * 0.45) * decay;
                    } else if (soundType == WEAPON_LASER) {
                        double fLaser = 1750.0 * Math.exp(-p * 4.2) + 210.0;
                        ph1 += (2.0 * Math.PI * fLaser) / sampleRate;
                        ph2 += (2.0 * Math.PI * (fLaser * 1.5)) / sampleRate;
                        s = (Math.sin(ph1) * 0.70 + Math.sin(ph2) * 0.30) * Math.pow(1.0 - p, 0.85);
                    } else if (soundType == WEAPON_FLAMETHROWER) {
                        noiseFilter = noiseFilter * 0.82 + white * 0.18;
                        ph1 += (2.0 * Math.PI * 75.0) / sampleRate;
                        s = (noiseFilter * 0.85 + Math.sin(ph1) * 0.25) * Math.sin(p * Math.PI);
                    } else if (soundType == WEAPON_SHOTGUN) {
                        double fSg = 165.0 * Math.exp(-p * 6.5) + 42.0;
                        ph1 += (2.0 * Math.PI * fSg) / sampleRate;
                        noiseFilter = noiseFilter * 0.78 + white * 0.22;
                        s = Math.tanh((Math.sin(ph1) * 0.80 + noiseFilter * 0.75) * 2.0) * Math.pow(1.0 - p, 1.4);
                    } else if (soundType == WEAPON_MACHINE_GUN) {
                        double fMg = 240.0 * Math.exp(-p * 9.0) + 65.0;
                        ph1 += (2.0 * Math.PI * fMg) / sampleRate;
                        s = (Math.sin(ph1) * 0.75 + white * 0.45) * Math.pow(1.0 - p, 1.8);
                    } else if (soundType == WEAPON_EAGLE) {
                        double fEg = 340.0 * Math.exp(-p * 7.5) + 80.0;
                        ph1 += (2.0 * Math.PI * fEg) / sampleRate;
                        s = (Math.sin(ph1) * 0.70 + white * 0.50) * Math.pow(1.0 - p, 1.6);
                    } else {
                        noiseFilter = noiseFilter * 0.70 + white * 0.30;
                        ph1 += (2.0 * Math.PI * (580.0 - 380.0 * p)) / sampleRate;
                        s = (noiseFilter * 0.65 + Math.sin(ph1) * 0.35) * Math.sin(p * Math.PI);
                    }
                    pcm[i] = (short) (Math.max(-1.0, Math.min(1.0, s)) * 24500);
                }

                int byteSize = numSamples * 2;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setBufferSizeInBytes(byteSize).setTransferMode(AudioTrack.MODE_STATIC).build();
                } else {
                    track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, byteSize, AudioTrack.MODE_STATIC);
                }
                track.write(pcm, 0, numSamples); track.play(); Thread.sleep(durationMs + 20L);
            } catch (Exception ignored) {
            } finally {
                if (track != null) { try { track.stop(); } catch (Exception ignored) {} try { track.release(); } catch (Exception ignored) {} }
            }
        });
    }

    private void vibrateForWeapon(int weaponType) {
        if (!vibrationEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                switch (weaponType) {
                    case WEAPON_KNIFE: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 16, 12, 26}, new int[]{0, 140, 0, 230}, -1)); break;
                    case WEAPON_EAGLE: vibrator.vibrate(VibrationEffect.createOneShot(22, 175)); break;
                    case WEAPON_MACHINE_GUN: vibrator.vibrate(VibrationEffect.createOneShot(12, 115)); break;
                    case WEAPON_SHOTGUN: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 34, 22, 46}, new int[]{0, 255, 0, 185}, -1)); break;
                    case WEAPON_ROCKET: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 45, 18, 85}, new int[]{0, 210, 80, 255}, -1)); break;
                    case WEAPON_FLAMETHROWER: vibrator.vibrate(VibrationEffect.createOneShot(18, 90)); break;
                    case WEAPON_SNIPER: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 55, 30, 65}, new int[]{0, 255, 0, 220}, -1)); break;
                    case WEAPON_LASER: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 20, 10, 35}, new int[]{0, 190, 90, 255}, -1)); break;
                    case WEAPON_ELECTRIC: vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 14, 10, 14, 10, 24}, new int[]{0, 220, 0, 180, 0, 255}, -1)); break;
                }
            } else vibrator.vibrate(weaponType == WEAPON_SNIPER || weaponType == WEAPON_ROCKET ? 80 : 25);
        } catch (Exception ignored) {}
    }

    private void triggerVibration(int duration) {
        if (vibrationEnabled && vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(duration);
            } catch (Exception ignored) {}
        }
    }

    private void equipWeapon(int weaponType) {
        currentWeapon = weaponType;
        switch (weaponType) {
            case WEAPON_KNIFE: currentAmmo = -1; break;
            case WEAPON_EAGLE: currentAmmo = -1; lastFirearmWeapon = WEAPON_EAGLE; lastFirearmAmmo = -1; break;
            case WEAPON_MACHINE_GUN: currentAmmo = 80; lastFirearmWeapon = WEAPON_MACHINE_GUN; lastFirearmAmmo = 80; break;
            case WEAPON_SHOTGUN: currentAmmo = 26; lastFirearmWeapon = WEAPON_SHOTGUN; lastFirearmAmmo = 26; break;
            case WEAPON_ROCKET: currentAmmo = 12; lastFirearmWeapon = WEAPON_ROCKET; lastFirearmAmmo = 12; break;
            case WEAPON_FLAMETHROWER: currentAmmo = 120; lastFirearmWeapon = WEAPON_FLAMETHROWER; lastFirearmAmmo = 120; break;
            case WEAPON_SNIPER: currentAmmo = 3; lastFirearmWeapon = WEAPON_SNIPER; lastFirearmAmmo = 3; break;
            case WEAPON_LASER: currentAmmo = 45; lastFirearmWeapon = WEAPON_LASER; lastFirearmAmmo = 45; break;
            case WEAPON_ELECTRIC: currentAmmo = 35; lastFirearmWeapon = WEAPON_ELECTRIC; lastFirearmAmmo = 35; break;
        }
        triggerVibration(30); playBeep(ToneGenerator.TONE_PROP_ACK, 45);
    }

    private void toggleKnifeQuickSwap() {
        if (currentWeapon == WEAPON_KNIFE) { currentWeapon = lastFirearmWeapon; currentAmmo = lastFirearmAmmo; }
        else { lastFirearmWeapon = currentWeapon; lastFirearmAmmo = currentAmmo; currentWeapon = WEAPON_KNIFE; currentAmmo = -1; }
        triggerVibration(25); playBeep(ToneGenerator.TONE_PROP_ACK, 35);
    }

    private long getCurrentWeaponCooldown() {
        switch (currentWeapon) {
            case WEAPON_FLAMETHROWER: return 48L; case WEAPON_MACHINE_GUN: return 85L; case WEAPON_EAGLE: return 225L;
            case WEAPON_KNIFE: return 235L; case WEAPON_LASER: return 310L; case WEAPON_ELECTRIC: return 380L;
            case WEAPON_SHOTGUN: return 520L; case WEAPON_ROCKET: return 840L; case WEAPON_SNIPER: return 950L;
            default: return 225L;
        }
    }

    private String getWeaponShortName(int weaponType) {
        switch (weaponType) {
            case WEAPON_KNIFE: return "Bowie Knife"; case WEAPON_MACHINE_GUN: return "Machine Gun"; case WEAPON_SHOTGUN: return "Shotgun";
            case WEAPON_ROCKET: return "Rocket RPG"; case WEAPON_FLAMETHROWER: return "Flamethrower"; case WEAPON_SNIPER: return "Sniper Gun";
            case WEAPON_LASER: return "Laser Gun"; case WEAPON_ELECTRIC: return "Electric Gun"; default: return "Eagle Gun";
        }
    }

    private String getWeaponPowerDetails(int weaponType) {
        switch (weaponType) {
            case WEAPON_KNIFE: return "DMG: 65 • Short Scope"; case WEAPON_MACHINE_GUN: return "DMG: 19 • Rapid Auto";
            case WEAPON_SHOTGUN: return "DMG: 22x6 • Short Scope"; case WEAPON_ROCKET: return "DMG: 130 • AoE Blast";
            case WEAPON_FLAMETHROWER: return "DMG: 14x • Fire Stream"; case WEAPON_SNIPER: return "DMG: 220 • Auto-Aim Scope";
            case WEAPON_LASER: return "DMG: 75 • Piercing Beam"; case WEAPON_ELECTRIC: return "DMG: 55 • Chain Lightning";
            default: return "DMG: 32 • Med Scope";
        }
    }

    private Enemy findAutoAimSniperTarget() {
        Enemy best = null; float bestDist = getWeaponScopeDistance(WEAPON_SNIPER) * 1.15f;
        for (Enemy e : enemies) {
            float d = (float) Math.hypot(e.x - playerX, e.y - playerY);
            if (e.type == ENEMY_KNIFE_RUSHER && d <= assassinCloseShieldRadius) continue;
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best == null && !enemies.isEmpty()) {
            for (Enemy e : enemies) { float d = (float) Math.hypot(e.x - playerX, e.y - playerY); if (d < bestDist) { bestDist = d; best = e; } }
        }
        return best;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (System.currentTimeMillis() < touchCooldown || gameOver || paused) return true;
        int action = event.getActionMasked(), index = event.getActionIndex(), pointerId = event.getPointerId(index);
        float tx = event.getX(index), ty = event.getY(index), maxJoyRadius = 56f * dp;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (rightKnifeBtnRect.contains(tx, ty)) { toggleKnifeQuickSwap(); return true; }
                if (rightWeaponCardRect.contains(tx, ty)) {
                    equipWeapon((currentWeapon == WEAPON_ELECTRIC) ? WEAPON_KNIFE : (currentWeapon + 1));
                    return true;
                }
                if (!playing) {
                    playing = true; lastEnemySpawnTime = System.currentTimeMillis(); lastCrateSpawnTime = System.currentTimeMillis();
                    if (listener != null) listener.onGameStarted();
                }
                if (tx < screenW * 0.5f && movePointerId == -1) {
                    movePointerId = pointerId; moveOriginX = tx; moveOriginY = ty; moveCurX = tx; moveCurY = ty; moveDX = 0f; moveDY = 0f;
                } else if (tx >= screenW * 0.5f && aimPointerId == -1) {
                    aimPointerId = pointerId; aimOriginX = tx; aimOriginY = ty; aimCurX = tx; aimCurY = ty; isShooting = true;
                    if (currentWeapon == WEAPON_SNIPER) {
                        Enemy target = findAutoAimSniperTarget();
                        if (target != null) playerAngle = (float) Math.atan2(target.y - playerY, target.x - playerX);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i); float px = event.getX(i), py = event.getY(i);
                    if (pid == movePointerId) {
                        float dx = px - moveOriginX, dy = py - moveOriginY, dist = (float) Math.hypot(dx, dy);
                        if (dist > maxJoyRadius) { dx = (dx / dist) * maxJoyRadius; dy = (dy / dist) * maxJoyRadius; }
                        moveCurX = moveOriginX + dx; moveCurY = moveOriginY + dy; moveDX = dx / maxJoyRadius; moveDY = dy / maxJoyRadius;
                        if (aimPointerId == -1 && dist > 8f * dp && currentWeapon != WEAPON_SNIPER) playerAngle = (float) Math.atan2(dy, dx);
                    } else if (pid == aimPointerId) {
                        float dx = px - aimOriginX, dy = py - aimOriginY, dist = (float) Math.hypot(dx, dy);
                        if (dist > maxJoyRadius) { dx = (dx / dist) * maxJoyRadius; dy = (dy / dist) * maxJoyRadius; }
                        aimCurX = aimOriginX + dx; aimCurY = aimOriginY + dy;
                        if (currentWeapon == WEAPON_SNIPER) {
                            Enemy target = findAutoAimSniperTarget();
                            if (target != null) playerAngle = (float) Math.atan2(target.y - playerY, target.x - playerX);
                            else if (dist > 6f * dp) playerAngle = (float) Math.atan2(dy, dx);
                        } else if (dist > 6f * dp) playerAngle = (float) Math.atan2(dy, dx);
                        isShooting = true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (pointerId == movePointerId || action == MotionEvent.ACTION_CANCEL) { movePointerId = -1; moveDX = 0f; moveDY = 0f; }
                if (pointerId == aimPointerId || action == MotionEvent.ACTION_CANCEL) { aimPointerId = -1; isShooting = false; }
                break;
        }
        return true;
    }

    private void spawnEnemy() {
        float spawnAngle = (float) (Math.random() * Math.PI * 2), spawnDist = Math.max(screenW, screenH) * 0.72f;
        float ex = playerX + (float) Math.cos(spawnAngle) * spawnDist, ey = playerY + (float) Math.sin(spawnAngle) * spawnDist;
        float distFromCenter = (float) Math.hypot(ex, ey);
        if (distFromCenter > worldRadius - 45f * dp) {
            ex = (ex / distFromCenter) * (worldRadius - 55f * dp); ey = (ey / distFromCenter) * (worldRadius - 55f * dp);
        }
        float hpMult = getDifficultyEnemyHpMult();
        double roll = Math.random(), assassinChance = (currentDifficulty == 0) ? 0.18 : (currentDifficulty == 1 ? 0.24 : 0.28);
        int enemyClass;
        if (roll < assassinChance) enemyClass = ENEMY_KNIFE_RUSHER;
        else {
            int pool = (waveCounter < 3) ? 5 : 9;
            enemyClass = 1 + (int) (Math.random() * pool);
        }
        float[] radii = { 21f, 22.5f, 24f, 24.5f, 28f, 25f, 23f, 23.5f, 24f, 26.5f };
        float[] baseHps = { 45f, 55f, 78f, 88f, 135f, 95f, 65f, 75f, 82f, 115f };
        float radius = radii[enemyClass] * dp, hp = baseHps[enemyClass] * hpMult;
        float[] safeSpawn = pushOutOfObstacles(ex, ey, radius);
        enemies.add(new Enemy(safeSpawn[0], safeSpawn[1], radius, hp, computeEnemySpeed(enemyClass), enemyClass));
        waveCounter++;
    }

    private void ejectBrassShell(float x, float y, float angle, int color) {
        float ejectAngle = angle + (float) (Math.PI * 0.55f + (Math.random() - 0.5f) * 0.4f), spd = (2.0f + (float) Math.random() * 2.2f) * dp;
        shells.add(new ShellCasing(x, y, (float) Math.cos(ejectAngle) * spd, (float) Math.sin(ejectAngle) * spd, color));
    }

    private void performPlayerKnifeSlash() {
        weaponShotsUsed[0]++; playerSlashPhase = 1.0f; vibrateForWeapon(WEAPON_KNIFE);
        playSynthesizedWeaponSound(WEAPON_KNIFE, 0);
        float cos = (float) Math.cos(playerAngle), sin = (float) Math.sin(playerAngle);
        playerX += cos * 4.5f * dp; playerY += sin * 4.5f * dp;
        if (isSpaceMode) { playerVX += cos * 2.2f * dp; playerVY += sin * 2.2f * dp; }
        resolveEntityObstacleCollisions(true, null);

        float slashReach = getWeaponScopeDistance(WEAPON_KNIFE);
        for (int i = -4; i <= 4; i++) {
            float a = playerAngle + i * 0.16f;
            particles.add(new Particle(playerX + (float) Math.cos(a) * (playerRadius * 1.6f), playerY + (float) Math.sin(a) * (playerRadius * 1.6f),
                    (float) Math.cos(a) * 3.8f * dp, (float) Math.sin(a) * 3.8f * dp, 4.0f * dp, Color.parseColor("#38BDF8"), 10));
        }
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            float dx = e.x - playerX, dy = e.y - playerY, dist = (float) Math.hypot(dx, dy);
            if (dist <= slashReach + e.radius) {
                float angleToEnemy = (float) Math.atan2(dy, dx);
                float angleDiff = (float) Math.atan2(Math.sin(angleToEnemy - playerAngle), Math.cos(angleToEnemy - playerAngle));
                if (Math.abs(angleDiff) <= 1.25f || dist <= playerRadius + e.radius + 18f * dp) {
                    float dmg = (e.type == ENEMY_KNIFE_RUSHER) ? 65f : 55f;
                    e.hp -= dmg; createExplosion(e.x, e.y, Color.parseColor("#38BDF8"), 9);
                    floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "SLASH -" + (int) dmg, Color.parseColor("#38BDF8")));
                    if (dist > 1f) { e.x += (dx / dist) * 14f * dp; e.y += (dy / dist) * 14f * dp; resolveEntityObstacleCollisions(false, e); }
                    if (e.hp <= 0) handleEnemyDefeated(i, e);
                }
            }
        }
    }

    private void fireLaserBeam(float startX, float startY, float angle) {
        float maxDist = getWeaponScopeDistance(WEAPON_LASER), cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), actualDist = maxDist;
        for (Obstacle ob : obstacles) {
            float ox = ob.x - startX, oy = ob.y - startY, proj = ox * cos + oy * sin;
            if (proj > 0 && proj < actualDist && Math.abs(-ox * sin + oy * cos) < ob.radius * 0.80f) actualDist = proj;
        }
        float endX = startX + cos * actualDist, endY = startY + sin * actualDist;
        laserBeams.add(new LaserBeam(startX, startY, endX, endY, Color.parseColor("#00F5D4")));
        createExplosion(endX, endY, Color.parseColor("#00F5D4"), 8);

        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            float ex = e.x - startX, ey = e.y - startY, proj = ex * cos + ey * sin;
            if (proj > 0 && proj <= actualDist + e.radius && Math.abs(-ex * sin + ey * cos) <= e.radius + 8f * dp) {
                if (e.type == ENEMY_KNIFE_RUSHER && Math.hypot(e.x - playerX, e.y - playerY) <= assassinCloseShieldRadius) {
                    e.slashPhase = 1.0f; floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "USE KNIFE!", Color.parseColor("#FACC15"))); continue;
                }
                e.hp -= 75f; createExplosion(e.x, e.y, Color.parseColor("#00F5D4"), 7);
                floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "-75 LASER", Color.parseColor("#00F5D4")));
                if (e.hp <= 0) handleEnemyDefeated(i, e);
            }
        }
    }

    private void fireElectricChain(float startX, float startY, float angle) {
        float scopeDist = getWeaponScopeDistance(WEAPON_ELECTRIC), curX = startX, curY = startY;
        List<Enemy> hitChain = new ArrayList<>();
        for (int hop = 0; hop < 4; hop++) {
            Enemy nearest = null; float minDist = (hop == 0) ? scopeDist : (220f * dp);
            for (Enemy e : enemies) {
                if (hitChain.contains(e)) continue;
                float d = (float) Math.hypot(e.x - curX, e.y - curY);
                if (d <= minDist) { minDist = d; nearest = e; }
            }
            if (nearest == null) break;
            lightningBolts.add(new LightningBolt(curX, curY, nearest.x, nearest.y, dp));
            hitChain.add(nearest); curX = nearest.x; curY = nearest.y;
        }
        playSynthesizedElectricZap(Math.max(1, hitChain.size()));
        if (hitChain.isEmpty()) {
            lightningBolts.add(new LightningBolt(startX, startY, startX + (float) Math.cos(angle) * 180f * dp, startY + (float) Math.sin(angle) * 180f * dp, dp));
            return;
        }
        for (int c = 0; c < hitChain.size(); c++) {
            Enemy target = hitChain.get(c); int idx = enemies.indexOf(target);
            if (idx == -1) continue;
            if (target.type == ENEMY_KNIFE_RUSHER && Math.hypot(target.x - playerX, target.y - playerY) <= assassinCloseShieldRadius) {
                target.slashPhase = 1.0f; floatingTexts.add(new FloatingText(target.x, target.y - target.radius, "USE KNIFE!", Color.parseColor("#FACC15"))); continue;
            }
            float dmg = Math.round(58f * Math.pow(0.82, c));
            target.hp -= dmg; createExplosion(target.x, target.y, Color.parseColor("#38BDF8"), 8);
            floatingTexts.add(new FloatingText(target.x, target.y - target.radius, "ZAP -" + (int) dmg, Color.parseColor("#7DD3FC")));
            if (target.hp <= 0) handleEnemyDefeated(idx, target);
        }
    }

    private void firePlayerWeapon() {
        if (currentWeapon == WEAPON_KNIFE) { performPlayerKnifeSlash(); return; }
        if (currentWeapon >= 0 && currentWeapon <= 7) weaponShotsUsed[currentWeapon + 1] += (currentWeapon == WEAPON_SHOTGUN) ? 6 : 1;

        if (currentWeapon == WEAPON_SNIPER) {
            autoAimSniperTarget = findAutoAimSniperTarget();
            if (autoAimSniperTarget != null) playerAngle = (float) Math.atan2(autoAimSniperTarget.y - playerY, autoAimSniperTarget.x - playerX);
        }

        float gunLength = playerRadius * 2.05f, gunOffsetRight = playerRadius * 0.36f;
        float cos = (float) Math.cos(playerAngle), sin = (float) Math.sin(playerAngle);
        float bx = playerX + cos * gunLength - sin * gunOffsetRight, by = playerY + sin * gunLength + cos * gunOffsetRight;
        float breechX = playerX + cos * (playerRadius * 0.8f) - sin * gunOffsetRight, breechY = playerY + sin * (playerRadius * 0.8f) + cos * gunOffsetRight;

        vibrateForWeapon(currentWeapon);

        if (currentWeapon == WEAPON_EAGLE) {
            float bSpeed = 16.5f * dp;
            bullets.add(new Bullet(bx, by, cos * bSpeed, sin * bSpeed, 5f * dp, 32f, true, WEAPON_EAGLE, 75, ""));
            spawnMuzzleFlash(bx, by, playerAngle, Color.parseColor("#FDE047"), 5);
            ejectBrassShell(breechX, breechY, playerAngle, Color.parseColor("#FACC15"));
            playerRecoilOffset = 4.5f * dp;
            if (isSpaceMode) { playerVX -= cos * 0.6f * dp; playerVY -= sin * 0.6f * dp; }
            screenShake = Math.max(screenShake, 2.2f * dp);
            playSynthesizedWeaponSound(WEAPON_EAGLE, 0);
        } else if (currentWeapon == WEAPON_MACHINE_GUN) {
            float spreadAngle = playerAngle + (float) ((Math.random() - 0.5) * 0.14f), bSpeed = 18.5f * dp;
            bullets.add(new Bullet(bx, by, (float) Math.cos(spreadAngle) * bSpeed, (float) Math.sin(spreadAngle) * bSpeed, 4.2f * dp, 19f, true, WEAPON_MACHINE_GUN, 65, ""));
            spawnMuzzleFlash(bx, by, spreadAngle, Color.parseColor("#38BDF8"), 4);
            ejectBrassShell(breechX, breechY, playerAngle, Color.parseColor("#FBBF24"));
            playerRecoilOffset = 3.2f * dp;
            if (isSpaceMode) { playerVX -= cos * 0.45f * dp; playerVY -= sin * 0.45f * dp; }
            screenShake = Math.max(screenShake, 2.8f * dp);
            playSynthesizedWeaponSound(WEAPON_MACHINE_GUN, 0);
        } else if (currentWeapon == WEAPON_SHOTGUN) {
            for (int i = 0; i < 6; i++) {
                float pAngle = playerAngle - 0.26f + (0.52f * i / 5f) + (float) ((Math.random() - 0.5) * 0.05f), bSpeed = (14.0f + (float) Math.random() * 2.5f) * dp;
                bullets.add(new Bullet(bx, by, (float) Math.cos(pAngle) * bSpeed, (float) Math.sin(pAngle) * bSpeed, 4.5f * dp, 22f, true, WEAPON_SHOTGUN, 22, ""));
            }
            spawnMuzzleFlash(bx, by, playerAngle, Color.parseColor("#FB923C"), 11);
            ejectBrassShell(breechX, breechY, playerAngle, Color.parseColor("#EF4444"));
            playerX -= cos * 4.5f * dp; playerY -= sin * 4.5f * dp;
            if (isSpaceMode) { playerVX -= cos * 2.4f * dp; playerVY -= sin * 2.4f * dp; }
            resolveEntityObstacleCollisions(true, null);
            playerRecoilOffset = 7.5f * dp; screenShake = Math.max(screenShake, 6.8f * dp);
            playSynthesizedWeaponSound(WEAPON_SHOTGUN, 0);
        } else if (currentWeapon == WEAPON_ROCKET) {
            float bSpeed = 11.0f * dp;
            bullets.add(new Bullet(bx, by, cos * bSpeed, sin * bSpeed, 8.5f * dp, 125f, true, WEAPON_ROCKET, 95, ""));
            spawnMuzzleFlash(bx, by, playerAngle, Color.parseColor("#EF4444"), 13);
            spawnMuzzleFlash(playerX - cos * playerRadius * 1.4f - sin * gunOffsetRight,
                    playerY - sin * playerRadius * 1.4f + cos * gunOffsetRight, playerAngle + (float) Math.PI, Color.parseColor("#CBD5E1"), 8);
            if (isSpaceMode) { playerVX -= cos * 3.0f * dp; playerVY -= sin * 3.0f * dp; }
            playerRecoilOffset = 9.0f * dp; screenShake = Math.max(screenShake, 9.0f * dp);
            playSynthesizedWeaponSound(WEAPON_ROCKET, 0);
        } else if (currentWeapon == WEAPON_FLAMETHROWER) {
            for (int i = 0; i < 2; i++) {
                float fAngle = playerAngle + (float) ((Math.random() - 0.5) * 0.34f), fSpeed = (7.2f + (float) Math.random() * 2.8f) * dp;
                bullets.add(new Bullet(bx, by, (float) Math.cos(fAngle) * fSpeed, (float) Math.sin(fAngle) * fSpeed, 7f * dp, 14f, true, WEAPON_FLAMETHROWER, 28, ""));
            }
            playerRecoilOffset = 1.5f * dp;
            playSynthesizedWeaponSound(WEAPON_FLAMETHROWER, 0);
        } else if (currentWeapon == WEAPON_SNIPER) {
            float bSpeed = 30.0f * dp;
            bullets.add(new Bullet(bx, by, cos * bSpeed, sin * bSpeed, 6.2f * dp, 220f, true, WEAPON_SNIPER, 65, ""));
            laserBeams.add(new LaserBeam(bx, by, bx + cos * 680f * dp, by + sin * 680f * dp, Color.parseColor("#88EF4444")));
            spawnMuzzleFlash(bx, by, playerAngle, Color.parseColor("#FDE047"), 14);
            ejectBrassShell(breechX, breechY, playerAngle, Color.parseColor("#FACC15"));
            playerX -= cos * 6.0f * dp; playerY -= sin * 6.0f * dp;
            if (isSpaceMode) { playerVX -= cos * 3.2f * dp; playerVY -= sin * 3.2f * dp; }
            resolveEntityObstacleCollisions(true, null);
            playerRecoilOffset = 10.0f * dp; screenShake = Math.max(screenShake, 11.0f * dp);
            playSynthesizedWeaponSound(WEAPON_SNIPER, 0);
        } else if (currentWeapon == WEAPON_LASER) {
            fireLaserBeam(bx, by, playerAngle);
            playerRecoilOffset = 3.5f * dp; screenShake = Math.max(screenShake, 3.5f * dp);
            playSynthesizedWeaponSound(WEAPON_LASER, 0);
        } else if (currentWeapon == WEAPON_ELECTRIC) {
            fireElectricChain(bx, by, playerAngle);
            playerRecoilOffset = 4.0f * dp; screenShake = Math.max(screenShake, 4.5f * dp);
        }

        if (currentAmmo > 0) {
            currentAmmo--; lastFirearmAmmo = currentAmmo;
            if (currentAmmo == 0) { currentWeapon = WEAPON_EAGLE; lastFirearmWeapon = WEAPON_EAGLE; lastFirearmAmmo = -1; currentAmmo = -1; }
        }
    }

    private void fireEnemyWeapon(Enemy e, float targetAngle) {
        float gunLength = e.radius * 1.9f, gunOffsetRight = e.radius * 0.36f;
        float cos = (float) Math.cos(targetAngle), sin = (float) Math.sin(targetAngle);
        float bx = e.x + cos * gunLength - sin * gunOffsetRight, by = e.y + sin * gunLength + cos * gunOffsetRight;
        e.recoilOffset = 4.5f * dp;
        float spdMult = getDifficultyBulletSpeedMult(), dmgMult = getDifficultyDamageMult();
        String cause = ENEMY_DEATH_TITLES[Math.max(0, Math.min(9, e.type))];

        switch (e.type) {
            case ENEMY_EAGLE_SCOUT:
                bullets.add(new Bullet(bx, by, cos * 8.2f * dp * spdMult, sin * 8.2f * dp * spdMult, 5.2f * dp, 12f * dmgMult, false, WEAPON_EAGLE, 85, cause));
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#FDE047"), 3);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_EAGLE, 0);
                break;
            case ENEMY_SHOTGUNNER:
                for (int i = 0; i < 5; i++) {
                    float a = targetAngle - 0.22f + (0.44f * i / 4f), bSpd = 7.6f * dp * spdMult;
                    bullets.add(new Bullet(bx, by, (float) Math.cos(a) * bSpd, (float) Math.sin(a) * bSpd, 4.5f * dp, 8f * dmgMult, false, WEAPON_SHOTGUN, 42, cause));
                }
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#FB923C"), 6);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_SHOTGUN, 0);
                break;
            case ENEMY_MACHINE_GUNNER:
                float spread = targetAngle + (float) ((Math.random() - 0.5) * 0.16f), bSpd = 9.2f * dp * spdMult;
                bullets.add(new Bullet(bx, by, (float) Math.cos(spread) * bSpd, (float) Math.sin(spread) * bSpd, 4.5f * dp, 9f * dmgMult, false, WEAPON_MACHINE_GUN, 75, cause));
                spawnMuzzleFlash(bx, by, spread, Color.parseColor("#38BDF8"), 2);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_MACHINE_GUN, 0);
                break;
            case ENEMY_ROCKET_HEAVY:
                bullets.add(new Bullet(bx, by, cos * 6.8f * dp * spdMult, sin * 6.8f * dp * spdMult, 7.8f * dp, 26f * dmgMult, false, WEAPON_ROCKET, 105, cause));
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#EF4444"), 7);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_ROCKET, 0);
                break;
            case ENEMY_PYRO:
                for (int i = 0; i < 2; i++) {
                    float fa = targetAngle + (float) ((Math.random() - 0.5) * 0.30f), fSpd = 6.2f * dp * spdMult;
                    bullets.add(new Bullet(bx, by, (float) Math.cos(fa) * fSpd, (float) Math.sin(fa) * fSpd, 6.5f * dp, 7f * dmgMult, false, WEAPON_FLAMETHROWER, 28, cause));
                }
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_FLAMETHROWER, 0);
                break;
            case ENEMY_SNIPER:
                bullets.add(new Bullet(bx, by, cos * 15.5f * dp * spdMult, sin * 15.5f * dp * spdMult, 5.8f * dp, 28f * dmgMult, false, WEAPON_SNIPER, 95, cause));
                laserBeams.add(new LaserBeam(bx, by, bx + cos * 520f * dp, by + sin * 520f * dp, Color.parseColor("#66EF4444")));
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#EF4444"), 6);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_SNIPER, 0);
                break;
            case ENEMY_LASER:
                bullets.add(new Bullet(bx, by, cos * 12.5f * dp * spdMult, sin * 12.5f * dp * spdMult, 5.0f * dp, 16f * dmgMult, false, WEAPON_LASER, 80, cause));
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#00F5D4"), 5);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_LASER, 0);
                break;
            case ENEMY_ELECTRIC:
                float distP = (float) Math.hypot(playerX - bx, playerY - by);
                if (distP <= 270f * dp) {
                    lightningBolts.add(new LightningBolt(bx, by, playerX, playerY, dp));
                    createExplosion(playerX, playerY, Color.parseColor("#38BDF8"), 6);
                    damagePlayer(14f * dmgMult, cause);
                    if (enemySoundEnabled) playSynthesizedElectricZap(1);
                }
                break;
            case ENEMY_BOMBER:
                float bombSpd = 5.6f * dp * spdMult;
                bullets.add(new Bullet(bx, by, cos * bombSpd, sin * bombSpd, 10.5f * dp, 30f * dmgMult, false, WEAPON_BOMB, 92, cause));
                spawnMuzzleFlash(bx, by, targetAngle, Color.parseColor("#FDE047"), 8);
                if (enemySoundEnabled) playSynthesizedWeaponSound(WEAPON_ROCKET, 0);
                break;
        }
    }

    private void spawnMuzzleFlash(float x, float y, float angle, int color, int count) {
        for (int i = 0; i < count; i++) {
            float spread = angle + (float) ((Math.random() - 0.5) * 0.65), sp = (2.2f + (float) Math.random() * 4.8f) * dp;
            particles.add(new Particle(x, y, (float) Math.cos(spread) * sp, (float) Math.sin(spread) * sp, 3.8f * dp, color, 11));
        }
    }

    private void triggerRocketExplosion(float rx, float ry, boolean fromPlayer, String deathCauseLabel) {
        float blastRadius = (fromPlayer ? 120f : 92f) * dp;
        shockwaves.add(new Shockwave(rx, ry, blastRadius, Color.parseColor("#FB923C")));
        createExplosion(rx, ry, Color.parseColor("#F97316"), 24); createExplosion(rx, ry, Color.parseColor("#FDE047"), 15);
        screenShake = 14f * dp; triggerVibration(65);
        playSynthesizedWeaponSound(99, 0);

        if (fromPlayer) {
            for (int i = enemies.size() - 1; i >= 0; i--) {
                Enemy e = enemies.get(i); float dist = (float) Math.hypot(e.x - rx, e.y - ry);
                if (dist <= blastRadius + e.radius) {
                    if (e.type == ENEMY_KNIFE_RUSHER && Math.hypot(e.x - playerX, e.y - playerY) <= assassinCloseShieldRadius) {
                        e.slashPhase = 1.0f; floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "USE KNIFE!", Color.parseColor("#FACC15"))); continue;
                    }
                    int dmg = Math.round(130f * (1f - (dist / (blastRadius + e.radius)) * 0.45f));
                    e.hp -= dmg; floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "-" + dmg, Color.parseColor("#FBBF24")));
                    if (e.hp <= 0) handleEnemyDefeated(i, e);
                }
            }
        } else if (Math.hypot(playerX - rx, playerY - ry) <= blastRadius + playerRadius) {
            damagePlayer(25f * getDifficultyDamageMult(), deathCauseLabel != null && !deathCauseLabel.isEmpty() ? deathCauseLabel : "Killed by Explosion");
        }
    }

    private void handleEnemyDefeated(int index, Enemy e) {
        killsThisGame++;
        createExplosion(e.x, e.y, isSpaceMode ? Color.parseColor("#A855F7") : Color.parseColor("#DC2626"), 16);
        triggerVibration(18); playBeep(ToneGenerator.TONE_PROP_BEEP2, 35);

        long now = System.currentTimeMillis();
        killStreak = (now - lastKillTime < 3400) ? (killStreak + 1) : 1; lastKillTime = now;
        int basePts = (e.type == ENEMY_ROCKET_HEAVY || e.type == ENEMY_BOMBER) ? 35 : (e.type >= ENEMY_PYRO ? 30 : (e.type == ENEMY_MACHINE_GUNNER ? 25 : 20));
        int mult = Math.min(4, 1 + (killStreak / 3)), gained = basePts * mult;
        score += gained;
        if (listener != null) listener.onScoreUpdated(score);

        floatingTexts.add(new FloatingText(e.x, e.y, "+" + gained, Color.parseColor("#4ADE80")));
        if (killStreak >= 2) {
            streakBannerText = (killStreak == 2) ? "DOUBLE KILL!" : (killStreak == 3 ? "TRIPLE KILL!"
                                                                     : (killStreak == 4 ? "MEGA KILL!" : (killStreak == 5 ? "RAMPAGE!" : "GODLIKE STREAK! (" + mult + "x)")));
            streakBannerUntil = now + 1800;

            String spokenPhrase = (killStreak == 2) ? "Double Kill"
                    : (killStreak == 3 ? "Triple Kill"
                       : (killStreak == 4 ? "Mega Kill"
                          : (killStreak == 5 ? "Rampage"
                             : (killStreak == 6 ? "Unstoppable" : "Godlike"))));
            speakKillStreakAnnouncement(spokenPhrase, killStreak);
        }
        double roll = Math.random();
        if (roll < 0.36) {
            float[] safeDrop = pushOutOfObstacles(e.x, e.y, 20f * dp);
            int dropKind = (e.type >= 2 && e.type <= 8) ? e.type : (2 + (int) (Math.random() * 7));
            pickups.add(new Pickup(safeDrop[0], safeDrop[1], dropKind));
        } else if (roll < 0.58) {
            float[] safeDrop = pushOutOfObstacles(e.x, e.y, 16f * dp);
            pickups.add(new Pickup(safeDrop[0], safeDrop[1], Math.random() < 0.5 ? 0 : 1));
        }
        enemies.remove(index);
    }

    private void createExplosion(float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (Math.random() * Math.PI * 2), speed = (1.5f + (float) Math.random() * 5.8f) * dp, r = (3f + (float) Math.random() * 4.5f) * dp;
            particles.add(new Particle(x, y, (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed, r, color, 22));
        }
    }

    private Bitmap captureDeathSnapshot() {
        try {
            int size = Math.max(120, (int) (220 * dp));
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            float oldW = screenW, oldH = screenH, oldCamX = camX, oldCamY = camY, oldZoom = camZoom, oldShake = screenShake;
            isCapturingSnapshot = true;
            screenW = size; screenH = size; camX = playerX; camY = playerY; camZoom = 1.15f; screenShake = 0f;
            draw(c);
            screenW = oldW; screenH = oldH; camX = oldCamX; camY = oldCamY; camZoom = oldZoom; screenShake = oldShake;
            isCapturingSnapshot = false;
            return bmp;
        } catch (Exception e) {
            isCapturingSnapshot = false;
            return null;
        }
    }

    private void damagePlayer(float amount, String cause) {
        if (cause != null && !cause.isEmpty()) lastDeathCause = cause;
        lastDamageTime = System.currentTimeMillis(); screenShake = 10f * dp; triggerVibration(40);
        if (playerShield > 0) {
            if (playerShield >= amount) { playerShield -= amount; amount = 0; }
            else { amount -= playerShield; playerShield = 0; }
        }
        playerHP -= amount;
        if (playerHP <= 0) {
            playerHP = 0;
            createExplosion(playerX, playerY, Color.parseColor("#EF4444"), 18);
            lastDeathSnapshot = captureDeathSnapshot();
            gameOver = true; playing = false; aimPointerId = -1; movePointerId = -1; isShooting = false;
            triggerVibration(600);
            if (listener != null) listener.onGameOver(score, killsThisGame, weaponShotsUsed, lastDeathSnapshot, lastDeathCause);
        }
    }

    private boolean resolveEntityObstacleCollisions(boolean isPlayerEntity, Enemy enemy) {
        boolean collided = false;
        float ex = isPlayerEntity ? playerX : enemy.x, ey = isPlayerEntity ? playerY : enemy.y, er = isPlayerEntity ? playerRadius : enemy.radius;
        for (Obstacle ob : obstacles) {
            float solidR = ob.radius * 0.85f, dx = ex - ob.x, dy = ey - ob.y, dist = (float) Math.hypot(dx, dy), minDist = er + solidR;
            if (dist < minDist) {
                collided = true;
                if (dist < 0.001f) { dx = 1f; dy = 0f; dist = 1f; }
                float overlap = minDist - dist, nx = dx / dist, ny = dy / dist;
                ex += nx * overlap; ey += ny * overlap;
                if (isPlayerEntity && isSpaceMode) { playerVX *= 0.5f; playerVY *= 0.5f; }
                else if (!isPlayerEntity && enemy != null) { ex += (-ny * enemy.strafeDir) * enemy.speed * 0.55f; ey += (nx * enemy.strafeDir) * enemy.speed * 0.55f; }
            }
        }
        if (isPlayerEntity) { playerX = ex; playerY = ey; } else if (enemy != null) { enemy.x = ex; enemy.y = ey; }
        return collided;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        Choreographer.getInstance().removeFrameCallback(this);
        if (paused || gameOver) return;

        long now = System.currentTimeMillis();
        starPhase += 0.05f;
        playerRecoilOffset = (playerRecoilOffset > 0.2f) ? (playerRecoilOffset * 0.78f) : 0f;
        playerSlashPhase = (playerSlashPhase > 0.05f) ? (playerSlashPhase * 0.80f) : 0f;
        camZoom += (getWeaponTargetCameraZoom(currentWeapon) - camZoom) * 0.08f;

        if (isSpaceMode) for (Obstacle ob : obstacles) ob.rotationDeg = (ob.rotationDeg + ob.rotSpeed) % 360f;

        if (playing) {
            if (currentWeapon == WEAPON_SNIPER) {
                autoAimSniperTarget = findAutoAimSniperTarget();
                if (autoAimSniperTarget != null && aimPointerId != -1) playerAngle = (float) Math.atan2(autoAimSniperTarget.y - playerY, autoAimSniperTarget.x - playerX);
            } else autoAimSniperTarget = null;

            float weightMult = getWeaponWeightSpeedMultiplier(currentWeapon);
            float targetVX = moveDX * playerSpeed * weightMult, targetVY = moveDY * playerSpeed * weightMult;

            if (isSpaceMode) {
                playerVX = playerVX * 0.91f + targetVX * 0.09f; playerVY = playerVY * 0.91f + targetVY * 0.09f;
                for (Obstacle ob : obstacles) {
                    float gdx = ob.x - playerX, gdy = ob.y - playerY, gDist = (float) Math.hypot(gdx, gdy), gravRange = ob.radius * 3.4f;
                    if (gDist > ob.radius && gDist < gravRange) {
                        float pull = (1f - (gDist / gravRange)) * 0.14f * dp;
                        playerVX += (gdx / gDist) * pull; playerVY += (gdy / gDist) * pull;
                    }
                }
                playerX += playerVX; playerY += playerVY;
            } else { playerX += targetVX; playerY += targetVY; }

            resolveEntityObstacleCollisions(true, null);

            float moveMag = (float) Math.hypot(moveDX, moveDY);
            if (moveMag > 0.08f) playerWalkPhase += moveMag * 0.34f * weightMult;

            if (isSpaceMode && moveMag > 0.08f && Math.random() < 0.75f) {
                float cos = (float) Math.cos(playerAngle), sin = (float) Math.sin(playerAngle);
                for (int side = -1; side <= 1; side += 2) {
                    float nozX = playerX - cos * playerRadius * 1.18f - sin * (playerRadius * 0.42f * side);
                    float nozY = playerY - sin * playerRadius * 1.18f + cos * (playerRadius * 0.42f * side);
                    particles.add(new Particle(nozX, nozY, -moveDX * 2.2f * dp, -moveDY * 2.2f * dp,
                            3.4f * dp, side == -1 ? Color.parseColor("#38BDF8") : Color.parseColor("#60A5FA"), 13));
                }
            }

            float distFromCenter = (float) Math.hypot(playerX, playerY);
            if (distFromCenter > worldRadius - playerRadius) {
                playerX = (playerX / distFromCenter) * (worldRadius - playerRadius);
                playerY = (playerY / distFromCenter) * (worldRadius - playerRadius);
                damagePlayer(0.25f, "Killed by Zone Border");
                if (gameOver) return;
            }

            if (now - lastDamageTime > 4000 && playerShield < maxShield) playerShield = Math.min(maxShield, playerShield + 0.25f);
            if (isShooting && now - lastShotTime >= getCurrentWeaponCooldown()) { lastShotTime = now; firePlayerWeapon(); }

            if (now - lastCrateSpawnTime > 9500 && pickups.size() < 12) {
                lastCrateSpawnTime = now;
                float angle = (float) (Math.random() * Math.PI * 2), dist = (150f + (float) Math.random() * (worldRadius / dp - 280f)) * dp;
                float[] safeDrop = pushOutOfObstacles((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist, 24f * dp);
                pickups.add(new Pickup(safeDrop[0], safeDrop[1], 2 + (int) (Math.random() * 7)));
            }

            long baseSpawnMs = (currentDifficulty == 0) ? 2900L : (currentDifficulty == 1 ? 1850L : 1180L);
            long minSpawnMs = (currentDifficulty == 0) ? 1500L : (currentDifficulty == 1 ? 750L : 460L);
            int maxEnemies = (currentDifficulty == 0) ? 10 : (currentDifficulty == 1 ? 20 : 30);
            if (now - lastEnemySpawnTime > Math.max(minSpawnMs, baseSpawnMs - waveCounter * 18L) && enemies.size() < maxEnemies) {
                lastEnemySpawnTime = now; spawnEnemy();
            }

            Iterator<Bullet> bIt = bullets.iterator();
            while (bIt.hasNext()) {
                Bullet b = bIt.next();
                b.x += b.dx; b.y += b.dy; b.life--;

                if (b.weaponType == WEAPON_FLAMETHROWER) { b.radius += 0.65f * dp; b.dx *= 0.94f; b.dy *= 0.94f; }
                else if (b.weaponType == WEAPON_BOMB) {
                    b.dx *= 0.985f; b.dy *= 0.985f;
                    if (b.life % 3 == 0) particles.add(new Particle(b.x, b.y, (float) ((Math.random() - 0.5) * 2.4f * dp), (float) ((Math.random() - 0.5) * 2.4f * dp), 3.6f * dp, Color.parseColor("#FDE047"), 10));
                } else if (b.weaponType == WEAPON_ROCKET) {
                    particles.add(new Particle(b.x - b.dx * 0.3f, b.y - b.dy * 0.3f,
                            (float) ((Math.random() - 0.5) * 1.2f * dp), (float) ((Math.random() - 0.5) * 1.2f * dp), 4.2f * dp, Color.parseColor("#FB923C"), 12));
                }

                boolean hitObstacle = false;
                for (Obstacle ob : obstacles) {
                    if (Math.hypot(b.x - ob.x, b.y - ob.y) < ob.radius * 0.80f + b.radius) {
                        hitObstacle = true;
                        if (b.weaponType == WEAPON_ROCKET || b.weaponType == WEAPON_BOMB) triggerRocketExplosion(b.x, b.y, b.fromPlayer, b.sourceCause);
                        else createExplosion(b.x, b.y, isSpaceMode ? Color.parseColor("#94A3B8") : Color.parseColor("#28C76F"), 3);
                        break;
                    }
                }
                if (hitObstacle) { bIt.remove(); if (gameOver) return; continue; }
                if (b.life <= 0 || Math.hypot(b.x, b.y) > worldRadius + 100f * dp) {
                    if (b.weaponType == WEAPON_ROCKET || b.weaponType == WEAPON_BOMB) triggerRocketExplosion(b.x, b.y, b.fromPlayer, b.sourceCause);
                    bIt.remove(); if (gameOver) return; continue;
                }

                if (b.fromPlayer) {
                    for (int i = enemies.size() - 1; i >= 0; i--) {
                        Enemy e = enemies.get(i);
                        if (Math.hypot(b.x - e.x, b.y - e.y) < e.radius + b.radius) {
                            if (e.type == ENEMY_KNIFE_RUSHER && Math.hypot(e.x - playerX, e.y - playerY) <= assassinCloseShieldRadius) {
                                e.slashPhase = 1.0f; createExplosion(b.x, b.y, Color.parseColor("#FDE047"), 5);
                                if (now - e.lastBlockTextTime > 650) { e.lastBlockTextTime = now; floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "USE KNIFE!", Color.parseColor("#FACC15"))); }
                                playBeep(ToneGenerator.TONE_PROP_NACK, 20); bIt.remove(); break;
                            }
                            if (b.weaponType == WEAPON_ROCKET) { triggerRocketExplosion(b.x, b.y, true, ""); bIt.remove(); break; }
                            else {
                                e.hp -= b.damage; createExplosion(b.x, b.y, b.weaponType == WEAPON_FLAMETHROWER ? Color.parseColor("#FB923C") : Color.parseColor("#EF4444"), 5);
                                if (b.weaponType == WEAPON_SNIPER) floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "SNIPED -220!", Color.parseColor("#EF4444")));
                                else if (b.weaponType == WEAPON_FLAMETHROWER) floatingTexts.add(new FloatingText(e.x, e.y - e.radius, "BURN -" + (int) b.damage, Color.parseColor("#FB923C")));
                                if (e.hp <= 0) handleEnemyDefeated(i, e);
                                bIt.remove(); break;
                            }
                        }
                    }
                } else if (Math.hypot(b.x - playerX, b.y - playerY) < playerRadius + b.radius) {
                    if (b.weaponType == WEAPON_ROCKET || b.weaponType == WEAPON_BOMB) triggerRocketExplosion(b.x, b.y, false, b.sourceCause);
                    else { createExplosion(b.x, b.y, Color.parseColor("#38BDF8"), 6); damagePlayer(b.damage, b.sourceCause); }
                    bIt.remove();
                    if (gameOver) return;
                }
            }

            float fireDelayMult = getDifficultyEnemyFireDelayMult(), dmgMult = getDifficultyDamageMult();
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e = enemies.get(i);
                float dx = playerX - e.x, dy = playerY - e.y, dist = (float) Math.hypot(dx, dy);
                e.angle = (float) Math.atan2(dy, dx);
                e.recoilOffset = (e.recoilOffset > 0.2f) ? (e.recoilOffset * 0.8f) : 0f;
                float normX = (dist > 1f) ? (dx / dist) : 0f, normY = (dist > 1f) ? (dy / dist) : 0f;
                float strafeX = -normY * e.strafeDir, strafeY = normX * e.strafeDir;

                if (e.type == ENEMY_KNIFE_RUSHER) {
                    e.x += normX * e.speed; e.y += normY * e.speed; e.walkPhase += 0.42f;
                    resolveEntityObstacleCollisions(false, e);
                    if (dist < playerRadius + e.radius + 14f * dp) {
                        e.slashPhase = 1.0f;
                        if (now - e.lastShot > (long) (440 * fireDelayMult)) {
                            e.lastShot = now; createExplosion(playerX, playerY, Color.parseColor("#EF4444"), 6);
                            damagePlayer(11f * dmgMult, "Killed by Assassin");
                            if (gameOver) return;
                        }
                    }
                    e.slashPhase = (e.slashPhase > 0.05f) ? (e.slashPhase * 0.84f) : 0f;
                } else {
                    float minRange = (e.type == ENEMY_SHOTGUNNER || e.type == ENEMY_PYRO) ? 145f * dp : (e.type == ENEMY_SNIPER ? 360f * dp : 230f * dp);
                    float maxRange = (e.type == ENEMY_SHOTGUNNER || e.type == ENEMY_PYRO) ? 215f * dp : (e.type == ENEMY_SNIPER ? 520f * dp : 365f * dp);
                    if (dist > maxRange) { e.x += (normX * 0.85f + strafeX * 0.35f) * e.speed; e.y += (normY * 0.85f + strafeY * 0.35f) * e.speed; e.walkPhase += 0.26f; }
                    else if (dist < minRange) { e.x += (-normX * 0.75f + strafeX * 0.5f) * e.speed; e.y += (-normY * 0.75f + strafeY * 0.5f) * e.speed; e.walkPhase += 0.24f; }
                    else { e.x += strafeX * e.speed * 0.75f; e.y += strafeY * e.speed * 0.75f; e.walkPhase += 0.20f; if (Math.random() < 0.012f) e.strafeDir = -e.strafeDir; }

                    resolveEntityObstacleCollisions(false, e);
                    float eCenterDist = (float) Math.hypot(e.x, e.y);
                    if (eCenterDist > worldRadius - e.radius) { e.x = (e.x / eCenterDist) * (worldRadius - e.radius); e.y = (e.y / eCenterDist) * (worldRadius - e.radius); }

                    if (dist < maxRange + 95f * dp) {
                        if (e.type == ENEMY_MACHINE_GUNNER) {
                            if (e.burstCount < 4 && now - e.lastShot > 150) { e.lastShot = now; e.burstCount++; fireEnemyWeapon(e, e.angle); }
                            else if (e.burstCount >= 4 && now - e.lastShot > (long) (1750 * fireDelayMult)) e.burstCount = 0;
                        } else {
                            long baseDelay = (e.type == ENEMY_PYRO) ? 220L : (e.type == ENEMY_EAGLE_SCOUT ? 1450L : (e.type == ENEMY_LASER || e.type == ENEMY_ELECTRIC ? 1650L : (e.type == ENEMY_SHOTGUNNER ? 1950L : (e.type == ENEMY_BOMBER ? 2350L : 2700L))));
                            if (now - e.lastShot > (long) (baseDelay * fireDelayMult)) {
                                e.lastShot = now; fireEnemyWeapon(e, e.angle);
                                if (gameOver) return;
                            }
                        }
                    }
                }

                for (int j = i + 1; j < enemies.size(); j++) {
                    Enemy other = enemies.get(j);
                    float edx = e.x - other.x, edy = e.y - other.y, edist = (float) Math.hypot(edx, edy), minEDist = e.radius + other.radius;
                    if (edist > 0.01f && edist < minEDist) {
                        float push = (minEDist - edist) * 0.5f;
                        e.x += (edx / edist) * push; e.y += (edy / edist) * push;
                        other.x -= (edx / edist) * push; other.y -= (edy / edist) * push;
                    }
                }
            }

            Iterator<Pickup> pIt = pickups.iterator();
            while (pIt.hasNext()) {
                Pickup pk = pIt.next(); pk.life--;
                if (pk.life <= 0) { pIt.remove(); continue; }
                if (Math.hypot(playerX - pk.x, playerY - pk.y) < playerRadius + 24f * dp) {
                    if (pk.kind == 0) {
                        playerHP = Math.min(maxHP, playerHP + 30f); createExplosion(pk.x, pk.y, Color.parseColor("#4CD964"), 8);
                        floatingTexts.add(new FloatingText(playerX, playerY - playerRadius, "+30 HP", Color.parseColor("#4ADE80"))); playBeep(ToneGenerator.TONE_PROP_ACK, 40);
                    } else if (pk.kind == 1) {
                        playerShield = Math.min(maxShield, playerShield + 40f); createExplosion(pk.x, pk.y, Color.parseColor("#29B6F6"), 8);
                        floatingTexts.add(new FloatingText(playerX, playerY - playerRadius, "+40 SHIELD", Color.parseColor("#38BDF8"))); playBeep(ToneGenerator.TONE_PROP_ACK, 40);
                    } else {
                        int wMap = (pk.kind == 2) ? WEAPON_MACHINE_GUN : (pk.kind == 3 ? WEAPON_SHOTGUN : (pk.kind == 4 ? WEAPON_ROCKET
                                                                                                           : (pk.kind == 5 ? WEAPON_FLAMETHROWER : (pk.kind == 6 ? WEAPON_SNIPER : (pk.kind == 7 ? WEAPON_LASER : WEAPON_ELECTRIC)))));
                        equipWeapon(wMap); createExplosion(pk.x, pk.y, Color.parseColor("#FACC15"), 10);
                    }
                    pIt.remove();
                }
            }
        }

        if (isMoonlightMode) for (Star s : stars) { s.x += (float) Math.cos(starPhase + s.phase) * 0.45f * dp; s.y += (float) Math.sin(starPhase * 0.8f + s.phase) * 0.45f * dp; }
        for (int i = laserBeams.size() - 1; i >= 0; i--) if (--laserBeams.get(i).life <= 0) laserBeams.remove(i);
        for (int i = lightningBolts.size() - 1; i >= 0; i--) if (--lightningBolts.get(i).life <= 0) lightningBolts.remove(i);
        for (int i = shells.size() - 1; i >= 0; i--) { ShellCasing sc = shells.get(i); sc.x += sc.dx; sc.y += sc.dy; sc.dx *= 0.88f; sc.dy *= 0.88f; sc.rot += sc.rotSpd; if (--sc.life <= 0) shells.remove(i); }
        for (int i = floatingTexts.size() - 1; i >= 0; i--) { FloatingText ft = floatingTexts.get(i); ft.y -= 1.2f * dp; if (--ft.life <= 0) floatingTexts.remove(i); }
        for (int i = particles.size() - 1; i >= 0; i--) { Particle p = particles.get(i); p.x += p.dx; p.y += p.dy; p.dx *= 0.92f; p.dy *= 0.92f; if (--p.life <= 0) particles.remove(i); }
        for (int i = shockwaves.size() - 1; i >= 0; i--) { Shockwave sw = shockwaves.get(i); sw.currentRadius += (sw.maxRadius - sw.currentRadius) * 0.25f; sw.alpha -= 14; if (sw.alpha <= 0) shockwaves.remove(i); }

        camX += (playerX - camX) * 0.14f; camY += (playerY - camY) * 0.14f;
        screenShake = (screenShake > 0.4f) ? (screenShake * 0.85f) : 0f;

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(groundColor);

        float shakeX = (float) ((Math.random() - 0.5) * screenShake), shakeY = (float) ((Math.random() - 0.5) * screenShake);
        canvas.save();
        canvas.translate(screenW / 2f + shakeX, screenH / 2f + shakeY);
        canvas.scale(camZoom, camZoom);
        canvas.translate(-camX, -camY);

        if (isSunnyDay) drawSunnyMeadowGlades(canvas);

        float viewW = screenW / camZoom, viewH = screenH / camZoom, gridStep = 110f * dp;
        paint.setColor(gridColor); paint.setStrokeWidth(2f * dp);
        float startX = (float) Math.floor((camX - viewW) / gridStep) * gridStep, endX = camX + viewW;
        float startY = (float) Math.floor((camY - viewH) / gridStep) * gridStep, endY = camY + viewH;
        for (float gx = startX; gx <= endX; gx += gridStep) canvas.drawLine(gx, startY, gx, endY, paint);
        for (float gy = startY; gy <= endY; gy += gridStep) canvas.drawLine(startX, gy, endX, gy, paint);

        if (isSpaceMode) drawSpaceStarsOrFireflies(canvas, viewW, viewH);
        else drawGrassTufts(canvas, viewW, viewH);

        strokePaint.setColor(isSpaceMode ? Color.parseColor("#7C3AED") : Color.parseColor("#E11D48"));
        strokePaint.setStrokeWidth(14f * dp); canvas.drawCircle(0, 0, worldRadius, strokePaint);
        strokePaint.setColor(isSpaceMode ? Color.parseColor("#38BDF8") : Color.parseColor("#FBBF24"));
        strokePaint.setStrokeWidth(3.5f * dp); canvas.drawCircle(0, 0, worldRadius - 7f * dp, strokePaint);

        for (OutZoneDecoration dec : outZoneDecorations) {
            if (dec.x < camX - viewW - 60f * dp || dec.x > camX + viewW + 60f * dp || dec.y < camY - viewH - 60f * dp || dec.y > camY + viewH + 60f * dp) continue;
            drawOutZoneDecoration(canvas, dec);
        }

        paint.setStyle(Paint.Style.FILL);
        for (ShellCasing sc : shells) {
            canvas.save(); canvas.translate(sc.x, sc.y); canvas.rotate(sc.rot);
            paint.setColor(sc.color); paint.setAlpha(Math.min(255, sc.life * 8));
            tempRect.set(-3.2f * dp, -1.4f * dp, 3.2f * dp, 1.4f * dp); canvas.drawRoundRect(tempRect, 1f * dp, 1f * dp, paint);
            canvas.restore();
        }
        paint.setAlpha(255);

        if (playing && !gameOver && !isCapturingSnapshot && aimPointerId != -1) drawWeaponScopeSight(canvas);

        for (Pickup pk : pickups) drawPickupItem(canvas, pk);
        for (Bullet b : bullets) drawProjectile(canvas, b);

        for (LaserBeam lb : laserBeams) {
            float alphaRatio = lb.life / (float) lb.maxLife;
            strokePaint.setColor(lb.color); strokePaint.setAlpha((int) (110 * alphaRatio));
            strokePaint.setStrokeWidth(13f * dp * alphaRatio);
            canvas.drawLine(lb.x1, lb.y1, lb.x2, lb.y2, strokePaint);
            strokePaint.setColor(Color.WHITE); strokePaint.setAlpha((int) (240 * alphaRatio));
            strokePaint.setStrokeWidth(4.5f * dp * alphaRatio);
            canvas.drawLine(lb.x1, lb.y1, lb.x2, lb.y2, strokePaint);
        }
        strokePaint.setAlpha(255);

        for (LightningBolt lt : lightningBolts) {
            float alphaRatio = lt.life / (float) lt.maxLife;
            strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setAlpha((int) (180 * alphaRatio));
            strokePaint.setStrokeWidth(6.5f * dp);
            canvas.drawPath(lt.boltPath, strokePaint);
            strokePaint.setColor(Color.WHITE); strokePaint.setAlpha((int) (255 * alphaRatio));
            strokePaint.setStrokeWidth(2.2f * dp);
            canvas.drawPath(lt.boltPath, strokePaint);
        }
        strokePaint.setAlpha(255);

        for (Shockwave sw : shockwaves) {
            strokePaint.setColor(sw.color); strokePaint.setAlpha(Math.max(0, sw.alpha)); strokePaint.setStrokeWidth(6f * dp);
            canvas.drawCircle(sw.x, sw.y, sw.currentRadius, strokePaint);
        }
        strokePaint.setAlpha(255);

        for (Obstacle ob : obstacles) { if (isSpaceMode) drawAsteroid(canvas, ob); else drawTexturedTree(canvas, ob); }

        for (Enemy e : enemies) {
            if (e.type == ENEMY_KNIFE_RUSHER && Math.hypot(e.x - playerX, e.y - playerY) <= assassinCloseShieldRadius) {
                strokePaint.setColor(Color.parseColor("#FACC15")); strokePaint.setStrokeWidth(2.6f * dp);
                canvas.drawCircle(e.x, e.y, e.radius * 1.45f, strokePaint);
            }
            int eWeapon = getEnemyWeaponType(e.type);
            drawHumanCharacter(canvas, e.x, e.y, e.radius, e.angle, false, e.type, eWeapon, e.walkPhase, e.recoilOffset, e.slashPhase, true);

            float bw = e.radius * 2.1f, bh = 5f * dp, bx = e.x - bw / 2f, by = e.y - e.radius - 15f * dp;
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#66000000")); canvas.drawRect(bx, by, bx + bw, by + bh, paint);
            paint.setColor(isSpaceMode ? Color.parseColor("#C084FC") : Color.parseColor("#EF4444"));
            canvas.drawRect(bx, by, bx + bw * Math.max(0f, e.hp / e.maxHp), by + bh, paint);
        }

        boolean playerJetpackFiring = (Math.hypot(moveDX, moveDY) > 0.08f);
        drawHumanCharacter(canvas, playerX, playerY, playerRadius, playerAngle, true, -1, currentWeapon, playerWalkPhase, playerRecoilOffset, playerSlashPhase, playerJetpackFiring);

        float barW = playerRadius * 2.6f, barH = 5.5f * dp, barX = playerX - barW / 2f, shieldY = playerY - playerRadius - 25f * dp, hpY = shieldY + barH + 3.5f * dp;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#77000000"));
        canvas.drawRect(barX, shieldY, barX + barW, shieldY + barH, paint); canvas.drawRect(barX, hpY, barX + barW, hpY + barH, paint);
        paint.setColor(Color.parseColor("#29B6F6")); canvas.drawRect(barX, shieldY, barX + barW * (playerShield / maxShield), shieldY + barH, paint);
        paint.setColor(Color.parseColor("#4CD964")); canvas.drawRect(barX, hpY, barX + barW * (playerHP / maxHP), hpY + barH, paint);

        if (isMoonlightMode) drawSpaceStarsOrFireflies(canvas, viewW, viewH);

        paint.setStyle(Paint.Style.FILL);
        for (Particle p : particles) {
            paint.setColor(p.color); paint.setAlpha((int) (255f * ((float) p.life / p.maxLife)));
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }
        paint.setAlpha(255);

        Typeface bloomTypeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC);
        Paint strokePaintFT = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaintFT.setTypeface(bloomTypeface);
        strokePaintFT.setTextSize(16f * dp);
        strokePaintFT.setStyle(Paint.Style.STROKE);
        strokePaintFT.setStrokeWidth(3f * dp);
        strokePaintFT.setStrokeJoin(Paint.Join.ROUND);
        strokePaintFT.setTextAlign(Paint.Align.CENTER);

        Paint fillPaintFT = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaintFT.setTypeface(bloomTypeface);
        fillPaintFT.setTextSize(16f * dp);
        fillPaintFT.setStyle(Paint.Style.FILL);
        fillPaintFT.setTextAlign(Paint.Align.CENTER);

        for (FloatingText ft : floatingTexts) {
            int alpha = Math.min(255, ft.life * 10);
            String t = ft.text.toUpperCase();

            int strokeColor = Color.parseColor("#14532D");
            int topColor = Color.parseColor("#86EFAC");

            if (t.contains("ZAP")) {
                strokeColor = Color.parseColor("#1E3A8A");
                topColor = Color.parseColor("#60A5FA");
            } else if (t.contains("SLASH") || t.contains("SPLASH")) {
                strokeColor = Color.parseColor("#374151");
                topColor = Color.parseColor("#D1D5DB");
            } else if (t.contains("LASER")) {
                strokeColor = Color.parseColor("#7F1D1D");
                topColor = Color.parseColor("#F87171");
            } else if (t.contains("BURN")) {
                strokeColor = Color.parseColor("#7C2D12");
                topColor = Color.parseColor("#FB923C");
            } else if (t.contains("+")) {
                strokeColor = Color.parseColor("#14532D");
                topColor = Color.parseColor("#86EFAC");
            } else {
                float[] hsv = new float[3];
                Color.colorToHSV(ft.color, hsv);
                topColor = ft.color;
                hsv[2] = Math.max(0.1f, hsv[2] * 0.4f);
                strokeColor = Color.HSVToColor(hsv);
            }

            strokePaintFT.setColor(strokeColor);
            strokePaintFT.setAlpha(alpha);
            canvas.drawText(ft.text, ft.x, ft.y, strokePaintFT);

            Shader gradient = new LinearGradient(0, ft.y - 14f * dp, 0, ft.y + 2f * dp,
                    topColor, Color.WHITE, Shader.TileMode.CLAMP);
            fillPaintFT.setShader(gradient);
            fillPaintFT.setAlpha(alpha);
            canvas.drawText(ft.text, ft.x, ft.y, fillPaintFT);
        }
        canvas.restore();

        drawAtmosphericLighting(canvas);
        if (!isCapturingSnapshot) {
            drawLeftMinimap(canvas);
            drawRightSmartPillWeaponHud(canvas);
            drawJoysticks(canvas);
        }
    }

    private void drawWeaponScopeSight(Canvas canvas) {
        float scopeDist = getWeaponScopeDistance(currentWeapon);
        float cos = (float) Math.cos(playerAngle), sin = (float) Math.sin(playerAngle);
        float forwardOffset = (currentWeapon == WEAPON_KNIFE) ? (playerRadius * 1.35f) : (playerRadius * 2.05f - playerRecoilOffset);
        float rightHandOffset = (currentWeapon == WEAPON_KNIFE) ? (playerRadius * 0.58f) : (playerRadius * 0.36f);
        float startX = playerX + cos * forwardOffset - sin * rightHandOffset;
        float startY = playerY + sin * forwardOffset + cos * rightHandOffset;

        int scopeColor = (currentWeapon == WEAPON_SNIPER) ? Color.parseColor("#EF4444")
                : (currentWeapon == WEAPON_LASER ? Color.parseColor("#00F5D4")
                   : (currentWeapon == WEAPON_ELECTRIC ? Color.parseColor("#38BDF8")
                      : (currentWeapon == WEAPON_FLAMETHROWER || currentWeapon == WEAPON_SHOTGUN ? Color.parseColor("#FB923C") : Color.parseColor("#FACC15"))));

        scopePaint.setColor(scopeColor); scopePaint.setAlpha(145); scopePaint.setStrokeWidth(2.0f * dp); scopePaint.setPathEffect(scopeDash);

        if (currentWeapon == WEAPON_KNIFE || currentWeapon == WEAPON_SHOTGUN || currentWeapon == WEAPON_FLAMETHROWER) {
            float halfDeg = (currentWeapon == WEAPON_KNIFE) ? 55f : (currentWeapon == WEAPON_SHOTGUN ? 18f : 16f);
            float leftRad = playerAngle - (float) Math.toRadians(halfDeg), rightRad = playerAngle + (float) Math.toRadians(halfDeg);
            canvas.drawLine(startX, startY, startX + (float) Math.cos(leftRad) * scopeDist, startY + (float) Math.sin(leftRad) * scopeDist, scopePaint);
            canvas.drawLine(startX, startY, startX + (float) Math.cos(rightRad) * scopeDist, startY + (float) Math.sin(rightRad) * scopeDist, scopePaint);
        } else if (currentWeapon == WEAPON_SNIPER && autoAimSniperTarget != null) {
            canvas.drawLine(startX, startY, autoAimSniperTarget.x, autoAimSniperTarget.y, scopePaint);
        } else {
            canvas.drawLine(startX, startY, startX + cos * scopeDist, startY + sin * scopeDist, scopePaint);
        }
        scopePaint.setPathEffect(null); scopePaint.setAlpha(255);
    }

    private void drawOutZoneDecoration(Canvas canvas, OutZoneDecoration dec) {
        canvas.save();
        canvas.translate(dec.x, dec.y);
        canvas.rotate(dec.rotationDeg);

        paint.setStyle(Paint.Style.FILL);
        strokePaint.setStrokeWidth(2f * dp);

        if (dec.type == 0) {
            paint.setColor(isSunnyDay ? Color.parseColor("#CBD5E1") : Color.parseColor("#4B5563"));
            strokePaint.setColor(isSunnyDay ? Color.parseColor("#64748B") : Color.parseColor("#1F2937"));
            canvas.drawCircle(0, -4f * dp, 10f * dp, paint); canvas.drawCircle(0, -4f * dp, 10f * dp, strokePaint);
            tempRect.set(-6f * dp, 0f, 6f * dp, 10f * dp); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
            paint.setColor(isSunnyDay ? Color.parseColor("#64748B") : Color.parseColor("#111827"));
            canvas.drawCircle(-3.5f * dp, -2f * dp, 2.5f * dp, paint); canvas.drawCircle(3.5f * dp, -2f * dp, 2.5f * dp, paint);
            tempPath.reset(); tempPath.moveTo(0, 2f * dp); tempPath.lineTo(-2f * dp, 5f * dp); tempPath.lineTo(2f * dp, 5f * dp); tempPath.close(); canvas.drawPath(tempPath, paint);
            strokePaint.setStrokeWidth(1.5f * dp); canvas.drawLine(-3f * dp, 10f * dp, -3f * dp, 6f * dp, strokePaint); canvas.drawLine(0f, 10f * dp, 0f, 6f * dp, strokePaint); canvas.drawLine(3f * dp, 10f * dp, 3f * dp, 6f * dp, strokePaint);
        } else if (dec.type == 1) {
            paint.setColor(isSunnyDay ? Color.parseColor("#CBD5E1") : Color.parseColor("#4B5563"));
            strokePaint.setColor(isSunnyDay ? Color.parseColor("#64748B") : Color.parseColor("#1F2937"));
            tempRect.set(-3.5f * dp, -12f * dp, 3.5f * dp, 12f * dp); canvas.drawRect(tempRect, paint);
            canvas.drawCircle(-4f * dp, -12f * dp, 4.5f * dp, paint); canvas.drawCircle(4f * dp, -12f * dp, 4.5f * dp, paint);
            canvas.drawCircle(-4f * dp, 12f * dp, 4.5f * dp, paint); canvas.drawCircle(4f * dp, 12f * dp, 4.5f * dp, paint);
            canvas.drawCircle(-4f * dp, -12f * dp, 4.5f * dp, strokePaint); canvas.drawCircle(4f * dp, -12f * dp, 4.5f * dp, strokePaint);
            canvas.drawCircle(-4f * dp, 12f * dp, 4.5f * dp, strokePaint); canvas.drawCircle(4f * dp, 12f * dp, 4.5f * dp, strokePaint);
            canvas.drawLine(-3.5f * dp, -8f * dp, -3.5f * dp, 8f * dp, strokePaint); canvas.drawLine(3.5f * dp, -8f * dp, 3.5f * dp, 8f * dp, strokePaint);
        } else if (dec.type == 2) {
            paint.setColor(Color.parseColor("#475569")); strokePaint.setColor(Color.parseColor("#1E293B"));
            tempPath.reset(); tempPath.moveTo(-10f * dp, -20f * dp); tempPath.lineTo(10f * dp, -20f * dp); tempPath.lineTo(15f * dp, 10f * dp); tempPath.lineTo(12f * dp, 20f * dp); tempPath.lineTo(-5f * dp, 15f * dp); tempPath.lineTo(-15f * dp, 5f * dp); tempPath.close(); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempPath.reset(); tempPath.moveTo(10f * dp, -10f * dp); tempPath.lineTo(30f * dp, 5f * dp); tempPath.lineTo(20f * dp, 15f * dp); tempPath.lineTo(14f * dp, 5f * dp); tempPath.close(); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            paint.setColor(Color.parseColor("#0F172A")); tempRect.set(-6f * dp, -15f * dp, 6f * dp, -5f * dp); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
            paint.setColor(Color.parseColor("#F59E0B")); canvas.drawCircle(-5f * dp, 18f * dp, 3f * dp, paint); canvas.drawCircle(8f * dp, 22f * dp, 2.5f * dp, paint);
        } else if (dec.type == 3) {
            paint.setColor(Color.parseColor("#1E3A8A")); strokePaint.setColor(Color.parseColor("#0F172A"));
            tempRect.set(-30f * dp, -8f * dp, -10f * dp, 8f * dp); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            canvas.drawLine(-20f * dp, -8f * dp, -20f * dp, 8f * dp, strokePaint); canvas.drawLine(-30f * dp, 0f, -10f * dp, 0f, strokePaint);
            tempRect.set(10f * dp, -6f * dp, 20f * dp, 6f * dp); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            paint.setColor(Color.parseColor("#94A3B8")); tempRect.set(-10f * dp, -12f * dp, 10f * dp, 12f * dp); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, strokePaint);
            strokePaint.setStrokeWidth(2f * dp); canvas.drawLine(0f, -12f * dp, 5f * dp, -25f * dp, strokePaint);
            paint.setColor(Color.parseColor("#EF4444")); canvas.drawCircle(5f * dp, -25f * dp, 2.5f * dp, paint);
        }
        canvas.restore();
    }

    private void drawSunnyMeadowGlades(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        for (MeadowGlade mg : meadowGlades) {
            if (mg.isStone) {
                paint.setColor(Color.parseColor("#D6E0D6")); canvas.drawCircle(mg.x, mg.y, mg.radius, paint);
                strokePaint.setColor(Color.parseColor("#9EB2A0")); strokePaint.setStrokeWidth(1.8f * dp); canvas.drawCircle(mg.x, mg.y, mg.radius, strokePaint);
            } else {
                paint.setColor(mg.lighterTone ? Color.parseColor("#92CE82") : Color.parseColor("#7EB86E")); canvas.drawCircle(mg.x, mg.y, mg.radius, paint);
            }
        }
    }

    private void drawGrassTufts(Canvas canvas, float viewW, float viewH) {
        strokePaint.setColor(isSunnyDay ? Color.parseColor("#4E9440") : Color.parseColor("#17375E"));
        strokePaint.setStrokeWidth(2.0f * dp); paint.setStyle(Paint.Style.FILL);
        for (GrassPatch gp : grassPatches) {
            if (gp.x < camX - viewW || gp.x > camX + viewW || gp.y < camY - viewH || gp.y > camY + viewH) continue;
            float s = gp.size;
            canvas.drawLine(gp.x, gp.y, gp.x - s * 0.55f, gp.y - s * 0.75f, strokePaint);
            canvas.drawLine(gp.x, gp.y, gp.x, gp.y - s * 0.95f, strokePaint);
            canvas.drawLine(gp.x, gp.y, gp.x + s * 0.55f, gp.y - s * 0.75f, strokePaint);
            if (gp.hasFlower) {
                float fx = gp.x + s * 0.45f, fy = gp.y - s * 0.75f;
                paint.setColor(gp.flowerColor);
                canvas.drawCircle(fx - 2.2f * dp, fy, 2.2f * dp, paint); canvas.drawCircle(fx + 2.2f * dp, fy, 2.2f * dp, paint);
                canvas.drawCircle(fx, fy - 2.2f * dp, 2.2f * dp, paint); canvas.drawCircle(fx, fy + 2.2f * dp, 2.2f * dp, paint);
                paint.setColor(Color.parseColor("#F59E0B")); canvas.drawCircle(fx, fy, 1.8f * dp, paint);
            }
        }
    }

    private void drawAtmosphericLighting(Canvas canvas) {
        if (isSunnyDay) {
            ambientPaint.setShader(new RadialGradient(screenW * 0.18f, screenH * 0.12f, screenW * 0.55f,
                    new int[]{Color.parseColor("#24FFFFFF"), Color.parseColor("#10FEF08A"), Color.TRANSPARENT}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenW, screenH, ambientPaint); ambientPaint.setShader(null);
        } else if (isMoonlightMode) {
            ambientPaint.setShader(new RadialGradient(screenW * 0.78f, screenH * 0.18f, screenW * 0.65f,
                    new int[]{Color.parseColor("#367DD3FC"), Color.parseColor("#1838BDF8"), Color.parseColor("#22020617")}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenW, screenH, ambientPaint); ambientPaint.setShader(null);
        }
    }

    private void drawSpaceStarsOrFireflies(Canvas canvas, float viewW, float viewH) {
        paint.setStyle(Paint.Style.FILL);
        for (Star s : stars) {
            if (s.x < camX - viewW || s.x > camX + viewW || s.y < camY - viewH || s.y > camY + viewH) continue;
            paint.setColor(s.color); paint.setAlpha(100 + (int) (150 * (0.5f + 0.5f * Math.sin(starPhase + s.phase))));
            canvas.drawCircle(s.x, s.y, s.radius, paint);
        }
        paint.setAlpha(255);
    }

    private void drawTexturedTree(Canvas canvas, Obstacle tree) {
        float x = tree.x, y = tree.y, r = tree.radius;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isMoonlightMode ? Color.parseColor("#55020617") : Color.parseColor("#2E000000"));
        canvas.drawCircle(x + r * 0.16f, y + r * 0.18f, r * 1.02f, paint);

        paint.setColor(isMoonlightMode ? Color.parseColor("#0F4C3A") : Color.parseColor("#1E7E45"));
        strokePaint.setColor(isMoonlightMode ? Color.parseColor("#06281E") : Color.parseColor("#124E2A")); strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawCircle(x, y, r * 0.86f, paint);
        for (int i = 0; i < 8; i++) {
            float lobeX = x + tree.lobeCos[i] * (r * 0.52f), lobeY = y + tree.lobeSin[i] * (r * 0.52f), lobeR = r * tree.lobeScale[i];
            canvas.drawCircle(lobeX, lobeY, lobeR, paint); canvas.drawCircle(lobeX, lobeY, lobeR, strokePaint);
        }
        paint.setColor(isMoonlightMode ? Color.parseColor("#156B52") : Color.parseColor("#28A45A"));
        canvas.drawCircle(x - r * 0.05f, y - r * 0.05f, r * 0.72f, paint);
        for (int i = 0; i < 8; i += 2) canvas.drawCircle(x + tree.lobeCos[i] * (r * 0.36f), y + tree.lobeSin[i] * (r * 0.36f), r * 0.36f, paint);

        paint.setColor(Color.parseColor("#5C3A21")); canvas.drawCircle(x, y, r * 0.16f, paint);
        strokePaint.setColor(Color.parseColor("#4A2E1B")); strokePaint.setStrokeWidth(2.6f * dp);
        for (int i = 0; i < 3; i++) canvas.drawLine(x, y, x + (float) Math.cos(tree.branchAngles[i]) * r * 0.34f, y + (float) Math.sin(tree.branchAngles[i]) * r * 0.34f, strokePaint);

        paint.setColor(isMoonlightMode ? Color.parseColor("#2DD4BF") : Color.parseColor("#4ADE80"));
        canvas.drawCircle(x - r * 0.22f, y - r * 0.22f, r * 0.34f, paint);
        canvas.drawCircle(x + r * 0.18f, y - r * 0.14f, r * 0.24f, paint);
        canvas.drawCircle(x - r * 0.08f, y + r * 0.20f, r * 0.22f, paint);
    }

    private void drawAsteroid(Canvas canvas, Obstacle ast) {
        float r = ast.radius;
        strokePaint.setColor(Color.parseColor("#1838BDF8")); strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawCircle(ast.x, ast.y, r * 1.35f, strokePaint);

        canvas.save(); canvas.translate(ast.x, ast.y); canvas.rotate(ast.rotationDeg);
        tempPath.reset();
        for (int i = 0; i < 10; i++) {
            float vx = ast.astCos[i] * r * ast.astRadScale[i], vy = ast.astSin[i] * r * ast.astRadScale[i];
            if (i == 0) tempPath.moveTo(vx, vy); else tempPath.lineTo(vx, vy);
        }
        tempPath.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#334155")); canvas.drawPath(tempPath, paint);
        strokePaint.setColor(Color.parseColor("#64748B")); strokePaint.setStrokeWidth(2.8f * dp); canvas.drawPath(tempPath, strokePaint);

        paint.setColor(Color.parseColor("#1E293B")); strokePaint.setColor(Color.parseColor("#475569")); strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawCircle(-r * 0.28f, -r * 0.18f, r * 0.24f, paint); canvas.drawCircle(-r * 0.28f, -r * 0.18f, r * 0.24f, strokePaint);
        canvas.drawCircle(r * 0.24f, r * 0.22f, r * 0.18f, paint); canvas.drawCircle(r * 0.24f, r * 0.22f, r * 0.18f, strokePaint);
        canvas.drawCircle(r * 0.12f, -r * 0.32f, r * 0.13f, paint);
        canvas.restore();
    }

    private void drawPickupItem(Canvas canvas, Pickup pk) {
        paint.setStyle(Paint.Style.FILL);
        if (pk.kind == 0 || pk.kind == 1) {
            float r = 14f * dp;
            if (pk.kind == 0) { // Health box
                tempRect.set(pk.x - r, pk.y - r, pk.x + r, pk.y + r);
                paint.setColor(Color.parseColor("#F8FAFC"));
                canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                strokePaint.setColor(Color.parseColor("#0D1B2A"));
                strokePaint.setStrokeWidth(2.5f * dp);
                canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);

                paint.setColor(Color.parseColor("#EF4444"));
                canvas.drawRect(pk.x - 6f * dp, pk.y - 2f * dp, pk.x + 6f * dp, pk.y + 2f * dp, paint);
                canvas.drawRect(pk.x - 2f * dp, pk.y - 6f * dp, pk.x + 2f * dp, pk.y + 6f * dp, paint);
            } else { // Shield shape
                tempPath.reset();
                tempPath.moveTo(pk.x - r, pk.y - r * 0.8f);
                tempPath.lineTo(pk.x + r, pk.y - r * 0.8f);
                tempPath.lineTo(pk.x + r, pk.y + r * 0.1f);
                tempPath.quadTo(pk.x + r, pk.y + r * 0.8f, pk.x, pk.y + r * 1.2f);
                tempPath.quadTo(pk.x - r, pk.y + r * 0.8f, pk.x - r, pk.y + r * 0.1f);
                tempPath.close();

                paint.setColor(Color.parseColor("#29B6F6"));
                canvas.drawPath(tempPath, paint);

                strokePaint.setColor(Color.parseColor("#0D1B2A"));
                strokePaint.setStrokeWidth(2.5f * dp);
                canvas.drawPath(tempPath, strokePaint);

                tempPath.reset();
                tempPath.moveTo(pk.x - r * 0.6f, pk.y - r * 0.5f);
                tempPath.lineTo(pk.x + r * 0.6f, pk.y - r * 0.5f);
                tempPath.lineTo(pk.x + r * 0.6f, pk.y + r * 0.1f);
                tempPath.quadTo(pk.x + r * 0.6f, pk.y + r * 0.5f, pk.x, pk.y + r * 0.8f);
                tempPath.quadTo(pk.x - r * 0.6f, pk.y + r * 0.5f, pk.x - r * 0.6f, pk.y + r * 0.1f);
                tempPath.close();
                paint.setColor(Color.parseColor("#81D4FA"));
                canvas.drawPath(tempPath, paint);
            }
        } else {
            int weaponType = (pk.kind == 2) ? WEAPON_MACHINE_GUN : (pk.kind == 3 ? WEAPON_SHOTGUN : (pk.kind == 4 ? WEAPON_ROCKET
                                                                                                     : (pk.kind == 5 ? WEAPON_FLAMETHROWER : (pk.kind == 6 ? WEAPON_SNIPER : (pk.kind == 7 ? WEAPON_LASER : WEAPON_ELECTRIC)))));
            int auraColor = (weaponType == WEAPON_MACHINE_GUN) ? Color.parseColor("#38BDF8")
                    : (weaponType == WEAPON_SHOTGUN || weaponType == WEAPON_FLAMETHROWER ? Color.parseColor("#FB923C")
                       : (weaponType == WEAPON_LASER ? Color.parseColor("#00F5D4") : (weaponType == WEAPON_ELECTRIC ? Color.parseColor("#A855F7") : Color.parseColor("#EF4444"))));
            drawTesseractWeaponCover(canvas, pk.x, pk.y, auraColor, weaponType);
        }
    }

    private void drawTesseractWeaponCover(Canvas canvas, float x, float y, int auraColor, int weaponType) {
        float pulse = 1.0f + 0.05f * (float) Math.sin(starPhase * 2.4f);
        float hoverOffset = (float) Math.sin(starPhase * 2.4f) * 2.2f * dp;
        float cx = x;
        float cy = y + hoverOffset;

        int rCol = Color.red(auraColor), gCol = Color.green(auraColor), bCol = Color.blue(auraColor);

        // 1. Ground shadow underneath the hovering 3D Tesseract
        float shW = 24f * dp * pulse, shH = 15f * dp * pulse, shY = y + 8.5f * dp;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        tempRect.set(cx - shW, shY - shH, cx + shW, shY + shH);
        canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, paint);

        // Outer cube dimensions (chamfered 3D isometric box) and 3D depth extrusion vector
        float ow = 24f * dp * pulse, oh = 15.5f * dp * pulse, cut = 6.5f * dp * pulse;
        float extX = 4.5f * dp, extY = 6.5f * dp;

        // 2. 3D Extruded Bottom/Right Side Walls (gives solid 3D block depth)
        // Bottom-front beveled wall
        tempPath.reset();
        tempPath.moveTo(cx - ow + cut, cy + oh);
        tempPath.lineTo(cx + ow - cut, cy + oh);
        tempPath.lineTo(cx + ow - cut + extX, cy + oh + extY);
        tempPath.lineTo(cx - ow + cut + extX, cy + oh + extY);
        tempPath.close();
        paint.setColor(Color.argb(210, Math.max(0, rCol / 4), Math.max(0, gCol / 4), Math.max(0, bCol / 4)));
        canvas.drawPath(tempPath, paint);

        // Bottom-right beveled corner wall
        tempPath.reset();
        tempPath.moveTo(cx + ow - cut, cy + oh);
        tempPath.lineTo(cx + ow, cy + oh - cut);
        tempPath.lineTo(cx + ow + extX, cy + oh - cut + extY);
        tempPath.lineTo(cx + ow - cut + extX, cy + oh + extY);
        tempPath.close();
        paint.setColor(Color.argb(225, Math.max(0, rCol / 3), Math.max(0, gCol / 3), Math.max(0, bCol / 3)));
        canvas.drawPath(tempPath, paint);

        // Right vertical side wall
        tempPath.reset();
        tempPath.moveTo(cx + ow, cy - oh + cut);
        tempPath.lineTo(cx + ow, cy + oh - cut);
        tempPath.lineTo(cx + ow + extX, cy + oh - cut + extY);
        tempPath.lineTo(cx + ow + extX, cy - oh + cut + extY);
        tempPath.close();
        paint.setColor(Color.argb(195, Math.max(0, rCol / 5), Math.max(0, gCol / 5), Math.max(0, bCol / 5)));
        canvas.drawPath(tempPath, paint);

        // 3D wireframe edges for the extruded lower hull
        strokePaint.setStrokeWidth(1.5f * dp);
        strokePaint.setColor(Color.argb(135, rCol, gCol, bCol));
        canvas.drawLine(cx - ow + cut, cy + oh, cx - ow + cut + extX, cy + oh + extY, strokePaint);
        canvas.drawLine(cx + ow - cut, cy + oh, cx + ow - cut + extX, cy + oh + extY, strokePaint);
        canvas.drawLine(cx + ow, cy + oh - cut, cx + ow + extX, cy + oh - cut + extY, strokePaint);
        canvas.drawLine(cx + ow, cy - oh + cut, cx + ow + extX, cy - oh + cut + extY, strokePaint);
        canvas.drawLine(cx - ow + cut + extX, cy + oh + extY, cx + ow - cut + extX, cy + oh + extY, strokePaint);
        canvas.drawLine(cx + ow - cut + extX, cy + oh + extY, cx + ow + extX, cy + oh - cut + extY, strokePaint);
        canvas.drawLine(cx + ow + extX, cy + oh - cut + extY, cx + ow + extX, cy - oh + cut + extY, strokePaint);

        // 3. Outer Tesseract Top Face (dark tech core chamber)
        tempPath.reset();
        tempPath.moveTo(cx - ow + cut, cy - oh);
        tempPath.lineTo(cx + ow - cut, cy - oh);
        tempPath.lineTo(cx + ow, cy - oh + cut);
        tempPath.lineTo(cx + ow, cy + oh - cut);
        tempPath.lineTo(cx + ow - cut, cy + oh);
        tempPath.lineTo(cx - ow + cut, cy + oh);
        tempPath.lineTo(cx - ow, cy + oh - cut);
        tempPath.lineTo(cx - ow, cy - oh + cut);
        tempPath.close();

        paint.setColor(Color.parseColor("#CC091326"));
        canvas.drawPath(tempPath, paint);

        // Subtle inner energy tint
        paint.setColor(Color.argb(38, rCol, gCol, bCol));
        canvas.drawPath(tempPath, paint);

        // 4. Inner Tesseract Hypercube Frame & 8 Perspective Struts
        float icx = cx - 1.2f * dp;
        float icy = cy - 1.2f * dp;
        float iw = ow * 0.64f, ih = oh * 0.64f, icut = cut * 0.64f;

        // Connect all 8 outer vertices to the 8 inner tesseract vertices (3D hypercube struts)
        strokePaint.setStrokeWidth(1.3f * dp);
        strokePaint.setColor(Color.argb(150, rCol, gCol, bCol));
        canvas.drawLine(cx - ow + cut, cy - oh, icx - iw + icut, icy - ih, strokePaint);
        canvas.drawLine(cx + ow - cut, cy - oh, icx + iw - icut, icy - ih, strokePaint);
        canvas.drawLine(cx + ow, cy - oh + cut, icx + iw, icy - ih + icut, strokePaint);
        canvas.drawLine(cx + ow, cy + oh - cut, icx + iw, icy + ih - icut, strokePaint);
        canvas.drawLine(cx + ow - cut, cy + oh, icx + iw - icut, icy + ih, strokePaint);
        canvas.drawLine(cx - ow + cut, cy + oh, icx - iw + icut, icy + ih, strokePaint);
        canvas.drawLine(cx - ow, cy + oh - cut, icx - iw, icy + ih - icut, strokePaint);
        canvas.drawLine(cx - ow, cy - oh + cut, icx - iw, icy - ih + icut, strokePaint);

        // Draw Inner Tesseract Octagonal Cube
        tempPath.reset();
        tempPath.moveTo(icx - iw + icut, icy - ih);
        tempPath.lineTo(icx + iw - icut, icy - ih);
        tempPath.lineTo(icx + iw, icy - ih + icut);
        tempPath.lineTo(icx + iw, icy + ih - icut);
        tempPath.lineTo(icx + iw - icut, icy + ih);
        tempPath.lineTo(icx - iw + icut, icy + ih);
        tempPath.lineTo(icx - iw, icy + ih - icut);
        tempPath.lineTo(icx - iw, icy - ih + icut);
        tempPath.close();

        paint.setColor(Color.argb(45, rCol, gCol, bCol));
        canvas.drawPath(tempPath, paint);
        strokePaint.setStrokeWidth(1.4f * dp);
        strokePaint.setColor(Color.argb(190, rCol, gCol, bCol));
        canvas.drawPath(tempPath, strokePaint);

        // 5. Render the Weapon Silhouette inside the 3D Tesseract Chamber
        drawSideProfileWeaponSilhouette(canvas, cx, cy, 0.96f * dp, weaponType);

        // 6. 3D Holographic Glass Canopy Highlight & Glowing Outer Tesseract Rim
        tempPath.reset();
        tempPath.moveTo(cx - ow + cut, cy - oh);
        tempPath.lineTo(cx + ow - cut, cy - oh);
        tempPath.lineTo(cx + ow, cy - oh + cut);
        tempPath.lineTo(cx - ow, cy + oh - cut);
        tempPath.lineTo(cx - ow, cy - oh + cut);
        tempPath.close();
        paint.setColor(Color.parseColor("#1FFFFFFF"));
        canvas.drawPath(tempPath, paint);

        // Outer Tesseract Glowing Border
        tempPath.reset();
        tempPath.moveTo(cx - ow + cut, cy - oh);
        tempPath.lineTo(cx + ow - cut, cy - oh);
        tempPath.lineTo(cx + ow, cy - oh + cut);
        tempPath.lineTo(cx + ow, cy + oh - cut);
        tempPath.lineTo(cx + ow - cut, cy + oh);
        tempPath.lineTo(cx - ow + cut, cy + oh);
        tempPath.lineTo(cx - ow, cy + oh - cut);
        tempPath.lineTo(cx - ow, cy - oh + cut);
        tempPath.close();
        strokePaint.setStrokeWidth(2.4f * dp);
        strokePaint.setColor(auraColor);
        canvas.drawPath(tempPath, strokePaint);

        // Bright energy nodes at the 4 primary corner joints
        paint.setColor(Color.WHITE);
        float nodeR = 1.9f * dp;
        canvas.drawCircle(cx - ow + cut, cy - oh, nodeR, paint);
        canvas.drawCircle(cx + ow - cut, cy - oh, nodeR, paint);
        canvas.drawCircle(cx + ow - cut, cy + oh, nodeR, paint);
        canvas.drawCircle(cx - ow + cut, cy + oh, nodeR, paint);
    }

    private void drawSideProfileWeaponSilhouette(Canvas canvas, float cx, float cy, float s, int weaponType) {
        canvas.save(); canvas.translate(cx, cy);
        strokePaint.setColor(Color.parseColor("#0D1B2A")); strokePaint.setStrokeWidth(1.8f * s);
        paint.setStyle(Paint.Style.FILL);

        if (weaponType == WEAPON_KNIFE) {
            tempRect.set(-19f * s, -3.5f * s, -5f * s, 3.5f * s);
            paint.setColor(Color.parseColor("#78350F")); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, strokePaint);
            paint.setColor(Color.parseColor("#FACC15")); canvas.drawCircle(-19.5f * s, 0f, 3.8f * s, paint);
            tempRect.set(-6f * s, -8f * s, -2.5f * s, 8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempPath.reset(); tempPath.moveTo(-2.5f * s, -5f * s); tempPath.lineTo(13f * s, -5f * s); tempPath.lineTo(22f * s, -1f * s); tempPath.lineTo(14f * s, 5.5f * s); tempPath.lineTo(-2.5f * s, 4.5f * s); tempPath.close();
            paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
        } else if (weaponType == WEAPON_EAGLE) {
            tempPath.reset(); tempPath.moveTo(-10f * s, 0f); tempPath.lineTo(-3f * s, 0f); tempPath.lineTo(-7f * s, 12f * s); tempPath.lineTo(-14f * s, 11f * s); tempPath.close();
            paint.setColor(Color.parseColor("#1E293B")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempPath.reset(); tempPath.moveTo(-12f * s, -7f * s); tempPath.lineTo(15f * s, -7f * s); tempPath.lineTo(13f * s, 1f * s); tempPath.lineTo(-12f * s, 1f * s); tempPath.close();
            paint.setColor(Color.parseColor("#F59E0B")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempRect.set(4f * s, -6f * s, 15f * s, -1f * s); paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRect(tempRect, paint);
        } else if (weaponType == WEAPON_MACHINE_GUN) {
            tempPath.reset(); tempPath.moveTo(-21f * s, -4f * s); tempPath.lineTo(-11f * s, -4f * s); tempPath.lineTo(-11f * s, 2f * s); tempPath.lineTo(-21f * s, 6f * s); tempPath.close();
            paint.setColor(Color.parseColor("#1E293B")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempPath.reset(); tempPath.moveTo(-1f * s, 1f * s); tempPath.lineTo(6f * s, 1f * s); tempPath.lineTo(9f * s, 12f * s); tempPath.lineTo(2f * s, 13f * s); tempPath.close();
            paint.setColor(Color.parseColor("#FACC15")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempRect.set(-12f * s, -6f * s, 10f * s, 2f * s); paint.setColor(Color.parseColor("#0284C7")); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, strokePaint);
            tempRect.set(10f * s, -5f * s, 22f * s, -1f * s); paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
        } else if (weaponType == WEAPON_SHOTGUN) {
            tempPath.reset(); tempPath.moveTo(-22f * s, -2f * s); tempPath.lineTo(-10f * s, -4f * s); tempPath.lineTo(-10f * s, 2f * s); tempPath.lineTo(-21f * s, 7f * s); tempPath.close();
            paint.setColor(Color.parseColor("#9A3412")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempRect.set(-10f * s, -5f * s, 4f * s, 2f * s); paint.setColor(Color.parseColor("#475569")); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempRect.set(4f * s, -5.5f * s, 21f * s, -1.5f * s); paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempRect.set(5f * s, -1.5f * s, 16f * s, 3.5f * s); paint.setColor(Color.parseColor("#EA580C")); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, strokePaint);
        } else if (weaponType == WEAPON_ROCKET) {
            tempRect.set(-16f * s, -3.5f * s, 13f * s, 3.5f * s); paint.setColor(Color.parseColor("#15803D")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempRect.set(-20f * s, -5f * s, -14f * s, 5f * s); paint.setColor(Color.parseColor("#64748B")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempPath.reset(); tempPath.moveTo(13f * s, -4f * s); tempPath.lineTo(22f * s, -1f * s); tempPath.lineTo(22f * s, 1f * s); tempPath.lineTo(13f * s, 4f * s); tempPath.close(); paint.setColor(Color.parseColor("#EF4444")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempRect.set(-2f * s, 3.5f * s, 3f * s, 8f * s); paint.setColor(Color.parseColor("#1E293B")); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, strokePaint);
            tempRect.set(-13f * s, 3.5f * s, -6f * s, 9f * s); paint.setColor(Color.parseColor("#F59E0B")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
        } else if (weaponType == WEAPON_FLAMETHROWER) {
            tempRect.set(-17f * s, -3.5f * s, 16f * s, 3f * s); paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, paint); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, strokePaint);
            tempRect.set(-8f * s, -8f * s, 6f * s, -3f * s); paint.setColor(Color.parseColor("#DC2626")); canvas.drawRoundRect(tempRect, 3.5f * s, 3.5f * s, paint); canvas.drawRoundRect(tempRect, 3.5f * s, 3.5f * s, strokePaint);
            tempRect.set(-17f * s, 3f * s, -10f * s, 9f * s); paint.setColor(Color.parseColor("#1E293B")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempRect.set(13f * s, 2.5f * s, 18f * s, 6f * s); paint.setColor(Color.parseColor("#FB923C")); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, strokePaint);
        } else if (weaponType == WEAPON_SNIPER) {
            tempRect.set(-22f * s, -3f * s, 23f * s, 1.5f * s); paint.setColor(Color.parseColor("#334155")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempRect.set(-22f * s, 1.5f * s, -14f * s, 7f * s); paint.setColor(Color.parseColor("#94A3B8")); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, strokePaint);
            tempRect.set(-7f * s, -8f * s, 5f * s, -4f * s); paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempRect.set(0f, 1.5f * s, 5f * s, 8f * s); paint.setColor(Color.parseColor("#1E293B")); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, strokePaint);
        } else if (weaponType == WEAPON_LASER) {
            tempRect.set(-17f * s, -4f * s, 14f * s, 4f * s); paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, paint); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, strokePaint);
            tempRect.set(-17f * s, 3.5f * s, -10f * s, 9f * s); paint.setColor(Color.parseColor("#94A3B8")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempRect.set(14f * s, -1.5f * s, 23f * s, 1.5f * s); paint.setColor(Color.parseColor("#00F5D4")); canvas.drawRoundRect(tempRect, 0.5f * s, 0.5f * s, paint); canvas.drawRoundRect(tempRect, 0.5f * s, 0.5f * s, strokePaint);
            tempRect.set(-3f * s, -7f * s, 6f * s, -3.5f * s); paint.setColor(Color.parseColor("#3B82F6")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
        } else if (weaponType == WEAPON_ELECTRIC) {
            tempRect.set(-16f * s, -4.5f * s, 10f * s, 4.5f * s); paint.setColor(Color.parseColor("#1E293B")); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, paint); canvas.drawRoundRect(tempRect, 2.5f * s, 2.5f * s, strokePaint);
            tempRect.set(-16f * s, 3.5f * s, -9f * s, 10f * s); paint.setColor(Color.parseColor("#334155")); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, strokePaint);
            tempPath.reset(); tempPath.moveTo(10f * s, -3.5f * s); tempPath.lineTo(17f * s, -3.5f * s); tempPath.lineTo(14f * s, 3.5f * s); tempPath.lineTo(10f * s, 3.5f * s); tempPath.close(); paint.setColor(Color.parseColor("#A855F7")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            paint.setColor(Color.parseColor("#FDE047")); canvas.drawCircle(19f * s, -1.5f * s, 2f * s, paint); canvas.drawCircle(19f * s, -1.5f * s, 2f * s, strokePaint);
            canvas.drawCircle(17f * s, 3f * s, 2f * s, paint); canvas.drawCircle(17f * s, 3f * s, 2f * s, strokePaint);
        }
        canvas.restore();
    }

    private void drawProjectile(Canvas canvas, Bullet b) {
        paint.setStyle(Paint.Style.FILL);
        float angle = (float) Math.atan2(b.dy, b.dx);

        if (b.weaponType == WEAPON_BOMB) {
            float pulse = 1.0f + 0.25f * (float) Math.sin(starPhase * 5.0f + b.life * 0.4f);
            paint.setColor(Color.parseColor("#44FF2A85")); canvas.drawCircle(b.x, b.y, b.radius * 2.2f * pulse, paint);
            paint.setColor(Color.parseColor("#88FF9F1C")); canvas.drawCircle(b.x, b.y, b.radius * 1.45f * pulse, paint);
            paint.setColor(Color.parseColor("#1E293B")); canvas.drawCircle(b.x, b.y, b.radius, paint);
            strokePaint.setColor(Color.parseColor("#FF2A85")); strokePaint.setStrokeWidth(2.2f * dp); canvas.drawCircle(b.x, b.y, b.radius, strokePaint);
            paint.setColor((b.life % 6 < 3) ? Color.parseColor("#FDE047") : Color.parseColor("#FF2A85"));
            canvas.drawCircle(b.x, b.y, b.radius * 0.44f, paint);
            return;
        }

        if (b.weaponType == WEAPON_FLAMETHROWER) {
            paint.setColor(b.life > 14 ? Color.parseColor("#FDE047") : Color.parseColor("#F97316")); paint.setAlpha(Math.min(240, b.life * 9));
            canvas.drawCircle(b.x, b.y, b.radius, paint);
            paint.setColor(Color.parseColor("#EF4444")); paint.setAlpha(Math.min(180, b.life * 6));
            canvas.drawCircle(b.x, b.y, b.radius * 0.6f, paint); paint.setAlpha(255);
            return;
        }
        if (b.weaponType == WEAPON_ROCKET) {
            canvas.save(); canvas.translate(b.x, b.y); canvas.rotate((float) Math.toDegrees(angle));
            paint.setColor(Color.parseColor("#475569")); tempRect.set(-12f * dp, -7f * dp, -6f * dp, 7f * dp); canvas.drawRect(tempRect, paint);
            tempRect.set(-10f * dp, -4f * dp, 6f * dp, 4f * dp); paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
            tempPath.reset(); tempPath.moveTo(5f * dp, -5.5f * dp); tempPath.lineTo(14f * dp, 0f); tempPath.lineTo(5f * dp, 5.5f * dp); tempPath.close();
            paint.setColor(Color.parseColor("#EF4444")); canvas.drawPath(tempPath, paint);
            paint.setColor(Color.parseColor("#FACC15")); canvas.drawCircle(-12f * dp, 0f, 4.2f * dp, paint);
            canvas.restore();
            return;
        }
        canvas.save(); canvas.translate(b.x, b.y); canvas.rotate((float) Math.toDegrees(angle));
        int bulletColor = (b.weaponType == WEAPON_LASER) ? Color.parseColor("#00F5D4")
                : (!b.fromPlayer ? (isSpaceMode ? Color.parseColor("#E879F9") : Color.parseColor("#FB7185"))
                   : (b.weaponType == WEAPON_SNIPER ? Color.parseColor("#EF4444") : (b.weaponType == WEAPON_MACHINE_GUN ? Color.parseColor("#38BDF8")
                                                                                     : (b.weaponType == WEAPON_SHOTGUN ? Color.parseColor("#FB923C") : Color.parseColor("#FDE047")))));
        paint.setColor(bulletColor);
        float lenMult = (b.weaponType == WEAPON_SNIPER || b.weaponType == WEAPON_LASER) ? 2.8f : 1.6f;
        tempRect.set(-b.radius * lenMult, -b.radius * 0.7f, b.radius * lenMult, b.radius * 0.7f);
        canvas.drawRoundRect(tempRect, b.radius, b.radius, paint);
        canvas.restore();
    }

    private static final int[] SPACE_UNIFORM = { 0xFF4C0519, 0xFF3B0764, 0xFF1E293B, 0xFF064E3B, 0xFF27272A, 0xFF7C2D12, 0xFF1E1B4B, 0xFF0F766E, 0xFF311042, 0xFF4A044E };
    private static final int[] SPACE_VEST    = { 0xFF881337, 0xFF581C87, 0xFF334155, 0xFF047857, 0xFF3F3F46, 0xFF9A3412, 0xFF312E81, 0xFF115E59, 0xFF581C87, 0xFF701A75 };
    private static final int[] SPACE_SHLDR   = { 0xFFF43F5E, 0xFFC084FC, 0xFFFB923C, 0xFF34D399, 0xFFEF4444, 0xFFF97316, 0xFF818CF8, 0xFF2DD4BF, 0xFF38BDF8, 0xFFFF2A85 };
    private static final int[] SPACE_HELMET  = { 0xFFBE123C, 0xFF6B21A8, 0xFF475569, 0xFF059669, 0xFF52525B, 0xFFC2410C, 0xFF3730A3, 0xFF0D9488, 0xFF6B21A8, 0xFF86198F };

    private static final int[] EARTH_UNIFORM = { 0xFF31102F, 0xFFA16207, 0xFF334155, 0xFF065F46, 0xFF450A0A, 0xFF7C2D12, 0xFF1E293B, 0xFF0F766E, 0xFF3B0764, 0xFF4A044E };
    private static final int[] EARTH_VEST    = { 0xFF991B1B, 0xFF713F12, 0xFF1E293B, 0xFF064E3B, 0xFF18181B, 0xFF9A3412, 0xFF0F172A, 0xFF134E4A, 0xFF581C87, 0xFF701A75 };
    private static final int[] EARTH_SHLDR   = { 0xFFEF4444, 0xFFEAB308, 0xFFF97316, 0xFF10B981, 0xFF7F1D1D, 0xFFFB923C, 0xFFEF4444, 0xFF00F5D4, 0xFF38BDF8, 0xFFFF2A85 };
    private static final int[] EARTH_HELMET  = { 0xFFDC2626, 0xFF854D0E, 0xFF0F172A, 0xFF047857, 0xFF27272A, 0xFFEA580C, 0xFF334155, 0xFF0D9488, 0xFF6B21A8, 0xFF86198F };

    private void drawHumanCharacter(Canvas canvas, float cx, float cy, float r, float angleRad,
                                    boolean isPlayer, int enemyType, int weaponType,
                                    float walkPhase, float recoilOffset, float slashPhase, boolean jetpackActive) {
        canvas.save(); canvas.translate(cx, cy); canvas.rotate((float) Math.toDegrees(angleRad));
        int outlineColor = Color.parseColor("#0D1B2A");
        strokePaint.setColor(outlineColor); strokePaint.setStrokeWidth(2.4f * dp); paint.setStyle(Paint.Style.FILL);

        int idx = Math.max(0, Math.min(9, enemyType));
        int uniformColor, vestColor, shoulderColor, helmetColor, skinColor = isSpaceMode ? Color.parseColor("#E2E8F0") : Color.parseColor("#F1C27D");
        if (isSpaceMode) {
            if (isPlayer) { uniformColor = 0xFFF8FAFC; vestColor = 0xFFCBD5E1; shoulderColor = 0xFF0284C7; helmetColor = 0xFFFFFFFF; }
            else { uniformColor = SPACE_UNIFORM[idx]; vestColor = SPACE_VEST[idx]; shoulderColor = SPACE_SHLDR[idx]; helmetColor = SPACE_HELMET[idx]; }
        } else {
            if (isPlayer) { uniformColor = 0xFF1D4ED8; vestColor = 0xFF1E3A8A; shoulderColor = 0xFFFBBF24; helmetColor = 0xFF2563EB; }
            else { uniformColor = EARTH_UNIFORM[idx]; vestColor = EARTH_VEST[idx]; shoulderColor = EARTH_SHLDR[idx]; helmetColor = EARTH_HELMET[idx]; }
        }

        float stride = (float) Math.sin(walkPhase) * (r * 0.34f);
        paint.setColor(isSpaceMode ? Color.parseColor("#94A3B8") : Color.parseColor("#1E293B"));
        tempRect.set(stride - r * 0.25f, -r * 0.72f, stride + r * 0.32f, -r * 0.36f); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
        tempRect.set(-stride - r * 0.25f, r * 0.36f, -stride + r * 0.32f, r * 0.72f); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);

        if (isSpaceMode) {
            paint.setColor(Color.parseColor("#64748B"));
            tempRect.set(-r * 1.18f, -r * 0.78f, -r * 0.32f, -r * 0.14f); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, paint); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, strokePaint);
            tempRect.set(-r * 1.18f, r * 0.14f, -r * 0.32f, r * 0.78f); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, paint); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, strokePaint);
            paint.setColor(isPlayer ? Color.parseColor("#0284C7") : Color.parseColor("#E11D48"));
            tempRect.set(-r * 1.02f, -r * 0.32f, -r * 0.42f, r * 0.32f); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            if (jetpackActive) {
                float flameLen = r * (1.55f + (float) Math.sin(starPhase * 6f) * 0.28f);
                paint.setColor(isPlayer ? Color.parseColor("#38BDF8") : Color.parseColor("#FB923C"));
                tempPath.reset(); tempPath.moveTo(-r * 1.18f, -r * 0.65f); tempPath.lineTo(-flameLen, -r * 0.46f); tempPath.lineTo(-r * 1.18f, -r * 0.27f); tempPath.close(); canvas.drawPath(tempPath, paint);
                tempPath.reset(); tempPath.moveTo(-r * 1.18f, r * 0.27f); tempPath.lineTo(-flameLen, r * 0.46f); tempPath.lineTo(-r * 1.18f, r * 0.65f); tempPath.close(); canvas.drawPath(tempPath, paint);
            }
        } else {
            tempPath.reset(); tempPath.moveTo(-r * 1.12f, -r * 0.82f); tempPath.lineTo(-r * 0.28f, -r * 0.62f); tempPath.lineTo(-r * 0.28f, r * 0.62f); tempPath.lineTo(-r * 1.12f, r * 0.82f); tempPath.close();
            paint.setColor(isPlayer ? Color.parseColor("#F59E0B") : Color.parseColor("#451A03")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempRect.set(-r * 0.95f, -r * 0.55f, -r * 0.50f, r * 0.55f); paint.setColor(isPlayer ? Color.parseColor("#D97706") : Color.parseColor("#292524")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
        }

        tempRect.set(-r * 0.42f, -r * 0.95f, r * 0.42f, r * 0.95f); paint.setColor(uniformColor); canvas.drawRoundRect(tempRect, r * 0.38f, r * 0.38f, paint); canvas.drawRoundRect(tempRect, r * 0.38f, r * 0.38f, strokePaint);
        tempRect.set(-r * 0.28f, -r * 0.72f, r * 0.36f, r * 0.72f); paint.setColor(vestColor); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, paint);

        if (weaponType == WEAPON_KNIFE) {
            float swingDeg = (float) Math.sin(slashPhase * Math.PI) * -65f;
            paint.setColor(uniformColor); tempRect.set(0f, -r * 0.90f, r * 0.72f, -r * 0.52f); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, paint); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, strokePaint);
            paint.setColor(skinColor); canvas.drawCircle(r * 0.74f, -r * 0.70f, r * 0.17f, paint); canvas.drawCircle(r * 0.74f, -r * 0.70f, r * 0.17f, strokePaint);

            canvas.save(); canvas.translate(r * 0.18f, r * 0.62f); canvas.rotate(swingDeg);
            paint.setColor(uniformColor); tempRect.set(-r * 0.1f, -r * 0.20f, r * 0.75f, r * 0.20f); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, paint); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, strokePaint);
            paint.setColor(skinColor); canvas.drawCircle(r * 0.75f, 0f, r * 0.19f, paint); canvas.drawCircle(r * 0.75f, 0f, r * 0.19f, strokePaint);
            paint.setColor(Color.parseColor("#FACC15")); tempRect.set(r * 0.85f, -r * 0.30f, r * 0.98f, r * 0.30f); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempPath.reset(); tempPath.moveTo(r * 0.98f, -r * 0.18f); tempPath.lineTo(r * 2.05f, -r * 0.04f); tempPath.lineTo(r * 1.72f, r * 0.22f); tempPath.lineTo(r * 0.98f, r * 0.18f); tempPath.close();
            paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            canvas.restore();
        } else {
            float gunY = r * 0.36f, gunX = -recoilOffset;
            paint.setColor(uniformColor);
            canvas.save(); canvas.translate(r * 0.18f, -r * 0.70f); canvas.rotate(32f);
            tempRect.set(0f, -r * 0.19f, r * 0.95f, r * 0.19f); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, paint); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, strokePaint);
            paint.setColor(skinColor); canvas.drawCircle(r * 0.92f, 0f, r * 0.17f, paint); canvas.drawCircle(r * 0.92f, 0f, r * 0.17f, strokePaint);
            canvas.restore();

            paint.setColor(uniformColor); tempRect.set(0f, r * 0.42f, r * 0.82f, r * 0.78f); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, paint); canvas.drawRoundRect(tempRect, r * 0.18f, r * 0.18f, strokePaint);
            paint.setColor(skinColor); canvas.drawCircle(r * 0.75f + gunX * 0.4f, gunY + r * 0.12f, r * 0.17f, paint); canvas.drawCircle(r * 0.75f + gunX * 0.4f, gunY + r * 0.12f, r * 0.17f, strokePaint);
            drawTopDownGun(canvas, gunX, gunY, r, isPlayer, weaponType);
        }

        paint.setColor(shoulderColor);
        tempRect.set(-r * 0.34f, -r * 1.04f, r * 0.28f, -r * 0.60f); canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, paint); canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, strokePaint);
        tempRect.set(-r * 0.34f, r * 0.60f, r * 0.28f, r * 1.04f); canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, paint); canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, strokePaint);

        if (isSpaceMode) {
            paint.setColor(Color.parseColor("#64748B")); canvas.drawCircle(0f, 0f, r * 0.76f, paint); canvas.drawCircle(0f, 0f, r * 0.76f, strokePaint);
            paint.setColor(helmetColor); canvas.drawCircle(0f, 0f, r * 0.69f, paint); canvas.drawCircle(0f, 0f, r * 0.69f, strokePaint);
            paint.setColor(isPlayer ? Color.parseColor("#38BDF8") : Color.parseColor("#F59E0B"));
            tempRect.set(r * 0.05f, -r * 0.50f, r * 0.64f, r * 0.50f); canvas.drawRoundRect(tempRect, r * 0.35f, r * 0.35f, paint); canvas.drawRoundRect(tempRect, r * 0.35f, r * 0.35f, strokePaint);
            paint.setColor(Color.WHITE); canvas.drawCircle(r * 0.42f, -r * 0.22f, r * 0.11f, paint);
        } else {
            paint.setColor(skinColor); canvas.drawCircle(0f, -r * 0.68f, r * 0.14f, paint); canvas.drawCircle(0f, r * 0.68f, r * 0.14f, paint);
            paint.setColor(helmetColor); canvas.drawCircle(0f, 0f, r * 0.66f, paint); canvas.drawCircle(0f, 0f, r * 0.66f, strokePaint);
            if (isPlayer) {
                paint.setColor(Color.parseColor("#1E3A8A")); tempRect.set(-r * 0.45f, -r * 0.48f, r * 0.25f, r * 0.48f); canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, paint);
                paint.setColor(Color.parseColor("#38BDF8"));
                canvas.drawCircle(r * 0.48f, -r * 0.22f, r * 0.16f, paint); canvas.drawCircle(r * 0.48f, r * 0.22f, r * 0.16f, paint);
                canvas.drawCircle(r * 0.48f, -r * 0.22f, r * 0.16f, strokePaint); canvas.drawCircle(r * 0.48f, r * 0.22f, r * 0.16f, strokePaint);
            } else if (enemyType == ENEMY_KNIFE_RUSHER) {
                paint.setColor(Color.parseColor("#EF4444"));
                tempPath.reset(); tempPath.moveTo(-r * 0.55f, -r * 0.2f); tempPath.lineTo(-r * 1.25f, -r * 0.55f); tempPath.lineTo(-r * 1.05f, -r * 0.1f); tempPath.close(); canvas.drawPath(tempPath, paint);
                paint.setColor(Color.parseColor("#111827")); tempRect.set(r * 0.22f, -r * 0.38f, r * 0.56f, r * 0.38f); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
            } else {
                paint.setColor(Color.parseColor("#0F172A")); tempRect.set(r * 0.24f, -r * 0.40f, r * 0.55f, r * 0.40f); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
            }
        }
        canvas.restore();
    }

    private void drawTopDownGun(Canvas canvas, float gx, float gy, float r, boolean isPlayer, int weaponType) {
        strokePaint.setColor(Color.parseColor("#0D1B2A")); strokePaint.setStrokeWidth(2.2f * dp); paint.setStyle(Paint.Style.FILL);
        if (weaponType == WEAPON_EAGLE) {
            tempRect.set(gx, gy - r * 0.16f, gx + r * 0.90f, gy + r * 0.16f); paint.setColor(isPlayer ? Color.parseColor("#F59E0B") : Color.parseColor("#475569"));
            canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
            tempPath.reset(); tempPath.moveTo(gx + r * 0.65f, gy - r * 0.18f); tempPath.lineTo(gx + r * 1.72f, gy - r * 0.13f); tempPath.lineTo(gx + r * 1.72f, gy + r * 0.13f); tempPath.lineTo(gx + r * 0.65f, gy + r * 0.18f); tempPath.close();
            paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
        } else if (weaponType == WEAPON_MACHINE_GUN) {
            tempRect.set(gx + r * 0.35f, gy - r * 0.56f, gx + r * 0.95f, gy - r * 0.15f); paint.setColor(Color.parseColor("#0284C7")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            tempRect.set(gx + r * 0.10f, gy - r * 0.21f, gx + r * 1.45f, gy + r * 0.21f); paint.setColor(Color.parseColor("#334155")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            paint.setColor(Color.parseColor("#38BDF8"));
            tempRect.set(gx + r * 1.40f, gy - r * 0.16f, gx + r * 2.30f, gy - r * 0.02f); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempRect.set(gx + r * 1.40f, gy + r * 0.02f, gx + r * 2.30f, gy + r * 0.16f); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
        } else if (weaponType == WEAPON_SHOTGUN) {
            tempRect.set(gx + r * 0.15f, gy - r * 0.18f, gx + r * 0.90f, gy + r * 0.18f); paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
            tempRect.set(gx + r * 0.85f, gy - r * 0.20f, gx + r * 2.08f, gy + r * 0.20f); paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, strokePaint);
            tempRect.set(gx + r * 0.98f, gy - r * 0.26f, gx + r * 1.62f, gy + r * 0.26f); paint.setColor(Color.parseColor("#EA580C")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
        } else if (weaponType == WEAPON_ROCKET) {
            tempRect.set(gx - r * 0.98f, gy - r * 0.24f, gx + r * 1.68f, gy + r * 0.24f); paint.setColor(Color.parseColor("#15803D")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            tempPath.reset(); tempPath.moveTo(gx - r * 1.48f, gy - r * 0.38f); tempPath.lineTo(gx - r * 0.95f, gy - r * 0.22f); tempPath.lineTo(gx - r * 0.95f, gy + r * 0.22f); tempPath.lineTo(gx - r * 1.48f, gy + r * 0.38f); tempPath.close();
            paint.setColor(Color.parseColor("#64748B")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
            tempPath.reset(); tempPath.moveTo(gx + r * 1.65f, gy - r * 0.20f); tempPath.lineTo(gx + r * 1.98f, gy - r * 0.42f); tempPath.lineTo(gx + r * 2.48f, gy); tempPath.lineTo(gx + r * 1.98f, gy + r * 0.42f); tempPath.lineTo(gx + r * 1.65f, gy + r * 0.20f); tempPath.close();
            paint.setColor(Color.parseColor("#EF4444")); canvas.drawPath(tempPath, paint); canvas.drawPath(tempPath, strokePaint);
        } else if (weaponType == WEAPON_FLAMETHROWER) {
            tempRect.set(gx, gy - r * 0.22f, gx + r * 1.75f, gy + r * 0.22f); paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            tempRect.set(gx + r * 0.25f, gy + r * 0.14f, gx + r * 1.15f, gy + r * 0.56f); paint.setColor(Color.parseColor("#DC2626")); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, paint); canvas.drawRoundRect(tempRect, 5f * dp, 5f * dp, strokePaint);
            tempRect.set(gx + r * 1.70f, gy - r * 0.18f, gx + r * 2.22f, gy + r * 0.18f); paint.setColor(Color.parseColor("#FB923C")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
        } else if (weaponType == WEAPON_SNIPER) {
            tempRect.set(gx, gy - r * 0.20f, gx + r * 1.25f, gy + r * 0.20f); paint.setColor(Color.parseColor("#047857")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
            tempRect.set(gx + r * 1.20f, gy - r * 0.09f, gx + r * 2.85f, gy + r * 0.09f); paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempRect.set(gx + r * 2.75f, gy - r * 0.18f, gx + r * 3.05f, gy + r * 0.18f); paint.setColor(Color.parseColor("#1E293B")); canvas.drawRect(tempRect, paint); canvas.drawRect(tempRect, strokePaint);
            tempRect.set(gx + r * 0.25f, gy - r * 0.42f, gx + r * 1.35f, gy - r * 0.12f); paint.setColor(Color.parseColor("#0F172A")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, strokePaint);
        } else if (weaponType == WEAPON_LASER) {
            tempRect.set(gx, gy - r * 0.24f, gx + r * 1.85f, gy + r * 0.24f); paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            tempRect.set(gx + r * 0.30f, gy - r * 0.10f, gx + r * 2.25f, gy + r * 0.10f); paint.setColor(Color.parseColor("#00F5D4")); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
        } else if (weaponType == WEAPON_ELECTRIC) {
            tempRect.set(gx, gy - r * 0.24f, gx + r * 1.65f, gy + r * 0.24f); paint.setColor(Color.parseColor("#581C87")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
            paint.setColor(Color.parseColor("#38BDF8")); canvas.drawCircle(gx + r * 1.95f, gy, r * 0.26f, paint); canvas.drawCircle(gx + r * 1.95f, gy, r * 0.26f, strokePaint);
        } else if (weaponType == WEAPON_BOMB) {
            float bx = gx + r * 1.15f;
            paint.setColor(Color.parseColor("#66FF2A85")); canvas.drawCircle(bx, gy, r * 0.55f, paint);
            paint.setColor(Color.parseColor("#1E293B")); canvas.drawCircle(bx, gy, r * 0.36f, paint); canvas.drawCircle(bx, gy, r * 0.36f, strokePaint);
            paint.setColor(Color.parseColor("#FDE047")); canvas.drawCircle(bx, gy, r * 0.16f, paint);
        }
    }

    private void drawLeftMinimap(Canvas canvas) {
        float mapR = 54f * dp, margin = 16f * dp, mapCX = margin + safeInsetLeft + mapR, mapCY = margin + mapR;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#CC0F172A")); canvas.drawCircle(mapCX, mapCY, mapR, paint);
        strokePaint.setColor(Color.parseColor("#3394A3B8")); strokePaint.setStrokeWidth(1.2f * dp);
        canvas.drawCircle(mapCX, mapCY, mapR * 0.5f, strokePaint);
        canvas.drawLine(mapCX - mapR, mapCY, mapCX + mapR, mapCY, strokePaint); canvas.drawLine(mapCX, mapCY - mapR, mapCX, mapCX + mapR, strokePaint);

        strokePaint.setColor(isSpaceMode ? Color.parseColor("#38BDF8") : Color.parseColor("#4CD964")); strokePaint.setStrokeWidth(2.5f * dp);
        canvas.drawCircle(mapCX, mapCY, mapR, strokePaint);

        float scale = (mapR - 5f * dp) / worldRadius;
        paint.setStyle(Paint.Style.FILL); paint.setColor(isSpaceMode ? Color.parseColor("#94A3B8") : Color.parseColor("#25B560"));
        for (Obstacle ob : obstacles) canvas.drawCircle(mapCX + ob.x * scale, mapCY + ob.y * scale, 2.2f * dp, paint);

        paint.setColor(Color.parseColor("#FACC15"));
        for (Pickup pk : pickups) if (pk.kind >= 2) canvas.drawCircle(mapCX + pk.x * scale, mapCY + pk.y * scale, 2.6f * dp, paint);

        for (Enemy e : enemies) {
            paint.setColor(e.type == ENEMY_KNIFE_RUSHER ? Color.parseColor("#FB923C") : (e.type == ENEMY_BOMBER ? Color.parseColor("#FF2A85") : Color.parseColor("#EF4444")));
            canvas.drawCircle(mapCX + e.x * scale, mapCY + e.y * scale, 3.0f * dp, paint);
        }
        float px = mapCX + playerX * scale, py = mapCY + playerY * scale;
        strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setStrokeWidth(2f * dp);
        canvas.drawLine(px, py, px + (float) Math.cos(playerAngle) * 8f * dp, py + (float) Math.sin(playerAngle) * 8f * dp, strokePaint);
        paint.setColor(Color.WHITE); canvas.drawCircle(px, py, 3.8f * dp, paint);
    }

    private void drawRightSmartPillWeaponHud(Canvas canvas) {
        float rightEdge = screenW - 16f * dp - safeInsetRight, topY = 12f * dp;
        String ammoStr = (currentAmmo < 0) ? "∞" : String.valueOf(currentAmmo);
        String titleStr = getWeaponShortName(currentWeapon) + " • " + ammoStr, subStr = getWeaponPowerDetails(currentWeapon);

        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(14.5f * dp); subTextPaint.setTextSize(10.8f * dp);
        float textWidth = Math.max(textPaint.measureText(titleStr), subTextPaint.measureText(subStr));
        float padStart = 6f * dp, boxSize = 38f * dp, textMarginStart = 10f * dp, padEnd = 22f * dp, pillH = 50f * dp;
        float pillW = padStart + boxSize + textMarginStart + textWidth + padEnd, leftX = rightEdge - pillW;

        rightWeaponCardRect.set(leftX, topY, rightEdge, topY + pillH);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#24000000"));
        tempRect.set(leftX, topY + 2.5f * dp, rightEdge, topY + pillH + 2.5f * dp); canvas.drawRoundRect(tempRect, pillH / 2f, pillH / 2f, paint);
        paint.setColor(hudPillBgColor); canvas.drawRoundRect(rightWeaponCardRect, pillH / 2f, pillH / 2f, paint);

        float boxLeft = leftX + padStart, boxTop = topY + 6f * dp;
        tempRect.set(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize); paint.setColor(hudBoxBgColor); canvas.drawRoundRect(tempRect, 30f, 30f, paint);
        drawSmartPillWeaponIcon(canvas, boxLeft + boxSize / 2f, boxTop + boxSize / 2f, 0.72f * dp, currentWeapon, hudIconColor);

        float textX = boxLeft + boxSize + textMarginStart;
        textPaint.setColor(hudTitleColor); canvas.drawText(titleStr, textX, topY + 23f * dp, textPaint);
        subTextPaint.setColor(hudSubTextColor); canvas.drawText(subStr, textX, topY + 39f * dp, subTextPaint);

        float knifePillTop = topY + pillH + 8f * dp, knifeBoxSize = 32f * dp, knifePillH = 44f * dp;
        boolean knifeActive = (currentWeapon == WEAPON_KNIFE);
        String kTitle = knifeActive ? "Knife Active • ∞" : "Quick Knife • ∞", kSub = knifeActive ? "Fastest • Tap for Gun" : "Kills Close Assassin";

        textPaint.setTextSize(13.0f * dp); subTextPaint.setTextSize(10.0f * dp);
        float kTextWidth = Math.max(textPaint.measureText(kTitle), subTextPaint.measureText(kSub));
        float knifePillW = padStart + knifeBoxSize + textMarginStart + kTextWidth + 20f * dp, kLeftX = rightEdge - knifePillW;

        rightKnifeBtnRect.set(kLeftX, knifePillTop, rightEdge, knifePillTop + knifePillH);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#20000000"));
        tempRect.set(kLeftX, knifePillTop + 2f * dp, rightEdge, knifePillTop + knifePillH + 2f * dp); canvas.drawRoundRect(tempRect, knifePillH / 2f, knifePillH / 2f, paint);
        paint.setColor(hudPillBgColor); canvas.drawRoundRect(rightKnifeBtnRect, knifePillH / 2f, knifePillH / 2f, paint);

        float kBoxLeft = kLeftX + padStart, kBoxTop = knifePillTop + 6f * dp;
        tempRect.set(kBoxLeft, kBoxTop, kBoxLeft + knifeBoxSize, kBoxTop + knifeBoxSize);
        paint.setColor(knifeActive ? Color.parseColor("#34C759") : hudBoxBgColor); canvas.drawRoundRect(tempRect, 26f, 26f, paint);
        drawSmartPillWeaponIcon(canvas, kBoxLeft + knifeBoxSize / 2f, kBoxTop + knifeBoxSize / 2f, 0.62f * dp, WEAPON_KNIFE, knifeActive ? Color.WHITE : hudIconColor);

        float kTextX = kBoxLeft + knifeBoxSize + textMarginStart;
        textPaint.setColor(hudTitleColor); canvas.drawText(kTitle, kTextX, knifePillTop + 20.5f * dp, textPaint);
        subTextPaint.setColor(knifeActive ? Color.parseColor("#34C759") : hudSubTextColor); canvas.drawText(kSub, kTextX, knifePillTop + 34.5f * dp, subTextPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        if (System.currentTimeMillis() < streakBannerUntil && !streakBannerText.isEmpty()) {
            long now = System.currentTimeMillis();
            float timeOffset = (now % 1500L) / 1500f;
            float bannerX = screenW / 2f;
            float bannerY = 82f * dp;

            Typeface oldTf = textPaint.getTypeface();
            textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD_ITALIC));
            textPaint.setTextSize(22f * dp);

            float textW = textPaint.measureText(streakBannerText);
            float startX = bannerX - textW / 2f;

            int[] colors = {Color.parseColor("#A855F7"), Color.parseColor("#34D399"), Color.parseColor("#FDE047"), Color.parseColor("#A855F7")};
            LinearGradient grad = new LinearGradient(0, 0, textW, 0, colors, null, Shader.TileMode.REPEAT);

            Matrix matrix = new Matrix();
            matrix.setTranslate(startX - (timeOffset * textW), 0);
            grad.setLocalMatrix(matrix);

            textPaint.setStyle(Paint.Style.STROKE);
            textPaint.setStrokeJoin(Paint.Join.ROUND);
            textPaint.setStrokeWidth(4.5f * dp);
            textPaint.setColor(Color.parseColor("#D97706"));
            canvas.drawText(streakBannerText, bannerX, bannerY, textPaint);

            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setShader(grad);
            canvas.drawText(streakBannerText, bannerX, bannerY, textPaint);

            textPaint.setShader(null);
            textPaint.setTypeface(oldTf);
            textPaint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawSmartPillWeaponIcon(Canvas canvas, float cx, float cy, float s, int weaponType, int iconColor) {
        canvas.save(); canvas.translate(cx, cy); paint.setStyle(Paint.Style.FILL); paint.setColor(iconColor);
        if (weaponType == WEAPON_KNIFE) {
            canvas.rotate(-28f);
            tempRect.set(-16f * s, -2.8f * s, -5f * s, 2.8f * s); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint);
            tempRect.set(-5.5f * s, -7f * s, -2.5f * s, 7f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempPath.reset(); tempPath.moveTo(-2f * s, -4.2f * s); tempPath.lineTo(11f * s, -4.2f * s); tempPath.lineTo(18f * s, -0.5f * s); tempPath.lineTo(11f * s, 4.5f * s); tempPath.lineTo(-2f * s, 3.8f * s); tempPath.close();
            canvas.drawPath(tempPath, paint);
        } else if (weaponType == WEAPON_EAGLE) {
            tempPath.reset(); tempPath.moveTo(-9f * s, -1f * s); tempPath.lineTo(-2f * s, -1f * s); tempPath.lineTo(-6f * s, 10f * s); tempPath.lineTo(-13f * s, 9f * s); tempPath.close(); canvas.drawPath(tempPath, paint);
            tempPath.reset(); tempPath.moveTo(-11f * s, -7f * s); tempPath.lineTo(14f * s, -7f * s); tempPath.lineTo(12f * s, 0.5f * s); tempPath.lineTo(-11f * s, 0.5f * s); tempPath.close(); canvas.drawPath(tempPath, paint);
        } else if (weaponType == WEAPON_MACHINE_GUN) {
            tempRect.set(-16f * s, -5f * s, 10f * s, 1.5f * s); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint);
            tempRect.set(-1f * s, 1.5f * s, 5f * s, 10f * s); canvas.drawRect(tempRect, paint);
            tempRect.set(10f * s, -4f * s, 19f * s, -0.5f * s); canvas.drawRect(tempRect, paint);
        } else if (weaponType == WEAPON_SHOTGUN) {
            tempRect.set(-18f * s, -4.5f * s, -2f * s, -0.5f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempRect.set(-2f * s, -4.5f * s, 18f * s, -0.5f * s); canvas.drawRect(tempRect, paint);
            tempRect.set(2f * s, -0.5f * s, 13f * s, 3.8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempRect.set(-11f * s, -0.5f * s, -6f * s, 8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
        } else if (weaponType == WEAPON_ROCKET) {
            tempRect.set(-14f * s, -3f * s, 11f * s, 3f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
            tempRect.set(-17f * s, -4.5f * s, -12f * s, 4.5f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempPath.reset(); tempPath.moveTo(11f * s, -3.5f * s); tempPath.lineTo(19f * s, -1f * s); tempPath.lineTo(19f * s, 1f * s); tempPath.lineTo(11f * s, 3.5f * s); tempPath.close(); canvas.drawPath(tempPath, paint);
            tempRect.set(-2f * s, 3f * s, 2f * s, 7f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
            tempRect.set(-11f * s, 3f * s, -5f * s, 8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
        } else if (weaponType == WEAPON_FLAMETHROWER) {
            tempRect.set(-15f * s, -3f * s, 14f * s, 2.5f * s); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint);
            tempRect.set(-7f * s, -7f * s, 5f * s, -2.5f * s); canvas.drawRoundRect(tempRect, 3f * s, 3f * s, paint);
            tempRect.set(-15f * s, 2.5f * s, -9f * s, 8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempRect.set(12f * s, 2f * s, 16f * s, 5f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
        } else if (weaponType == WEAPON_SNIPER) {
            tempRect.set(-19f * s, -2.5f * s, 20f * s, 1f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
            tempRect.set(-19f * s, 1f * s, -12f * s, 6f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
            tempRect.set(-6f * s, -7f * s, 4f * s, -3.5f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempRect.set(0f * s, 1f * s, 4f * s, 7f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
        } else if (weaponType == WEAPON_LASER) {
            tempRect.set(-15f * s, -3.5f * s, 12f * s, 3.5f * s); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint);
            tempRect.set(-15f * s, 3f * s, -9f * s, 8f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempRect.set(12f * s, -1f * s, 20f * s, 1f * s); canvas.drawRoundRect(tempRect, 0.5f * s, 0.5f * s, paint);
            tempRect.set(-3f * s, -6f * s, 5f * s, -3f * s); canvas.drawRoundRect(tempRect, 1f * s, 1f * s, paint);
        } else if (weaponType == WEAPON_ELECTRIC) {
            tempRect.set(-14f * s, -4f * s, 8f * s, 4f * s); canvas.drawRoundRect(tempRect, 2f * s, 2f * s, paint);
            tempRect.set(-14f * s, 3f * s, -8f * s, 9f * s); canvas.drawRoundRect(tempRect, 1.5f * s, 1.5f * s, paint);
            tempPath.reset(); tempPath.moveTo(8f * s, -3f * s); tempPath.lineTo(15f * s, -3f * s); tempPath.lineTo(12f * s, 3f * s); tempPath.lineTo(8f * s, 3f * s); tempPath.close(); canvas.drawPath(tempPath, paint);
            canvas.drawCircle(17f * s, -1.5f * s, 1.5f * s, paint);
            canvas.drawCircle(15f * s, 2.5f * s, 1.5f * s, paint);
        }
        canvas.restore();
    }

    private void drawJoysticks(Canvas canvas) {
        float baseR = 54f * dp, knobR = 24f * dp;
        int ringColor = isSunnyDay ? Color.parseColor("#33000000") : Color.parseColor("#33FFFFFF");
        int knobColor = isSunnyDay ? Color.parseColor("#66007AFF") : Color.parseColor("#6638BDF8");
        if (movePointerId != -1) {
            strokePaint.setColor(ringColor); strokePaint.setStrokeWidth(3f * dp); canvas.drawCircle(moveOriginX, moveOriginY, baseR, strokePaint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(knobColor); canvas.drawCircle(moveCurX, moveCurY, knobR, paint);
        }
        if (aimPointerId != -1) {
            strokePaint.setColor(ringColor); strokePaint.setStrokeWidth(3f * dp); canvas.drawCircle(aimOriginX, aimOriginY, baseR, strokePaint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#77F59E0B")); canvas.drawCircle(aimCurX, aimCurY, knobR, paint);
        }
    }

    private static class MeadowGlade {
        float x, y, radius; boolean isStone, lighterTone;
        MeadowGlade(float x, float y, float r, boolean s, boolean l) { this.x = x; this.y = y; this.radius = r; this.isStone = s; this.lighterTone = l; }
    }
    private static class GrassPatch {
        float x, y, size; boolean hasFlower; int flowerColor;
        GrassPatch(float x, float y, float s, boolean f, int c) { this.x = x; this.y = y; this.size = s; this.hasFlower = f; this.flowerColor = c; }
    }
    private static class Obstacle {
        float x, y, radius, rotationDeg = 0f, rotSpeed;
        final float[] lobeCos = new float[8], lobeSin = new float[8], lobeScale = new float[8], branchAngles = new float[3];
        final float[] astCos = new float[10], astSin = new float[10], astRadScale = new float[10];
        Obstacle(float x, float y, float radius) {
            this.x = x; this.y = y; this.radius = radius; this.rotSpeed = (float) ((Math.random() - 0.5) * 0.7f);
            for (int i = 0; i < 8; i++) { double a = i * (Math.PI * 2 / 8.0); lobeCos[i] = (float) Math.cos(a); lobeSin[i] = (float) Math.sin(a); lobeScale[i] = 0.42f + (float) (Math.random() * 0.12f); }
            for (int i = 0; i < 3; i++) branchAngles[i] = (float) (i * (Math.PI * 2 / 3.0) + Math.random() * 0.5);
            for (int i = 0; i < 10; i++) { double a = i * (Math.PI * 2 / 10.0); astCos[i] = (float) Math.cos(a); astSin[i] = (float) Math.sin(a); astRadScale[i] = 0.72f + (float) (Math.random() * 0.34f); }
        }
    }
    private static class Star {
        float x, y, radius, phase; int color;
        Star(float x, float y, float r, int c, float p) { this.x = x; this.y = y; this.radius = r; this.color = c; this.phase = p; }
    }
    private static class Bullet {
        float x, y, dx, dy, radius, damage; boolean fromPlayer; int weaponType, life; String sourceCause;
        Bullet(float x, float y, float dx, float dy, float r, float dmg, boolean fp, int wt, int l, String cause) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.radius = r; this.damage = dmg; this.fromPlayer = fp; this.weaponType = wt; this.life = l; this.sourceCause = cause;
        }
    }
    private static class LaserBeam {
        float x1, y1, x2, y2; int color, life = 11, maxLife = 11;
        LaserBeam(float x1, float y1, float x2, float y2, int c) { this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.color = c; }
    }
    private static class LightningBolt {
        final Path boltPath = new Path(); int life = 10, maxLife = 10;
        LightningBolt(float x1, float y1, float x2, float y2, float dp) {
            boltPath.moveTo(x1, y1); int segments = 6; float dx = (x2 - x1) / segments, dy = (y2 - y1) / segments;
            for (int i = 1; i < segments; i++) boltPath.lineTo(x1 + dx * i + (float) ((Math.random() - 0.5) * 22f * dp), y1 + dy * i + (float) ((Math.random() - 0.5) * 22f * dp));
            boltPath.lineTo(x2, y2);
        }
    }
    private static class Enemy {
        float x, y, radius, hp, maxHp, speed, angle; int type, burstCount = 0;
        long lastShot = 0, lastBlockTextTime = 0;
        float walkPhase = (float) (Math.random() * Math.PI * 2), recoilOffset = 0f, slashPhase = 0f, strafeDir = Math.random() < 0.5 ? 1f : -1f;
        Enemy(float x, float y, float r, float hp, float spd, int t) { this.x = x; this.y = y; this.radius = r; this.hp = hp; this.maxHp = hp; this.speed = spd; this.type = t; }
    }
    private static class Pickup {
        float x, y; int kind, life = 950;
        Pickup(float x, float y, int k) { this.x = x; this.y = y; this.kind = k; }
    }
    private static class ShellCasing {
        float x, y, dx, dy, rot, rotSpd; int color, life = 35;
        ShellCasing(float x, float y, float dx, float dy, int c) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.color = c;
            this.rot = (float) (Math.random() * 360f); this.rotSpd = (float) ((Math.random() - 0.5) * 24f);
        }
    }
    private static class FloatingText {
        float x, y; String text; int color, life = 28;
        FloatingText(float x, float y, String t, int c) { this.x = x; this.y = y; this.text = t; this.color = c; }
    }
    private static class Particle {
        float x, y, dx, dy, radius; int color, life, maxLife;
        Particle(float x, float y, float dx, float dy, float r, int c, int l) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy; this.radius = r; this.color = c; this.life = l; this.maxLife = l;
        }
    }
    private static class Shockwave {
        float x, y, currentRadius = 12f, maxRadius; int color, alpha = 240;
        Shockwave(float x, float y, float mr, int c) { this.x = x; this.y = y; this.maxRadius = mr; this.color = c; }
    }
    private static class OutZoneDecoration {
        float x, y, rotationDeg; int type;
        OutZoneDecoration(float x, float y, float r, int t) { this.x = x; this.y = y; this.rotationDeg = r; this.type = t; }
    }
}