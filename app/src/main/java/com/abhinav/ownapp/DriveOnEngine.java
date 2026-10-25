package com.abhinav.ownapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

@SuppressWarnings("all")
@SuppressLint("ViewConstructor")
public class DriveOnEngine extends View implements Choreographer.FrameCallback {

    public interface GameListener { void onScoreUpdated(int score); void onGameStarted(); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ambientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tempRect = new RectF(), minimapRect = new RectF();
    private final Path minimapClipPath = new Path();

    private final float dp;
    private final int themeState;
    private final boolean isSunnyDay, isMoonlightMode, isAmoledStarMode;
    private int hudPillBgColor, hudBoxBgColor, hudTitleColor, hudSubTextColor;

    private float screenW, screenH;
    private int safeInsetLeft = 0, safeInsetRight = 0;

    public final DriveOnCar car = new DriveOnCar();
    public final DriveOnWorld world;
    public final DriveOnAudio audio;

    private float camX = 0f, camY = 0f, camZoom = 1.0f, starPhase = 0f;
    private boolean playing = false, paused = false;
    private int handlingMode = 1, score = 0;
    private float driftScoreCarry = 0f;

    private final RectF rightCarPillRect = new RectF(), rightHornPillRect = new RectF();
    private final RectF gasPedalRect = new RectF(), brakePedalRect = new RectF(), nitroBtnRect = new RectF();
    private final RectF gearDownRect = new RectF(), gearUpRect = new RectF();
    private final RectF dpadLeftRect = new RectF(), dpadRightRect = new RectF(), engineBtnRect = new RectF();

    private boolean leftPressed = false, rightPressed = false;
    private GameListener listener;

