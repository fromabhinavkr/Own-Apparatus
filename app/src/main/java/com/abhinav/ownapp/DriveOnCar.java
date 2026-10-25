package com.abhinav.ownapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("all")
public class DriveOnCar {

    public static final int MODEL_SPORTS_GT   = 0;
    public static final int MODEL_SUPERBIKE   = 1;
    public static final int MODEL_HEAVY_TRUCK = 2;
    public static final int MODEL_BUGGY_RAID  = 3;
    public static final int MODEL_HELICOPTER  = 4;

    private static final float[] GEAR_SPEED_MULTS = { 0.22f, 0.42f, 0.64f, 0.84f, 1.05f };
    private static final float[] GEAR_ACCEL_MULTS = { 1.30f, 1.15f, 1.00f, 0.88f, 0.78f };

    public float x = 0f, y = 0f;
    public float vx = 0f, vy = 0f;
    public float angle = (float) (-Math.PI / 2.0);
    public float angularVelocity = 0f;
    public float visualSteerAngle = 0f;
    public float bodyRoll = 0f;
    public float forwardSpeed = 0f;
    public float rotorAngle = 0f;
    public float rotorSpinSpeed = 15f;

    public float targetSteerInput = 0f;
    public float steerInput = 0f;
    public boolean gasPressed = false;
    public boolean brakePressed = false;
    public boolean boostPressed = false;

    public int carModel = MODEL_SPORTS_GT;
    public int currentGear = 3;
    public float nitro = 100f, maxNitro = 100f;
    public float speedKmh = 0f;
    public float topSpeedKmh = 0f;
    public float totalDistanceMeters = 0f;
    public int coinsCollected = 0;
    public boolean isDrifting = false;
    public boolean isBoosting = false;
    public boolean isEngineOn = true;

    public final List<SkidMark> skidMarks = new ArrayList<>();
    public final List<SmokeParticle> smokeParticles = new ArrayList<>();

    private final RectF tempRect = new RectF();
    private final Path tempPath = new Path();

    public void reset(float startX, float startY) {
        x = startX; y = startY;
        vx = 0f; vy = 0f;
        angle = (float) (-Math.PI / 2.0);
        angularVelocity = 0f;
        visualSteerAngle = 0f;
        bodyRoll = 0f;
        forwardSpeed = 0f;
        rotorAngle = 0f;
        rotorSpinSpeed = 15f;
        targetSteerInput = 0f;
        steerInput = 0f;
        gasPressed = false; brakePressed = false; boostPressed = false;
        isEngineOn = true;
        nitro = maxNitro;
        speedKmh = 0f;
        isDrifting = false; isBoosting = false;
        skidMarks.clear();
        smokeParticles.clear();
    }

    public void cycleCarModel() { carModel = (carModel + 1) % 5; }

    public void shiftGearUp(DriveOnAudio audio) {
        if (currentGear < 5) { currentGear++; if (audio != null) audio.playGearShift(currentGear); }
    }

    public void shiftGearDown(DriveOnAudio audio) {
        if (currentGear > 1) { currentGear--; if (audio != null) audio.playGearShift(currentGear); }
    }

    public String getModelName() {
        switch (carModel) {
            case MODEL_SUPERBIKE:   return "Superbike";
            case MODEL_HEAVY_TRUCK: return "Heavy Truck";
            case MODEL_BUGGY_RAID:  return "Buggy Raid";
            case MODEL_HELICOPTER:  return "Helicopter";
            default:                return "Sports GT";
        }
    }

