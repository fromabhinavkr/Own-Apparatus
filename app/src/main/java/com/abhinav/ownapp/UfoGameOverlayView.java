package com.abhinav.ownapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UfoGameOverlayView extends View {

    public interface OnGameOverListener {
        void onGameOver(int finalScore);
    }

    // 13 Distinct 3D UFOs, Spaceships & Large Asteroids
    public static final int TYPE_IRON_ASTEROID = 0;
    public static final int TYPE_MAGMA_ASTEROID = 1;
    public static final int TYPE_CRYO_COMET = 2;
    public static final int TYPE_IMPERIAL_SAUCER = 3;
    public static final int TYPE_JELLY_UFO = 4;
    public static final int TYPE_JUGGERNAUT = 5;
    public static final int TYPE_BIOMECH_LEVIATHAN = 6;
    public static final int TYPE_SENTINEL_HORROR = 7;
    public static final int TYPE_STEALTH_TRIANGLE = 8;
    public static final int TYPE_STAR_DESTROYER = 9;
    public static final int TYPE_ORBITAL_RING = 10;
    public static final int TYPE_CARRIER_CRUISER = 11;
    public static final int TYPE_CHROME_TICTAC = 12;
    private static final int TOTAL_ENTITY_TYPES = 13;

    private final List<SpaceEntity> entities = new ArrayList<>();
    private final List<BurstEffect> bursts = new ArrayList<>();
    private final List<Integer> spawnBag = new ArrayList<>();
    private final Random random = new Random();

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final RectF tempRect = new RectF();
    private final Rect srcRect = new Rect();

    private OnGameOverListener gameOverListener;
    private UfoSoundEngine soundEngine;
    private Sprite3DFactory spriteFactory;

    private boolean isPlaying = false;
    private int score = 0;
    private int lives = 3;
    private long lastFrameTimeNs = 0L;
    private float spawnTimer = 0.7f;
    private float breachFlashAlpha = 0f;
    private float density = 1f;

    public UfoGameOverlayView(Context context) {
        super(context);
        init(context);
    }

    public UfoGameOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public UfoGameOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        spriteFactory = new Sprite3DFactory();
        spriteFactory.prewarmAsync();

        soundEngine = new UfoSoundEngine();
        refillSpawnBag();
    }

    public void setOnGameOverListener(OnGameOverListener listener) {
        this.gameOverListener = listener;
    }

    public void startGame() {
        entities.clear();
        bursts.clear();
        score = 0;
        lives = 3;
        spawnTimer = 0.4f;
        breachFlashAlpha = 0f;
        isPlaying = true;
        lastFrameTimeNs = System.nanoTime();
        refillSpawnBag();
        notifyHud();
        postInvalidateOnAnimation();
    }

    public void stopGame() {
        isPlaying = false;
        entities.clear();
        bursts.clear();
        invalidate();
    }

    private void refillSpawnBag() {
        spawnBag.clear();
        for (int i = 0; i < TOTAL_ENTITY_TYPES; i++) {
            spawnBag.add(i);
        }
        Collections.shuffle(spawnBag, random);
    }

    private int nextEntityType() {
        if (spawnBag.isEmpty()) {
            refillSpawnBag();
        }
        return spawnBag.remove(spawnBag.size() - 1);
    }

    private void notifyHud() {
        if (getContext() instanceof GlobeGameActivity) {
            ((GlobeGameActivity) getContext()).updateScoreAndLives(score, lives);
        }
    }

    public boolean handleTouch(float x, float y) {
        if (!isPlaying) return false;

        for (int i = entities.size() - 1; i >= 0; i--) {
            SpaceEntity e = entities.get(i);
            float dx = x - e.x;
            float dy = y - e.y;
            float hitR = Math.max(44f * density, e.size * 1.18f);
            if (dx * dx + dy * dy <= hitR * hitR) {
                entities.remove(i);
                score += e.points;
                notifyHud();
                bursts.add(createBurst(e));
                if (soundEngine != null) {
                    soundEngine.playExplosion(e.type);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isPlaying = false;
        if (soundEngine != null) {
            soundEngine.release();
        }
        if (spriteFactory != null) {
            spriteFactory.release();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isPlaying && bursts.isEmpty() && breachFlashAlpha <= 0.01f) return;

        long now = System.nanoTime();
        if (lastFrameTimeNs == 0L) lastFrameTimeNs = now;
        float dt = Math.min(0.05f, (now - lastFrameTimeNs) / 1_000_000_000f);
        lastFrameTimeNs = now;

        if (isPlaying) {
            updateGame(dt);
        }
        updateBursts(dt);

        for (int i = 0; i < entities.size(); i++) {
            drawEntity(canvas, entities.get(i));
        }

        for (int i = 0; i < bursts.size(); i++) {
            drawBurst(canvas, bursts.get(i));
        }

        if (breachFlashAlpha > 0.01f) {
            paint.reset();
            paint.setColor(Color.argb((int) (breachFlashAlpha * 145), 220, 25, 35));
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            breachFlashAlpha = Math.max(0f, breachFlashAlpha - dt * 2.4f);
        }

        if (isPlaying || !bursts.isEmpty() || breachFlashAlpha > 0.01f) {
            postInvalidateOnAnimation();
        }
    }

    private void updateGame(float dt) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w * 0.5f;
        float cy = h * 0.5f;
        float planetImpactRadius = Math.min(w, h) * 0.145f;

        spawnTimer -= dt;
        if (spawnTimer <= 0f) {
            spawnEntity(w, h, cx, cy);
            float difficulty = Math.min(1.0f, score / 480f);
            spawnTimer = 1.50f - difficulty * 0.72f + random.nextFloat() * 0.35f;
        }

        Iterator<SpaceEntity> it = entities.iterator();
        while (it.hasNext()) {
            SpaceEntity e = it.next();
            e.age += dt;
            e.phase += dt * e.animSpeed;
            e.spinDeg += e.spinSpeed * dt;

            float dx = cx - e.x;
            float dy = cy - e.y;
            float dist = (float) Math.hypot(dx, dy);

            if (dist <= planetImpactRadius) {
                it.remove();
                lives--;
                breachFlashAlpha = 1f;
                bursts.add(createBurst(e));
                if (soundEngine != null) {
                    soundEngine.playBreach();
                }
                notifyHud();
                if (lives <= 0) {
                    isPlaying = false;
                    entities.clear();
                    if (gameOverListener != null) {
                        gameOverListener.onGameOver(score);
                    }
                    break;
                }
                continue;
            }

            float nx = dx / dist;
            float ny = dy / dist;
            float px = -ny;
            float py = nx;

            float sway = (float) Math.sin(e.age * e.swayFreq + e.swayOffset) * e.swayAmp;
            float moveX = (nx * e.speed + px * sway) * dt;
            float moveY = (ny * e.speed + py * sway) * dt;

            e.x += moveX;
            e.y += moveY;
            e.vx = moveX / Math.max(0.001f, dt);
            e.vy = moveY / Math.max(0.001f, dt);
            e.headingRad = (float) Math.atan2(moveY, moveX);
        }
    }

    private void spawnEntity(int w, int h, float cx, float cy) {
        int type = nextEntityType();
        float margin = 78f * density;
        float x, y;
        int edge = random.nextInt(4);
        if (edge == 0) {
            x = random.nextFloat() * w;
            y = -margin;
        } else if (edge == 1) {
            x = random.nextFloat() * w;
            y = h + margin;
        } else if (edge == 2) {
            x = -margin;
            y = random.nextFloat() * h;
        } else {
            x = w + margin;
            y = random.nextFloat() * h;
        }

        float diff = 1f + Math.min(0.65f, score / 620f);
        SpaceEntity e = new SpaceEntity();
        e.type = type;
        e.x = x;
        e.y = y;
        e.headingRad = (float) Math.atan2(cy - y, cx - x);
        e.swayOffset = random.nextFloat() * 6.28f;
        e.animSpeed = 2.4f + random.nextFloat() * 1.4f;
        e.spinDeg = random.nextFloat() * 360f;

        switch (type) {
            case TYPE_IRON_ASTEROID:
                e.size = 48f * density;
                e.speed = 56f * density * diff;
                e.swayAmp = 4f * density;
                e.swayFreq = 0.7f;
                e.spinSpeed = (random.nextBoolean() ? 1 : -1) * (16f + random.nextFloat() * 18f);
                e.points = 15;
                break;
            case TYPE_MAGMA_ASTEROID:
                e.size = 50f * density;
                e.speed = 62f * density * diff;
                e.swayAmp = 6f * density;
                e.swayFreq = 0.9f;
                e.spinSpeed = (random.nextBoolean() ? 1 : -1) * (20f + random.nextFloat() * 20f);
                e.points = 20;
                break;
            case TYPE_CRYO_COMET:
                e.size = 44f * density;
                e.speed = 92f * density * diff;
                e.swayAmp = 8f * density;
                e.swayFreq = 1.2f;
                e.spinSpeed = (random.nextBoolean() ? 1 : -1) * 32f;
                e.points = 20;
                break;
            case TYPE_IMPERIAL_SAUCER:
                e.size = 50f * density;
                e.speed = 82f * density * diff;
                e.swayAmp = 26f * density;
                e.swayFreq = 2.1f;
                e.points = 15;
                break;
            case TYPE_JELLY_UFO:
                e.size = 48f * density;
                e.speed = 70f * density * diff;
                e.swayAmp = 34f * density;
                e.swayFreq = 1.9f;
                e.points = 20;
                break;
            case TYPE_JUGGERNAUT:
                e.size = 54f * density;
                e.speed = 58f * density * diff;
                e.swayAmp = 12f * density;
                e.swayFreq = 1.1f;
                e.points = 25;
                break;
            case TYPE_BIOMECH_LEVIATHAN:
                e.size = 56f * density;
                e.speed = 66f * density * diff;
                e.swayAmp = 18f * density;
                e.swayFreq = 1.5f;
                e.points = 25;
                break;
            case TYPE_SENTINEL_HORROR:
                e.size = 48f * density;
                e.speed = 78f * density * diff;
                e.swayAmp = 36f * density;
                e.swayFreq = 2.4f;
                e.points = 25;
                break;
            case TYPE_STEALTH_TRIANGLE:
                e.size = 46f * density;
                e.speed = 96f * density * diff;
                e.swayAmp = 22f * density;
                e.swayFreq = 2.2f;
                e.points = 15;
                break;
            case TYPE_STAR_DESTROYER:
                e.size = 56f * density;
                e.speed = 54f * density * diff;
                e.swayAmp = 6f * density;
                e.swayFreq = 0.8f;
                e.points = 25;
                break;
            case TYPE_ORBITAL_RING:
                e.size = 49f * density;
                e.speed = 68f * density * diff;
                e.swayAmp = 16f * density;
                e.swayFreq = 1.4f;
                e.spinSpeed = 24f;
                e.points = 20;
                break;
            case TYPE_CARRIER_CRUISER:
                e.size = 52f * density;
                e.speed = 62f * density * diff;
                e.swayAmp = 10f * density;
                e.swayFreq = 1.0f;
                e.points = 20;
                break;
            case TYPE_CHROME_TICTAC:
            default:
                e.size = 40f * density;
                e.speed = 112f * density * diff;
                e.swayAmp = 42f * density;
                e.swayFreq = 3.4f;
                e.points = 20;
                break;
        }
        entities.add(e);
    }

    // =========================================================================
    // LIVE 3D COMPOSITE RENDERER (PRE-BAKED 3D BITMAP + REAL-TIME ANIMATION)
    // =========================================================================

    private void drawEntity(Canvas canvas, SpaceEntity e) {
        Bitmap sprite = spriteFactory.getSprite(e.type);
        if (sprite == null || sprite.isRecycled()) return;

        float pulse = 0.5f + 0.5f * (float) Math.sin(e.phase * 2.4f);
        float s = e.size;

        canvas.save();
        canvas.translate(e.x, e.y);

        // 1. Pre-layer live effects (tails, thrusters, tentacles behind hulls)
        switch (e.type) {
            case TYPE_CRYO_COMET:
                drawCometTail(canvas, s, e.headingRad, pulse);
                break;
            case TYPE_MAGMA_ASTEROID:
                drawSoftGlow(canvas, 0, 0, s * 1.35f, Color.argb((int) (90 + 55 * pulse), 255, 95, 20));
                break;
            case TYPE_IMPERIAL_SAUCER:
                drawSoftGlow(canvas, 0, s * 0.25f, s * 1.25f, Color.argb((int) (125 + 65 * pulse), 255, 145, 40));
                break;
            case TYPE_JELLY_UFO:
                drawSoftGlow(canvas, 0, -s * 0.05f, s * 1.35f, Color.argb((int) (105 + 50 * pulse), 60, 240, 210));
                drawLiveJellyTentacles(canvas, s, e.phase, pulse);
                break;
            case TYPE_SENTINEL_HORROR:
                drawSoftGlow(canvas, 0, 0, s * 1.3f, Color.argb((int) (90 + 45 * pulse), 130, 200, 235));
                canvas.save();
                canvas.rotate((float) Math.toDegrees(e.headingRad) + 90f);
                drawLiveSentinelTentacles(canvas, s, e.phase);
                canvas.restore();
                break;
            case TYPE_STAR_DESTROYER:
                canvas.save();
                canvas.rotate((float) Math.toDegrees(e.headingRad) + 90f);
                drawEnginePlumes(canvas, s, pulse, Color.parseColor("#38BDF8"), new float[]{-0.22f, 0f, 0.22f}, 0.72f, 0.45f);
                canvas.restore();
                break;
            case TYPE_CARRIER_CRUISER:
                canvas.save();
                canvas.rotate((float) Math.toDegrees(e.headingRad) + 90f);
                drawEnginePlumes(canvas, s, pulse, Color.parseColor("#60A5FA"), new float[]{-0.26f, 0.26f}, 0.72f, 0.50f);
                canvas.restore();
                break;
            case TYPE_CHROME_TICTAC:
                drawSoftGlow(canvas, 0, 0, s * 1.2f, Color.argb((int) (85 + 45 * pulse), 185, 225, 255));
                break;
        }

        // 2. Transform and draw the high-precision 3D baked bitmap
        canvas.save();
        switch (e.type) {
            case TYPE_IRON_ASTEROID:
            case TYPE_MAGMA_ASTEROID:
            case TYPE_CRYO_COMET:
            case TYPE_ORBITAL_RING:
                canvas.rotate(e.spinDeg);
                break;
            case TYPE_IMPERIAL_SAUCER:
            case TYPE_JELLY_UFO:
                // Keep 3/4 side profile upright with eerie hovering tilt
                canvas.rotate((float) Math.sin(e.phase * 0.85f) * 8f);
                break;
            case TYPE_BIOMECH_LEVIATHAN:
                // Keep striking side-profile silhouette upright, facing travel direction
                if (e.vx < 0) {
                    canvas.scale(-1f, 1f);
                    canvas.rotate((float) Math.toDegrees(Math.atan2(e.vy, -e.vx)) * 0.45f);
                } else {
                    canvas.rotate((float) Math.toDegrees(Math.atan2(e.vy, e.vx)) * 0.45f);
                }
                break;
            case TYPE_CHROME_TICTAC:
                canvas.rotate((float) Math.toDegrees(e.headingRad));
                break;
            default:
                // Directional warships, horseshoe juggernaut, sentinel head, stealth triangle
                canvas.rotate((float) Math.toDegrees(e.headingRad) + 90f);
                break;
        }

        srcRect.set(0, 0, sprite.getWidth(), sprite.getHeight());
        tempRect.set(-s, -s, s, s);
        canvas.drawBitmap(sprite, srcRect, tempRect, bitmapPaint);

        // 3. Post-layer live surface lights / animated glints on top of the 3D hull
        switch (e.type) {
            case TYPE_IMPERIAL_SAUCER:
                // Animated rotating warm amber rim strobes + pulsing bottom reactor
                paint.reset();
                paint.setAntiAlias(true);
                int strobes = 9;
                for (int i = 0; i < strobes; i++) {
                    float t = ((i / (float) strobes) + e.phase * 0.14f) % 1f;
                    float lx = (t * 2f - 1f) * s * 0.56f;
                    float ly = s * 0.06f + (float) Math.sqrt(Math.max(0f, 1f - (lx / (s * 0.60f)) * (lx / (s * 0.60f)))) * s * 0.09f;
                    paint.setColor(Color.argb((int) (180 + 75 * pulse), 255, 200, 110));
                    canvas.drawCircle(lx, ly, s * 0.025f, paint);
                }
                drawSoftGlow(canvas, 0, s * 0.42f, s * (0.28f + 0.06f * pulse), Color.argb(230, 255, 210, 115));
                break;
            case TYPE_STEALTH_TRIANGLE:
                // Live pulsing plasma thrusters on 3 corners + central mercury core
                float[][] pts = {{0, -s * 0.56f}, {s * 0.56f, s * 0.46f}, {-s * 0.56f, s * 0.46f}};
                for (float[] p : pts) {
                    drawSoftGlow(canvas, p[0], p[1], s * (0.16f + 0.04f * pulse), Color.argb(225, 0, 230, 255));
                }
                drawSoftGlow(canvas, 0, s * 0.10f, s * (0.25f + 0.05f * pulse), Color.argb(215, 160, 245, 255));
                break;
            case TYPE_ORBITAL_RING:
                // Central pulsing fusion core
                drawSoftGlow(canvas, 0, 0, s * (0.28f + 0.06f * pulse), Color.argb(210, 255, 195, 70));
                break;
            case TYPE_CHROME_TICTAC:
                // Travelling razor-sharp chrome specular glint
                float gx = (float) Math.sin(e.phase * 1.4f) * s * 0.34f;
                paint.reset();
                paint.setAntiAlias(true);
                paint.setColor(Color.argb(225, 255, 255, 255));
                canvas.drawOval(new RectF(gx - s * 0.24f, -s * 0.22f, gx + s * 0.24f, -s * 0.12f), paint);
                break;
            case TYPE_SENTINEL_HORROR:
                // Piercing eerie cold-red optical scan glint inside maw
                drawSoftGlow(canvas, 0, -s * 0.26f, s * (0.14f + 0.04f * pulse), Color.argb(220, 255, 65, 75));
                break;
        }

        canvas.restore();
        canvas.restore();
    }

    private void drawSoftGlow(Canvas canvas, float cx, float cy, float radius, int color) {
        if (radius <= 1f) return;
        glowPaint.reset();
        glowPaint.setAntiAlias(true);
        glowPaint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{color, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, glowPaint);
    }

    private void drawCometTail(Canvas canvas, float s, float headingRad, float pulse) {
        canvas.save();
        canvas.rotate((float) Math.toDegrees(headingRad));
        float tailLen = s * (2.2f + 0.35f * pulse);
        tempPath.reset();
        tempPath.moveTo(0, -s * 0.45f);
        tempPath.lineTo(-tailLen, 0);
        tempPath.lineTo(0, s * 0.45f);
        tempPath.close();

        paint.reset();
        paint.setAntiAlias(true);
        paint.setShader(new LinearGradient(0, 0, -tailLen, 0,
                new int[]{Color.argb(190, 165, 243, 252), Color.argb(95, 56, 189, 248), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP));
        canvas.drawPath(tempPath, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawEnginePlumes(Canvas canvas, float s, float pulse, int color, float[] xOffsets, float yStart, float lenFactor) {
        paint.reset();
        paint.setAntiAlias(true);
        float len = s * (lenFactor + 0.18f * pulse);
        for (float xo : xOffsets) {
            float ex = xo * s;
            float ey = yStart * s;
            paint.setShader(new LinearGradient(ex, ey, ex, ey + len,
                    new int[]{Color.WHITE, color, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(ex - s * 0.08f, ey - s * 0.05f, ex + s * 0.08f, ey + len), paint);
        }
        paint.setShader(null);
    }

    // Live animated fleshy bioluminescent tentacles for Reference 3 (Jellyfish UFO)
    private void drawLiveJellyTentacles(Canvas canvas, float s, float phase, float pulse) {
        float tilt = (float) Math.sin(phase * 0.85f) * 8f;
        canvas.save();
        canvas.rotate(tilt);

        // Ultra-fine glowing filaments
        strokePaint.setStrokeWidth(1.1f * density);
        strokePaint.setColor(Color.argb(120, 145, 245, 235));
        for (int i = -4; i <= 4; i++) {
            float bx = i * s * 0.15f;
            float w1 = (float) Math.sin(phase * 2.8f + i) * s * 0.08f;
            tempPath.reset();
            tempPath.moveTo(bx, s * 0.12f);
            tempPath.quadTo(bx + w1, s * 0.55f, bx - w1, s * 0.92f);
            canvas.drawPath(tempPath, strokePaint);
        }

        // 6 Thick luminous coral/magenta undulating tentacles
        float[] xs = {-0.42f, -0.25f, -0.08f, 0.09f, 0.27f, 0.43f};
        float[] lens = {0.82f, 1.05f, 1.18f, 1.14f, 0.98f, 0.78f};
        for (int i = 0; i < xs.length; i++) {
            float bx = xs[i] * s;
            float len = lens[i] * s;
            float w1 = (float) Math.sin(phase * 2.2f + i * 1.15f) * s * 0.17f;
            float w2 = (float) Math.cos(phase * 2.2f + i * 1.15f) * s * 0.21f;

            tempPath.reset();
            tempPath.moveTo(bx, s * 0.06f);
            tempPath.cubicTo(bx + w1, s * 0.06f + len * 0.35f, bx - w2, s * 0.06f + len * 0.72f, bx + w1 * 0.8f, s * 0.06f + len);

            // Outer warm coral/magenta glow
            strokePaint.setColor(Color.argb((int) (140 + 45 * pulse), 255, 110, 140));
            strokePaint.setStrokeWidth(6.5f * density);
            canvas.drawPath(tempPath, strokePaint);

            // Mid translucent fleshy body
            strokePaint.setColor(Color.argb(235, 255, 185, 145));
            strokePaint.setStrokeWidth(3.4f * density);
            canvas.drawPath(tempPath, strokePaint);

            // Inner bright specular highlight
            strokePaint.setColor(Color.argb(200, 255, 240, 210));
            strokePaint.setStrokeWidth(1.2f * density);
            canvas.drawPath(tempPath, strokePaint);
        }
        canvas.restore();
    }

    // Live animated segmented steel-ringed tentacles for Reference 6 (Sentinel Horror)
    private void drawLiveSentinelTentacles(Canvas canvas, float s, float phase) {
        int count = 12;
        for (int i = 0; i < count; i++) {
            float spread = (i / (float) (count - 1)) * 2f - 1f; // -1 to 1
            float baseAng = (float) (Math.PI * 0.5 + spread * 1.25f); // flaring rearward & sideways
            float len = s * (1.15f + 0.28f * (float) Math.sin(i * 1.9f));
            float wave = (float) Math.sin(phase * 2.6f + i * 0.85f) * 0.34f;

            float x0 = (float) Math.cos(baseAng) * s * 0.20f;
            float y0 = (float) Math.sin(baseAng) * s * 0.20f + s * 0.12f;
            float x1 = (float) Math.cos(baseAng + wave) * len * 0.55f;
            float y1 = (float) Math.sin(baseAng + wave) * len * 0.55f + s * 0.15f;
            float x2 = (float) Math.cos(baseAng - wave * 0.8f) * len;
            float y2 = (float) Math.sin(baseAng - wave * 0.8f) * len + s * 0.15f;

            // Draw segmented ribbed metallic hose using 8 connected segments
            int segs = 8;
            float px = x0, py = y0;
            for (int k = 1; k <= segs; k++) {
                float t = k / (float) segs;
                float u = 1f - t;
                float nx = u * u * x0 + 2 * u * t * x1 + t * t * x2;
                float ny = u * u * y0 + 2 * u * t * y1 + t * t * y2;
                float thick = (1f - t * 0.72f) * 4.2f * density;

                // Dark metallic core hose
                strokePaint.setColor(Color.parseColor("#111722"));
                strokePaint.setStrokeWidth(thick + 1.4f * density);
                canvas.drawLine(px, py, nx, ny, strokePaint);

                // Steel metallic highlight along top of segment
                strokePaint.setColor((k % 2 == 0) ? Color.parseColor("#8FA3B8") : Color.parseColor("#3B485B"));
                strokePaint.setStrokeWidth(thick * 0.65f);
                canvas.drawLine(px, py, nx, ny, strokePaint);

                px = nx;
                py = ny;
            }
        }
    }

    // =========================================================================
    // 13 UNIQUE HIGH-PRECISION BURST ANIMATIONS
    // =========================================================================

    private BurstEffect createBurst(SpaceEntity e) {
        BurstEffect b = new BurstEffect();
        b.type = e.type;
        b.x = e.x;
        b.y = e.y;
        b.baseSize = e.size;
        b.headingRad = e.headingRad;
        b.duration = (e.type == TYPE_STAR_DESTROYER || e.type == TYPE_JUGGERNAUT || e.type == TYPE_BIOMECH_LEVIATHAN) ? 0.96f : 0.76f;

        int count = (e.type == TYPE_STAR_DESTROYER || e.type == TYPE_IRON_ASTEROID || e.type == TYPE_MAGMA_ASTEROID) ? 38 : 30;

        for (int i = 0; i < count; i++) {
            DebrisParticle p = new DebrisParticle();
            float ang = (float) (random.nextFloat() * Math.PI * 2.0);
            float spd = (65f + random.nextFloat() * 215f) * density;

            switch (e.type) {
                case TYPE_IRON_ASTEROID: // Jagged shaded rocky chunks + dust
                    p.shape = (i % 4 == 0) ? 0 : 1;
                    p.color = (i % 4 == 0) ? Color.parseColor("#94A3B8")
                            : (i % 2 == 0 ? Color.parseColor("#8C6F56") : Color.parseColor("#475569"));
                    p.size = (4.5f + random.nextFloat() * 8.5f) * density;
                    spd *= 0.92f;
                    break;
                case TYPE_MAGMA_ASTEROID: // Dark basalt chunks + flying molten lava blobs
                    p.shape = (i % 2 == 0) ? 1 : 0;
                    p.color = (i % 2 == 0) ? Color.parseColor("#2A2220")
                            : (i % 3 == 0 ? Color.parseColor("#FDE047") : Color.parseColor("#F97316"));
                    p.size = (4.5f + random.nextFloat() * 8f) * density;
                    break;
                case TYPE_CRYO_COMET: // Sharp translucent ice crystal shards + cyan mist
                    p.shape = (i % 3 == 0) ? 0 : 1;
                    p.color = (i % 2 == 0) ? Color.parseColor("#E0F2FE") : Color.parseColor("#38BDF8");
                    p.size = (4f + random.nextFloat() * 7.5f) * density;
                    spd *= 1.25f;
                    break;
                case TYPE_IMPERIAL_SAUCER: // Curved metallic plates + golden plasma sparks
                    p.shape = (i % 3 == 0) ? 3 : 2;
                    p.color = (i % 2 == 0) ? Color.parseColor("#FDBA74") : Color.parseColor("#94A3B8");
                    p.size = (4.5f + random.nextFloat() * 7f) * density;
                    spd *= 1.2f;
                    break;
                case TYPE_JELLY_UFO: // Curling luminous tentacles + turquoise/coral spores
                    p.shape = (i < 8) ? 5 : 0;
                    p.color = (i < 8) ? Color.parseColor("#FDBA74")
                            : (i % 2 == 0 ? Color.parseColor("#2DD4BF") : Color.parseColor("#FB7185"));
                    p.size = (4f + random.nextFloat() * 7.5f) * density;
                    spd *= 0.9f;
                    break;
                case TYPE_JUGGERNAUT: // Heavy ribbed grey hull blocks + cold sparks
                    p.shape = (i % 3 == 0) ? 2 : 1;
                    p.color = (i % 3 == 0) ? Color.parseColor("#BAE6FD") : Color.parseColor("#475569");
                    p.size = (5f + random.nextFloat() * 9f) * density;
                    break;
                case TYPE_BIOMECH_LEVIATHAN: // Dark chitin ribs + amber bio-vapour droplets
                    p.shape = (i % 2 == 0) ? 3 : 0;
                    p.color = (i % 2 == 0) ? Color.parseColor("#574A3D") : Color.parseColor("#D97706");
                    p.size = (4.5f + random.nextFloat() * 8f) * density;
                    break;
                case TYPE_SENTINEL_HORROR: // Thrashing detached steel tentacles + crimson sparks
                    p.shape = (i < 10) ? 5 : 2;
                    p.color = (i < 10) ? Color.parseColor("#94A3B8") : Color.parseColor("#F43F5E");
                    p.size = (4.5f + random.nextFloat() * 8f) * density;
                    spd *= 1.15f;
                    break;
                case TYPE_STEALTH_TRIANGLE: // EMP cyan arcs + dark carbon triangles
                    p.shape = (i % 2 == 0) ? 2 : 1;
                    p.color = (i % 2 == 0) ? Color.parseColor("#00E5FF") : Color.parseColor("#334155");
                    p.size = (4f + random.nextFloat() * 7.5f) * density;
                    spd *= 1.3f;
                    break;
                case TYPE_STAR_DESTROYER: // Fiery orange explosions + dark smoke + steel hull girders
                    p.shape = (i % 3 == 0) ? 1 : 0;
                    p.color = (i % 3 == 0) ? Color.parseColor("#64748B")
                            : (i % 2 == 0 ? Color.parseColor("#FF6B00") : Color.parseColor("#FEF08A"));
                    p.size = (5f + random.nextFloat() * 9.5f) * density;
                    break;
                case TYPE_ORBITAL_RING: // Snapping metallic ring arcs + gold embers
                    p.shape = (i % 2 == 0) ? 3 : 0;
                    p.color = (i % 2 == 0) ? Color.parseColor("#CBD5E1") : Color.parseColor("#FBBF24");
                    p.size = (4.5f + random.nextFloat() * 7.5f) * density;
                    break;
                case TYPE_CARRIER_CRUISER: // Rectangular armoured plates + blue fusion sparks
                    p.shape = (i % 2 == 0) ? 4 : 2;
                    p.color = (i % 2 == 0) ? Color.parseColor("#64748B") : Color.parseColor("#60A5FA");
                    p.size = (4.5f + random.nextFloat() * 7.5f) * density;
                    break;
                case TYPE_CHROME_TICTAC:
                default: // High-velocity liquid mercury droplets & chrome streaks
                    p.shape = 2;
                    p.color = (i % 2 == 0) ? Color.WHITE : Color.parseColor("#CBD5E1");
                    p.size = (3.5f + random.nextFloat() * 6f) * density;
                    spd *= 1.5f;
                    break;
            }

            p.vx = (float) Math.cos(ang) * spd;
            p.vy = (float) Math.sin(ang) * spd;
            p.rot = random.nextFloat() * 360f;
            p.vRot = (random.nextFloat() - 0.5f) * 480f;
            b.particles.add(p);
        }
        return b;
    }

    private void updateBursts(float dt) {
        Iterator<BurstEffect> it = bursts.iterator();
        while (it.hasNext()) {
            BurstEffect b = it.next();
            b.age += dt;
            if (b.age >= b.duration) {
                it.remove();
                continue;
            }
            float drag = (b.type == TYPE_IRON_ASTEROID || b.type == TYPE_JELLY_UFO) ? 0.94f : 0.965f;
            for (int i = 0; i < b.particles.size(); i++) {
                DebrisParticle p = b.particles.get(i);
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                p.vx *= drag;
                p.vy *= drag;
                p.rot += p.vRot * dt;
            }
        }
    }

    private void drawBurst(Canvas canvas, BurstEffect b) {
        float t = Math.min(1f, b.age / b.duration);
        float inv = 1f - t;
        int alpha = Math.max(0, Math.min(255, (int) (inv * 255)));

        canvas.save();
        canvas.translate(b.x, b.y);

        // 1. Distinct primary shockwave / explosion core per class
        switch (b.type) {
            case TYPE_IRON_ASTEROID: // Expanding dust cloud + orange kinetic flash
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.8f + t * 2.1f), Color.argb((int) (alpha * 0.7f), 160, 145, 130));
                if (t < 0.3f) {
                    drawSoftGlow(canvas, 0, 0, b.baseSize * (1.2f * (1f - t)), Color.argb((int) ((1f - t / 0.3f) * 240), 255, 195, 120));
                }
                break;
            case TYPE_MAGMA_ASTEROID: // Scorching lava shockwave ring + molten glow
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.9f + t * 2.2f), Color.argb(alpha, 255, 110, 20));
                strokePaint.setColor(Color.argb(alpha, 254, 240, 138));
                strokePaint.setStrokeWidth(4.5f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.4f + t * 2.4f), strokePaint);
                break;
            case TYPE_CRYO_COMET: // Freezing cyan mist + 8-point crystalline frost starburst
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.8f + t * 2.0f), Color.argb((int) (alpha * 0.75f), 125, 211, 252));
                strokePaint.setColor(Color.argb(alpha, 224, 242, 254));
                strokePaint.setStrokeWidth(2.6f * inv * density);
                float spikeR = b.baseSize * (0.6f + t * 2.5f);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4.0;
                    canvas.drawLine(0, 0, (float) Math.cos(a) * spikeR, (float) Math.sin(a) * spikeR, strokePaint);
                }
                break;
            case TYPE_IMPERIAL_SAUCER: // Twin superheated golden-orange concentric rings
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.8f + t * 1.9f), Color.argb(alpha, 255, 180, 60));
                strokePaint.setColor(Color.argb(alpha, 255, 155, 45));
                strokePaint.setStrokeWidth(4.5f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.45f + t * 2.6f), strokePaint);
                strokePaint.setColor(Color.argb(alpha, 255, 245, 200));
                strokePaint.setStrokeWidth(2.0f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.25f + t * 1.8f), strokePaint);
                break;
            case TYPE_JELLY_UFO: // Bioluminescent turquoise & magenta nebula cloud
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.9f + t * 2.4f), Color.argb((int) (alpha * 0.85f), 45, 212, 191));
                drawSoftGlow(canvas, 0, b.baseSize * 0.15f, b.baseSize * (0.6f + t * 1.8f), Color.argb((int) (alpha * 0.7f), 251, 113, 133));
                break;
            case TYPE_JUGGERNAUT: // Heavy steel shock-ring + cold plasma flash
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.8f + t * 2.1f), Color.argb(alpha, 186, 230, 253));
                strokePaint.setColor(Color.argb(alpha, 148, 163, 184));
                strokePaint.setStrokeWidth(5f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.5f + t * 2.5f), strokePaint);
                break;
            case TYPE_BIOMECH_LEVIATHAN: // Corrosive amber-ochre vapour burst + organic rift
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.9f + t * 2.2f), Color.argb((int) (alpha * 0.85f), 217, 119, 6));
                strokePaint.setColor(Color.argb(alpha, 253, 186, 116));
                strokePaint.setStrokeWidth(3.2f * inv * density);
                canvas.drawOval(new RectF(-b.baseSize * (0.8f + t * 2.2f), -b.baseSize * (0.35f + t * 1.1f),
                        b.baseSize * (0.8f + t * 2.2f), b.baseSize * (0.35f + t * 1.1f)), strokePaint);
                break;
            case TYPE_SENTINEL_HORROR: // Blinding crimson optical flash + jagged dark arcs
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.8f + t * 2.0f), Color.argb(alpha, 244, 63, 94));
                strokePaint.setColor(Color.argb(alpha, 255, 228, 230));
                strokePaint.setStrokeWidth(2.6f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.4f + t * 2.5f), strokePaint);
                break;
            case TYPE_STEALTH_TRIANGLE: // Jagged cyan EMP lightning bolts + ring
                strokePaint.setColor(Color.argb(alpha, 0, 235, 255));
                strokePaint.setStrokeWidth(2.4f * density);
                float maxR = b.baseSize * (0.7f + t * 2.4f);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3.0 + t * 0.5;
                    float mx = (float) Math.cos(a + 0.16) * maxR * 0.55f;
                    float my = (float) Math.sin(a + 0.16) * maxR * 0.55f;
                    canvas.drawLine(0, 0, mx, my, strokePaint);
                    canvas.drawLine(mx, my, (float) Math.cos(a) * maxR, (float) Math.sin(a) * maxR, strokePaint);
                }
                canvas.drawCircle(0, 0, maxR * 0.85f, strokePaint);
                break;
            case TYPE_STAR_DESTROYER: // Multi-stage cascading fireballs
                drawSoftGlow(canvas, 0, 0, b.baseSize * (1.0f + t * 2.1f), Color.argb(alpha, 255, 190, 70));
                if (t > 0.16f) {
                    float t2 = (t - 0.16f) / 0.84f;
                    int a2 = (int) ((1f - t2) * 240);
                    drawSoftGlow(canvas, b.baseSize * 0.42f, -b.baseSize * 0.28f, b.baseSize * (0.4f + t2 * 1.4f), Color.argb(a2, 255, 110, 25));
                    drawSoftGlow(canvas, -b.baseSize * 0.36f, b.baseSize * 0.34f, b.baseSize * (0.35f + t2 * 1.3f), Color.argb(a2, 255, 210, 90));
                }
                break;
            case TYPE_ORBITAL_RING: // Snapping concentric metallic gold arcs
                strokePaint.setColor(Color.argb(alpha, 251, 191, 36));
                strokePaint.setStrokeWidth(3.8f * inv * density);
                float r1 = b.baseSize * (0.5f + t * 2.4f);
                canvas.drawArc(new RectF(-r1, -r1, r1, r1), t * 160f, 115f, false, strokePaint);
                canvas.drawArc(new RectF(-r1, -r1, r1, r1), t * 160f + 180f, 115f, false, strokePaint);
                drawSoftGlow(canvas, 0, 0, b.baseSize * (0.7f + t * 1.5f), Color.argb(alpha, 255, 235, 160));
                break;
            case TYPE_CARRIER_CRUISER: // Twin blue fusion shockwaves
                drawSoftGlow(canvas, -b.baseSize * 0.25f, 0, b.baseSize * (0.7f + t * 1.8f), Color.argb(alpha, 96, 165, 250));
                drawSoftGlow(canvas, b.baseSize * 0.25f, 0, b.baseSize * (0.7f + t * 1.8f), Color.argb(alpha, 147, 197, 253));
                strokePaint.setColor(Color.argb(alpha, 191, 219, 254));
                strokePaint.setStrokeWidth(3.2f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.45f + t * 2.5f), strokePaint);
                break;
            case TYPE_CHROME_TICTAC: // Blinding white flash + sonic-boom ring
                if (t < 0.35f) {
                    float ft = 1f - (t / 0.35f);
                    drawSoftGlow(canvas, 0, 0, b.baseSize * (1.1f + t * 3.8f), Color.argb((int) (ft * 255), 255, 255, 255));
                }
                strokePaint.setColor(Color.argb(alpha, 241, 245, 249));
                strokePaint.setStrokeWidth(3.2f * inv * density);
                canvas.drawCircle(0, 0, b.baseSize * (0.5f + t * 3.0f), strokePaint);
                break;
        }

        // 2. Render particles
        paint.reset();
        paint.setAntiAlias(true);
        for (int i = 0; i < b.particles.size(); i++) {
            DebrisParticle p = b.particles.get(i);
            int pAlpha = Math.max(0, Math.min(255, (int) (inv * 255)));
            int col = (pAlpha << 24) | (p.color & 0x00FFFFFF);

            canvas.save();
            canvas.translate(p.x, p.y);
            canvas.rotate(p.rot);

            if (p.shape == 0) {
                paint.setColor(col);
                canvas.drawCircle(0, 0, p.size * (0.35f + 0.65f * inv), paint);
            } else if (p.shape == 1) { // Jagged 3D-like boulder / hull shard
                paint.setColor(col);
                tempPath.reset();
                tempPath.moveTo(0, -p.size);
                tempPath.lineTo(p.size * 0.72f, -p.size * 0.1f);
                tempPath.lineTo(p.size * 0.45f, p.size * 0.8f);
                tempPath.lineTo(-p.size * 0.62f, p.size * 0.55f);
                tempPath.close();
                canvas.drawPath(tempPath, paint);
            } else if (p.shape == 2) { // High-velocity streak
                strokePaint.setColor(col);
                strokePaint.setStrokeWidth(Math.max(1.5f, p.size * 0.32f));
                canvas.drawLine(-p.size * 1.35f, 0, p.size * 1.35f, 0, strokePaint);
            } else if (p.shape == 3) { // Curved hull plate / rib arc
                strokePaint.setColor(col);
                strokePaint.setStrokeWidth(2.6f * density);
                canvas.drawArc(new RectF(-p.size, -p.size, p.size, p.size), 0, 120, false, strokePaint);
            } else if (p.shape == 4) { // Armoured rectangular hull plate
                paint.setColor(col);
                canvas.drawRect(-p.size * 0.75f, -p.size * 0.45f, p.size * 0.75f, p.size * 0.45f, paint);
            } else if (p.shape == 5) { // Curling detached tentacle
                strokePaint.setColor(col);
                strokePaint.setStrokeWidth(2.3f * density);
                tempPath.reset();
                tempPath.moveTo(-p.size * 1.6f, 0);
                tempPath.quadTo(0, p.size * 1.3f, p.size * 1.6f, -p.size * 0.45f);
                canvas.drawPath(tempPath, strokePaint);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    // =========================================================================
    // HIGH-PRECISION 3D PROCEDURAL SPRITE FACTORY (256x256 MULTI-PASS SHADING)
    // =========================================================================

    private static class Sprite3DFactory {
        private static final int SIZE = 256;
        private static final float HALF = SIZE * 0.5f;
        private final Bitmap[] cache = new Bitmap[TOTAL_ENTITY_TYPES];
        private final ExecutorService builderExec = Executors.newSingleThreadExecutor();
        private volatile boolean released = false;

        void prewarmAsync() {
            builderExec.execute(() -> {
                for (int i = 0; i < TOTAL_ENTITY_TYPES; i++) {
                    if (released) return;
                    getSprite(i);
                }
            });
        }

        Bitmap getSprite(int type) {
            if (released || type < 0 || type >= TOTAL_ENTITY_TYPES) return null;
            Bitmap bmp = cache[type];
            if (bmp != null && !bmp.isRecycled()) return bmp;
            synchronized (cache) {
                if (released) return null;
                bmp = cache[type];
                if (bmp == null || bmp.isRecycled()) {
                    bmp = buildSprite(type);
                    cache[type] = bmp;
                }
                return bmp;
            }
        }

        void release() {
            released = true;
            builderExec.shutdownNow();
            synchronized (cache) {
                for (int i = 0; i < cache.length; i++) {
                    if (cache[i] != null && !cache[i].isRecycled()) {
                        cache[i].recycle();
                    }
                    cache[i] = null;
                }
            }
        }

        private Bitmap buildSprite(int type) {
            Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.translate(HALF, HALF);

            switch (type) {
                case TYPE_IRON_ASTEROID:
                    bakeAsteroid(c, 101, false, false);
                    break;
                case TYPE_MAGMA_ASTEROID:
                    bakeAsteroid(c, 202, true, false);
                    break;
                case TYPE_CRYO_COMET:
                    bakeAsteroid(c, 303, false, true);
                    break;
                case TYPE_IMPERIAL_SAUCER:
                    bakeImperialSaucer(c);
                    break;
                case TYPE_JELLY_UFO:
                    bakeJellyUfo(c);
                    break;
                case TYPE_JUGGERNAUT:
                    bakeJuggernaut(c);
                    break;
                case TYPE_BIOMECH_LEVIATHAN:
                    bakeBiomechLeviathan(c);
                    break;
                case TYPE_SENTINEL_HORROR:
                    bakeSentinelHead(c);
                    break;
                case TYPE_STEALTH_TRIANGLE:
                    bakeStealthTriangle(c);
                    break;
                case TYPE_STAR_DESTROYER:
                    bakeStarDestroyer(c);
                    break;
                case TYPE_ORBITAL_RING:
                    bakeOrbitalRing(c);
                    break;
                case TYPE_CARRIER_CRUISER:
                    bakeCarrierCruiser(c);
                    break;
                case TYPE_CHROME_TICTAC:
                default:
                    bakeChromeTicTac(c);
                    break;
            }
            return bmp;
        }

        // ---------------------------------------------------------------------
        // 1, 2, 3: 3D CRATERED ASTEROIDS (IRON, MAGMA, CRYO-COMET) — Ref 1
        // ---------------------------------------------------------------------
        private void bakeAsteroid(Canvas c, long seed, boolean isMagma, boolean isCryo) {
            Random rnd = new Random(seed);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Build irregular 56-point realistic rocky silhouette
            Path rockPath = new Path();
            int verts = 56;
            float baseR = 98f;
            float p1 = rnd.nextFloat() * 6.28f;
            float p2 = rnd.nextFloat() * 6.28f;
            float p3 = rnd.nextFloat() * 6.28f;

            for (int i = 0; i < verts; i++) {
                double a = (i / (double) verts) * Math.PI * 2.0;
                double wobble = 1.0
                        + 0.085 * Math.sin(a * 2 + p1)
                        + 0.065 * Math.cos(a * 3 + p2)
                        + 0.045 * Math.sin(a * 5 + p3)
                        + (rnd.nextDouble() - 0.5) * (isCryo ? 0.09 : 0.045);
                float r = (float) (baseR * wobble);
                float vx = (float) Math.cos(a) * r;
                float vy = (float) Math.sin(a) * r;
                if (i == 0) rockPath.moveTo(vx, vy);
                else rockPath.lineTo(vx, vy);
            }
            rockPath.close();

            c.save();
            c.clipPath(rockPath);

            // Base 3D spherical space-lit rock gradient (upper-left sun vs lower-right cold space shadow)
            int[] cols;
            if (isMagma) {
                cols = new int[]{
                        Color.parseColor("#5C4B47"),
                        Color.parseColor("#2B2324"),
                        Color.parseColor("#131015"),
                        Color.parseColor("#2A1818")
                };
            } else if (isCryo) {
                cols = new int[]{
                        Color.parseColor("#E0F2FE"),
                        Color.parseColor("#7DD3FC"),
                        Color.parseColor("#1E3A5F"),
                        Color.parseColor("#0F1D33")
                };
            } else {
                // Exact ochre/brown sunlit crest -> blue-slate crater shadow from Reference 1
                cols = new int[]{
                        Color.parseColor("#D4B495"),
                        Color.parseColor("#8D7765"),
                        Color.parseColor("#3F485E"),
                        Color.parseColor("#161C2B")
                };
            }
            p.setShader(new RadialGradient(-38f, -42f, 175f, cols, new float[]{0f, 0.34f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(rockPath, p);
            p.setShader(null);

            // Subtle rocky terrain elevation patches
            for (int i = 0; i < 45; i++) {
                float px = (rnd.nextFloat() * 2f - 1f) * 86f;
                float py = (rnd.nextFloat() * 2f - 1f) * 86f;
                float pr = 10f + rnd.nextFloat() * 24f;
                boolean bright = (px + py) < 10f && rnd.nextBoolean();
                p.setColor(bright ? Color.argb(38, 235, 215, 190) : Color.argb(48, 12, 16, 28));
                c.drawCircle(px, py, pr, p);
            }

            // Realistic 3D sculpted impact craters (large basins + medium sharp craters)
            int craterCount = isCryo ? 10 : 14;
            for (int i = 0; i < craterCount; i++) {
                float cr = (i < 3) ? (22f + rnd.nextFloat() * 18f) : (6f + rnd.nextFloat() * 13f);
                float dist = rnd.nextFloat() * (76f - cr * 0.5f);
                float ang = rnd.nextFloat() * 6.28f;
                float cx = (float) Math.cos(ang) * dist;
                float cy = (float) Math.sin(ang) * dist;

                // Outer sunlit raised rim (bottom-right rim catches upper-left sun)
                p.setColor(isCryo ? Color.argb(120, 224, 242, 254) : Color.argb(115, 226, 200, 172));
                c.drawCircle(cx + cr * 0.12f, cy + cr * 0.12f, cr * 1.08f, p);

                // Dark shadow rim on top-left
                p.setColor(Color.argb(135, 14, 18, 30));
                c.drawCircle(cx - cr * 0.08f, cy - cr * 0.08f, cr * 1.04f, p);

                // Concave crater bowl with opposite-side directional lighting
                int bowlDark = isCryo ? Color.parseColor("#0F243F") : Color.parseColor("#181D2B");
                int bowlLit = isCryo ? Color.parseColor("#7DD3FC") : (isMagma ? Color.parseColor("#473938") : Color.parseColor("#9C8570"));
                p.setShader(new LinearGradient(cx - cr, cy - cr, cx + cr, cy + cr,
                        new int[]{bowlDark, Color.argb(230, 38, 43, 58), bowlLit},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, cr * 0.92f, p);
                p.setShader(null);

                // Crisp crater rim bevel
                sp.setStrokeWidth(1.5f);
                sp.setColor(isCryo ? Color.argb(110, 240, 249, 255) : Color.argb(85, 230, 205, 180));
                c.drawArc(new RectF(cx - cr, cy - cr, cx + cr, cy + cr), -15, 120, false, sp);
            }

            // Magma lava fissures carved across dark rock
            if (isMagma) {
                for (int i = 0; i < 6; i++) {
                    Path crack = new Path();
                    float sx = (rnd.nextFloat() - 0.5f) * 35f;
                    float sy = (rnd.nextFloat() - 0.5f) * 35f;
                    crack.moveTo(sx, sy);
                    double ca = (i / 6.0) * Math.PI * 2.0 + rnd.nextDouble() * 0.5;
                    for (int s = 1; s <= 4; s++) {
                        ca += (rnd.nextDouble() - 0.5) * 0.7;
                        sx += (float) Math.cos(ca) * 24f;
                        sy += (float) Math.sin(ca) * 24f;
                        crack.lineTo(sx, sy);
                    }
                    sp.setStrokeCap(Paint.Cap.ROUND);
                    sp.setColor(Color.argb(160, 234, 88, 12));
                    sp.setStrokeWidth(9f);
                    sp.setMaskFilter(new BlurMaskFilter(5f, BlurMaskFilter.Blur.NORMAL));
                    c.drawPath(crack, sp);
                    sp.setMaskFilter(null);

                    sp.setColor(Color.parseColor("#FF6B00"));
                    sp.setStrokeWidth(4.5f);
                    c.drawPath(crack, sp);

                    sp.setColor(Color.parseColor("#FEF08A"));
                    sp.setStrokeWidth(1.8f);
                    c.drawPath(crack, sp);
                }
            }

            // High-frequency granular rock texture & mineral speckles
            for (int i = 0; i < 320; i++) {
                float gx = (rnd.nextFloat() * 2f - 1f) * 95f;
                float gy = (rnd.nextFloat() * 2f - 1f) * 95f;
                float gr = 0.8f + rnd.nextFloat() * 2.1f;
                if (rnd.nextBoolean()) {
                    p.setColor(isCryo ? Color.argb(115, 240, 250, 255) : Color.argb(75, 245, 225, 200));
                } else {
                    p.setColor(Color.argb(95, 10, 12, 20));
                }
                c.drawCircle(gx, gy, gr, p);
            }

            // Subtle cold space blue ambient rim light on dark bottom-right edge (matching Reference 1)
            sp.setStrokeWidth(6f);
            sp.setColor(isMagma ? Color.argb(110, 255, 110, 40) : Color.argb(130, 115, 155, 225));
            sp.setMaskFilter(new BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL));
            c.drawPath(rockPath, sp);
            sp.setMaskFilter(null);
            c.restore();
        }

        // ---------------------------------------------------------------------
        // 4: TIERED IMPERIAL SAUCER (Reference 2)
        // ---------------------------------------------------------------------
        private void bakeImperialSaucer(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // 1. Bottom glowing plasma exhaust nozzle housing
            Path lowerCone = new Path();
            lowerCone.moveTo(-54, 18);
            lowerCone.lineTo(-34, 52);
            lowerCone.quadTo(0, 66, 34, 52);
            lowerCone.lineTo(54, 18);
            lowerCone.close();
            p.setShader(new LinearGradient(-54, 18, 54, 18,
                    new int[]{Color.parseColor("#2A333E"), Color.parseColor("#4B5563"), Color.parseColor("#141921"), Color.parseColor("#2E3744")},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(lowerCone, p);
            p.setShader(null);

            // Concentric dark ribs on bottom propulsion cone
            sp.setStrokeWidth(1.3f);
            for (int i = 0; i < 6; i++) {
                float y = 24f + i * 4.8f;
                float rx = 50f - i * 3.1f;
                sp.setColor(Color.argb(175, 14, 18, 24));
                c.drawArc(new RectF(-rx, y - 6f, rx, y + 10f), 10, 160, false, sp);
            }

            // Intense blazing orange-white thruster vent underneath with radial vanes
            RectF vent = new RectF(-33f, 43f, 33f, 59f);
            p.setShader(new RadialGradient(0, 49f, 34f,
                    new int[]{Color.WHITE, Color.parseColor("#FFE082"), Color.parseColor("#FF7A18"), Color.parseColor("#6E1F05")},
                    new float[]{0f, 0.35f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawOval(vent, p);
            p.setShader(null);

            sp.setColor(Color.argb(145, 95, 25, 0));
            sp.setStrokeWidth(1.3f);
            for (int i = -6; i <= 6; i++) {
                c.drawLine(i * 2.2f, 46f, i * 4.8f, 57f, sp);
            }

            // 2. Main wide 3D saucer underside dish with 18 precision ribbed grooves
            RectF saucerRim = new RectF(-114f, -26f, 114f, 32f);
            p.setShader(new LinearGradient(0, -26f, 0, 32f,
                    new int[]{Color.parseColor("#1D2631"), Color.parseColor("#0F151D"), Color.parseColor("#2A3442")},
                    null, Shader.TileMode.CLAMP));
            c.drawOval(saucerRim, p);
            p.setShader(null);

            c.save();
            Path underClip = new Path();
            underClip.addOval(saucerRim, Path.Direction.CW);
            c.clipPath(underClip);

            for (int i = 0; i < 16; i++) {
                float f = 1f - i * 0.048f;
                float rx = 112f * f;
                float ry = 29f * f;
                sp.setStrokeWidth(1.2f);
                sp.setColor(i % 2 == 0 ? Color.argb(140, 120, 140, 160) : Color.argb(180, 10, 14, 20));
                c.drawOval(new RectF(-rx, -ry + 4f, rx, ry + 4f), sp);
            }

            // Recessed glowing amber inner light ring reflecting off ribs
            sp.setStrokeWidth(4.5f);
            sp.setColor(Color.argb(180, 255, 175, 85));
            sp.setMaskFilter(new BlurMaskFilter(3.5f, BlurMaskFilter.Blur.NORMAL));
            c.drawArc(new RectF(-76f, -4f, 76f, 24f), 8, 164, false, sp);
            sp.setMaskFilter(null);
            c.restore();

            // 3. Upper brushed-steel sloped deck
            Path upperHull = new Path();
            upperHull.moveTo(-114f, 2f);
            upperHull.cubicTo(-95f, -24f, -55f, -38f, 0f, -40f);
            upperHull.cubicTo(55f, -38f, 95f, -24f, 114f, 2f);
            upperHull.quadTo(0f, -24f, -114f, 2f);
            upperHull.close();
            p.setShader(new LinearGradient(-114f, -40f, 114f, 2f,
                    new int[]{Color.parseColor("#C7D4E2"), Color.parseColor("#7E92A8"), Color.parseColor("#4A5A6D"), Color.parseColor("#8FA4BA")},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(upperHull, p);
            p.setShader(null);

            // Crisp metallic highlight along leading rim edge
            sp.setStrokeWidth(2.2f);
            sp.setColor(Color.argb(230, 232, 242, 252));
            c.drawArc(new RectF(-114f, -25f, 114f, 29f), 188, 164, false, sp);

            // 4. Truncated-cone brushed titanium command cupola
            Path cupola = new Path();
            cupola.moveTo(-46f, -32f);
            cupola.lineTo(-32f, -66f);
            cupola.quadTo(0f, -74f, 32f, -66f);
            cupola.lineTo(46f, -32f);
            cupola.quadTo(0f, -41f, -46f, -32f);
            cupola.close();

            p.setShader(new LinearGradient(-46f, -68f, 46f, -32f,
                    new int[]{Color.parseColor("#D8E4F0"), Color.parseColor("#8BA0B5"), Color.parseColor("#54667B"), Color.parseColor("#73879C")},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(cupola, p);
            p.setShader(null);

            // 5 perspective-skewed dark glass bridge windows curving around cupola
            p.setColor(Color.parseColor("#1B2430"));
            float[] wx = {-31f, -18f, 0f, 18f, 31f};
            float[] ww = {5.5f, 7.5f, 8.5f, 7.5f, 5.5f};
            for (int i = 0; i < wx.length; i++) {
                Path win = new Path();
                float cx = wx[i];
                float hw = ww[i];
                float skew = cx * 0.14f;
                win.moveTo(cx - hw + skew * 0.4f, -58f);
                win.lineTo(cx + hw + skew * 0.4f, -58f);
                win.lineTo(cx + hw + skew, -43f);
                win.lineTo(cx - hw + skew, -43f);
                win.close();
                c.drawPath(win, p);
            }
        }

        // ---------------------------------------------------------------------
        // 5: BIOLUMINESCENT JELLYFISH UFO (Reference 3 — dome + brim)
        // ---------------------------------------------------------------------
        private void bakeJellyUfo(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Translucent glowing coral scalloped underside bells
            for (int i = -2; i <= 2; i++) {
                float bx = i * 26f;
                p.setShader(new RadialGradient(bx, 18f, 34f,
                        new int[]{Color.parseColor("#FFE2A8"), Color.parseColor("#FF8A75"), Color.argb(180, 175, 65, 115)},
                        null, Shader.TileMode.CLAMP));
                c.drawOval(new RectF(bx - 28f, -4f, bx + 28f, 34f), p);
            }
            p.setShader(null);

            // Wide luminous crystalline saucer brim
            RectF brim = new RectF(-106f, -18f, 106f, 24f);
            p.setShader(new LinearGradient(-106f, -18f, 106f, 24f,
                    new int[]{Color.parseColor("#67E8F9"), Color.parseColor("#D9F9F9"), Color.parseColor("#38BDF8"), Color.parseColor("#A5F3FC")},
                    null, Shader.TileMode.CLAMP));
            c.drawOval(brim, p);
            p.setShader(null);

            sp.setStrokeWidth(2.4f);
            sp.setColor(Color.argb(220, 236, 254, 255));
            c.drawOval(brim, sp);

            // Glossy 3D translucent emerald-cyan glass bell dome
            RectF domeRect = new RectF(-76f, -104f, 76f, 22f);
            Path dome = new Path();
            dome.addArc(domeRect, 180, 180);
            dome.quadTo(0, -14f, -76f, -41f);
            dome.close();

            p.setShader(new RadialGradient(22f, -76f, 105f,
                    new int[]{
                            Color.parseColor("#D9FFF2"),
                            Color.parseColor("#34D399"),
                            Color.parseColor("#134E4A"),
                            Color.parseColor("#0B1E28")
                    },
                    new float[]{0f, 0.28f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            c.drawArc(domeRect, 180, 180, true, p);
            p.setShader(null);

            // Dark metallic band around base of dome with glowing amber light slits
            p.setColor(Color.argb(195, 15, 32, 46));
            c.drawRect(-75f, -30f, 75f, -14f, p);
            p.setColor(Color.parseColor("#FDE68A"));
            c.drawOval(new RectF(-42f, -25f, -16f, -19f), p);
            c.drawOval(new RectF(24f, -25f, 48f, -19f), p);

            // Bright glass dome rim glow & curved specular highlight
            sp.setStrokeWidth(3.2f);
            sp.setColor(Color.argb(235, 167, 243, 208));
            c.drawArc(domeRect, 185, 170, false, sp);

            p.setColor(Color.argb(150, 255, 255, 255));
            c.save();
            c.rotate(24f, 24f, -72f);
            c.drawOval(new RectF(2f, -84f, 48f, -62f), p);
            c.restore();
        }

        // ---------------------------------------------------------------------
        // 6: JUGGERNAUT HORSESHOE SHIP (Reference 4 — Alien/Prometheus Derelict)
        // ---------------------------------------------------------------------
        private void bakeJuggernaut(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);
            sp.setStrokeCap(Paint.Cap.ROUND);

            // Thick C-shaped horseshoe tubular hull (opening facing UP / forward -Y)
            RectF arcBounds = new RectF(-76f, -68f, 76f, 82f);

            // 1. Deep shadow outer silhouette
            sp.setStrokeWidth(42f);
            sp.setColor(Color.parseColor("#111720"));
            c.drawArc(arcBounds, -42f, 264f, false, sp);

            // 2. Mid metallic steel-slate tubular curvature
            sp.setStrokeWidth(36f);
            sp.setShader(new LinearGradient(-95f, -80f, 95f, 95f,
                    new int[]{Color.parseColor("#758391"), Color.parseColor("#39434F"), Color.parseColor("#1E2630"), Color.parseColor("#546170")},
                    null, Shader.TileMode.CLAMP));
            c.drawArc(arcBounds, -42f, 264f, false, sp);
            sp.setShader(null);

            // 3. Lit upper spine highlight along outer horseshoe ridge
            sp.setStrokeWidth(14f);
            sp.setColor(Color.argb(155, 165, 180, 195));
            c.drawArc(new RectF(-82f, -74f, 82f, 88f), -38f, 256f, false, sp);

            // 4. 36 Perpendicular biomechanical armour ribs wrapping around the horseshoe
            float cx = 0f, cy = 7f, rMid = 76f;
            for (int i = 0; i <= 36; i++) {
                double deg = -40.0 + (i / 36.0) * 260.0;
                double rad = Math.toRadians(deg);
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);
                float xIn = cx + cos * (rMid - 18f);
                float yIn = cy + sin * (rMid - 18f);
                float xOut = cx + cos * (rMid + 18f);
                float yOut = cy + sin * (rMid + 18f);

                sp.setStrokeWidth(2.6f);
                sp.setColor(Color.argb(170, 14, 18, 26));
                c.drawLine(xIn, yIn, xOut, yOut, sp);

                sp.setStrokeWidth(1.2f);
                sp.setColor(Color.argb(125, 185, 200, 215));
                c.drawLine(xIn + 1.5f, yIn - 1.5f, xOut + 1.5f, yOut - 1.5f, sp);
            }

            // 5. Asymmetrical docking tips (hammerhead right tip, spiked forked left tip)
            p.setShader(new LinearGradient(-70f, -90f, 80f, -20f,
                    Color.parseColor("#7D8C9B"), Color.parseColor("#212933"), Shader.TileMode.CLAMP));
            // Left forked prow
            Path leftTip = new Path();
            leftTip.moveTo(-82f, -36f);
            leftTip.lineTo(-68f, -92f);
            leftTip.lineTo(-56f, -64f);
            leftTip.lineTo(-42f, -80f);
            leftTip.lineTo(-42f, -44f);
            leftTip.close();
            c.drawPath(leftTip, p);
            // Right blunt hammerhead prow
            c.drawOval(new RectF(42f, -68f, 84f, -34f), p);
            p.setShader(null);

            // 6. Central raised circular ribbed command dome hub at the bottom curve
            float hubX = 0f, hubY = 82f;
            p.setColor(Color.parseColor("#18202A"));
            c.drawCircle(hubX, hubY, 27f, p);
            p.setShader(new RadialGradient(hubX - 7f, hubY - 7f, 26f,
                    new int[]{Color.parseColor("#9FB0C2"), Color.parseColor("#475569"), Color.parseColor("#161D27")},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(hubX, hubY, 22f, p);
            p.setShader(null);
            sp.setStrokeWidth(2f);
            sp.setColor(Color.parseColor("#CBD5E1"));
            c.drawCircle(hubX, hubY, 13f, sp);
        }

        // ---------------------------------------------------------------------
        // 7: GIGER BIOMECH LEVIATHAN (Reference 5 — Horizontal Dreadnought)
        // ---------------------------------------------------------------------
        private void bakeBiomechLeviathan(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Main elongated biomechanical hull silhouette (facing RIGHT +X)
            Path body = new Path();
            body.moveTo(106f, 2f); // xenomorph domed nose
            body.cubicTo(104f, -34f, 65f, -46f, 15f, -44f); // smooth cranial dome + dorsal spine
            body.cubicTo(-45f, -42f, -88f, -26f, -106f, -6f); // tapered biomechanical tail
            body.cubicTo(-95f, 28f, -55f, 44f, 5f, 36f); // deep ribbed ventral thorax
            body.cubicTo(55f, 30f, 85f, 24f, 102f, 20f); // curved lower mandible keel
            body.quadTo(92f, 12f, 106f, 2f);
            body.close();

            // Base obsidian-bronze metallic gradient
            p.setShader(new LinearGradient(0, -46f, 0, 44f,
                    new int[]{Color.parseColor("#6E6A63"), Color.parseColor("#1E1D21"), Color.parseColor("#3E3228"), Color.parseColor("#0E0E12")},
                    new float[]{0f, 0.35f, 0.68f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(body, p);
            p.setShader(null);

            c.save();
            c.clipPath(body);

            // 18 Curved metallic-bone ventral thoracic ribs
            for (int i = 0; i < 18; i++) {
                float rx = -88f + i * 9.5f;
                sp.setStrokeWidth(4.5f);
                sp.setColor(Color.argb(200, 12, 11, 14));
                c.drawArc(new RectF(rx - 12f, -12f, rx + 12f, 46f), -70, 155, false, sp);

                sp.setStrokeWidth(2.1f);
                sp.setColor(i % 2 == 0 ? Color.parseColor("#9E8A74") : Color.parseColor("#5C5652"));
                c.drawArc(new RectF(rx - 10f, -10f, rx + 10f, 44f), -65, 145, false, sp);
            }

            // Sweeping fluted dorsal pipes running from brow to tail
            for (int k = 0; k < 4; k++) {
                float dy = -34f + k * 6.5f;
                sp.setStrokeWidth(2.6f);
                sp.setColor(k == 0 ? Color.parseColor("#B5AEA6") : Color.parseColor("#3F3D42"));
                Path pipe = new Path();
                pipe.moveTo(86f, dy + 8f);
                pipe.quadTo(10f, dy - 6f, -94f, dy + 12f);
                c.drawPath(pipe, sp);
            }

            // Glossy curved specular highlight on xenomorph cranial prow
            p.setShader(new RadialGradient(82f, -20f, 34f,
                    new int[]{Color.argb(220, 215, 222, 235), Color.argb(60, 70, 75, 90), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            c.drawOval(new RectF(46f, -38f, 102f, -6f), p);
            p.setShader(null);

            // Recessed sinister amber biomechanical eye-cavity
            p.setColor(Color.parseColor("#FF7A18"));
            c.drawOval(new RectF(74f, -4f, 88f, 5f), p);
            p.setColor(Color.parseColor("#FEF08A"));
            c.drawCircle(82f, 0f, 2.5f, p);
            c.restore();

            // Spiked biomechanical tail vanes
            sp.setStrokeWidth(3.2f);
            sp.setColor(Color.parseColor("#78716C"));
            c.drawLine(-84f, -6f, -114f, -14f, sp);
            c.drawLine(-86f, 4f, -116f, 8f, sp);
            c.drawLine(-82f, 14f, -110f, 22f, sp);
        }

        // ---------------------------------------------------------------------
        // 8: ABYSSAL SENTINEL HORROR HEAD (Reference 6 — Tentacles drawn live)
        // ---------------------------------------------------------------------
        private void bakeSentinelHead(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Elongated dark chrome biomechanical cranium facing UP (-Y)
            Path skull = new Path();
            skull.moveTo(0, -86f);
            skull.cubicTo(38f, -84f, 44f, -30f, 34f, 28f);
            skull.cubicTo(26f, 62f, -26f, 62f, -34f, 28f);
            skull.cubicTo(-44f, -30f, -38f, -84f, 0, -86f);
            skull.close();

            p.setShader(new LinearGradient(-40f, -80f, 42f, 50f,
                    new int[]{Color.parseColor("#CBD5E1"), Color.parseColor("#475569"), Color.parseColor("#0F172A"), Color.parseColor("#334155")},
                    new float[]{0f, 0.32f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(skull, p);
            p.setShader(null);

            // Ribbed biomechanical ridges down the cranium
            sp.setStrokeWidth(2.2f);
            for (int i = -2; i <= 2; i++) {
                float y = -10f + i * 13f;
                sp.setColor(Color.argb(165, 15, 23, 42));
                c.drawArc(new RectF(-34f, y - 8f, 34f, y + 10f), 20, 140, false, sp);
            }

            // Glossy wet-chrome forehead specular reflection
            p.setColor(Color.argb(195, 241, 245, 249));
            c.drawOval(new RectF(-22f, -76f, 4f, -34f), p);

            // Terrifying recessed dark metallic maw & sharp chrome teeth grill
            RectF maw = new RectF(-19f, -56f, 19f, -16f);
            p.setColor(Color.parseColor("#05070B"));
            c.drawRoundRect(maw, 8f, 8f, p);

            sp.setColor(Color.parseColor("#CBD5E1"));
            sp.setStrokeWidth(1.6f);
            for (int i = -3; i <= 3; i++) {
                float tx = i * 4.5f;
                c.drawLine(tx, -54f, tx, -44f, sp);
                c.drawLine(tx, -28f, tx, -18f, sp);
            }
            sp.setColor(Color.parseColor("#64748B"));
            sp.setStrokeWidth(2.0f);
            c.drawRoundRect(maw, 8f, 8f, sp);
        }

        // ---------------------------------------------------------------------
        // 9: TR-3B STEALTH TRIANGLE (3D Faceted Carbon Composite)
        // ---------------------------------------------------------------------
        private void bakeStealthTriangle(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // 3 Distinct 3D bevel facets meeting at central apex (0, 8)
            // Left lit carbon facet
            Path left = new Path();
            left.moveTo(0, -98f);
            left.lineTo(-96f, 76f);
            left.lineTo(0, 12f);
            left.close();
            p.setShader(new LinearGradient(-96f, -98f, 0, 76f,
                    Color.parseColor("#475569"), Color.parseColor("#1E293B"), Shader.TileMode.CLAMP));
            c.drawPath(left, p);

            // Right shadowed stealth facet
            Path right = new Path();
            right.moveTo(0, -98f);
            right.lineTo(96f, 76f);
            right.lineTo(0, 12f);
            right.close();
            p.setShader(new LinearGradient(0, -98f, 96f, 76f,
                    Color.parseColor("#1E293B"), Color.parseColor("#0B0F19"), Shader.TileMode.CLAMP));
            c.drawPath(right, p);

            // Rear bevel facet
            Path rear = new Path();
            rear.moveTo(-96f, 76f);
            rear.lineTo(96f, 76f);
            rear.lineTo(0, 12f);
            rear.close();
            p.setShader(new LinearGradient(0, 12f, 0, 76f,
                    Color.parseColor("#141B29"), Color.parseColor("#2B3648"), Shader.TileMode.CLAMP));
            c.drawPath(rear, p);
            p.setShader(null);

            // Crisp stealth bevel lines
            sp.setColor(Color.parseColor("#64748B"));
            sp.setStrokeWidth(2.0f);
            c.drawLine(0, -98f, -96f, 76f, sp);
            c.drawLine(0, -98f, 96f, 76f, sp);
            c.drawLine(-96f, 76f, 96f, 76f, sp);
            sp.setColor(Color.argb(160, 148, 163, 184));
            c.drawLine(0, -98f, 0, 12f, sp);
            c.drawLine(-96f, 76f, 0, 12f, sp);
            c.drawLine(96f, 76f, 0, 12f, sp);

            // Recessed corner thruster rings & central accelerator housing
            float[][] wells = {{0, -68f}, {66f, 54f}, {-66f, 54f}};
            for (float[] w : wells) {
                p.setColor(Color.parseColor("#090D14"));
                c.drawCircle(w[0], w[1], 14f, p);
                sp.setColor(Color.parseColor("#38BDF8"));
                sp.setStrokeWidth(2.2f);
                c.drawCircle(w[0], w[1], 11f, sp);
            }
            p.setColor(Color.parseColor("#090D14"));
            c.drawCircle(0, 12f, 22f, p);
            sp.setColor(Color.parseColor("#67E8F9"));
            sp.setStrokeWidth(2.5f);
            c.drawCircle(0, 12f, 18f, sp);
        }

        // ---------------------------------------------------------------------
        // 10: IMPERIAL STAR-WEDGE DESTROYER (3D Terraced Warship)
        // ---------------------------------------------------------------------
        private void bakeStarDestroyer(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Left lit hull half
            Path leftHull = new Path();
            leftHull.moveTo(0, -108f);
            leftHull.lineTo(-78f, 84f);
            leftHull.lineTo(0, 92f);
            leftHull.close();
            p.setShader(new LinearGradient(-78f, -40f, 0, 90f,
                    Color.parseColor("#CBD5E1"), Color.parseColor("#64748B"), Shader.TileMode.CLAMP));
            c.drawPath(leftHull, p);

            // Right shadowed hull half
            Path rightHull = new Path();
            rightHull.moveTo(0, -108f);
            rightHull.lineTo(78f, 84f);
            rightHull.lineTo(0, 92f);
            rightHull.close();
            p.setShader(new LinearGradient(0, -40f, 78f, 90f,
                    Color.parseColor("#475569"), Color.parseColor("#1E293B"), Shader.TileMode.CLAMP));
            c.drawPath(rightHull, p);
            p.setShader(null);

            // Hull armour panel grid lines
            sp.setColor(Color.argb(95, 15, 23, 42));
            sp.setStrokeWidth(1.4f);
            for (int i = 1; i <= 5; i++) {
                float y = -80f + i * 30f;
                float hw = (y + 108f) * 0.38f;
                c.drawLine(-hw, y, hw, y, sp);
            }

            // Terraced Tier 2 superstructure (with drop shadow)
            Path tier2 = new Path();
            tier2.moveTo(0, -45f);
            tier2.lineTo(-46f, 74f);
            tier2.lineTo(46f, 74f);
            tier2.close();
            p.setColor(Color.argb(120, 9, 13, 22));
            c.save();
            c.translate(5f, 5f);
            c.drawPath(tier2, p);
            c.restore();

            p.setShader(new LinearGradient(-46f, 0, 46f, 0,
                    Color.parseColor("#B8C5D6"), Color.parseColor("#334155"), Shader.TileMode.CLAMP));
            c.drawPath(tier2, p);

            // Terraced Tier 3 upper deck
            Path tier3 = new Path();
            tier3.moveTo(0, 0f);
            tier3.lineTo(-28f, 66f);
            tier3.lineTo(28f, 66f);
            tier3.close();
            p.setShader(new LinearGradient(-28f, 0, 28f, 0,
                    Color.parseColor("#E2E8F0"), Color.parseColor("#475569"), Shader.TileMode.CLAMP));
            c.drawPath(tier3, p);
            p.setShader(null);

            // Iconic raised T-shaped command bridge tower + twin shield domes
            p.setColor(Color.parseColor("#1E293B"));
            c.drawRect(-30f, 52f, 30f, 65f, p);
            p.setColor(Color.parseColor("#CBD5E1"));
            c.drawRect(-28f, 54f, 28f, 58f, p);
            c.drawCircle(-14f, 52f, 5.5f, p);
            c.drawCircle(14f, 52f, 5.5f, p);

            // Tiny illuminated viewport dots along hull trenches
            p.setColor(Color.parseColor("#E0F2FE"));
            for (int i = 0; i < 6; i++) {
                float y = -20f + i * 16f;
                float hw = (y + 108f) * 0.36f;
                c.drawCircle(-hw + 3f, y, 1.5f, p);
                c.drawCircle(hw - 3f, y, 1.5f, p);
            }
        }

        // ---------------------------------------------------------------------
        // 11: OPHANIM ORBITAL RING-SHIP (3D Shaded Metallic Torus Station)
        // ---------------------------------------------------------------------
        private void bakeOrbitalRing(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // 4 Structural docking spokes connecting outer torus to central core
            p.setShader(new LinearGradient(-80f, -80f, 80f, 80f,
                    Color.parseColor("#94A3B8"), Color.parseColor("#1E293B"), Shader.TileMode.CLAMP));
            c.drawRect(-90f, -7f, 90f, 7f, p);
            c.drawRect(-7f, -90f, 7f, 90f, p);
            p.setShader(null);

            // 3D Torus Ring: outer shadow, mid metallic bevel, inner highlight
            sp.setStrokeWidth(30f);
            sp.setColor(Color.parseColor("#18202C"));
            c.drawCircle(0, 0, 86f, sp);

            sp.setStrokeWidth(24f);
            sp.setShader(new LinearGradient(-100f, -100f, 100f, 100f,
                    new int[]{Color.parseColor("#E2E8F0"), Color.parseColor("#64748B"), Color.parseColor("#1E293B"), Color.parseColor("#94A3B8")},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(0, 0, 86f, sp);
            sp.setShader(null);

            sp.setStrokeWidth(4.5f);
            sp.setColor(Color.argb(180, 248, 250, 252));
            c.drawCircle(0, 0, 92f, sp);

            // 12 Radial hull seam lines & warm amber deck lights around torus
            for (int i = 0; i < 12; i++) {
                double a = (i / 12.0) * Math.PI * 2.0;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                sp.setStrokeWidth(2f);
                sp.setColor(Color.parseColor("#0F172A"));
                c.drawLine(cos * 73f, sin * 73f, cos * 99f, sin * 99f, sp);

                p.setColor(Color.parseColor("#FBBF24"));
                c.drawCircle(cos * 86f, sin * 86f, 3.2f, p);
            }

            // Spherical central metallic reactor core
            p.setShader(new RadialGradient(-10f, -10f, 36f,
                    new int[]{Color.parseColor("#F8FAFC"), Color.parseColor("#64748B"), Color.parseColor("#0F172A")},
                    null, Shader.TileMode.CLAMP));
            c.drawCircle(0, 0, 32f, p);
            p.setShader(null);
        }

        // ---------------------------------------------------------------------
        // 12: DEEP-SPACE CARRIER CRUISER (Heavy Industrial Warship)
        // ---------------------------------------------------------------------
        private void bakeCarrierCruiser(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            // Side radiator wings
            p.setColor(Color.parseColor("#1E293B"));
            c.drawRect(-88f, 6f, 88f, 44f, p);
            sp.setColor(Color.parseColor("#475569"));
            sp.setStrokeWidth(2f);
            for (int i = -4; i <= 4; i++) {
                c.drawLine(i * 19f, 6f, i * 19f, 44f, sp);
            }

            // Twin heavy armoured side hangar pods
            p.setShader(new LinearGradient(-68f, -50f, 68f, 80f,
                    Color.parseColor("#94A3B8"), Color.parseColor("#1E293B"), Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(-64f, -44f, -24f, 86f), 8f, 8f, p);
            c.drawRoundRect(new RectF(24f, -44f, 64f, 86f), 8f, 8f, p);

            // Central forward armoured hull spine
            Path spine = new Path();
            spine.moveTo(0, -106f);
            spine.lineTo(28f, -64f);
            spine.lineTo(28f, 82f);
            spine.lineTo(-28f, 82f);
            spine.lineTo(-28f, -64f);
            spine.close();
            p.setShader(new LinearGradient(-28f, 0, 28f, 0,
                    new int[]{Color.parseColor("#CBD5E1"), Color.parseColor("#64748B"), Color.parseColor("#1E293B")},
                    null, Shader.TileMode.CLAMP));
            c.drawPath(spine, p);
            p.setShader(null);

            // Recessed dark hangar bay doors + illuminated yellow-blue runway lights
            p.setColor(Color.parseColor("#0B0F19"));
            c.drawRect(-52f, -34f, -36f, 18f, p);
            c.drawRect(36f, -34f, 52f, 18f, p);
            p.setColor(Color.parseColor("#38BDF8"));
            for (int i = 0; i < 4; i++) {
                float y = -26f + i * 12f;
                c.drawCircle(-44f, y, 2.2f, p);
                c.drawCircle(44f, y, 2.2f, p);
            }

            // Command bridge deck & sensor mast
            p.setColor(Color.parseColor("#334155"));
            c.drawRect(-20f, 16f, 20f, 44f, p);
            p.setColor(Color.parseColor("#FDE047"));
            c.drawRect(-14f, 20f, 14f, 24f, p);
        }

        // ---------------------------------------------------------------------
        // 13: CHROME UAP TIC-TAC CRUISER (Studio-Grade 3D Liquid Titanium)
        // ---------------------------------------------------------------------
        private void bakeChromeTicTac(Canvas c) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
            sp.setStyle(Paint.Style.STROKE);

            RectF hull = new RectF(-98f, -44f, 98f, 44f);

            // Multi-stop studio metallic horizon reflection gradient
            p.setShader(new LinearGradient(0, -44f, 0, 44f,
                    new int[]{
                            Color.parseColor("#FFFFFF"),
                            Color.parseColor("#CBD5E1"),
                            Color.parseColor("#334155"),
                            Color.parseColor("#0F172A"),
                            Color.parseColor("#475569"),
                            Color.parseColor("#93C5FD"),
                            Color.parseColor("#E2E8F0")
                    },
                    new float[]{0f, 0.20f, 0.44f, 0.54f, 0.72f, 0.88f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(hull, 44f, 44f, p);
            p.setShader(null);

            // Subtle longitudinal metallic sheen & end-cap curvature shadow
            p.setShader(new RadialGradient(-32f, -18f, 120f,
                    new int[]{Color.argb(120, 255, 255, 255), Color.TRANSPARENT, Color.argb(115, 10, 16, 28)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRoundRect(hull, 44f, 44f, p);
            p.setShader(null);

            // Precision chrome rim highlight
            sp.setStrokeWidth(2.2f);
            sp.setColor(Color.argb(215, 248, 250, 252));
            c.drawRoundRect(hull, 44f, 44f, sp);
        }
    }

    // =========================================================================
    // DATA MODELS
    // =========================================================================

    private static class SpaceEntity {
        int type;
        float x, y;
        float vx, vy;
        float size;
        float speed;
        float headingRad;
        float age;
        float phase;
        float animSpeed;
        float spinDeg;
        float spinSpeed;
        float swayAmp;
        float swayFreq;
        float swayOffset;
        int points;
    }

    private static class BurstEffect {
        int type;
        float x, y;
        float baseSize;
        float age;
        float duration;
        float headingRad;
        final List<DebrisParticle> particles = new ArrayList<>();
    }

    private static class DebrisParticle {
        float x, y;
        float vx, vy;
        float rot, vRot;
        float size;
        int color;
        int shape;
    }

    // =========================================================================
    // 13 DISTINCT REAL-TIME SYNTHESIZED DESTRUCTION SOUNDS (NO EXTERNAL FILES)
    // =========================================================================

    private static class UfoSoundEngine {
        private static final int SAMPLE_RATE = 22050;
        private final short[][] explosionBuffers = new short[TOTAL_ENTITY_TYPES][];
        private short[] breachBuffer;
        private final ExecutorService audioExecutor = Executors.newFixedThreadPool(4);
        private volatile boolean released = false;

        UfoSoundEngine() {
            audioExecutor.execute(() -> {
                for (int i = 0; i < TOTAL_ENTITY_TYPES; i++) {
                    if (released) return;
                    explosionBuffers[i] = synthesizeBreakSound(i);
                }
                if (!released) {
                    breachBuffer = synthesizeBreachSound();
                }
            });
        }

        void playExplosion(int type) {
            if (released || type < 0 || type >= TOTAL_ENTITY_TYPES) return;
            final short[] pcm = explosionBuffers[type];
            if (pcm == null) return;
            playBuffer(pcm, 0.88f);
        }

        void playBreach() {
            if (released || breachBuffer == null) return;
            playBuffer(breachBuffer, 0.96f);
        }

        private void playBuffer(final short[] pcm, final float volume) {
            if (released) return;
            audioExecutor.execute(() -> {
                AudioTrack track = null;
                try {
                    int byteLen = pcm.length * 2;
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build())
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build())
                            .setBufferSizeInBytes(Math.max(byteLen, AudioTrack.getMinBufferSize(
                                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)))
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build();

                    track.write(pcm, 0, pcm.length);
                    track.setVolume(volume);
                    track.play();
                    long ms = (long) ((pcm.length / (float) SAMPLE_RATE) * 1000f) + 60;
                    Thread.sleep(ms);
                } catch (Throwable ignored) {
                } finally {
                    if (track != null) {
                        try {
                            track.stop();
                            track.release();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
        }

        void release() {
            released = true;
            audioExecutor.shutdownNow();
        }

        private short[] synthesizeBreakSound(int type) {
            float dur = (type == TYPE_STAR_DESTROYER || type == TYPE_JUGGERNAUT || type == TYPE_MAGMA_ASTEROID) ? 0.68f : 0.50f;
            int total = (int) (SAMPLE_RATE * dur);
            short[] buf = new short[total];
            Random rnd = new Random(500L + type * 131L);
            double p1 = 0, p2 = 0, lp = 0;

            for (int i = 0; i < total; i++) {
                double t = i / (double) SAMPLE_RATE;
                double prog = i / (double) total;
                double env = Math.pow(1.0 - prog, 1.75);
                double white = rnd.nextDouble() * 2.0 - 1.0;
                lp = lp * 0.86 + white * 0.14;
                double s = 0.0;

                switch (type) {
                    case TYPE_IRON_ASTEROID: { // Heavy rocky crack + deep boulder crumble
                        double f = 95.0 * Math.exp(-prog * 3.0) + 28.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        double crunch = (prog < 0.12 ? white * 1.1 : lp * 0.85);
                        s = Math.sin(p1) * 0.65 + crunch * 0.7;
                        break;
                    }
                    case TYPE_MAGMA_ASTEROID: { // Tectonic lava blast + volcanic steam hiss
                        double f = 115.0 * Math.exp(-prog * 2.6) + 30.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        s = Math.sin(p1) * 0.7 + lp * 0.65 + white * 0.35 * (1.0 - prog * 0.6);
                        break;
                    }
                    case TYPE_CRYO_COMET: { // Sharp crystalline ice shatter + frost chimes
                        double f1 = 2150.0 - prog * 520.0;
                        double f2 = 3120.0 - prog * 780.0;
                        p1 += 2.0 * Math.PI * f1 / SAMPLE_RATE;
                        p2 += 2.0 * Math.PI * f2 / SAMPLE_RATE;
                        double crack = (prog < 0.08 ? white * 1.2 : white * 0.25);
                        s = crack + (Math.sin(p1) * 0.45 + Math.sin(p2) * 0.35);
                        break;
                    }
                    case TYPE_IMPERIAL_SAUCER: { // Classic UFO turbine drop + plasma boom
                        double f = 820.0 * Math.exp(-prog * 3.6) + 65.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        s = Math.sin(p1) * 0.6 + lp * 0.65;
                        break;
                    }
                    case TYPE_JELLY_UFO: { // Ethereal bio-chime + pressurized organic pop
                        double f = 460.0 * Math.exp(-prog * 2.8) + 95.0 + Math.sin(t * 55.0) * 40.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        double pop = (prog < 0.06) ? Math.sin(prog * 110.0) * 1.0 : 0.0;
                        s = pop + Math.sin(p1) * 0.65 + lp * 0.3;
                        break;
                    }
                    case TYPE_JUGGERNAUT: { // Massive metallic hull groan + deep detonation
                        double f1 = 155.0 * Math.exp(-prog * 2.2) + 32.0;
                        double f2 = 230.0 * Math.exp(-prog * 2.9) + 48.0;
                        p1 += 2.0 * Math.PI * f1 / SAMPLE_RATE;
                        p2 += 2.0 * Math.PI * f2 / SAMPLE_RATE;
                        s = Math.tanh((Math.sin(p1) + Math.sin(p2) * 0.6) * 1.6) * 0.6 + lp * 0.75;
                        break;
                    }
                    case TYPE_BIOMECH_LEVIATHAN: { // Alien chitin fracture + terrifying bio-screech
                        double f = 1080.0 * Math.exp(-prog * 3.9) + 115.0 + Math.sin(t * 78.0) * 70.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        s = Math.tanh(Math.sin(p1) * 2.0) * 0.55 + lp * 0.55;
                        break;
                    }
                    case TYPE_SENTINEL_HORROR: { // Mechanical screech + metallic tentacle arc
                        double f = 1480.0 * Math.exp(-prog * 4.5) + 130.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        double arc = ((i / 110) % 2 == 0 ? 1.0 : -1.0) * white * 0.5;
                        s = Math.sin(p1) * 0.55 + arc * 0.55;
                        break;
                    }
                    case TYPE_STEALTH_TRIANGLE: { // EMP snap + bass cutoff
                        double f = 560.0 * Math.exp(-prog * 5.2) + 52.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        double zap = ((i / 140) % 2 == 0 ? 1.0 : -1.0) * white * 0.55;
                        s = Math.sin(p1) * 0.6 + zap * 0.6;
                        break;
                    }
                    case TYPE_STAR_DESTROYER: { // Cascading multi-stage warship explosions
                        double b2 = (prog > 0.22) ? Math.exp(-(prog - 0.22) * 5.0) : 0.0;
                        double b3 = (prog > 0.45) ? Math.exp(-(prog - 0.45) * 6.0) : 0.0;
                        double f = 80.0 * Math.exp(-prog * 2.4) + 26.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        s = Math.sin(p1) * 0.7 + lp * (0.8 + b2 * 0.75 + b3 * 0.6);
                        break;
                    }
                    case TYPE_ORBITAL_RING: { // Metallic gong chord shatter + blast
                        double f1 = 590.0 * (1.0 - prog * 0.22);
                        double f2 = 835.0 * (1.0 - prog * 0.28);
                        p1 += 2.0 * Math.PI * f1 / SAMPLE_RATE;
                        p2 += 2.0 * Math.PI * f2 / SAMPLE_RATE;
                        s = Math.sin(p1) * 0.5 + Math.sin(p2) * 0.45 + lp * 0.35;
                        break;
                    }
                    case TYPE_CARRIER_CRUISER: { // Reactor overload siren + fusion boom
                        double f = 135.0 * Math.exp(-prog * 2.9) + 34.0;
                        double siren = 510.0 + Math.sin(t * 46.0) * 130.0;
                        p1 += 2.0 * Math.PI * f / SAMPLE_RATE;
                        p2 += 2.0 * Math.PI * siren / SAMPLE_RATE;
                        s = Math.sin(p1) * 0.72 + Math.sin(p2) * 0.25 * (1.0 - prog) + lp * 0.72;
                        break;
                    }
                    case TYPE_CHROME_TICTAC:
                    default: { // Supersonic crack + shimmering titanium chime
                        double f1 = 1520.0 - prog * 400.0;
                        double f2 = 2280.0 - prog * 580.0;
                        p1 += 2.0 * Math.PI * f1 / SAMPLE_RATE;
                        p2 += 2.0 * Math.PI * f2 / SAMPLE_RATE;
                        double boom = (prog < 0.07) ? white * 1.25 : lp * 0.22;
                        s = boom + (Math.sin(p1) + Math.sin(p2) * 0.6) * 0.36;
                        break;
                    }
                }
                double out = Math.max(-0.98, Math.min(0.98, s * env));
                buf[i] = (short) (out * Short.MAX_VALUE);
            }
            return buf;
        }

        private short[] synthesizeBreachSound() {
            int total = (int) (SAMPLE_RATE * 0.55f);
            short[] buf = new short[total];
            double p = 0;
            for (int i = 0; i < total; i++) {
                double prog = i / (double) total;
                double f = 180.0 - prog * 125.0;
                p += 2.0 * Math.PI * f / SAMPLE_RATE;
                double env = Math.pow(1.0 - prog, 1.4);
                double w = Math.tanh(Math.sin(p) * 2.5) * 0.75 * env;
                buf[i] = (short) (Math.max(-0.98, Math.min(0.98, w)) * Short.MAX_VALUE);
            }
            return buf;
        }
    }
}