    public DriveOnEngine(Context context, int themeState) {
        super(context);
        this.themeState = themeState;
        this.isSunnyDay = (themeState == 0); this.isMoonlightMode = (themeState == 1); this.isAmoledStarMode = (themeState == 2);
        this.dp = context.getResources().getDisplayMetrics().density;
        this.audio = new DriveOnAudio(context);
        this.world = new DriveOnWorld(dp, themeState);

        if (isSunnyDay) {
            hudPillBgColor = Color.parseColor("#F4F7F2"); hudBoxBgColor = Color.parseColor("#FFFFFF");
            hudTitleColor = Color.parseColor("#1B3022"); hudSubTextColor = Color.parseColor("#4A6B53");
        } else if (isMoonlightMode) {
            hudPillBgColor = Color.parseColor("#162E4F"); hudBoxBgColor = Color.parseColor("#BAE6FD");
            hudTitleColor = Color.parseColor("#F0F9FF"); hudSubTextColor = Color.parseColor("#93C5FD");
        } else {
            hudPillBgColor = Color.parseColor("#111624"); hudBoxBgColor = Color.parseColor("#38BDF8");
            hudTitleColor = Color.parseColor("#F8FAFC"); hudSubTextColor = Color.parseColor("#94A3B8");
        }

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND); strokePaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); textPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD)); subTextPaint.setTextAlign(Paint.Align.LEFT);
    }

    public void setSafeInsets(int left, int right) { this.safeInsetLeft = left; this.safeInsetRight = right; invalidate(); }
    public void setGameListener(GameListener listener) { this.listener = listener; }
    public void setVibrationEnabled(boolean enabled) { audio.setVibrationEnabled(enabled); }
    public void setMusicEnabled(boolean enabled) { audio.setSoundEnabled(enabled); }
    public void setHandlingMode(int mode) { this.handlingMode = mode; }
    public boolean isPlaying() { return playing; }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenW = w; screenH = h;
        if (oldw == 0 || oldh == 0) resetGame();
    }

    public void resetGame() {
        score = 0; driftScoreCarry = 0f;
        car.reset(0f, 0f); world.generateOpenWorld();
        camX = car.x; camY = car.y; camZoom = 1.0f;
        leftPressed = false; rightPressed = false; playing = false; paused = false;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
        invalidate();
    }

    public void pauseGame() {
        paused = true; leftPressed = false; rightPressed = false;
        car.targetSteerInput = 0f; car.steerInput = 0f;
        car.gasPressed = false; car.brakePressed = false; car.boostPressed = false;
    }

    public void resumeGame() {
        paused = false;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (paused) return true;
        int action = event.getActionMasked(), index = event.getActionIndex();
        float tx = event.getX(index), ty = event.getY(index);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (engineBtnRect.contains(tx, ty)) {
                car.isEngineOn = !car.isEngineOn;
                if (car.isEngineOn) audio.startEngineIgnition(car.carModel); else audio.stopEngineLoop();
                return true;
            }
            if (rightCarPillRect.contains(tx, ty)) {
                car.cycleCarModel(); audio.vibrateTap();
                if (car.isEngineOn) audio.switchVehicleImmediate(car.carModel); else audio.playClickBeep();
                return true;
            }
            if (rightHornPillRect.contains(tx, ty)) { audio.playCarHorn(car.carModel); world.triggerHornWave(car.x, car.y); return true; }
            if (gearDownRect.contains(tx, ty)) { car.shiftGearDown(audio); return true; }
            if (gearUpRect.contains(tx, ty)) { car.shiftGearUp(audio); return true; }

            if (!playing) {
                playing = true;
                if (car.isEngineOn) audio.startEngineIgnition(car.carModel);
                if (listener != null) listener.onGameStarted();
            }
        }

        boolean l = false, r = false, gas = false, brake = false, boost = false;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == index) continue;
            if (action == MotionEvent.ACTION_CANCEL) break;
            float px = event.getX(i), py = event.getY(i);
            if (dpadLeftRect.contains(px, py)) l = true;
            else if (dpadRightRect.contains(px, py)) r = true;
            else if (gasPedalRect.contains(px, py)) gas = true;
            else if (brakePedalRect.contains(px, py)) brake = true;
            else if (nitroBtnRect.contains(px, py)) { boost = true; gas = true; }
        }

        leftPressed = l; rightPressed = r;
        car.gasPressed = gas; car.brakePressed = brake; car.boostPressed = boost;
        car.targetSteerInput = (leftPressed && !rightPressed) ? -1f : ((rightPressed && !leftPressed) ? 1f : 0f);
        return true;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        Choreographer.getInstance().removeFrameCallback(this);
        if (paused) return;
        starPhase += 0.05f;

        if (playing) {
            boolean onRoad = world.isPointOnRoad(car.x, car.y);
            car.updatePhysics(dp, onRoad, handlingMode, audio, world);
            int pts = world.updateAndResolveCollisions(car, audio);
            if (car.isDrifting) {
                driftScoreCarry += 0.35f;
                if (driftScoreCarry >= 1f) { int dPts = (int) driftScoreCarry; driftScoreCarry -= dPts; pts += dPts; }
            }
            if (pts > 0) { score += pts; if (listener != null) listener.onScoreUpdated(score); }
        }

        float dx = world.getShortestDist(camX, car.x), dy = world.getShortestDist(camY, car.y);
        camX = world.wrapMapCoord(camX + (dx + car.vx * 12f) * 0.12f);
        camY = world.wrapMapCoord(camY + (dy + car.vy * 12f) * 0.12f);

        float targetZoom = 1.0f - Math.min(0.24f, (car.speedKmh / 240f) * 0.22f);
        camZoom += (targetZoom - camZoom) * 0.08f;

        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (screenW <= 0 || screenH <= 0) return;
        float viewW = (screenW / camZoom) * 1.5f, viewH = (screenH / camZoom) * 1.5f;

        canvas.save();
        canvas.translate(screenW * 0.5f, screenH * 0.5f);
        canvas.scale(camZoom, camZoom);
        canvas.rotate(-(float) Math.toDegrees(car.angle) - 90f);
        canvas.translate(-camX, -camY);

        world.drawWorldGroundAndRoads(canvas, paint, strokePaint, camX, camY, viewW, viewH, starPhase);
        car.drawSkidMarksAndSmoke(world, canvas, paint, camX, camY, viewW, viewH, dp);

        if (car.carModel == DriveOnCar.MODEL_HELICOPTER) {
            world.drawBuildingsAndTrees(canvas, paint, strokePaint, camX, camY, viewW, viewH, starPhase);
            world.drawPickupsAndWaves(canvas, paint, strokePaint, camX, camY, viewW, viewH, starPhase, car.carModel);
            car.drawCar(canvas, paint, strokePaint, camX, camY, dp, themeState, starPhase);
        } else {
            car.drawCar(canvas, paint, strokePaint, camX, camY, dp, themeState, starPhase);
            world.drawBuildingsAndTrees(canvas, paint, strokePaint, camX, camY, viewW, viewH, starPhase);
            world.drawPickupsAndWaves(canvas, paint, strokePaint, camX, camY, viewW, viewH, starPhase, car.carModel);
        }

        canvas.restore();
        drawAtmosphericLighting(canvas);
        drawLeftMinimap(canvas);
        drawRightSmartPills(canvas);
        drawSpeedometerGearShifterAndPedals(canvas);
        drawDPadControls(canvas);
    }

    private void drawAtmosphericLighting(Canvas canvas) {
        if (isSunnyDay) {
            ambientPaint.setShader(new RadialGradient(screenW * 0.2f, screenH * 0.15f, screenW * 0.55f,
                    new int[]{ Color.parseColor("#22FFFFFF"), Color.parseColor("#10FEF08A"), Color.TRANSPARENT },
                    new float[]{ 0f, 0.45f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenW, screenH, ambientPaint); ambientPaint.setShader(null);
        } else if (isMoonlightMode) {
            ambientPaint.setShader(new RadialGradient(screenW * 0.78f, screenH * 0.18f, screenW * 0.65f,
                    new int[]{ Color.parseColor("#307DD3FC"), Color.parseColor("#1638BDF8"), Color.parseColor("#28020617") },
                    new float[]{ 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenW, screenH, ambientPaint); ambientPaint.setShader(null);
        }
    }

    private void drawLeftMinimap(Canvas canvas) {
        float mapSize = 112f * dp, halfMap = mapSize * 0.5f, cornerR = 20f * dp, margin = 16f * dp;
        float left = margin + safeInsetLeft, top = margin, mapCX = left + halfMap, mapCY = top + halfMap;
        minimapRect.set(left, top, left + mapSize, top + mapSize);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#28000000"));
        tempRect.set(left, top + 2.5f * dp, left + mapSize, top + mapSize + 2.5f * dp);
        canvas.drawRoundRect(tempRect, cornerR, cornerR, paint);

        paint.setColor(Color.parseColor("#DD0F172A"));
        canvas.drawRoundRect(minimapRect, cornerR, cornerR, paint);

        canvas.save();
        minimapClipPath.reset(); minimapClipPath.addRoundRect(minimapRect, cornerR, cornerR, Path.Direction.CW);
        canvas.clipPath(minimapClipPath);

        canvas.translate(mapCX, mapCY);
        canvas.rotate(-(float) Math.toDegrees(car.angle) - 90f);

        float radarWorldRadius = world.gridStep * 3.2f, scale = halfMap / radarWorldRadius, rSpan = halfMap * 1.45f;
        strokePaint.setColor(Color.parseColor("#4494A3B8")); strokePaint.setStrokeWidth(1.4f * dp);

        for (float gx = -world.WORLD_HALF; gx < world.WORLD_HALF; gx += world.gridStep) {
            float ro = world.getShortestDist(car.x, gx) * scale;
            if (Math.abs(ro) < rSpan) canvas.drawLine(ro, -rSpan, ro, rSpan, strokePaint);
        }
        for (float gy = -world.WORLD_HALF; gy < world.WORLD_HALF; gy += world.gridStep) {
            float ro = world.getShortestDist(car.y, gy) * scale;
            if (Math.abs(ro) < rSpan) canvas.drawLine(-rSpan, ro, rSpan, ro, strokePaint);
        }

        // 1. Botanical Lotus Lakes on Minimap
        paint.setStyle(Paint.Style.FILL);
        for (DriveOnWorld.Lake l : world.lakes) {
            float dx = world.getShortestDist(car.x, l.cx) * scale, dy = world.getShortestDist(car.y, l.cy) * scale;
            if (Math.abs(dx) > rSpan || Math.abs(dy) > rSpan) continue;
            float lrx = l.rx * scale, lry = l.ry * scale;
            tempRect.set(dx - lrx, dy - lry, dx + lrx, dy + lry);
            paint.setColor(Color.parseColor("#C7C2B7")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
            paint.setColor(Color.parseColor("#0284C7")); canvas.drawOval(new RectF(dx - lrx * 0.78f, dy - lry * 0.58f, dx + lrx * 0.78f, dy + lry * 0.58f), paint);
            paint.setColor(Color.parseColor("#76C4E5")); canvas.drawOval(new RectF(dx - lrx * 0.50f, dy - lry * 0.36f, dx + lrx * 0.50f, dy + lry * 0.36f), paint);
            paint.setColor(Color.parseColor("#8BC34A")); canvas.drawCircle(dx - lrx * 0.58f, dy - lry * 0.58f, lrx * 0.28f, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(dx + lrx * 0.15f, dy - lry * 0.12f, 1.4f * dp, paint);
        }

        // 2. Buildings & Special Zones on Minimap
        for (DriveOnWorld.Building b : world.buildings) {
            float dx = world.getShortestDist(car.x, b.rect.centerX()) * scale, dy = world.getShortestDist(car.y, b.rect.centerY()) * scale;
            float sw = b.rect.width() * 0.5f * scale, sh = b.rect.height() * 0.5f * scale;
            if (Math.abs(dx) > rSpan + sw || Math.abs(dy) > rSpan + sh) continue;
            tempRect.set(dx - sw, dy - sh, dx + sw, dy + sh);

            switch (b.type) {
                case HOTEL_POOL:
                    // Mediterranean Luxury Resort & Lagoon Pool Radar Icon
                    paint.setColor(Color.parseColor("#C8B598"));
                    canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    // Grey flagstone patio & cyan lagoon pool with central island
                    paint.setColor(Color.parseColor("#B6BEBC"));
                    canvas.drawCircle(dx, dy + sh * 0.12f, sw * 0.56f, paint);
                    paint.setColor(Color.parseColor("#00B0FF"));
                    canvas.drawCircle(dx, dy + sh * 0.12f, sw * 0.40f, paint);
                    paint.setColor(Color.parseColor("#EAE3D2"));
                    canvas.drawCircle(dx + sw * 0.05f, dy + sh * 0.10f, sw * 0.16f, paint);
                    // Upper-left rectangular lap pool
                    paint.setColor(Color.parseColor("#29C5F6"));
                    canvas.drawRect(dx - sw * 0.65f, dy - sh * 0.60f, dx - sw * 0.38f, dy - sh * 0.44f, paint);
                    // Top & Right Terracotta Hotel Wings
                    paint.setColor(Color.parseColor("#B87044"));
                    canvas.drawRect(dx - sw * 0.50f, dy - sh * 0.92f, dx + sw * 0.88f, dy - sh * 0.66f, paint);
                    canvas.drawRect(dx + sw * 0.42f, dy - sh * 0.66f, dx + sw * 0.72f, dy + sh * 0.52f, paint);
                    break;

                case ASTEROID_CRATER:
                    // Giant 10x Area Barringer Asteroid Crater Radar Icon
                    paint.setColor(Color.parseColor("#9E5B36"));
                    canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#4A2518"));
                    canvas.drawOval(new RectF(dx - sw * 0.58f, dy - sh * 0.48f, dx + sw * 0.60f, dy + sh * 0.44f), paint);
                    paint.setColor(Color.parseColor("#A86640"));
                    canvas.drawOval(new RectF(dx - sw * 0.36f, dy - sh * 0.28f, dx + sw * 0.38f, dy + sh * 0.28f), paint);
                    paint.setColor(Color.parseColor("#E6D2BA"));
                    canvas.drawCircle(dx, dy, sw * 0.14f, paint);
                    paint.setColor(Color.parseColor("#F97316"));
                    canvas.drawCircle(dx + sw * 0.05f, dy, 1.8f * dp, paint);
                    break;

                case VOLCANO:
                    // Giant 10x Area Active Volcano & Molten Lava Caldera Radar Icon
                    paint.setColor(Color.parseColor("#14171F"));
                    canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#2E3545"));
                    canvas.drawOval(new RectF(dx - sw * 0.78f, dy - sh * 0.74f, dx + sw * 0.78f, dy + sh * 0.74f), paint);
                    // SE Breached Lava River on Minimap
                    strokePaint.setColor(Color.parseColor("#F97316"));
                    strokePaint.setStrokeWidth(2.2f * dp);
                    canvas.drawLine(dx, dy, dx + sw * 0.65f, dy + sh * 0.55f, strokePaint);
                    // Glowing Red/Orange/Yellow Molten Caldera Core
                    paint.setColor(Color.parseColor("#DC2626"));
                    canvas.drawOval(new RectF(dx - sw * 0.36f, dy - sh * 0.28f, dx + sw * 0.36f, dy + sh * 0.28f), paint);
                    paint.setColor(Color.parseColor("#F97316"));
                    canvas.drawOval(new RectF(dx - sw * 0.24f, dy - sh * 0.18f, dx + sw * 0.24f, dy + sh * 0.18f), paint);
                    paint.setColor(Color.parseColor("#FEF08A"));
                    canvas.drawCircle(dx, dy + sh * 0.03f, sw * 0.11f, paint);
                    // Upper-Left White/Grey Smoke Plume Dot
                    paint.setColor(Color.parseColor("#E2E8F0"));
                    canvas.drawCircle(dx - sw * 1.26f, dy - sh * 1.28f, sw * 0.14f, paint);
                    break;

                case FOREST:
                    paint.setColor(Color.parseColor("#15803D")); canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#84CC16"));
                    canvas.drawCircle(dx - sw * 0.35f, dy - sh * 0.35f, sw * 0.35f, paint);
                    canvas.drawCircle(dx + sw * 0.30f, dy + sh * 0.25f, sw * 0.32f, paint);
                    paint.setColor(Color.parseColor("#22C55E"));
                    canvas.drawCircle(dx + sw * 0.25f, dy - sh * 0.30f, sw * 0.28f, paint);
                    canvas.drawCircle(dx - sw * 0.25f, dy + sh * 0.30f, sw * 0.30f, paint);
                    break;
                case AMUSEMENT:
                    paint.setColor(Color.parseColor("#A3E635")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    paint.setColor(Color.parseColor("#F472B6")); canvas.drawCircle(dx, dy, sw * 0.42f, paint);
                    break;
                case HOSPITAL:
                    paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
                    paint.setColor(Color.parseColor("#EF4444"));
                    canvas.drawRect(dx - sw * 0.22f, dy - sh * 0.65f, dx + sw * 0.22f, dy + sh * 0.65f, paint);
                    canvas.drawRect(dx - sw * 0.65f, dy - sh * 0.22f, dx + sw * 0.65f, dy + sh * 0.22f, paint);
                    break;
                case POLICE:
                    paint.setColor(Color.parseColor("#2563EB")); canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
                    paint.setColor(Color.parseColor("#38BDF8")); canvas.drawCircle(dx, dy, sw * 0.38f, paint);
                    break;
                case AIRPORT:
                    paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    paint.setColor(Color.parseColor("#1E293B")); canvas.drawRect(dx - sw, dy - sh * 0.22f, dx + sw, dy + sh * 0.12f, paint);
                    paint.setColor(Color.parseColor("#F5E6CC")); canvas.drawCircle(dx, dy + sh * 0.52f, sw * 0.34f, paint);
                    paint.setColor(Color.parseColor("#38BDF8")); canvas.drawCircle(dx, dy + sh * 0.52f, sw * 0.18f, paint);
                    canvas.drawRect(dx - sw * 0.18f, dy - sh * 0.78f, dx + sw * 0.18f, dy - sh * 0.42f, paint);
                    break;
                case NUCLEAR:
                    // Coastal Nuclear Power Plant Radar Icon (Cooling Towers, Reactor Dome, Sea & Radiation Badge)
                    paint.setColor(Color.parseColor("#558B2F"));
                    canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    // Right coastal sea strip
                    paint.setColor(Color.parseColor("#0284C7"));
                    canvas.drawRect(dx + sw * 0.72f, dy - sh, dx + sw, dy + sh, paint);
                    // Middle-left turbine hall & center reactor base
                    paint.setColor(Color.parseColor("#64748B"));
                    canvas.drawRect(dx - sw * 0.78f, dy - sh * 0.20f, dx - sw * 0.35f, dy + sh * 0.18f, paint);
                    canvas.drawRect(dx - sw * 0.24f, dy - sh * 0.22f, dx + sw * 0.20f, dy + sh * 0.20f, paint);
                    // White reactor containment dome
                    paint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawCircle(dx - sw * 0.02f, dy - sh * 0.02f, sw * 0.18f, paint);
                    // Twin cooling towers + white steam dots (top-right)
                    paint.setColor(Color.parseColor("#CBD5E1"));
                    canvas.drawCircle(dx + sw * 0.28f, dy - sh * 0.48f, sw * 0.18f, paint);
                    canvas.drawCircle(dx + sw * 0.60f, dy - sh * 0.44f, sw * 0.16f, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(dx + sw * 0.32f, dy - sh * 0.56f, sw * 0.10f, paint);
                    canvas.drawCircle(dx + sw * 0.64f, dy - sh * 0.52f, sw * 0.09f, paint);
                    // Yellow radwaste badge (bottom-right)
                    paint.setColor(Color.parseColor("#FACC15"));
                    canvas.drawCircle(dx + sw * 0.44f, dy + sh * 0.44f, 1.8f * dp, paint);
                    break;
                case CRICKET_STADIUM:
                    // Pleated White Tensile Roof & Emerald Cricket Turf Radar Icon
                    paint.setColor(Color.parseColor("#D6D3CD"));
                    canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    paint.setColor(Color.parseColor("#334155"));
                    canvas.drawOval(new RectF(dx - sw * 0.88f, dy - sh * 0.90f, dx + sw * 0.88f, dy + sh * 0.90f), paint);
                    paint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawOval(new RectF(dx - sw * 0.82f, dy - sh * 0.84f, dx + sw * 0.82f, dy + sh * 0.84f), paint);
                    paint.setColor(Color.parseColor("#D99B26"));
                    canvas.drawOval(new RectF(dx - sw * 0.44f, dy - sh * 0.48f, dx + sw * 0.44f, dy + sh * 0.48f), paint);
                    paint.setColor(Color.parseColor("#4C8535"));
                    canvas.drawOval(new RectF(dx - sw * 0.30f, dy - sh * 0.34f, dx + sw * 0.30f, dy + sh * 0.34f), paint);
                    paint.setColor(Color.parseColor("#E2C799"));
                    canvas.drawRect(dx - 0.8f * dp, dy - sh * 0.14f, dx + 0.8f * dp, dy + sh * 0.14f, paint);
                    break;

                case HIMALAYA_MOUNTAINS:
                    // Cartographic Shaded-Relief Topographic Map (Image 1 Reference — Minimap Only)
                    canvas.save();
                    minimapClipPath.reset();
                    minimapClipPath.addRoundRect(tempRect, 4f * dp, 4f * dp, Path.Direction.CW);
                    canvas.clipPath(minimapClipPath);

                    // 1. Warm Parchment / Sand Lowland Plains Base (Top-Left & West of Image 1)
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#F2E4C8"));
                    canvas.drawRect(tempRect, paint);

                    // Soft sandy contour washes on the western plain
                    paint.setColor(Color.parseColor("#E5CFA6"));
                    canvas.drawOval(new RectF(dx - sw * 0.85f, dy - sh * 0.95f, dx + sw * 0.25f, dy - sh * 0.15f), paint);
                    canvas.drawOval(new RectF(dx - sw * 0.88f, dy - sh * 0.25f, dx - sw * 0.20f, dy + sh * 0.85f), paint);

                    // 2. Far-Left Pale Slate-Blue Coastal Shoreline & Islands (Left Edge of Image 1)
                    minimapClipPath.reset();
                    minimapClipPath.moveTo(dx - sw, dy - sh);
                    minimapClipPath.lineTo(dx - sw * 0.72f, dy - sh);
                    minimapClipPath.quadTo(dx - sw * 0.86f, dy - sh * 0.45f, dx - sw * 0.78f, dy + sh * 0.10f);
                    minimapClipPath.quadTo(dx - sw * 0.88f, dy + sh * 0.55f, dx - sw * 0.68f, dy + sh);
                    minimapClipPath.lineTo(dx - sw, dy + sh);
                    minimapClipPath.close();
                    paint.setColor(Color.parseColor("#D4E0E3"));
                    canvas.drawPath(minimapClipPath, paint);

                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setColor(Color.parseColor("#8FA6B0"));
                    strokePaint.setStrokeWidth(0.7f * dp);
                    canvas.drawPath(minimapClipPath, strokePaint);

                    // Small coastal barrier islands in top-left corner
                    paint.setColor(Color.parseColor("#E5CFA6"));
                    canvas.drawOval(new RectF(dx - sw * 0.90f, dy - sh * 0.86f, dx - sw * 0.76f, dy - sh * 0.70f), paint);
                    canvas.drawOval(new RectF(dx - sw * 0.96f, dy - sh * 0.66f, dx - sw * 0.84f, dy - sh * 0.54f), paint);

                    // 3. Lush Olive Meadows & Deep Pine Forest Valley Gorges (Bottom-Left, Center & Right of Image 1)
                    paint.setColor(Color.parseColor("#9AB56F")); // Soft olive valley meadows
                    canvas.drawOval(new RectF(dx - sw * 0.72f, dy + sh * 0.02f, dx + sw * 0.38f, dy + sh * 0.98f), paint);
                    canvas.drawOval(new RectF(dx + sw * 0.20f, dy - sh * 0.55f, dx + sw * 1.05f, dy + sh * 0.98f), paint);

                    paint.setColor(Color.parseColor("#6E944E")); // Mid-tone green valley basins
                    canvas.drawOval(new RectF(dx - sw * 0.52f, dy + sh * 0.15f, dx + sw * 0.25f, dy + sh * 0.88f), paint);
                    canvas.drawOval(new RectF(dx + sw * 0.35f, dy + sh * 0.35f, dx + sw * 0.98f, dy + sh * 0.98f), paint);

                    // Deep Dark-Green Forested Gorges Nestled Between the Ridges
                    paint.setColor(Color.parseColor("#244F32"));
                    canvas.drawOval(new RectF(dx - sw * 0.08f, dy - sh * 0.08f, dx + sw * 0.65f, dy + sh * 0.28f), paint);
                    canvas.drawOval(new RectF(dx + sw * 0.38f, dy - sh * 0.38f, dx + sw * 0.96f, dy + sh * 0.38f), paint);
                    canvas.drawOval(new RectF(dx - sw * 0.38f, dy + sh * 0.32f, dx + sw * 0.28f, dy + sh * 0.82f), paint);

                    // 4. Braided Blue River Deltas (Top-Left) & Winding White Valley River (Bottom-Left)
                    strokePaint.setColor(Color.parseColor("#4A778C"));
                    strokePaint.setStrokeWidth(0.75f * dp);
                    canvas.drawLine(dx - sw * 0.72f, dy - sh * 0.78f, dx - sw * 0.12f, dy - sh * 0.42f, strokePaint);
                    canvas.drawLine(dx - sw * 0.52f, dy - sh * 0.66f, dx + sw * 0.04f, dy - sh * 0.56f, strokePaint);
                    canvas.drawLine(dx + sw * 0.18f, dy - sh * 0.32f, dx + sw * 0.78f, dy - sh * 0.08f, strokePaint);

                    strokePaint.setColor(Color.parseColor("#F5EBD6"));
                    strokePaint.setStrokeWidth(0.9f * dp);
                    canvas.drawLine(dx - sw * 0.60f, dy + sh * 0.12f, dx - sw * 0.48f, dy + sh * 0.52f, strokePaint);
                    canvas.drawLine(dx - sw * 0.48f, dy + sh * 0.52f, dx - sw * 0.36f, dy + sh * 0.92f, strokePaint);

                    // 5. Four 3D Shaded-Relief Mountain Massifs with Fine Lateral Hachure Spurs (Image 1!)
                    // Massif A: Upper-Right High Serrated Range
                    drawCartographicReliefRidge(canvas, paint, strokePaint, dx, dy, sw, sh,
                            new float[][]{
                                    { -0.02f, -0.48f }, {  0.18f, -0.62f }, {  0.42f, -0.70f },
                                    {  0.64f, -0.72f }, {  0.82f, -0.60f }
                            }, 0.26f, 0.24f, true);

                    // Massif B: Dominant Central Golden-Ochre & Dark-Shadowed Alpine Massif
                    drawCartographicReliefRidge(canvas, paint, strokePaint, dx, dy, sw, sh,
                            new float[][]{
                                    { -0.28f,  0.04f }, { -0.12f, -0.18f }, {  0.06f, -0.32f },
                                    {  0.26f, -0.24f }, {  0.46f, -0.08f }
                            }, 0.34f, 0.30f, true);

                    // Massif C: Mid-South Rolling Ochre Highland Plateau
                    drawCartographicReliefRidge(canvas, paint, strokePaint, dx, dy, sw, sh,
                            new float[][]{
                                    { -0.22f,  0.52f }, { -0.02f,  0.32f }, {  0.22f,  0.24f }, {  0.42f,  0.30f }
                            }, 0.24f, 0.20f, false);

                    // Massif D: Steep Lower-Right Dark-Green & Ochre Escarpment Ridge
                    drawCartographicReliefRidge(canvas, paint, strokePaint, dx, dy, sw, sh,
                            new float[][]{
                                    { -0.08f,  0.92f }, {  0.18f,  0.64f }, {  0.46f,  0.38f },
                                    {  0.72f,  0.14f }, {  0.94f, -0.04f }
                            }, 0.22f, 0.28f, true);

                    // 6. Fine Stippled Forest Canopy Texture Along the Foothill Gorges
                    paint.setStyle(Paint.Style.FILL);
                    for (int f = 0; f < 42; f++) {
                        double fa = f * 2.39996;
                        float fx = dx + ((f % 2 == 0 ? 0.22f : -0.18f) + (float) Math.cos(fa) * 0.36f) * sw;
                        float fy = dy + (0.22f + (float) Math.sin(fa) * 0.42f) * sh;
                        paint.setColor((f % 2 == 0) ? Color.parseColor("#1D4228") : Color.parseColor("#356640"));
                        canvas.drawCircle(fx, fy, (0.9f + (f % 3) * 0.4f) * dp, paint);
                    }

                    canvas.restore();

                    // Crisp Cartographic Map Frame Border
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setColor(Color.parseColor("#5C4632"));
                    strokePaint.setStrokeWidth(1.2f * dp);
                    canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, strokePaint);
                    break;

                case FOOTBALL_STADIUM:
                    // Tri-Color (White/Blue/Red) Football Stadium & Green Pitch Radar Icon
                    paint.setColor(Color.parseColor("#9FA6AD"));
                    canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    // White top, Blue middle, Red bottom outer stadium shell
                    paint.setColor(Color.parseColor("#F1F5F9"));
                    canvas.drawRoundRect(new RectF(dx - sw * 0.90f, dy - sh * 0.82f, dx + sw * 0.90f, dy - sh * 0.20f), 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#E12D39"));
                    canvas.drawRoundRect(new RectF(dx - sw * 0.90f, dy + sh * 0.20f, dx + sw * 0.90f, dy + sh * 0.82f), 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#0088DD"));
                    canvas.drawRect(dx - sw * 0.90f, dy - sh * 0.28f, dx + sw * 0.90f, dy + sh * 0.28f, paint);
                    // Pink track & green football pitch
                    paint.setColor(Color.parseColor("#C68B8E"));
                    canvas.drawRoundRect(new RectF(dx - sw * 0.62f, dy - sh * 0.54f, dx + sw * 0.62f, dy + sh * 0.54f), 3f * dp, 3f * dp, paint);
                    paint.setColor(Color.parseColor("#689F38"));
                    canvas.drawRect(dx - sw * 0.44f, dy - sh * 0.38f, dx + sw * 0.44f, dy + sh * 0.38f, paint);
                    break;
                case CONSTRUCTION:
                    paint.setColor(Color.parseColor("#D97706")); canvas.drawRoundRect(tempRect, 3f * dp, 3f * dp, paint);
                    paint.setColor(Color.parseColor("#94A3B8"));
                    canvas.drawRect(dx - sw * 0.85f, dy - sh * 0.85f, dx - sw * 0.42f, dy + sh * 0.35f, paint);
                    canvas.drawRect(dx + sw * 0.45f, dy - sh * 0.80f, dx + sw * 0.88f, dy + sh * 0.85f, paint);
                    paint.setColor(Color.parseColor("#FACC15"));
                    canvas.drawRect(dx - sw * 0.25f, dy - sh * 0.12f, dx + sw * 0.55f, dy + sh * 0.12f, paint);
                    break;
                default:
                    paint.setColor(Color.parseColor("#64748B")); canvas.drawRect(tempRect, paint);
                    break;
            }
        }

        // 3. Pickups on Minimap
        for (DriveOnWorld.OrbPickup orb : world.pickups) {
            boolean isFlying = (car.carModel == DriveOnCar.MODEL_HELICOPTER);
            if ((isFlying && orb.altitude == 0) || (!isFlying && orb.altitude == 1)) continue;
            float dx = world.getShortestDist(car.x, orb.x) * scale, dy = world.getShortestDist(car.y, orb.y) * scale;
            if (Math.abs(dx) > rSpan || Math.abs(dy) > rSpan) continue;
            paint.setColor((orb.kind == 2) ? Color.parseColor("#EF4444") : (orb.kind == 1 ? Color.parseColor("#38BDF8") : Color.parseColor("#FACC15")));
            canvas.drawCircle(dx, dy, 1.8f * dp, paint);
        }

        canvas.restore();

        strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawLine(mapCX, mapCY, mapCX, mapCY - 8f * dp, strokePaint);
        paint.setColor(Color.WHITE); canvas.drawCircle(mapCX, mapCY, 3.8f * dp, paint);

        strokePaint.setColor(isAmoledStarMode ? Color.parseColor("#38BDF8") : Color.parseColor("#4CD964"));
        strokePaint.setStrokeWidth(2.5f * dp);
        canvas.drawRoundRect(minimapRect, cornerR, cornerR, strokePaint);
    }

    /**
     * Renders a 3D shaded-relief cartographic mountain ridge on the minimap with sunlit golden-ochre NW slopes,
     * deep charcoal-green SE shadow faces, and 22 feathered lateral hachure spurs (matching Image 1).
     */
    private void drawCartographicReliefRidge(Canvas canvas, Paint paint, Paint strokePaint,
                                             float dx, float dy, float sw, float sh,
                                             float[][] spine, float sunReach, float shadowReach, boolean sharpPeaks) {
        int steps = 22;
        float[] rx = new float[steps + 1];
        float[] ry = new float[steps + 1];
        float[] lx = new float[steps + 1];
        float[] ly = new float[steps + 1];
        float[] sx = new float[steps + 1];
        float[] sy = new float[steps + 1];

        int segCount = spine.length - 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float scaled = t * segCount;
            int idx = Math.min(segCount - 1, (int) scaled);
            float frac = scaled - idx;

            float bx = spine[idx][0] + (spine[idx + 1][0] - spine[idx][0]) * frac;
            float by = spine[idx][1] + (spine[idx + 1][1] - spine[idx][1]) * frac;

            // Taper at both ends of the ridge & add craggy peak jitter
            float env = (float) Math.sin(t * Math.PI);
            float jitter = (float) Math.sin(i * 2.7f) * 0.028f * env;
            rx[i] = dx + (bx + jitter) * sw;
            ry[i] = dy + (by - jitter) * sh;

            // Spur length modulation for feathered hachure ridges
            float spurMod = env * (0.65f + 0.35f * (float) Math.abs(Math.cos(i * 1.9f)));
            // NW Sunlit Slope Toe (upper-left direction)
            lx[i] = rx[i] - sw * sunReach * spurMod * 0.85f;
            ly[i] = ry[i] - sh * sunReach * spurMod * 0.75f;
            // SE Shaded Slope Toe (lower-right direction)
            sx[i] = rx[i] + sw * shadowReach * spurMod * 0.85f;
            sy[i] = ry[i] + sh * shadowReach * spurMod * 0.75f;
        }

        paint.setStyle(Paint.Style.FILL);

        // 1. Broad Sunlit NW Apron (Warm Golden-Ochre)
        minimapClipPath.reset();
        minimapClipPath.moveTo(rx[0], ry[0]);
        for (int i = 1; i <= steps; i++) minimapClipPath.lineTo(rx[i], ry[i]);
        for (int i = steps; i >= 0; i--) minimapClipPath.lineTo(lx[i], ly[i]);
        minimapClipPath.close();
        paint.setColor(Color.parseColor("#D8AE72"));
        canvas.drawPath(minimapClipPath, paint);

        // 2. Broad Shaded SE Apron (Deep Charcoal-Forest Shadow)
        minimapClipPath.reset();
        minimapClipPath.moveTo(rx[0], ry[0]);
        for (int i = 1; i <= steps; i++) minimapClipPath.lineTo(rx[i], ry[i]);
        for (int i = steps; i >= 0; i--) minimapClipPath.lineTo(sx[i], sy[i]);
        minimapClipPath.close();
        paint.setColor(sharpPeaks ? Color.parseColor("#21362A") : Color.parseColor("#7A8F52"));
        canvas.drawPath(minimapClipPath, paint);

        // 3. Feathered Lateral Spur Facets & Dendritic Gully Hachures (22 Crinkled 3D Ribs)
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(0.6f * dp);

        for (int i = 0; i < steps; i++) {
            // Sunlit NW Spur Triangle (Bright Cream-Gold vs Warm Ochre)
            minimapClipPath.reset();
            minimapClipPath.moveTo(rx[i], ry[i]);
            minimapClipPath.lineTo(rx[i + 1], ry[i + 1]);
            minimapClipPath.lineTo(lx[i], ly[i]);
            minimapClipPath.close();
            paint.setColor((i % 2 == 0) ? Color.parseColor("#F5E4C4") : Color.parseColor("#C49252"));
            canvas.drawPath(minimapClipPath, paint);

            // Shaded SE Spur Triangle (Deep Slate-Green vs Dark Umber)
            minimapClipPath.reset();
            minimapClipPath.moveTo(rx[i], ry[i]);
            minimapClipPath.lineTo(rx[i + 1], ry[i + 1]);
            minimapClipPath.lineTo(sx[i], sy[i]);
            minimapClipPath.close();
            paint.setColor((i % 2 == 0) ? Color.parseColor("#182920") : Color.parseColor("#3B2F23"));
            canvas.drawPath(minimapClipPath, paint);

            // Fine Hachure Gully Lines Radiating Off the Spine
            strokePaint.setColor(Color.parseColor("#9E713B"));
            canvas.drawLine(rx[i], ry[i], lx[i], ly[i], strokePaint);
            strokePaint.setColor(Color.parseColor("#111F18"));
            canvas.drawLine(rx[i], ry[i], sx[i], sy[i], strokePaint);
        }

        // 4. Crisp Ivory-White Summit Ridge Crest Line
        if (sharpPeaks) {
            minimapClipPath.reset();
            minimapClipPath.moveTo(rx[0], ry[0]);
            for (int i = 1; i <= steps; i++) minimapClipPath.lineTo(rx[i], ry[i]);
            strokePaint.setColor(Color.parseColor("#FFFDF7"));
            strokePaint.setStrokeWidth(0.95f * dp);
            canvas.drawPath(minimapClipPath, strokePaint);
        }
    }

    private void drawRightSmartPills(Canvas canvas) {
        float rightEdge = screenW - 16f * dp - safeInsetRight, topY = 12f * dp;
        String titleStr = car.getModelName();

        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setTextSize(16.0f * dp);
        float textW = textPaint.measureText(titleStr);
        float padStart = 8f * dp, boxSize = 38f * dp, textMargin = 12f * dp, padEnd = 24f * dp, pillH = 50f * dp;
        float pillW = padStart + boxSize + textMargin + textW + padEnd, leftX = rightEdge - pillW;

        rightCarPillRect.set(leftX, topY, rightEdge, topY + pillH);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#24000000"));
        tempRect.set(leftX, topY + 2.5f * dp, rightEdge, topY + pillH + 2.5f * dp);
        canvas.drawRoundRect(tempRect, pillH / 2f, pillH / 2f, paint);

        paint.setColor(hudPillBgColor); canvas.drawRoundRect(rightCarPillRect, pillH / 2f, pillH / 2f, paint);

        float boxLeft = leftX + padStart, boxTop = topY + 6f * dp;
        tempRect.set(boxLeft, boxTop, boxLeft + boxSize, boxTop + boxSize);
        paint.setColor(hudPillBgColor); canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, paint);
        strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, strokePaint);

        car.drawMiniIcon(canvas, paint, strokePaint, boxLeft + boxSize / 2f, boxTop + boxSize / 2f, dp, themeState);

        float textX = boxLeft + boxSize + textMargin;
        textPaint.setColor(hudTitleColor); canvas.drawText(titleStr, textX, topY + 31.0f * dp, textPaint);

        float hornTop = topY + pillH + 8f * dp, hornBox = 32f * dp, hornH = 44f * dp;
        String hTitle = "Horn";
        textPaint.setTextSize(14.0f * dp);
        float hTextW = textPaint.measureText(hTitle);
        float hornW = padStart + hornBox + textMargin + hTextW + 20f * dp, hLeft = rightEdge - hornW;

        rightHornPillRect.set(hLeft, hornTop, rightEdge, hornTop + hornH);
        paint.setColor(hudPillBgColor); canvas.drawRoundRect(rightHornPillRect, hornH / 2f, hornH / 2f, paint);

        float hbLeft = hLeft + padStart, hbTop = hornTop + 6f * dp;
        tempRect.set(hbLeft, hbTop, hbLeft + hornBox, hbTop + hornBox);
        paint.setColor(hudPillBgColor); canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, paint);
        strokePaint.setColor(Color.parseColor("#38BDF8")); canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, strokePaint);

        paint.setColor(Color.parseColor("#38BDF8")); canvas.drawCircle(hbLeft + hornBox / 2f, hbTop + hornBox / 2f, 5.5f * dp, paint);
        float hTextX = hbLeft + hornBox + textMargin;
        textPaint.setColor(hudTitleColor); canvas.drawText(hTitle, hTextX, hornTop + 27.5f * dp, textPaint);
    }

    private void drawSpeedometerGearShifterAndPedals(Canvas canvas) {
        float speedCX = screenW * 0.5f, speedBottom = screenH - 16f * dp, pillW = 178f * dp, pillH = 56f * dp;
        tempRect.set(speedCX - pillW / 2f, speedBottom - pillH, speedCX + pillW / 2f, speedBottom);
        paint.setStyle(Paint.Style.FILL); paint.setColor(hudPillBgColor);
        canvas.drawRoundRect(tempRect, 20f * dp, 20f * dp, paint);

        textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setTextSize(17.5f * dp); textPaint.setColor(hudTitleColor);
        String gearBadge = (car.forwardSpeed < -0.3f * dp) ? "GEAR R" : ("GEAR " + car.currentGear);
        canvas.drawText(gearBadge + " • " + Math.round(car.speedKmh) + " KM/H", speedCX, speedBottom - 25f * dp, textPaint);

        float barW = pillW - 32f * dp, barH = 6.5f * dp, barLeft = speedCX - barW / 2f, barTop = speedBottom - 14f * dp;
        paint.setColor(Color.parseColor("#3394A3B8")); tempRect.set(barLeft, barTop, barLeft + barW, barTop + barH);
        canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);

        paint.setColor(car.isBoosting ? Color.parseColor("#FACC15") : Color.parseColor("#38BDF8"));
        tempRect.set(barLeft, barTop, barLeft + barW * (car.nitro / car.maxNitro), barTop + barH);
        canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);

        float gearBtnW = 52f * dp, gearBtnH = 48f * dp, gearGap = 10f * dp;
        gearDownRect.set(speedCX - pillW / 2f - gearGap - gearBtnW, speedBottom - gearBtnH - 4f * dp, speedCX - pillW / 2f - gearGap, speedBottom - 4f * dp);
        gearUpRect.set(speedCX + pillW / 2f + gearGap, speedBottom - gearBtnH - 4f * dp, speedCX + pillW / 2f + gearGap + gearBtnW, speedBottom - 4f * dp);

        paint.setColor(hudPillBgColor); canvas.drawRoundRect(gearDownRect, 15f * dp, 15f * dp, paint);
        strokePaint.setColor(car.currentGear > 1 ? Color.parseColor("#38BDF8") : Color.parseColor("#5594A3B8"));
        strokePaint.setStrokeWidth(2.6f * dp); canvas.drawRoundRect(gearDownRect, 15f * dp, 15f * dp, strokePaint);
        textPaint.setTextSize(13f * dp); textPaint.setColor(car.currentGear > 1 ? hudTitleColor : hudSubTextColor);
        canvas.drawText("G -", gearDownRect.centerX(), gearDownRect.centerY() + 5f * dp, textPaint);

        paint.setColor(hudPillBgColor); canvas.drawRoundRect(gearUpRect, 15f * dp, 15f * dp, paint);
        strokePaint.setColor(car.currentGear < 5 ? Color.parseColor("#34C759") : Color.parseColor("#5594A3B8"));
        canvas.drawRoundRect(gearUpRect, 15f * dp, 15f * dp, strokePaint);
        textPaint.setColor(car.currentGear < 5 ? hudTitleColor : hudSubTextColor);
        canvas.drawText("G +", gearUpRect.centerX(), gearUpRect.centerY() + 5f * dp, textPaint);

        float rightEdge = screenW - 20f * dp - safeInsetRight, pedalBottom = screenH - 18f * dp;
        float gasW = 68f * dp, gasH = 96f * dp, brakeW = 62f * dp, brakeH = 76f * dp;

        gasPedalRect.set(rightEdge - gasW, pedalBottom - gasH, rightEdge, pedalBottom);
        brakePedalRect.set(gasPedalRect.left - 14f * dp - brakeW, pedalBottom - brakeH, gasPedalRect.left - 14f * dp, pedalBottom);
        nitroBtnRect.set(gasPedalRect.left - 4f * dp, gasPedalRect.top - 56f * dp, rightEdge, gasPedalRect.top - 10f * dp);

        paint.setColor(car.brakePressed ? Color.parseColor("#EF4444") : hudPillBgColor);
        canvas.drawRoundRect(brakePedalRect, 18f * dp, 18f * dp, paint);
        strokePaint.setColor(Color.parseColor("#EF4444")); strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(brakePedalRect, 18f * dp, 18f * dp, strokePaint);
        textPaint.setTextSize(10.5f * dp); textPaint.setColor(car.brakePressed ? Color.WHITE : hudTitleColor);
        canvas.drawText("BRAKE/REV", brakePedalRect.centerX(), brakePedalRect.centerY() + 4f * dp, textPaint);

        paint.setColor(car.gasPressed ? Color.parseColor("#34C759") : hudPillBgColor);
        canvas.drawRoundRect(gasPedalRect, 18f * dp, 18f * dp, paint);
        strokePaint.setColor(Color.parseColor("#34C759")); canvas.drawRoundRect(gasPedalRect, 18f * dp, 18f * dp, strokePaint);
        textPaint.setTextSize(11.5f * dp); textPaint.setColor(car.gasPressed ? Color.WHITE : hudTitleColor);
        canvas.drawText("GAS", gasPedalRect.centerX(), gasPedalRect.centerY() + 4f * dp, textPaint);

        paint.setColor(car.isBoosting ? Color.parseColor("#F59E0B") : hudPillBgColor);
        canvas.drawRoundRect(nitroBtnRect, 16f * dp, 16f * dp, paint);
        strokePaint.setColor(Color.parseColor("#38BDF8")); canvas.drawRoundRect(nitroBtnRect, 16f * dp, 16f * dp, strokePaint);
        textPaint.setTextSize(11.0f * dp); textPaint.setColor(car.isBoosting ? Color.WHITE : hudTitleColor);
        canvas.drawText("NITRO", nitroBtnRect.centerX(), nitroBtnRect.centerY() + 4f * dp, textPaint);
    }

    private void drawDPadControls(Canvas canvas) {
        float startX = 24f * dp + safeInsetLeft, startY = screenH - 124f * dp, btnSize = 62f * dp, gap = 14f * dp;

        engineBtnRect.set(startX, startY - 48f * dp - 12f * dp, startX + 90f * dp, startY - 12f * dp);
        paint.setStyle(Paint.Style.FILL); paint.setColor(hudPillBgColor);
        canvas.drawRoundRect(engineBtnRect, 12f * dp, 12f * dp, paint);
        strokePaint.setColor(car.isEngineOn ? Color.parseColor("#34C759") : Color.parseColor("#EF4444"));
        strokePaint.setStrokeWidth(2.6f * dp); canvas.drawRoundRect(engineBtnRect, 12f * dp, 12f * dp, strokePaint);
        textPaint.setTextSize(13f * dp); textPaint.setColor(car.isEngineOn ? hudTitleColor : Color.parseColor("#EF4444"));
        canvas.drawText(car.isEngineOn ? "ENGINE ON" : "ENGINE OFF", engineBtnRect.centerX(), engineBtnRect.centerY() + 5f * dp, textPaint);

        dpadLeftRect.set(startX, startY, startX + btnSize, startY + btnSize);
        paint.setColor(leftPressed ? Color.parseColor("#38BDF8") : hudPillBgColor);
        canvas.drawRoundRect(dpadLeftRect, 18f * dp, 18f * dp, paint);
        strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(dpadLeftRect, 18f * dp, 18f * dp, strokePaint);

        textPaint.setTextSize(22f * dp); textPaint.setColor(leftPressed ? Color.WHITE : hudTitleColor);
        canvas.drawText("◄", dpadLeftRect.centerX(), dpadLeftRect.centerY() + 8f * dp, textPaint);

        dpadRightRect.set(startX + btnSize + gap, startY, startX + btnSize * 2f + gap, startY + btnSize);
        paint.setColor(rightPressed ? Color.parseColor("#38BDF8") : hudPillBgColor);
        canvas.drawRoundRect(dpadRightRect, 18f * dp, 18f * dp, paint);
        strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(dpadRightRect, 18f * dp, 18f * dp, strokePaint);

        textPaint.setColor(rightPressed ? Color.WHITE : hudTitleColor);
        canvas.drawText("►", dpadRightRect.centerX(), dpadRightRect.centerY() + 8f * dp, textPaint);
    }
}