    public void updatePhysics(float dp, boolean isOnRoad, int handlingMode, DriveOnAudio audio, DriveOnWorld world) {
        float steerLerp = (targetSteerInput == 0f) ? 0.28f : 0.16f;
        steerInput += (targetSteerInput - steerInput) * steerLerp;
        if (Math.abs(steerInput) < 0.01f && targetSteerInput == 0f) steerInput = 0f;

        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        forwardSpeed = vx * cos + vy * sin;
        float lateralSpeed = -vx * sin + vy * cos;

        visualSteerAngle += (steerInput * 0.52f - visualSteerAngle) * 0.25f;

        float modelAccel, modelTopSpd;
        if (carModel == MODEL_SUPERBIKE) { modelAccel = 1.24f; modelTopSpd = 1.12f; }
        else if (carModel == MODEL_HEAVY_TRUCK) { modelAccel = 0.78f; modelTopSpd = 0.84f; }
        else if (carModel == MODEL_BUGGY_RAID) { modelAccel = 1.08f; modelTopSpd = 0.98f; }
        else if (carModel == MODEL_HELICOPTER) { modelAccel = 0.90f; modelTopSpd = 1.35f; }
        else { modelAccel = 1.05f; modelTopSpd = 1.06f; }

        float surfaceTopMult = (isOnRoad || carModel == MODEL_HELICOPTER) ? 1.0f : (carModel == MODEL_BUGGY_RAID ? 0.94f : 0.78f);

        int gearIdx = Math.max(0, Math.min(4, currentGear - 1));
        float gearSpdMult = GEAR_SPEED_MULTS[gearIdx];
        float gearAccMult = GEAR_ACCEL_MULTS[gearIdx];

        float maxForwardSpd = 17.5f * dp * modelTopSpd * surfaceTopMult * gearSpdMult;
        float accelRate = 0.36f * dp * modelAccel * gearAccMult;

        boolean effectiveGas = gasPressed && isEngineOn;
        boolean effectiveBoost = boostPressed && isEngineOn && nitro > 1.5f && (effectiveGas || forwardSpeed > 1.0f * dp);

        isBoosting = effectiveBoost;

        if (isBoosting) {
            maxForwardSpd *= 1.38f;
            accelRate *= 1.65f;
            nitro = Math.max(0f, nitro - 0.52f);
            if (audio != null) audio.playNitroBoost();
        } else if (nitro < maxNitro) {
            nitro = Math.min(maxNitro, nitro + (isDrifting ? 0.24f : 0.08f));
        }

        if (effectiveGas || isBoosting) {
            if (forwardSpeed < 0f) forwardSpeed += 0.65f * dp;
            else forwardSpeed += accelRate;
        } else if (brakePressed) {
            if (forwardSpeed > 0.5f * dp) {
                forwardSpeed -= 0.62f * dp;
                if (audio != null) audio.playBrakeSound(speedKmh);
            } else if (isEngineOn) {
                forwardSpeed -= 0.25f * dp;
            } else {
                forwardSpeed *= 0.92f;
            }
        } else {
            forwardSpeed *= isOnRoad ? 0.984f : 0.960f;
        }

        if (forwardSpeed > maxForwardSpd) forwardSpeed = maxForwardSpd + (forwardSpeed - maxForwardSpd) * 0.92f;
        float maxReverseSpd = -6.0f * dp;
        if (forwardSpeed < maxReverseSpd) forwardSpeed = maxReverseSpd;

        if (audio != null && isEngineOn) {
            audio.updateEngineTelemetry(carModel, speedKmh, currentGear, effectiveGas, isBoosting);
        }

        float speedFactor = Math.min(1.0f, Math.abs(forwardSpeed) / (2.5f * dp));
        float reverseDir = (forwardSpeed >= -0.15f * dp) ? 1f : -1f;
        float turnRate = (handlingMode == 0) ? 0.064f : (handlingMode == 1 ? 0.058f : 0.052f);
        if (carModel == MODEL_SUPERBIKE) turnRate *= 1.14f;
        else if (carModel == MODEL_HEAVY_TRUCK) turnRate *= 0.82f;
        else if (carModel == MODEL_HELICOPTER) turnRate *= 0.65f;

        float targetAngVel = steerInput * turnRate * speedFactor * reverseDir;
        angularVelocity += (targetAngVel - angularVelocity) * 0.28f;
        angle += angularVelocity;

        float baseGrip = (handlingMode == 0) ? 0.78f : (handlingMode == 1 ? 0.85f : 0.90f);
        if (!isOnRoad && carModel != MODEL_BUGGY_RAID && carModel != MODEL_HELICOPTER) baseGrip = Math.min(0.94f, baseGrip + 0.04f);
        lateralSpeed *= baseGrip;

        float rollMult = (carModel == MODEL_SUPERBIKE) ? 5.5f : -2.4f;
        float targetRoll = steerInput * speedFactor * rollMult * dp;
        bodyRoll += (targetRoll - bodyRoll) * 0.22f;

        float newCos = (float) Math.cos(angle);
        float newSin = (float) Math.sin(angle);
        vx = newCos * forwardSpeed - newSin * lateralSpeed;
        vy = newSin * forwardSpeed + newCos * lateralSpeed;

        x += vx;
        y += vy;

        x = world.wrapMapCoord(x);
        y = world.wrapMapCoord(y);

        float totalSpd = (float) Math.hypot(vx, vy);
        speedKmh = (totalSpd / dp) * 14.5f;
        if (speedKmh < 1.2f && !effectiveGas && !brakePressed) {
            speedKmh = 0f; vx = 0f; vy = 0f; forwardSpeed = 0f;
        }
        if (speedKmh > topSpeedKmh) topSpeedKmh = speedKmh;
        totalDistanceMeters += (totalSpd / dp) * 0.045f;

        if (carModel == MODEL_HELICOPTER) {
            isDrifting = false;
            float targetSpin = isEngineOn ? (15f + speedKmh * 0.2f) : 0f;
            rotorSpinSpeed += (targetSpin - rotorSpinSpeed) * 0.06f;
            rotorAngle += rotorSpinSpeed;
        } else {
            isDrifting = Math.abs(lateralSpeed) > 1.85f * dp && Math.abs(forwardSpeed) > 4.2f * dp;
            if (isDrifting || (brakePressed && forwardSpeed > 4.5f * dp)) {
                float rearOffset = (carModel == MODEL_HEAVY_TRUCK) ? -38f * dp : -25f * dp;
                float wheelSpread = (carModel == MODEL_SUPERBIKE) ? 0f : 15f * dp;

                float rxCenter = x + newCos * rearOffset;
                float ryCenter = y + newSin * rearOffset;

                float lx = rxCenter - newSin * wheelSpread, ly = ryCenter + newCos * wheelSpread;
                float rx = rxCenter + newSin * wheelSpread, ry = ryCenter - newCos * wheelSpread;
                skidMarks.add(new SkidMark(lx, ly, rx, ry));
                if (skidMarks.size() > 160) skidMarks.remove(0);

                if (Math.random() < 0.65) {
                    smokeParticles.add(new SmokeParticle(
                            rxCenter + (float) ((Math.random() - 0.5) * 16f * dp),
                            ryCenter + (float) ((Math.random() - 0.5) * 16f * dp),
                            -vx * 0.15f + (float) ((Math.random() - 0.5) * 1.2f * dp),
                            -vy * 0.15f + (float) ((Math.random() - 0.5) * 1.2f * dp),
                            (5f + (float) Math.random() * 5f) * dp,
                            Color.parseColor("#99E2E8F0"), 22));
                }
                if (isDrifting && audio != null) {
                    audio.playDriftScreech();
                    if (Math.random() < 0.25) audio.vibrateDrift();
                }
            }
        }

        if (isBoosting) {
            float exX = x - newCos * 34f * dp;
            float exY = y - newSin * 34f * dp;
            for (int i = 0; i < 2; i++) {
                smokeParticles.add(new SmokeParticle(
                        exX + (float) ((Math.random() - 0.5) * 8f * dp),
                        exY + (float) ((Math.random() - 0.5) * 8f * dp),
                        -newCos * 4.5f * dp + (float) ((Math.random() - 0.5) * 1.2f * dp),
                        -newSin * 4.5f * dp + (float) ((Math.random() - 0.5) * 1.2f * dp),
                        (4.5f + (float) Math.random() * 3.5f) * dp,
                        (i % 2 == 0) ? Color.parseColor("#38BDF8") : Color.parseColor("#FDE047"), 12));
            }
        }

        Iterator<SmokeParticle> pIt = smokeParticles.iterator();
        while (pIt.hasNext()) {
            SmokeParticle sp = pIt.next();
            sp.x += sp.dx; sp.y += sp.dy;
            sp.radius += 0.22f * dp;
            sp.life--;
            if (sp.life <= 0) pIt.remove();
        }
        Iterator<SkidMark> sIt = skidMarks.iterator();
        while (sIt.hasNext()) {
            SkidMark sm = sIt.next();
            sm.alpha -= 1;
            if (sm.alpha <= 0) sIt.remove();
        }
    }

    public void drawSkidMarksAndSmoke(DriveOnWorld world, Canvas canvas, Paint paint, float camX, float camY, float viewW, float viewH, float dp) {
        paint.setStyle(Paint.Style.FILL);
        for (SkidMark sm : skidMarks) {
            float dx = world.getShortestDist(camX, sm.lx), dy = world.getShortestDist(camY, sm.ly);
            if (Math.abs(dx) > viewW || Math.abs(dy) > viewH) continue;
            paint.setColor(Color.BLACK);
            paint.setAlpha(Math.min(130, sm.alpha));
            canvas.drawCircle(camX + dx, camY + dy, 3.2f * dp, paint);
            float rdx = world.getShortestDist(camX, sm.rx), rdy = world.getShortestDist(camY, sm.ry);
            canvas.drawCircle(camX + rdx, camY + rdy, 3.2f * dp, paint);
        }
        for (SmokeParticle sp : smokeParticles) {
            float dx = world.getShortestDist(camX, sp.x), dy = world.getShortestDist(camY, sp.y);
            if (Math.abs(dx) > viewW || Math.abs(dy) > viewH) continue;
            paint.setColor(sp.color);
            paint.setAlpha((int) (210f * ((float) sp.life / sp.maxLife)));
            canvas.drawCircle(camX + dx, camY + dy, sp.radius, paint);
        }
        paint.setAlpha(255);
    }

    public void drawMiniIcon(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float dp, int themeState) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(0.32f, 0.32f);
        canvas.rotate(-90);
        if (carModel == MODEL_SUPERBIKE) drawSuperbike(canvas, paint, strokePaint, dp, true);
        else if (carModel == MODEL_HEAVY_TRUCK) drawHeavyTruck(canvas, paint, strokePaint, dp, true);
        else if (carModel == MODEL_BUGGY_RAID) drawBuggy(canvas, paint, strokePaint, dp, true);
        else if (carModel == MODEL_HELICOPTER) drawHelicopter(canvas, paint, strokePaint, dp, true);
        else drawSportsGT(canvas, paint, strokePaint, dp, themeState, true);
        canvas.restore();
    }

    public void drawCar(Canvas canvas, Paint paint, Paint strokePaint, float camX, float camY, float dp, int themeState, float starPhase) {
        canvas.save();
        canvas.translate(camX, camY);
        canvas.rotate((float) Math.toDegrees(angle));

        if (carModel == MODEL_SUPERBIKE) drawSuperbike(canvas, paint, strokePaint, dp, false);
        else if (carModel == MODEL_HEAVY_TRUCK) drawHeavyTruck(canvas, paint, strokePaint, dp, false);
        else if (carModel == MODEL_BUGGY_RAID) drawBuggy(canvas, paint, strokePaint, dp, false);
        else if (carModel == MODEL_HELICOPTER) drawHelicopter(canvas, paint, strokePaint, dp, false);
        else drawSportsGT(canvas, paint, strokePaint, dp, themeState, false);

        canvas.restore();
    }

    private void drawSuperbike(Canvas canvas, Paint paint, Paint strokePaint, float dp, boolean isIcon) {
        if (!isIcon) {
            paint.setColor(Color.parseColor("#66000000"));
            canvas.drawRoundRect(new RectF(-22f * dp, -5f * dp, 22f * dp, 5f * dp), 4f * dp, 4f * dp, paint);
        }
        paint.setColor(Color.parseColor("#111827"));
        canvas.drawRoundRect(new RectF(-22f * dp, -3.5f * dp, -12f * dp, 3.5f * dp), 2f * dp, 2f * dp, paint);
        canvas.save(); canvas.translate(14f * dp, 0f);
        if (!isIcon) canvas.rotate((float) Math.toDegrees(visualSteerAngle));
        canvas.drawRoundRect(new RectF(-6f * dp, -3.2f * dp, 6f * dp, 3.2f * dp), 2f * dp, 2f * dp, paint);
        canvas.restore();
        paint.setColor(Color.parseColor("#DC2626"));
        tempRect.set(-16f * dp, -6.5f * dp, 12f * dp, 6.5f * dp);
        canvas.drawRoundRect(tempRect, 6f * dp, 6f * dp, paint);
        tempRect.set(4f * dp, -5f * dp, 16f * dp, 5f * dp);
        canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawOval(new RectF(8f * dp, -4f * dp, 15f * dp, 4f * dp), paint);
        paint.setColor(Color.parseColor("#475569"));
        canvas.drawRect(8f * dp, -9f * dp, 10f * dp, 9f * dp, paint);
        paint.setColor(Color.parseColor("#111827"));
        canvas.drawOval(new RectF(1f * dp, -4.5f * dp, 9f * dp, 4.5f * dp), paint);
        paint.setColor(Color.parseColor("#1F2937"));
        canvas.drawOval(new RectF(-10f * dp, -5.5f * dp, 2f * dp, 5.5f * dp), paint);
    }

    private void drawHeavyTruck(Canvas canvas, Paint paint, Paint strokePaint, float dp, boolean isIcon) {
        if (!isIcon) {
            paint.setColor(Color.parseColor("#66000000"));
            canvas.drawRoundRect(new RectF(-46f * dp, -16f * dp, 28f * dp, 16f * dp), 6f * dp, 6f * dp, paint);
        }
        paint.setColor(Color.parseColor("#111827"));
        float[] tireX = { -38f, -28f, 16f };
        for (float tx : tireX) {
            canvas.drawRoundRect(new RectF(tx * dp, -19f * dp, (tx + 8f) * dp, -14f * dp), 2f * dp, 2f * dp, paint);
            canvas.drawRoundRect(new RectF(tx * dp, 14f * dp, (tx + 8f) * dp, 19f * dp), 2f * dp, 2f * dp, paint);
        }
        paint.setColor(Color.parseColor("#F8FAFC"));
        tempRect.set(-48f * dp, -16f * dp, 8f * dp, 16f * dp);
        canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(2f * dp);
        canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, strokePaint);
        paint.setColor(Color.parseColor("#2563EB"));
        tempRect.set(10f * dp, -14f * dp, 26f * dp, 14f * dp);
        canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRect(18f * dp, -12f * dp, 24f * dp, 12f * dp, paint);
        canvas.drawRect(12f * dp, -14.5f * dp, 17f * dp, -12f * dp, paint);
        canvas.drawRect(12f * dp, 12f * dp, 17f * dp, 14.5f * dp, paint);
        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawCircle(8f * dp, -13f * dp, 2.5f * dp, paint);
        canvas.drawCircle(8f * dp, 13f * dp, 2.5f * dp, paint);
    }

    private void drawBuggy(Canvas canvas, Paint paint, Paint strokePaint, float dp, boolean isIcon) {
        if (!isIcon) {
            paint.setColor(Color.parseColor("#66000000"));
            canvas.drawRoundRect(new RectF(-26f * dp, -16f * dp, 26f * dp, 16f * dp), 6f * dp, 6f * dp, paint);
        }
        paint.setColor(Color.parseColor("#111827"));
        canvas.drawRoundRect(new RectF(-24f * dp, -22f * dp, -12f * dp, -14f * dp), 3f * dp, 3f * dp, paint);
        canvas.drawRoundRect(new RectF(-24f * dp, 14f * dp, -12f * dp, 22f * dp), 3f * dp, 3f * dp, paint);
        canvas.save(); canvas.translate(18f * dp, -18f * dp);
        if (!isIcon) canvas.rotate((float) Math.toDegrees(visualSteerAngle));
        canvas.drawRoundRect(new RectF(-6f * dp, -4f * dp, 6f * dp, 4f * dp), 3f * dp, 3f * dp, paint);
        canvas.restore();
        canvas.save(); canvas.translate(18f * dp, 18f * dp);
        if (!isIcon) canvas.rotate((float) Math.toDegrees(visualSteerAngle));
        canvas.drawRoundRect(new RectF(-6f * dp, -4f * dp, 6f * dp, 4f * dp), 3f * dp, 3f * dp, paint);
        canvas.restore();
        paint.setColor(Color.parseColor("#F59E0B"));
        tempRect.set(-20f * dp, -12f * dp, 22f * dp, 12f * dp);
        canvas.drawRoundRect(tempRect, 8f * dp, 8f * dp, paint);
        strokePaint.setColor(Color.parseColor("#1F2937"));
        strokePaint.setStrokeWidth(2.5f * dp);
        canvas.drawRoundRect(new RectF(-10f * dp, -10f * dp, 10f * dp, 10f * dp), 4f * dp, 4f * dp, strokePaint);
        canvas.drawLine(-10f * dp, -10f * dp, 10f * dp, 10f * dp, strokePaint);
        canvas.drawLine(-10f * dp, 10f * dp, 10f * dp, -10f * dp, strokePaint);
        paint.setColor(Color.parseColor("#111827"));
        canvas.drawCircle(-18f * dp, 0f, 6.5f * dp, paint);
        paint.setColor(Color.parseColor("#475569"));
        canvas.drawCircle(-18f * dp, 0f, 2.5f * dp, paint);
    }

    private void drawHelicopter(Canvas canvas, Paint paint, Paint strokePaint, float dp, boolean isIcon) {
        if (!isIcon) {
            paint.setColor(Color.parseColor("#33000000"));
            canvas.save();
            canvas.translate(-50f * dp, 60f * dp);
            canvas.scale(0.85f, 0.85f);
            canvas.drawRoundRect(new RectF(-35f * dp, -12f * dp, 22f * dp, 12f * dp), 14f * dp, 14f * dp, paint);
            canvas.rotate(rotorAngle);
            canvas.drawCircle(0f, 0f, 32f * dp, paint);
            canvas.restore();
            canvas.scale(1.15f, 1.15f);
        }

        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawRoundRect(new RectF(-16f * dp, -16f * dp, 22f * dp, -13f * dp), 1.5f * dp, 1.5f * dp, paint);
        canvas.drawRoundRect(new RectF(-16f * dp, 13f * dp, 22f * dp, 16f * dp), 1.5f * dp, 1.5f * dp, paint);
        paint.setColor(Color.parseColor("#059669"));
        canvas.drawRect(-42f * dp, -3.5f * dp, -10f * dp, 3.5f * dp, paint);
        canvas.drawRect(-38f * dp, -9f * dp, -34f * dp, 9f * dp, paint);
        tempRect.set(-18f * dp, -12f * dp, 26f * dp, 12f * dp);
        canvas.drawRoundRect(tempRect, 12f * dp, 12f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawOval(new RectF(8f * dp, -9f * dp, 24f * dp, 9f * dp), paint);

        canvas.save();
        canvas.translate(-42f * dp, 0f);
        canvas.rotate(rotorAngle * 2.5f);
        paint.setColor(Color.parseColor("#88475569"));
        canvas.drawCircle(0f, 0f, 6f * dp, paint);
        paint.setColor(Color.parseColor("#111827"));
        canvas.drawRect(-6f * dp, -0.8f * dp, 6f * dp, 0.8f * dp, paint);
        canvas.restore();

        canvas.save();
        canvas.translate(2f * dp, 0f);
        canvas.rotate(rotorAngle);
        paint.setColor(Color.parseColor("#55111827"));
        canvas.drawCircle(0f, 0f, 32f * dp, paint);
        paint.setColor(Color.parseColor("#1F2937"));
        canvas.drawRect(-32f * dp, -2f * dp, 32f * dp, 2f * dp, paint);
        canvas.drawRect(-2f * dp, -32f * dp, 2f * dp, 32f * dp, paint);
        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawCircle(0f, 0f, 3.5f * dp, paint);
        canvas.restore();
    }

    private void drawSportsGT(Canvas canvas, Paint paint, Paint strokePaint, float dp, int themeState, boolean isIcon) {
        float halfL = 28f * dp;
        float halfW = 15f * dp;
        if (!isIcon && themeState != 0 && isEngineOn) {
            paint.setStyle(Paint.Style.FILL);
            LinearGradient beamGrad = new LinearGradient(halfL, 0, halfL + 195f * dp, 0, Color.parseColor("#48FEF08A"), Color.TRANSPARENT, Shader.TileMode.CLAMP);
            paint.setShader(beamGrad);
            tempPath.reset();
            tempPath.moveTo(halfL - 2f * dp, -halfW * 0.72f);
            tempPath.lineTo(halfL + 195f * dp, -halfW * 3.6f);
            tempPath.lineTo(halfL + 195f * dp, halfW * 3.6f);
            tempPath.lineTo(halfL - 2f * dp, halfW * 0.72f);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
            paint.setShader(null);
        }
        if (!isIcon) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#55000000"));
            tempRect.set(-halfL + 4f * dp, -halfW + 5f * dp, halfL + 4f * dp, halfW + 5f * dp);
            canvas.drawRoundRect(tempRect, 9f * dp, 9f * dp, paint);
        }
        paint.setColor(Color.parseColor("#111827"));
        float wheelL = 10f * dp, wheelW = 5f * dp;
        tempRect.set(-halfL * 0.65f - wheelL / 2f, -halfW - 1.5f * dp, -halfL * 0.65f + wheelL / 2f, -halfW + wheelW - 1.5f * dp);
        canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
        tempRect.set(-halfL * 0.65f - wheelL / 2f, halfW - wheelW + 1.5f * dp, -halfL * 0.65f + wheelL / 2f, halfW + 1.5f * dp);
        canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
        float frontX = halfL * 0.60f;
        float steerDeg = isIcon ? 0 : (float) Math.toDegrees(visualSteerAngle);
        for (int side = -1; side <= 1; side += 2) {
            canvas.save();
            canvas.translate(frontX, side * (halfW - 0.5f * dp));
            canvas.rotate(steerDeg);
            tempRect.set(-wheelL / 2f, -wheelW / 2f, wheelL / 2f, wheelW / 2f);
            canvas.drawRoundRect(tempRect, 2f * dp, 2f * dp, paint);
            canvas.restore();
        }
        tempRect.set(-halfL, -halfW, halfL, halfW);
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, paint);
        strokePaint.setColor(Color.parseColor("#0D1B2A"));
        strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, strokePaint);
        paint.setColor(Color.parseColor("#EF4444"));
        canvas.drawRect(-halfL + 2f * dp, -3.2f * dp, halfL - 2f * dp, 3.2f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRoundRect(new RectF(-halfL * 0.35f, -halfW * 0.85f, halfL * 0.35f, halfW * 0.85f), 6f * dp, 6f * dp, paint);
        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawRoundRect(new RectF(-halfL * 0.20f, -halfW * 0.70f, halfL * 0.15f, halfW * 0.70f), 4f * dp, 4f * dp, paint);

        paint.setColor(isEngineOn ? Color.parseColor("#FEF08A") : Color.parseColor("#475569"));
        canvas.drawRoundRect(new RectF(halfL - 3f * dp, -halfW * 0.8f, halfL + 1f * dp, -halfW * 0.4f), 2f * dp, 2f * dp, paint);
        canvas.drawRoundRect(new RectF(halfL - 3f * dp, halfW * 0.4f, halfL + 1f * dp, halfW * 0.8f), 2f * dp, 2f * dp, paint);
        paint.setColor(brakePressed ? Color.parseColor("#FF1744") : Color.parseColor("#DC2626"));
        canvas.drawRoundRect(new RectF(-halfL - 1f * dp, -halfW * 0.85f, -halfL + 2f * dp, -halfW * 0.45f), 1f * dp, 1f * dp, paint);
        canvas.drawRoundRect(new RectF(-halfL - 1f * dp, halfW * 0.45f, -halfL + 2f * dp, halfW * 0.85f), 1f * dp, 1f * dp, paint);
    }

    public static class SkidMark {
        public final float lx, ly, rx, ry;
        public int alpha = 120;
        public SkidMark(float lx, float ly, float rx, float ry) { this.lx = lx; this.ly = ly; this.rx = rx; this.ry = ry; }
    }

    public static class SmokeParticle {
        public float x, y, dx, dy, radius;
        public int color, life, maxLife;
        public SmokeParticle(float x, float y, float dx, float dy, float r, int c, int l) {
            this.x = x; this.y = y; this.dx = dx; this.dy = dy;
            this.radius = r; this.color = c; this.life = l; this.maxLife = l;
        }
    }
}