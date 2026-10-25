package com.abhinav.ownapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SuppressWarnings("all")
public class DriveOnWorld {

    public final float dp;
    public final int themeState;
    public final boolean isSunnyDay, isMoonlightMode, isAmoledStarMode;

    public static final int GRID_COUNT = 12;
    public final float WORLD_SIZE, WORLD_HALF, gridStep;

    public final List<Building> buildings = new ArrayList<>();
    public final List<TreeObstacle> trees = new ArrayList<>();
    public final List<Lake> lakes = new ArrayList<>();
    public final List<OrbPickup> pickups = new ArrayList<>();
    public final List<AmbientStar> stars = new ArrayList<>();
    public final List<SoundWave> hornWaves = new ArrayList<>();

    private final RectF tempRect = new RectF();
    private final Path tempPath = new Path();
    private final DashPathEffect laneDash, kartDash, railSleeperDash, rutDash;
    private final int groundColor, roadAsphaltColor, roadEdgeColor, laneLineColor, sidewalkColor;

    public enum ZoneType { NORMAL, POLICE, HOSPITAL, AIRPORT, NUCLEAR, CONSTRUCTION, AMUSEMENT, FOREST, HOTEL_POOL,CRICKET_STADIUM, FOOTBALL_STADIUM, ASTEROID_CRATER, VOLCANO, HIMALAYA_MOUNTAINS }

    public DriveOnWorld(float dp, int themeState) {
        this.dp = dp; this.themeState = themeState;
        this.isSunnyDay = (themeState == 0); this.isMoonlightMode = (themeState == 1); this.isAmoledStarMode = (themeState == 2);
        this.gridStep = 1600f * dp; this.WORLD_SIZE = gridStep * GRID_COUNT; this.WORLD_HALF = WORLD_SIZE / 2f;

        this.laneDash = new DashPathEffect(new float[]{ 24f * dp, 24f * dp }, 0f);
        this.kartDash = new DashPathEffect(new float[]{ 10f * dp, 10f * dp }, 0f);
        this.railSleeperDash = new DashPathEffect(new float[]{ 3.5f * dp, 7.5f * dp }, 0f);
        this.rutDash = new DashPathEffect(new float[]{ 14f * dp, 8f * dp }, 0f);

        if (isSunnyDay) {
            groundColor = Color.parseColor("#7CB86B"); roadAsphaltColor = Color.parseColor("#334155");
            roadEdgeColor = Color.parseColor("#E2E8F0"); laneLineColor = Color.parseColor("#FACC15"); sidewalkColor = Color.parseColor("#94A3B8");
        } else if (isMoonlightMode) {
            groundColor = Color.parseColor("#071324"); roadAsphaltColor = Color.parseColor("#182232");
            roadEdgeColor = Color.parseColor("#64748B"); laneLineColor = Color.parseColor("#FDE047"); sidewalkColor = Color.parseColor("#283548");
        } else {
            groundColor = Color.parseColor("#030710"); roadAsphaltColor = Color.parseColor("#0D131F");
            roadEdgeColor = Color.parseColor("#475569"); laneLineColor = Color.parseColor("#FACC15"); sidewalkColor = Color.parseColor("#1E293B");
        }
        generateOpenWorld();
    }

    public float wrapMapCoord(float coord) {
        float c = coord % WORLD_SIZE;
        if (c < -WORLD_HALF) c += WORLD_SIZE; else if (c > WORLD_HALF) c -= WORLD_SIZE;
        return c;
    }

    public float getShortestDist(float cam, float obj) {
        float d = (obj - cam) % WORLD_SIZE;
        if (d > WORLD_HALF) d -= WORLD_SIZE; else if (d < -WORLD_HALF) d += WORLD_SIZE;
        return d;
    }

    private int toroidalGridDist(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x1 - x2), dy = Math.abs(y1 - y2);
        return Math.min(dx, GRID_COUNT - dx) + Math.min(dy, GRID_COUNT - dy);
    }

    private int toroidalChebyshevDist(int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x1 - x2), dy = Math.abs(y1 - y2);
        return Math.max(Math.min(dx, GRID_COUNT - dx), Math.min(dy, GRID_COUNT - dy));
    }

    public void generateOpenWorld() {
        buildings.clear(); trees.clear(); lakes.clear(); pickups.clear(); stars.clear(); hornWaves.clear();
        Random r = new Random(2026);
        float roadW = 176f * dp;

        int[][] cellZone = new int[GRID_COUNT][GRID_COUNT];
        for (int ix = 0; ix < GRID_COUNT; ix++) for (int iy = 0; iy < GRID_COUNT; iy++) cellZone[ix][iy] = -1;

        // Well-spaced landmarks near spawn (6,6) obeying non-adjacency (Chebyshev >= 2) & 6+ block repeat
        cellZone[6][6] = ZoneType.HOTEL_POOL.ordinal();   // Luxury Hotel & Swimming Pool Estate right near spawn!
        cellZone[4][6] = ZoneType.CONSTRUCTION.ordinal();
        cellZone[6][4] = ZoneType.AIRPORT.ordinal();
        cellZone[8][6] = ZoneType.AMUSEMENT.ordinal();
        cellZone[6][8] = ZoneType.FOREST.ordinal();
        cellZone[8][8] = -2;                              // Botanical Lotus Lake
        cellZone[0][4] = ZoneType.HOSPITAL.ordinal();
        cellZone[11][4] = ZoneType.POLICE.ordinal();
        cellZone[0][0] = ZoneType.NUCLEAR.ordinal();
        cellZone[2][6] = ZoneType.FOOTBALL_STADIUM.ordinal(); // International Football Stadium near spawn!
        cellZone[4][8] = ZoneType.CRICKET_STADIUM.ordinal();  // International Cricket Stadium near spawn!

        // 1. Reserve SINGLE 3x3 Super-Block (10x area) for the Asteroid Impact Crater at (1..3, 7..9)
        for (int cx = 1; cx <= 3; cx++) {
            for (int cy = 7; cy <= 9; cy++) {
                cellZone[cx][cy] = (cx == 2 && cy == 8) ? ZoneType.ASTEROID_CRATER.ordinal() : -3;
            }
        }

        // 2. Reserve SINGLE 3x3 Super-Block (10x area) for the Active Volcano at (9..11, 8..10)
        for (int vx = 9; vx <= 11; vx++) {
            for (int vy = 8; vy <= 10; vy++) {
                cellZone[vx][vy] = (vx == 10 && vy == 9) ? ZoneType.VOLCANO.ordinal() : -3;
            }
        }

        // 3. Reserve SINGLE 4x4 Super-Block (16 cells = 15x area) for the Great Himalaya Mountains at (7..10, 0..3)
        for (int hx = 7; hx <= 10; hx++) {
            for (int hy = 0; hy <= 3; hy++) {
                cellZone[hx][hy] = (hx == 8 && hy == 1) ? ZoneType.HIMALAYA_MOUNTAINS.ordinal() : -3;
            }
        }

        int[] extraSpecials = {
                ZoneType.HOTEL_POOL.ordinal(), -2, ZoneType.CONSTRUCTION.ordinal(), ZoneType.FOREST.ordinal(),
                ZoneType.HOSPITAL.ordinal(), ZoneType.POLICE.ordinal(), ZoneType.CRICKET_STADIUM.ordinal(),
                ZoneType.AIRPORT.ordinal(), ZoneType.NUCLEAR.ordinal(), ZoneType.AMUSEMENT.ordinal(), ZoneType.FOOTBALL_STADIUM.ordinal()
        };

        for (int spCode : extraSpecials) {
            boolean placed = false;
            for (int attempt = 0; attempt < 250 && !placed; attempt++) {
                int cxIdx = r.nextInt(GRID_COUNT), cyIdx = r.nextInt(GRID_COUNT);
                if (cellZone[cxIdx][cyIdx] != -1) continue;
                boolean valid = true;
                for (int ix = 0; ix < GRID_COUNT && valid; ix++) {
                    for (int iy = 0; iy < GRID_COUNT; iy++) {
                        int existing = cellZone[ix][iy];
                        if (existing == -1) continue;
                        if (toroidalChebyshevDist(cxIdx, cyIdx, ix, iy) < 2 || toroidalGridDist(cxIdx, cyIdx, ix, iy) < 3) { valid = false; break; }
                        if (existing == spCode && toroidalGridDist(cxIdx, cyIdx, ix, iy) < 6) { valid = false; break; }
                    }
                }
                if (valid) { cellZone[cxIdx][cyIdx] = spCode; placed = true; }
            }
        }

        int archCounter = 0;
        for (int ix = 0; ix < GRID_COUNT; ix++) {
            for (int iy = 0; iy < GRID_COUNT; iy++) {
                float gx = -WORLD_HALF + ix * gridStep, gy = -WORLD_HALF + iy * gridStep;
                float blockLeft = gx + roadW * 0.65f, blockRight = gx + gridStep - roadW * 0.65f;
                float blockTop = gy + roadW * 0.65f, blockBottom = gy + gridStep - roadW * 0.65f;
                float cx = (blockLeft + blockRight) / 2f, cy = (blockTop + blockBottom) / 2f, blockW = blockRight - blockLeft;

                int code = cellZone[ix][iy];
                if (code == -3) {
                    continue; // Absorbed into a Mega-Zone (Crater, Volcano, or Himalaya Mountains)
                } else if (code == -2) {
                    lakes.add(new Lake(cx, cy, blockW * 0.90f, blockW * 0.90f, dp));
                } else if (code != -1) {
                    ZoneType zt = ZoneType.values()[code];
                    if (zt == ZoneType.HIMALAYA_MOUNTAINS) {
                        // 15x Area Super-Landmark (sqrt(15) = 3.873f) centered across the 4x4 block (7..10, 0..3)!
                        float himalayaSpan = blockW * 3.873f;
                        float hCenterX = cx + gridStep * 0.5f;
                        float hCenterY = cy + gridStep * 0.5f;
                        buildings.add(new Building(hCenterX - himalayaSpan * 0.5f, hCenterY - himalayaSpan * 0.5f, himalayaSpan, himalayaSpan, zt, 0, dp));
                    } else if (zt == ZoneType.ASTEROID_CRATER || zt == ZoneType.VOLCANO) {
                        // 10x Area Super-Landmark spanning across the 3x3 block sector!
                        float megaSpan = blockW * 3.16f;
                        buildings.add(new Building(cx - megaSpan * 0.5f, cy - megaSpan * 0.5f, megaSpan, megaSpan, zt, 0, dp));
                    } else if (zt == ZoneType.AMUSEMENT || zt == ZoneType.FOREST || zt == ZoneType.AIRPORT || zt == ZoneType.CONSTRUCTION || zt == ZoneType.HOTEL_POOL || zt == ZoneType.CRICKET_STADIUM || zt == ZoneType.NUCLEAR || zt == ZoneType.FOOTBALL_STADIUM) {
                        buildings.add(new Building(cx - blockW * 0.45f, cy - blockW * 0.45f, blockW * 0.90f, blockW * 0.90f, zt, 0, dp));
                    } else {
                        buildings.add(new Building(cx - blockW * 0.38f, cy - blockW * 0.38f, blockW * 0.76f, blockW * 0.76f, zt, 0, dp));
                    }
                } else {
                    float subW = blockW * 0.36f;
                    float[][] offsets = {
                            { blockLeft + 52f * dp, blockTop + 52f * dp }, { cx + 22f * dp, blockTop + 52f * dp },
                            { blockLeft + 52f * dp, cy + 22f * dp },        { cx + 22f * dp, cy + 22f * dp }
                    };
                    for (int s = 0; s < 4; s++) {
                        int styleId = (archCounter * 5 + ix * 3 + iy * 7 + s) % 8;
                        archCounter++;
                        buildings.add(new Building(offsets[s][0], offsets[s][1], subW, subW, ZoneType.NORMAL, styleId, dp));
                    }
                }

                if (code == -1 || code == ZoneType.HOSPITAL.ordinal() || code == ZoneType.POLICE.ordinal() || code == ZoneType.NUCLEAR.ordinal()) {
                    trees.add(new TreeObstacle(blockLeft + 20f * dp, blockTop + 20f * dp, 28f * dp));
                    trees.add(new TreeObstacle(blockRight - 20f * dp, blockTop + 20f * dp, 26f * dp));
                    trees.add(new TreeObstacle(blockLeft + 20f * dp, blockBottom - 20f * dp, 26f * dp));
                    trees.add(new TreeObstacle(blockRight - 20f * dp, blockBottom - 20f * dp, 28f * dp));
                }
            }
        }

        for (int i = 0; i < 240; i++) spawnRandomPickup();

        if (!isSunnyDay) {
            int[] starPalette = { Color.WHITE, Color.parseColor("#E0F2FE"), Color.parseColor("#FEF08A"), Color.parseColor("#BAE6FD") };
            int starCount = isAmoledStarMode ? 450 : 280;
            for (int i = 0; i < starCount; i++) {
                float sx = (float) ((Math.random() - 0.5) * WORLD_SIZE), sy = (float) ((Math.random() - 0.5) * WORLD_SIZE);
                stars.add(new AmbientStar(sx, sy, (1.3f + (float) Math.random() * 2.2f) * dp, starPalette[i % starPalette.length], (float) (Math.random() * Math.PI * 2)));
            }
        }
    }

    public void spawnRandomPickup() {
        float px = wrapMapCoord((float) ((Math.random() - 0.5) * WORLD_SIZE));
        float py = wrapMapCoord((float) ((Math.random() - 0.5) * WORLD_SIZE));
        boolean inObstacle = false;
        for (Building b : buildings) if (b.rect.contains(px, py)) { inObstacle = true; break; }
        if (!inObstacle) for (Lake l : lakes) if (l.rect.contains(px, py)) { inObstacle = true; break; }
        if (!inObstacle) for (TreeObstacle t : trees) if (Math.hypot(t.x - px, t.y - py) < t.radius) { inObstacle = true; break; }

        int kind = 0; double rng = Math.random();
        if (rng < 0.25) kind = 2; else if (rng < 0.6) kind = 1;
        int altitude = inObstacle ? 1 : 0;

        if (!inObstacle && Math.random() < 0.5) {
            boolean horiz = Math.random() < 0.5;
            float roadPos = (Math.round(((Math.random() - 0.5) * WORLD_SIZE) / gridStep) * gridStep);
            px = horiz ? px : roadPos + (float) ((Math.random() - 0.5) * 45f * dp);
            py = horiz ? roadPos + (float) ((Math.random() - 0.5) * 45f * dp) : py;
        }
        pickups.add(new OrbPickup(px, py, kind, altitude));
    }

    public void triggerHornWave(float x, float y) { hornWaves.add(new SoundWave(x, y, 260f * dp)); }

    public boolean isPointOnRoad(float px, float py) {
        float halfRoad = (176f * dp) * 0.52f;
        float wrapX = wrapMapCoord(px), wrapY = wrapMapCoord(py);
        if (Math.abs(wrapX % gridStep) <= halfRoad || Math.abs((wrapX % gridStep) - gridStep) <= halfRoad) return true;
        if (Math.abs(wrapY % gridStep) <= halfRoad || Math.abs((wrapY % gridStep) - gridStep) <= halfRoad) return true;
        return false;
    }

    private void resolveBoxCollision(DriveOnCar car, DriveOnAudio audio, float boxL, float boxT, float boxR, float boxB, float cX, float cY, float carR) {
        float closestX = Math.max(boxL, Math.min(cX, boxR)), closestY = Math.max(boxT, Math.min(cY, boxB));
        float ix = cX - closestX, iy = cY - closestY, distSq = ix * ix + iy * iy;
        if (distSq < carR * carR) {
            float dist = (float) Math.sqrt(distSq);
            float nx, ny, overlap;
            if (dist > 0.001f) {
                nx = ix / dist; ny = iy / dist; overlap = carR - dist;
            } else {
                float dL = Math.abs(cX - boxL), dR = Math.abs(boxR - cX), dT = Math.abs(cY - boxT), dB = Math.abs(boxB - cY);
                float min = Math.min(Math.min(dL, dR), Math.min(dT, dB));
                if (min == dL) { nx = -1f; ny = 0f; overlap = dL + carR; }
                else if (min == dR) { nx = 1f; ny = 0f; overlap = dR + carR; }
                else if (min == dT) { nx = 0f; ny = -1f; overlap = dT + carR; }
                else { nx = 0f; ny = 1f; overlap = dB + carR; }
            }
            car.x = wrapMapCoord(car.x + nx * overlap); car.y = wrapMapCoord(car.y + ny * overlap);
            float impactSpd = (float) Math.hypot(car.vx, car.vy) / dp, vn = car.vx * nx + car.vy * ny;
            if (vn < 0) { car.vx = (car.vx - 1.45f * vn * nx) * 0.65f; car.vy = (car.vy - 1.45f * vn * ny) * 0.65f; }
            if (impactSpd > 1.8f && audio != null) { audio.playBuildingCrash(impactSpd / 13f); audio.vibrateCrash(impactSpd / 13f); }
        }
    }

    public int updateAndResolveCollisions(DriveOnCar car, DriveOnAudio audio) {
        int earnedPoints = 0; float carR = 26f * dp;
        boolean isFlying = (car.carModel == DriveOnCar.MODEL_HELICOPTER);

        if (!isFlying) {
            for (Building b : buildings) {
                float dx = getShortestDist(b.rect.centerX(), car.x), dy = getShortestDist(b.rect.centerY(), car.y);
                if (Math.abs(dx) > b.rect.width() || Math.abs(dy) > b.rect.height()) continue;
                float cX = b.rect.centerX() + dx, cY = b.rect.centerY() + dy;
                resolveBoxCollision(car, audio, b.rect.left, b.rect.top, b.rect.right, b.rect.bottom, cX, cY, carR);
            }

            for (Lake l : lakes) {
                float dx = getShortestDist(l.cx, car.x), dy = getShortestDist(l.cy, car.y);
                if (Math.abs(dx) > l.rect.width() || Math.abs(dy) > l.rect.height()) continue;
                float cX = l.cx + dx, cY = l.cy + dy;
                resolveBoxCollision(car, audio, l.rect.left, l.rect.top, l.rect.right, l.rect.bottom, cX, cY, carR);
            }

            for (TreeObstacle t : trees) {
                float dx = getShortestDist(t.x, car.x), dy = getShortestDist(t.y, car.y), dist = (float) Math.hypot(dx, dy);
                float minDist = carR + t.radius * 0.72f;
                if (dist < minDist) {
                    float nx = (dist > 0.001f) ? (dx / dist) : 1f, ny = (dist > 0.001f) ? (dy / dist) : 0f;
                    car.x = wrapMapCoord(car.x + nx * (minDist - dist)); car.y = wrapMapCoord(car.y + ny * (minDist - dist));
                    float impactSpd = (float) Math.hypot(car.vx, car.vy) / dp;
                    car.vx = (car.vx - 1.35f * (car.vx * nx + car.vy * ny) * nx) * 0.72f;
                    car.vy = (car.vy - 1.35f * (car.vx * nx + car.vy * ny) * ny) * 0.72f;
                    if (impactSpd > 1.8f && audio != null) { audio.playTreeCrash(impactSpd / 14f); audio.vibrateCrash(impactSpd / 14f); }
                }
            }
        }

        for (int i = pickups.size() - 1; i >= 0; i--) {
            OrbPickup orb = pickups.get(i);
            if ((isFlying && orb.altitude == 0) || (!isFlying && orb.altitude == 1)) continue;
            float dx = getShortestDist(orb.x, car.x), dy = getShortestDist(orb.y, car.y);
            if (Math.hypot(dx, dy) < carR + 24f * dp) {
                if (orb.kind == 2) { car.nitro = car.maxNitro; earnedPoints += 15; }
                else { earnedPoints += (orb.kind == 1) ? 50 : 25; car.nitro = Math.min(car.maxNitro, car.nitro + (orb.kind == 1 ? 45f : 25f)); }
                if (orb.altitude == 1) earnedPoints += 25;
                car.coinsCollected++;
                if (audio != null) audio.playPickupChime();
                pickups.remove(i); spawnRandomPickup();
            }
        }

        for (int i = hornWaves.size() - 1; i >= 0; i--) {
            SoundWave sw = hornWaves.get(i);
            sw.radius += (sw.maxRadius - sw.radius) * 0.18f; sw.alpha -= 12;
            if (sw.alpha <= 0) hornWaves.remove(i);
        }
        return earnedPoints;
    }

    public void drawWorldGroundAndRoads(Canvas canvas, Paint paint, Paint strokePaint, float camX, float camY, float viewW, float viewH, float starPhase) {
        canvas.drawColor(groundColor);
        if (isAmoledStarMode) drawAmbientStars(canvas, paint, camX, camY, viewW, viewH, starPhase);

        float halfR = (176f * dp) * 0.5f, sidewalkW = halfR + 18f * dp;
        float srX = (float) Math.floor((camX - viewW) / gridStep) * gridStep;
        float srY = (float) Math.floor((camY - viewH) / gridStep) * gridStep;

        for (float gx = srX; gx <= camX + viewW; gx += gridStep) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(sidewalkColor); canvas.drawRect(gx - sidewalkW, camY - viewH, gx + sidewalkW, camY + viewH, paint);
            paint.setColor(roadAsphaltColor); canvas.drawRect(gx - halfR, camY - viewH, gx + halfR, camY + viewH, paint);

            strokePaint.setColor(roadEdgeColor); strokePaint.setStrokeWidth(2.4f * dp);
            canvas.drawLine(gx - halfR, camY - viewH, gx - halfR, camY + viewH, strokePaint);
            canvas.drawLine(gx + halfR, camY - viewH, gx + halfR, camY + viewH, strokePaint);

            strokePaint.setColor(laneLineColor); strokePaint.setStrokeWidth(2.2f * dp);
            canvas.drawLine(gx - 3f * dp, camY - viewH, gx - 3f * dp, camY + viewH, strokePaint);
            canvas.drawLine(gx + 3f * dp, camY - viewH, gx + 3f * dp, camY + viewH, strokePaint);

            strokePaint.setColor(roadEdgeColor); strokePaint.setStrokeWidth(1.8f * dp); strokePaint.setPathEffect(laneDash);
            canvas.drawLine(gx - halfR * 0.5f, camY - viewH, gx - halfR * 0.5f, camY + viewH, strokePaint);
            canvas.drawLine(gx + halfR * 0.5f, camY - viewH, gx + halfR * 0.5f, camY + viewH, strokePaint);
            strokePaint.setPathEffect(null);
        }

        for (float gy = srY; gy <= camY + viewH; gy += gridStep) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(sidewalkColor); canvas.drawRect(camX - viewW, gy - sidewalkW, camX + viewW, gy + sidewalkW, paint);
            paint.setColor(roadAsphaltColor); canvas.drawRect(camX - viewW, gy - halfR, camX + viewW, gy + halfR, paint);

            strokePaint.setColor(roadEdgeColor); strokePaint.setStrokeWidth(2.4f * dp);
            canvas.drawLine(camX - viewW, gy - halfR, camX + viewW, gy - halfR, strokePaint);
            canvas.drawLine(camX - viewW, gy + halfR, camX + viewW, gy + halfR, strokePaint);

            strokePaint.setColor(laneLineColor); strokePaint.setStrokeWidth(2.2f * dp);
            canvas.drawLine(camX - viewW, gy - 3f * dp, camX + viewW, gy - 3f * dp, strokePaint);
            canvas.drawLine(camX - viewW, gy + 3f * dp, camX + viewW, gy + 3f * dp, strokePaint);

            strokePaint.setColor(roadEdgeColor); strokePaint.setStrokeWidth(1.8f * dp); strokePaint.setPathEffect(laneDash);
            canvas.drawLine(camX - viewW, gy - halfR * 0.5f, camX + viewW, gy - halfR * 0.5f, strokePaint);
            canvas.drawLine(camX - viewW, gy + halfR * 0.5f, camX + viewW, gy + halfR * 0.5f, strokePaint);
            strokePaint.setPathEffect(null);

            for (float gx = srX; gx <= camX + viewW; gx += gridStep) {
                paint.setColor(roadAsphaltColor); canvas.drawRect(gx - halfR, gy - halfR, gx + halfR, gy + halfR, paint);
                paint.setColor(Color.parseColor("#CCE2E8F0"));
                for (int s = -3; s <= 3; s++) {
                    float off = s * 18f * dp;
                    canvas.drawRect(gx + off - 4f * dp, gy - halfR - 14f * dp, gx + off + 4f * dp, gy - halfR - 2f * dp, paint);
                    canvas.drawRect(gx + off - 4f * dp, gy + halfR + 2f * dp, gx + off + 4f * dp, gy + halfR + 14f * dp, paint);
                    canvas.drawRect(gx - halfR - 14f * dp, gy + off - 4f * dp, gx - halfR - 2f * dp, gy + off + 4f * dp, paint);
                    canvas.drawRect(gx + halfR + 2f * dp, gy + off - 4f * dp, gx + halfR + 14f * dp, gy + off + 4f * dp, paint);
                }
            }
        }
    }

    /**
     * Renders the 2.5D Luxury Hotel / Villa Swimming Pool Estate matching the aerial reference photo:
     * Manicured emerald lawn, dark mulch landscaping beds with hostas & shrubs, sweeping travertine
     * stone pool deck, figure-8 freeform lagoon swimming pool with animated water ripples, submerged
     * shallow steps, white diving board, stacked-boulder waterfall grotto, circular cobblestone fire pit
     * lounge with navy club chairs, clamshell canopy daybed, twin sun loungers, raised dining terrace
     * with terracotta umbrella & stainless BBQ bar, boomerang standing-seam metal-roof pool house,
     * and the multi-gabled luxury mansion house on the left.
     */
    /**
     * Renders the 2.5D Mediterranean Luxury Hotel & Multi-Pool Resort Complex (Image 1 Model):
     * - Smooth organic grey flagstone pool deck (NO grid/table lines) with radial stone paving patches
     * - Central freeform cyan lagoon swimming pool with multi-depth water & animated caustic ripples
     * - Central circular island with turquoise whirlpool/jacuzzi, sun loungers, palms & 3 white bridges
     * - Octagonal wooden/thatched pool bar gazebo, submerged palm islet & cascading kids' splash pools
     * - West Wing: Rectangular cyan lap pool, 3 private plunge-pool villas, side villa wing & 4-square courtyard
     * - North & East Main Hotel Complex: Terracotta pitched roofs, flat white/grey HVAC roofs with ductwork,
     *   curved amphitheater pergola terrace, reflective colonnade pools, roundabout & parked cars.
     */
    private void drawDetailedHotelPoolEstate25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int baseEarthCol    = isSunnyDay ? Color.parseColor("#C8B598") : (isMoonlightMode ? Color.parseColor("#1E2631") : Color.parseColor("#111722"));
        int lawnGreenCol    = isSunnyDay ? Color.parseColor("#4F7932") : (isMoonlightMode ? Color.parseColor("#163A24") : Color.parseColor("#0C2617"));
        int darkFoliageCol  = isSunnyDay ? Color.parseColor("#2E5222") : (isMoonlightMode ? Color.parseColor("#0F291A") : Color.parseColor("#07190F"));
        int flagstoneBase   = isSunnyDay ? Color.parseColor("#B6BEBC") : Color.parseColor("#475569");
        int flagstoneLight  = isSunnyDay ? Color.parseColor("#CBD2D0") : Color.parseColor("#64748B");
        int flagstoneDark   = isSunnyDay ? Color.parseColor("#9DA7A5") : Color.parseColor("#334155");
        int creamDeckCol    = isSunnyDay ? Color.parseColor("#EAE3D2") : Color.parseColor("#94A3B8");
        int creamShadowCol  = isSunnyDay ? Color.parseColor("#CFC5B0") : Color.parseColor("#64748B");
        int terracottaDark  = isSunnyDay ? Color.parseColor("#9C5B34") : Color.parseColor("#5C341D");
        int terracottaLight = isSunnyDay ? Color.parseColor("#B87044") : Color.parseColor("#784527");
        int flatRoofGrey    = isSunnyDay ? Color.parseColor("#D5DBD8") : Color.parseColor("#475569");
        int flatRoofWhite   = isSunnyDay ? Color.parseColor("#EEF2F5") : Color.parseColor("#64748B");
        int poolRimWhite    = isSunnyDay ? Color.parseColor("#F8FAFC") : Color.parseColor("#CBD5E1");
        int waterOuter      = isSunnyDay ? Color.parseColor("#29C5F6") : Color.parseColor("#0284C7");
        int waterMid        = isSunnyDay ? Color.parseColor("#00B0FF") : Color.parseColor("#0369A1");
        int waterDeep       = isSunnyDay ? Color.parseColor("#0091EA") : Color.parseColor("#075985");
        int waterShallow    = isSunnyDay ? Color.parseColor("#70E1FF") : Color.parseColor("#38BDF8");

        // 1. Estate Drop Shadow & Base Coastal Ground
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(cx - w + 10f * dp, cy - h + 12f * dp, cx + w + 10f * dp, cy + h + 12f * dp), 26f * dp, 26f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(baseEarthCol);
        canvas.drawRoundRect(tempRect, 26f * dp, 26f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 26f * dp, 26f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Lush Resort Greenery Zones (Top-Left Forest, Bottom Park Lawn, Right Buffer)
        paint.setColor(darkFoliageCol);
        canvas.drawRect(cx - w, cy - h, cx - w * 0.25f, cy + h * 0.12f, paint);
        canvas.drawCircle(cx - w * 0.55f, cy + h * 0.08f, w * 0.35f, paint);
        canvas.drawOval(new RectF(cx + w * 0.45f, cy - h * 0.15f, cx + w * 0.78f, cy + h * 0.85f), paint);

        paint.setColor(lawnGreenCol);
        canvas.drawOval(new RectF(cx - w * 0.72f, cy + h * 0.62f, cx + w * 0.65f, cy + h * 1.18f), paint);

        // Bottom-Left Coastal Scrub Texture & Small Tennis/Sport Court
        paint.setColor(isSunnyDay ? Color.parseColor("#A48E6E") : Color.parseColor("#1E242B"));
        canvas.drawOval(new RectF(cx - w * 1.05f, cy + h * 0.06f, cx - w * 0.32f, cy + h * 0.88f), paint);
        canvas.save();
        canvas.translate(cx - w * 0.55f, cy + h * 0.80f);
        canvas.rotate(-22f);
        paint.setColor(Color.parseColor("#5C5346"));
        canvas.drawRoundRect(new RectF(-20f * dp, -10f * dp, 20f * dp, 10f * dp), 2f * dp, 2f * dp, paint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1.2f * dp);
        canvas.drawRect(-16f * dp, -7f * dp, 16f * dp, 7f * dp, strokePaint);
        canvas.drawLine(0f, -7f * dp, 0f, 7f * dp, strokePaint);
        canvas.restore();

        // Sweeping Southern Curved Stone Promenade Path
        tempPath.reset();
        tempPath.moveTo(cx - w * 0.68f, cy + h * 0.98f);
        tempPath.quadTo(cx - w * 0.08f, cy + h * 0.58f, cx + w * 0.34f, cy + h * 0.98f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(creamShadowCol);
        strokePaint.setStrokeWidth(14f * dp);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(creamDeckCol);
        strokePaint.setStrokeWidth(10f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // =========================================================================
        // 2. ORGANIC GREY FLAGSTONE LAGOON PATIO (NO TABLE/GRID LINES)
        // =========================================================================
        float poolCX = cx - w * 0.01f, poolCY = cy + h * 0.12f;
        float poolR = w * 0.25f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(flagstoneDark);
        canvas.drawOval(new RectF(poolCX - w * 0.34f, poolCY - h * 0.35f, poolCX + w * 0.35f, poolCY + h * 0.36f), paint);
        paint.setColor(flagstoneBase);
        canvas.drawOval(new RectF(poolCX - w * 0.33f, poolCY - h * 0.34f, poolCX + w * 0.34f, poolCY + h * 0.35f), paint);

        // Lower-Left Terraced Patio Extension for Cascading Kids' Splash Pools
        canvas.drawOval(new RectF(poolCX - w * 0.39f, poolCY + h * 0.06f, poolCX - w * 0.16f, poolCY + h * 0.44f), paint);

        // Organic Flagstone Slab Patches (Natural Crazy-Paving Texture without Grid Lines)
        float[][] stonePatches = {
                { -0.26f, -0.18f, 22f, 14f }, { -0.18f, -0.27f, 25f, 15f }, {  0.04f, -0.28f, 28f, 14f },
                {  0.22f, -0.20f, 24f, 16f }, {  0.27f, -0.02f, 20f, 26f }, {  0.25f,  0.18f, 24f, 18f },
                {  0.12f,  0.28f, 28f, 15f }, { -0.08f,  0.29f, 26f, 14f }, { -0.25f,  0.22f, 22f, 16f },
                { -0.30f,  0.04f, 18f, 24f }, {  0.18f,  0.26f, 22f, 14f }, { -0.22f, -0.08f, 20f, 15f }
        };
        for (int i = 0; i < stonePatches.length; i++) {
            paint.setColor((i % 2 == 0) ? flagstoneLight : flagstoneDark);
            float sx = poolCX + stonePatches[i][0] * w;
            float sy = poolCY + stonePatches[i][1] * h;
            float srw = stonePatches[i][2] * dp, srh = stonePatches[i][3] * dp;
            canvas.drawRoundRect(new RectF(sx - srw, sy - srh, sx + srw, sy + srh), 8f * dp, 8f * dp, paint);
        }

        // Upper-Left Semi-Circular Amphitheater Steps & Curved Red Flowerbed
        float stepCX = cx - w * 0.28f, stepCY = cy - h * 0.33f;
        paint.setColor(creamShadowCol);
        canvas.drawCircle(stepCX, stepCY, w * 0.082f, paint);
        paint.setColor(creamDeckCol);
        canvas.drawCircle(stepCX, stepCY, w * 0.068f, paint);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawCircle(stepCX, stepCY, w * 0.052f, paint);
        // Magenta Bougainvillea Curved Planter Bed
        strokePaint.setColor(Color.parseColor("#9D174D"));
        strokePaint.setStrokeWidth(7f * dp);
        canvas.drawArc(new RectF(stepCX - w * 0.06f, stepCY + h * 0.04f, stepCX + w * 0.06f, stepCY + h * 0.16f), 115f, 130f, false, strokePaint);

        // =========================================================================
        // 3. CENTRAL FREEFORM LAGOON SWIMMING POOL, ISLAND JACUZZI & BRIDGES
        // =========================================================================
        // Crisp White Stone Coping Rim
        buildLagoonPoolPath(tempPath, poolCX, poolCY, poolR, poolR, 1.05f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(poolRimWhite);
        canvas.drawPath(tempPath, paint);

        // Outer Shallow Turquoise Water
        buildLagoonPoolPath(tempPath, poolCX, poolCY, poolR, poolR, 1.00f);
        paint.setColor(waterOuter);
        canvas.drawPath(tempPath, paint);

        // Sunlit Shallow Beach Shelf (Right & Bottom-Right Curve)
        buildLagoonPoolPath(tempPath, poolCX + 4f * dp, poolCY + 3f * dp, poolR, poolR, 0.92f);
        paint.setColor(waterShallow);
        canvas.drawPath(tempPath, paint);

        // Mid-Depth Bright Cyan Water
        buildLagoonPoolPath(tempPath, poolCX, poolCY, poolR, poolR, 0.84f);
        paint.setColor(waterMid);
        canvas.drawPath(tempPath, paint);

        // Deep-Core Azure Water
        buildLagoonPoolPath(tempPath, poolCX - 4f * dp, poolCY - 2f * dp, poolR, poolR, 0.62f);
        paint.setColor(waterDeep);
        canvas.drawPath(tempPath, paint);

        // Night Underwater LED Glow
        if (!isSunnyDay) {
            paint.setColor(Color.parseColor("#5538BDF8"));
            canvas.drawCircle(poolCX - poolR * 0.42f, poolCY + poolR * 0.25f, 34f * dp, paint);
            canvas.drawCircle(poolCX + poolR * 0.45f, poolCY + poolR * 0.15f, 34f * dp, paint);
            canvas.drawCircle(poolCX, poolCY - poolR * 0.48f, 28f * dp, paint);
        }

        // Animated Shimmering Caustic Water Ripples across the Lagoon Pool
        drawAnimatedWaterRipples(canvas, strokePaint, poolCX - poolR * 0.35f, poolCY + poolR * 0.18f, poolR * 0.38f, poolR * 0.26f, starPhase);
        drawAnimatedWaterRipples(canvas, strokePaint, poolCX + poolR * 0.42f, poolCY + poolR * 0.16f, poolR * 0.34f, poolR * 0.32f, starPhase + 1.7f);
        drawAnimatedWaterRipples(canvas, strokePaint, poolCX - poolR * 0.04f, poolCY - poolR * 0.52f, poolR * 0.36f, poolR * 0.18f, starPhase + 3.1f);

        // Submerged Green Palm Islet in the Lower-Left Lagoon Lobe
        float miniIslX = poolCX - poolR * 0.56f, miniIslY = poolCY + poolR * 0.42f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(poolRimWhite);
        canvas.drawCircle(miniIslX, miniIslY, 13f * dp, paint);
        paint.setColor(lawnGreenCol);
        canvas.drawCircle(miniIslX, miniIslY, 10f * dp, paint);
        drawResortPalmTree(canvas, strokePaint, paint, miniIslX, miniIslY, 11f * dp);

        // Central Circular Island & Inner Turquoise Whirlpool / Jacuzzi Pool
        float islX = poolCX + poolR * 0.10f, islY = poolCY - poolR * 0.04f, islR = poolR * 0.46f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#33000000"));
        canvas.drawCircle(islX + 4f * dp, islY + 5f * dp, islR, paint);
        paint.setColor(poolRimWhite);
        canvas.drawCircle(islX, islY, islR, paint);
        paint.setColor(creamDeckCol);
        canvas.drawCircle(islX, islY, islR * 0.92f, paint);

        // Radial Sun-Lounger Wooden Deck Segments on the Central Island
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        strokePaint.setColor(Color.parseColor("#8D6E63"));
        strokePaint.setStrokeWidth(7f * dp);
        RectF islLoungerArc = new RectF(islX - islR * 0.72f, islY - islR * 0.72f, islX + islR * 0.72f, islY + islR * 0.72f);
        canvas.drawArc(islLoungerArc, -65f, 110f, false, strokePaint);
        canvas.drawArc(islLoungerArc, 110f, 95f, false, strokePaint);

        // Central Circular Turquoise Jacuzzi Pool inside the Island
        float jacR = islR * 0.46f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(poolRimWhite);
        canvas.drawCircle(islX, islY, jacR + 3f * dp, paint);
        paint.setColor(waterOuter);
        canvas.drawCircle(islX, islY, jacR, paint);
        paint.setColor(waterMid);
        canvas.drawCircle(islX, islY, jacR * 0.70f, paint);
        paint.setColor(poolRimWhite);
        canvas.drawCircle(islX, islY, 2.5f * dp, paint); // Center fountain jet

        // Animated Concentric Ripples inside the Central Jacuzzi
        strokePaint.setStyle(Paint.Style.STROKE);
        for (int r = 0; r < 3; r++) {
            float p = ((starPhase * 0.45f) + r * 0.33f) % 1.0f;
            strokePaint.setColor(Color.argb(Math.max(0, Math.round((1f - p) * 190f)), 224, 247, 250));
            strokePaint.setStrokeWidth(1.5f * dp);
            canvas.drawCircle(islX, islY, 3f * dp + p * (jacR * 0.85f), strokePaint);
        }

        // Palm Trees on the Central Island Ring
        drawResortPalmTree(canvas, strokePaint, paint, islX - islR * 0.62f, islY - islR * 0.42f, 16f * dp);
        drawResortPalmTree(canvas, strokePaint, paint, islX - islR * 0.68f, islY + islR * 0.15f, 14f * dp);
        drawResortPalmTree(canvas, strokePaint, paint, islX + islR * 0.64f, islY - islR * 0.12f, 15f * dp);
        drawResortPalmTree(canvas, strokePaint, paint, islX - islR * 0.15f, islY + islR * 0.72f, 13f * dp);

        // 3 White Footbridges Connecting the Central Island to the Outer Pool Deck
        drawPoolFootbridge(canvas, paint, strokePaint, islX + islR * 0.65f, islY - islR * 0.65f, poolCX + poolR * 0.56f, poolCY - poolR * 0.52f);
        drawPoolFootbridge(canvas, paint, strokePaint, islX - islR * 0.18f, islY + islR * 0.90f, poolCX - poolR * 0.16f, poolCY + poolR * 0.86f);
        drawPoolFootbridge(canvas, paint, strokePaint, islX - islR * 0.92f, islY - islR * 0.15f, poolCX - poolR * 0.44f, poolCY - poolR * 0.12f);

        // Octagonal Wooden/Thatched Pool Bar Gazebo (Left Edge of Lagoon Pool)
        float gazX = poolCX - poolR * 0.58f, gazY = poolCY - poolR * 0.08f, gazR = 24f * dp;
        drawConicalRoof25D(canvas, paint, gazX, gazY, gazR, Color.parseColor("#8D5B3C"), Color.parseColor("#A6704F"), Color.parseColor("#6E442A"));

        // Lower-Left Cascading Half-Moon & Circular Kids' Splash Pools
        float kp1X = poolCX - poolR * 0.96f, kp1Y = poolCY + poolR * 0.62f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(poolRimWhite);
        canvas.drawOval(new RectF(kp1X - 24f * dp, kp1Y - 14f * dp, kp1X + 24f * dp, kp1Y + 14f * dp), paint);
        paint.setColor(waterOuter);
        canvas.drawOval(new RectF(kp1X - 20f * dp, kp1Y - 11f * dp, kp1X + 20f * dp, kp1Y + 11f * dp), paint);

        float kp2X = poolCX - poolR * 0.90f, kp2Y = poolCY + poolR * 0.96f;
        paint.setColor(poolRimWhite);
        canvas.drawCircle(kp2X, kp2Y, 15f * dp, paint);
        paint.setColor(waterMid);
        canvas.drawCircle(kp2X, kp2Y, 12f * dp, paint);

        // Tiny Sun Lounger Pairs & Umbrellas Around the Outer Lagoon Deck
        float[][] deckLoungers = {
                { poolCX - poolR * 0.82f, poolCY + poolR * 0.25f, 65f },
                { poolCX - poolR * 0.72f, poolCY + poolR * 0.52f, 40f },
                { poolCX - poolR * 0.05f, poolCY + poolR * 0.92f, 0f },
                { poolCX + poolR * 0.22f, poolCY + poolR * 0.90f, -15f },
                { poolCX + poolR * 0.85f, poolCY + poolR * 0.35f, -65f },
                { poolCX + poolR * 0.88f, poolCY - poolR * 0.05f, -90f },
                { poolCX + poolR * 0.72f, poolCY - poolR * 0.42f, -125f }
        };
        for (float[] dl : deckLoungers) {
            canvas.save();
            canvas.translate(dl[0], dl[1]);
            canvas.rotate(dl[2]);
            paint.setColor(Color.parseColor("#5D4037"));
            canvas.drawRoundRect(new RectF(-6f * dp, -4f * dp, -1.5f * dp, 4f * dp), 1.5f * dp, 1.5f * dp, paint);
            canvas.drawRoundRect(new RectF(1.5f * dp, -4f * dp, 6f * dp, 4f * dp), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawCircle(0f, -6f * dp, 3.5f * dp, paint);
            canvas.restore();
        }

        // =========================================================================
        // 4. WEST WING: RECTANGULAR LAP POOL, PRIVATE PLUNGE VILLAS & 4-SQUARE GARDEN
        // =========================================================================
        // Cream Stone Deck for the Upper-Left Rectangular Pool
        RectF westPatio = new RectF(cx - w * 0.67f, cy - h * 0.66f, cx - w * 0.39f, cy - h * 0.40f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(creamShadowCol);
        canvas.drawRoundRect(new RectF(westPatio.left - 3f * dp, westPatio.top - 3f * dp, westPatio.right + 3f * dp, westPatio.bottom + 3f * dp), 4f * dp, 4f * dp, paint);
        paint.setColor(creamDeckCol);
        canvas.drawRoundRect(westPatio, 4f * dp, 4f * dp, paint);

        // Crisp Rectangular Cyan Lap Pool
        RectF lapPool = new RectF(cx - w * 0.61f, cy - h * 0.59f, cx - w * 0.43f, cy - h * 0.47f);
        paint.setColor(poolRimWhite);
        canvas.drawRect(lapPool.left - 2.5f * dp, lapPool.top - 2.5f * dp, lapPool.right + 2.5f * dp, lapPool.bottom + 2.5f * dp, paint);
        paint.setColor(waterOuter);
        canvas.drawRect(lapPool, paint);
        paint.setColor(waterMid);
        canvas.drawRect(lapPool.left + 6f * dp, lapPool.top + 4f * dp, lapPool.right - 6f * dp, lapPool.bottom - 4f * dp, paint);

        // Animated Ripples in the Rectangular Lap Pool
        drawAnimatedWaterRipples(canvas, strokePaint, lapPool.centerX(), lapPool.centerY(), lapPool.width() * 0.38f, lapPool.height() * 0.32f, starPhase + 0.8f);

        // Neat Rows of Dark-Blue Sun Loungers around the Rectangular Lap Pool
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1E3A8A"));
        for (int i = 0; i < 6; i++) {
            float lx = lapPool.left + 6f * dp + i * (lapPool.width() - 12f * dp) / 5f;
            canvas.drawRect(lx - 2.2f * dp, lapPool.top - 10f * dp, lx + 2.2f * dp, lapPool.top - 4.5f * dp, paint);
            canvas.drawRect(lx - 2.2f * dp, lapPool.bottom + 4.5f * dp, lx + 2.2f * dp, lapPool.bottom + 10f * dp, paint);
        }

        // 4-Quadrant Manicured Courtyard Garden (Right of Rectangular Lap Pool)
        RectF courtRect = new RectF(cx - w * 0.37f, cy - h * 0.82f, cx - w * 0.19f, cy - h * 0.54f);
        paint.setColor(creamDeckCol);
        canvas.drawRect(courtRect, paint);
        float cMidX = courtRect.centerX(), cMidY = courtRect.centerY();
        float pad = 5f * dp, gap = 4.5f * dp;
        paint.setColor(lawnGreenCol);
        canvas.drawRect(courtRect.left + pad, courtRect.top + pad, cMidX - gap, cMidY - gap, paint);
        canvas.drawRect(cMidX + gap, courtRect.top + pad, courtRect.right - pad, cMidY - gap, paint);
        canvas.drawRect(courtRect.left + pad, cMidY + gap, cMidX - gap, courtRect.bottom - pad, paint);
        canvas.drawRect(cMidX + gap, cMidY + gap, courtRect.right - pad, courtRect.bottom - pad, paint);
        // Central Courtyard Fountain
        paint.setColor(creamShadowCol);
        canvas.drawCircle(cMidX, cMidY, 7f * dp, paint);
        paint.setColor(waterOuter);
        canvas.drawCircle(cMidX, cMidY, 4.5f * dp, paint);

        // Far-Left Long Terracotta Villa Wing + 6 Cyan Side Plunge Pools
        RectF leftWing = new RectF(cx - w * 0.82f, cy - h * 0.76f, cx - w * 0.69f, cy - h * 0.34f);
        for (int p = 0; p < 6; p++) {
            float py = leftWing.top + 8f * dp + p * ((leftWing.height() - 16f * dp) / 6f);
            paint.setColor(poolRimWhite);
            canvas.drawRect(leftWing.left - 11f * dp, py, leftWing.left, py + 14f * dp, paint);
            paint.setColor(waterOuter);
            canvas.drawRect(leftWing.left - 9.5f * dp, py + 1.5f * dp, leftWing.left - 1f * dp, py + 12.5f * dp, paint);
        }
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, leftWing.left, leftWing.top, leftWing.right, leftWing.bottom, false, terracottaDark, terracottaLight);

        // Top-Left Angled Detached Villa + Twin Rooftop Mini Pools
        canvas.save();
        canvas.translate(cx - w * 0.64f, cy - h * 0.86f);
        canvas.rotate(-12f);
        paint.setColor(poolRimWhite);
        canvas.drawRect(-18f * dp, -26f * dp, 18f * dp, -14f * dp, paint);
        paint.setColor(waterOuter);
        canvas.drawRect(-15f * dp, -24f * dp, -2f * dp, -16f * dp, paint);
        canvas.drawRect(2f * dp, -24f * dp, 15f * dp, -16f * dp, paint);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, -28f * dp, -14f * dp, 28f * dp, 14f * dp, true, terracottaDark, terracottaLight);
        canvas.restore();

        // 3 Luxury Suites Below the Lap Pool with 9 Individual Private Cyan Plunge Pools
        float rowY = cy - h * 0.24f;
        for (int v = 0; v < 3; v++) {
            float vx = cx - w * 0.76f + v * (w * 0.165f);
            float vw = w * 0.15f, vh = h * 0.13f;
            // 3 Private Plunge Pools per Villa Building
            for (int sp = 0; sp < 3; sp++) {
                float pLeft = vx - vw * 0.48f + sp * (vw * 0.33f);
                float pRight = pLeft + vw * 0.28f;
                float pTop = rowY + vh * 0.45f, pBot = rowY + vh * 1.08f;
                paint.setColor(poolRimWhite);
                canvas.drawRect(pLeft - 1.5f * dp, pTop, pRight + 1.5f * dp, pBot + 1.5f * dp, paint);
                paint.setColor(waterOuter);
                canvas.drawRect(pLeft, pTop + 2f * dp, pRight, pBot, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle((pLeft + pRight) * 0.5f, pTop + 4f * dp, 2.8f * dp, paint); // White patio umbrella
            }
            drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, vx - vw * 0.5f, rowY - vh * 0.5f, vx + vw * 0.5f, rowY + vh * 0.5f, true, terracottaDark, terracottaLight);
        }

        // =========================================================================
        // 5. CURVED AMPHITHEATER PERGOLA TERRACE & REFLECTIVE WATER COLONNADE
        // =========================================================================
        // Upper-Center Cream Courtyard Plaza with Café Parasol Dots
        RectF upperPlaza = new RectF(cx - w * 0.19f, cy - h * 0.62f, cx + w * 0.11f, cy - h * 0.36f);
        paint.setColor(creamDeckCol);
        canvas.drawRect(upperPlaza, paint);
        paint.setColor(Color.parseColor("#5D4037"));
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 5; c++) {
                canvas.drawCircle(upperPlaza.left + 16f * dp + c * 22f * dp, upperPlaza.top + 18f * dp + r * 20f * dp, 3.2f * dp, paint);
            }
        }

        // Sweeping Quarter-Circle Tiered Pergola Terrace (Top-Right of Lagoon Pool)
        float pergCX = cx + w * 0.37f, pergCY = cy - h * 0.52f;
        RectF pergOuter = new RectF(pergCX - w * 0.25f, pergCY - h * 0.32f, pergCX + w * 0.25f, pergCY + h * 0.32f);
        paint.setColor(creamDeckCol);
        canvas.drawArc(pergOuter, 90f, 90f, true, paint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        strokePaint.setColor(Color.parseColor("#6D4C41"));
        strokePaint.setStrokeWidth(8f * dp);
        RectF pergBand1 = new RectF(pergCX - w * 0.22f, pergCY - h * 0.28f, pergCX + w * 0.22f, pergCY + h * 0.28f);
        RectF pergBand2 = new RectF(pergCX - w * 0.16f, pergCY - h * 0.20f, pergCX + w * 0.16f, pergCY + h * 0.20f);
        canvas.drawArc(pergBand1, 92f, 86f, false, strokePaint);
        canvas.drawArc(pergBand2, 92f, 86f, false, strokePaint);

        // Reflective Turquoise Water Basins Inside the Colonnade Gallery
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#26A69A"));
        canvas.drawRect(cx + w * 0.25f, cy - h * 0.58f, cx + w * 0.30f, cy - h * 0.40f, paint);
        canvas.drawRect(cx + w * 0.32f, cy - h * 0.58f, cx + w * 0.36f, cy - h * 0.36f, paint);
        canvas.drawRect(cx + w * 0.38f, cy - h * 0.58f, cx + w * 0.41f, cy - h * 0.24f, paint);

        // =========================================================================
        // 6. MAIN LUXURY HOTEL MEGASTRUCTURE (TERRACOTTA + FLAT HVAC ROOFS)
        // =========================================================================
        // A. Top-Center & Top-Right Flat Industrial Roof Complex
        RectF topFlatRoof = new RectF(cx - w * 0.48f, cy - h * 0.98f, cx + w * 0.96f, cy - h * 0.64f);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRect(topFlatRoof.left + 8f * dp, topFlatRoof.top + 10f * dp, topFlatRoof.right + 8f * dp, topFlatRoof.bottom + 10f * dp, paint);
        paint.setColor(flatRoofGrey);
        canvas.drawRect(topFlatRoof, paint);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(2f * dp);
        canvas.drawRect(topFlatRoof, strokePaint);

        // Terracotta Pitched Roof Wings Framing the Top Megastructure
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx - w * 0.50f, cy - h * 0.96f, cx - w * 0.14f, cy - h * 0.84f, true, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx - w * 0.18f, cy - h * 0.84f, cx - w * 0.13f, cy - h * 0.62f, false, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx - w * 0.05f, cy - h * 0.90f, cx + w * 0.18f, cy - h * 0.74f, true, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx - w * 0.06f, cy - h * 0.70f, cx + w * 0.20f, cy - h * 0.62f, true, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx + w * 0.64f, cy - h * 0.76f, cx + w * 0.98f, cy - h * 0.66f, true, terracottaDark, terracottaLight);

        // B. Right-Side Vertical Hotel Wing (Terracotta Spine + White Flat Roofs + HVAC Ducts)
        RectF rightWingFlat = new RectF(cx + w * 0.38f, cy - h * 0.66f, cx + w * 0.66f, cy + h * 0.46f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRect(rightWingFlat.left + 8f * dp, rightWingFlat.top + 10f * dp, rightWingFlat.right + 8f * dp, rightWingFlat.bottom + 10f * dp, paint);
        paint.setColor(flatRoofWhite);
        canvas.drawRect(rightWingFlat, paint);

        // Long Terracotta Pitched Roof Corridors Flanking the Right Wing
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx + w * 0.19f, cy - h * 0.66f, cx + w * 0.52f, cy - h * 0.58f, true, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx + w * 0.45f, cy - h * 0.66f, cx + w * 0.53f, cy - h * 0.14f, false, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx + w * 0.37f, cy - h * 0.16f, cx + w * 0.45f, cy + h * 0.44f, false, terracottaDark, terracottaLight);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, cx + w * 0.56f, cy - h * 0.12f, cx + w * 0.62f, cy + h * 0.44f, false, terracottaDark, terracottaLight);

        // Lower-Right Angled Terracotta Pavilion Wing (Bottom of Right Hotel Wing)
        canvas.save();
        canvas.translate(cx + w * 0.46f, cy + h * 0.56f);
        canvas.rotate(28f);
        drawPitchedTerracottaRoof25D(canvas, paint, strokePaint, -w * 0.11f, -h * 0.08f, w * 0.11f, h * 0.08f, true, terracottaDark, terracottaLight);
        canvas.restore();

        // Detailed Rooftop HVAC Chillers, Piping Ducts & Skylights on Flat Roofs
        drawRooftopHVACDetails(canvas, paint, strokePaint, cx + w * 0.22f, cy - h * 0.92f, w * 0.68f, h * 0.20f);
        drawRooftopHVACDetails(canvas, paint, strokePaint, cx + w * 0.46f, cy - h * 0.52f, w * 0.16f, h * 0.88f);

        // =========================================================================
        // 7. RIGHT SIDE: CIRCULAR PORTE-COCHÈRE ROUNDABOUT & PARKING LOT
        // =========================================================================
        float rbX = cx + w * 0.88f, rbY = cy - h * 0.38f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(creamShadowCol);
        canvas.drawCircle(rbX, rbY, w * 0.16f, paint);
        paint.setColor(creamDeckCol);
        canvas.drawCircle(rbX, rbY, w * 0.12f, paint);
        paint.setColor(poolRimWhite);
        canvas.drawCircle(rbX - w * 0.02f, rbY, 12f * dp, paint);
        paint.setColor(lawnGreenCol);
        canvas.drawCircle(rbX - w * 0.02f, rbY, 8f * dp, paint);

        // Right-Side Asphalt Parking Strip & Parked Cars
        canvas.save();
        canvas.translate(cx + w * 0.86f, cy + h * 0.12f);
        canvas.rotate(-7f);
        paint.setColor(poolRimWhite);
        canvas.drawRoundRect(new RectF(-w * 0.16f, -24f * dp, w * 0.16f, 24f * dp), 4f * dp, 4f * dp, paint);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRect(-w * 0.15f, -18f * dp, w * 0.15f, 18f * dp, paint);

        // White Parking Bay Lines & Parked Cars
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.2f * dp);
        int[] carPalette = { Color.parseColor("#EF4444"), Color.parseColor("#F8FAFC"), Color.parseColor("#38BDF8"), Color.parseColor("#1E293B"), Color.parseColor("#FACC15") };
        for (int slot = -4; slot <= 4; slot++) {
            float sx = slot * 13f * dp;
            canvas.drawLine(sx, -18f * dp, sx, -8f * dp, strokePaint);
            if (slot < 4 && slot != 0) {
                paint.setColor(carPalette[Math.abs(slot) % carPalette.length]);
                canvas.drawRoundRect(new RectF(sx + 2.5f * dp, -16f * dp, sx + 10.5f * dp, -6f * dp), 2f * dp, 2f * dp, paint);
            }
        }
        canvas.restore();

        // =========================================================================
        // 8. LUSH MEDITERRANEAN PALMS & SHRUB CLUSTERS AROUND THE RESORT
        // =========================================================================
        float[][] resortPalms = {
                { poolCX - poolR * 0.25f, poolCY - poolR * 0.88f, 16f },
                { poolCX + poolR * 0.08f, poolCY - poolR * 0.86f, 15f },
                { poolCX - poolR * 0.92f, poolCY + poolR * 0.28f, 15f },
                { poolCX - poolR * 0.65f, poolCY + poolR * 0.86f, 17f },
                { poolCX - poolR * 0.22f, poolCY + poolR * 0.94f, 18f },
                { poolCX + poolR * 0.68f, poolCY + poolR * 0.78f, 17f },
                { poolCX + poolR * 0.92f, poolCY + poolR * 0.45f, 16f },
                { cx + w * 0.68f, cy + h * 0.02f, 20f },
                { cx - w * 0.02f, cy + h * 0.78f, 16f },
                { cx + w * 0.25f, cy + h * 0.84f, 17f }
        };
        for (float[] rp : resortPalms) {
            drawResortPalmTree(canvas, strokePaint, paint, rp[0], rp[1], rp[2] * dp);
        }

        canvas.restore();

        // Crisp Outer Resort Border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1E293B"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 26f * dp, 26f * dp, strokePaint);
    }

    /**
     * Builds the organic multi-lobed freeform lagoon pool contour matching Image 1.
     */
    private void buildLagoonPoolPath(Path out, float cx, float cy, float a, float b, float scale) {
        out.reset();
        int steps = 72;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            // Multi-harmonic contour creating the top cove, wide right basin & lower-left bay
            double r = 0.84
                    + 0.14 * Math.sin(t - 0.3)
                    + 0.12 * Math.cos(2.0 * t + 0.6)
                    - 0.10 * Math.cos(3.0 * t - 0.4)
                    + 0.06 * Math.sin(4.0 * t);
            float px = cx + (float) (a * scale * r * Math.cos(t));
            float py = cy + (float) (b * scale * r * Math.sin(t));
            if (i == 0) out.moveTo(px, py);
            else out.lineTo(px, py);
        }
        out.close();
    }

    private void drawAnimatedWaterRipples(Canvas canvas, Paint strokePaint, float rcx, float rcy, float rx, float ry, float phase) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        // Shimmering wavy caustic lines
        for (int i = -2; i <= 2; i++) {
            float wy = rcy + i * (ry * 0.36f);
            float shift = (float) Math.sin(phase * 2.6f + i * 1.1f) * (5f * dp);
            float span = rx * (0.85f - Math.abs(i) * 0.14f);
            strokePaint.setColor(Color.argb(135, 224, 247, 250));
            strokePaint.setStrokeWidth(1.5f * dp);
            tempPath.reset();
            tempPath.moveTo(rcx - span + shift, wy);
            tempPath.cubicTo(rcx - span * 0.3f, wy - 4.5f * dp, rcx + span * 0.3f, wy + 4.5f * dp, rcx + span + shift, wy);
            canvas.drawPath(tempPath, strokePaint);
        }
        // Expanding concentric surface rings
        for (int ring = 0; ring < 3; ring++) {
            float p = ((phase * 0.32f) + ring * 0.33f) % 1.0f;
            float rad = 4f * dp + p * Math.min(rx, ry) * 0.78f;
            strokePaint.setColor(Color.argb(Math.max(0, Math.round((1f - p) * 165f)), 255, 255, 255));
            strokePaint.setStrokeWidth(1.3f * dp);
            canvas.drawCircle(rcx, rcy, rad, strokePaint);
        }
        strokePaint.setAlpha(255);
    }

    private void drawPoolFootbridge(Canvas canvas, Paint paint, Paint strokePaint, float x1, float y1, float x2, float y2) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        // Underwater bridge shadow
        strokePaint.setColor(Color.parseColor("#38000000"));
        strokePaint.setStrokeWidth(8.5f * dp);
        canvas.drawLine(x1 + 2.5f * dp, y1 + 3f * dp, x2 + 2.5f * dp, y2 + 3f * dp, strokePaint);
        // White bridge deck & bright handrails
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(7.5f * dp);
        canvas.drawLine(x1, y1, x2, y2, strokePaint);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(5.0f * dp);
        canvas.drawLine(x1, y1, x2, y2, strokePaint);
    }

    private void drawPitchedTerracottaRoof25D(Canvas canvas, Paint paint, Paint strokePaint, float left, float top, float right, float bottom, boolean horizRidge, int darkTile, int lightTile) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#40000000"));
        canvas.drawRect(left + 5f * dp, top + 6f * dp, right + 5f * dp, bottom + 6f * dp, paint);

        paint.setColor(darkTile);
        canvas.drawRect(left, top, right, bottom, paint);

        paint.setColor(lightTile);
        if (horizRidge) {
            canvas.drawRect(left, top, right, (top + bottom) * 0.5f, paint);
        } else {
            canvas.drawRect(left, top, (left + right) * 0.5f, bottom, paint);
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#D99B72"));
        strokePaint.setStrokeWidth(1.8f * dp);
        if (horizRidge) {
            float my = (top + bottom) * 0.5f;
            canvas.drawLine(left + 3f * dp, my, right - 3f * dp, my, strokePaint);
        } else {
            float mx = (left + right) * 0.5f;
            canvas.drawLine(mx, top + 3f * dp, mx, bottom - 3f * dp, strokePaint);
        }
    }

    private void drawRooftopHVACDetails(Canvas canvas, Paint paint, Paint strokePaint, float startX, float startY, float areaW, float areaH) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(2.2f * dp);
        // Metallic HVAC piping network
        canvas.drawLine(startX + 10f * dp, startY + areaH * 0.35f, startX + areaW - 10f * dp, startY + areaH * 0.35f, strokePaint);
        canvas.drawLine(startX + areaW * 0.45f, startY + 8f * dp, startX + areaW * 0.45f, startY + areaH - 8f * dp, strokePaint);

        int cols = Math.max(2, (int) (areaW / (34f * dp)));
        int rows = Math.max(2, (int) (areaH / (34f * dp)));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float ux = startX + (c + 0.5f) * (areaW / cols);
                float uy = startY + (r + 0.5f) * (areaH / rows);
                if ((r + c) % 2 == 0) {
                    drawRooftopAC(canvas, paint, ux, uy);
                } else {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.parseColor("#64748B"));
                    canvas.drawRect(ux - 5f * dp, uy - 3.5f * dp, ux + 5f * dp, uy + 3.5f * dp, paint);
                }
            }
        }
    }

    private void drawResortPalmTree(Canvas canvas, Paint strokePaint, Paint fillPaint, float px, float py, float rad) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#33000000"));
        canvas.drawCircle(px + 3f * dp, py + 4f * dp, rad * 0.85f, fillPaint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 10; i++) {
            double ang = i * (Math.PI * 2.0 / 10.0) + 0.15;
            float ex = px + (float) Math.cos(ang) * rad;
            float ey = py + (float) Math.sin(ang) * rad;
            strokePaint.setColor((i % 2 == 0) ? Color.parseColor("#2E7D32") : Color.parseColor("#66BB6A"));
            strokePaint.setStrokeWidth(2.4f * dp);
            canvas.drawLine(px, py, ex, ey, strokePaint);
        }
        fillPaint.setColor(Color.parseColor("#5D4037"));
        canvas.drawCircle(px, py, 2.8f * dp, fillPaint);
    }

    /**
     * Renders the Large 2.5D Botanical Lotus Lake Sanctuary.
     */
    private void drawDetailedLake25D(Canvas canvas, Paint paint, Paint strokePaint, Lake l, float cx, float cy, float starPhase) {
        float w = l.rx, h = l.ry;
        int mortarCol    = isSunnyDay ? Color.parseColor("#B5B0A6") : Color.parseColor("#334155");
        int slabLightCol = isSunnyDay ? Color.parseColor("#D5D0C5") : Color.parseColor("#475569");
        int slabMidCol   = isSunnyDay ? Color.parseColor("#C7C2B7") : Color.parseColor("#3E4C5E");
        int waterShallow = isSunnyDay ? Color.parseColor("#76C4E5") : Color.parseColor("#0284C7");
        int waterMid     = isSunnyDay ? Color.parseColor("#58ACE0") : Color.parseColor("#0369A1");
        int waterDeep    = isSunnyDay ? Color.parseColor("#3B8EC2") : Color.parseColor("#075985");
        int waterCore    = isSunnyDay ? Color.parseColor("#2A75A8") : Color.parseColor("#0C4A6E");

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(cx - w + 10f * dp, cy - h + 12f * dp, cx + w + 10f * dp, cy + h + 12f * dp), 28f * dp, 28f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(mortarCol);
        canvas.drawRoundRect(tempRect, 28f * dp, 28f * dp, paint);

        canvas.save();
        tempPath.reset(); tempPath.addRoundRect(tempRect, 28f * dp, 28f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        float tileStep = 44f * dp;
        for (float gx = cx - w + 22f * dp; gx < cx + w; gx += tileStep) {
            for (float gy = cy - h + 22f * dp; gy < cy + h; gy += tileStep) {
                int hash = Math.abs((int) ((gx - cx) * 13 + (gy - cy) * 31));
                float jx = ((hash % 11) - 5) * 1.4f * dp, jy = (((hash / 11) % 11) - 5) * 1.4f * dp;
                float tw = (17f + (hash % 5)) * dp, th = (15f + ((hash / 7) % 6)) * dp;
                paint.setColor((hash % 2 == 0) ? slabLightCol : slabMidCol);
                canvas.drawRoundRect(new RectF(gx + jx - tw, gy + jy - th, gx + jx + tw, gy + jy + th), 5f * dp, 5f * dp, paint);
            }
        }

        paint.setColor(isSunnyDay ? Color.parseColor("#8BC34A") : Color.parseColor("#14532D"));
        canvas.drawCircle(cx + w * 0.82f, cy - h * 0.82f, w * 0.42f, paint);
        canvas.drawCircle(cx + w * 0.85f, cy + h * 0.85f, w * 0.36f, paint);
        canvas.drawCircle(cx - w * 0.88f, cy + h * 0.72f, w * 0.34f, paint);

        float rgX = cx - w * 0.62f, rgY = cy - h * 0.64f, rgR = w * 0.36f;
        paint.setColor(Color.parseColor("#7D7461"));
        canvas.drawCircle(rgX, rgY, rgR, paint);

        for (int i = 0; i < 16; i++) {
            double ang = i * (Math.PI * 2.0 / 16.0);
            float bx = rgX + (float) Math.cos(ang) * (rgR * 0.76f), by = rgY + (float) Math.sin(ang) * (rgR * 0.76f);
            paint.setColor(Color.parseColor("#689F38")); canvas.drawCircle(bx + 2f * dp, by + 3f * dp, 18f * dp, paint);
            paint.setColor(Color.parseColor("#9CCC65")); canvas.drawCircle(bx, by, 16f * dp, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(bx - 5f * dp, by - 4f * dp, 2.5f * dp, paint);
            canvas.drawCircle(bx + 5f * dp, by + 3f * dp, 2.2f * dp, paint);
            canvas.drawCircle(bx + 3f * dp, by - 5f * dp, 2.0f * dp, paint);
        }

        paint.setColor(Color.parseColor("#558B2F")); canvas.drawCircle(rgX, rgY, rgR * 0.54f, paint);
        paint.setColor(Color.parseColor("#7CB342")); canvas.drawCircle(rgX - 4f * dp, rgY - 4f * dp, rgR * 0.48f, paint);
        for (int rIdx = 0; rIdx < 14; rIdx++) {
            double ra = rIdx * 2.39996;
            float dist = (float) Math.sqrt(rIdx / 14.0) * (rgR * 0.44f);
            float rxPos = rgX + (float) Math.cos(ra) * dist, ryPos = rgY + (float) Math.sin(ra) * dist;
            paint.setColor(Color.parseColor("#EC407A")); canvas.drawCircle(rxPos, ryPos, 7.5f * dp, paint);
            paint.setColor(Color.parseColor("#F48FB1")); canvas.drawCircle(rxPos - 1f * dp, ryPos - 1f * dp, 5.2f * dp, paint);
            paint.setColor(Color.parseColor("#FCE4EC")); canvas.drawCircle(rxPos - 1.5f * dp, ryPos - 1.5f * dp, 2.6f * dp, paint);
        }

        float pondCX = cx + w * 0.04f, pondCY = cy + h * 0.06f;

        l.buildKidneyPath(tempPath, pondCX, pondCY, 1.00f);
        paint.setColor(waterShallow); canvas.drawPath(tempPath, paint);

        for (int i = 0; i < l.submergedStones.size(); i++) {
            Lake.StoneNode sn = l.submergedStones.get(i);
            paint.setColor(sn.col);
            canvas.drawOval(new RectF(pondCX + sn.ox - sn.rx, pondCY + sn.oy - sn.ry, pondCX + sn.ox + sn.rx, pondCY + sn.oy + sn.ry), paint);
        }

        l.buildKidneyPath(tempPath, pondCX, pondCY, 0.84f);
        paint.setColor(waterMid); canvas.drawPath(tempPath, paint);

        l.buildKidneyPath(tempPath, pondCX, pondCY, 0.66f);
        paint.setColor(waterDeep); canvas.drawPath(tempPath, paint);

        l.buildKidneyPath(tempPath, pondCX, pondCY, 0.44f);
        paint.setColor(waterCore); canvas.drawPath(tempPath, paint);

        float[][] koiOffsets = {
                { -w * 0.20f, h * 0.18f, 0.4f }, { -w * 0.02f, h * 0.24f, 2.1f },
                {  w * 0.12f, -h * 0.04f, 3.8f }, { w * 0.24f, -h * 0.16f, 5.2f }
        };
        for (int k = 0; k < koiOffsets.length; k++) {
            float ka = starPhase * 0.9f + koiOffsets[k][2];
            float kx = pondCX + koiOffsets[k][0] + (float) Math.cos(ka) * 18f * dp;
            float ky = pondCY + koiOffsets[k][1] + (float) Math.sin(ka) * 12f * dp;
            canvas.save(); canvas.translate(kx, ky); canvas.rotate((float) Math.toDegrees(ka) + 90f);
            paint.setColor((k % 2 == 0) ? Color.parseColor("#EF5350") : Color.parseColor("#E0F7FA"));
            canvas.drawOval(new RectF(-6f * dp, -2.2f * dp, 6f * dp, 2.2f * dp), paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(-5.5f * dp, 0f, 1.8f * dp, paint);
            canvas.restore();
        }

        float[][] rippleCenters = { { pondCX - w * 0.22f, pondCY + h * 0.16f }, { pondCX + w * 0.16f, pondCY - h * 0.02f } };
        strokePaint.setStyle(Paint.Style.STROKE);
        for (int rc = 0; rc < rippleCenters.length; rc++) {
            float rcx = rippleCenters[rc][0], rcy = rippleCenters[rc][1];
            float maxRippleR = w * 0.26f;
            for (int ring = 0; ring < 6; ring++) {
                float p = ((starPhase * 0.22f) + ring / 6.0f + rc * 0.35f) % 1.0f;
                float rad = 6f * dp + p * maxRippleR;
                int alpha = Math.max(0, Math.round((1.0f - p) * 195f));
                strokePaint.setColor(Color.argb(alpha, 179, 229, 252));
                strokePaint.setStrokeWidth((2.2f - p * 1.1f) * dp);
                canvas.drawCircle(rcx, rcy, rad, strokePaint);
            }
        }
        strokePaint.setAlpha(255);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < l.lilyPads.size(); i++) {
            Lake.LilyPadNode lp = l.lilyPads.get(i);
            float lpx = pondCX + lp.ox + (float) Math.sin(starPhase * 1.5f + i) * 1.8f * dp;
            float lpy = pondCY + lp.oy + (float) Math.cos(starPhase * 1.3f + i) * 1.8f * dp;

            paint.setColor(Color.parseColor("#38000000"));
            tempRect.set(lpx - lp.r + 3f * dp, lpy - lp.r + 4f * dp, lpx + lp.r + 3f * dp, lpy + lp.r + 4f * dp);
            canvas.drawArc(tempRect, lp.rotDeg, 325f, true, paint);

            paint.setColor(Color.parseColor("#689F38"));
            tempRect.set(lpx - lp.r, lpy - lp.r, lpx + lp.r, lpy + lp.r);
            canvas.drawArc(tempRect, lp.rotDeg, 325f, true, paint);

            paint.setColor(Color.parseColor("#9CCC65"));
            tempRect.inset(2.5f * dp, 2.5f * dp);
            canvas.drawArc(tempRect, lp.rotDeg, 325f, true, paint);

            if (lp.hasLotus) drawWhiteLotus25D(canvas, paint, lpx, lpy, lp.r * 0.82f, starPhase + i);
        }

        for (int i = 0; i < l.shoreBoulders.size(); i++) {
            Lake.StoneNode b = l.shoreBoulders.get(i);
            float bx = pondCX + b.ox, by = pondCY + b.oy;
            canvas.save(); canvas.translate(bx, by); canvas.rotate(b.angleDeg);
            paint.setColor(Color.parseColor("#44000000"));
            canvas.drawRoundRect(new RectF(-b.rx + 2.5f * dp, -b.ry + 3f * dp, b.rx + 2.5f * dp, b.ry + 3f * dp), 6f * dp, 6f * dp, paint);
            paint.setColor(b.col);
            canvas.drawRoundRect(new RectF(-b.rx, -b.ry, b.rx, b.ry), 6f * dp, 6f * dp, paint);
            paint.setColor(Color.parseColor("#E5E2DC"));
            canvas.drawRoundRect(new RectF(-b.rx * 0.65f, -b.ry * 0.65f, b.rx * 0.45f, b.ry * 0.35f), 4f * dp, 4f * dp, paint);
            canvas.restore();
        }

        for (int i = 0; i < l.shoreFlowers.size(); i++) {
            Lake.FlowerNode fn = l.shoreFlowers.get(i);
            float fx = pondCX + fn.ox, fy = pondCY + fn.oy;
            paint.setColor(Color.parseColor("#558B2F")); canvas.drawCircle(fx, fy, fn.r * 1.35f, paint);
            paint.setColor(Color.parseColor("#8BC34A")); canvas.drawCircle(fx - 1.5f * dp, fy - 1.5f * dp, fn.r, paint);
            paint.setColor(fn.petalCol); canvas.drawCircle(fx, fy, fn.r * 0.68f, paint);
            paint.setColor(Color.parseColor("#FFF59D")); canvas.drawCircle(fx, fy, fn.r * 0.28f, paint);
        }

        float[][] cattails = { { pondCX - w * 0.56f, pondCY + h * 0.24f }, { pondCX - w * 0.50f, pondCY + h * 0.42f } };
        for (float[] ct : cattails) {
            drawTropicalPalmRosette(canvas, strokePaint, paint, ct[0], ct[1], 32f * dp);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#6D4C41"));
            canvas.drawOval(new RectF(ct[0] - 14f * dp, ct[1] - 6f * dp, ct[0] - 4f * dp, ct[1] + 6f * dp), paint);
            canvas.drawOval(new RectF(ct[0] - 8f * dp, ct[1] + 4f * dp, ct[0] + 2f * dp, ct[1] + 14f * dp), paint);
            paint.setColor(Color.parseColor("#FF5252"));
            canvas.drawCircle(ct[0] + 10f * dp, ct[1] - 4f * dp, 3.5f * dp, paint);
        }

        drawFanPalmFronds25D(canvas, strokePaint, paint, cx + w * 0.58f, cy - h * 0.72f, 82f * dp);
        drawFanPalmFronds25D(canvas, strokePaint, paint, cx + w * 0.78f, cy - h * 0.42f, 76f * dp);
        drawFanPalmFronds25D(canvas, strokePaint, paint, cx + w * 0.68f, cy + h * 0.68f, 74f * dp);
        drawFanPalmFronds25D(canvas, strokePaint, paint, cx - w * 0.78f, cy - h * 0.04f, 70f * dp);
        drawTropicalPalmRosette(canvas, strokePaint, paint, cx + w * 0.34f, cy - h * 0.48f, 44f * dp);
        drawTropicalPalmRosette(canvas, strokePaint, paint, cx + w * 0.52f, cy - h * 0.24f, 40f * dp);

        canvas.restore();

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#689F38")); strokePaint.setStrokeWidth(3.2f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 28f * dp, 28f * dp, strokePaint);
    }

    private void drawWhiteLotus25D(Canvas canvas, Paint paint, float x, float y, float radius, float phase) {
        canvas.save(); canvas.translate(x, y); canvas.rotate((float) Math.sin(phase) * 6f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(2f * dp, 3f * dp, radius * 0.92f, paint);

        paint.setColor(Color.parseColor("#E2E8F0"));
        for (int i = 0; i < 8; i++) { canvas.drawOval(new RectF(-radius * 0.28f, -radius, radius * 0.28f, radius), paint); canvas.rotate(22.5f); }

        paint.setColor(Color.WHITE);
        float innerR = radius * 0.82f;
        for (int i = 0; i < 8; i++) { canvas.drawOval(new RectF(-innerR * 0.26f, -innerR, innerR * 0.26f, innerR), paint); canvas.rotate(22.5f); }

        paint.setColor(Color.parseColor("#F59E0B")); canvas.drawCircle(0f, 0f, radius * 0.28f, paint);
        paint.setColor(Color.parseColor("#FEF08A")); canvas.drawCircle(-1f * dp, -1f * dp, radius * 0.16f, paint);
        canvas.restore();
    }

    private void drawFanPalmFronds25D(Canvas canvas, Paint strokePaint, Paint fillPaint, float px, float py, float rad) {
        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 24; i++) {
            double ang = i * (Math.PI * 2.0 / 24.0);
            float ex = px + (float) Math.cos(ang) * rad, ey = py + (float) Math.sin(ang) * rad;
            strokePaint.setColor(Color.parseColor("#33000000")); strokePaint.setStrokeWidth(3.2f * dp);
            canvas.drawLine(px + 4f * dp, py + 5f * dp, ex + 4f * dp, ey + 5f * dp, strokePaint);
            strokePaint.setColor((i % 2 == 0) ? Color.parseColor("#689F38") : Color.parseColor("#8BC34A"));
            strokePaint.setStrokeWidth(2.8f * dp);
            canvas.drawLine(px, py, ex, ey, strokePaint);
        }
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.parseColor("#558B2F")); canvas.drawCircle(px, py, 8f * dp, fillPaint);
        fillPaint.setColor(Color.parseColor("#9CCC65")); canvas.drawCircle(px, py, 4.5f * dp, fillPaint);
    }

    private void drawDetailedConstructionSite25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int sandEarthCol = isSunnyDay ? Color.parseColor("#DFC19B") : (isMoonlightMode ? Color.parseColor("#6E563B") : Color.parseColor("#4A3825"));
        int warmSandCol  = isSunnyDay ? Color.parseColor("#CDA477") : (isMoonlightMode ? Color.parseColor("#574129") : Color.parseColor("#38291A"));
        int mudTrackCol  = isSunnyDay ? Color.parseColor("#8A5A2E") : Color.parseColor("#3E2723");
        int deepMudCol   = isSunnyDay ? Color.parseColor("#5C3A1B") : Color.parseColor("#271815");

        paint.setStyle(Paint.Style.FILL); paint.setColor(sandEarthCol);
        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        canvas.drawRoundRect(tempRect, 20f * dp, 20f * dp, paint);

        canvas.save();
        tempPath.reset(); tempPath.addRoundRect(tempRect, 20f * dp, 20f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        paint.setColor(warmSandCol);
        canvas.drawOval(new RectF(cx - w * 0.90f, cy + h * 0.36f, cx + w * 0.08f, cy + h * 0.96f), paint);
        canvas.drawOval(new RectF(cx - w * 0.42f, cy - h * 0.85f, cx + w * 0.25f, cy - h * 0.12f), paint);

        tempPath.reset();
        tempPath.moveTo(cx + w * 0.12f, cy - h);
        tempPath.cubicTo(cx + w * 0.22f, cy - h * 0.40f, cx + w * 0.44f, cy - h * 0.10f, cx + w * 0.40f, cy + h * 0.42f);
        tempPath.moveTo(cx - w * 0.18f, cy - h);
        tempPath.cubicTo(cx - w * 0.14f, cy - h * 0.35f, cx + w * 0.06f, cy + h * 0.25f, cx + w * 0.02f, cy + h);
        tempPath.moveTo(cx - w * 0.45f, cy + h * 0.72f);
        tempPath.cubicTo(cx - w * 0.10f, cy + h * 0.58f, cx + w * 0.12f, cy + h * 0.65f, cx + w * 0.22f, cy + h);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(mudTrackCol); strokePaint.setStrokeWidth(42f * dp); canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(deepMudCol); strokePaint.setStrokeWidth(6f * dp); strokePaint.setPathEffect(rutDash);
        canvas.drawPath(tempPath, strokePaint); strokePaint.setPathEffect(null);

        paint.setStyle(Paint.Style.FILL);
        float[][] rebarBundles = { { cx - w * 0.26f, cy - h * 0.62f, 75f * dp }, { cx + w * 0.08f, cy + h * 0.02f, 68f * dp } };
        for (float[] rb : rebarBundles) {
            paint.setColor(Color.parseColor("#334155"));
            canvas.drawRect(rb[0] - 18f * dp, rb[1] - rb[2] * 0.5f, rb[0] + 18f * dp, rb[1] + rb[2] * 0.5f, paint);
            strokePaint.setColor(Color.parseColor("#94A3B8")); strokePaint.setStrokeWidth(2.2f * dp);
            for (int bar = -14; bar <= 14; bar += 5) {
                canvas.drawLine(rb[0] + bar * dp, rb[1] - rb[2] * 0.48f, rb[0] + bar * dp, rb[1] + rb[2] * 0.48f, strokePaint);
            }
        }

        for (int tb = 0; tb < 5; tb++) {
            float tx = cx - w * 0.04f + (tb % 2) * 26f * dp, ty = cy - h * 0.28f + tb * 12f * dp;
            paint.setColor(Color.parseColor("#44000000")); canvas.drawRect(tx + 3f * dp, ty + 3f * dp, tx + 22f * dp, ty + 11f * dp, paint);
            paint.setColor(Color.parseColor("#FDE047")); canvas.drawRect(tx, ty, tx + 20f * dp, ty + 8f * dp, paint);
        }

        float[][] precastBoxes = {
                { cx - w * 0.36f, cy - h * 0.72f }, { cx - w * 0.32f, cy - h * 0.60f }, { cx - w * 0.06f, cy - h * 0.74f },
                { cx + w * 0.28f, cy + h * 0.02f }, { cx + w * 0.35f, cy + h * 0.08f }, { cx + w * 0.25f, cy + h * 0.14f },
                { cx + w * 0.38f, cy - h * 0.05f }, { cx - w * 0.56f, cy + h * 0.50f }
        };
        for (float[] pb : precastBoxes) drawHollowPrecastBox25D(canvas, paint, pb[0], pb[1], 19f * dp, 16f * dp);

        float[][] brickPallets = { { cx - w * 0.18f, cy - h * 0.80f }, { cx + w * 0.16f, cy - h * 0.22f }, { cx - w * 0.12f, cy + h * 0.22f } };
        for (float[] bp : brickPallets) {
            paint.setColor(Color.parseColor("#44000000")); canvas.drawRect(bp[0] - 9f * dp, bp[1] - 7f * dp, bp[0] + 15f * dp, bp[1] + 11f * dp, paint);
            paint.setColor(Color.parseColor("#C2410C")); canvas.drawRect(bp[0] - 12f * dp, bp[1] - 10f * dp, bp[0] + 12f * dp, bp[1] + 8f * dp, paint);
            paint.setColor(Color.parseColor("#EA580C")); canvas.drawRect(bp[0] - 10f * dp, bp[1] - 8f * dp, bp[0] + 10f * dp, bp[1] - 1f * dp, paint);
        }
        paint.setColor(Color.parseColor("#64748B")); canvas.drawCircle(cx + w * 0.32f, cy - h * 0.78f, 22f * dp, paint);
        paint.setColor(Color.parseColor("#94A3B8")); canvas.drawCircle(cx + w * 0.30f, cy - h * 0.80f, 14f * dp, paint);

        drawSiteContainer25D(canvas, paint, cx + w * 0.32f, cy - h * 0.64f, 24f * dp, 14f * dp, Color.parseColor("#0284C7"));
        drawSiteContainer25D(canvas, paint, cx + w * 0.02f, cy - h * 0.14f, 18f * dp, 18f * dp, Color.parseColor("#10B981"));

        drawUnbuiltConcreteWing25D(canvas, paint, strokePaint, cx - w * 0.92f, cy - h * 0.88f, cx - w * 0.50f, cy + h * 0.36f, true, true);
        drawUnbuiltConcreteWing25D(canvas, paint, strokePaint, cx - w * 0.52f, cy - h * 0.08f, cx - w * 0.02f, cy + h * 0.36f, false, false);
        drawUnbuiltConcreteWing25D(canvas, paint, strokePaint, cx + w * 0.54f, cy - h * 0.84f, cx + w * 0.94f, cy + h * 0.64f, true, false);
        drawUnbuiltConcreteWing25D(canvas, paint, strokePaint, cx + w * 0.16f, cy + h * 0.44f, cx + w * 0.88f, cy + h * 0.90f, true, true);

        drawActiveJCBExcavator25D(canvas, paint, strokePaint, cx - w * 0.36f, cy + h * 0.64f, -25f, starPhase);
        drawActiveJCBExcavator25D(canvas, paint, strokePaint, cx + w * 0.08f, cy - h * 0.52f, 145f, starPhase + 1.8f);

        float lorry1Prog = (starPhase * 0.14f) % 1.0f;
        float l1X = (cx - w * 0.16f) + lorry1Prog * (w * 0.20f), l1Y = (cy - h * 0.85f) + lorry1Prog * (h * 1.65f);
        drawMaterialLorry25D(canvas, paint, strokePaint, l1X, l1Y, 82f, true, starPhase);

        float lorry2Prog = ((starPhase * 0.14f) + 0.5f) % 1.0f;
        float l2X = (cx + w * 0.38f) - lorry2Prog * (w * 0.22f), l2Y = (cy + h * 0.32f) - lorry2Prog * (h * 1.10f);
        drawMaterialLorry25D(canvas, paint, strokePaint, l2X, l2Y, -102f, false, starPhase + 2.0f);
        drawMaterialLorry25D(canvas, paint, strokePaint, cx - w * 0.44f, cy - h * 0.92f, 0f, false, starPhase);

        float[][] workers = { { cx - w * 0.22f, cy - h * 0.35f }, { cx + w * 0.20f, cy + h * 0.22f }, { cx - w * 0.65f, cy + h * 0.12f }, { cx + w * 0.42f, cy + h * 0.62f } };
        for (int wk = 0; wk < workers.length; wk++) {
            float wx = workers[wk][0] + (float) Math.sin(starPhase * 2.5f + wk) * 8f * dp;
            float wy = workers[wk][1] + (float) Math.cos(starPhase * 2.0f + wk) * 6f * dp;
            paint.setColor(Color.parseColor("#1D4ED8")); canvas.drawCircle(wx, wy + 2f * dp, 4.5f * dp, paint);
            paint.setColor(Color.parseColor("#FACC15")); canvas.drawCircle(wx, wy, 3.5f * dp, paint);
        }

        drawConstructionDustAndSmoke(canvas, paint, cx - w * 0.36f, cy + h * 0.64f, starPhase, true);
        drawConstructionDustAndSmoke(canvas, paint, cx + w * 0.08f, cy - h * 0.52f, starPhase + 1.5f, true);
        drawConstructionDustAndSmoke(canvas, paint, l1X, l1Y, starPhase * 1.4f, false);
        drawConstructionDustAndSmoke(canvas, paint, l2X, l2Y, starPhase * 1.4f + 2.2f, false);

        drawLatticeTowerCrane25D(canvas, paint, strokePaint, cx - w * 0.82f, cy - h * 0.84f, w * 0.56f, 48f + (float) Math.sin(starPhase * 0.45f) * 14f, Color.parseColor("#F1F5F9"), Color.parseColor("#94A3B8"), 0, starPhase);
        drawLatticeTowerCrane25D(canvas, paint, strokePaint, cx + w * 0.52f, cy - h * 0.72f, w * 0.46f, 135f + (float) Math.cos(starPhase * 0.50f) * 18f, Color.parseColor("#FACC15"), Color.parseColor("#CA8A04"), 1, starPhase + 1.2f);
        drawLatticeTowerCrane25D(canvas, paint, strokePaint, cx + w * 0.86f, cy + h * 0.68f, w * 0.64f, -142f + (float) Math.sin(starPhase * 0.40f + 1f) * 16f, Color.parseColor("#F59E0B"), Color.parseColor("#B45309"), 2, starPhase + 2.5f);

        canvas.restore();
        strokePaint.setColor(Color.parseColor("#78350F")); strokePaint.setStrokeWidth(3.2f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 20f * dp, 20f * dp, strokePaint);
    }

    private void drawUnbuiltConcreteWing25D(Canvas canvas, Paint paint, Paint strokePaint, float left, float top, float right, float bottom, boolean darkDeckLeft, boolean yellowBeamsCorner) {
        float w = right - left, h = bottom - top;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000")); canvas.drawRect(left + 16f * dp, top + 18f * dp, right + 16f * dp, bottom + 18f * dp, paint);
        paint.setColor(Color.parseColor("#94A3B8")); canvas.drawRect(left - 6f * dp, top + 6f * dp, right + 6f * dp, bottom + 6f * dp, paint);
        paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRect(left, top, right, bottom, paint);

        paint.setColor(Color.parseColor("#2B3444"));
        if (darkDeckLeft) canvas.drawRect(left + 4f * dp, top + 4f * dp, left + w * 0.58f, bottom - 4f * dp, paint);
        else canvas.drawRect(left + w * 0.45f, top + 4f * dp, right - 4f * dp, bottom - 4f * dp, paint);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(Color.parseColor("#475569")); strokePaint.setStrokeWidth(1.4f * dp);
        float stepX = 18f * dp, stepY = 18f * dp;
        for (float gx = left + stepX; gx < right; gx += stepX) canvas.drawLine(gx, top + 4f * dp, gx, bottom - 4f * dp, strokePaint);
        for (float gy = top + stepY; gy < bottom; gy += stepY) canvas.drawLine(left + 4f * dp, gy, right - 4f * dp, gy, strokePaint);

        if (yellowBeamsCorner) {
            strokePaint.setColor(Color.parseColor("#FACC15")); strokePaint.setStrokeWidth(2.8f * dp);
            float yStart = bottom - h * 0.38f;
            for (float by = yStart; by < bottom - 4f * dp; by += 7f * dp) canvas.drawLine(right - w * 0.38f, by, right - 2f * dp, by, strokePaint);
            for (float bx = right - w * 0.36f; bx < right - 4f * dp; bx += 9f * dp) canvas.drawLine(bx, yStart - 4f * dp, bx, bottom - 2f * dp, strokePaint);
        }

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#E2E8F0"));
        for (float cxPos = left + 8f * dp; cxPos < right - 8f * dp; cxPos += 24f * dp) {
            canvas.drawRect(cxPos, top - 3f * dp, cxPos + 6f * dp, top + 5f * dp, paint);
            canvas.drawRect(cxPos, bottom - 5f * dp, cxPos + 6f * dp, bottom + 3f * dp, paint);
        }

        drawHollowPrecastBox25D(canvas, paint, left + w * 0.28f, top + h * 0.26f, 26f * dp, 22f * dp);
        canvas.save(); canvas.translate(left + w * 0.34f, bottom - h * 0.28f); canvas.rotate(-32f);
        drawHollowPrecastBox25D(canvas, paint, 0f, 0f, 28f * dp, 20f * dp); canvas.restore();
        if (w > 120f * dp) drawHollowPrecastBox25D(canvas, paint, right - w * 0.26f, top + h * 0.45f, 24f * dp, 24f * dp);
    }

    private void drawHollowPrecastBox25D(Canvas canvas, Paint paint, float cx, float cy, float w, float h) {
        float hw = w * 0.5f, hh = h * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000")); canvas.drawRect(cx - hw + 5f * dp, cy - hh + 6f * dp, cx + hw + 5f * dp, cy + hh + 6f * dp, paint);
        paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRect(cx - hw, cy - hh, cx + hw, cy + hh, paint);
        paint.setColor(Color.parseColor("#1E293B")); canvas.drawRect(cx - hw + 3.5f * dp, cy - hh + 3.5f * dp, cx + hw - 3.5f * dp, cy + hh - 3.5f * dp, paint);
    }

    private void drawSiteContainer25D(Canvas canvas, Paint paint, float cx, float cy, float w, float h, int col) {
        float hw = w * 0.5f, hh = h * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000")); canvas.drawRect(cx - hw + 4f * dp, cy - hh + 5f * dp, cx + hw + 4f * dp, cy + hh + 5f * dp, paint);
        paint.setColor(col); canvas.drawRoundRect(new RectF(cx - hw, cy - hh, cx + hw, cy + hh), 2f * dp, 2f * dp, paint);
    }

    private void drawActiveJCBExcavator25D(Canvas canvas, Paint paint, Paint strokePaint, float x, float y, float baseAngleDeg, float phase) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(baseAngleDeg);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#3E2723")); canvas.drawOval(new RectF(14f * dp, -20f * dp, 52f * dp, 20f * dp), paint);
        paint.setColor(Color.parseColor("#5D4037")); canvas.drawOval(new RectF(18f * dp, -14f * dp, 46f * dp, 14f * dp), paint);

        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRoundRect(new RectF(-14f * dp, -13f * dp, 14f * dp, -8f * dp), 2f * dp, 2f * dp, paint);
        canvas.drawRoundRect(new RectF(-14f * dp, 8f * dp, 14f * dp, 13f * dp), 2f * dp, 2f * dp, paint);

        canvas.rotate((float) Math.sin(phase * 1.8f) * 24f);
        paint.setColor(Color.parseColor("#44000000")); canvas.drawRoundRect(new RectF(-12f * dp + 4f * dp, -10f * dp + 5f * dp, 12f * dp + 4f * dp, 10f * dp + 5f * dp), 3f * dp, 3f * dp, paint);
        paint.setColor(Color.parseColor("#F59E0B")); canvas.drawRoundRect(new RectF(-12f * dp, -10f * dp, 12f * dp, 10f * dp), 3f * dp, 3f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B")); canvas.drawRoundRect(new RectF(-2f * dp, -8f * dp, 9f * dp, 3f * dp), 2f * dp, 2f * dp, paint);

        float reach = (0.5f + 0.5f * (float) Math.sin(phase * 3.6f)) * 14f * dp;
        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.parseColor("#D97706")); strokePaint.setStrokeWidth(5f * dp); canvas.drawLine(6f * dp, 4f * dp, 24f * dp + reach * 0.5f, 4f * dp, strokePaint);
        strokePaint.setColor(Color.parseColor("#FACC15")); strokePaint.setStrokeWidth(3.8f * dp); canvas.drawLine(6f * dp, 4f * dp, 28f * dp + reach, 4f * dp, strokePaint);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#334155"));
        canvas.drawRoundRect(new RectF(26f * dp + reach, -2f * dp, 35f * dp + reach, 10f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.parseColor("#4E342E")); canvas.drawCircle(30f * dp + reach, 4f * dp, 3.5f * dp, paint);
        canvas.restore();
    }

    private void drawMaterialLorry25D(Canvas canvas, Paint paint, Paint strokePaint, float x, float y, float angleDeg, boolean loadBricks, float phase) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angleDeg);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(-26f * dp + 4f * dp, -10f * dp + 5f * dp, 22f * dp + 4f * dp, 10f * dp + 5f * dp), 3f * dp, 3f * dp, paint);

        paint.setColor(Color.parseColor("#475569")); canvas.drawRoundRect(new RectF(-26f * dp, -10f * dp, 8f * dp, 10f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRect(-24f * dp, -8.5f * dp, 6f * dp, 8.5f * dp, paint);

        if (loadBricks) {
            paint.setColor(Color.parseColor("#C2410C"));
            for (int r = 0; r < 3; r++) {
                float bx = -22f * dp + r * 9.5f * dp;
                canvas.drawRect(bx, -7f * dp, bx + 7.5f * dp, -1f * dp, paint);
                canvas.drawRect(bx, 1f * dp, bx + 7.5f * dp, 7f * dp, paint);
            }
        } else {
            paint.setColor(Color.parseColor("#64748B")); canvas.drawOval(new RectF(-23f * dp, -7.5f * dp, 5f * dp, 7.5f * dp), paint);
            paint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawCircle(-14f * dp, -2f * dp, 5f * dp, paint); canvas.drawCircle(-5f * dp, 2f * dp, 4.5f * dp, paint); canvas.drawCircle(-9f * dp, -1f * dp, 4f * dp, paint);
        }

        paint.setColor(loadBricks ? Color.parseColor("#2563EB") : Color.parseColor("#EA580C"));
        canvas.drawRoundRect(new RectF(9f * dp, -9f * dp, 22f * dp, 9f * dp), 3f * dp, 3f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B")); canvas.drawRect(16f * dp, -7.5f * dp, 20f * dp, 7.5f * dp, paint);
        canvas.restore();
    }

    private void drawLatticeTowerCrane25D(Canvas canvas, Paint paint, Paint strokePaint, float baseX, float baseY, float jibLen, float angleDeg, int mainCol, int rimCol, int loadKind, float phase) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#475569"));
        canvas.drawRect(baseX - 12f * dp, baseY - 12f * dp, baseX + 12f * dp, baseY + 12f * dp, paint);

        canvas.save(); canvas.translate(baseX + 22f * dp, baseY + 26f * dp); canvas.rotate(angleDeg);
        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(Color.parseColor("#38000000")); strokePaint.setStrokeWidth(8f * dp);
        canvas.drawLine(-jibLen * 0.28f, 0f, jibLen, 0f, strokePaint); canvas.restore();

        canvas.save(); canvas.translate(baseX, baseY); canvas.rotate(angleDeg);
        float counterLen = jibLen * 0.28f, halfTruss = 5.5f * dp;
        float trolleyPos = jibLen * (0.42f + 0.36f * (0.5f + 0.5f * (float) Math.sin(phase * 0.9f)));

        paint.setColor(Color.parseColor("#44000000")); canvas.drawRect(trolleyPos - 8f * dp, 14f * dp, trolleyPos + 8f * dp, 28f * dp, paint);
        if (loadKind == 0) drawHollowPrecastBox25D(canvas, paint, trolleyPos, 6f * dp, 16f * dp, 14f * dp);
        else if (loadKind == 1) { paint.setColor(Color.parseColor("#334155")); canvas.drawRect(trolleyPos - 18f * dp, 2f * dp, trolleyPos + 18f * dp, 10f * dp, paint); }
        else { paint.setColor(Color.parseColor("#C2410C")); canvas.drawRect(trolleyPos - 10f * dp, 2f * dp, trolleyPos + 10f * dp, 12f * dp, paint); }

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(rimCol); strokePaint.setStrokeWidth(1.8f * dp);
        for (float lx = -counterLen; lx < jibLen - 12f * dp; lx += 12f * dp) {
            canvas.drawLine(lx, -halfTruss, lx + 12f * dp, halfTruss, strokePaint);
            canvas.drawLine(lx, halfTruss, lx + 12f * dp, -halfTruss, strokePaint);
        }

        strokePaint.setColor(mainCol); strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawLine(-counterLen, -halfTruss, jibLen, -halfTruss * 0.5f, strokePaint);
        canvas.drawLine(-counterLen, halfTruss, jibLen, halfTruss * 0.5f, strokePaint);
        canvas.drawLine(-counterLen, 0f, jibLen, 0f, strokePaint);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#64748B"));
        canvas.drawRect(-counterLen + 2f * dp, -halfTruss - 2f * dp, -counterLen + 18f * dp, halfTruss + 2f * dp, paint);
        paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawRoundRect(new RectF(-6f * dp, halfTruss, 8f * dp, halfTruss + 9f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(mainCol); canvas.drawCircle(0f, 0f, 8f * dp, paint);
        canvas.restore();
    }

    private void drawConstructionDustAndSmoke(Canvas canvas, Paint paint, float ox, float oy, float phase, boolean heavyDiggingDust) {
        paint.setStyle(Paint.Style.FILL);
        int count = heavyDiggingDust ? 6 : 4;
        for (int i = 0; i < count; i++) {
            float p = ((phase * 0.55f) + i * (1.0f / count)) % 1.0f;
            double ang = i * 1.7 + phase * 0.3;
            float dist = p * (heavyDiggingDust ? 38f : 24f) * dp;
            float px = ox + (float) Math.cos(ang) * dist, py = oy + (float) Math.sin(ang) * dist;
            float rad = (6f + p * (heavyDiggingDust ? 18f : 12f)) * dp;
            int alpha = Math.max(0, Math.round((1.0f - p) * (heavyDiggingDust ? 135 : 95)));
            paint.setColor((i % 2 == 0) ? Color.argb(alpha, 212, 163, 115) : Color.argb(alpha, 100, 116, 139));
            canvas.drawCircle(px, py, rad, paint);
        }
        paint.setAlpha(255);
    }

    private void drawDetailedAirport25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int apronCol       = isSunnyDay ? Color.parseColor("#576A76") : (isMoonlightMode ? Color.parseColor("#1E293B") : Color.parseColor("#111827"));
        int runwayCol      = isSunnyDay ? Color.parseColor("#45545E") : (isMoonlightMode ? Color.parseColor("#0F172A") : Color.parseColor("#090D16"));
        int roofCreamCol   = isSunnyDay ? Color.parseColor("#F5E6CC") : Color.parseColor("#CBD5E1");
        int roofTrimCol    = isSunnyDay ? Color.parseColor("#D7C09D") : Color.parseColor("#94A3B8");
        int skylightCol    = isSunnyDay ? Color.parseColor("#00A8E8") : Color.parseColor("#38BDF8");
        int skylightRimCol = isSunnyDay ? Color.parseColor("#80DEEA") : Color.parseColor("#BAE6FD");
        int guideLineCol   = isSunnyDay ? Color.parseColor("#90A4AE") : Color.parseColor("#475569");

        paint.setStyle(Paint.Style.FILL); paint.setColor(apronCol);
        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        canvas.drawRoundRect(tempRect, 20f * dp, 20f * dp, paint);

        canvas.save();
        tempPath.reset(); tempPath.addRoundRect(tempRect, 20f * dp, 20f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.SQUARE);
        strokePaint.setColor(guideLineCol); strokePaint.setStrokeWidth(2.2f * dp);

        canvas.drawLine(cx - w * 0.76f, cy - h * 0.44f, cx - w * 0.76f, cy - h * 0.34f, strokePaint);
        canvas.drawLine(cx - w * 0.76f, cy - h * 0.34f, cx + w * 0.68f, cy - h * 0.34f, strokePaint);
        canvas.drawLine(cx + w * 0.68f, cy - h * 0.34f, cx + w * 0.68f, cy - h * 0.44f, strokePaint);

        canvas.drawLine(cx - w * 0.86f, cy + h * 0.20f, cx + w * 0.86f, cy + h * 0.20f, strokePaint);
        canvas.drawLine(cx - w * 0.86f, cy + h * 0.20f, cx - w * 0.86f, cy + h * 0.38f, strokePaint);
        canvas.drawLine(cx + w * 0.86f, cy + h * 0.20f, cx + w * 0.86f, cy + h * 0.38f, strokePaint);
        canvas.drawLine(cx - w * 0.56f, cy + h * 0.78f, cx + w * 0.56f, cy + h * 0.78f, strokePaint);

        drawHexagonPad(canvas, paint, strokePaint, cx - w * 0.72f, cy + h * 0.74f, w * 0.16f, runwayCol, guideLineCol);
        drawHexagonPad(canvas, paint, strokePaint, cx + w * 0.72f, cy + h * 0.74f, w * 0.16f, runwayCol, guideLineCol);

        float rwTop = cy - h * 0.25f, rwBot = cy + h * 0.11f, rwCY = (rwTop + rwBot) * 0.5f;

        paint.setStyle(Paint.Style.FILL); paint.setColor(runwayCol);
        canvas.drawRect(cx - w, rwTop, cx + w, rwBot, paint);
        canvas.drawRect(cx - w * 0.10f, cy - h * 0.44f, cx + w * 0.10f, cy + h * 0.28f, paint);
        canvas.drawRect(cx - w * 0.13f, cy + h * 0.86f, cx + w * 0.13f, cy + h, paint);

        strokePaint.setColor(Color.WHITE); strokePaint.setStrokeWidth(4.5f * dp);
        canvas.drawLine(cx - w, rwTop, cx - w * 0.10f, rwTop, strokePaint);
        canvas.drawLine(cx + w * 0.10f, rwTop, cx + w, rwTop, strokePaint);
        canvas.drawLine(cx - w, rwBot, cx - w * 0.10f, rwBot, strokePaint);
        canvas.drawLine(cx + w * 0.10f, rwBot, cx + w, rwBot, strokePaint);

        canvas.drawLine(cx - w * 0.10f, cy - h * 0.42f, cx - w * 0.10f, rwTop, strokePaint);
        canvas.drawLine(cx + w * 0.10f, cy - h * 0.42f, cx + w * 0.10f, rwTop, strokePaint);
        canvas.drawLine(cx - w * 0.10f, rwBot, cx - w * 0.10f, cy + h * 0.26f, strokePaint);
        canvas.drawLine(cx + w * 0.10f, rwBot, cx + w * 0.10f, cy + h * 0.26f, strokePaint);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        for (int z = -2; z <= 2; z++) {
            float zx = cx + z * (w * 0.034f);
            canvas.drawRect(zx - 3.5f * dp, cy - h * 0.42f, zx + 3.5f * dp, rwTop - 4f * dp, paint);
            canvas.drawRect(zx - 3.5f * dp, rwBot + 4f * dp, zx + 3.5f * dp, cy + h * 0.26f, paint);
            canvas.drawRect(zx - 3.5f * dp, cy + h * 0.88f, zx + 3.5f * dp, cy + h * 0.99f, paint);
        }

        strokePaint.setColor(Color.WHITE); strokePaint.setStrokeWidth(3.2f * dp); strokePaint.setPathEffect(laneDash);
        canvas.drawLine(cx - w, rwCY, cx + w, rwCY, strokePaint); strokePaint.setPathEffect(null);

        for (int lIdx = -5; lIdx <= 5; lIdx++) {
            if (lIdx == 0) continue;
            float lx = cx + lIdx * (w * 0.17f);
            if (!isSunnyDay) {
                paint.setColor(Color.parseColor("#55FACC15"));
                canvas.drawCircle(lx, rwTop + 7f * dp, 7.5f * dp, paint);
                canvas.drawCircle(lx, rwBot - 7f * dp, 7.5f * dp, paint);
            }
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRect(lx - 3f * dp, rwTop + 4f * dp, lx + 3f * dp, rwTop + 10f * dp, paint);
            canvas.drawRect(lx - 3f * dp, rwBot - 10f * dp, lx + 3f * dp, rwBot - 4f * dp, paint);
        }

        RectF hangarRect = new RectF(cx - w * 0.88f, cy - h * 0.88f, cx - w * 0.64f, cy - h * 0.50f);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRect(hangarRect.left + 8f * dp, hangarRect.top + 10f * dp, hangarRect.right + 8f * dp, hangarRect.bottom + 10f * dp, paint);
        paint.setColor(roofCreamCol); canvas.drawRect(hangarRect, paint);
        paint.setColor(roofTrimCol); canvas.drawRect(hangarRect.centerX() - 4f * dp, hangarRect.top, hangarRect.centerX() + 4f * dp, hangarRect.bottom, paint);
        strokePaint.setColor(roofTrimCol); strokePaint.setStrokeWidth(2f * dp);
        for (int hr = 1; hr <= 5; hr++) {
            float ry = hangarRect.top + hr * (hangarRect.height() / 6f);
            canvas.drawLine(hangarRect.left, ry, hangarRect.right, ry, strokePaint);
        }
        strokePaint.setColor(Color.parseColor("#CFD8DC"));
        canvas.drawLine(hangarRect.left + 6f * dp, hangarRect.bottom, hangarRect.left, hangarRect.bottom + 14f * dp, strokePaint);
        canvas.drawLine(hangarRect.right - 6f * dp, hangarRect.bottom, hangarRect.right, hangarRect.bottom + 14f * dp, strokePaint);

        float radX = cx - w * 0.52f, radY = cy - h * 0.80f;
        paint.setColor(Color.parseColor("#E53935")); canvas.drawCircle(radX, radY, 16f * dp, paint);
        paint.setColor(Color.parseColor("#FFB300")); canvas.drawCircle(radX, radY, 11f * dp, paint);
        paint.setColor(Color.parseColor("#8E24AA")); canvas.drawCircle(radX, radY, 7f * dp, paint);

        RectF techRect = new RectF(cx - w * 0.60f, cy - h * 0.70f, cx - w * 0.34f, cy - h * 0.50f);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRect(techRect.left + 7f * dp, techRect.top + 8f * dp, techRect.right + 7f * dp, techRect.bottom + 8f * dp, paint);
        paint.setColor(Color.parseColor("#CFD8DC")); canvas.drawRect(techRect, paint);
        tempRect.set(techRect); tempRect.inset(3f * dp, 3f * dp);
        paint.setColor(Color.parseColor("#A1887F")); canvas.drawRect(tempRect, paint);
        drawRooftopAC(canvas, paint, techRect.right - 22f * dp, techRect.top + 10f * dp);
        drawRooftopAC(canvas, paint, techRect.right - 10f * dp, techRect.top + 10f * dp);
        RectF satBox = new RectF(techRect.left + 4f * dp, techRect.bottom - 20f * dp, techRect.left + 26f * dp, techRect.bottom + 2f * dp);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRect(satBox.left + 4f * dp, satBox.top + 4f * dp, satBox.right + 4f * dp, satBox.bottom + 4f * dp, paint);
        paint.setColor(Color.WHITE); canvas.drawRect(satBox, paint);
        paint.setColor(roofTrimCol); canvas.drawCircle(satBox.centerX(), satBox.centerY(), 7.5f * dp, paint);

        drawGroundSupportTruck(canvas, paint, cx - w * 0.27f, cy - h * 0.58f, 16f * dp, 38f * dp, Color.parseColor("#FFB300"), true);
        drawGroundSupportTruck(canvas, paint, cx - w * 0.18f, cy - h * 0.58f, 16f * dp, 36f * dp, Color.parseColor("#E53935"), false);

        RectF ctrlStem = new RectF(cx - w * 0.11f, cy - h * 0.80f, cx + w * 0.11f, cy - h * 0.44f);
        RectF ctrlTop  = new RectF(cx - w * 0.19f, cy - h * 0.88f, cx + w * 0.19f, cy - h * 0.78f);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(ctrlStem.left + 8f * dp, ctrlStem.top + 10f * dp, ctrlStem.right + 8f * dp, ctrlStem.bottom + 10f * dp), 6f * dp, 6f * dp, paint);
        canvas.drawRoundRect(new RectF(ctrlTop.left + 8f * dp, ctrlTop.top + 10f * dp, ctrlTop.right + 8f * dp, ctrlTop.bottom + 10f * dp), 6f * dp, 6f * dp, paint);

        paint.setColor(roofCreamCol);
        canvas.drawRoundRect(ctrlStem, 6f * dp, 6f * dp, paint);
        canvas.drawRoundRect(ctrlTop, 6f * dp, 6f * dp, paint);

        float skW = (ctrlStem.width() - 18f * dp) * 0.5f, skH = (ctrlStem.height() - 32f * dp) / 5f;
        paint.setColor(skylightCol);
        for (int r = 0; r < 5; r++) {
            float sy = ctrlStem.top + 14f * dp + r * (skH + 2.5f * dp);
            canvas.drawRect(ctrlStem.left + 7f * dp, sy, ctrlStem.left + 7f * dp + skW, sy + skH, paint);
            canvas.drawRect(ctrlStem.centerX() + 2f * dp, sy, ctrlStem.centerX() + 2f * dp + skW, sy + skH, paint);
        }
        for (int c = -1; c <= 1; c++) {
            float bx = ctrlTop.centerX() + c * (ctrlTop.width() * 0.25f);
            canvas.drawRoundRect(new RectF(bx - 10f * dp, ctrlTop.top + 5f * dp, bx + 10f * dp, ctrlTop.bottom - 5f * dp), 2f * dp, 2f * dp, paint);
        }

        RectF whiteBus = new RectF(cx + w * 0.15f, cy - h * 0.62f, cx + w * 0.22f, cy - h * 0.46f);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(whiteBus.left + 4f * dp, whiteBus.top + 5f * dp, whiteBus.right + 4f * dp, whiteBus.bottom + 5f * dp), 4f * dp, 4f * dp, paint);
        paint.setColor(Color.WHITE); canvas.drawRoundRect(whiteBus, 4f * dp, 4f * dp, paint);
        paint.setColor(skylightCol);
        canvas.drawRect(whiteBus.left + 3f * dp, whiteBus.top + 4f * dp, whiteBus.right - 3f * dp, whiteBus.top + 9f * dp, paint);
        canvas.drawRect(whiteBus.left + 3f * dp, whiteBus.bottom - 9f * dp, whiteBus.right - 3f * dp, whiteBus.bottom - 4f * dp, paint);

        float hpX = cx + w * 0.68f, hpY = cy - h * 0.64f, hpR = w * 0.18f;
        strokePaint.setColor(Color.WHITE); strokePaint.setStrokeWidth(3.2f * dp);
        canvas.drawCircle(hpX, hpY, hpR, strokePaint);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
        float hBarW = hpR * 0.18f, hBarH = hpR * 0.58f;
        canvas.drawRect(hpX - hpR * 0.45f, hpY - hBarH, hpX - hpR * 0.45f + hBarW, hpY + hBarH, paint);
        canvas.drawRect(hpX + hpR * 0.45f - hBarW, hpY - hBarH, hpX + hpR * 0.45f, hpY + hBarH, paint);
        canvas.drawRect(hpX - hpR * 0.45f, hpY - hBarW * 0.45f, hpX + hpR * 0.45f, hpY + hBarW * 0.45f, paint);

        canvas.save(); canvas.translate(hpX + 4f * dp, hpY);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawOval(new RectF(-24f * dp, -9f * dp + 6f * dp, 30f * dp, 9f * dp + 6f * dp), paint);
        paint.setColor(Color.parseColor("#FF7043"));
        canvas.drawRect(-38f * dp, -2.5f * dp, -6f * dp, 2.5f * dp, paint);
        canvas.drawRect(-34f * dp, -7f * dp, -30f * dp, 7f * dp, paint);
        canvas.drawOval(new RectF(-12f * dp, -9.5f * dp, 26f * dp, 9.5f * dp), paint);
        paint.setColor(Color.parseColor("#FFCA28"));
        canvas.drawOval(new RectF(-6f * dp, -6.5f * dp, 18f * dp, 6.5f * dp), paint);
        paint.setColor(skylightCol);
        canvas.drawOval(new RectF(10f * dp, -6f * dp, 23f * dp, 6f * dp), paint);
        canvas.rotate(starPhase * 22f);
        strokePaint.setColor(Color.parseColor("#263238")); strokePaint.setStrokeWidth(3f * dp);
        canvas.drawLine(-32f * dp, -32f * dp, 32f * dp, 32f * dp, strokePaint);
        canvas.drawLine(-32f * dp, 32f * dp, 32f * dp, -32f * dp, strokePaint);
        paint.setColor(Color.WHITE); canvas.drawCircle(0f, 0f, 3.5f * dp, paint);
        canvas.restore();

        float termCX = cx, domeCY = cy + h * 0.44f, domeR = w * 0.17f;
        RectF lowerWing = new RectF(cx - w * 0.30f, cy + h * 0.56f, cx + w * 0.30f, cy + h * 0.86f);

        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#44000000"));
        canvas.drawCircle(termCX + 10f * dp, domeCY + 12f * dp, domeR, paint);
        canvas.drawRect(lowerWing.left + 10f * dp, lowerWing.top - 16f * dp, lowerWing.right + 10f * dp, lowerWing.bottom + 10f * dp, paint);

        float[] jetwayYs = { cy + h * 0.38f, cy + h * 0.51f };
        for (int j = 0; j < 2; j++) {
            float jy = jetwayYs[j];
            paint.setColor(Color.parseColor("#ECEFF1"));
            canvas.drawRect(cx - w * 0.30f, jy - 5f * dp, cx - domeR * 0.85f, jy + 5f * dp, paint);
            paint.setColor(Color.parseColor("#E53935"));
            canvas.drawRect(cx - w * 0.30f, jy - 11f * dp, cx - w * 0.24f, jy - 5f * dp, paint);

            paint.setColor(Color.parseColor("#ECEFF1"));
            canvas.drawRect(cx + domeR * 0.85f, jy - 5f * dp, cx + w * 0.30f, jy + 5f * dp, paint);
            paint.setColor(Color.parseColor("#E53935"));
            canvas.drawRect(cx + w * 0.24f, jy - 11f * dp, cx + w * 0.30f, jy - 5f * dp, paint);
        }

        paint.setColor(roofTrimCol);
        canvas.drawCircle(termCX, domeCY, domeR, paint);
        canvas.drawRoundRect(lowerWing, 6f * dp, 6f * dp, paint);

        paint.setColor(roofCreamCol);
        canvas.drawCircle(termCX, domeCY, domeR - 4f * dp, paint);
        canvas.drawRect(termCX - domeR + 4f * dp, domeCY, termCX + domeR - 4f * dp, lowerWing.top + 8f * dp, paint);
        tempRect.set(lowerWing); tempRect.inset(4f * dp, 4f * dp);
        canvas.drawRoundRect(tempRect, 4f * dp, 4f * dp, paint);

        drawRadialSkylightDome(canvas, paint, strokePaint, termCX, domeCY, domeR * 0.58f, skylightCol, skylightRimCol);
        drawRadialSkylightDome(canvas, paint, strokePaint, termCX, lowerWing.centerY(), domeR * 0.56f, skylightCol, skylightRimCol);

        paint.setStyle(Paint.Style.FILL); paint.setColor(skylightCol);
        for (int s = 0; s < 3; s++) {
            float lx = lowerWing.left + 12f * dp + s * 14f * dp, rx = lowerWing.right - 12f * dp - s * 14f * dp;
            canvas.drawRoundRect(new RectF(lx - 3.5f * dp, lowerWing.top + 10f * dp, lx + 3.5f * dp, lowerWing.bottom - 10f * dp), 3f * dp, 3f * dp, paint);
            canvas.drawRoundRect(new RectF(rx - 3.5f * dp, lowerWing.top + 10f * dp, rx + 3.5f * dp, lowerWing.bottom - 10f * dp), 3f * dp, 3f * dp, paint);
        }

        drawDetailedJetAirliner25D(canvas, paint, strokePaint, cx - w * 0.50f, cy + h * 0.45f, 0f, 1.12f, 255, 8f * dp, 10f * dp, Color.parseColor("#FF5252"), Color.parseColor("#1976D2"), false, starPhase);
        drawDetailedJetAirliner25D(canvas, paint, strokePaint, cx + w * 0.45f, cy + h * 0.34f, 180f, 0.85f, 255, 7f * dp, 9f * dp, Color.parseColor("#E53935"), Color.parseColor("#0288D1"), false, starPhase);
        drawTurbopropPlane25D(canvas, paint, strokePaint, cx + w * 0.72f, cy + h * 0.74f, starPhase);

        float bagY = cy + h * 0.32f;
        int[] cartCols = { Color.parseColor("#E53935"), Color.parseColor("#43A047"), Color.parseColor("#1E88E5") };
        for (int c = 0; c < 3; c++) {
            float bx = cx - w * 0.84f + c * 15f * dp;
            paint.setColor(Color.WHITE); canvas.drawRect(bx - 5.5f * dp, bagY - 4f * dp, bx + 5.5f * dp, bagY + 4f * dp, paint);
            paint.setColor(cartCols[c]); canvas.drawRect(bx - 3.5f * dp, bagY - 2.5f * dp, bx + 3.5f * dp, bagY + 2.5f * dp, paint);
        }
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(cx - w * 0.84f + 42f * dp, bagY - 4.5f * dp, cx - w * 0.84f + 54f * dp, bagY + 4.5f * dp), 2f * dp, 2f * dp, paint);

        drawGroundSupportTruck(canvas, paint, cx - w * 0.77f, cy + h * 0.74f, 14f * dp, 34f * dp, Color.parseColor("#FBC02D"), true);
        drawGroundSupportTruck(canvas, paint, cx - w * 0.67f, cy + h * 0.76f, 12f * dp, 20f * dp, Color.parseColor("#F57F17"), false);

        float cycleDuration = 1.45f, rawCycle = starPhase * 0.18f;
        int liveryIdx = ((int) (rawCycle / cycleDuration)) % 4;
        float t = (rawCycle % cycleDuration) / cycleDuration;

        int[][] liveries = {
                { Color.parseColor("#FF5252"), Color.parseColor("#0288D1") }, { Color.parseColor("#1E88E5"), Color.parseColor("#FFB300") },
                { Color.parseColor("#00897B"), Color.parseColor("#FBC02D") }, { Color.parseColor("#8E24AA"), Color.parseColor("#FF7043") }
        };

        float planeX, planeScale = 0.82f, shadowOffX = 8f * dp, shadowOffY = 12f * dp;
        int planeAlpha = 255; boolean showTakeoffStreaks = false;

        if (t < 0.16f) {
            float p = t / 0.16f; planeX = (cx - w * 0.88f) + p * (w * 0.12f);
            if (p > 0.5f) showTakeoffStreaks = true;
        } else if (t < 0.55f) {
            float p = (t - 0.16f) / 0.39f; planeX = (cx - w * 0.76f) + (p * p) * (w * 0.96f);
            showTakeoffStreaks = true;
        } else if (t < 0.92f) {
            float p = (t - 0.55f) / 0.37f;
            planeX = (cx + w * 0.20f) + p * (w * 0.78f); planeScale = 0.82f + p * 0.58f;
            shadowOffX = (8f + p * 48f) * dp; shadowOffY = (12f + p * 56f) * dp; showTakeoffStreaks = true;
            if (p > 0.45f) planeAlpha = Math.max(0, Math.round(255f * (1f - (p - 0.45f) / 0.55f)));
        } else {
            planeAlpha = 0; planeX = cx - w * 0.88f;
        }

        if (planeAlpha > 5) {
            drawDetailedJetAirliner25D(canvas, paint, strokePaint, planeX, rwCY, 0f, planeScale, planeAlpha, shadowOffX, shadowOffY, liveries[liveryIdx][0], liveries[liveryIdx][1], showTakeoffStreaks, starPhase);
        }

        canvas.restore();
        strokePaint.setColor(Color.parseColor("#37474F")); strokePaint.setStrokeWidth(3f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 20f * dp, 20f * dp, strokePaint);
    }

    private void drawDetailedJetAirliner25D(Canvas canvas, Paint paint, Paint strokePaint, float x, float y, float angleDeg, float scale, int alpha, float shadowDx, float shadowDy, int wingStripeCol, int accentCol, boolean speedTrails, float starPhase) {
        canvas.save(); canvas.translate(x, y); canvas.rotate(angleDeg);

        int shadowAlpha = Math.min(95, (alpha * 95) / 255);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(shadowAlpha, 0, 0, 0));
        canvas.save(); canvas.translate(shadowDx, shadowDy);
        float shadowScale = scale * Math.max(0.55f, 1.0f - (shadowDx / (120f * dp)));
        canvas.scale(shadowScale, shadowScale); drawAirlinerSilhouettePath(canvas, paint); canvas.restore();

        canvas.scale(scale, scale);

        if (speedTrails && alpha > 20) {
            strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(Color.argb(Math.min(180, (alpha * 180) / 255), 255, 255, 255));
            strokePaint.setStrokeWidth(1.6f * dp);
            float[] trailYs = { -38f * dp, -26f * dp, -14f * dp, 14f * dp, 26f * dp, 38f * dp };
            for (int i = 0; i < trailYs.length; i++) {
                float ty = trailYs[i], len = (i == 1 || i == 4) ? 82f * dp : 58f * dp;
                canvas.drawLine(-18f * dp - len, ty, -18f * dp, ty, strokePaint);
            }
        }

        paint.setStyle(Paint.Style.FILL); paint.setColor(applyAlpha(Color.parseColor("#CFD8DC"), alpha));
        float[] engYs = { -22f * dp, -12f * dp, 12f * dp, 22f * dp };
        for (float ey : engYs) canvas.drawRoundRect(new RectF(-6f * dp, ey - 2.6f * dp, 8f * dp, ey + 2.6f * dp), 2f * dp, 2f * dp, paint);

        tempPath.reset();
        tempPath.moveTo(10f * dp, -4.5f * dp); tempPath.lineTo(-10f * dp, -48f * dp); tempPath.lineTo(-18f * dp, -48f * dp);
        tempPath.lineTo(-8f * dp, -4.5f * dp); tempPath.lineTo(-8f * dp, 4.5f * dp); tempPath.lineTo(-18f * dp, 48f * dp);
        tempPath.lineTo(-10f * dp, 48f * dp); tempPath.lineTo(10f * dp, 4.5f * dp); tempPath.close();
        paint.setColor(applyAlpha(Color.WHITE, alpha)); canvas.drawPath(tempPath, paint);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(applyAlpha(wingStripeCol, alpha));
        strokePaint.setStrokeWidth(3.2f * dp);
        canvas.drawLine(6f * dp, -6f * dp, -12f * dp, -45f * dp, strokePaint);
        canvas.drawLine(6f * dp, 6f * dp, -12f * dp, 45f * dp, strokePaint);
        strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawLine(0f, -12f * dp, -15f * dp, -42f * dp, strokePaint);
        canvas.drawLine(0f, 12f * dp, -15f * dp, 42f * dp, strokePaint);

        tempPath.reset();
        tempPath.moveTo(-28f * dp, -3f * dp); tempPath.lineTo(-39f * dp, -16f * dp); tempPath.lineTo(-43f * dp, -16f * dp);
        tempPath.lineTo(-36f * dp, 0f); tempPath.lineTo(-43f * dp, 16f * dp); tempPath.lineTo(-39f * dp, 16f * dp);
        tempPath.lineTo(-28f * dp, 3f * dp); tempPath.close();
        paint.setColor(applyAlpha(Color.WHITE, alpha)); canvas.drawPath(tempPath, paint);
        strokePaint.setColor(applyAlpha(wingStripeCol, alpha)); strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawLine(-31f * dp, -3f * dp, -41f * dp, -15f * dp, strokePaint);
        canvas.drawLine(-31f * dp, 3f * dp, -41f * dp, 15f * dp, strokePaint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(applyAlpha(Color.parseColor("#DCE4EC"), alpha));
        canvas.drawRoundRect(new RectF(-40f * dp, -5.2f * dp, 36f * dp, 5.2f * dp), 12f * dp, 12f * dp, paint);
        paint.setColor(applyAlpha(Color.WHITE, alpha));
        canvas.drawRoundRect(new RectF(-39f * dp, -5.2f * dp, 36f * dp, 2.2f * dp), 10f * dp, 10f * dp, paint);

        paint.setColor(applyAlpha(accentCol, alpha));
        canvas.drawArc(new RectF(24f * dp, -3.8f * dp, 33f * dp, 3.8f * dp), -75f, 150f, true, paint);

        int winDotCol = (!isSunnyDay) ? Color.parseColor("#FDE047") : accentCol;
        paint.setColor(applyAlpha(winDotCol, alpha));
        for (int wIdx = -6; wIdx <= 5; wIdx++) {
            float wx = wIdx * 4.2f * dp;
            canvas.drawCircle(wx, -3.2f * dp, 0.9f * dp, paint); canvas.drawCircle(wx, 3.2f * dp, 0.9f * dp, paint);
        }

        paint.setColor(applyAlpha(wingStripeCol, alpha));
        canvas.drawRoundRect(new RectF(-42f * dp, -1.3f * dp, -28f * dp, 1.3f * dp), 1.5f * dp, 1.5f * dp, paint);

        if (!isSunnyDay && alpha > 40) {
            paint.setColor(applyAlpha(Color.parseColor("#EF4444"), alpha)); canvas.drawCircle(-14f * dp, -47f * dp, 2.4f * dp, paint);
            paint.setColor(applyAlpha(Color.parseColor("#22C55E"), alpha)); canvas.drawCircle(-14f * dp, 47f * dp, 2.4f * dp, paint);
            if (((int) (starPhase * 5f)) % 2 == 0) {
                paint.setColor(applyAlpha(Color.WHITE, alpha)); canvas.drawCircle(-41f * dp, 0f, 3.0f * dp, paint);
            }
        }
        canvas.restore();
    }

    private void drawAirlinerSilhouettePath(Canvas canvas, Paint paint) {
        tempPath.reset();
        tempPath.moveTo(36f * dp, 0f); tempPath.lineTo(10f * dp, -5f * dp); tempPath.lineTo(-10f * dp, -48f * dp);
        tempPath.lineTo(-18f * dp, -48f * dp); tempPath.lineTo(-8f * dp, -5f * dp); tempPath.lineTo(-28f * dp, -4f * dp);
        tempPath.lineTo(-39f * dp, -16f * dp); tempPath.lineTo(-43f * dp, 0f); tempPath.lineTo(-39f * dp, 16f * dp);
        tempPath.lineTo(-28f * dp, 4f * dp); tempPath.lineTo(-8f * dp, 5f * dp); tempPath.lineTo(-18f * dp, 48f * dp);
        tempPath.lineTo(-10f * dp, 48f * dp); tempPath.lineTo(10f * dp, 5f * dp); tempPath.close();
        canvas.drawPath(tempPath, paint);
    }

    private void drawTurbopropPlane25D(Canvas canvas, Paint paint, Paint strokePaint, float x, float y, float starPhase) {
        canvas.save(); canvas.translate(x, y);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#E53935"));
        canvas.drawRoundRect(new RectF(-34f * dp, 6f * dp, -10f * dp, 16f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.parseColor("#FFB300")); canvas.drawRect(-32f * dp, 8f * dp, -12f * dp, 11f * dp, paint);

        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(-42f * dp + 5f * dp, -2f * dp + 6f * dp, 42f * dp + 5f * dp, 8f * dp + 6f * dp), 3f * dp, 3f * dp, paint);
        canvas.drawRoundRect(new RectF(-4f * dp + 5f * dp, -28f * dp + 6f * dp, 4f * dp + 5f * dp, 28f * dp + 6f * dp), 4f * dp, 4f * dp, paint);

        paint.setColor(Color.WHITE); canvas.drawRoundRect(new RectF(-42f * dp, -3f * dp, 42f * dp, 7f * dp), 3f * dp, 3f * dp, paint);
        paint.setColor(Color.parseColor("#E53935")); canvas.drawRect(-40f * dp, -3f * dp, 40f * dp, 0f, paint);

        paint.setColor(Color.WHITE); canvas.drawRoundRect(new RectF(-14f * dp, 22f * dp, 14f * dp, 28f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.parseColor("#E53935")); canvas.drawRect(-12f * dp, 25f * dp, 12f * dp, 28f * dp, paint);

        paint.setColor(Color.WHITE); canvas.drawRoundRect(new RectF(-4.5f * dp, -26f * dp, 4.5f * dp, 30f * dp), 6f * dp, 6f * dp, paint);
        paint.setColor(Color.parseColor("#29B6F6")); canvas.drawArc(new RectF(-3.5f * dp, -22f * dp, 3.5f * dp, -14f * dp), 180f, 180f, true, paint);

        float propW = 13f * dp * (0.35f + 0.65f * Math.abs((float) Math.cos(starPhase * 18f)));
        paint.setColor(Color.parseColor("#FBC02D"));
        canvas.drawRoundRect(new RectF(-propW, -29f * dp, propW, -26.5f * dp), 1.5f * dp, 1.5f * dp, paint);
        canvas.restore();
    }

    private void drawRadialSkylightDome(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float r, int glassCol, int rimCol) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); canvas.drawCircle(cx, cy, r + 3f * dp, paint);
        paint.setColor(glassCol); canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(rimCol); canvas.drawCircle(cx, cy, r * 0.72f, paint);
        paint.setColor(glassCol); canvas.drawCircle(cx, cy, r * 0.58f, paint);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(Color.WHITE); strokePaint.setStrokeWidth(2.2f * dp);
        for (int i = 0; i < 4; i++) {
            double a = i * (Math.PI / 4.0);
            float dx = (float) Math.cos(a) * r, dy = (float) Math.sin(a) * r;
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, strokePaint);
        }
        paint.setColor(Color.WHITE); canvas.drawCircle(cx, cy, r * 0.34f, paint);
        paint.setColor(Color.parseColor("#263238")); canvas.drawCircle(cx, cy, 2.5f * dp, paint);
    }

    private void drawHexagonPad(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float radius, int fillCol, int strokeCol) {
        tempPath.reset();
        for (int i = 0; i < 6; i++) {
            double a = i * (Math.PI / 3.0);
            float px = cx + (float) Math.cos(a) * radius, py = cy + (float) Math.sin(a) * radius;
            if (i == 0) tempPath.moveTo(px, py); else tempPath.lineTo(px, py);
        }
        tempPath.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(fillCol); canvas.drawPath(tempPath, paint);
        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setColor(strokeCol); strokePaint.setStrokeWidth(2f * dp); canvas.drawPath(tempPath, strokePaint);
    }

    private void drawGroundSupportTruck(Canvas canvas, Paint paint, float cx, float cy, float w, float h, int bodyCol, boolean isTanker) {
        float hw = w * 0.5f, hh = h * 0.5f;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(cx - hw + 3f * dp, cy - hh + 4f * dp, cx + hw + 3f * dp, cy + hh + 4f * dp), 3f * dp, 3f * dp, paint);
        paint.setColor(Color.WHITE); canvas.drawRoundRect(new RectF(cx - hw, cy + hh * 0.35f, cx + hw, cy + hh), 2.5f * dp, 2.5f * dp, paint);
        paint.setColor(Color.parseColor("#29B6F6")); canvas.drawRect(cx - hw + 2f * dp, cy + hh * 0.55f, cx + hw - 2f * dp, cy + hh * 0.82f, paint);
        paint.setColor(bodyCol); canvas.drawRoundRect(new RectF(cx - hw, cy - hh, cx + hw, cy + hh * 0.35f), isTanker ? 5f * dp : 2f * dp, isTanker ? 5f * dp : 2f * dp, paint);
        paint.setColor(Color.parseColor("#FFF59D"));
        if (isTanker) { canvas.drawCircle(cx, cy - hh * 0.55f, 2.5f * dp, paint); canvas.drawCircle(cx, cy - hh * 0.10f, 2.5f * dp, paint); }
        else canvas.drawRect(cx - 2f * dp, cy - hh + 3f * dp, cx + 2f * dp, cy + hh * 0.25f, paint);
    }

    private void drawRooftopAC(Canvas canvas, Paint paint, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); canvas.drawRect(x - 4.5f * dp, y - 4.5f * dp, x + 4.5f * dp, y + 4.5f * dp, paint);
        paint.setColor(Color.parseColor("#263238")); canvas.drawCircle(x, y, 2.5f * dp, paint);
    }

    private int applyAlpha(int color, int alpha) {
        return Color.argb(Math.min(255, Math.max(0, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private void drawDenseRainforest25D(Canvas canvas, Paint paint, Paint strokePaint, Building b, float cx, float cy, float w, float h) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#052E16"));
        tempRect.set(cx - w * 0.92f, cy - h * 0.92f, cx + w * 0.92f, cy + h * 0.92f);
        canvas.drawRoundRect(tempRect, 64f * dp, 64f * dp, paint);

        paint.setColor(Color.parseColor("#6D543E")); canvas.drawOval(new RectF(cx - w * 0.24f, cy + h * 0.56f, cx + w * 0.30f, cy + h * 0.94f), paint);
        paint.setColor(Color.parseColor("#8D7358")); canvas.drawOval(new RectF(cx - w * 0.16f, cy + h * 0.64f, cx + w * 0.22f, cy + h * 0.88f), paint);

        drawTropicalPalmRosette(canvas, strokePaint, paint, cx - w * 0.12f, cy + h * 0.78f, 32f * dp);
        drawTropicalPalmRosette(canvas, strokePaint, paint, cx + w * 0.16f, cy + h * 0.80f, 30f * dp);
        drawTropicalPalmRosette(canvas, strokePaint, paint, cx + w * 0.02f, cy + h * 0.86f, 26f * dp);

        if (b.forestCrowns == null) return;
        paint.setColor(Color.parseColor("#44000000"));
        for (int i = 0; i < b.forestCrowns.size(); i++) {
            CanopyCrown c = b.forestCrowns.get(i);
            canvas.drawCircle(cx + c.ox + 9f * dp, cy + c.oy + 11f * dp, c.radius * 1.04f, paint);
        }

        for (int i = 0; i < b.forestCrowns.size(); i++) {
            CanopyCrown c = b.forestCrowns.get(i);
            float tx = cx + c.ox, ty = cy + c.oy, r = c.radius;
            paint.setColor(c.shadowCol); canvas.drawCircle(tx, ty, r, paint);
            for (int p = 0; p < 6; p++) canvas.drawCircle(tx + c.puffDx[p], ty + c.puffDy[p], c.puffR[p], paint);

            paint.setColor(c.midCol); canvas.drawCircle(tx - r * 0.08f, ty - r * 0.08f, r * 0.78f, paint);
            for (int p = 0; p < 6; p++) canvas.drawCircle(tx + c.puffDx[p] * 0.76f - r * 0.05f, ty + c.puffDy[p] * 0.76f - r * 0.05f, c.puffR[p] * 0.78f, paint);

            paint.setColor(c.highlightCol);
            canvas.drawCircle(tx - r * 0.24f, ty - r * 0.24f, r * 0.44f, paint);
            canvas.drawCircle(tx + r * 0.18f, ty - r * 0.22f, r * 0.34f, paint);
            canvas.drawCircle(tx - r * 0.14f, ty + r * 0.18f, r * 0.32f, paint);
            canvas.drawCircle(tx + r * 0.14f, ty + r * 0.12f, r * 0.26f, paint);
        }
    }

    private void drawTropicalPalmRosette(Canvas canvas, Paint strokePaint, Paint fillPaint, float px, float py, float rad) {
        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int f = 0; f < 9; f++) {
            double ang = f * (Math.PI * 2.0 / 9.0) + 0.2;
            float ex = px + (float) Math.cos(ang) * rad, ey = py + (float) Math.sin(ang) * rad;
            strokePaint.setColor(Color.parseColor("#14532D")); strokePaint.setStrokeWidth(4.5f * dp); canvas.drawLine(px, py, ex, ey, strokePaint);
            strokePaint.setColor(Color.parseColor("#4D7C0F")); strokePaint.setStrokeWidth(2.2f * dp); canvas.drawLine(px, py, ex, ey, strokePaint);
        }
        fillPaint.setStyle(Paint.Style.FILL); fillPaint.setColor(Color.parseColor("#65A30D")); canvas.drawCircle(px, py, 4.5f * dp, fillPaint);
    }

    private void drawDetailedAmusementPark(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#E9F3C0"));
        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        canvas.drawRoundRect(tempRect, 28f * dp, 28f * dp, paint);

        canvas.save();
        tempPath.reset(); tempPath.addRoundRect(tempRect, 28f * dp, 28f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        tempPath.reset();
        tempPath.moveTo(cx - w * 0.65f, cy - h); tempPath.cubicTo(cx - w * 0.65f, cy - h * 0.2f, cx - w * 0.55f, cy + h * 0.4f, cx - w * 0.58f, cy + h);
        tempPath.moveTo(cx - w * 0.02f, cy - h); tempPath.cubicTo(cx - w * 0.05f, cy - h * 0.45f, cx - w * 0.45f, cy - h * 0.35f, cx - w * 0.62f, cy - h * 0.15f);
        tempPath.moveTo(cx - w * 0.04f, cy - h * 0.55f); tempPath.cubicTo(cx + w * 0.22f, cy - h * 0.45f, cx + w * 0.28f, cy + h * 0.1f, cx + w * 0.85f, cy + h * 0.25f);
        tempPath.moveTo(cx + w * 0.58f, cy - h); tempPath.cubicTo(cx + w * 0.62f, cy - h * 0.55f, cx + w * 0.92f, cy - h * 0.45f, cx + w, cy - h * 0.35f);
        tempPath.moveTo(cx - w, cy - h * 0.48f); tempPath.lineTo(cx - w * 0.62f, cy - h * 0.42f);
        tempPath.moveTo(cx - w * 0.60f, cy + h * 0.22f); tempPath.cubicTo(cx - w * 0.20f, cy + h * 0.30f, cx - w * 0.25f, cy + h * 0.75f, cx - w * 0.12f, cy + h);
        tempPath.moveTo(cx + w * 0.68f, cy + h * 0.22f); tempPath.cubicTo(cx + w * 0.52f, cy + h * 0.50f, cx + w * 0.52f, cy + h * 0.78f, cx + w * 0.60f, cy + h);

        strokePaint.setStyle(Paint.Style.STROKE); strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.parseColor("#E2BEA0")); strokePaint.setStrokeWidth(28f * dp); canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(Color.parseColor("#F5DBC4")); strokePaint.setStrokeWidth(22f * dp); canvas.drawPath(tempPath, strokePaint);

        float trCX = cx - w * 0.40f, trCY = cy - h * 0.68f;
        RectF outerTrack = new RectF(trCX - w * 0.22f, trCY - h * 0.17f, trCX + w * 0.22f, trCY + h * 0.17f);
        RectF innerLoop  = new RectF(trCX - w * 0.12f, trCY - h * 0.04f, trCX + w * 0.30f, trCY + h * 0.12f);

        strokePaint.setColor(Color.parseColor("#8D6E63")); strokePaint.setStrokeWidth(9f * dp); strokePaint.setPathEffect(railSleeperDash);
        canvas.drawRoundRect(outerTrack, 32f * dp, 32f * dp, strokePaint); canvas.drawRoundRect(innerLoop, 22f * dp, 22f * dp, strokePaint);
        strokePaint.setPathEffect(null);

        strokePaint.setColor(Color.parseColor("#B7950B")); strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawRoundRect(outerTrack, 32f * dp, 32f * dp, strokePaint); canvas.drawRoundRect(innerLoop, 22f * dp, 22f * dp, strokePaint);

        RectF stRect = new RectF(trCX - w * 0.11f, trCY - h * 0.09f, trCX + w * 0.07f, trCY + h * 0.04f);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#33000000"));
        canvas.drawRect(stRect.left + 5f * dp, stRect.top + 6f * dp, stRect.right + 5f * dp, stRect.bottom + 6f * dp, paint);
        paint.setColor(Color.parseColor("#F28B9B")); canvas.drawRect(stRect, paint);
        tempPath.reset(); tempPath.moveTo(stRect.left, stRect.bottom); tempPath.lineTo(stRect.centerX(), stRect.centerY()); tempPath.lineTo(stRect.right, stRect.bottom); tempPath.close();
        paint.setColor(Color.parseColor("#D96B7B")); canvas.drawPath(tempPath, paint);
        strokePaint.setColor(Color.parseColor("#FFFFFF")); strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawLine(stRect.left, stRect.top, stRect.centerX(), stRect.centerY(), strokePaint);
        canvas.drawLine(stRect.right, stRect.top, stRect.centerX(), stRect.centerY(), strokePaint);

        for (int carIdx = 0; carIdx < 3; carIdx++) {
            double ta = starPhase * 1.4 - carIdx * 0.22;
            float tx = trCX + (float) Math.cos(ta) * (w * 0.22f), ty = trCY + (float) Math.sin(ta) * (h * 0.17f);
            paint.setColor(carIdx == 0 ? Color.parseColor("#C0392B") : Color.parseColor("#8E44AD"));
            canvas.drawRoundRect(new RectF(tx - 6f * dp, ty - 4f * dp, tx + 6f * dp, ty + 4f * dp), 2f * dp, 2f * dp, paint);
        }

        float gkX = cx - w * 0.82f, gkY = cy - h * 0.12f;
        tempPath.reset();
        tempPath.moveTo(gkX - w * 0.08f, gkY - h * 0.16f);
        tempPath.cubicTo(gkX - w * 0.16f, gkY - h * 0.30f, gkX + w * 0.10f, gkY - h * 0.30f, gkX + w * 0.10f, gkY - h * 0.12f);
        tempPath.lineTo(gkX + w * 0.10f, gkY + h * 0.12f);
        tempPath.cubicTo(gkX + w * 0.10f, gkY + h * 0.30f, gkX - w * 0.16f, gkY + h * 0.30f, gkX - w * 0.08f, gkY + h * 0.12f);
        tempPath.cubicTo(gkX - w * 0.06f, gkY + h * 0.02f, gkX + w * 0.02f, gkY + h * 0.02f, gkX + w * 0.02f, gkY + h * 0.12f);

        strokePaint.setColor(Color.parseColor("#7F8C8D")); strokePaint.setStrokeWidth(24f * dp); canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(Color.parseColor("#ECF0F1")); strokePaint.setStrokeWidth(2f * dp); strokePaint.setPathEffect(kartDash);
        canvas.drawPath(tempPath, strokePaint); strokePaint.setPathEffect(null);
        paint.setColor(Color.parseColor("#E8889A")); canvas.drawRect(gkX - w * 0.04f, gkY - h * 0.10f, gkX + w * 0.02f, gkY - h * 0.04f, paint);

        float casX = cx - w * 0.12f, casY = cy - h * 0.12f;
        paint.setColor(Color.parseColor("#38000000")); canvas.drawCircle(casX + 18f * dp, casY + 18f * dp, w * 0.16f, paint);
        paint.setColor(Color.parseColor("#9E826B")); canvas.drawRect(casX - w * 0.11f, casY - h * 0.10f, casX + w * 0.11f, casY + h * 0.10f, paint);

        float[][] bastions = { { casX - w * 0.14f, casY - h * 0.02f, 24f * dp }, { casX - w * 0.08f, casY - h * 0.12f, 22f * dp }, { casX + w * 0.09f, casY + h * 0.08f, 26f * dp } };
        for (float[] bTower : bastions) {
            paint.setColor(Color.parseColor("#8D735D")); canvas.drawCircle(bTower[0], bTower[1], bTower[2], paint);
            paint.setColor(Color.parseColor("#B89B82")); canvas.drawCircle(bTower[0], bTower[1], bTower[2] * 0.72f, paint);
            strokePaint.setColor(Color.parseColor("#D7CCC8")); strokePaint.setStrokeWidth(3.5f * dp); strokePaint.setPathEffect(railSleeperDash);
            canvas.drawCircle(bTower[0], bTower[1], bTower[2] * 0.86f, strokePaint); strokePaint.setPathEffect(null);
        }

        float[][] pinkCones = { { casX - w * 0.05f, casY + h * 0.06f, 26f * dp }, { casX + w * 0.01f, casY - h * 0.06f, 34f * dp }, { casX + w * 0.12f, casY - h * 0.12f, 25f * dp } };
        for (float[] pc : pinkCones) drawConicalRoof25D(canvas, paint, pc[0], pc[1], pc[2], Color.parseColor("#E06377"), Color.parseColor("#F48FB1"), Color.parseColor("#AD1457"));

        float rcX = cx + w * 0.16f, rcY = cy + h * 0.50f;
        RectF loop1 = new RectF(rcX - w * 0.30f, rcY - h * 0.22f, rcX + w * 0.18f, rcY + h * 0.20f);
        RectF loop2 = new RectF(rcX - w * 0.08f, rcY - h * 0.16f, rcX + w * 0.34f, rcY + h * 0.16f);

        strokePaint.setColor(Color.parseColor("#26000000")); strokePaint.setStrokeWidth(12f * dp);
        canvas.save(); canvas.translate(14f * dp, 16f * dp); canvas.drawOval(loop1, strokePaint); canvas.drawOval(loop2, strokePaint); canvas.restore();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(rcX - w * 0.05f + 16f * dp, rcY + 14f * dp, w * 0.13f, paint);
        paint.setColor(Color.parseColor("#7F8C8D")); canvas.drawCircle(rcX - w * 0.12f, rcY + h * 0.02f, w * 0.11f, paint);
        paint.setColor(Color.parseColor("#95A5A6")); canvas.drawCircle(rcX - w * 0.04f, rcY - h * 0.01f, w * 0.09f, paint);
        paint.setColor(Color.parseColor("#BDC3C7")); canvas.drawCircle(rcX + w * 0.02f, rcY - h * 0.04f, w * 0.065f, paint);

        strokePaint.setColor(Color.parseColor("#5499C7")); strokePaint.setStrokeWidth(8.5f * dp); strokePaint.setPathEffect(railSleeperDash);
        canvas.drawOval(loop1, strokePaint); canvas.drawOval(loop2, strokePaint); strokePaint.setPathEffect(null);
        strokePaint.setColor(Color.parseColor("#85C1E9")); strokePaint.setStrokeWidth(3.5f * dp);
        canvas.drawOval(loop1, strokePaint); canvas.drawOval(loop2, strokePaint);

        int[] coasterCols = { Color.parseColor("#E74C3C"), Color.parseColor("#F1C40F"), Color.parseColor("#2ECC71"), Color.parseColor("#9B59B6") };
        for (int c = 0; c < 4; c++) {
            double ca = starPhase * 2.4 - c * 0.16;
            float carX = loop1.centerX() + (float) Math.cos(ca) * (loop1.width() * 0.5f), carY = loop1.centerY() + (float) Math.sin(ca) * (loop1.height() * 0.5f);
            paint.setColor(coasterCols[c]); canvas.drawRoundRect(new RectF(carX - 6f * dp, carY - 4.5f * dp, carX + 6f * dp, carY + 4.5f * dp), 2.5f * dp, 2.5f * dp, paint);
        }

        float skX = cx + w * 0.42f, skY = cy - h * 0.72f;
        paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(skX + 8f * dp, skY + 8f * dp, w * 0.09f, paint);
        paint.setColor(Color.parseColor("#7F8C8D")); canvas.drawCircle(skX, skY, w * 0.09f, paint);
        paint.setColor(Color.parseColor("#95A5A6")); canvas.drawCircle(skX - 4f * dp, skY - 4f * dp, w * 0.07f, paint);
        paint.setColor(Color.parseColor("#2C3E50"));
        canvas.drawCircle(skX - 12f * dp, skY - 8f * dp, 7f * dp, paint); canvas.drawCircle(skX + 8f * dp, skY - 12f * dp, 7f * dp, paint); canvas.drawCircle(skX - 10f * dp, skY + 12f * dp, 9f * dp, paint);

        float fwX = cx + w * 0.74f, fwY = cy - h * 0.78f, shadowCX = fwX + 18f * dp, shadowCY = fwY + 38f * dp;
        strokePaint.setColor(Color.parseColor("#30000000")); strokePaint.setStrokeWidth(2.5f * dp);
        canvas.drawCircle(shadowCX, shadowCY, 32f * dp, strokePaint);
        for (int s = 0; s < 8; s++) {
            double sa = starPhase * 0.8 + s * (Math.PI / 4.0);
            float sx = shadowCX + (float) Math.cos(sa) * 32f * dp, sy = shadowCY + (float) Math.sin(sa) * 32f * dp;
            canvas.drawLine(shadowCX, shadowCY, sx, sy, strokePaint);
            paint.setColor(Color.parseColor("#30000000")); canvas.drawCircle(sx, sy, 4.5f * dp, paint);
        }
        paint.setColor(Color.parseColor("#5499C7")); canvas.drawRoundRect(new RectF(fwX - 46f * dp, fwY - 6f * dp, fwX + 46f * dp, fwY + 6f * dp), 4f * dp, 4f * dp, paint);
        for (int g = -3; g <= 3; g++) {
            float gxPos = fwX + g * 12f * dp + (float) Math.sin(starPhase + g) * 3f * dp;
            paint.setColor(coasterCols[Math.abs(g) % coasterCols.length]);
            canvas.drawRoundRect(new RectF(gxPos - 4.5f * dp, fwY - 9f * dp, gxPos + 4.5f * dp, fwY + 9f * dp), 3f * dp, 3f * dp, paint);
        }

        float wsX = cx + w * 0.56f, wsY = cy - h * 0.36f;
        tempPath.reset(); tempPath.moveTo(wsX - w * 0.18f, wsY + 12f * dp); tempPath.lineTo(wsX + w * 0.12f, wsY + 12f * dp);
        tempPath.lineTo(wsX - w * 0.08f, wsY + 38f * dp); tempPath.lineTo(wsX - w * 0.18f, wsY + 38f * dp); tempPath.close();
        paint.setColor(Color.parseColor("#33000000")); canvas.drawPath(tempPath, paint);

        RectF splashPool = new RectF(wsX + w * 0.08f, wsY - 22f * dp, wsX + w * 0.24f, wsY + 22f * dp);
        paint.setColor(Color.parseColor("#B39DDB")); canvas.drawRoundRect(splashPool, 4f * dp, 4f * dp, paint);
        splashPool.inset(4f * dp, 4f * dp); paint.setColor(Color.parseColor("#4DD0E1")); canvas.drawRect(splashPool, paint);

        paint.setColor(Color.parseColor("#26C6DA")); canvas.drawRect(wsX - w * 0.18f, wsY - 9f * dp, wsX + w * 0.12f, wsY + 9f * dp, paint);
        paint.setColor(Color.parseColor("#80DEEA")); canvas.drawRect(wsX - w * 0.18f, wsY - 5f * dp, wsX + w * 0.12f, wsY + 5f * dp, paint);
        paint.setColor(Color.parseColor("#B39DDB")); canvas.drawRect(wsX - w * 0.24f, wsY - 14f * dp, wsX - w * 0.16f, wsY + 14f * dp, paint);

        float slideProgress = (starPhase * 0.6f) % 1.0f, raftX = (wsX - w * 0.15f) + slideProgress * (w * 0.30f);
        paint.setColor(Color.parseColor("#FACC15")); canvas.drawOval(new RectF(raftX - 6f * dp, wsY - 4f * dp, raftX + 6f * dp, wsY + 4f * dp), paint);

        float bpX = cx + w * 0.66f, bpY = cy + h * 0.02f;
        RectF pondRect = new RectF(bpX - w * 0.22f, bpY - h * 0.14f, bpX + w * 0.22f, bpY + h * 0.14f);
        paint.setColor(Color.parseColor("#A3E4D7")); canvas.drawRoundRect(pondRect, 48f * dp, 48f * dp, paint);
        pondRect.inset(5f * dp, 5f * dp); paint.setColor(Color.parseColor("#76D7C4")); canvas.drawRoundRect(pondRect, 44f * dp, 44f * dp, paint);
        paint.setColor(Color.parseColor("#F28B9B")); canvas.drawRect(bpX - w * 0.15f, bpY - h * 0.15f, bpX - w * 0.07f, bpY - h * 0.07f, paint);

        for (int bIdx = 0; bIdx < 4; bIdx++) {
            float bx = bpX + (bIdx - 1.5f) * 26f * dp + (float) Math.sin(starPhase + bIdx) * 5f * dp, by = bpY + ((bIdx % 2 == 0) ? -12f : 16f) * dp;
            paint.setColor(Color.WHITE); canvas.drawOval(new RectF(bx - 5f * dp, by - 8f * dp, bx + 5f * dp, by + 8f * dp), paint);
            paint.setColor(coasterCols[bIdx]); canvas.drawOval(new RectF(bx - 3.5f * dp, by - 5f * dp, bx + 3.5f * dp, by + 5f * dp), paint);
        }

        float stX = cx - w * 0.68f, stY = cy + h * 0.56f;
        paint.setColor(Color.parseColor("#76D7C4")); canvas.drawRoundRect(new RectF(stX - w * 0.15f, stY - 18f * dp, stX + w * 0.15f, stY + 18f * dp), 20f * dp, 20f * dp, paint);
        paint.setColor(Color.parseColor("#D38D5F")); canvas.drawRect(stX - 14f * dp, stY - 26f * dp, stX + 14f * dp, stY + 26f * dp, paint);

        drawStripedTent(canvas, paint, cx - w * 0.34f, cy + h * 0.68f, 44f * dp, 12, Color.parseColor("#E05A5A"), Color.parseColor("#F4F6F7"));

        float tcX = cx - w * 0.45f, tcY = cy + h * 0.20f;
        for (int cup = 0; cup < 8; cup++) {
            double ca = starPhase * 1.5 + cup * (Math.PI / 4.0);
            float cupX = tcX + (float) Math.cos(ca) * 32f * dp, cupY = tcY + (float) Math.sin(ca) * 32f * dp;
            paint.setColor(coasterCols[cup % coasterCols.length]); canvas.drawCircle(cupX, cupY, 8.5f * dp, paint);
            paint.setColor(Color.WHITE); canvas.drawCircle(cupX, cupY, 4f * dp, paint);
        }
        drawStripedTent(canvas, paint, tcX, tcY, 22f * dp, 10, Color.parseColor("#F39C12"), Color.parseColor("#85C1E9"));

        float psX = cx + w * 0.72f, psY = cy + h * 0.66f;
        for (int ship = -1; ship <= 1; ship += 2) {
            float sy = psY + ship * 22f * dp, swing = (float) Math.sin(starPhase * 2.2f + ship) * 10f * dp;
            paint.setColor(Color.parseColor("#8D6E63")); canvas.drawOval(new RectF(psX - 28f * dp + swing, sy - 9f * dp, psX + 28f * dp + swing, sy + 9f * dp), paint);
            paint.setColor(Color.parseColor("#D7CCC8")); canvas.drawRect(psX - 16f * dp + swing, sy - 5f * dp, psX + 16f * dp + swing, sy + 5f * dp, paint);
        }
        paint.setColor(Color.parseColor("#6D4C41")); canvas.drawRect(psX - 4f * dp, psY - 38f * dp, psX + 4f * dp, psY + 38f * dp, paint);

        float[][] parkTrees = {
                { cx - w * 0.44f, cy - h * 0.34f, 32f * dp }, { cx - w * 0.62f, cy - h * 0.04f, 26f * dp },
                { cx + w * 0.20f, cy - h * 0.42f, 28f * dp }, { cx + w * 0.44f, cy - h * 0.20f, 26f * dp },
                { cx + w * 0.86f, cy - h * 0.38f, 28f * dp }, { cx + w * 0.84f, cy + h * 0.30f, 26f * dp },
                { cx - w * 0.04f, cy + h * 0.70f, 28f * dp }, { cx + w * 0.46f, cy + h * 0.68f, 26f * dp }
        };
        for (int i = 0; i < parkTrees.length; i++) {
            float ptx = parkTrees[i][0], pty = parkTrees[i][1], ptr = parkTrees[i][2];
            paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(ptx + 5f * dp, pty + 6f * dp, ptr, paint);
            paint.setColor((i % 2 == 0) ? Color.parseColor("#6AB04C") : Color.parseColor("#78E08F")); canvas.drawCircle(ptx, pty, ptr, paint);
            strokePaint.setColor(Color.parseColor("#44BD32")); strokePaint.setStrokeWidth(2.5f * dp);
            canvas.drawCircle(ptx, pty, ptr * 0.68f, strokePaint); canvas.drawCircle(ptx, pty, ptr * 0.36f, strokePaint);
        }

        canvas.restore();
        strokePaint.setColor(Color.parseColor("#C5E1A5")); strokePaint.setStrokeWidth(3f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 28f * dp, 28f * dp, strokePaint);
    }

    private void drawConicalRoof25D(Canvas canvas, Paint paint, float cx, float cy, float radius, int midCol, int lightCol, int darkCol) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(cx + 6f * dp, cy + 7f * dp, radius, paint);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setColor(midCol); canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(lightCol); canvas.drawArc(oval, 180f, 135f, true, paint);
        paint.setColor(darkCol); canvas.drawArc(oval, 0f, 120f, true, paint);
        paint.setColor(Color.parseColor("#FCE4EC")); canvas.drawCircle(cx, cy, 3f * dp, paint);
    }

    private void drawStripedTent(Canvas canvas, Paint paint, float cx, float cy, float radius, int wedges, int col1, int col2) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#33000000")); canvas.drawCircle(cx + 6f * dp, cy + 7f * dp, radius, paint);
        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        float sweep = 360f / wedges;
        for (int i = 0; i < wedges; i++) { paint.setColor((i % 2 == 0) ? col1 : col2); canvas.drawArc(oval, i * sweep, sweep + 0.5f, true, paint); }
        paint.setColor(Color.WHITE); canvas.drawCircle(cx, cy, radius * 0.14f, paint);
    }

    public void drawPickupsAndWaves(Canvas canvas, Paint paint, Paint strokePaint, float camX, float camY, float viewW, float viewH, float starPhase, int currentVehicleModel) {
        boolean isFlying = (currentVehicleModel == DriveOnCar.MODEL_HELICOPTER);

        for (OrbPickup orb : pickups) {
            if ((isFlying && orb.altitude == 0) || (!isFlying && orb.altitude == 1)) continue;
            float dx = getShortestDist(camX, orb.x), dy = getShortestDist(camY, orb.y);
            if (Math.abs(dx) > viewW || Math.abs(dy) > viewH) continue;
            float cx = camX + dx, cy = camY + dy;

            if (orb.kind == 2) {
                float fR = 8f * dp, fH = 16f * dp;
                paint.setColor(Color.parseColor("#44000000")); canvas.drawOval(new RectF(cx - fR, cy - fR / 2 + 8f * dp, cx + fR, cy + fR / 2 + 8f * dp), paint);
                paint.setColor(Color.parseColor("#B91C1C")); canvas.drawRect(cx - fR, cy - fH, cx + fR, cy, paint);
                canvas.drawOval(new RectF(cx - fR, cy - fR / 2, cx + fR, cy + fR / 2), paint);
                paint.setColor(Color.parseColor("#EF4444")); canvas.drawOval(new RectF(cx - fR, cy - fH - fR / 2, cx + fR, cy - fH + fR / 2), paint);
                paint.setColor(Color.parseColor("#FCA5A5")); canvas.drawRect(cx - 3f * dp, cy - fH / 2 - 4f * dp, cx + 3f * dp, cy - fH / 2 + 4f * dp, paint);
            } else {
                float radius = 10f * dp, thickness = 2.5f * dp, localPhase = starPhase * 3f + (orb.x % 100);
                float apparentWidth = radius * Math.abs((float) Math.cos(localPhase));
                int faceCol = (orb.kind == 1) ? Color.parseColor("#38BDF8") : Color.parseColor("#FACC15");
                int rimCol  = (orb.kind == 1) ? Color.parseColor("#0284C7") : Color.parseColor("#CA8A04");

                paint.setColor(Color.parseColor("#44000000")); canvas.drawOval(new RectF(cx - radius, cy - radius / 2 + 10f * dp, cx + radius, cy + radius / 2 + 10f * dp), paint);
                cy += (float) Math.sin(starPhase * 4f + (orb.y % 100)) * 4f * dp;

                paint.setColor(rimCol); tempRect.set(cx - apparentWidth, cy - radius + thickness, cx + apparentWidth, cy + radius + thickness); canvas.drawOval(tempRect, paint);
                paint.setColor(faceCol); tempRect.set(cx - apparentWidth, cy - radius, cx + apparentWidth, cy + radius); canvas.drawOval(tempRect, paint);
                paint.setColor(rimCol); tempRect.set(cx - apparentWidth * 0.6f, cy - radius * 0.6f, cx + apparentWidth * 0.6f, cy + radius * 0.6f); canvas.drawOval(tempRect, paint);
            }
        }

        for (SoundWave sw : hornWaves) {
            float dx = getShortestDist(camX, sw.x), dy = getShortestDist(camY, sw.y);
            if (Math.abs(dx) > viewW || Math.abs(dy) > viewH) continue;
            strokePaint.setColor(Color.parseColor("#38BDF8")); strokePaint.setAlpha(Math.max(0, sw.alpha)); strokePaint.setStrokeWidth(4f * dp);
            canvas.drawCircle(camX + dx, camY + dy, sw.radius, strokePaint);
        }
        strokePaint.setAlpha(255);
    }

    public void drawBuildingsAndTrees(Canvas canvas, Paint paint, Paint strokePaint, float camX, float camY, float viewW, float viewH, float starPhase) {
        paint.setStyle(Paint.Style.FILL);
        int bldgBase = isSunnyDay ? Color.parseColor("#64748B") : (isMoonlightMode ? Color.parseColor("#1E293B") : Color.parseColor("#0F172A"));

        for (Lake l : lakes) {
            float dx = getShortestDist(camX, l.cx), dy = getShortestDist(camY, l.cy);
            if (Math.abs(dx) > viewW + l.rx * 2f || Math.abs(dy) > viewH + l.ry * 2f) continue;
            drawDetailedLake25D(canvas, paint, strokePaint, l, camX + dx, camY + dy, starPhase);
        }

        for (Building b : buildings) {
            float dx = getShortestDist(camX, b.rect.centerX()), dy = getShortestDist(camY, b.rect.centerY());
            if (Math.abs(dx) > viewW + b.rect.width() || Math.abs(dy) > viewH + b.rect.height()) continue;
            float cx = camX + dx, cy = camY + dy, w = b.rect.width() / 2f, h = b.rect.height() / 2f;

            if (b.type == ZoneType.AMUSEMENT) { drawDetailedAmusementPark(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.FOREST) { drawDenseRainforest25D(canvas, paint, strokePaint, b, cx, cy, w, h); continue; }
            if (b.type == ZoneType.AIRPORT) { drawDetailedAirport25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.CONSTRUCTION) { drawDetailedConstructionSite25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.HOTEL_POOL) { drawDetailedHotelPoolEstate25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.CRICKET_STADIUM) { drawDetailedCricketStadium25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.FOOTBALL_STADIUM) { drawDetailedFootballStadium25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.ASTEROID_CRATER) { drawDetailedAsteroidCrater25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.VOLCANO) { drawDetailedVolcano25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.NUCLEAR) { drawDetailedNuclearPlant25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }
            if (b.type == ZoneType.HIMALAYA_MOUNTAINS) { drawDetailedHimalayaMountains25D(canvas, paint, strokePaint, cx, cy, w, h, starPhase); continue; }

            tempRect.set(cx - w, cy - h, cx + w, cy + h);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.parseColor("#55000000"));
            canvas.drawRoundRect(new RectF(tempRect.left + 12f * dp, tempRect.top + 14f * dp, tempRect.right + 12f * dp, tempRect.bottom + 14f * dp), 10f * dp, 10f * dp, paint);

            if (b.type == ZoneType.NORMAL) {
                int style = b.styleId % 8;
                paint.setColor(bldgBase); canvas.drawRoundRect(tempRect, 8f * dp, 8f * dp, paint);
                if (style == 0) {
                    paint.setColor(Color.parseColor("#94A3B8"));
                    canvas.drawRoundRect(new RectF(cx - w * 0.85f, cy - h * 0.85f, cx + w * 0.85f, cy - h * 0.2f), 6f * dp, 6f * dp, paint);
                    canvas.drawRoundRect(new RectF(cx - w * 0.85f, cy - h * 0.2f, cx - w * 0.2f, cy + h * 0.85f), 6f * dp, 6f * dp, paint);
                } else if (style == 1) {
                    paint.setColor(Color.parseColor("#94A3B8"));
                    canvas.drawRoundRect(new RectF(cx - w * 0.85f, cy - h * 0.85f, cx - w * 0.15f, cy + h * 0.85f), 6f * dp, 6f * dp, paint);
                    canvas.drawRoundRect(new RectF(cx + w * 0.15f, cy - h * 0.85f, cx + w * 0.85f, cy + h * 0.85f), 6f * dp, 6f * dp, paint);
                    paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRect(cx - w * 0.2f, cy - 12f * dp, cx + w * 0.2f, cy + 12f * dp, paint);
                } else if (style == 2) {
                    paint.setColor(Color.parseColor("#0284C7"));
                    canvas.drawRect(cx - w * 0.8f, cy - h * 0.8f, cx - w * 0.1f, cy - h * 0.2f, paint);
                    canvas.drawRect(cx + w * 0.1f, cy - h * 0.8f, cx + w * 0.8f, cy - h * 0.2f, paint);
                    paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRect(cx - 18f * dp, cy + h * 0.35f, cx + 18f * dp, cy + h * 0.7f, paint);
                } else if (style == 3) {
                    paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRoundRect(new RectF(cx - w * 0.82f, cy - h * 0.82f, cx + w * 0.82f, cy + h * 0.82f), 6f * dp, 6f * dp, paint);
                    paint.setColor(Color.parseColor("#15803D")); canvas.drawRoundRect(new RectF(cx - w * 0.38f, cy - h * 0.38f, cx + w * 0.38f, cy + h * 0.38f), 4f * dp, 4f * dp, paint);
                } else if (style == 4) {
                    paint.setColor(Color.parseColor("#94A3B8")); canvas.drawRoundRect(new RectF(cx - w * 0.75f, cy - h * 0.75f, cx + w * 0.75f, cy + h * 0.75f), 6f * dp, 6f * dp, paint);
                    paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRoundRect(new RectF(cx - w * 0.48f, cy - h * 0.48f, cx + w * 0.48f, cy + h * 0.48f), 5f * dp, 5f * dp, paint);
                    paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawCircle(cx, cy, w * 0.22f, paint);
                } else if (style == 5) {
                    paint.setColor(Color.parseColor("#D6D3D1"));
                    canvas.drawRect(cx - w * 0.8f, cy - h * 0.8f, cx - w * 0.3f, cy + h * 0.8f, paint);
                    canvas.drawRect(cx + w * 0.3f, cy - h * 0.8f, cx + w * 0.8f, cy + h * 0.8f, paint);
                    canvas.drawRect(cx - w * 0.8f, cy + h * 0.3f, cx + w * 0.8f, cy + h * 0.8f, paint);
                } else if (style == 6) {
                    paint.setColor(Color.parseColor("#38BDF8")); canvas.drawCircle(cx, cy, w * 0.72f, paint);
                    paint.setColor(Color.parseColor("#E0F2FE")); canvas.drawCircle(cx - w * 0.12f, cy - h * 0.12f, w * 0.38f, paint);
                } else {
                    paint.setColor(Color.parseColor("#78716C")); canvas.drawRoundRect(new RectF(cx - w * 0.85f, cy - h * 0.85f, cx + w * 0.85f, cy + h * 0.85f), 4f * dp, 4f * dp, paint);
                    paint.setColor(Color.parseColor("#BAE6FD"));
                    for (int k = -1; k <= 1; k++) canvas.drawRect(cx - w * 0.68f, cy + k * h * 0.42f - 5f * dp, cx + w * 0.68f, cy + k * h * 0.42f + 5f * dp, paint);
                }
            } else if (b.type == ZoneType.HOSPITAL) {
                paint.setColor(Color.parseColor("#55000000"));
                canvas.drawRoundRect(new RectF(cx - w * 0.4f + 12f * dp, cy - h + 14f * dp, cx + w * 0.4f + 12f * dp, cy + h + 14f * dp), 8f * dp, 8f * dp, paint);
                canvas.drawRoundRect(new RectF(cx - w + 12f * dp, cy - h * 0.4f + 14f * dp, cx + w + 12f * dp, cy + h * 0.4f + 14f * dp), 8f * dp, 8f * dp, paint);
                paint.setColor(Color.parseColor("#E2E8F0")); canvas.drawRoundRect(new RectF(cx - w * 0.4f, cy - h, cx + w * 0.4f, cy + h), 8f * dp, 8f * dp, paint);
                paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawRoundRect(new RectF(cx - w, cy - h * 0.4f, cx + w, cy + h * 0.4f), 8f * dp, 8f * dp, paint);
                paint.setColor(Color.parseColor("#334155")); canvas.drawCircle(cx, cy + h * 0.6f, 20f * dp, paint);
                strokePaint.setColor(Color.parseColor("#FACC15")); strokePaint.setStrokeWidth(2f * dp); canvas.drawCircle(cx, cy + h * 0.6f, 20f * dp, strokePaint);
                paint.setColor(Color.parseColor("#EF4444"));
                canvas.drawRect(cx - 6f * dp, cy - 20f * dp, cx + 6f * dp, cy + 20f * dp, paint);
                canvas.drawRect(cx - 20f * dp, cy - 6f * dp, cx + 20f * dp, cy + 6f * dp, paint);
                paint.setColor(Color.parseColor("#CBD5E1")); canvas.drawRoundRect(new RectF(cx - w * 0.8f, cy - h * 0.8f, cx - w * 0.4f, cy - h * 0.4f), 4f * dp, 4f * dp, paint);
            } else if (b.type == ZoneType.POLICE) {
                paint.setColor(Color.parseColor("#1D4ED8")); canvas.drawRoundRect(tempRect, 8f * dp, 8f * dp, paint);
                paint.setColor(Color.parseColor("#F8FAFC")); canvas.drawRoundRect(new RectF(cx - w * 0.6f, cy - h * 0.6f, cx + w * 0.6f, cy + h * 0.6f), 4f * dp, 4f * dp, paint);
                for (int i = 0; i < 3; i++) {
                    paint.setColor(Color.parseColor("#0F172A")); canvas.drawRect(cx - w * 0.8f, cy + h * 0.2f + i * 15f * dp, cx - w * 0.4f, cy + h * 0.2f + i * 15f * dp + 8f * dp, paint);
                    paint.setColor(Color.parseColor("#EF4444")); canvas.drawCircle(cx - w * 0.6f, cy + h * 0.2f + i * 15f * dp + 4f * dp, 2f * dp, paint);
                }
            } else if (b.type == ZoneType.NUCLEAR) {
                paint.setColor(Color.parseColor("#334155")); canvas.drawRoundRect(tempRect, 16f * dp, 16f * dp, paint);
                paint.setColor(Color.parseColor("#94A3B8")); canvas.drawCircle(cx + w * 0.3f, cy - h * 0.3f, 35f * dp, paint);
                paint.setColor(Color.parseColor("#0F172A")); canvas.drawCircle(cx + w * 0.3f, cy - h * 0.3f, 22f * dp, paint);
                paint.setColor(Color.parseColor("#94A3B8")); canvas.drawCircle(cx - w * 0.4f, cy + h * 0.2f, 35f * dp, paint);
                paint.setColor(Color.parseColor("#0F172A")); canvas.drawCircle(cx - w * 0.4f, cy + h * 0.2f, 22f * dp, paint);
            }

            strokePaint.setColor(Color.parseColor("#0D1B2A")); strokePaint.setStrokeWidth(2.4f * dp);
            canvas.drawRoundRect(tempRect, 10f * dp, 10f * dp, strokePaint);
        }

        for (TreeObstacle t : trees) {
            float dx = getShortestDist(camX, t.x), dy = getShortestDist(camY, t.y);
            if (Math.abs(dx) > viewW + t.radius || Math.abs(dy) > viewH + t.radius) continue;
            float cx = camX + dx, cy = camY + dy, r = t.radius;

            paint.setColor(Color.parseColor("#44000000")); canvas.drawCircle(cx + 6f * dp, cy + 7f * dp, r * 1.05f, paint);
            int darkLeaf = !isSunnyDay ? Color.parseColor("#065F46") : Color.parseColor("#15803D");
            int midLeaf  = !isSunnyDay ? Color.parseColor("#059669") : Color.parseColor("#16A34A");
            int sunLeaf  = !isSunnyDay ? Color.parseColor("#34D399") : Color.parseColor("#4ADE80");

            paint.setColor(darkLeaf); canvas.drawCircle(cx, cy, r, paint);
            canvas.drawCircle(cx - r * 0.48f, cy - r * 0.32f, r * 0.68f, paint); canvas.drawCircle(cx + r * 0.48f, cy - r * 0.28f, r * 0.65f, paint);
            canvas.drawCircle(cx - r * 0.38f, cy + r * 0.45f, r * 0.66f, paint); canvas.drawCircle(cx + r * 0.42f, cy + r * 0.42f, r * 0.64f, paint);
            paint.setColor(midLeaf); canvas.drawCircle(cx - r * 0.12f, cy - r * 0.12f, r * 0.72f, paint);
            paint.setColor(sunLeaf); canvas.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.42f, paint); canvas.drawCircle(cx + r * 0.22f, cy - r * 0.20f, r * 0.28f, paint);
        }
    }

    private void drawAmbientStars(Canvas canvas, Paint paint, float camX, float camY, float viewW, float viewH, float starPhase) {
        paint.setStyle(Paint.Style.FILL);
        for (AmbientStar s : stars) {
            float dx = getShortestDist(camX, s.x), dy = getShortestDist(camY, s.y);
            if (Math.abs(dx) > viewW || Math.abs(dy) > viewH) continue;
            paint.setColor(s.color); paint.setAlpha(110 + (int) (140 * (0.5f + 0.5f * Math.sin(starPhase + s.phase))));
            canvas.drawCircle(camX + dx, camY + dy, s.radius, paint);
        }
        paint.setAlpha(255);
    }

    public static class CanopyCrown {
        public final float ox, oy, radius;
        public final int shadowCol, midCol, highlightCol;
        public final float[] puffDx = new float[6], puffDy = new float[6], puffR = new float[6];

        public CanopyCrown(float ox, float oy, float radius, int shadowCol, int midCol, int highlightCol, Random rng) {
            this.ox = ox; this.oy = oy; this.radius = radius;
            this.shadowCol = shadowCol; this.midCol = midCol; this.highlightCol = highlightCol;
            for (int p = 0; p < 6; p++) {
                double a = p * (Math.PI / 3.0) + (rng.nextDouble() - 0.5) * 0.45;
                float dist = radius * (0.48f + rng.nextFloat() * 0.22f);
                puffDx[p] = (float) Math.cos(a) * dist; puffDy[p] = (float) Math.sin(a) * dist;
                puffR[p]  = radius * (0.52f + rng.nextFloat() * 0.18f);
            }
        }
    }

    public static class Building {
        public final RectF rect;
        public final ZoneType type;
        public final int styleId;
        public final List<CanopyCrown> forestCrowns;

        public Building(float x, float y, float w, float h, ZoneType type, int styleId, float dp) {
            this.rect = new RectF(x, y, x + w, y + h); this.type = type; this.styleId = styleId;
            if (type == ZoneType.FOREST) {
                this.forestCrowns = new ArrayList<>(90);
                Random rng = new Random((long) (x * 31 + y * 17));
                float halfW = w * 0.5f, halfH = h * 0.5f;
                int[][] palettes = {
                        { Color.parseColor("#064E3B"), Color.parseColor("#15803D"), Color.parseColor("#4ADE80") },
                        { Color.parseColor("#14532D"), Color.parseColor("#16A34A"), Color.parseColor("#86EFAC") },
                        { Color.parseColor("#1A2E05"), Color.parseColor("#3F6212"), Color.parseColor("#84CC16") },
                        { Color.parseColor("#365314"), Color.parseColor("#65A30D"), Color.parseColor("#BEF264") },
                        { Color.parseColor("#052E16"), Color.parseColor("#166534"), Color.parseColor("#22C55E") },
                        { Color.parseColor("#14532D"), Color.parseColor("#4D7C0F"), Color.parseColor("#A3E635") },
                        { Color.parseColor("#065F46"), Color.parseColor("#059669"), Color.parseColor("#6EE7B7") }
                };
                int rows = 9, cols = 9; float cellW = (w * 0.94f) / cols, cellH = (h * 0.94f) / rows;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        float ox = -halfW * 0.94f + (c + 0.5f) * cellW + (rng.nextFloat() - 0.5f) * cellW * 0.65f;
                        float oy = -halfH * 0.94f + (r + 0.5f) * cellH + (rng.nextFloat() - 0.5f) * cellH * 0.65f;
                        if (oy > halfH * 0.52f && Math.abs(ox) < halfW * 0.24f) continue;
                        float rad = (34f + rng.nextFloat() * 24f) * dp;
                        int[] pal = palettes[rng.nextInt(palettes.length)];
                        forestCrowns.add(new CanopyCrown(ox, oy, rad, pal[0], pal[1], pal[2], rng));
                    }
                }
                for (int e = 0; e < 14; e++) {
                    float ox = (rng.nextFloat() - 0.5f) * w * 0.78f, oy = (rng.nextFloat() - 0.5f) * h * 0.72f;
                    float rad = (44f + rng.nextFloat() * 18f) * dp;
                    int[] pal = palettes[(e % 2 == 0) ? 3 : 5];
                    forestCrowns.add(new CanopyCrown(ox, oy, rad, pal[0], pal[1], pal[2], rng));
                }
            } else this.forestCrowns = null;
        }
    }




    /**
     * Renders the Ultra-Detailed 15x-Area (4x4 Super-Block) 2.5D Great Himalayan Mountain Range:
     * - Deep sub-alpine pine valleys, stepped ochre/granite foothills & 12 avalanche-buried road stubs
     * - Braided glacial meltwater rivers, 3 turquoise moraine tarns & crevassed valley glaciers
     * - Winding high-altitude mountain pass highway (Khardung La style) with hairpin turns, suspension bridge & trucks
     * - 5 interconnected serrated mountain massif chains + 48 multi-tiered craggy 2.5D peaks (36-vertex jagged contours,
     *   sunlit white NW snow faces, deep cobalt/indigo SE shadow faces, exposed ochre/slate rock strata & couloirs)
     * - 320+ snow-dusted Himalayan Deodar pine trees, cliffside monastery, Base Camp, foreground cairn ledge & clouds.
     */
    private void drawDetailedHimalayaMountains25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int valleyForestCol = isSunnyDay ? Color.parseColor("#1D382B") : (isMoonlightMode ? Color.parseColor("#0B1B15") : Color.parseColor("#06100C"));
        int lowerTurfCol    = isSunnyDay ? Color.parseColor("#2E523D") : (isMoonlightMode ? Color.parseColor("#12291F") : Color.parseColor("#0A1812"));
        int graniteDarkCol  = isSunnyDay ? Color.parseColor("#3B332E") : Color.parseColor("#1A1614");
        int foothillBrown   = isSunnyDay ? Color.parseColor("#6E503B") : Color.parseColor("#2E2118");
        int foothillOchre   = isSunnyDay ? Color.parseColor("#BA8556") : Color.parseColor("#4D3624");
        int slateShadowCol  = isSunnyDay ? Color.parseColor("#193252") : Color.parseColor("#0D1B2E");
        int cobaltShadowCol = isSunnyDay ? Color.parseColor("#264E82") : Color.parseColor("#152C4A");
        int iceBlueMidCol   = isSunnyDay ? Color.parseColor("#6CA2DE") : Color.parseColor("#386696");
        int snowPowderBlue  = isSunnyDay ? Color.parseColor("#C2DCF5") : Color.parseColor("#6E92B8");
        int snowPureWhite   = isSunnyDay ? Color.parseColor("#FFFFFF") : Color.parseColor("#E2ECF7");

        // =========================================================================
        // 1. 4x4 MEGA-ZONE ALPINE VALLEY BASE, STEPPED FOOTHILLS & 12 ROAD STUBS
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#66000000"));
        canvas.drawRoundRect(new RectF(cx - w + 16f * dp, cy - h + 18f * dp, cx + w + 16f * dp, cy + h + 18f * dp), 44f * dp, 44f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(valleyForestCol);
        canvas.drawRoundRect(tempRect, 44f * dp, 44f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 44f * dp, 44f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Stepped Topographic Elevation Contours (Sub-Alpine Forest -> Granite -> Ochre Foothills -> Snow Line)
        buildOrganicFloorContourPath(tempPath, cx, cy, w * 0.96f, h * 0.96f, 0.5f);
        paint.setColor(lowerTurfCol);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, cx - w * 0.03f, cy + h * 0.03f, w * 0.90f, h * 0.89f, 1.4f);
        paint.setColor(graniteDarkCol);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, cx - w * 0.04f, cy + h * 0.02f, w * 0.84f, h * 0.83f, 2.3f);
        paint.setColor(foothillBrown);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, cx - w * 0.02f, cy - h * 0.02f, w * 0.76f, h * 0.75f, 3.2f);
        paint.setColor(foothillOchre);
        canvas.drawPath(tempPath, paint);

        // High-Altitude Snow & Glacial Ice Massif Mantle Across the Central/Northern Range
        buildOrganicFloorContourPath(tempPath, cx + w * 0.02f, cy - h * 0.06f, w * 0.68f, h * 0.64f, 4.1f);
        paint.setColor(slateShadowCol);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, cx + w * 0.01f, cy - h * 0.08f, w * 0.60f, h * 0.56f, 5.0f);
        paint.setColor(cobaltShadowCol);
        canvas.drawPath(tempPath, paint);

        // 12 Avalanche-Buried City Road Stubs at the 4x4 Grid Intersections (-gridStep, 0, +gridStep)
        float[] roadOffsets = { -gridStep, 0f, gridStep };
        float stubW = 140f * dp;
        for (float ro : roadOffsets) {
            drawAvalancheRoadStub(canvas, paint, strokePaint, cx + ro, cy - h, stubW, 145f * dp, 0f, starPhase);
            drawAvalancheRoadStub(canvas, paint, strokePaint, cx + ro, cy + h, stubW, 145f * dp, 180f, starPhase);
            drawAvalancheRoadStub(canvas, paint, strokePaint, cx - w, cy + ro, stubW, 145f * dp, 270f, starPhase);
            drawAvalancheRoadStub(canvas, paint, strokePaint, cx + w, cy + ro, stubW, 145f * dp, 90f, starPhase);
        }

        // =========================================================================
        // 2. CREVASSED VALLEY GLACIERS, BRAIDED RIVERS & TURQUOISE GLACIAL LAKES
        // =========================================================================
        // 3 Major Crevassed Valley Glaciers Winding Down from the High Massif
        drawGlacierStream25D(canvas, paint, strokePaint,
                cx - w * 0.08f, cy - h * 0.22f,
                cx - w * 0.26f, cy + h * 0.18f,
                cx - w * 0.18f, cy + h * 0.62f, 56f * dp);

        drawGlacierStream25D(canvas, paint, strokePaint,
                cx + w * 0.16f, cy - h * 0.18f,
                cx + w * 0.42f, cy + h * 0.16f,
                cx + w * 0.58f, cy + h * 0.56f, 48f * dp);

        drawGlacierStream25D(canvas, paint, strokePaint,
                cx - w * 0.24f, cy - h * 0.36f,
                cx - w * 0.54f, cy - h * 0.48f,
                cx - w * 0.78f, cy - h * 0.68f, 42f * dp);

        // Braided Glacial Meltwater Rivers Flowing Out of the Glacier Termini to the Valley Edges
        drawAlpineMeltwaterRiver(canvas, strokePaint,
                cx - w * 0.18f, cy + h * 0.60f,
                cx - w * 0.10f, cy + h * 0.80f,
                cx - w * 0.22f, cy + h, starPhase);

        drawAlpineMeltwaterRiver(canvas, strokePaint,
                cx + w * 0.56f, cy + h * 0.54f,
                cx + w * 0.74f, cy + h * 0.72f,
                cx + w * 0.96f, cy + h * 0.82f, starPhase + 1.5f);

        // 3 Vivid Turquoise Alpine Glacial Lakes (Gokyo / Pangong Style) with Ice Floes
        float[][] glacialLakes = {
                { -0.18f,  0.60f, 76f, 48f, 0.8f },
                {  0.56f,  0.54f, 64f, 42f, 2.1f },
                { -0.62f, -0.46f, 58f, 38f, 3.6f }
        };
        for (float[] gl : glacialLakes) {
            float lx = cx + gl[0] * w, ly = cy + gl[1] * h;
            float lrx = gl[2] * dp, lry = gl[3] * dp;
            buildOrganicFloorContourPath(tempPath, lx, ly, lrx * 1.22f, lry * 1.22f, gl[4]);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(snowPureWhite);
            canvas.drawPath(tempPath, paint);

            buildOrganicFloorContourPath(tempPath, lx, ly, lrx, lry, gl[4]);
            paint.setColor(Color.parseColor("#0369A1"));
            canvas.drawPath(tempPath, paint);

            buildOrganicFloorContourPath(tempPath, lx - 4f * dp, ly - 3f * dp, lrx * 0.74f, lry * 0.74f, gl[4] + 0.6f);
            paint.setColor(Color.parseColor("#00B0FF"));
            canvas.drawPath(tempPath, paint);

            buildOrganicFloorContourPath(tempPath, lx - 6f * dp, ly - 5f * dp, lrx * 0.42f, lry * 0.42f, gl[4] + 1.2f);
            paint.setColor(Color.parseColor("#70E1FF"));
            canvas.drawPath(tempPath, paint);
        }

        // =========================================================================
        // 3. WINDING HIMALAYAN SWITCHBACK PASS ROAD (KHARDUNG LA) & SUSPENSION BRIDGE
        // =========================================================================
        drawHimalayanMountainPassRoad(canvas, paint, strokePaint, cx, cy, w, h, starPhase);

        // =========================================================================
        // 4. 5 INTERCONNECTED SERRATED MOUNTAIN RIDGES & 48 MULTI-TIERED 2.5D PEAKS
        //    (Ordered North-to-South & Outer-to-Inner using Painter's Algorithm!)
        // =========================================================================
        // A. Continuous 3D Faceted Ridge Chains (Connecting Saddles/Cols Between Peaks)
        float[][][] ridgeChains = {
                // Northern High Barrier Ridge
                { { -0.78f, -0.66f }, { -0.52f, -0.74f }, { -0.22f, -0.78f }, {  0.08f, -0.76f }, {  0.42f, -0.72f }, {  0.74f, -0.62f } },
                // Upper-Mid Greater Himalaya Spine
                { { -0.82f, -0.28f }, { -0.56f, -0.44f }, { -0.26f, -0.50f }, {  0.04f, -0.48f }, {  0.36f, -0.44f }, {  0.66f, -0.32f }, {  0.84f, -0.18f } },
                // Central Main Massif Spine (Everest — Lhotse — Nuptse — Makalu Arête)
                { { -0.76f,  0.12f }, { -0.48f, -0.12f }, { -0.22f, -0.22f }, { -0.02f, -0.28f }, {  0.22f, -0.20f }, {  0.48f, -0.08f }, {  0.78f,  0.14f } },
                // Lower-Mid Alpine Ridge
                { { -0.68f,  0.42f }, { -0.38f,  0.22f }, { -0.08f,  0.12f }, {  0.20f,  0.18f }, {  0.48f,  0.28f }, {  0.76f,  0.48f } },
                // Southern Lesser Himalaya Foothill Ridge
                { { -0.42f,  0.76f }, { -0.04f,  0.68f }, {  0.32f,  0.74f }, {  0.68f,  0.72f } }
        };
        for (int rc = 0; rc < ridgeChains.length; rc++) {
            boolean heavySnowChain = (rc >= 1 && rc <= 3);
            drawHimalayanMassifChain25D(canvas, paint, strokePaint, cx, cy, w, h, ridgeChains[rc],
                    heavySnowChain ? (95f * dp) : (72f * dp), heavySnowChain,
                    graniteDarkCol, foothillOchre, slateShadowCol, cobaltShadowCol, iceBlueMidCol, snowPureWhite);
        }

        // B. 22 Lower-Elevation Perimeter & Foothill Peaks (Ochre/Slate Rock Cliffs + Partial Snow Caps, Image 2!)
        // { relX, relY, radiusInDp, snowRatio (0.25..0.55), seed }
        float[][] foothillPeaks = {
                { -0.84f, -0.74f, 165f, 0.45f, 0.4f }, { -0.58f, -0.82f, 175f, 0.50f, 1.1f },
                { -0.24f, -0.84f, 180f, 0.55f, 1.8f }, {  0.12f, -0.84f, 175f, 0.55f, 2.5f },
                {  0.48f, -0.80f, 170f, 0.50f, 3.2f }, {  0.80f, -0.72f, 165f, 0.45f, 3.9f },
                { -0.86f, -0.42f, 185f, 0.40f, 4.6f }, { -0.86f, -0.06f, 190f, 0.35f, 5.3f },
                { -0.82f,  0.28f, 185f, 0.32f, 6.0f }, { -0.68f,  0.46f, 175f, 0.35f, 0.8f },
                {  0.86f, -0.38f, 180f, 0.42f, 1.5f }, {  0.86f,  0.02f, 190f, 0.38f, 2.2f },
                {  0.82f,  0.36f, 185f, 0.35f, 2.9f }, {  0.74f,  0.66f, 175f, 0.32f, 3.6f },
                {  0.44f,  0.78f, 170f, 0.30f, 4.3f }, {  0.14f,  0.76f, 165f, 0.32f, 5.0f },
                { -0.36f,  0.78f, 165f, 0.30f, 5.7f }, { -0.42f,  0.48f, 170f, 0.38f, 6.4f }
        };
        for (float[] fp : foothillPeaks) {
            drawRealisticAlpinePeak25D(canvas, paint, strokePaint,
                    cx + fp[0] * w, cy + fp[1] * h, fp[2] * dp, fp[3], fp[4],
                    graniteDarkCol, foothillBrown, foothillOchre, slateShadowCol, cobaltShadowCol, iceBlueMidCol, snowPowderBlue, snowPureWhite);
        }

        // C. 26 High-Altitude Heavy-Snow Himalayan Peaks & 8,000m Summits (Dense Sea of Jagged White/Blue Peaks, Image 1!)
        float[][] highSnowPeaks = {
                // Row 1: Northern High Snow Peaks
                { -0.64f, -0.58f, 210f, 0.82f, 0.5f }, { -0.38f, -0.66f, 225f, 0.86f, 1.3f },
                { -0.10f, -0.68f, 235f, 0.88f, 2.1f }, {  0.20f, -0.66f, 230f, 0.86f, 2.9f },
                {  0.48f, -0.60f, 215f, 0.84f, 3.7f }, {  0.68f, -0.48f, 200f, 0.78f, 4.5f },
                // Row 2: Upper-Mid Serrated Massif
                { -0.62f, -0.26f, 220f, 0.84f, 5.3f }, { -0.42f, -0.38f, 245f, 0.90f, 6.1f },
                { -0.18f, -0.44f, 265f, 0.94f, 0.9f }, {  0.10f, -0.44f, 260f, 0.92f, 1.7f },
                {  0.36f, -0.38f, 245f, 0.90f, 2.5f }, {  0.60f, -0.24f, 220f, 0.82f, 3.3f },
                // Row 3: Central Core 8,000m Giants (Everest / K2 / Kangchenjunga / Lhotse / Makalu)
                { -0.52f,  0.02f, 230f, 0.85f, 4.1f }, { -0.28f, -0.14f, 280f, 0.95f, 4.9f },
                {  0.24f, -0.14f, 275f, 0.94f, 5.7f }, {  0.50f,  0.02f, 235f, 0.85f, 6.5f },
                // Row 4: Lower-Mid Alpine Snow Peaks
                { -0.54f,  0.26f, 205f, 0.74f, 1.2f }, { -0.24f,  0.16f, 240f, 0.88f, 2.0f },
                {  0.02f,  0.10f, 250f, 0.90f, 2.8f }, {  0.28f,  0.18f, 235f, 0.86f, 3.6f },
                {  0.52f,  0.28f, 205f, 0.75f, 4.4f }, {  0.32f,  0.44f, 195f, 0.68f, 5.2f },
                // Crown Jewel: Mount Everest / Sagarmatha Central Towering Summit Pyramid!
                { -0.02f, -0.24f, 330f, 0.98f, 0.2f }
        };
        for (float[] sp : highSnowPeaks) {
            drawRealisticAlpinePeak25D(canvas, paint, strokePaint,
                    cx + sp[0] * w, cy + sp[1] * h, sp[2] * dp, sp[3], sp[4],
                    graniteDarkCol, foothillBrown, foothillOchre, slateShadowCol, cobaltShadowCol, iceBlueMidCol, snowPowderBlue, snowPureWhite);
        }

        // =========================================================================
        // 5. 320+ SNOW-CAPPED DEODAR PINE TREES & CRAGGY BOULDER FIELDS IN VALLEYS
        //    (Gives immediate camera-scale realism when driving or flying low!)
        // =========================================================================
        for (int tr = 0; tr < 320; tr++) {
            double tang = tr * 2.39996;
            // Distribute trees in the lower valleys, gorges, and outer timberline rings
            float ring = 0.56f + (tr % 14) * 0.031f;
            float tx = cx + (float) Math.cos(tang) * (w * ring);
            float ty = cy + (float) Math.sin(tang) * (h * ring);
            float treeR = (6.5f + (tr % 4) * 2.4f) * dp;
            boolean heavySnowTree = (ring < 0.74f);
            drawSnowPineTree25D(canvas, paint, tx, ty, treeR, heavySnowTree);
        }

        // Valley & Moraine Craggy Rock Boulders
        for (int b = 0; b < 85; b++) {
            double bang = b * 1.913;
            float bring = 0.42f + (b % 11) * 0.048f;
            float bx = cx + (float) Math.cos(bang) * (w * bring);
            float by = cy + (float) Math.sin(bang) * (h * bring);
            float bRad = (4.5f + (b % 5) * 2.5f) * dp;
            drawCraggyRock25D(canvas, paint, bx, by, bRad, graniteDarkCol,
                    (bring < 0.65f) ? snowPowderBlue : foothillOchre, b * 1.3f);
        }

        // =========================================================================
        // 6. FOREGROUND ROCKY PROMONTORY LEDGE & STACKED STONE CAIRNS (IMAGE 2!)
        // =========================================================================
        drawForegroundCairnPromontory25D(canvas, paint, strokePaint,
                cx - w * 0.56f, cy + h * 0.68f, 195f * dp, 145f * dp);

        // =========================================================================
        // 7. CLIFFSIDE HIMALAYAN MONASTERY (GOMPA), BASE CAMP & ROPED CLIMBERS
        // =========================================================================
        // Traditional Tibetan/Ladakhi Cliffside Monastery on the South-East Spur
        float monX = cx + w * 0.28f, monY = cy + h * 0.58f;
        drawCraggyRock25D(canvas, paint, monX, monY, 48f * dp, graniteDarkCol, foothillOchre, 2.2f);
        drawIndustrialBox25D(canvas, paint, strokePaint, monX, monY - 6f * dp, 22f * dp, 14f * dp,
                Color.parseColor("#991B1B"), Color.parseColor("#F8FAFC"), true);
        drawIndustrialBox25D(canvas, paint, strokePaint, monX, monY - 10f * dp, 13f * dp, 8f * dp,
                Color.parseColor("#B91C1C"), Color.parseColor("#FACC15"), false);
        // White Buddhist Chorten Stupa beside Monastery
        drawStoneCairn25D(canvas, paint, monX - 28f * dp, monY + 4f * dp, 1.15f);

        // Everest / Khumbu Expedition Base Camp in the Southern Glacial Valley
        float bcX = cx - w * 0.06f, bcY = cy + h * 0.42f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#475569"));
        canvas.drawOval(new RectF(bcX - 62f * dp, bcY - 36f * dp, bcX + 62f * dp, bcY + 36f * dp), paint);
        paint.setColor(snowPureWhite);
        canvas.drawOval(new RectF(bcX - 52f * dp, bcY - 28f * dp, bcX + 52f * dp, bcY + 28f * dp), paint);

        // Fluttering 5-Color Tibetan Prayer Flag Lines
        int[] flagCols = {
                Color.parseColor("#2563EB"), Color.WHITE, Color.parseColor("#DC2626"),
                Color.parseColor("#16A34A"), Color.parseColor("#FACC15")
        };
        float[][] flagSpans = {
                { bcX - 46f * dp, bcY - 18f * dp, bcX + 42f * dp, bcY - 12f * dp },
                { bcX - 42f * dp, bcY - 8f * dp,  bcX + 46f * dp, bcY + 14f * dp }
        };
        for (float[] fs : flagSpans) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.parseColor("#334155"));
            strokePaint.setStrokeWidth(1.2f * dp);
            canvas.drawLine(fs[0], fs[1], fs[2], fs[3], strokePaint);
            paint.setStyle(Paint.Style.FILL);
            for (int f = 0; f < 14; f++) {
                float t = f / 13f;
                float fx = fs[0] + (fs[2] - fs[0]) * t;
                float fy = fs[1] + (fs[3] - fs[1]) * t + (float) Math.sin(starPhase * 6f + f) * 2.2f * dp;
                paint.setColor(flagCols[f % flagCols.length]);
                canvas.drawRect(fx - 2.2f * dp, fy, fx + 2.2f * dp, fy + 3.5f * dp, paint);
            }
        }

        // 10 High-Altitude Dome Tents
        float[][] tents = {
                { -34f, -8f }, { -18f, -14f }, { -2f, -8f }, { 14f, -12f }, { 28f, -6f },
                { -30f,  8f }, { -12f,  12f }, {  6f,  8f }, { 22f,  10f }, { 36f,  4f }
        };
        for (int t = 0; t < tents.length; t++) {
            float tx = bcX + tents[t][0] * dp, ty = bcY + tents[t][1] * dp;
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawCircle(tx + 2f * dp, ty + 2.5f * dp, 6f * dp, paint);
            paint.setColor((t % 3 == 0) ? Color.parseColor("#F97316") : ((t % 2 == 0) ? Color.parseColor("#FACC15") : Color.parseColor("#0284C7")));
            canvas.drawCircle(tx, ty, 6f * dp, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(tx - 1.5f * dp, ty - 1.5f * dp, 2.4f * dp, paint);
        }

        // =========================================================================
        // 8. DRIFTING OROGRAPHIC CLOUD BANKS, SPINDRIFT PLUMES & RESCUE HELICOPTER
        // =========================================================================
        // Soft Translucent Cloud Banks Drifting Across the Mid-Slopes (Image 1 horizon mist)
        paint.setStyle(Paint.Style.FILL);
        float[][] cloudBanks = {
                { -0.45f, -0.32f, 110f, 42f, 0.0f }, {  0.38f, -0.36f, 125f, 46f, 1.5f },
                { -0.22f,  0.04f, 115f, 40f, 3.1f }, {  0.46f,  0.12f, 105f, 38f, 4.7f },
                { -0.52f, -0.62f, 120f, 44f, 2.3f }, {  0.22f, -0.64f, 130f, 45f, 5.4f }
        };
        for (float[] cb : cloudBanks) {
            float drift = (float) Math.sin(starPhase * 0.25f + cb[4]) * (42f * dp);
            float clX = cx + cb[0] * w + drift;
            float clY = cy + cb[1] * h;
            float cw = cb[2] * dp, ch = cb[3] * dp;
            paint.setColor(Color.argb(isSunnyDay ? 68 : 42, 240, 249, 255));
            canvas.drawOval(new RectF(clX - cw, clY - ch, clX + cw, clY + ch), paint);
            paint.setColor(Color.argb(isSunnyDay ? 95 : 55, 255, 255, 255));
            canvas.drawOval(new RectF(clX - cw * 0.65f, clY - ch * 0.70f, clX + cw * 0.55f, clY + ch * 0.55f), paint);
        }

        // Jet-Stream Summit Snow Banners (Spindrift) Blowing Eastward off the Highest Peaks
        for (int spIdx = 12; spIdx < highSnowPeaks.length; spIdx += 2) {
            float sx = cx + highSnowPeaks[spIdx][0] * w;
            float sy = cy + highSnowPeaks[spIdx][1] * h - highSnowPeaks[spIdx][2] * dp * 0.16f;
            for (int p = 0; p < 7; p++) {
                float prog = ((starPhase * 0.38f) + (p / 7.0f) + spIdx * 0.13f) % 1.0f;
                float px = sx + prog * (75f * dp);
                float py = sy - prog * (18f * dp) + (float) Math.sin(prog * 6f + spIdx) * (5f * dp);
                float rad = (6f + prog * 24f) * dp;
                int alpha = Math.max(0, Math.round((1f - prog) * 175f));
                paint.setColor(Color.argb(alpha, 248, 250, 252));
                canvas.drawOval(new RectF(px - rad * 1.5f, py - rad * 0.6f, px + rad * 1.5f, py + rad * 0.6f), paint);
            }
        }
        paint.setAlpha(255);

        // Orbiting Red-and-White Alpine Rescue Helicopter
        float heliAng = starPhase * 0.20f;
        float heliX = cx + (float) Math.cos(heliAng) * (w * 0.38f);
        float heliY = cy - h * 0.10f + (float) Math.sin(heliAng) * (h * 0.30f);
        canvas.save();
        canvas.translate(heliX, heliY);
        canvas.rotate((float) Math.toDegrees(heliAng) + 90f);
        paint.setColor(Color.parseColor("#38000000"));
        canvas.drawOval(new RectF(18f * dp, 22f * dp, 42f * dp, 32f * dp), paint);
        paint.setColor(Color.parseColor("#DC2626"));
        canvas.drawRect(-18f * dp, -2f * dp, -2f * dp, 2f * dp, paint);
        canvas.drawOval(new RectF(-7f * dp, -5.5f * dp, 12f * dp, 5.5f * dp), paint);
        paint.setColor(Color.WHITE);
        canvas.drawOval(new RectF(-4f * dp, -3.8f * dp, 8f * dp, 3.8f * dp), paint);
        paint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawOval(new RectF(3f * dp, -4.0f * dp, 11f * dp, 4.0f * dp), paint);
        canvas.rotate(starPhase * 30f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1E293B"));
        strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawLine(-20f * dp, -20f * dp, 20f * dp, 20f * dp, strokePaint);
        canvas.drawLine(-20f * dp, 20f * dp, 20f * dp, -20f * dp, strokePaint);
        canvas.restore();

        canvas.restore(); // End 4x4 Mega-Zone clip

        // Crisp Alpine Slate-Blue Border Around the 4x4 Himalayan Mega-Zone
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1E3A5F"));
        strokePaint.setStrokeWidth(3.8f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 44f * dp, 44f * dp, strokePaint);
    }

    /**
     * Renders a high-resolution, multi-tiered 2.5D Craggy Himalayan Peak (NO giant flat triangles!).
     * Uses 5 concentric 36-vertex jagged topographic contours + 12 zig-zagging 3D faceted arête spurs,
     * sunlit NW snow slopes, deep cobalt/indigo SE shadow faces, exposed ochre/slate rock cliffs & couloirs.
     */
    private void drawRealisticAlpinePeak25D(Canvas canvas, Paint paint, Paint strokePaint,
                                            float baseX, float baseY, float baseR, float snowRatio, float seed,
                                            int graniteDark, int brownRock, int ochreRock,
                                            int slateShadow, int cobaltShadow, int iceBlue, int powderBlue, int pureSnow) {
        // 2.5D Oblique Summit Offset (shifts the summit North-West so the mountain towers upward in 2.5D)
        float apexX = baseX - baseR * 0.14f;
        float apexY = baseY - baseR * 0.22f;

        // 1. Deep Cobalt-Indigo 2.5D Valley Cast Shadow to the Bottom-Right (Image 1 & 2 shadow!)
        buildCraggyPeakContour(tempPath, baseX + baseR * 0.16f, baseY + baseR * 0.18f, baseR * 0.98f, seed);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#48071120"));
        canvas.drawPath(tempPath, paint);

        // 2. Five Stacked 36-Vertex Jagged Topographic Elevation Tiers (Rock Base -> Cliff -> Ice -> Snow Crown)
        float[] tierScales = { 1.00f, 0.82f, 0.65f, 0.48f, 0.30f };
        for (int t = 0; t < tierScales.length; t++) {
            float sc = tierScales[t];
            float progress = 1f - sc;
            float tx = baseX + (apexX - baseX) * progress;
            float ty = baseY + (apexY - baseY) * progress;

            buildCraggyPeakContour(tempPath, tx, ty, baseR * sc, seed + t * 0.85f);

            if (t == 0) {
                paint.setColor(graniteDark);
            } else if (t == 1) {
                paint.setColor((snowRatio > 0.75f) ? slateShadow : brownRock);
            } else if (t == 2) {
                paint.setColor((snowRatio > 0.60f) ? cobaltShadow : ochreRock);
            } else if (t == 3) {
                paint.setShader(new android.graphics.RadialGradient(
                        tx - baseR * sc * 0.25f, ty - baseR * sc * 0.25f, baseR * sc * 1.15f,
                        new int[]{ pureSnow, powderBlue, iceBlue, cobaltShadow },
                        new float[]{ 0f, 0.38f, 0.72f, 1f },
                        android.graphics.Shader.TileMode.CLAMP
                ));
            } else {
                paint.setColor(pureSnow);
            }
            canvas.drawPath(tempPath, paint);
            paint.setShader(null);
        }

        // 3. Twelve Zig-Zagging 3D Faceted Arête Spurs, Shadow Faces & Rock Crags (Image 1 & 2!)
        int spurs = 12;
        int segs = 5;
        float[] ridgeX = new float[segs + 1];
        float[] ridgeY = new float[segs + 1];

        for (int s = 0; s < spurs; s++) {
            double baseAng = s * (Math.PI * 2.0 / spurs) + (seed % 0.5f) * 0.4f;
            float spurLen = baseR * (0.86f + 0.20f * (float) Math.sin(s * 2.5f + seed));

            // Directional sunlight from Upper-Left / North-West
            float sunDot = (float) Math.cos(baseAng + 2.35);

            ridgeX[0] = apexX;
            ridgeY[0] = apexY;
            for (int k = 1; k <= segs; k++) {
                float frac = k / (float) segs;
                float zig = ((k + s) % 2 == 0 ? 1f : -1f) * 0.09f * (1f - frac * 0.4f);
                double a = baseAng + zig;
                float cxLerp = baseX + (apexX - baseX) * (1f - frac);
                float cyLerp = baseY + (apexY - baseY) * (1f - frac);
                ridgeX[k] = cxLerp + (float) Math.cos(a) * (spurLen * frac);
                ridgeY[k] = cyLerp + (float) Math.sin(a) * (spurLen * frac * 0.88f);
            }

            // Normal vector perpendicular to the spur for 3D left/right slope facets
            float perpX = (float) -Math.sin(baseAng);
            float perpY = (float) Math.cos(baseAng);

            for (int k = 0; k < segs; k++) {
                float f1 = k / (float) segs;
                float f2 = (k + 1) / (float) segs;
                float w1 = baseR * (0.04f + f1 * 0.16f);
                float w2 = baseR * (0.04f + f2 * 0.16f);

                boolean isSnowTier = (f1 < snowRatio);
                int litFacetCol, darkFacetCol;
                if (isSnowTier) {
                    litFacetCol  = (sunDot > -0.2f) ? pureSnow : powderBlue;
                    darkFacetCol = (sunDot > 0.2f) ? iceBlue : ((k % 2 == 0) ? cobaltShadow : slateShadow);
                } else {
                    litFacetCol  = (sunDot > -0.1f) ? ochreRock : brownRock;
                    darkFacetCol = (k % 2 == 0) ? graniteDark : slateShadow;
                }

                // Sunlit Left Slope Facet of the Spur
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(litFacetCol);
                tempPath.reset();
                tempPath.moveTo(ridgeX[k], ridgeY[k]);
                tempPath.lineTo(ridgeX[k + 1], ridgeY[k + 1]);
                tempPath.lineTo(ridgeX[k + 1] + perpX * w2, ridgeY[k + 1] + perpY * w2);
                tempPath.lineTo(ridgeX[k] + perpX * w1, ridgeY[k] + perpY * w1);
                tempPath.close();
                canvas.drawPath(tempPath, paint);

                // Shaded Right Slope Facet of the Spur (Deep Cobalt/Indigo Shadow as in Image 1 & 2)
                paint.setColor(darkFacetCol);
                tempPath.reset();
                tempPath.moveTo(ridgeX[k], ridgeY[k]);
                tempPath.lineTo(ridgeX[k + 1], ridgeY[k + 1]);
                tempPath.lineTo(ridgeX[k + 1] - perpX * w2, ridgeY[k + 1] - perpY * w2);
                tempPath.lineTo(ridgeX[k] - perpX * w1, ridgeY[k] - perpY * w1);
                tempPath.close();
                canvas.drawPath(tempPath, paint);

                // Exposed Jagged Ochre & Dark-Slate Rock Crags Breaking Through the Snow (Image 2 texture!)
                if (k >= 1 && (k + s) % 2 == 0) {
                    float cragX = ridgeX[k] - perpX * (w1 * 0.45f);
                    float cragY = ridgeY[k] - perpY * (w1 * 0.45f);
                    paint.setColor((s % 2 == 0) ? ochreRock : graniteDark);
                    tempPath.reset();
                    tempPath.moveTo(cragX, cragY - 4f * dp);
                    tempPath.lineTo(cragX + perpX * (w1 * 0.55f), cragY + 5f * dp);
                    tempPath.lineTo(cragX - perpX * (w1 * 0.35f), cragY + 7f * dp);
                    tempPath.close();
                    canvas.drawPath(tempPath, paint);
                }
            }

            // Crisp Serrated Snow/Rock Arête Spine Line Along the Spur
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeCap(Paint.Cap.ROUND);
            strokePaint.setColor((sunDot > -0.25f) ? pureSnow : powderBlue);
            strokePaint.setStrokeWidth((2.4f - (s % 2) * 0.6f) * dp);
            for (int k = 0; k < segs; k++) {
                if (k / (float) segs > snowRatio + 0.15f) {
                    strokePaint.setColor(ochreRock);
                }
                canvas.drawLine(ridgeX[k], ridgeY[k], ridgeX[k + 1], ridgeY[k + 1], strokePaint);
            }
        }

        // 4. Steep Snow Couloir Gullies & Cornice Striations Near the Summit Pyramid
        strokePaint.setColor( pureSnow );
        strokePaint.setStrokeWidth(2.8f * dp);
        canvas.drawLine(apexX - 10f * dp, apexY + 4f * dp, apexX, apexY, strokePaint);
        canvas.drawLine(apexX, apexY, apexX + 12f * dp, apexY + 6f * dp, strokePaint);
    }

    /**
     * Connects a chain of peaks with a continuous, multi-faceted 2.5D mountain ridge massif
     * (Sunlit NW slope + Deep Cobalt/Slate SE shadow slope + Transverse rock/snow gullies).
     */
    private void drawHimalayanMassifChain25D(Canvas canvas, Paint paint, Paint strokePaint,
                                             float cx, float cy, float w, float h, float[][] chainPts,
                                             float halfWidth, boolean heavySnow,
                                             int graniteDark, int ochreRock, int slateShadow,
                                             int cobaltShadow, int iceBlue, int pureSnow) {
        for (int i = 0; i < chainPts.length - 1; i++) {
            float x1 = cx + chainPts[i][0] * w,     y1 = cy + chainPts[i][1] * h;
            float x2 = cx + chainPts[i + 1][0] * w, y2 = cy + chainPts[i + 1][1] * h;

            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            if (len < 1f) continue;
            float nx = -dy / len, ny = dx / len;

            // Subdivide ridge segment into 6 craggy saddle sub-segments
            int subSegs = 6;
            for (int s = 0; s < subSegs; s++) {
                float t1 = s / (float) subSegs;
                float t2 = (s + 1) / (float) subSegs;
                float sx1 = x1 + dx * t1, sy1 = y1 + dy * t1 - ((s % 2 == 0) ? 6f * dp : -4f * dp);
                float sx2 = x1 + dx * t2, sy2 = y1 + dy * t2 - (((s + 1) % 2 == 0) ? 6f * dp : -4f * dp);

                // 1. North-West Sunlit Slope Facet
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(heavySnow ? pureSnow : ochreRock);
                tempPath.reset();
                tempPath.moveTo(sx1, sy1);
                tempPath.lineTo(sx2, sy2);
                tempPath.lineTo(sx2 + nx * halfWidth, sy2 + ny * halfWidth);
                tempPath.lineTo(sx1 + nx * halfWidth, sy1 + ny * halfWidth);
                tempPath.close();
                canvas.drawPath(tempPath, paint);

                // 2. South-East Shaded Slope Facet (Cobalt Blue / Dark Slate)
                paint.setColor(heavySnow ? ((s % 2 == 0) ? cobaltShadow : iceBlue) : graniteDark);
                tempPath.reset();
                tempPath.moveTo(sx1, sy1);
                tempPath.lineTo(sx2, sy2);
                tempPath.lineTo(sx2 - nx * halfWidth, sy2 - ny * halfWidth);
                tempPath.lineTo(sx1 - nx * halfWidth, sy1 - ny * halfWidth);
                tempPath.close();
                canvas.drawPath(tempPath, paint);

                // 3. Transverse Couloir Gullies Running Down Both Sides of the Ridge
                strokePaint.setStyle(Paint.Style.STROKE);
                strokePaint.setColor(heavySnow ? slateShadow : graniteDark);
                strokePaint.setStrokeWidth(1.8f * dp);
                canvas.drawLine(sx1, sy1, sx1 - nx * (halfWidth * 0.92f), sy1 - ny * (halfWidth * 0.92f), strokePaint);
                strokePaint.setColor(heavySnow ? iceBlue : ochreRock);
                canvas.drawLine(sx1, sy1, sx1 + nx * (halfWidth * 0.85f), sy1 + ny * (halfWidth * 0.85f), strokePaint);

                // 4. Sharp Ridge Crest Line
                strokePaint.setColor(heavySnow ? pureSnow : Color.parseColor("#E2EEFA"));
                strokePaint.setStrokeWidth(2.6f * dp);
                canvas.drawLine(sx1, sy1, sx2, sy2, strokePaint);
            }
        }
    }

    private void buildCraggyPeakContour(Path out, float cx, float cy, float radius, float seed) {
        out.reset();
        int steps = 36;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            double r = 0.84
                    + 0.10 * Math.cos(3.0 * t + seed)
                    + 0.08 * Math.sin(5.0 * t - seed * 1.2)
                    + 0.06 * Math.cos(9.0 * t + seed * 2.1)
                    + 0.04 * Math.sin(15.0 * t);
            float px = cx + (float) (radius * r * Math.cos(t));
            float py = cy + (float) (radius * 0.88f * r * Math.sin(t));
            if (i == 0) out.moveTo(px, py);
            else out.lineTo(px, py);
        }
        out.close();
    }

    /**
     * Renders a snow-dusted 2.5D Himalayan Deodar Pine Tree (visible at camera/helicopter scale).
     */
    private void drawSnowPineTree25D(Canvas canvas, Paint paint, float tx, float ty, float rad, boolean snowCapped) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawCircle(tx + 2.5f * dp, ty + 3.0f * dp, rad, paint);

        paint.setColor(Color.parseColor("#1B3B2B"));
        canvas.drawCircle(tx, ty, rad, paint);

        paint.setColor(Color.parseColor("#2D5A3F"));
        canvas.drawCircle(tx - rad * 0.15f, ty - rad * 0.15f, rad * 0.72f, paint);

        if (snowCapped) {
            paint.setColor(Color.parseColor("#F1F5F9"));
            canvas.drawCircle(tx - rad * 0.24f, ty - rad * 0.24f, rad * 0.44f, paint);
        }
    }

    /**
     * Renders a braided glacial meltwater river with whitewater rapids & boulder banks.
     */
    private void drawAlpineMeltwaterRiver(Canvas canvas, Paint strokePaint,
                                          float x1, float y1, float mx, float my, float x2, float y2, float starPhase) {
        tempPath.reset();
        tempPath.moveTo(x1, y1);
        tempPath.quadTo(mx, my, x2, y2);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        // Cobblestone riverbanks
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(22f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Deep glacial blue channel
        strokePaint.setColor(Color.parseColor("#0284C7"));
        strokePaint.setStrokeWidth(15f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Bright turquoise current
        strokePaint.setColor(Color.parseColor("#38BDF8"));
        strokePaint.setStrokeWidth(8f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Animated whitewater foam streaks
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(2.2f * dp);
        strokePaint.setPathEffect(kartDash);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setPathEffect(null);
    }

    /**
     * Renders a winding Himalayan switchback mountain pass road (Khardung La style) with hairpin bends,
     * snow shoulders, a suspension bridge over the gorge & moving expedition trucks.
     */
    private void drawHimalayanMountainPassRoad(Canvas canvas, Paint paint, Paint strokePaint,
                                               float cx, float cy, float w, float h, float starPhase) {
        float x0 = cx - w * 0.34f, y0 = cy + h;
        float x1 = cx - w * 0.42f, y1 = cy + h * 0.74f;
        float x2 = cx - w * 0.12f, y2 = cy + h * 0.68f;
        float x3 = cx - w * 0.06f, y3 = cy + h * 0.44f;

        tempPath.reset();
        tempPath.moveTo(x0, y0);
        tempPath.cubicTo(x1, y1, x2, y2, x3, y3);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        // Snowbank retaining wall & dark mountain asphalt
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(20f * dp);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#1E293B"));
        strokePaint.setStrokeWidth(14f * dp);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#FACC15"));
        strokePaint.setStrokeWidth(1.4f * dp);
        strokePaint.setPathEffect(kartDash);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setPathEffect(null);

        // Steel/Timber Suspension Bridge where the Pass Crosses the Glacial River Gorge
        float brX = cx - w * 0.21f, brY = cy + h * 0.72f;
        canvas.save();
        canvas.translate(brX, brY);
        canvas.rotate(-18f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRect(-22f * dp, -9f * dp, 22f * dp, 9f * dp, paint);
        strokePaint.setColor(Color.parseColor("#EF4444"));
        strokePaint.setStrokeWidth(2.2f * dp);
        canvas.drawLine(-22f * dp, -8f * dp, 22f * dp, -8f * dp, strokePaint);
        canvas.drawLine(-22f * dp,  8f * dp, 22f * dp,  8f * dp, strokePaint);
        canvas.restore();

        // 2 Animated Expedition Supply Trucks Climbing the Mountain Pass
        for (int trk = 0; trk < 2; trk++) {
            float prog = ((starPhase * 0.12f) + trk * 0.5f) % 1.0f;
            float inv = 1f - prog;
            float tx = inv * inv * inv * x0 + 3f * inv * inv * prog * x1 + 3f * inv * prog * prog * x2 + prog * prog * prog * x3;
            float ty = inv * inv * inv * y0 + 3f * inv * inv * prog * y1 + 3f * inv * prog * prog * y2 + prog * prog * prog * y3;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((trk == 0) ? Color.parseColor("#EA580C") : Color.parseColor("#FACC15"));
            canvas.drawRoundRect(new RectF(tx - 4.5f * dp, ty - 6.5f * dp, tx + 4.5f * dp, ty + 6.5f * dp), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRect(tx - 3.5f * dp, ty - 5.5f * dp, tx + 3.5f * dp, ty - 1.5f * dp, paint);
        }
    }

    /**
     * Renders the iconic stepped dark-brown/ochre rocky promontory ledge & twin stacked stone cairns
     * in the lower-left foreground of Image 2.
     */
    private void drawForegroundCairnPromontory25D(Canvas canvas, Paint paint, Paint strokePaint,
                                                  float lx, float ly, float pw, float ph) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#66000000"));
        canvas.drawRoundRect(new RectF(lx - pw + 8f * dp, ly - ph * 0.6f + 10f * dp, lx + pw + 8f * dp, ly + ph + 10f * dp), 12f * dp, 12f * dp, paint);

        tempPath.reset();
        tempPath.moveTo(lx - pw, ly - ph * 0.45f);
        tempPath.lineTo(lx + pw * 0.72f, ly - ph * 0.65f);
        tempPath.lineTo(lx + pw * 0.95f, ly + ph * 0.35f);
        tempPath.lineTo(lx + pw * 0.45f, ly + ph);
        tempPath.lineTo(lx - pw * 0.85f, ly + ph * 0.85f);
        tempPath.close();
        paint.setColor(Color.parseColor("#3D312A"));
        canvas.drawPath(tempPath, paint);

        tempPath.reset();
        tempPath.moveTo(lx - pw * 0.85f, ly - ph * 0.38f);
        tempPath.lineTo(lx + pw * 0.68f, ly - ph * 0.58f);
        tempPath.lineTo(lx + pw * 0.56f, ly + ph * 0.12f);
        tempPath.lineTo(lx - pw * 0.65f, ly + ph * 0.24f);
        tempPath.close();
        paint.setColor(Color.parseColor("#6E5A4F"));
        canvas.drawPath(tempPath, paint);

        paint.setColor(Color.parseColor("#C2A385"));
        canvas.drawRoundRect(new RectF(lx - pw * 0.55f, ly + ph * 0.08f, lx + pw * 0.15f, ly + ph * 0.58f), 4f * dp, 4f * dp, paint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(4.0f * dp);
        canvas.drawLine(lx - pw * 0.78f, ly - ph * 0.10f, lx - pw * 0.12f, ly + ph * 0.68f, strokePaint);
        canvas.drawLine(lx - pw * 0.45f, ly - ph * 0.32f, lx + pw * 0.35f, ly - ph * 0.18f, strokePaint);

        drawStoneCairn25D(canvas, paint, lx + pw * 0.46f, ly - ph * 0.42f, 1.35f);
        drawStoneCairn25D(canvas, paint, lx + pw * 0.22f, ly - ph * 0.22f, 1.05f);
    }

    private void drawStoneCairn25D(Canvas canvas, Paint paint, float cx, float cy, float scale) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#66000000"));
        canvas.drawOval(new RectF(cx - 12f * dp * scale, cy - 2f * dp, cx + 4f * dp * scale, cy + 6f * dp * scale), paint);

        for (int tier = 0; tier < 5; tier++) {
            float w = (8.5f - tier * 1.4f) * dp * scale;
            float h = 3.2f * dp * scale;
            float ty = cy - tier * (3.6f * dp * scale);
            paint.setColor((tier % 2 == 0) ? Color.parseColor("#3E322B") : Color.parseColor("#786659"));
            canvas.drawRoundRect(new RectF(cx - w, ty - h, cx + w, ty + h), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.parseColor("#D6C7B8"));
            canvas.drawRect(cx - w * 0.75f, ty - h, cx + w * 0.25f, ty, paint);
        }
    }

    private void drawGlacierStream25D(Canvas canvas, Paint paint, Paint strokePaint,
                                      float x1, float y1, float mx, float my, float x2, float y2, float width) {
        tempPath.reset();
        tempPath.moveTo(x1, y1);
        tempPath.quadTo(mx, my, x2, y2);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        strokePaint.setColor(Color.parseColor("#334155"));
        strokePaint.setStrokeWidth(width * 1.25f);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#93C5FD"));
        strokePaint.setStrokeWidth(width);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#F8FAFC"));
        strokePaint.setStrokeWidth(width * 0.62f);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#1D4ED8"));
        strokePaint.setStrokeWidth(width * 0.45f);
        strokePaint.setPathEffect(railSleeperDash);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setPathEffect(null);
    }

    private void drawAvalancheRoadStub(Canvas canvas, Paint paint, Paint strokePaint, float edgeX, float edgeY, float roadW, float stubLen, float rotDeg, float starPhase) {
        canvas.save();
        canvas.translate(edgeX, edgeY);
        canvas.rotate(rotDeg);

        float halfW = roadW * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(sidewalkColor);
        canvas.drawRect(-halfW - 14f * dp, 0f, halfW + 14f * dp, stubLen * 0.76f, paint);
        paint.setColor(roadAsphaltColor);
        canvas.drawRect(-halfW, 0f, halfW, stubLen * 0.82f, paint);

        paint.setColor(Color.parseColor("#254E80"));
        canvas.drawOval(new RectF(-halfW * 1.35f, stubLen * 0.48f, halfW * 1.35f, stubLen * 1.18f), paint);
        paint.setColor(Color.parseColor("#B4D2F0"));
        canvas.drawOval(new RectF(-halfW * 1.24f, stubLen * 0.54f, halfW * 1.24f, stubLen * 1.22f), paint);
        paint.setColor(Color.WHITE);
        canvas.drawOval(new RectF(-halfW * 1.10f, stubLen * 0.60f, halfW * 1.10f, stubLen * 1.26f), paint);

        boolean blink = ((int) (starPhase * 6f)) % 2 == 0;
        for (int b = -1; b <= 1; b++) {
            float bx = b * (halfW * 0.56f), by = stubLen * 0.36f;
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRoundRect(new RectF(bx - 12f * dp, by - 3.5f * dp, bx + 12f * dp, by + 3.5f * dp), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawRect(bx - 5f * dp, by - 3.5f * dp, bx + 5f * dp, by + 3.5f * dp, paint);
            paint.setColor(blink ? Color.parseColor("#F97316") : Color.parseColor("#38BDF8"));
            canvas.drawCircle(bx, by - 4.8f * dp, 3.0f * dp, paint);
        }
        canvas.restore();
    }




    private void drawDetailedNuclearPlant25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int forestGroundCol = isSunnyDay ? Color.parseColor("#3B6928") : (isMoonlightMode ? Color.parseColor("#0F291A") : Color.parseColor("#07180E"));
        int compoundGrass   = isSunnyDay ? Color.parseColor("#558B2F") : (isMoonlightMode ? Color.parseColor("#163822") : Color.parseColor("#0C2415"));
        int concretePadCol  = isSunnyDay ? Color.parseColor("#B0B7BC") : (isMoonlightMode ? Color.parseColor("#334155") : Color.parseColor("#1E293B"));
        int concreteDarkCol = isSunnyDay ? Color.parseColor("#8C959C") : (isMoonlightMode ? Color.parseColor("#1E293B") : Color.parseColor("#0F172A"));
        int roadCol         = isSunnyDay ? Color.parseColor("#374151") : Color.parseColor("#182232");
        int bldgWallCol     = isSunnyDay ? Color.parseColor("#64748B") : Color.parseColor("#1E293B");
        int bldgRoofCol     = isSunnyDay ? Color.parseColor("#CBD5E1") : Color.parseColor("#475569");
        int seaDeepCol      = isSunnyDay ? Color.parseColor("#0D5C75") : Color.parseColor("#082F49");
        int seaMidCol       = isSunnyDay ? Color.parseColor("#157A96") : Color.parseColor("#0369A1");
        int pipeBlueCol     = Color.parseColor("#0284C7");
        int pipeHighlight   = Color.parseColor("#38BDF8");

        // =========================================================================
        // 1. BASE FOREST GROUND, EASTERN COASTAL SEA & ROCKY SHORELINE
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawRoundRect(new RectF(cx - w + 10f * dp, cy - h + 12f * dp, cx + w + 10f * dp, cy + h + 12f * dp), 26f * dp, 26f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(forestGroundCol);
        canvas.drawRoundRect(tempRect, 26f * dp, 26f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 26f * dp, 26f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Eastern Coastal Sea / Cooling Reservoir (Right Edge of Reference Image)
        tempPath.reset();
        tempPath.moveTo(cx + w * 0.72f, cy - h);
        tempPath.cubicTo(cx + w * 0.86f, cy - h * 0.45f, cx + w * 0.82f, cy + h * 0.25f, cx + w * 0.90f, cy + h);
        tempPath.lineTo(cx + w, cy + h);
        tempPath.lineTo(cx + w, cy - h);
        tempPath.close();
        paint.setColor(seaDeepCol);
        canvas.drawPath(tempPath, paint);

        // Coastal Shallow Water Band & Ripples
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(seaMidCol);
        strokePaint.setStrokeWidth(14f * dp);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#88BAE6FD"));
        strokePaint.setStrokeWidth(2.2f * dp);
        for (int wLine = -6; wLine <= 6; wLine++) {
            float wy = cy + wLine * (h * 0.14f);
            float wx = cx + w * 0.88f + (float) Math.sin(starPhase * 2.4f + wLine) * 4f * dp;
            canvas.drawLine(wx, wy - 12f * dp, wx + 6f * dp, wy + 12f * dp, strokePaint);
        }

        // Inner Compound Grass Base
        RectF compoundRect = new RectF(cx - w * 0.88f, cy - h * 0.88f, cx + w * 0.82f, cy + h * 0.88f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(compoundGrass);
        canvas.drawRoundRect(compoundRect, 8f * dp, 8f * dp, paint);

        // =========================================================================
        // 2. INTERNAL ASPHALT ROAD GRID, CROSSWALKS & MAIN ENTRANCE GATE
        // =========================================================================
        float rLeft   = cx - w * 0.82f;
        float rMidL   = cx - w * 0.28f;
        float rMidR   = cx + w * 0.24f;
        float rTop    = cy - h * 0.82f;
        float rUpperM = cy - h * 0.36f;
        float rLowerM = cy + h * 0.28f;
        float rBot    = cy + h * 0.82f;

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.SQUARE);
        strokePaint.setStrokeJoin(Paint.Join.MITER);

        // Road Curb Border
        strokePaint.setColor(concretePadCol);
        strokePaint.setStrokeWidth(20f * dp);
        drawNuclearRoadNetwork(canvas, strokePaint, cx, cy, w, h, rLeft, rMidL, rMidR, rTop, rUpperM, rLowerM, rBot);

        // Dark Asphalt Surface
        strokePaint.setColor(roadCol);
        strokePaint.setStrokeWidth(16f * dp);
        drawNuclearRoadNetwork(canvas, strokePaint, cx, cy, w, h, rLeft, rMidL, rMidR, rTop, rUpperM, rLowerM, rBot);

        // Dashed White Center Lane Lines
        strokePaint.setColor(Color.parseColor("#CCE2E8F0"));
        strokePaint.setStrokeWidth(1.2f * dp);
        strokePaint.setPathEffect(kartDash);
        drawNuclearRoadNetwork(canvas, strokePaint, cx, cy, w, h, rLeft, rMidL, rMidR, rTop, rUpperM, rLowerM, rBot);
        strokePaint.setPathEffect(null);

        // White Pedestrian Zebra Crossings (matching reference intersections)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#E2E8F0"));
        float[][] crosswalks = {
                { rLeft, cy - h * 0.56f, 1f }, { rMidL, cy - h * 0.42f, 1f },
                { rMidL, cy + h * 0.35f, 1f }, { cx - w * 0.52f, rUpperM, 0f }
        };
        for (float[] cw : crosswalks) {
            for (int s = -2; s <= 2; s++) {
                if (cw[2] > 0.5f) {
                    canvas.drawRect(cw[0] + s * 2.5f * dp - 0.8f * dp, cw[1] - 4f * dp, cw[0] + s * 2.5f * dp + 0.8f * dp, cw[1] + 4f * dp, paint);
                } else {
                    canvas.drawRect(cw[0] - 4f * dp, cw[1] + s * 2.5f * dp - 0.8f * dp, cw[0] + 4f * dp, cw[1] + s * 2.5f * dp + 0.8f * dp, paint);
                }
            }
        }

        // =========================================================================
        // 3. TOP-LEFT SECTOR: CHEMICAL STORAGE TANKS (2x2) & CONTROL SUBSTATION
        // =========================================================================
        // Left Tank Farm Concrete Pad
        RectF tankPad = new RectF(cx - w * 0.76f, cy - h * 0.76f, cx - w * 0.52f, cy - h * 0.44f);
        paint.setColor(concreteDarkCol);
        canvas.drawRoundRect(tankPad, 4f * dp, 4f * dp, paint);
        paint.setColor(concretePadCol);
        canvas.drawRect(tankPad.left + 3f * dp, tankPad.top + 3f * dp, tankPad.right - 3f * dp, tankPad.bottom - 20f * dp, paint);
        canvas.drawRect(tankPad.left + 3f * dp, tankPad.bottom - 17f * dp, tankPad.right - 3f * dp, tankPad.bottom - 3f * dp, paint);

        // 2 Upper Cylindrical Tanks + 2 Lower Large Spherical/Domed Storage Tanks
        float[][] tanks = {
                { cx - w * 0.70f, cy - h * 0.68f, 12f }, { cx - w * 0.62f, cy - h * 0.68f, 12f },
                { cx - w * 0.69f, cy - h * 0.52f, 16f }, { cx - w * 0.58f, cy - h * 0.52f, 16f }
        };
        for (float[] tk : tanks) {
            float tx = tk[0], ty = tk[1], tr = tk[2] * dp;
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawCircle(tx + 3.5f * dp, ty + 4.5f * dp, tr, paint);
            paint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawCircle(tx, ty, tr, paint);
            paint.setColor(Color.parseColor("#E2E8F0"));
            canvas.drawCircle(tx - tr * 0.15f, ty - tr * 0.15f, tr * 0.78f, paint);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(tx - tr * 0.28f, ty - tr * 0.28f, tr * 0.35f, paint);
            // Structural roof ribs
            strokePaint.setColor(Color.parseColor("#64748B"));
            strokePaint.setStrokeWidth(1.0f * dp);
            canvas.drawLine(tx - tr, ty, tx + tr, ty, strokePaint);
            canvas.drawLine(tx, ty - tr, tx, ty + tr, strokePaint);
        }

        // Auxiliary Substation & Blue-Roofed Control Building (Right of Tank Farm)
        RectF subPad = new RectF(cx - w * 0.48f, cy - h * 0.76f, cx - w * 0.32f, cy - h * 0.44f);
        paint.setColor(concretePadCol);
        canvas.drawRoundRect(subPad, 4f * dp, 4f * dp, paint);
        drawIndustrialBox25D(canvas, paint, strokePaint, cx - w * 0.42f, cy - h * 0.66f, 24f * dp, 32f * dp,
                Color.parseColor("#1E3A8A"), Color.parseColor("#2563EB"), true);

        // =========================================================================
        // 4. MIDDLE-LEFT SECTOR: OPEN-ROOF TURBINE HALL & YELLOW GANTRY CRANES
        // =========================================================================
        RectF turbHall = new RectF(cx - w * 0.80f, cy - h * 0.22f, cx - w * 0.38f, cy + h * 0.18f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawRoundRect(new RectF(turbHall.left + 8f * dp, turbHall.top + 10f * dp, turbHall.right + 8f * dp, turbHall.bottom + 10f * dp), 5f * dp, 5f * dp, paint);

        // 2.5D South Facade Wall & Concrete Roof Frame
        paint.setColor(bldgWallCol);
        canvas.drawRoundRect(new RectF(turbHall.left, turbHall.top + 6f * dp, turbHall.right, turbHall.bottom + 8f * dp), 4f * dp, 4f * dp, paint);
        paint.setColor(bldgRoofCol);
        canvas.drawRoundRect(turbHall, 4f * dp, 4f * dp, paint);

        // Open Recessed Mechanical Bay Inside the Turbine Hall
        RectF bayRect = new RectF(turbHall.left + 10f * dp, turbHall.top + 22f * dp, turbHall.right - 10f * dp, turbHall.bottom - 10f * dp);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRect(bayRect, paint);

        // Internal Structural Steel Grid
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(1.4f * dp);
        for (float gx = bayRect.left + 12f * dp; gx < bayRect.right; gx += 14f * dp) {
            canvas.drawLine(gx, bayRect.top, gx, bayRect.bottom, strokePaint);
        }

        // Massive Horizontal Blue Cylindrical Steam Turbine & Generator Rotor Shaft
        float shaftCY = bayRect.centerY();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0369A1"));
        canvas.drawRoundRect(new RectF(bayRect.left + 8f * dp, shaftCY - 12f * dp, bayRect.right - 8f * dp, shaftCY + 12f * dp), 6f * dp, 6f * dp, paint);
        paint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawRoundRect(new RectF(bayRect.left + 10f * dp, shaftCY - 6f * dp, bayRect.right - 10f * dp, shaftCY + 2f * dp), 3f * dp, 3f * dp, paint);

        // Turbine Rotor Casings & Animated Spinning Blades
        for (int stage = 0; stage < 4; stage++) {
            float sx = bayRect.left + 22f * dp + stage * ((bayRect.width() - 44f * dp) / 3f);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawRect(sx - 5f * dp, shaftCY - 14f * dp, sx + 5f * dp, shaftCY + 14f * dp, paint);
            paint.setColor(Color.parseColor("#CBD5E1"));
            float spinOff = (float) Math.sin(starPhase * 12f + stage) * 6f * dp;
            canvas.drawRect(sx - 2.5f * dp, shaftCY + spinOff - 4f * dp, sx + 2.5f * dp, shaftCY + spinOff + 4f * dp, paint);
        }

        // Twin Yellow Overhead Bridge Cranes Spanning the Turbine Bay
        float craneShift = (float) Math.sin(starPhase * 0.7f) * 14f * dp;
        float[] craneXs = { bayRect.centerX() - 28f * dp + craneShift, bayRect.centerX() + 28f * dp + craneShift };
        for (float crX : craneXs) {
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawRect(crX - 5f * dp, bayRect.top + 2f * dp, crX + 7f * dp, bayRect.bottom + 2f * dp, paint);
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRect(crX - 6f * dp, bayRect.top - 2f * dp, crX + 4f * dp, bayRect.bottom + 2f * dp, paint);
            paint.setColor(Color.parseColor("#CA8A04"));
            canvas.drawRect(crX - 4f * dp, shaftCY - 8f * dp, crX + 2f * dp, shaftCY + 8f * dp, paint);
        }

        // Yellow & Blue High-Pressure Steam Pipes Connecting Reactor to Turbine Hall
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#FACC15"));
        strokePaint.setStrokeWidth(4.0f * dp);
        canvas.drawLine(turbHall.right, cy - h * 0.08f, cx - w * 0.22f, cy - h * 0.08f, strokePaint);
        canvas.drawLine(turbHall.right, cy - h * 0.02f, cx - w * 0.22f, cy - h * 0.02f, strokePaint);
        strokePaint.setColor(pipeBlueCol);
        canvas.drawLine(turbHall.right, cy + h * 0.06f, cx - w * 0.22f, cy + h * 0.06f, strokePaint);

        // =========================================================================
        // 5. CENTER SECTOR: REACTOR CONTAINMENT DOME & RED/WHITE EXHAUST STACK
        // =========================================================================
        RectF reactBase = new RectF(cx - w * 0.24f, cy - h * 0.24f, cx + w * 0.20f, cy + h * 0.20f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#60000000"));
        canvas.drawRoundRect(new RectF(reactBase.left + 10f * dp, reactBase.top + 12f * dp, reactBase.right + 10f * dp, reactBase.bottom + 12f * dp), 6f * dp, 6f * dp, paint);

        // 2.5D Multi-Story Reactor Auxiliary Building Facade (South Wall with Windows)
        paint.setColor(bldgWallCol);
        canvas.drawRoundRect(new RectF(reactBase.left, reactBase.top + 10f * dp, reactBase.right, reactBase.bottom + 12f * dp), 5f * dp, 5f * dp, paint);
        paint.setColor(isSunnyDay ? Color.parseColor("#BAE6FD") : Color.parseColor("#FDE047"));
        for (int wy = 0; wy < 2; wy++) {
            for (int wx = -4; wx <= 4; wx++) {
                float winX = reactBase.centerX() + wx * 16f * dp;
                float winY = reactBase.bottom + 1f * dp + wy * 5f * dp;
                canvas.drawRect(winX - 4f * dp, winY, winX + 4f * dp, winY + 2.8f * dp, paint);
            }
        }

        // Reactor Building Stepped Concrete Roof
        paint.setColor(bldgRoofCol);
        canvas.drawRoundRect(reactBase, 5f * dp, 5f * dp, paint);
        paint.setColor(concreteDarkCol);
        canvas.drawRect(reactBase.left + 8f * dp, reactBase.top + 8f * dp, reactBase.right - 8f * dp, reactBase.bottom - 8f * dp, paint);

        // Giant 3D Hemispherical White Reactor Containment Dome
        float domeCX = reactBase.centerX() - 10f * dp, domeCY = reactBase.centerY() - 14f * dp;
        float domeR = w * 0.145f;

        // Dome Drop Shadow
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawCircle(domeCX + 8f * dp, domeCY + 10f * dp, domeR, paint);

        // Cylindrical Reactor Silo Wall & Red Equator Warning Band (matching reference image!)
        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawCircle(domeCX, domeCY + 4f * dp, domeR, paint);
        paint.setColor(Color.parseColor("#DC2626")); // Red safety stripe around containment dome
        canvas.drawCircle(domeCX, domeCY + 1f * dp, domeR, paint);

        // 3D Sunlit Hemispherical Containment Dome Cap
        paint.setShader(new android.graphics.RadialGradient(
                domeCX - domeR * 0.28f, domeCY - domeR * 0.28f, domeR * 1.15f,
                new int[]{ Color.WHITE, Color.parseColor("#E2E8F0"), Color.parseColor("#94A3B8"), Color.parseColor("#475569") },
                new float[]{ 0f, 0.45f, 0.82f, 1f },
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(domeCX, domeCY - 2f * dp, domeR * 0.96f, paint);
        paint.setShader(null);

        // Dome Top Sensor Hatch & Exterior Access Ladder Gantry
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRoundRect(new RectF(domeCX - 8f * dp, domeCY - 8f * dp, domeCX + 8f * dp, domeCY + 8f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawRect(domeCX - 4f * dp, domeCY - 4f * dp, domeCX + 4f * dp, domeCY + 4f * dp, paint);
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawLine(domeCX, domeCY + 8f * dp, domeCX, domeCY + domeR + 4f * dp, strokePaint);

        // Right-Side Steam Generator Silos & Red-and-White Striped Ventilation Chimney Stack
        float stackX = reactBase.right + 16f * dp, stackY = reactBase.centerY() - 10f * dp;
        // 2 Silver Pressurizer Silos
        for (int s = -1; s <= 1; s += 2) {
            paint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawCircle(stackX - 10f * dp, stackY + s * 16f * dp, 9f * dp, paint);
            paint.setColor(Color.parseColor("#E2E8F0"));
            canvas.drawCircle(stackX - 12f * dp, stackY + s * 16f * dp - 2f * dp, 5.5f * dp, paint);
        }
        // Tall 2.5D Red & White Striped Chimney Stack
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawLine(stackX, stackY, stackX + 18f * dp, stackY + 12f * dp, strokePaint);
        for (int band = 0; band < 6; band++) {
            float by = stackY - band * 6.5f * dp;
            float br = (6.5f - band * 0.4f) * dp;
            paint.setColor((band % 2 == 0) ? Color.parseColor("#DC2626") : Color.WHITE);
            canvas.drawRoundRect(new RectF(stackX - br, by - 4.5f * dp, stackX + br, by + 4.5f * dp), 2f * dp, 2f * dp, paint);
        }
        // Blinking Red Aviation Obstruction Light on Stack Top
        if (((int) (starPhase * 6f)) % 2 == 0) {
            paint.setColor(Color.parseColor("#EF4444"));
            canvas.drawCircle(stackX, stackY - 34f * dp, 3.2f * dp, paint);
        }

        // =========================================================================
        // 6. MIDDLE-RIGHT SECTOR: BLUE COOLING PIPELINES & COASTAL OUTFALL SLUICE
        // =========================================================================
        // Thick Blue Cooling Water Pipelines Running from Cooling Towers to Coastal Pump House
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        float[][] pipeRoutes = {
                { cx + w * 0.30f, cy - h * 0.32f, cx + w * 0.30f, cy - h * 0.14f, cx + w * 0.68f, cy - h * 0.14f },
                { cx + w * 0.56f, cy - h * 0.30f, cx + w * 0.56f, cy - h * 0.18f, cx + w * 0.68f, cy - h * 0.18f }
        };
        for (float[] pr : pipeRoutes) {
            tempPath.reset();
            tempPath.moveTo(pr[0], pr[1]);
            tempPath.lineTo(pr[2], pr[3]);
            tempPath.lineTo(pr[4], pr[5]);
            strokePaint.setColor(Color.parseColor("#075985"));
            strokePaint.setStrokeWidth(7.5f * dp);
            canvas.drawPath(tempPath, strokePaint);
            strokePaint.setColor(pipeBlueCol);
            strokePaint.setStrokeWidth(5.0f * dp);
            canvas.drawPath(tempPath, strokePaint);
            strokePaint.setColor(pipeHighlight);
            strokePaint.setStrokeWidth(1.8f * dp);
            canvas.drawPath(tempPath, strokePaint);
        }

        // Coastal Pump House & 4-Bay Concrete Outfall Sluice Structure on the Right Shoreline
        RectF sluiceRect = new RectF(cx + w * 0.66f, cy - h * 0.22f, cx + w * 0.85f, cy - h * 0.04f);
        drawIndustrialBox25D(canvas, paint, strokePaint, sluiceRect.centerX(), sluiceRect.centerY() - 8f * dp,
                sluiceRect.width() * 0.5f, sluiceRect.height() * 0.45f,
                bldgWallCol, Color.parseColor("#0284C7"), true);

        // 4 Concrete Spillway Bays & Animated Foaming White Water Jets Discharging into the Sea
        for (int bay = 0; bay < 4; bay++) {
            float bx = sluiceRect.left + 6f * dp + bay * ((sluiceRect.width() - 12f * dp) / 3.5f);
            float by = sluiceRect.bottom - 2f * dp;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(concreteDarkCol);
            canvas.drawRect(bx - 3f * dp, by, bx + 3f * dp, by + 16f * dp, paint);

            // Animated White Foaming Outfall Water Jet
            for (int f = 0; f < 4; f++) {
                float fp = ((starPhase * 0.85f) + f * 0.25f + bay * 0.15f) % 1.0f;
                float fy = by + 6f * dp + fp * 32f * dp;
                float fx = bx + (float) Math.sin(starPhase * 5f + f + bay) * 3f * dp;
                int fAlpha = Math.max(0, Math.round((1f - fp) * 220f));
                paint.setColor(Color.argb(fAlpha, 224, 242, 254));
                canvas.drawCircle(fx, fy, (3.2f + fp * 5.5f) * dp, paint);
            }
        }

        // =========================================================================
        // 7. TOP-RIGHT SECTOR: TWIN 2.5D HYPERBOLOID COOLING TOWERS & STEAM PLUMES
        // =========================================================================
        drawHyperboloidCoolingTower25D(canvas, paint, strokePaint,
                cx + w * 0.28f, cy - h * 0.50f, w * 0.175f, h * 0.185f, starPhase, 0f);
        drawHyperboloidCoolingTower25D(canvas, paint, strokePaint,
                cx + w * 0.62f, cy - h * 0.46f, w * 0.155f, h * 0.165f, starPhase, 1.8f);

        // =========================================================================
        // 8. BOTTOM-LEFT SECTOR: HIGH-VOLTAGE SWITCHYARD, INSULATORS & 4 PYLONS
        // =========================================================================
        RectF switchYard = new RectF(cx - w * 0.80f, cy + h * 0.34f, cx - w * 0.32f, cy + h * 0.78f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isSunnyDay ? Color.parseColor("#9E9D89") : Color.parseColor("#262B36"));
        canvas.drawRoundRect(new RectF(switchYard.left, switchYard.top, switchYard.right, switchYard.centerY() + 8f * dp), 4f * dp, 4f * dp, paint);

        // 3 Rows of Ceramic Insulator Stacks, Circuit Breakers & Overhead Busbars
        for (int row = 0; row < 3; row++) {
            float ry = switchYard.top + 10f * dp + row * 16f * dp;
            strokePaint.setColor(Color.parseColor("#94A3B8"));
            strokePaint.setStrokeWidth(1.4f * dp);
            canvas.drawLine(switchYard.left + 8f * dp, ry, switchYard.right - 8f * dp, ry, strokePaint);

            for (int col = 0; col < 9; col++) {
                float ix = switchYard.left + 12f * dp + col * ((switchYard.width() - 24f * dp) / 8f);
                paint.setColor(Color.parseColor("#475569"));
                canvas.drawRect(ix - 2.5f * dp, ry - 3.5f * dp, ix + 2.5f * dp, ry + 3.5f * dp, paint);
                paint.setColor(Color.parseColor("#E2E8F0"));
                canvas.drawCircle(ix, ry - 1.5f * dp, 1.8f * dp, paint);

                // Periodic High-Voltage Blue Electric Corona Arc
                if ((row + col * 3) % 7 == 0 && Math.sin(starPhase * 9f + col) > 0.90) {
                    paint.setColor(Color.parseColor("#38BDF8"));
                    canvas.drawCircle(ix, ry, 4.2f * dp, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(ix, ry, 1.8f * dp, paint);
                }
            }
        }

        // 4 Steel Lattice High-Voltage Transmission Pylons & Power Lines
        for (int p = 0; p < 4; p++) {
            float px = switchYard.left + 22f * dp + p * ((switchYard.width() - 44f * dp) / 3f);
            float py = switchYard.bottom - 14f * dp;
            drawTransmissionPylon25D(canvas, paint, strokePaint, px, py);
            // Overhead high-voltage power cables connecting switchyard to pylons
            strokePaint.setColor(Color.parseColor("#441E293B"));
            strokePaint.setStrokeWidth(1.1f * dp);
            canvas.drawLine(px - 8f * dp, switchYard.top + 10f * dp, px - 10f * dp, py - 18f * dp, strokePaint);
            canvas.drawLine(px + 8f * dp, switchYard.top + 10f * dp, px + 10f * dp, py - 18f * dp, strokePaint);
        }

        // =========================================================================
        // 9. BOTTOM-CENTER SECTOR: ADMIN COMPLEX, PARKING LOTS & MAIN GATEHOUSE
        // =========================================================================
        // Administration Building
        drawIndustrialBox25D(canvas, paint, strokePaint, cx - w * 0.02f, cy + h * 0.42f, w * 0.16f, h * 0.065f,
                bldgWallCol, bldgRoofCol, true);

        // Employee Parking Lots (Left & Right of Central Entrance Avenue) + Green Tree Medians
        for (int side = -1; side <= 1; side += 2) {
            float lotX = cx - w * 0.02f + side * w * 0.14f;
            RectF pLot = new RectF(lotX - 24f * dp, cy + h * 0.54f, lotX + 24f * dp, cy + h * 0.76f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(roadCol);
            canvas.drawRoundRect(pLot, 3f * dp, 3f * dp, paint);

            // Green Median Island Next to Avenue
            float medX = cx - w * 0.02f + side * w * 0.055f;
            paint.setColor(compoundGrass);
            canvas.drawRoundRect(new RectF(medX - 9f * dp, cy + h * 0.54f, medX + 9f * dp, cy + h * 0.74f), 4f * dp, 4f * dp, paint);

            // Parking Lines & Parked Employee Cars
            strokePaint.setColor(Color.parseColor("#94A3B8"));
            strokePaint.setStrokeWidth(1.0f * dp);
            int[] empCars = { Color.WHITE, Color.parseColor("#38BDF8"), Color.parseColor("#EF4444"), Color.parseColor("#CBD5E1"), Color.parseColor("#FACC15") };
            for (int r = 0; r < 5; r++) {
                float py = pLot.top + 6f * dp + r * 11f * dp;
                canvas.drawLine(pLot.left + 3f * dp, py, pLot.right - 3f * dp, py, strokePaint);
                if ((r + side) % 2 == 0) {
                    paint.setColor(empCars[(r + (side > 0 ? 2 : 0)) % empCars.length]);
                    canvas.drawRoundRect(new RectF(pLot.left + 5f * dp, py + 2f * dp, pLot.left + 15f * dp, py + 8f * dp), 1.5f * dp, 1.5f * dp, paint);
                }
            }
        }

        // Main Southern Entrance Road, Security Gatehouse Canopy & Hazard Striping
        float gateX = cx - w * 0.02f, gateY = cy + h * 0.86f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(roadCol);
        canvas.drawRect(gateX - 26f * dp, cy + h * 0.50f, gateX + 26f * dp, cy + h, paint);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(1.4f * dp);
        strokePaint.setPathEffect(kartDash);
        canvas.drawLine(gateX, cy + h * 0.50f, gateX, cy + h, strokePaint);
        strokePaint.setPathEffect(null);

        // Yellow & Black Security Boom-Gate Striping
        for (int b = -4; b <= 4; b++) {
            paint.setColor((b % 2 == 0) ? Color.parseColor("#FACC15") : Color.parseColor("#0F172A"));
            canvas.drawRect(gateX + b * 5.5f * dp - 2.5f * dp, gateY + 4f * dp, gateX + b * 5.5f * dp + 2.5f * dp, gateY + 7.5f * dp, paint);
        }
        // Overhead Gate Canopy
        drawIndustrialBox25D(canvas, paint, strokePaint, gateX, gateY - 4f * dp, 30f * dp, 7f * dp,
                bldgWallCol, Color.parseColor("#E2E8F0"), false);

        // =========================================================================
        // 10. BOTTOM-RIGHT SECTOR: RADWASTE BUILDING (☢), BLUE WAREHOUSE & CLARIFIER
        // =========================================================================
        // Radwaste Processing Building with Yellow & Black Radiation Trefoil Symbol (☢)
        float radX = cx + w * 0.44f, radY = cy + h * 0.44f;
        drawIndustrialBox25D(canvas, paint, strokePaint, radX, radY, 36f * dp, 26f * dp,
                bldgWallCol, bldgRoofCol, true);
        drawRadiationTrefoil(canvas, paint, radX, radY - 2f * dp, 9.5f * dp);

        // Bottom-Right Blue-Roofed Utility Warehouse
        drawIndustrialBox25D(canvas, paint, strokePaint, cx + w * 0.52f, cy + h * 0.68f, 44f * dp, 28f * dp,
                bldgWallCol, Color.parseColor("#1D4ED8"), true);

        // Circular Water Treatment Clarifier Basin with Rotating Scraper Bridge (Far Right)
        float clarX = cx + w * 0.74f, clarY = cy + h * 0.44f, clarR = 32f * dp;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawCircle(clarX + 4f * dp, clarY + 5f * dp, clarR, paint);
        paint.setColor(concretePadCol);
        canvas.drawCircle(clarX, clarY, clarR, paint);
        paint.setColor(Color.parseColor("#0369A1"));
        canvas.drawCircle(clarX, clarY, clarR - 4f * dp, paint);
        paint.setColor(Color.parseColor("#0284C7"));
        canvas.drawCircle(clarX, clarY, clarR * 0.65f, paint);

        // Rotating Mechanical Clarifier Bridge Arm
        canvas.save();
        canvas.translate(clarX, clarY);
        canvas.rotate(starPhase * 18f);
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(4.0f * dp);
        canvas.drawLine(-clarR + 3f * dp, 0f, clarR - 3f * dp, 0f, strokePaint);
        paint.setColor(Color.parseColor("#64748B"));
        canvas.drawCircle(0f, 0f, 6f * dp, paint);
        canvas.restore();

        // =========================================================================
        // 11. PERIMETER SECURITY WALL, 4 CORNER WATCHTOWERS & LUSH PLANT TREES
        // =========================================================================
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(5.0f * dp);
        canvas.drawRoundRect(compoundRect, 8f * dp, 8f * dp, strokePaint);
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(2.0f * dp);
        canvas.drawRoundRect(compoundRect, 8f * dp, 8f * dp, strokePaint);

        // 4 Corner Guard Watchtowers with Searchlight Glows
        float[][] watchTowers = {
                { compoundRect.left, compoundRect.top },
                { compoundRect.right, compoundRect.top },
                { compoundRect.left, compoundRect.bottom },
                { compoundRect.right, compoundRect.bottom }
        };
        for (float[] wt : watchTowers) {
            drawIndustrialBox25D(canvas, paint, strokePaint, wt[0], wt[1], 11f * dp, 11f * dp,
                    Color.parseColor("#334155"), Color.parseColor("#94A3B8"), false);
            if (!isSunnyDay) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#44FEF08A"));
                canvas.drawCircle(wt[0], wt[1], 18f * dp, paint);
            }
        }

        // Internal Greenbelt Trees & Dense Outer Forest Perimeter Trees
        float[][] plantTrees = {
                // Outer forest border trees (Top, Left & Bottom)
                { -0.94f, -0.82f }, { -0.94f, -0.45f }, { -0.94f,  0.05f }, { -0.94f,  0.52f }, { -0.94f,  0.86f },
                { -0.72f, -0.94f }, { -0.35f, -0.94f }, {  0.12f, -0.94f }, {  0.55f, -0.94f },
                { -0.75f,  0.94f }, { -0.42f,  0.94f }, {  0.35f,  0.94f }, {  0.68f,  0.94f },
                // Internal landscaped greenbelt trees
                { -0.76f, -0.30f }, { -0.52f, -0.30f }, { -0.18f, -0.30f }, {  0.04f, -0.82f },
                { -0.08f,  0.58f }, { -0.08f,  0.68f }, {  0.04f,  0.58f }, {  0.04f,  0.68f },
                {  0.28f,  0.34f }, {  0.28f,  0.76f }, {  0.76f,  0.18f }, {  0.78f,  0.68f }
        };
        paint.setStyle(Paint.Style.FILL);
        for (int t = 0; t < plantTrees.length; t++) {
            float tx = cx + plantTrees[t][0] * w, ty = cy + plantTrees[t][1] * h;
            float tr = ((t < 13) ? 14f : 9.5f) * dp;
            paint.setColor(Color.parseColor("#44000000"));
            canvas.drawCircle(tx + 3f * dp, ty + 4f * dp, tr, paint);
            paint.setColor(isSunnyDay ? Color.parseColor("#15803D") : Color.parseColor("#064E3B"));
            canvas.drawCircle(tx, ty, tr, paint);
            paint.setColor(isSunnyDay ? Color.parseColor("#4ADE80") : Color.parseColor("#10B981"));
            canvas.drawCircle(tx - tr * 0.25f, ty - tr * 0.25f, tr * 0.55f, paint);
        }

        canvas.restore();

        // Outer Landmark Border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#0F172A"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 26f * dp, 26f * dp, strokePaint);
    }

    /**
     * Renders a 2.5D Hyperboloid Concrete Cooling Tower with water basin, diagonal V-struts,
     * tapered concrete body, red-and-white checkered rim & billowing volumetric steam clouds.
     */
    private void drawHyperboloidCoolingTower25D(Canvas canvas, Paint paint, Paint strokePaint,
                                                float baseX, float baseY, float baseR, float heightShift,
                                                float starPhase, float phaseOff) {
        float topX = baseX - heightShift * 0.12f;
        float topY = baseY - heightShift * 0.48f;
        float topR = baseR * 0.66f;

        // 1. Massive 2.5D Tower Drop Shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawCircle(baseX + 14f * dp, baseY + 16f * dp, baseR * 1.05f, paint);

        // 2. Circular Cooling Water Basin & Concrete Rim Collar
        paint.setColor(Color.parseColor("#94A3B8"));
        canvas.drawCircle(baseX, baseY, baseR * 1.06f, paint);
        paint.setColor(Color.parseColor("#0F3A47"));
        canvas.drawCircle(baseX, baseY, baseR, paint);

        // 3. Diagonal V-Shaped Support Struts Around the Base Perimeter
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(2.0f * dp);
        int struts = 28;
        for (int i = 0; i < struts; i++) {
            double a1 = i * (Math.PI * 2.0 / struts);
            double a2 = (i + 0.5) * (Math.PI * 2.0 / struts);
            float bx1 = baseX + (float) Math.cos(a1) * baseR;
            float by1 = baseY + (float) Math.sin(a1) * baseR;
            float tx1 = baseX + (float) Math.cos(a2) * (baseR * 0.90f);
            float ty1 = baseY + (float) Math.sin(a2) * (baseR * 0.90f) - 4f * dp;
            canvas.drawLine(bx1, by1, tx1, ty1, strokePaint);
        }

        // 4. Hyperboloid Curved Concrete Shell (16 Stacked Concentric Rings with Waist Pinch & 3D Shading)
        int rings = 16;
        paint.setStyle(Paint.Style.FILL);
        for (int r = 0; r <= rings; r++) {
            float t = r / (float) rings;
            float rx = baseX + (topX - baseX) * t;
            float ry = baseY + (topY - baseY) * t;
            // Hyperbolic waist curve: narrows around t=0.68 then flares slightly at top rim
            float pinch = 1.0f - 0.36f * (float) Math.sin(t * Math.PI * 0.78);
            float rad = baseR * 0.92f * pinch;

            int shellCol = isSunnyDay
                    ? Color.rgb((int) (175 + t * 45), (int) (180 + t * 45), (int) (186 + t * 42))
                    : Color.rgb((int) (60 + t * 35), (int) (72 + t * 35), (int) (88 + t * 38));
            paint.setColor(shellCol);
            canvas.drawCircle(rx, ry, rad, paint);
        }

        // Subtle Vertical Concrete Formwork Seam Lines on the Tower Cone
        strokePaint.setColor(Color.parseColor("#28000000"));
        strokePaint.setStrokeWidth(1.2f * dp);
        for (int s = 0; s < 18; s++) {
            double ang = s * (Math.PI * 2.0 / 18.0);
            float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
            canvas.drawLine(baseX + cosA * (baseR * 0.90f), baseY + sinA * (baseR * 0.90f),
                    topX + cosA * topR, topY + sinA * topR, strokePaint);
        }

        // 5. Red-and-White Double Checkered Aviation Warning Band Around the Upper Rim (Image 1!)
        RectF checkRectOuter = new RectF(topX - topR * 1.02f, topY - topR * 1.02f, topX + topR * 1.02f, topY + topR * 1.02f);
        RectF checkRectInner = new RectF(topX - topR * 0.97f, topY - topR * 0.97f, topX + topR * 0.97f, topY + topR * 0.97f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        strokePaint.setStrokeWidth(3.6f * dp);
        int checks = 32;
        float sweep = 360f / checks;
        for (int c = 0; c < checks; c++) {
            strokePaint.setColor((c % 2 == 0) ? Color.parseColor("#DC2626") : Color.WHITE);
            canvas.drawArc(checkRectOuter, c * sweep, sweep, false, strokePaint);
            strokePaint.setColor((c % 2 == 1) ? Color.parseColor("#DC2626") : Color.WHITE);
            canvas.drawArc(checkRectInner, c * sweep, sweep, false, strokePaint);
        }

        // 6. Deep Dark Interior Throat Opening & Upper Concrete Lip
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawCircle(topX, topY, topR * 0.94f, paint);

        paint.setShader(new android.graphics.RadialGradient(
                topX + topR * 0.12f, topY + topR * 0.12f, topR * 0.90f,
                new int[]{ Color.parseColor("#1E293B"), Color.parseColor("#334155"), Color.parseColor("#64748B") },
                new float[]{ 0f, 0.65f, 1f },
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(topX, topY, topR * 0.86f, paint);
        paint.setShader(null);

        // 7. Billowing Volumetric White & Silver-Grey Steam Plumes Rising from the Throat (Image 1!)
        int steamPuffs = 12;
        for (int p = 0; p < steamPuffs; p++) {
            float prog = ((starPhase * 0.24f) + (p / (float) steamPuffs) + phaseOff * 0.15f) % 1.0f;
            float sx = topX + prog * (baseR * 0.45f) + (float) Math.sin(prog * 5f + p + phaseOff) * (8f * dp);
            float sy = topY - prog * (baseR * 1.15f) + (float) Math.cos(prog * 4f + p) * (6f * dp);
            float sRad = topR * (0.42f + prog * 0.48f);
            int alpha = Math.max(0, Math.round((1f - prog * 0.82f) * 210f));

            // Soft grey steam shadow base
            paint.setColor(Color.argb(alpha, 180, 190, 200));
            canvas.drawCircle(sx + 4f * dp, sy + 5f * dp, sRad, paint);

            // Fluffy pure-white sunlit steam top
            paint.setColor(Color.argb(alpha, 248, 250, 252));
            canvas.drawCircle(sx - sRad * 0.15f, sy - sRad * 0.15f, sRad * 0.82f, paint);
        }
        paint.setAlpha(255);
    }

    private void drawNuclearRoadNetwork(Canvas canvas, Paint strokePaint, float cx, float cy, float w, float h,
                                        float rLeft, float rMidL, float rMidR, float rTop, float rUpperM, float rLowerM, float rBot) {
        // Outer loop road
        canvas.drawRoundRect(new RectF(rLeft, rTop, rMidR, rBot), 14f * dp, 14f * dp, strokePaint);
        // Horizontal cross-roads
        canvas.drawLine(rLeft, rUpperM, rMidR, rUpperM, strokePaint);
        canvas.drawLine(rLeft, rLowerM, cx + w * 0.56f, rLowerM, strokePaint);
        // Vertical internal avenues
        canvas.drawLine(rMidL, rTop, rMidL, rBot, strokePaint);
        canvas.drawLine(cx + w * 0.04f, rTop, cx + w * 0.04f, rUpperM, strokePaint);
    }

    private void drawIndustrialBox25D(Canvas canvas, Paint paint, Paint strokePaint,
                                      float bx, float by, float hw, float hh, int wallCol, int roofCol, boolean drawWindows) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#50000000"));
        canvas.drawRoundRect(new RectF(bx - hw + 5f * dp, by - hh + 6f * dp, bx + hw + 5f * dp, by + hh + 8f * dp), 3f * dp, 3f * dp, paint);

        // 2.5D South Facade Wall
        paint.setColor(wallCol);
        canvas.drawRoundRect(new RectF(bx - hw, by - hh + 4f * dp, bx + hw, by + hh + 6f * dp), 3f * dp, 3f * dp, paint);

        if (drawWindows) {
            paint.setColor(isSunnyDay ? Color.parseColor("#BAE6FD") : Color.parseColor("#FDE047"));
            for (float wx = bx - hw + 6f * dp; wx < bx + hw - 6f * dp; wx += 9f * dp) {
                canvas.drawRect(wx, by + hh + 1f * dp, wx + 4.5f * dp, by + hh + 4f * dp, paint);
            }
        }

        // Roof Deck
        paint.setColor(roofCol);
        canvas.drawRoundRect(new RectF(bx - hw, by - hh, bx + hw, by + hh), 3f * dp, 3f * dp, paint);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.2f * dp);
        canvas.drawRoundRect(new RectF(bx - hw, by - hh, bx + hw, by + hh), 3f * dp, 3f * dp, strokePaint);
    }

    private void drawTransmissionPylon25D(Canvas canvas, Paint paint, Paint strokePaint, float px, float py) {
        // 2.5D Ground Shadow of Lattice Pylon
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#38000000"));
        strokePaint.setStrokeWidth(2.0f * dp);
        canvas.drawLine(px + 4f * dp, py + 4f * dp, px + 16f * dp, py - 14f * dp, strokePaint);

        // Steel Lattice Tower Legs & Cross-Arms
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(1.6f * dp);
        canvas.drawLine(px - 6f * dp, py + 6f * dp, px, py - 22f * dp, strokePaint);
        canvas.drawLine(px + 6f * dp, py + 6f * dp, px, py - 22f * dp, strokePaint);
        canvas.drawLine(px - 11f * dp, py - 10f * dp, px + 11f * dp, py - 10f * dp, strokePaint);
        canvas.drawLine(px - 8f * dp, py - 17f * dp, px + 8f * dp, py - 17f * dp, strokePaint);
    }

    private void drawRadiationTrefoil(Canvas canvas, Paint paint, float rx, float ry, float rad) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawCircle(rx, ry, rad, paint);
        paint.setColor(Color.parseColor("#0F172A"));
        RectF trOval = new RectF(rx - rad * 0.82f, ry - rad * 0.82f, rx + rad * 0.82f, rx == rx ? ry + rad * 0.82f : ry);
        for (int b = 0; b < 3; b++) {
            canvas.drawArc(trOval, b * 120f - 30f, 60f, true, paint);
        }
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawCircle(rx, ry, rad * 0.28f, paint);
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawCircle(rx, ry, rad * 0.16f, paint);
    }


    /**
     * Renders the 2.5D International Football Stadium matching the reference model:
     * - Outer stadium plaza with fan queues, trees & deep 2.5D drop shadow
     * - Tri-color outer stadium roof shell: Crisp White (top), Vibrant Blue (middle), Crimson Red (bottom)
     * - 6-tier concentric spectator bowl packed with fans, blue/red horizontal barrier rails, waving flags & camera flashes
     * - 4 Cardinal Stepped Staircases & Recessed Vomitory Tunnels (North, South, East, West)
     * - Dusty-rose / terracotta athletics track apron with lane markings, photographers & stewards
     * - Horizontal FIFA football pitch with 14 mowed grass stripes, penalty boxes, D-arcs, corner flags & 3D goals
     * - Live animated 11v11 football match (Red/White vs Cyan/Yellow), referee, linesmen & passing/shooting ball
     * - East-side 3D angled digital scoreboard with glowing green 7-segment display
     * - 4 triple-pod floodlight gantries (2 North, 2 South) casting volumetric light cones across the stands & pitch
     */
    private void drawDetailedFootballStadium25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int plazaCol      = isSunnyDay ? Color.parseColor("#9FA6AD") : (isMoonlightMode ? Color.parseColor("#1E293B") : Color.parseColor("#0F172A"));
        int plazaInnerCol = isSunnyDay ? Color.parseColor("#B5BCC4") : (isMoonlightMode ? Color.parseColor("#283548") : Color.parseColor("#182232"));
        int trackPinkCol  = isSunnyDay ? Color.parseColor("#C68B8E") : Color.parseColor("#7D4E52");
        int trackCarpet   = isSunnyDay ? Color.parseColor("#9E6266") : Color.parseColor("#593437");
        int turfDarkCol   = isSunnyDay ? Color.parseColor("#558B2F") : (isMoonlightMode ? Color.parseColor("#1B4322") : Color.parseColor("#123017"));
        int turfLightCol  = isSunnyDay ? Color.parseColor("#689F38") : (isMoonlightMode ? Color.parseColor("#23532B") : Color.parseColor("#183D1E"));

        // =========================================================================
        // 1. OUTER STADIUM CONCOURSE PLAZA, WALKWAYS & FAN QUEUES
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(cx - w + 10f * dp, cy - h + 12f * dp, cx + w + 10f * dp, cy + h + 12f * dp), 28f * dp, 28f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(plazaCol);
        canvas.drawRoundRect(tempRect, 28f * dp, 28f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 28f * dp, 28f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Inner Concourse Apron
        paint.setColor(plazaInnerCol);
        canvas.drawRoundRect(new RectF(cx - w * 0.96f, cy - h * 0.94f, cx + w * 0.96f, cy + h * 0.94f), 42f * dp, 42f * dp, paint);

        // Corner Plaza Landscaping Pockets & Trees
        float[][] cornerTrees = {
                { -0.86f, -0.82f }, { -0.74f, -0.88f }, { 0.86f, -0.82f }, { 0.74f, -0.88f },
                { -0.86f,  0.82f }, { -0.74f,  0.88f }, { 0.86f,  0.82f }, { 0.74f,  0.88f }
        };
        for (float[] ct : cornerTrees) {
            float tx = cx + ct[0] * w, ty = cy + ct[1] * h;
            paint.setColor(Color.parseColor("#38000000"));
            canvas.drawCircle(tx + 3.5f * dp, ty + 4.5f * dp, 10f * dp, paint);
            paint.setColor(Color.parseColor("#2E5A27"));
            canvas.drawCircle(tx, ty, 9.5f * dp, paint);
            paint.setColor(Color.parseColor("#558B2F"));
            canvas.drawCircle(tx - 2f * dp, ty - 2f * dp, 6f * dp, paint);
        }

        // =========================================================================
        // 2. TRI-COLOR OUTER STADIUM SHELL (WHITE TOP / BLUE MID / RED BOTTOM)
        // =========================================================================
        float stdW = w * 0.92f, stdH = h * 0.82f;
        float stdCorner = stdH * 0.58f;
        RectF outerShellRect = new RectF(cx - stdW, cy - stdH, cx + stdW, cy + stdH);

        // 2.5D Stadium Elevation Drop Shadow
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#58000000"));
        canvas.drawRoundRect(new RectF(outerShellRect.left + 14f * dp, outerShellRect.top + 16f * dp, outerShellRect.right + 14f * dp, outerShellRect.bottom + 16f * dp), stdCorner, stdCorner, paint);

        // Clip to the Outer Stadium Capsule to Render the Tri-Color Horizontal Bands
        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(outerShellRect, stdCorner, stdCorner, Path.Direction.CW);
        canvas.clipPath(tempPath);

        float bandSplit1 = cy - stdH * 0.34f;
        float bandSplit2 = cy + stdH * 0.34f;

        // Top Band: Crisp White Roof Rim
        paint.setColor(isSunnyDay ? Color.parseColor("#F1F5F9") : Color.parseColor("#CBD5E1"));
        canvas.drawRect(cx - stdW, cy - stdH, cx + stdW, bandSplit1, paint);

        // Middle Band: Vibrant Azure / Sky Blue Roof Rim
        paint.setColor(isSunnyDay ? Color.parseColor("#0088DD") : Color.parseColor("#0284C7"));
        canvas.drawRect(cx - stdW, bandSplit1, cx + stdW, bandSplit2, paint);

        // Bottom Band: Crimson Red Roof Rim
        paint.setColor(isSunnyDay ? Color.parseColor("#DC2626") : Color.parseColor("#B91C1C"));
        canvas.drawRect(cx - stdW, bandSplit2, cx + stdW, cy + stdH, paint);

        // Subtle Radial Roof Panel Seam Lines across the Outer Shell
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#22000000"));
        strokePaint.setStrokeWidth(1.2f * dp);
        for (int i = 0; i < 36; i++) {
            double ang = i * (Math.PI * 2.0 / 36.0);
            canvas.drawLine(cx + (float) Math.cos(ang) * (stdW * 0.65f), cy + (float) Math.sin(ang) * (stdH * 0.65f),
                    cx + (float) Math.cos(ang) * (stdW * 1.05f), cy + (float) Math.sin(ang) * (stdH * 1.05f), strokePaint);
        }
        canvas.restore(); // End outer tri-color shell clip

        // Outer Shell Metallic Perimeter Rim Stroke
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(2.6f * dp);
        canvas.drawRoundRect(outerShellRect, stdCorner, stdCorner, strokePaint);

        // =========================================================================
        // 3. MULTI-TIERED SPECTATOR SEATING BOWL, BARRIER RAILS, FLAGS & FLASHES
        // =========================================================================
        float bowlW = stdW * 0.86f, bowlH = stdH * 0.83f;
        float bowlCorner = bowlH * 0.56f;
        RectF bowlBaseRect = new RectF(cx - bowlW, cy - bowlH, cx + bowlW, cy + bowlH);

        // Dark Recessed Bowl Concrete Base
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#262936"));
        canvas.drawRoundRect(bowlBaseRect, bowlCorner, bowlCorner, paint);

        // 6 Concentric Stepped Seating Tiers (Alternating Concrete & Crowd Rows)
        for (int tier = 0; tier < 6; tier++) {
            float tFrac = 1.0f - tier * 0.052f;
            float tw = bowlW * tFrac, th = bowlH * tFrac;
            float tc = bowlCorner * tFrac;
            RectF tierRect = new RectF(cx - tw, cy - th, cx + tw, cy + th);

            // Concrete step riser
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor((tier % 2 == 0) ? Color.parseColor("#94A3B8") : Color.parseColor("#64748B"));
            strokePaint.setStrokeWidth(3.2f * dp);
            canvas.drawRoundRect(tierRect, tc, tc, strokePaint);

            // Dark walkway gap between tiers
            strokePaint.setColor(Color.parseColor("#331E293B"));
            strokePaint.setStrokeWidth(1.4f * dp);
            canvas.drawRoundRect(new RectF(cx - tw + 2f * dp, cy - th + 2f * dp, cx + tw - 2f * dp, cy + th - 2f * dp), tc, tc, strokePaint);
        }

        // Radial Concrete Stair Aisles Dividing the Seating Sections
        strokePaint.setColor(Color.parseColor("#88CBD5E1"));
        strokePaint.setStrokeWidth(2.2f * dp);
        for (int a = 0; a < 28; a++) {
            if (a % 7 == 0) continue; // Skip the 4 cardinal tunnel positions
            double ang = a * (Math.PI * 2.0 / 28.0);
            float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
            canvas.drawLine(cx + cosA * (bowlW * 0.68f), cy + sinA * (bowlH * 0.68f),
                    cx + cosA * (bowlW * 0.99f), cy + sinA * (bowlH * 0.99f), strokePaint);
        }

        // Horizontal Blue Safety Barrier Lines on North Stand & Red Barrier Lines on South Stand (matching Image 3)
        strokePaint.setStrokeWidth(2.0f * dp);
        strokePaint.setColor(Color.parseColor("#38BDF8")); // Cyan-blue rails on upper stand
        canvas.drawLine(cx - bowlW * 0.52f, cy - bowlH * 0.88f, cx + bowlW * 0.52f, cy - bowlH * 0.88f, strokePaint);
        canvas.drawLine(cx - bowlW * 0.56f, cy - bowlH * 0.78f, cx + bowlW * 0.56f, cy - bowlH * 0.78f, strokePaint);

        strokePaint.setColor(Color.parseColor("#EF4444")); // Red rails on lower stand
        canvas.drawLine(cx - bowlW * 0.52f, cy + bowlH * 0.88f, cx + bowlW * 0.52f, cy + bowlH * 0.88f, strokePaint);
        canvas.drawLine(cx - bowlW * 0.56f, cy + bowlH * 0.78f, cx + bowlW * 0.56f, cy + bowlH * 0.78f, strokePaint);

        // Packed Multi-Colored Spectator Crowd Dots, Waving Team Flags & Camera Flashes
        int[] fanColors = {
                Color.parseColor("#EF4444"), Color.parseColor("#38BDF8"), Color.parseColor("#FACC15"),
                Color.parseColor("#FFFFFF"), Color.parseColor("#22C55E"), Color.parseColor("#F97316"),
                Color.parseColor("#E879F9")
        };
        paint.setStyle(Paint.Style.FILL);
        for (int tier = 0; tier < 6; tier++) {
            float rScale = 0.98f - tier * 0.052f;
            int dotsInTier = 76 - tier * 4;
            for (int d = 0; d < dotsInTier; d++) {
                double theta = d * (Math.PI * 2.0 / dotsInTier) + tier * 0.15;
                float cosT = (float) Math.cos(theta), sinT = (float) Math.sin(theta);

                // Skip the 4 cardinal staircase portals
                if ((Math.abs(cosT) < 0.13f && Math.abs(sinT) > 0.90f) || (Math.abs(sinT) < 0.15f && Math.abs(cosT) > 0.90f)) {
                    continue;
                }

                // Map onto stadium super-ellipse contour
                float stretch = 1.0f + 0.14f * (float) Math.pow(Math.sin(2.0 * theta), 2.0);
                float fx = cx + cosT * bowlW * rScale * Math.min(1.02f, stretch * 0.92f);
                float fy = cy + sinT * bowlH * rScale * Math.min(1.02f, stretch * 0.92f);

                // Mexican wave bounce
                float waveBump = (float) Math.sin(starPhase * 4.5f - theta * 2.0) * 1.2f * dp;
                paint.setColor(fanColors[(d * 3 + tier * 5) % fanColors.length]);
                canvas.drawCircle(fx, fy + (waveBump > 0 ? -waveBump : 0f), 1.8f * dp, paint);

                // Waving Blue & Red Supporter Flags / Banners in the Stands (matching Image 3)
                if ((d + tier * 3) % 11 == 0) {
                    boolean isUpperOrRight = (sinT < 0.15f);
                    paint.setColor(isUpperOrRight ? Color.parseColor("#00A8E8") : Color.parseColor("#E11D48"));
                    float flagSwing = (float) Math.sin(starPhase * 6.0f + d) * 3.0f * dp;
                    canvas.drawRoundRect(new RectF(fx - 3.5f * dp + flagSwing, fy - 2.0f * dp, fx + 3.5f * dp + flagSwing, fy + 2.0f * dp), 1f * dp, 1f * dp, paint);
                }

                // Random Camera Strobe Flashes in the Crowd
                if (((d * 7 + tier * 13) % 29 == 0) && Math.sin(starPhase * 8.0f + d) > 0.93) {
                    paint.setColor(Color.parseColor("#99FFFFFF"));
                    canvas.drawCircle(fx, fy, 4.8f * dp, paint);
                    paint.setColor(Color.WHITE);
                    canvas.drawCircle(fx, fy, 2.0f * dp, paint);
                }
            }
        }

        // =========================================================================
        // 4. TERRACOTTA-PINK ATHLETICS TRACK APRON & 4 CARDINAL STAIR/TUNNEL PORTALS
        // =========================================================================
        float trackW = bowlW * 0.68f, trackH = bowlH * 0.66f;
        float trackCorner = trackH * 0.62f;
        RectF trackRect = new RectF(cx - trackW, cy - trackH, cx + trackW, cy + trackH);

        // Outer Concrete Parapet Wall around Track
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRoundRect(new RectF(trackRect.left - 5f * dp, trackRect.top - 5f * dp, trackRect.right + 5f * dp, trackRect.bottom + 5f * dp), trackCorner + 4f * dp, trackCorner + 4f * dp, paint);

        // Dusty-Rose / Terracotta Running Track & Perimeter Apron
        paint.setColor(trackPinkCol);
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, paint);

        // Subtle Running Track Lane Rings
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#44FFFFFF"));
        strokePaint.setStrokeWidth(1.1f * dp);
        canvas.drawRoundRect(new RectF(trackRect.left + 5f * dp, trackRect.top + 5f * dp, trackRect.right - 5f * dp, trackRect.bottom - 5f * dp), trackCorner - 4f * dp, trackCorner - 4f * dp, strokePaint);
        canvas.drawRoundRect(new RectF(trackRect.left + 10f * dp, trackRect.top + 10f * dp, trackRect.right - 10f * dp, trackRect.bottom - 10f * dp), trackCorner - 8f * dp, trackCorner - 8f * dp, strokePaint);

        // 4 Cardinal Stepped Stairways & Recessed Vomitory Tunnels (North, South, West, East)
        drawCardinalTunnelPortal(canvas, paint, strokePaint, cx, cy - trackH, bowlH - trackH, 0f, trackCarpet);
        drawCardinalTunnelPortal(canvas, paint, strokePaint, cx, cy + trackH, bowlH - trackH, 180f, trackCarpet);
        drawCardinalTunnelPortal(canvas, paint, strokePaint, cx - trackW, cy, bowlW - trackW, 270f, trackCarpet);
        drawCardinalTunnelPortal(canvas, paint, strokePaint, cx + trackW, cy, bowlW - trackW, 90f, trackCarpet);

        // =========================================================================
        // 5. FIFA REGULATION HORIZONTAL FOOTBALL PITCH, GOALS & LIVE 11v11 MATCH
        // =========================================================================
        float pitchW = trackW * 0.72f, pitchH = trackH * 0.76f;
        RectF pitchRect = new RectF(cx - pitchW, cy - pitchH, cx + pitchW, cy + pitchH);

        // Base Emerald Football Turf
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(turfDarkCol);
        canvas.drawRect(pitchRect, paint);

        // 14 Vertical Mowed Grass Stripes across the Pitch
        int turfStripes = 14;
        float stripeStep = (pitchW * 2f) / turfStripes;
        paint.setColor(turfLightCol);
        for (int s = 0; s < turfStripes; s += 2) {
            float sx = pitchRect.left + s * stripeStep;
            canvas.drawRect(sx, pitchRect.top, sx + stripeStep, pitchRect.bottom, paint);
        }

        // Animated Digital LED Sponsor Boards along the Top & Bottom Touchlines
        int[] ledCols = { Color.parseColor("#EF4444"), Color.parseColor("#38BDF8"), Color.parseColor("#FACC15"), Color.WHITE, Color.parseColor("#10B981") };
        int ledPhase = (int) (starPhase * 2.2f);
        for (int b = 0; b < 16; b++) {
            float bx = pitchRect.left + b * ((pitchW * 2f) / 16f);
            float bw = (pitchW * 2f) / 16f - 2f * dp;
            paint.setColor(ledCols[(b + ledPhase) % ledCols.length]);
            canvas.drawRect(bx, pitchRect.top - 4.0f * dp, bx + bw, pitchRect.top - 1.8f * dp, paint);
            canvas.drawRect(bx, pitchRect.bottom + 1.8f * dp, bx + bw, pitchRect.bottom + 4.0f * dp, paint);
        }

        // Crisp White FIFA Pitch Markings
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#F1F5F9"));
        strokePaint.setStrokeWidth(1.5f * dp);

        // Outer Touchlines, Goal Lines & Halfway Line
        canvas.drawRect(pitchRect, strokePaint);
        canvas.drawLine(cx, pitchRect.top, cx, pitchRect.bottom, strokePaint);

        // Center Circle & Center Kick-Off Spot
        float centerR = pitchH * 0.28f;
        canvas.drawCircle(cx, cy, centerR, strokePaint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 2.0f * dp, paint);

        // Left & Right 18-Yard Penalty Boxes, 6-Yard Goal Boxes & Penalty D-Arcs
        float penBoxW = pitchW * 0.28f, penBoxH = pitchH * 0.56f;
        float goalBoxW = pitchW * 0.11f, goalBoxH = pitchH * 0.26f;
        float penSpotOff = pitchW * 0.20f;
        float arcR = pitchH * 0.22f;

        // Left Penalty Area
        canvas.drawRect(pitchRect.left, cy - penBoxH, pitchRect.left + penBoxW, cy + penBoxH, strokePaint);
        canvas.drawRect(pitchRect.left, cy - goalBoxH, pitchRect.left + goalBoxW, cy + goalBoxH, strokePaint);
        canvas.drawCircle(pitchRect.left + penSpotOff, cy, 1.6f * dp, paint);
        canvas.drawArc(new RectF(pitchRect.left + penSpotOff - arcR, cy - arcR, pitchRect.left + penSpotOff + arcR, cy + arcR), -52f, 104f, false, strokePaint);

        // Right Penalty Area
        canvas.drawRect(pitchRect.right - penBoxW, cy - penBoxH, pitchRect.right, cy + penBoxH, strokePaint);
        canvas.drawRect(pitchRect.right - goalBoxW, cy - goalBoxH, pitchRect.right, cy + goalBoxH, strokePaint);
        canvas.drawCircle(pitchRect.right - penSpotOff, cy, 1.6f * dp, paint);
        canvas.drawArc(new RectF(pitchRect.right - penSpotOff - arcR, cy - arcR, pitchRect.right - penSpotOff + arcR, cy + arcR), 128f, 104f, false, strokePaint);

        // 4 Corner Arcs & Fluttering Corner Flags
        float cArc = 5.5f * dp;
        canvas.drawArc(new RectF(pitchRect.left - cArc, pitchRect.top - cArc, pitchRect.left + cArc, pitchRect.top + cArc), 0f, 90f, false, strokePaint);
        canvas.drawArc(new RectF(pitchRect.right - cArc, pitchRect.top - cArc, pitchRect.right + cArc, pitchRect.top + cArc), 90f, 90f, false, strokePaint);
        canvas.drawArc(new RectF(pitchRect.right - cArc, pitchRect.bottom - cArc, pitchRect.right + cArc, pitchRect.bottom + cArc), 180f, 90f, false, strokePaint);
        canvas.drawArc(new RectF(pitchRect.left - cArc, pitchRect.bottom - cArc, pitchRect.left + cArc, pitchRect.bottom + cArc), 270f, 90f, false, strokePaint);

        // 3D Left & Right Football Goals with Net Mesh
        float goalDepth = 7.0f * dp, goalHalfSpan = goalBoxH * 0.58f;
        paint.setColor(Color.parseColor("#38000000")); // Goal net shadow
        canvas.drawRect(pitchRect.left - goalDepth + 2f * dp, cy - goalHalfSpan + 2f * dp, pitchRect.left, cy + goalHalfSpan + 2f * dp, paint);
        canvas.drawRect(pitchRect.right, cy - goalHalfSpan + 2f * dp, pitchRect.right + goalDepth + 2f * dp, cy + goalHalfSpan + 2f * dp, paint);

        paint.setColor(Color.parseColor("#55FFFFFF")); // Translucent white net mesh
        canvas.drawRect(pitchRect.left - goalDepth, cy - goalHalfSpan, pitchRect.left, cy + goalHalfSpan, paint);
        canvas.drawRect(pitchRect.right, cy - goalHalfSpan, pitchRect.right + goalDepth, cy + goalHalfSpan, paint);

        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(2.0f * dp);
        canvas.drawRect(pitchRect.left - goalDepth, cy - goalHalfSpan, pitchRect.left, cy + goalHalfSpan, strokePaint);
        canvas.drawRect(pitchRect.right, cy - goalHalfSpan, pitchRect.right + goalDepth, cy + goalHalfSpan, strokePaint);

        // =========================================================================
        // 6. LIVE ANIMATED 11v11 FOOTBALL MATCH, REFEREES, PASSING & GOAL SHOTS
        // =========================================================================
        // Base Outfield Formations: Team A (Red & White) vs Team B (Cyan-Blue & Yellow)
        float[][] teamRedRel = {
                { -0.68f, -0.45f }, { -0.66f,  0.08f }, { -0.64f,  0.52f }, { -0.38f, -0.32f }, { -0.32f,  0.35f },
                { -0.18f, -0.05f }, { -0.10f,  0.58f }, { -0.04f, -0.02f }, {  0.14f,  0.02f }, {  0.22f,  0.34f }
        };
        float[][] teamBlueRel = {
                { -0.28f, -0.62f }, { -0.14f, -0.25f }, {  0.04f, -0.42f }, {  0.16f, -0.52f }, {  0.24f, -0.12f },
                {  0.32f,  0.04f }, {  0.42f, -0.22f }, {  0.38f,  0.42f }, {  0.52f,  0.58f }, {  0.64f, -0.28f }
        };

        float shiftX = (float) Math.sin(starPhase * 0.9f) * (pitchW * 0.08f);
        float shiftY = (float) Math.cos(starPhase * 1.1f) * (pitchH * 0.08f);

        // Draw 10 Red Outfield Players
        for (int i = 0; i < teamRedRel.length; i++) {
            float px = cx + teamRedRel[i][0] * pitchW + shiftX * ((i % 2 == 0) ? 1f : -0.7f);
            float py = cy + teamRedRel[i][1] * pitchH + shiftY * ((i % 3 == 0) ? 1f : -0.8f);
            drawFootballPlayer25D(canvas, paint, px, py, Color.parseColor("#DC2626"), Color.WHITE);
        }

        // Draw 10 Cyan/Yellow Outfield Players
        for (int i = 0; i < teamBlueRel.length; i++) {
            float px = cx + teamBlueRel[i][0] * pitchW - shiftX * ((i % 2 == 0) ? 0.9f : -0.6f);
            float py = cy + teamBlueRel[i][1] * pitchH - shiftY * ((i % 3 == 0) ? 0.8f : -0.9f);
            drawFootballPlayer25D(canvas, paint, px, py, Color.parseColor("#0284C7"), Color.parseColor("#FACC15"));
        }

        // Draw 2 Goalkeepers Patrolling the Goal Lines
        float gkDive = (float) Math.sin(starPhase * 3.2f) * (goalHalfSpan * 0.65f);
        drawFootballPlayer25D(canvas, paint, pitchRect.left + 3.5f * dp, cy + gkDive, Color.parseColor("#16A34A"), Color.WHITE);
        drawFootballPlayer25D(canvas, paint, pitchRect.right - 3.5f * dp, cy - gkDive, Color.parseColor("#9333EA"), Color.parseColor("#FACC15"));

        // Draw Main Referee (Black Kit) & 2 Trackside Photographers / Officials (matching Image 3 dots on pink apron)
        drawFootballPlayer25D(canvas, paint, cx - pitchW * 0.12f + shiftX * 0.5f, cy + pitchH * 0.62f, Color.parseColor("#1E293B"), Color.parseColor("#94A3B8"));
        drawFootballPlayer25D(canvas, paint, pitchRect.left - 14f * dp, cy - pitchH * 0.42f, Color.parseColor("#334155"), Color.WHITE);
        drawFootballPlayer25D(canvas, paint, pitchRect.right + 16f * dp, cy + pitchH * 0.48f, Color.parseColor("#334155"), Color.WHITE);

        // Animated Multi-Pass & Goal-Shot Ball Trajectory
        float[][] ballWaypoints = {
                { -0.18f, -0.05f }, { -0.04f, -0.02f }, { 0.14f, 0.02f }, { 0.32f, 0.04f },
                {  0.22f,  0.34f }, {  0.64f, -0.28f }, { 0.98f, 0.00f }, { 0.00f, 0.00f }
        };
        float rawStep = (starPhase * 1.15f) % ballWaypoints.length;
        int wpA = (int) rawStep;
        int wpB = (wpA + 1) % ballWaypoints.length;
        float frac = rawStep - wpA;

        float bx1 = cx + ballWaypoints[wpA][0] * pitchW, by1 = cy + ballWaypoints[wpA][1] * pitchH;
        float bx2 = cx + ballWaypoints[wpB][0] * pitchW, by2 = cy + ballWaypoints[wpB][1] * pitchH;
        float ballX = bx1 + (bx2 - bx1) * frac;
        float ballY = by1 + (by2 - by1) * frac;

        // Ball motion streak
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.argb(145, 255, 255, 255));
        strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawLine(bx1 + (bx2 - bx1) * Math.max(0f, frac - 0.25f), by1 + (by2 - by1) * Math.max(0f, frac - 0.25f), ballX, ballY, strokePaint);

        // Football & Goal Net Ripple Shockwave when Ball Hits the Right Goal (wpA == 6)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawCircle(ballX + 1.2f * dp, ballY + 1.5f * dp, 2.2f * dp, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(ballX, ballY, 2.2f * dp, paint);

        if (wpA == 6) {
            strokePaint.setColor(Color.argb(Math.max(0, (int) ((1f - frac) * 235f)), 250, 204, 21));
            strokePaint.setStrokeWidth(2.6f * dp);
            canvas.drawCircle(pitchRect.right, cy, (4f + frac * 22f) * dp, strokePaint);
        }

        // =========================================================================
        // 7. EAST-SIDE GIANT 2.5D DIGITAL SCOREBOARD ("0 : 0")
        // =========================================================================
        float sbX = cx + bowlW * 0.90f, sbY = cy;
        canvas.save();
        canvas.translate(sbX, sbY);
        // 3D Angled Trapezoid Scoreboard Housing (matching right edge of Image 3)
        tempPath.reset();
        tempPath.moveTo(-10f * dp, -34f * dp);
        tempPath.lineTo(8f * dp, -42f * dp);
        tempPath.lineTo(8f * dp, 42f * dp);
        tempPath.lineTo(-10f * dp, 34f * dp);
        tempPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.save();
        canvas.translate(4f * dp, 5f * dp);
        canvas.drawPath(tempPath, paint);
        canvas.restore();

        paint.setColor(Color.parseColor("#475569"));
        canvas.drawPath(tempPath, paint);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Dark LED Screen Inset
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRoundRect(new RectF(-7f * dp, -30f * dp, 5f * dp, 30f * dp), 2f * dp, 2f * dp, paint);

        // Glowing Neon-Green Digital "0 : 0" Digits (Vertically stacked like Image 3)
        int ledGreen = (wpA == 6 && ((int) (starPhase * 10f) % 2 == 0)) ? Color.parseColor("#FACC15") : Color.parseColor("#84CC16");
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(ledGreen);
        strokePaint.setStrokeWidth(2.4f * dp);
        // Top "0"
        canvas.drawRoundRect(new RectF(-4.2f * dp, -23f * dp, 2.2f * dp, -7f * dp), 1.5f * dp, 1.5f * dp, strokePaint);
        // Center Colon ":"
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ledGreen);
        canvas.drawCircle(-2.8f * dp, 0f, 1.5f * dp, paint);
        canvas.drawCircle(1.2f * dp, 0f, 1.5f * dp, paint);
        // Bottom "0"
        canvas.drawRoundRect(new RectF(-4.2f * dp, 7f * dp, 2.2f * dp, 23f * dp), 1.5f * dp, 1.5f * dp, strokePaint);
        canvas.restore();

        // =========================================================================
        // 8. 4 TRIPLE-POD FLOODLIGHT TOWERS & VOLUMETRIC LIGHT CONES (NORTH & SOUTH)
        // =========================================================================
        float[][] lightBanks = {
                { cx - bowlW * 0.34f, cy - bowlH * 0.96f,  1f }, // Top-Left Floodlight Bank
                { cx + bowlW * 0.30f, cy - bowlH * 0.96f,  1f }, // Top-Right Floodlight Bank
                { cx - bowlW * 0.34f, cy + bowlH * 0.96f, -1f }, // Bottom-Left Floodlight Bank
                { cx + bowlW * 0.30f, cy + bowlH * 0.96f, -1f }  // Bottom-Right Floodlight Bank
        };

        for (float[] lb : lightBanks) {
            float lx = lb[0], ly = lb[1], dirY = lb[2];

            // Volumetric Translucent Light Cones Projecting Across the Crowd & Pitch
            paint.setStyle(Paint.Style.FILL);
            int coneAlpha = isSunnyDay ? 42 : 76;
            paint.setColor(Color.argb(coneAlpha, 224, 242, 254));
            for (int pod = -1; pod <= 1; pod++) {
                float px = lx + pod * 11f * dp;
                tempPath.reset();
                tempPath.moveTo(px - 3.5f * dp, ly);
                tempPath.lineTo(px + 3.5f * dp, ly);
                tempPath.lineTo(px + (pod * 14f + 28f) * dp, ly + dirY * (bowlH * 0.56f));
                tempPath.lineTo(px + (pod * 14f - 22f) * dp, ly + dirY * (bowlH * 0.56f));
                tempPath.close();
                canvas.drawPath(tempPath, paint);
            }

            // Dark Steel Gantry Crossbar
            paint.setColor(Color.parseColor("#475569"));
            canvas.drawRoundRect(new RectF(lx - 18f * dp, ly - 3.5f * dp, lx + 18f * dp, ly + 3.5f * dp), 2f * dp, 2f * dp, paint);

            // 3 Individual Metallic Projector Pods per Floodlight Bank
            for (int pod = -1; pod <= 1; pod++) {
                float px = lx + pod * 11f * dp;
                paint.setColor(Color.parseColor("#64748B"));
                canvas.drawRoundRect(new RectF(px - 4.2f * dp, ly - 5.5f * dp, px + 4.2f * dp, ly + 5.5f * dp), 2f * dp, 2f * dp, paint);
                paint.setColor(Color.parseColor("#94A3B8"));
                canvas.drawCircle(px, ly - dirY * 1.5f * dp, 3.0f * dp, paint);
                // Bright Glowing Halogen/LED Emitter Face
                paint.setColor(Color.parseColor("#F8FAFC"));
                canvas.drawCircle(px, ly + dirY * 2.5f * dp, 2.4f * dp, paint);
            }
        }

        canvas.restore();

        // Crisp Outer Stadium Estate Border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1E293B"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 28f * dp, 28f * dp, strokePaint);
    }

    /**
     * Draws one of the 4 cardinal stepped staircase & recessed vomitory tunnel portals (North, South, East, West).
     */
    private void drawCardinalTunnelPortal(Canvas canvas, Paint paint, Paint strokePaint, float portX, float portY, float depth, float rotDeg, int carpetCol) {
        canvas.save();
        canvas.translate(portX, portY);
        canvas.rotate(rotDeg);

        float halfSpan = 22f * dp;
        float tunnelHalfW = 7.5f * dp;

        // Dark-Rose Entry Carpet Extending Inward onto the Track Apron
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(carpetCol);
        canvas.drawRect(-tunnelHalfW * 1.35f, 0f, tunnelHalfW * 1.35f, 16f * dp, paint);

        // Twin Light-Grey Stepped Concrete Staircases Flanking the Central Tunnel
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRect(-halfSpan, -depth, -tunnelHalfW, 2f * dp, paint);
        canvas.drawRect(tunnelHalfW, -depth, halfSpan, 2f * dp, paint);

        // Individual Horizontal Stair Treads
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.2f * dp);
        for (float sy = -depth + 4f * dp; sy < 0f; sy += 4.2f * dp) {
            canvas.drawLine(-halfSpan + 1.5f * dp, sy, -tunnelHalfW - 1.5f * dp, sy, strokePaint);
            canvas.drawLine(tunnelHalfW + 1.5f * dp, sy, halfSpan - 1.5f * dp, sy, strokePaint);
        }

        // Central Recessed Dark Vomitory Tunnel & White Portal Frame
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F1F5F9"));
        canvas.drawRect(-tunnelHalfW - 2.2f * dp, -depth * 0.48f, tunnelHalfW + 2.2f * dp, 3f * dp, paint);
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawRect(-tunnelHalfW, -depth * 0.42f, tunnelHalfW, 3f * dp, paint);

        canvas.restore();
    }

    private void drawFootballPlayer25D(Canvas canvas, Paint paint, float x, float y, int shirtCol, int accentCol) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawCircle(x + 1.2f * dp, y + 1.5f * dp, 2.7f * dp, paint);
        paint.setColor(shirtCol);
        canvas.drawCircle(x, y, 2.6f * dp, paint);
        paint.setColor(accentCol);
        canvas.drawCircle(x - 0.5f * dp, y - 0.5f * dp, 1.3f * dp, paint);
    }

    /**
     * Renders the Ultra-Detailed 10x-Area 2.5D Barringer Asteroid Impact Crater Mega-Zone:
     * - Volumetric RadialGradient depth shading, multi-layered desert badlands, mesas & secondary impact pits
     * - Organic multi-harmonic inner floor contours (no flat ovals or wheel-spoke lines)
     * - 90 scalloped talus scree lobes around the inner cliff toe + 3D polygonal rock outcrops
     * - Authentic hook-shaped Barringer central silica playa + colossal tectonic magma rift abyss,
     *   5 shattered 3D iron-nickel asteroid monoliths, billowing ash clouds & switchback mine trail.
     */
    private void drawDetailedAsteroidCrater25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int plateauDeepCol  = isSunnyDay ? Color.parseColor("#783A1E") : (isMoonlightMode ? Color.parseColor("#180E0A") : Color.parseColor("#0E0806"));
        int desertBaseCol   = isSunnyDay ? Color.parseColor("#984E2C") : (isMoonlightMode ? Color.parseColor("#24150F") : Color.parseColor("#150C09"));
        int desertMidCol    = isSunnyDay ? Color.parseColor("#B4653B") : (isMoonlightMode ? Color.parseColor("#321E16") : Color.parseColor("#1E120D"));
        int desertLightCol  = isSunnyDay ? Color.parseColor("#CC8052") : (isMoonlightMode ? Color.parseColor("#422A1F") : Color.parseColor("#291912"));
        int alkaliTanCol    = isSunnyDay ? Color.parseColor("#E4BF97") : (isMoonlightMode ? Color.parseColor("#614C3A") : Color.parseColor("#403125"));
        int rimWallDark     = isSunnyDay ? Color.parseColor("#36170C") : Color.parseColor("#100704");
        int rimWallMid      = isSunnyDay ? Color.parseColor("#5E2D1A") : Color.parseColor("#211009");
        int moenkopiRedCol  = isSunnyDay ? Color.parseColor("#8A3920") : Color.parseColor("#33150C");
        int kaibabBuffCol   = isSunnyDay ? Color.parseColor("#D49A68") : Color.parseColor("#543B28");
        int craterFloorCol  = isSunnyDay ? Color.parseColor("#9E5934") : Color.parseColor("#331C12");
        int playaCreamCol   = isSunnyDay ? Color.parseColor("#DDD0BE") : Color.parseColor("#6E6256");

        // =========================================================================
        // 1. 3x3 MEGA-ZONE BADLANDS SOIL, ERODED MESAS, ARROYOS & SATELLITE CRATERS
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#66000000"));
        canvas.drawRoundRect(new RectF(cx - w + 14f * dp, cy - h + 16f * dp, cx + w + 14f * dp, cy + h + 16f * dp), 38f * dp, 38f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(plateauDeepCol);
        canvas.drawRoundRect(tempRect, 38f * dp, 38f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 38f * dp, 38f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        float craterCX = cx + w * 0.04f, craterCY = cy - h * 0.04f;
        float rimRX = w * 0.55f, rimRY = h * 0.44f;

        // Organic Badlands Soil Contours (replaces plain ovals)
        buildEjectaBlanketPath(tempPath, cx, cy, w * 0.96f, h * 0.94f, 0.4f);
        paint.setColor(desertBaseCol);
        canvas.drawPath(tempPath, paint);

        buildEjectaBlanketPath(tempPath, craterCX, craterCY, rimRX * 1.62f, rimRY * 1.68f, 1.1f);
        paint.setColor(desertMidCol);
        canvas.drawPath(tempPath, paint);

        // Stepped Sedimentary Sandstone Mesa Outcrops on the Outer Desert Soil
        float[][] outerMesas = {
                { -0.78f, -0.76f, 0.18f, 0.11f, 0.5f },
                {  0.74f, -0.78f, 0.20f, 0.12f, 1.8f },
                { -0.82f,  0.18f, 0.14f, 0.18f, 2.9f },
                {  0.82f,  0.42f, 0.16f, 0.14f, 4.1f }
        };
        for (float[] m : outerMesas) {
            float mx = cx + m[0] * w, my = cy + m[1] * h, mrx = m[2] * w, mry = m[3] * h;
            buildEjectaBlanketPath(tempPath, mx + 8f * dp, my + 10f * dp, mrx, mry, m[4]);
            paint.setColor(Color.parseColor("#44000000"));
            canvas.drawPath(tempPath, paint);

            buildEjectaBlanketPath(tempPath, mx, my, mrx, mry, m[4]);
            paint.setColor(moenkopiRedCol);
            canvas.drawPath(tempPath, paint);

            buildEjectaBlanketPath(tempPath, mx - 4f * dp, my - 4f * dp, mrx * 0.72f, mry * 0.72f, m[4] + 0.4f);
            paint.setColor(kaibabBuffCol);
            canvas.drawPath(tempPath, paint);
        }

        // Branching Dendritic Dry Canyon Arroyos (Wadis) Carved into the Outer Soil
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        float[][][] arroyos = {
                { { -0.98f, -0.46f }, { -0.64f, -0.54f }, { -0.22f, -0.66f }, {  0.20f, -0.96f } },
                { { -0.64f, -0.54f }, { -0.48f, -0.74f }, { -0.32f, -0.88f }, { -0.18f, -0.98f } },
                { { -0.42f, -0.60f }, { -0.02f, -0.76f }, {  0.46f, -0.82f }, {  0.96f, -0.74f } },
                { {  0.54f, -0.50f }, {  0.72f, -0.40f }, {  0.86f, -0.20f }, {  0.99f, -0.06f } },
                { { -0.98f,  0.64f }, { -0.76f,  0.74f }, { -0.60f,  0.86f }, { -0.50f,  0.99f } }
        };
        for (float[][] arr : arroyos) {
            tempPath.reset();
            tempPath.moveTo(cx + arr[0][0] * w, cy + arr[0][1] * h);
            tempPath.cubicTo(cx + arr[1][0] * w, cy + arr[1][1] * h,
                    cx + arr[2][0] * w, cy + arr[2][1] * h,
                    cx + arr[3][0] * w, cy + arr[3][1] * h);
            strokePaint.setColor(rimWallDark);
            strokePaint.setAlpha(140);
            strokePaint.setStrokeWidth(18f * dp);
            canvas.drawPath(tempPath, strokePaint);

            strokePaint.setColor(desertLightCol);
            strokePaint.setAlpha(190);
            strokePaint.setStrokeWidth(11f * dp);
            canvas.drawPath(tempPath, strokePaint);

            strokePaint.setColor(alkaliTanCol);
            strokePaint.setAlpha(isSunnyDay ? 175 : 95);
            strokePaint.setStrokeWidth(4.5f * dp);
            canvas.drawPath(tempPath, strokePaint);
        }
        strokePaint.setAlpha(255);

        // Severed & Scorched City Road Stubs at the ±gridStep intersections
        float[] severedOffsets = { -gridStep * 0.5f, gridStep * 0.5f };
        float stubRoadW = 140f * dp;
        for (float ro : severedOffsets) {
            drawSeveredRoadStub(canvas, paint, strokePaint, cx + ro, cy - h, stubRoadW, 135f * dp, 0f, starPhase);
            drawSeveredRoadStub(canvas, paint, strokePaint, cx + ro, cy + h, stubRoadW, 135f * dp, 180f, starPhase);
            drawSeveredRoadStub(canvas, paint, strokePaint, cx - w, cy + ro, stubRoadW, 135f * dp, 270f, starPhase);
            drawSeveredRoadStub(canvas, paint, strokePaint, cx + w, cy + ro, stubRoadW, 135f * dp, 90f, starPhase);
        }

        // 3 Hummocky Overturned Ejecta Blanket Lobes (Kaibab & Moenkopi Rock Debris Aprons)
        float[] lobeScales = { 1.44f, 1.29f, 1.16f };
        int[] lobeColors   = { desertBaseCol, desertMidCol, desertLightCol };
        for (int l = 0; l < 3; l++) {
            buildEjectaBlanketPath(tempPath, craterCX + 8f * dp, craterCY + 10f * dp, rimRX * lobeScales[l], rimRY * lobeScales[l], l * 1.5f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#38000000"));
            canvas.drawPath(tempPath, paint);

            buildEjectaBlanketPath(tempPath, craterCX, craterCY, rimRX * lobeScales[l], rimRY * lobeScales[l], l * 1.5f);
            paint.setColor(lobeColors[l]);
            canvas.drawPath(tempPath, paint);
        }

        // Secondary Satellite Impact Craters & Bomb Sags in the Outer Soil
        float[][] secondaryCraters = {
                { -0.68f, -0.36f, 34f }, { -0.76f, -0.14f, 24f }, { -0.46f, -0.62f, 28f },
                {  0.66f, -0.48f, 38f }, {  0.78f, -0.26f, 26f }, {  0.74f,  0.14f, 32f },
                {  0.52f,  0.52f, 30f }, { -0.22f,  0.64f, 36f }, { -0.74f,  0.48f, 28f }
        };
        for (float[] sc : secondaryCraters) {
            float scx = cx + sc[0] * w, scy = cy + sc[1] * h, scr = sc[2] * dp;
            paint.setColor(alkaliTanCol);
            canvas.drawCircle(scx - 2f * dp, scy + 2f * dp, scr * 1.18f, paint);
            paint.setColor(rimWallDark);
            canvas.drawCircle(scx, scy, scr, paint);
            paint.setColor(desertMidCol);
            canvas.drawCircle(scx + scr * 0.16f, scy + scr * 0.18f, scr * 0.76f, paint);
            drawCraggyRock25D(canvas, paint, scx, scy, scr * 0.28f, Color.parseColor("#3E2723"), kaibabBuffCol, sc[2]);
        }

        // Outer Soil Boulder Fields & Arid Desert Scrub Bushes
        for (int b = 0; b < 90; b++) {
            double ang = b * 2.39996;
            float distFrac = 1.15f + (b % 9) * 0.055f;
            float bx = craterCX + (float) Math.cos(ang) * (rimRX * distFrac);
            float by = craterCY + (float) Math.sin(ang) * (rimRY * distFrac);
            if (b % 3 == 0) {
                // Arid desert sagebrush / scrub cluster
                paint.setColor(Color.parseColor("#38000000"));
                canvas.drawCircle(bx + 2f * dp, by + 2f * dp, (4.5f + (b % 4)) * dp, paint);
                paint.setColor(isSunnyDay ? Color.parseColor("#4D5D3B") : Color.parseColor("#1E2916"));
                canvas.drawCircle(bx, by, (4.2f + (b % 4)) * dp, paint);
            } else {
                // 3D polygonal ejecta boulder
                float bRad = (4.5f + (b % 5) * 2.2f) * dp;
                drawCraggyRock25D(canvas, paint, bx, by, bRad, rimWallMid, (b % 2 == 0) ? alkaliTanCol : kaibabBuffCol, b * 1.7f);
            }
        }

        // Pale Kaibab Limestone Ejecta Outcrops (South-East, South-West & West Flanks matching Image 4)
        float[][] alkaliLobes = {
                {  0.22f,  0.54f, 0.16f, 0.12f, 0.8f },
                { -0.24f,  0.72f, 0.11f, 0.12f, 2.1f },
                { -0.56f, -0.02f, 0.12f, 0.18f, 3.4f }
        };
        for (float[] al : alkaliLobes) {
            buildEjectaBlanketPath(tempPath, cx + al[0] * w, cy + al[1] * h, al[2] * w, al[3] * h, al[4]);
            paint.setColor(alkaliTanCol);
            paint.setAlpha(isSunnyDay ? 175 : 95);
            canvas.drawPath(tempPath, paint);
        }
        paint.setAlpha(255);

        // =========================================================================
        // 2. UPLIFTED OUTER CRATER RIM & EXTERIOR RADIAL RIDGES
        // =========================================================================
        buildBarringerCraterPath(tempPath, craterCX + 22f * dp, craterCY + 26f * dp, rimRX * 1.14f, rimRY * 1.14f);
        paint.setColor(Color.parseColor("#58000000"));
        canvas.drawPath(tempPath, paint);

        buildBarringerCraterPath(tempPath, craterCX, craterCY, rimRX * 1.13f, rimRY * 1.13f);
        paint.setColor(moenkopiRedCol);
        canvas.drawPath(tempPath, paint);

        // Sunlit Western & Southern Rim Crest Highlight
        buildBarringerCraterPath(tempPath, craterCX - 12f * dp, craterCY + 10f * dp, rimRX * 1.06f, rimRY * 1.06f);
        paint.setColor(kaibabBuffCol);
        paint.setAlpha(isSunnyDay ? 180 : 105);
        canvas.drawPath(tempPath, paint);
        paint.setAlpha(255);

        // 108 Exterior Rim Erosion Gullies
        for (int g = 0; g < 108; g++) {
            double ang = g * (Math.PI * 2.0 / 108.0);
            float[] pRim = evalBarringerPoint(ang, rimRX, rimRY, 1.00f);
            float[] pToe = evalBarringerPoint(ang + Math.sin(g) * 0.015, rimRX, rimRY, 1.12f + (g % 4) * 0.035f);
            strokePaint.setColor((g % 3 == 0) ? rimWallDark : ((g % 2 == 0) ? kaibabBuffCol : desertMidCol));
            strokePaint.setAlpha(isSunnyDay ? 165 : 110);
            strokePaint.setStrokeWidth((2.0f + (g % 3) * 1.4f) * dp);
            canvas.drawLine(craterCX + pRim[0], craterCY + pRim[1], craterCX + pToe[0], craterCY + pToe[1], strokePaint);
        }
        strokePaint.setAlpha(255);

        // =========================================================================
        // 3. INNER CRATER CLIFF ESCARPMENTS, 120 3D ROCK FACETS & TALUS SCREE LOBES
        // =========================================================================
        buildBarringerCraterPath(tempPath, craterCX, craterCY, rimRX, rimRY);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rimWallDark);
        canvas.drawPath(tempPath, paint);

        canvas.save();
        buildBarringerCraterPath(tempPath, craterCX, craterCY, rimRX, rimRY);
        canvas.clipPath(tempPath);

        // Concentric Geological Bedrock Terraces (Bench-and-Bluff Cliff Structure)
        float[] strataScales = { 0.94f, 0.86f, 0.77f, 0.67f, 0.56f };
        int[] strataCols     = { moenkopiRedCol, kaibabBuffCol, rimWallMid, desertMidCol, craterFloorCol };
        for (int s = 0; s < strataScales.length; s++) {
            buildBarringerCraterPath(tempPath, craterCX, craterCY + (s * 5f * dp), rimRX * strataScales[s], rimRY * strataScales[s]);
            paint.setColor(strataCols[s]);
            canvas.drawPath(tempPath, paint);
        }

        // 120-Sector 3D Triangular Cliff Buttresses & Scalloped Talus Fans (TERMINATING AT THE FLOOR EDGE, NOT THE CENTER!)
        int wallSectors = 120;
        double dAng = (Math.PI * 2.0) / wallSectors;
        for (int i = 0; i < wallSectors; i++) {
            double a1 = i * dAng;
            double aMid = (i + 0.5) * dAng;
            double a2 = (i + 1.0) * dAng;

            float[] r1 = evalBarringerPoint(a1, rimRX, rimRY, 1.00f);
            float[] r2 = evalBarringerPoint(a2, rimRX, rimRY, 1.00f);

            float midScale = 0.76f + (i % 3) * 0.03f;
            float[] m1 = evalBarringerPoint(a1, rimRX, rimRY, midScale);
            float[] mM = evalBarringerPoint(aMid, rimRX, rimRY, midScale - 0.04f);
            float[] m2 = evalBarringerPoint(a2, rimRX, rimRY, midScale);

            // Talus toes stop at 0.48f..0.55f (the outer edge of the basin floor)
            float toeScale = 0.47f + (i % 5) * 0.025f;
            float[] tM = evalBarringerPoint(aMid, rimRX, rimRY, toeScale);

            float sunFactor = (float) Math.sin(aMid + 0.25);
            int leftFacetCol, rightFacetCol, talusConeCol;
            if (isSunnyDay) {
                leftFacetCol  = (sunFactor > -0.15f) ? Color.parseColor("#C87D4E") : Color.parseColor("#4A2215");
                rightFacetCol = (sunFactor > -0.15f) ? Color.parseColor("#7A3B24") : Color.parseColor("#2B120A");
                talusConeCol  = (i % 2 == 0) ? Color.parseColor("#D69667") : Color.parseColor("#9C5532");
            } else {
                leftFacetCol  = Color.parseColor("#47281B");
                rightFacetCol = Color.parseColor("#24120B");
                talusConeCol  = Color.parseColor("#382015");
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(leftFacetCol);
            tempPath.reset();
            tempPath.moveTo(craterCX + r1[0], craterCY + r1[1]);
            tempPath.lineTo(craterCX + r2[0], craterCY + r2[1]);
            tempPath.lineTo(craterCX + mM[0], craterCY + mM[1]);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            paint.setColor(rightFacetCol);
            tempPath.reset();
            tempPath.moveTo(craterCX + r2[0], craterCY + r2[1]);
            tempPath.lineTo(craterCX + m2[0], craterCY + m2[1]);
            tempPath.lineTo(craterCX + mM[0], craterCY + mM[1]);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Lower Conical Talus Scree Fan
            paint.setColor(talusConeCol);
            tempPath.reset();
            tempPath.moveTo(craterCX + m1[0], craterCY + m1[1]);
            tempPath.lineTo(craterCX + m2[0], craterCY + m2[1]);
            tempPath.lineTo(craterCX + tM[0], craterCY + tM[1] + 10f * dp);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Deep Erosion Gully Chute along the Wall Only
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(rimWallDark);
            strokePaint.setAlpha(190);
            strokePaint.setStrokeWidth((1.8f + (i % 3) * 1.1f) * dp);
            canvas.drawLine(craterCX + r1[0], craterCY + r1[1], craterCX + tM[0], craterCY + tM[1] + 10f * dp, strokePaint);
        }
        strokePaint.setAlpha(255);

        // Exposed Horizontal Cliff Outcrop Ledges
        strokePaint.setStrokeWidth(2.8f * dp);
        strokePaint.setPathEffect(rutDash);
        for (int b = 0; b < 5; b++) {
            float sc = 0.94f - b * 0.06f;
            strokePaint.setColor((b % 2 == 0) ? rimWallDark : alkaliTanCol);
            strokePaint.setAlpha((b % 2 == 0) ? 215 : 145);
            buildBarringerCraterPath(tempPath, craterCX, craterCY + b * 2.5f * dp, rimRX * sc, rimRY * sc);
            canvas.drawPath(tempPath, strokePaint);
        }
        strokePaint.setPathEffect(null);
        strokePaint.setAlpha(255);

        // =========================================================================
        // 4. REALISTIC INNER CRATER BASIN FLOOR (NO FLAT OVALS OR SPOKE LINES!)
        // =========================================================================
        float floorRX = rimRX * 0.50f, floorRY = rimRY * 0.48f;
        float floorCY = craterCY + 14f * dp;

        // A. Outer Hummocky Talus Toe Apron (Organic Multi-Harmonic Contour)
        buildOrganicFloorContourPath(tempPath, craterCX, floorCY, floorRX * 1.06f, floorRY * 1.06f, 0.3f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rimWallMid);
        canvas.drawPath(tempPath, paint);

        // B. Main Sunken Basin Floor with Volumetric 3D RadialGradient Shading
        buildOrganicFloorContourPath(tempPath, craterCX, floorCY, floorRX, floorRY, 1.2f);
        paint.setShader(new android.graphics.RadialGradient(
                craterCX, floorCY, Math.max(floorRX, floorRY),
                new int[]{
                        isSunnyDay ? Color.parseColor("#783E22") : Color.parseColor("#1F100A"),
                        craterFloorCol,
                        isSunnyDay ? Color.parseColor("#BA7348") : Color.parseColor("#422518"),
                        rimWallDark
                },
                new float[]{ 0f, 0.45f, 0.82f, 1f },
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawPath(tempPath, paint);
        paint.setShader(null);

        // C. Scalloped Landslide Talus Lobes Spilling Onto the Floor Perimeter
        for (int tl = 0; tl < 36; tl++) {
            double tang = tl * (Math.PI * 2.0 / 36.0);
            float[] edgePt = evalBarringerPoint(tang, floorRX, floorRY, 0.96f);
            float lx = craterCX + edgePt[0], ly = floorCY + edgePt[1];
            float lobeW = floorRX * (0.11f + (tl % 3) * 0.03f);
            float lobeH = floorRY * (0.09f + (tl % 4) * 0.025f);
            buildEjectaBlanketPath(tempPath, lx, ly, lobeW, lobeH, tl * 0.9f);
            paint.setColor((tl % 2 == 0) ? desertMidCol : desertLightCol);
            paint.setAlpha(isSunnyDay ? 165 : 110);
            canvas.drawPath(tempPath, paint);
        }
        paint.setAlpha(255);

        // D. Inner Alluvial Wash Deposit & Terraced Breccia Mound Contours
        buildOrganicFloorContourPath(tempPath, craterCX - floorRX * 0.04f, floorCY + floorRY * 0.04f, floorRX * 0.76f, floorRY * 0.72f, 2.5f);
        paint.setColor(desertLightCol);
        paint.setAlpha(isSunnyDay ? 150 : 95);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, craterCX + floorRX * 0.02f, floorCY + floorRY * 0.06f, floorRX * 0.56f, floorRY * 0.52f, 4.0f);
        paint.setColor(kaibabBuffCol);
        paint.setAlpha(isSunnyDay ? 140 : 85);
        canvas.drawPath(tempPath, paint);
        paint.setAlpha(255);

        // E. Authentic Hook-Shaped Central Barringer Silica Playa & Mine Tailings (Matching Image 4 Center!)
        float playaX = craterCX - floorRX * 0.06f, playaY = floorCY + floorRY * 0.02f;
        // Upper-West Cream Silica Cap
        buildOrganicFloorContourPath(tempPath, playaX, playaY - floorRY * 0.06f, floorRX * 0.36f, floorRY * 0.25f, 0.8f);
        paint.setColor(playaCreamCol);
        canvas.drawPath(tempPath, paint);

        // Lower-South Hooked Alluvial Tailings Tongue (forming the "?" shape seen in Image 4)
        buildOrganicFloorContourPath(tempPath, playaX + floorRX * 0.08f, playaY + floorRY * 0.26f, floorRX * 0.22f, floorRY * 0.32f, 2.2f);
        paint.setColor(alkaliTanCol);
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, playaX, playaY - floorRY * 0.06f, floorRX * 0.24f, floorRY * 0.16f, 1.9f);
        paint.setColor(isSunnyDay ? Color.parseColor("#F5EBE0") : Color.parseColor("#8C8074"));
        canvas.drawPath(tempPath, paint);

        // =========================================================================
        // 5. DANGEROUS MOLTEN IMPACT ABYSS, TECTONIC MAGMA RIFTS & ASTEROID MONOLITHS
        //    (Scaled proportionally to floorRX so it dominates the center with high detail!)
        // =========================================================================
        float coreX = craterCX + floorRX * 0.14f, coreY = floorCY - floorRY * 0.06f;
        float coreR = floorRX * 0.28f; // Large, commanding impact abyss
        float lavaPulse = 0.5f + 0.5f * (float) Math.sin(starPhase * 4.2f);

        // A. Branching Tectonic Magma Rifts Tearing Across the Crater Floor (Tapered Jagged Polygons)
        float[][] riftSpecs = {
                { -2.65f, 1.35f, 0.35f }, // Long rift tearing SW across the silica playa
                { -1.85f, 1.10f, -0.25f },
                { -0.85f, 1.05f, 0.28f },
                {  0.15f, 1.25f, -0.32f },
                {  0.95f, 1.15f, 0.22f },
                {  1.75f, 1.30f, -0.28f },
                {  2.55f, 1.00f, 0.30f }
        };
        for (int rf = 0; rf < riftSpecs.length; rf++) {
            float baseAng = riftSpecs[rf][0];
            float reach = coreR * riftSpecs[rf][1] * 1.45f;
            float bend = riftSpecs[rf][2];
            drawTectonicMagmaRift(canvas, paint, strokePaint, coreX, coreY, baseAng, reach, bend, coreR * 0.12f, lavaPulse);
        }

        // B. Outer Scorched Basalt Blast Ring & Volumetric Magma Chamber Glow
        buildOrganicFloorContourPath(tempPath, coreX, coreY, coreR * 1.12f, coreR * 0.92f, 3.1f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1F0D07"));
        canvas.drawPath(tempPath, paint);

        buildOrganicFloorContourPath(tempPath, coreX, coreY, coreR * 0.92f, coreR * 0.76f, 1.4f);
        paint.setShader(new android.graphics.RadialGradient(
                coreX, coreY, coreR * 0.92f,
                new int[]{
                        Color.parseColor("#FEF08A"),
                        Color.parseColor("#F97316"),
                        Color.parseColor("#DC2626"),
                        Color.parseColor("#450A0A"),
                        Color.parseColor("#1C0A05")
                },
                new float[]{ 0f, 0.22f + lavaPulse * 0.08f, 0.52f, 0.82f, 1f },
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawPath(tempPath, paint);
        paint.setShader(null);

        // C. 5 Massive Shattered 3D Iron-Nickel Asteroid Monoliths Jutting from the Molten Pit
        float[][] monolithFragments = {
                {  0.00f,  0.00f, 0.42f, 0.32f, 0.2f }, // Central main Canyon Diablo iron-nickel mass
                { -0.44f, -0.22f, 0.24f, 0.18f, 1.4f }, // NW sheared fragment
                {  0.46f, -0.18f, 0.22f, 0.17f, 2.7f }, // NE sheared fragment
                { -0.32f,  0.34f, 0.20f, 0.16f, 3.9f }, // SW impactite slab
                {  0.38f,  0.30f, 0.25f, 0.18f, 5.1f }  // SE metallic spire
        };
        for (int mf = 0; mf < monolithFragments.length; mf++) {
            float mx = coreX + monolithFragments[mf][0] * coreR;
            float my = coreY + monolithFragments[mf][1] * coreR;
            float mRad = monolithFragments[mf][2] * coreR;
            drawCraggyRock25D(canvas, paint, mx, my, mRad,
                    Color.parseColor("#18181B"), Color.parseColor("#52525B"), monolithFragments[mf][4]);

            // Glowing orange cooling fissures across the metallic asteroid surface
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.argb((int) (175 + lavaPulse * 80), 251, 146, 60));
            strokePaint.setStrokeWidth(2.0f * dp);
            canvas.drawLine(mx - mRad * 0.45f, my - mRad * 0.20f, mx + mRad * 0.35f, my + mRad * 0.25f, strokePaint);
            canvas.drawLine(mx + mRad * 0.10f, my - mRad * 0.40f, mx - mRad * 0.20f, my + mRad * 0.35f, strokePaint);
        }

        // Expanding Thermal Shockwaves & Flying Incandescent Lava Embers
        for (int sr = 0; sr < 3; sr++) {
            float sp = ((starPhase * 0.26f) + sr * 0.33f) % 1.0f;
            float sRad = coreR * (0.35f + sp * 1.15f);
            strokePaint.setColor(Color.argb(Math.max(0, Math.round((1f - sp) * 165f)), 251, 146, 60));
            strokePaint.setStrokeWidth((2.8f - sp * 1.6f) * dp);
            canvas.drawCircle(coreX, coreY, sRad, strokePaint);
        }

        paint.setStyle(Paint.Style.FILL);
        for (int em = 0; em < 18; em++) {
            float ep = ((starPhase * 0.55f) + em * 0.055f) % 1.0f;
            double eAng = em * 1.95 + starPhase * 0.4;
            float ex = coreX + (float) Math.cos(eAng) * (coreR * 0.25f + ep * coreR * 0.95f);
            float ey = coreY + (float) Math.sin(eAng) * (coreR * 0.20f + ep * coreR * 0.75f);
            paint.setColor((em % 2 == 0) ? Color.parseColor("#FEF08A") : Color.parseColor("#F97316"));
            paint.setAlpha(Math.max(0, Math.round((1f - ep) * 240f)));
            canvas.drawCircle(ex, ey, (1.8f + (1f - ep) * 2.4f) * dp, paint);
        }
        paint.setAlpha(255);

        // =========================================================================
        // 6. 3D POLYGONAL BRECCIA BOULDER RIDGES, SWITCHBACK TRAIL & RESEARCH OUTPOST
        // =========================================================================
        // Natural Landslide Boulder Ridges on the Crater Floor (Using 3D Irregular Polygons, NOT Squares!)
        for (int br = 0; br < 42; br++) {
            double bAng = br * 2.39996;
            float ring = 0.28f + (br % 6) * 0.11f;
            float bx = playaX + (float) Math.cos(bAng) * (floorRX * ring);
            float by = playaY + (float) Math.sin(bAng) * (floorRY * ring);
            // Keep boulders clear of the molten core center
            if (Math.hypot(bx - coreX, by - coreY) < coreR * 0.85f) continue;

            float rockR = (5.0f + (br % 5) * 3.2f) * dp;
            int darkC = (br % 3 == 0) ? Color.parseColor("#3E2723") : rimWallMid;
            int lightC = (br % 2 == 0) ? playaCreamCol : kaibabBuffCol;
            drawCraggyRock25D(canvas, paint, bx, by, rockR, darkC, lightC, br * 1.3f);
        }

        // Winding Switchback Excavation Trail from the Southern Rim Down to the Central Silica Playa
        tempPath.reset();
        tempPath.moveTo(craterCX - floorRX * 0.12f, craterCY + rimRY * 0.96f);
        tempPath.cubicTo(craterCX - floorRX * 0.45f, craterCY + rimRY * 0.72f,
                craterCX + floorRX * 0.18f, craterCY + rimRY * 0.56f,
                playaX - floorRX * 0.18f, playaY + floorRY * 0.18f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(rimWallDark);
        strokePaint.setStrokeWidth(9f * dp);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(alkaliTanCol);
        strokePaint.setStrokeWidth(5.5f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Barringer Geological Research & Drilling Outpost on the West Playa
        float campX = playaX - floorRX * 0.22f, campY = playaY - floorRY * 0.04f;
        // Graded gravel compound pad
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#64748B"));
        canvas.drawRoundRect(new RectF(campX - 28f * dp, campY - 20f * dp, campX + 28f * dp, campY + 20f * dp), 5f * dp, 5f * dp, paint);

        // 2 White Geodesic Habitat Domes + Connecting Pressurized Tunnel
        strokePaint.setColor(Color.parseColor("#CBD5E1"));
        strokePaint.setStrokeWidth(5f * dp);
        canvas.drawLine(campX - 12f * dp, campY - 6f * dp, campX + 10f * dp, campY - 6f * dp, strokePaint);
        for (int d = -1; d <= 1; d += 2) {
            float dxp = campX + d * 11f * dp, dyp = campY - 6f * dp;
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawCircle(dxp + 2.5f * dp, dyp + 3f * dp, 9.5f * dp, paint);
            paint.setColor(Color.parseColor("#F8FAFC"));
            canvas.drawCircle(dxp, dyp, 9.5f * dp, paint);
            paint.setColor(Color.parseColor("#38BDF8"));
            canvas.drawCircle(dxp - 2f * dp, dyp - 2f * dp, 4.2f * dp, paint);
        }

        // Steel Drilling Derrick Tower over the Historical Barringer Mine Shaft
        float rigX = campX + 6f * dp, rigY = campY + 10f * dp;
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawRect(rigX - 7f * dp, rigY - 7f * dp, rigX + 7f * dp, rigY + 7f * dp, paint);
        strokePaint.setColor(Color.parseColor("#EF4444"));
        strokePaint.setStrokeWidth(2.4f * dp);
        canvas.drawLine(rigX - 6f * dp, rigY + 6f * dp, rigX + 14f * dp, rigY - 16f * dp, strokePaint);
        canvas.drawLine(rigX + 6f * dp, rigY + 6f * dp, rigX + 14f * dp, rigY - 16f * dp, strokePaint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(rigX + 14f * dp, rigY - 16f * dp, 2.5f * dp, paint);

        // 2 Animated Tracked Exploration Rovers with Headlight Cones
        for (int rv = 0; rv < 2; rv++) {
            float rAng = starPhase * (0.38f + rv * 0.14f) + rv * 3.14f;
            float rxp = playaX + (float) Math.cos(rAng) * (floorRX * (0.26f + rv * 0.14f));
            float ryp = playaY + (float) Math.sin(rAng) * (floorRY * (0.26f + rv * 0.14f));
            canvas.save();
            canvas.translate(rxp, ryp);
            canvas.rotate((float) Math.toDegrees(rAng) + 90f);

            // Rover Headlight Beam
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(75, 254, 240, 138));
            tempPath.reset();
            tempPath.moveTo(0f, -6f * dp);
            tempPath.lineTo(-12f * dp, -26f * dp);
            tempPath.lineTo(12f * dp, -26f * dp);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Rover Shadow, Treads & Solar Deck
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawRoundRect(new RectF(-6f * dp, -7f * dp, 8f * dp, 9f * dp), 2f * dp, 2f * dp, paint);
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawRect(-7.5f * dp, -6f * dp, 7.5f * dp, 6f * dp, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(-5.5f * dp, -7.5f * dp, 5.5f * dp, 7.5f * dp), 2f * dp, 2f * dp, paint);
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRect(-3.5f * dp, -4.5f * dp, 3.5f * dp, 4.5f * dp, paint);
            canvas.restore();
        }

        // Rising Volcanic Ash & Geothermal Smoke Plumes from the Core
        drawConstructionDustAndSmoke(canvas, paint, coreX, coreY, starPhase * 0.85f, true);
        drawConstructionDustAndSmoke(canvas, paint, coreX - coreR * 0.35f, coreY + coreR * 0.25f, starPhase * 0.65f + 1.4f, true);

        // =========================================================================
        // 7. RAZOR-SHARP PITCH-BLACK NORTH CLIFF SHADOW (IMAGE 4 SIGNATURE)
        // =========================================================================
        tempPath.reset();
        tempPath.moveTo(craterCX - rimRX * 0.48f, craterCY - rimRY * 0.96f);
        tempPath.quadTo(craterCX + rimRX * 0.06f, craterCY - rimRY * 1.06f, craterCX + rimRX * 0.74f, craterCY - rimRY * 0.72f);
        tempPath.lineTo(craterCX + rimRX * 0.56f, craterCY - rimRY * 0.28f);
        tempPath.lineTo(craterCX + rimRX * 0.32f, craterCY - rimRY * 0.36f);
        tempPath.lineTo(craterCX + rimRX * 0.12f, craterCY - rimRY * 0.26f);
        tempPath.lineTo(craterCX - rimRX * 0.10f, craterCY - rimRY * 0.34f);
        tempPath.lineTo(craterCX - rimRX * 0.28f, craterCY - rimRY * 0.62f);
        tempPath.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isSunnyDay ? Color.parseColor("#DC080507") : Color.parseColor("#F0020204"));
        canvas.drawPath(tempPath, paint);

        canvas.restore(); // End interior crater bowl clip

        // =========================================================================
        // 8. JAGGED UPLIFTED RIM CREST & 3D POLYGONAL RIM BOULDERS (WHALE ROCK)
        // =========================================================================
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(isSunnyDay ? Color.parseColor("#E6B38A") : Color.parseColor("#64748B"));
        strokePaint.setStrokeWidth(2.6f * dp);
        buildBarringerCraterPath(tempPath, craterCX, craterCY, rimRX, rimRY);
        canvas.drawPath(tempPath, strokePaint);

        for (int r = 0; r < 84; r++) {
            double ang = r * (Math.PI * 2.0 / 84.0);
            float[] pt = evalBarringerPoint(ang, rimRX, rimRY, 1.00f + (r % 5) * 0.022f);
            float rxp = craterCX + pt[0], ryp = craterCY + pt[1];
            float bRad = (3.2f + (r % 5) * 2.0f) * dp;
            drawCraggyRock25D(canvas, paint, rxp, ryp, bRad,
                    (r % 3 == 0) ? Color.parseColor("#3E2723") : rimWallMid,
                    (r % 2 == 0) ? alkaliTanCol : kaibabBuffCol, r * 1.1f);
        }

        // =========================================================================
        // 9. BOTTOM-LEFT RIM: VISITOR CENTER, HIGHWAY, TRAFFIC & ORBITING HELICOPTER
        // =========================================================================
        float vcX = cx - w * 0.56f, vcY = cy + h * 0.28f;

        // Sweeping Curved Asphalt Approach Road from Southern Edge to Parking Lot
        tempPath.reset();
        tempPath.moveTo(cx - w * 0.45f, cy + h);
        tempPath.cubicTo(cx - w * 0.38f, cy + h * 0.80f, cx - w * 0.36f, cy + h * 0.58f, vcX + 52f * dp, vcY + 48f * dp);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        strokePaint.setColor(Color.parseColor("#0F172A"));
        strokePaint.setStrokeWidth(24f * dp);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(Color.parseColor("#334155"));
        strokePaint.setStrokeWidth(19f * dp);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(Color.parseColor("#FACC15"));
        strokePaint.setStrokeWidth(1.5f * dp);
        strokePaint.setPathEffect(kartDash);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setPathEffect(null);

        // Moving Tourist Cars on the Approach Highway
        for (int tc = 0; tc < 2; tc++) {
            float tProg = ((starPhase * 0.16f) + tc * 0.5f) % 1.0f;
            float inv = 1f - tProg;
            float carX = inv * inv * inv * (cx - w * 0.45f)
                    + 3f * inv * inv * tProg * (cx - w * 0.38f)
                    + 3f * inv * tProg * tProg * (cx - w * 0.36f)
                    + tProg * tProg * tProg * (vcX + 52f * dp);
            float carY = inv * inv * inv * (cy + h)
                    + 3f * inv * inv * tProg * (cy + h * 0.80f)
                    + 3f * inv * tProg * tProg * (cy + h * 0.58f)
                    + tProg * tProg * tProg * (vcY + 48f * dp);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((tc == 0) ? Color.WHITE : Color.parseColor("#EF4444"));
            canvas.drawRoundRect(new RectF(carX - 3.5f * dp, carY - 5.5f * dp, carX + 3.5f * dp, carY + 5.5f * dp), 1.5f * dp, 1.5f * dp, paint);
        }

        // Angled Dark-Asphalt Visitor Parking Lot
        canvas.save();
        canvas.translate(vcX + 18f * dp, vcY + 56f * dp);
        canvas.rotate(-32f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawRoundRect(new RectF(-56f * dp, -38f * dp, 56f * dp, 38f * dp), 6f * dp, 6f * dp, paint);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRoundRect(new RectF(-52f * dp, -34f * dp, 52f * dp, 34f * dp), 4f * dp, 4f * dp, paint);

        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.1f * dp);
        int[] lotCars = { Color.WHITE, Color.parseColor("#CBD5E1"), Color.parseColor("#EF4444"), Color.parseColor("#38BDF8"), Color.parseColor("#0F172A"), Color.parseColor("#FACC15") };
        for (int row = -1; row <= 1; row += 2) {
            float ry = row * 15f * dp;
            canvas.drawLine(-44f * dp, ry, 44f * dp, ry, strokePaint);
            for (int c = -4; c <= 4; c++) {
                float sx = c * 9.8f * dp;
                canvas.drawLine(sx, ry - 6f * dp, sx, ry + 6f * dp, strokePaint);
                if ((c + row * 3) % 2 == 0) {
                    paint.setColor(lotCars[Math.abs(c * 3 + row) % lotCars.length]);
                    canvas.drawRoundRect(new RectF(sx + 1.8f * dp, ry - 4.5f * dp, sx + 7.8f * dp, ry + 4.5f * dp), 1.5f * dp, 1.5f * dp, paint);
                }
            }
        }
        canvas.restore();

        // Meteor Crater Visitor Center, Space Museum & Rim Observation Decks
        canvas.save();
        canvas.translate(vcX, vcY);
        canvas.rotate(-10f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawRoundRect(new RectF(-50f * dp, -28f * dp, 68f * dp, 20f * dp), 6f * dp, 6f * dp, paint);
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawRoundRect(new RectF(-44f * dp, -30f * dp, 60f * dp, -6f * dp), 4f * dp, 4f * dp, paint);

        tempPath.reset();
        tempPath.moveTo(-60f * dp, -8f * dp);
        tempPath.lineTo(-10f * dp, -20f * dp);
        tempPath.lineTo(14f * dp, -10f * dp);
        tempPath.lineTo(46f * dp, -24f * dp);
        tempPath.lineTo(70f * dp, -10f * dp);
        tempPath.lineTo(64f * dp, 5f * dp);
        tempPath.lineTo(38f * dp, -6f * dp);
        tempPath.lineTo(12f * dp, 5f * dp);
        tempPath.lineTo(-12f * dp, -4f * dp);
        tempPath.lineTo(-56f * dp, 7f * dp);
        tempPath.close();

        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawPath(tempPath, paint);
        strokePaint.setColor(Color.parseColor("#94A3B8"));
        strokePaint.setStrokeWidth(1.5f * dp);
        canvas.drawPath(tempPath, strokePaint);

        paint.setColor(Color.parseColor("#9C4A2D"));
        canvas.drawRoundRect(new RectF(-30f * dp, 0f, 4f * dp, 19f * dp), 2.5f * dp, 2.5f * dp, paint);
        paint.setColor(Color.parseColor("#B85D3B"));
        canvas.drawRoundRect(new RectF(8f * dp, -2f * dp, 34f * dp, 15f * dp), 2.5f * dp, 2.5f * dp, paint);
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawCircle(18f * dp, 19f * dp, 11.5f * dp, paint);
        paint.setColor(Color.parseColor("#64748B"));
        canvas.drawCircle(18f * dp, 19f * dp, 7.5f * dp, paint);
        canvas.restore();

        // South-East Winding Dirt Trail & 4x4 Survey Jeep
        tempPath.reset();
        tempPath.moveTo(cx + w * 0.34f, cy + h * 0.56f);
        tempPath.cubicTo(cx + w * 0.64f, cy + h * 0.60f, cx + w * 0.78f, cy + h * 0.80f, cx + w * 0.66f, cy + h * 0.90f);
        tempPath.quadTo(cx + w * 0.54f, cy + h * 0.96f, cx + w * 0.62f, cy + h);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(rimWallMid);
        strokePaint.setStrokeWidth(11f * dp);
        canvas.drawPath(tempPath, strokePaint);
        strokePaint.setColor(alkaliTanCol);
        strokePaint.setStrokeWidth(7f * dp);
        canvas.drawPath(tempPath, strokePaint);

        float jeepProg = 0.5f + 0.5f * (float) Math.sin(starPhase * 0.35f);
        float jx = (cx + w * 0.38f) + jeepProg * (w * 0.32f);
        float jy = (cy + h * 0.58f) + jeepProg * (h * 0.28f);
        drawConstructionDustAndSmoke(canvas, paint, jx, jy, starPhase * 1.5f, false);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F59E0B"));
        canvas.drawRoundRect(new RectF(jx - 7f * dp, jy - 4.5f * dp, jx + 7f * dp, jy + 4.5f * dp), 2f * dp, 2f * dp, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRect(jx - 4f * dp, jy - 3.5f * dp, jx + 2f * dp, jy + 3.5f * dp, paint);

        // Orbiting Survey Helicopter Scanning the Inner Crater
        float heliAng = starPhase * 0.24f;
        float heliX = craterCX + (float) Math.cos(heliAng) * (rimRX * 0.72f);
        float heliY = craterCY + (float) Math.sin(heliAng) * (rimRY * 0.72f);
        canvas.save();
        canvas.translate(heliX, heliY);
        canvas.rotate((float) Math.toDegrees(heliAng) + 90f);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(isSunnyDay ? 40 : 80, 224, 242, 254));
        tempPath.reset();
        tempPath.moveTo(0f, 0f);
        tempPath.lineTo(-48f * dp, -20f * dp);
        tempPath.lineTo(-48f * dp, 20f * dp);
        tempPath.close();
        canvas.drawPath(tempPath, paint);

        paint.setColor(Color.parseColor("#EF4444"));
        canvas.drawRect(-18f * dp, -1.8f * dp, -2f * dp, 1.8f * dp, paint);
        canvas.drawOval(new RectF(-7f * dp, -5.5f * dp, 12f * dp, 5.5f * dp), paint);
        paint.setColor(Color.parseColor("#38BDF8"));
        canvas.drawOval(new RectF(2f * dp, -4.2f * dp, 11f * dp, 4.2f * dp), paint);

        canvas.rotate(starPhase * 28f);
        strokePaint.setColor(Color.parseColor("#E2E8F0"));
        strokePaint.setStrokeWidth(2.0f * dp);
        canvas.drawLine(-18f * dp, -18f * dp, 18f * dp, 18f * dp, strokePaint);
        canvas.drawLine(-18f * dp, 18f * dp, 18f * dp, -18f * dp, strokePaint);
        canvas.restore();

        canvas.restore(); // End 3x3 Mega-Zone clip

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#3E2723"));
        strokePaint.setStrokeWidth(3.6f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 38f * dp, 38f * dp, strokePaint);
    }

    /**
     * Draws a jagged, multi-faceted 2.5D polygonal boulder / rock monolith (replaces flat squares/circles).
     */
    private void drawCraggyRock25D(Canvas canvas, Paint paint, float rx, float ry, float rad, int baseCol, int highlightCol, float seed) {
        paint.setStyle(Paint.Style.FILL);
        int verts = 7;

        // 1. Drop shadow polygon
        tempPath.reset();
        for (int v = 0; v < verts; v++) {
            double a = v * (Math.PI * 2.0 / verts) + seed;
            float rMod = rad * (0.76f + 0.28f * (float) Math.sin(v * 2.7f + seed));
            float px = rx + 2.5f * dp + (float) Math.cos(a) * rMod;
            float py = ry + 3.0f * dp + (float) Math.sin(a) * (rMod * 0.85f);
            if (v == 0) tempPath.moveTo(px, py);
            else tempPath.lineTo(px, py);
        }
        tempPath.close();
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawPath(tempPath, paint);

        // 2. Dark shaded rock base
        tempPath.reset();
        for (int v = 0; v < verts; v++) {
            double a = v * (Math.PI * 2.0 / verts) + seed;
            float rMod = rad * (0.76f + 0.28f * (float) Math.sin(v * 2.7f + seed));
            float px = rx + (float) Math.cos(a) * rMod;
            float py = ry + (float) Math.sin(a) * (rMod * 0.85f);
            if (v == 0) tempPath.moveTo(px, py);
            else tempPath.lineTo(px, py);
        }
        tempPath.close();
        paint.setColor(baseCol);
        canvas.drawPath(tempPath, paint);

        // 3. Sunlit upper-left 3D rock ridge facet
        tempPath.reset();
        for (int v = 0; v < verts; v++) {
            double a = v * (Math.PI * 2.0 / verts) + seed + 0.3;
            float rMod = rad * 0.56f * (0.78f + 0.25f * (float) Math.cos(v * 2.3f + seed));
            float px = (rx - rad * 0.16f) + (float) Math.cos(a) * rMod;
            float py = (ry - rad * 0.16f) + (float) Math.sin(a) * (rMod * 0.82f);
            if (v == 0) tempPath.moveTo(px, py);
            else tempPath.lineTo(px, py);
        }
        tempPath.close();
        paint.setColor(highlightCol);
        canvas.drawPath(tempPath, paint);
    }

    /**
     * Draws a tapered, jagged tectonic fault rift filled with glowing molten magma and a forked side-branch.
     */
    private void drawTectonicMagmaRift(Canvas canvas, Paint paint, Paint strokePaint, float startX, float startY, float baseAng, float length, float bend, float maxHalfW, float pulse) {
        int segs = 6;
        float[] cxPts = new float[segs + 1];
        float[] cyPts = new float[segs + 1];
        cxPts[0] = startX;
        cyPts[0] = startY;

        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            float zig = ((i % 2 == 0) ? 1f : -1f) * 0.14f * (1f - t * 0.5f);
            double a = baseAng + bend * t + zig;
            float dist = length * t;
            cxPts[i] = startX + (float) Math.cos(a) * dist;
            cyPts[i] = startY + (float) Math.sin(a) * (dist * 0.82f);
        }

        // Build tapered jagged canyon rift polygon
        tempPath.reset();
        for (int i = 0; i <= segs; i++) {
            float wFrac = (1f - (i / (float) segs)) * maxHalfW;
            float nx = (float) -Math.sin(baseAng) * wFrac;
            float ny = (float) Math.cos(baseAng) * wFrac;
            if (i == 0) tempPath.moveTo(cxPts[i] + nx, cyPts[i] + ny);
            else tempPath.lineTo(cxPts[i] + nx, cyPts[i] + ny);
        }
        for (int i = segs; i >= 0; i--) {
            float wFrac = (1f - (i / (float) segs)) * maxHalfW;
            float nx = (float) -Math.sin(baseAng) * wFrac;
            float ny = (float) Math.cos(baseAng) * wFrac;
            tempPath.lineTo(cxPts[i] - nx, cyPts[i] - ny);
        }
        tempPath.close();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#1F0B06"));
        canvas.drawPath(tempPath, paint);

        // Glowing Magma River Inside the Fault Rift
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < segs; i++) {
            float wFactor = 1f - (i / (float) segs) * 0.78f;
            strokePaint.setColor(Color.argb((int) (185 + pulse * 70), 234, 88, 12));
            strokePaint.setStrokeWidth(maxHalfW * 0.95f * wFactor);
            canvas.drawLine(cxPts[i], cyPts[i], cxPts[i + 1], cyPts[i + 1], strokePaint);

            strokePaint.setColor(Color.argb((int) (160 + pulse * 95), 254, 240, 138));
            strokePaint.setStrokeWidth(maxHalfW * 0.36f * wFactor);
            canvas.drawLine(cxPts[i], cyPts[i], cxPts[i + 1], cyPts[i + 1], strokePaint);
        }

        // Secondary Forked Branch Fissure from Mid-Rift
        float forkX = cxPts[2], forkY = cyPts[2];
        double forkAng = baseAng + (bend > 0 ? -0.65 : 0.65);
        float forkEndX = forkX + (float) Math.cos(forkAng) * (length * 0.38f);
        float forkEndY = forkY + (float) Math.sin(forkAng) * (length * 0.32f);
        strokePaint.setColor(Color.parseColor("#1F0B06"));
        strokePaint.setStrokeWidth(maxHalfW * 0.70f);
        canvas.drawLine(forkX, forkY, forkEndX, forkEndY, strokePaint);
        strokePaint.setColor(Color.parseColor("#F97316"));
        strokePaint.setStrokeWidth(maxHalfW * 0.35f);
        canvas.drawLine(forkX, forkY, forkEndX, forkEndY, strokePaint);
    }

    /**
     * Builds an irregular organic contour path for inner crater floor layers & silica beds (replaces drawOval).
     */
    private void buildOrganicFloorContourPath(Path out, float cx, float cy, float rx, float ry, float seed) {
        out.reset();
        int steps = 72;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            double r = 0.90
                    + 0.065 * Math.cos(3.0 * t + seed)
                    + 0.050 * Math.sin(5.0 * t - seed * 0.7)
                    + 0.035 * Math.cos(8.0 * t + seed * 1.3)
                    + 0.020 * Math.sin(13.0 * t);
            float px = cx + (float) (rx * r * Math.cos(t));
            float py = cy + (float) (ry * r * Math.sin(t));
            if (i == 0) out.moveTo(px, py);
            else out.lineTo(px, py);
        }
        out.close();
    }

    private float[] evalBarringerPoint(double t, float rx, float ry, float scale) {
        double tilt = Math.toRadians(-8.0);
        double uAng = t - tilt;
        double r = 0.92
                + 0.068 * Math.cos(4.0 * uAng)
                + 0.026 * Math.sin(3.0 * t + 0.4)
                + 0.019 * Math.cos(7.0 * t - 0.8)
                + 0.012 * Math.sin(13.0 * t)
                + 0.008 * Math.cos(21.0 * t + 1.1);
        float px = (float) (rx * scale * r * Math.cos(t));
        float py = (float) (ry * scale * r * Math.sin(t));
        return new float[]{ px, py };
    }

    private void buildBarringerCraterPath(Path out, float cx, float cy, float rx, float ry) {
        out.reset();
        int steps = 120;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            float[] pt = evalBarringerPoint(t, rx, ry, 1.0f);
            if (i == 0) out.moveTo(cx + pt[0], cy + pt[1]);
            else out.lineTo(cx + pt[0], cy + pt[1]);
        }
        out.close();
    }

    private void buildEjectaBlanketPath(Path out, float cx, float cy, float rx, float ry, float phaseOff) {
        out.reset();
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            double r = 0.90
                    + 0.08 * Math.cos(4.0 * t + phaseOff)
                    + 0.06 * Math.sin(5.0 * t - phaseOff)
                    + 0.04 * Math.cos(9.0 * t + 0.7)
                    + 0.025 * Math.sin(17.0 * t);
            float px = cx + (float) (rx * r * Math.cos(t));
            float py = cy + (float) (ry * r * Math.sin(t));
            if (i == 0) out.moveTo(px, py);
            else out.lineTo(px, py);
        }
        out.close();
    }

    private void drawSeveredRoadStub(Canvas canvas, Paint paint, Paint strokePaint, float edgeX, float edgeY, float roadW, float stubLen, float rotDeg, float starPhase) {
        canvas.save();
        canvas.translate(edgeX, edgeY);
        canvas.rotate(rotDeg);

        float halfW = roadW * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(sidewalkColor);
        canvas.drawRect(-halfW - 14f * dp, 0f, halfW + 14f * dp, stubLen * 0.78f, paint);
        paint.setColor(roadAsphaltColor);
        canvas.drawRect(-halfW, 0f, halfW, stubLen * 0.85f, paint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#0F172A"));
        strokePaint.setStrokeWidth(2.2f * dp);
        for (int c = -2; c <= 2; c++) {
            float cxOff = c * (halfW * 0.36f);
            canvas.drawLine(cxOff, stubLen * 0.35f, cxOff + (c % 2 == 0 ? 9f : -9f) * dp, stubLen * 0.78f, strokePaint);
        }

        paint.setColor(Color.parseColor("#3B1B10"));
        canvas.drawOval(new RectF(-halfW * 1.28f, stubLen * 0.54f, halfW * 1.28f, stubLen * 1.16f), paint);
        paint.setColor(Color.parseColor("#9C5230"));
        canvas.drawOval(new RectF(-halfW * 1.12f, stubLen * 0.66f, halfW * 1.12f, stubLen * 1.24f), paint);

        boolean blink = ((int) (starPhase * 7f)) % 2 == 0;
        for (int b = -1; b <= 1; b++) {
            float bx = b * (halfW * 0.56f), by = stubLen * 0.42f;
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRoundRect(new RectF(bx - 12f * dp, by - 3.5f * dp, bx + 12f * dp, by + 3.5f * dp), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawRect(bx - 5f * dp, by - 3.5f * dp, bx + 5f * dp, by + 3.5f * dp, paint);
            paint.setColor(blink ? Color.parseColor("#EF4444") : Color.parseColor("#38BDF8"));
            canvas.drawCircle(bx, by - 4.8f * dp, 3.0f * dp, paint);
        }
        canvas.restore();
    }



    /**
     * Renders the Ultra-Detailed 10x-Area 2.5D Active Stratovolcano & Molten Caldera Mega-Zone (Images 1 & 2):
     * - 3x3 Scorched basaltic ash wasteland, ropy pahoehoe lava plateaus & 8 lava-engulfed severed city road stubs
     * - 3-tiered jagged basalt shield aprons, 120-sector 3D faceted volcanic ridges & ropy pahoehoe rock folds
     * - Glowing surface lava tube fractures winding across the dark outer slopes (Image 2 background)
     * - Breached South-East crater rim with a braided river of incandescent yellow-orange lava pouring down the slope
     * - Deep abyssal caldera throat with overhanging obsidian walls & vertical dripping lava cascades (Image 2)
     * - Glistening multi-layered magma lake with a swirling golden-orange convection vortex (Image 2) AND
     *   a deep magma-skin recess fractured by lightning-like crimson/orange/yellow veins (Image 1)
     * - Billowing 2.5D pyroclastic ash & steam clouds rising from the upper-left rim with fire-lit underbellies.
     */
    private void drawDetailedVolcano25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int ashAbyssCol     = Color.parseColor("#0B0C10");
        int basaltDeepCol   = isSunnyDay ? Color.parseColor("#14171F") : Color.parseColor("#0D0F14");
        int basaltMidCol    = isSunnyDay ? Color.parseColor("#1E232E") : Color.parseColor("#151922");
        int basaltLightCol  = isSunnyDay ? Color.parseColor("#2E3545") : Color.parseColor("#222733");
        int basaltSlateRim  = isSunnyDay ? Color.parseColor("#3E4759") : Color.parseColor("#2C3340");
        int scorchedRedCol  = Color.parseColor("#2A120C");
        int crustMaroonCol  = Color.parseColor("#47120B");

        float pulseFast = 0.5f + 0.5f * (float) Math.sin(starPhase * 5.2f);
        float pulseSlow = 0.5f + 0.5f * (float) Math.sin(starPhase * 2.1f);

        // =========================================================================
        // 1. 3x3 MEGA-ZONE OBSIDIAN ASH WASTELAND & LAVA-ENGULFED CITY ROAD STUBS
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#77000000"));
        canvas.drawRoundRect(new RectF(cx - w + 16f * dp, cy - h + 18f * dp, cx + w + 16f * dp, cy + h + 18f * dp), 38f * dp, 38f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(ashAbyssCol);
        canvas.drawRoundRect(tempRect, 38f * dp, 38f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 38f * dp, 38f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        float volCX = cx - w * 0.02f, volCY = cy - h * 0.02f;
        float coneRX = w * 0.78f, coneRY = h * 0.74f;
        float calRX  = w * 0.36f, calRY  = h * 0.28f;

        // Outer Basaltic Ash Aprons (Organic Multi-Harmonic Lava Field Contours)
        buildVolcanicFlowPath(tempPath, cx, cy, w * 0.96f, h * 0.95f, 0.5f);
        paint.setColor(basaltDeepCol);
        canvas.drawPath(tempPath, paint);

        buildVolcanicFlowPath(tempPath, volCX, volCY, coneRX * 1.18f, coneRY * 1.18f, 1.7f);
        paint.setColor(basaltMidCol);
        canvas.drawPath(tempPath, paint);

        // 8 Severed City Road Stubs Overrun by Cooling Pahoehoe Lava Tongues
        float[] severedOffsets = { -gridStep * 0.5f, gridStep * 0.5f };
        float stubRoadW = 140f * dp;
        for (float ro : severedOffsets) {
            drawLavaEngulfedRoadStub(canvas, paint, strokePaint, cx + ro, cy - h, stubRoadW, 140f * dp, 0f, starPhase);
            drawLavaEngulfedRoadStub(canvas, paint, strokePaint, cx + ro, cy + h, stubRoadW, 140f * dp, 180f, starPhase);
            drawLavaEngulfedRoadStub(canvas, paint, strokePaint, cx - w, cy + ro, stubRoadW, 140f * dp, 270f, starPhase);
            drawLavaEngulfedRoadStub(canvas, paint, strokePaint, cx + w, cy + ro, stubRoadW, 140f * dp, 90f, starPhase);
        }

        // =========================================================================
        // 2. LAYEREDPAHOEHOE LAVA SHIELD, 120 3D BASALT RIDGES & ROPY ROCK FOLDS
        // =========================================================================
        float[] shieldScales = { 1.06f, 0.92f, 0.78f };
        int[] shieldColors   = { basaltDeepCol, basaltMidCol, basaltLightCol };
        for (int s = 0; s < shieldScales.length; s++) {
            buildVolcanicFlowPath(tempPath, volCX + 12f * dp, volCY + 14f * dp, coneRX * shieldScales[s], coneRY * shieldScales[s], s * 1.9f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#55000000"));
            canvas.drawPath(tempPath, paint);

            buildVolcanicFlowPath(tempPath, volCX, volCY, coneRX * shieldScales[s], coneRY * shieldScales[s], s * 1.9f);
            paint.setColor(shieldColors[s]);
            canvas.drawPath(tempPath, paint);
        }

        // 120-Sector 3D Faceted Volcanic Slope Ridges, Chutes & Pahoehoe Creases
        int slopeSectors = 120;
        double dAng = (Math.PI * 2.0) / slopeSectors;
        for (int i = 0; i < slopeSectors; i++) {
            double a1 = i * dAng;
            double aMid = (i + 0.5) * dAng;
            double a2 = (i + 1.0) * dAng;

            float[] rim1 = evalCalderaRimPoint(a1, calRX, calRY, 1.04f);
            float[] rim2 = evalCalderaRimPoint(a2, calRX, calRY, 1.04f);

            float midReach = 0.62f + (i % 4) * 0.045f;
            float[] midM = evalCalderaRimPoint(aMid, coneRX, coneRY, midReach);

            float toeReach = 0.94f + (i % 5) * 0.04f;
            float[] toe1 = evalCalderaRimPoint(a1, coneRX, coneRY, toeReach);
            float[] toe2 = evalCalderaRimPoint(a2, coneRX, coneRY, toeReach);

            // Cool slate-blue highlight on NW slopes (Image 1) + warm magma rim-glow near caldera (Image 2)
            float nwLight = (float) Math.cos(aMid + 2.25);
            int ridgeCol = (nwLight > 0.1f) ? basaltSlateRim : ((i % 2 == 0) ? basaltLightCol : basaltMidCol);
            int gullyCol = (nwLight > 0.1f) ? basaltMidCol : basaltDeepCol;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ridgeCol);
            tempPath.reset();
            tempPath.moveTo(volCX + rim1[0], volCY + rim1[1]);
            tempPath.lineTo(volCX + rim2[0], volCY + rim2[1]);
            tempPath.lineTo(volCX + midM[0], volCY + midM[1]);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            paint.setColor(gullyCol);
            tempPath.reset();
            tempPath.moveTo(volCX + rim2[0], volCY + rim2[1]);
            tempPath.lineTo(volCX + toe2[0], volCY + toe2[1]);
            tempPath.lineTo(volCX + midM[0], volCY + midM[1]);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Deep Basalt Crevasse Line Between Ridges
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(ashAbyssCol);
            strokePaint.setAlpha(190);
            strokePaint.setStrokeWidth((1.8f + (i % 3) * 1.1f) * dp);
            canvas.drawLine(volCX + rim1[0], volCY + rim1[1], volCX + toe1[0], volCY + toe1[1], strokePaint);
        }
        strokePaint.setAlpha(255);

        // Ropy Pahoehoe Folded Basalt Contours (replicating the coiled rock texture of Image 1)
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int rf = 0; rf < 84; rf++) {
            double rAng = rf * (Math.PI * 2.0 / 84.0) + (rf % 3) * 0.08;
            float distScale = 1.14f + (rf % 7) * 0.15f;
            float[] pCenter = evalCalderaRimPoint(rAng, calRX, calRY, distScale);
            float px = volCX + pCenter[0], py = volCY + pCenter[1];
            float arcW = (18f + (rf % 5) * 7f) * dp;
            float rotDeg = (float) Math.toDegrees(rAng) + 90f;

            canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotDeg);
            // Draw 3 nested ropy basalt folds per lobe
            for (int fold = 0; fold < 3; fold++) {
                float fy = fold * 4.5f * dp;
                strokePaint.setColor((fold % 2 == 0) ? basaltSlateRim : ashAbyssCol);
                strokePaint.setAlpha(185);
                strokePaint.setStrokeWidth(2.4f * dp);
                canvas.drawArc(new RectF(-arcW, fy - 6f * dp, arcW, fy + 6f * dp), 200f, 140f, false, strokePaint);
            }
            canvas.restore();
        }
        strokePaint.setAlpha(255);

        // =========================================================================
        // 3. GLOWING OUTER SURFACE LAVA FRACTURES & PARASITIC FUMAROLE CONES
        //    (Matching the glowing red/orange cracks in the upper slopes of Image 2)
        // =========================================================================
        float[][] outerLavaStreams = {
                { -2.55f, 2.15f, -0.35f }, // NW winding lava crack (top-left of Image 2)
                { -2.10f, 1.95f,  0.28f }, // Upper-left secondary lava vein
                { -1.45f, 1.80f, -0.22f }, // North rim glowing fracture
                { -0.75f, 1.85f,  0.25f }, // NE slope glowing fissure
                {  2.65f, 1.90f,  0.30f }, // West-SW glowing lava stream
                {  1.85f, 1.75f, -0.26f }  // South slope fracture
        };
        for (float[] ols : outerLavaStreams) {
            float[] startPt = evalCalderaRimPoint(ols[0], calRX, calRY, 1.02f);
            drawSlopeLavaStream(canvas, paint, strokePaint,
                    volCX + startPt[0], volCY + startPt[1],
                    ols[0], calRX * ols[1], ols[2], 14f * dp, pulseFast);
        }

        // 3 Secondary Parasitic Spatter Cones / Sulfur Fumarole Vents on the Outer Flanks
        float[][] parasiticVents = {
                { -0.64f, -0.52f, 44f }, // NW parasitic vent
                {  0.66f, -0.44f, 38f }, // NE parasitic vent
                { -0.58f,  0.54f, 42f }  // SW parasitic vent
        };
        for (int pv = 0; pv < parasiticVents.length; pv++) {
            float vx = cx + parasiticVents[pv][0] * w;
            float vy = cy + parasiticVents[pv][1] * h;
            float vr = parasiticVents[pv][2] * dp;
            drawCraggyRock25D(canvas, paint, vx, vy, vr, basaltDeepCol, basaltSlateRim, pv * 2.1f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#180704"));
            canvas.drawCircle(vx, vy, vr * 0.48f, paint);
            paint.setColor(Color.parseColor("#EA580C"));
            canvas.drawCircle(vx, vy, (vr * 0.28f) + pulseFast * 3f * dp, paint);
            paint.setColor(Color.parseColor("#FEF08A"));
            canvas.drawCircle(vx, vy, vr * 0.14f, paint);
            drawConstructionDustAndSmoke(canvas, paint, vx, vy, starPhase * 0.9f + pv, false);
        }

        // =========================================================================
        // 4. DEEP ABYSSAL CALDERA THROAT, MOLTEN MAGMA VORTEX & CRACKED SKIN VEINS
        //    (Combining Image 1's veined abyss & Image 2's churning golden lava pit)
        // =========================================================================
        // Outer Scorched Caldera Collar Glow
        buildCalderaPath(tempPath, volCX, volCY, calRX * 1.08f, calRY * 1.08f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(scorchedRedCol);
        canvas.drawPath(tempPath, paint);

        // Clip Inside the Jagged Caldera Opening
        canvas.save();
        buildCalderaPath(tempPath, volCX, volCY, calRX, calRY);
        canvas.clipPath(tempPath);

        // A. Deep Overhanging Inner Caldera Throat Walls (Pitch-Black & Dark Blood-Crimson)
        paint.setColor(Color.parseColor("#0D0403"));
        canvas.drawRect(volCX - calRX, volCY - calRY, volCX + calRX, volCY + calRY, paint);

        // Stepped Inner Cliff Escarpments Descending into the Magma Lake
        float[] innerWallScales = { 0.94f, 0.86f, 0.78f };
        int[] innerWallCols     = { Color.parseColor("#1C0806"), Color.parseColor("#2E0B07"), Color.parseColor("#471109") };
        for (int iw = 0; iw < innerWallScales.length; iw++) {
            buildCalderaPath(tempPath, volCX + iw * 4f * dp, volCY + (iw + 1) * 9f * dp,
                    calRX * innerWallScales[iw], calRY * innerWallScales[iw]);
            paint.setColor(innerWallCols[iw]);
            canvas.drawPath(tempPath, paint);
        }

        // B. Glowing Vertical Lava Cascades Dripping Down the Inner Cliff Walls (Image 2 Upper-Left & Right Walls!)
        float[][] wallCascades = {
                { -2.35f, 0.96f, 0.52f,  0.12f }, // Prominent double lava waterfall on NW inner wall (Image 2)
                { -2.12f, 0.96f, 0.48f, -0.10f },
                { -1.65f, 0.95f, 0.56f,  0.08f },
                { -0.45f, 0.96f, 0.50f, -0.14f }, // Blazing right-wall lava cascade (Image 2)
                { -0.15f, 0.98f, 0.44f,  0.10f },
                {  0.22f, 0.96f, 0.46f, -0.08f },
                {  2.55f, 0.94f, 0.54f,  0.11f }
        };
        for (float[] wc : wallCascades) {
            drawInnerWallLavaCascade(canvas, paint, strokePaint, volCX, volCY, calRX, calRY,
                    wc[0], wc[1], wc[2], wc[3], pulseFast);
        }

        // C. Main Molten Magma Lake Floor (Shifted slightly SE so the NW overhanging cliff is visible like Image 1 & 2)
        float lakeCX = volCX + calRX * 0.06f, lakeCY = volCY + calRY * 0.14f;
        float lakeRX = calRX * 0.78f, lakeRY = calRY * 0.72f;

        buildCalderaPath(tempPath, lakeCX, lakeCY, lakeRX, lakeRY);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new android.graphics.RadialGradient(
                lakeCX, lakeCY + lakeRY * 0.12f, Math.max(lakeRX, lakeRY),
                new int[]{
                        Color.parseColor("#FFFFFF"),
                        Color.parseColor("#FEF08A"),
                        Color.parseColor("#FACC15"),
                        Color.parseColor("#F97316"),
                        Color.parseColor("#DC2626"),
                        Color.parseColor("#7F1D1D"),
                        Color.parseColor("#280705")
                },
                new float[]{ 0f, 0.16f + pulseFast * 0.05f, 0.34f, 0.56f, 0.76f, 0.90f, 1f },
                android.graphics.Shader.TileMode.CLAMP
        ));
        canvas.drawPath(tempPath, paint);
        paint.setShader(null);

        // D. Dark Cooling Magma Skin & Lightning-Like Glowing Magma Veins (Exact match to Image 1's inner pit!)
        // Semi-cooled dark-crimson/obsidian crust plates floating over the upper/outer magma lake
        for (int p = 0; p < 14; p++) {
            double pAng = p * (Math.PI * 2.0 / 14.0) + starPhase * 0.06;
            float pDist = 0.42f + (p % 3) * 0.18f;
            float px = lakeCX + (float) Math.cos(pAng) * (lakeRX * pDist);
            float py = lakeCY + (float) Math.sin(pAng) * (lakeRY * pDist);
            float plateW = lakeRX * (0.18f + (p % 4) * 0.04f);
            float plateH = lakeRY * (0.12f + (p % 3) * 0.03f);
            buildVolcanicFlowPath(tempPath, px, py, plateW, plateH, p * 1.3f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((p % 2 == 0) ? Color.parseColor("#AA2A0805") : Color.parseColor("#99450A0A"));
            canvas.drawPath(tempPath, paint);
        }

        // Polygonal Web of Incandescent Magma Veins (Image 1's glowing red/orange/yellow crack network)
        drawCalderaMagmaVeins(canvas, strokePaint, lakeCX, lakeCY, lakeRX * 0.90f, lakeRY * 0.85f, starPhase, pulseFast);

        // E. Swirling Golden-Yellow Ropy Magma Vortex in the Lower-Center Pit (Exact match to Image 2 center!)
        float vortexCX = lakeCX + lakeRX * 0.04f, vortexCY = lakeCY + lakeRY * 0.10f;
        float vortexR  = Math.min(lakeRX, lakeRY) * 0.58f;
        drawChurningMagmaVortex(canvas, paint, strokePaint, vortexCX, vortexCY, vortexR, starPhase, pulseFast);

        // F. Bursting Molten Lava Fountains, Blisters & Flying Incandescent Embers
        float[][] bubbleHubs = {
                {  0.00f,  0.08f }, { -0.28f, -0.12f }, {  0.32f, -0.05f },
                { -0.18f,  0.26f }, {  0.24f,  0.28f }
        };
        for (int b = 0; b < bubbleHubs.length; b++) {
            float bx = lakeCX + bubbleHubs[b][0] * lakeRX;
            float by = lakeCY + bubbleHubs[b][1] * lakeRY;
            float bp = ((starPhase * 0.65f) + b * 0.21f) % 1.0f;
            float bRad = (4f + bp * 26f) * dp;

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.argb(Math.max(0, Math.round((1f - bp) * 230f)), 254, 240, 138));
            strokePaint.setStrokeWidth((3.2f - bp * 2.0f) * dp);
            canvas.drawCircle(bx, by, bRad, strokePaint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(bx, by, (3.5f + (1f - bp) * 3f) * dp, paint);
        }

        canvas.restore(); // End interior caldera clip

        // =========================================================================
        // 5. BREACHED SOUTH-EAST RIM & OVERFLOWING INCANDESCENT LAVA RIVER
        //    (Matching the blazing yellow-orange lava spillway in the bottom-right of Image 2!)
        // =========================================================================
        drawBreachedRimLavaRiver(canvas, paint, strokePaint, volCX, volCY, calRX, calRY, coneRX, coneRY, starPhase, pulseFast);

        // =========================================================================
        // 6. OVERHANGING 3D CRAGGY CALDERA RIM MONOLITHS & INCANDESCENT INNER LIP
        // =========================================================================
        // Blazing Orange-Red Rim Reflection Along the Jagged Inner Lip
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.argb((int) (180 + pulseFast * 75), 249, 115, 22));
        strokePaint.setStrokeWidth(4.5f * dp);
        buildCalderaPath(tempPath, volCX, volCY, calRX, calRY);
        canvas.drawPath(tempPath, strokePaint);

        // 72 Interlocking 3D Polygonal Basalt Rim Crags & Overhanging Outcrops (Skip the SE breached spillway angle ~0.45..0.85 rad)
        for (int r = 0; r < 72; r++) {
            double ang = r * (Math.PI * 2.0 / 72.0);
            if (ang > 0.38 && ang < 0.88) continue; // Leave the SE breach open for the overflowing lava river!

            float[] pt = evalCalderaRimPoint(ang, calRX, calRY, 1.02f + (r % 3) * 0.025f);
            float rxp = volCX + pt[0], ryp = volCY + pt[1];
            float cragR = (10f + (r % 5) * 5.5f) * dp;

            // Huge craggy promontory buttresses on the East and West rim (like Image 2's right-side rock tower)
            if (Math.abs(ang - 0.15) < 0.22 || Math.abs(ang - 2.85) < 0.25) {
                cragR *= 1.65f;
            }

            int baseC = (r % 2 == 0) ? basaltDeepCol : ashAbyssCol;
            int highC = (Math.cos(ang + 2.2) > 0) ? basaltSlateRim : basaltLightCol;
            drawCraggyRock25D(canvas, paint, rxp, ryp, cragR, baseC, highC, r * 1.3f);

            // Red-hot magma underglow on the inner edge of each rim crag
            if (r % 2 == 0) {
                float[] inPt = evalCalderaRimPoint(ang, calRX, calRY, 0.96f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb((int) (150 + pulseFast * 90), 239, 68, 68));
                canvas.drawCircle(volCX + inPt[0], volCY + inPt[1], 3.5f * dp, paint);
            }
        }

        // =========================================================================
        // 7. EJECTED VOLCANIC BOMBS, VULCANOLOGY MONITORING CAMP & HELICOPTER
        // =========================================================================
        // Smoking Ejected Volcanic Bombs (Glowing Tephra Boulders) Across the Outer Ash Plain
        for (int vb = 0; vb < 36; vb++) {
            double bAng = vb * 2.39996;
            float distF = 1.18f + (vb % 7) * 0.14f;
            float bx = volCX + (float) Math.cos(bAng) * (calRX * distF);
            float by = volCY + (float) Math.sin(bAng) * (calRY * distF);
            float bRad = (4.5f + (vb % 4) * 2.5f) * dp;
            drawCraggyRock25D(canvas, paint, bx, by, bRad, ashAbyssCol, basaltLightCol, vb * 1.7f);
            if (vb % 3 == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#F97316"));
                canvas.drawCircle(bx - 1f * dp, by - 1f * dp, 2.2f * dp, paint);
            }
        }

        // Heat-Shielded Vulcanology Research & Seismic Outpost (South-West Safe Plateau)
        float campX = cx - w * 0.62f, campY = cy + h * 0.56f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawRoundRect(new RectF(campX - 46f * dp, campY - 32f * dp, campX + 46f * dp, campY + 32f * dp), 8f * dp, 8f * dp, paint);
        // 2 Silver Thermal-Reflective Geodesic Domes & Orange Seismic Sensor Lab
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawCircle(campX - 18f * dp, campY - 8f * dp, 12f * dp, paint);
        canvas.drawCircle(campX + 8f * dp, campY - 10f * dp, 10f * dp, paint);
        paint.setColor(Color.parseColor("#EA580C"));
        canvas.drawRoundRect(new RectF(campX - 24f * dp, campY + 8f * dp, campX + 14f * dp, campY + 22f * dp), 3f * dp, 3f * dp, paint);
        // Emergency Evacuation Trucks with Flashing Strobes
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawRoundRect(new RectF(campX + 22f * dp, campY - 4f * dp, campX + 38f * dp, campY + 6f * dp), 2f * dp, 2f * dp, paint);
        canvas.drawRoundRect(new RectF(campX + 22f * dp, campY + 10f * dp, campX + 38f * dp, campY + 20f * dp), 2f * dp, 2f * dp, paint);

        // =========================================================================
        // 8. BILLOWING 2.5D PYROCLASTIC SMOKE & STEAM CLOUDS (UPPER-LEFT RIM, IMAGE 2)
        // =========================================================================
        drawVolcanicPyroclasticPlume(canvas, paint, strokePaint,
                volCX - calRX * 0.42f, volCY - calRY * 0.72f, w * 0.34f, starPhase, pulseFast);

        // Secondary Fumarole Steam Wisps on the North-East & Right Rim
        drawVolcanicPyroclasticPlume(canvas, paint, strokePaint,
                volCX + calRX * 0.56f, volCY - calRY * 0.42f, w * 0.16f, starPhase + 2.4f, pulseSlow);

        // Orbiting Vulcanology Heat-Shielded Helicopter Monitoring the Eruption
        float heliAng = -starPhase * 0.26f;
        float heliX = volCX + (float) Math.cos(heliAng) * (calRX * 1.15f);
        float heliY = volCY + (float) Math.sin(heliAng) * (calRY * 1.15f);
        canvas.save();
        canvas.translate(heliX, heliY);
        canvas.rotate((float) Math.toDegrees(heliAng) - 90f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(65, 56, 189, 248)); // Thermal scanner beam
        tempPath.reset();
        tempPath.moveTo(0f, 0f);
        tempPath.lineTo(44f * dp, -18f * dp);
        tempPath.lineTo(44f * dp, 18f * dp);
        tempPath.close();
        canvas.drawPath(tempPath, paint);
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawRect(-18f * dp, -2f * dp, -2f * dp, 2f * dp, paint);
        canvas.drawOval(new RectF(-7f * dp, -5.5f * dp, 12f * dp, 5.5f * dp), paint);
        paint.setColor(Color.parseColor("#0F172A"));
        canvas.drawOval(new RectF(2f * dp, -4.2f * dp, 11f * dp, 4.2f * dp), paint);
        canvas.rotate(starPhase * 30f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(2.0f * dp);
        canvas.drawLine(-18f * dp, -18f * dp, 18f * dp, 18f * dp, strokePaint);
        canvas.drawLine(-18f * dp, 18f * dp, 18f * dp, -18f * dp, strokePaint);
        canvas.restore();

        canvas.restore(); // End 3x3 Mega-Zone clip

        // Outer Volcanic Hazard Border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#7F1D1D"));
        strokePaint.setStrokeWidth(3.8f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 38f * dp, 38f * dp, strokePaint);
    }

    /**
     * Renders the swirling, ropy golden-orange magma convection vortex in the center of the caldera (Image 2).
     */
    private void drawChurningMagmaVortex(Canvas canvas, Paint paint, Paint strokePaint, float vx, float vy, float maxR, float starPhase, float pulse) {
        canvas.save();
        canvas.translate(vx, vy);
        canvas.rotate(starPhase * 8f); // Slow churning rotation of the molten lava pool

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        // 4 Intertwined Archimedean Ropy Magma Spirals (Alternating Dark Cooling Skin & Blinding Yellow-Orange Fire)
        int arms = 4;
        for (int arm = 0; arm < arms; arm++) {
            double armOffset = arm * (Math.PI * 2.0 / arms);
            tempPath.reset();
            int steps = 36;
            for (int s = 0; s <= steps; s++) {
                float t = s / (float) steps;
                double theta = armOffset + t * Math.PI * 3.2 + Math.sin(t * 6.0 + starPhase) * 0.18;
                float r = t * maxR;
                float px = (float) Math.cos(theta) * r;
                float py = (float) Math.sin(theta) * (r * 0.82f);
                if (s == 0) tempPath.moveTo(px, py);
                else tempPath.lineTo(px, py);
            }

            // Dark ropy crust fold shadow
            strokePaint.setColor(Color.parseColor("#88450A0A"));
            strokePaint.setStrokeWidth(8.5f * dp);
            canvas.drawPath(tempPath, strokePaint);

            // Blazing orange-gold ropy magma filament
            strokePaint.setColor(Color.argb((int) (200 + pulse * 55), 249, 115, 22));
            strokePaint.setStrokeWidth(5.0f * dp);
            canvas.drawPath(tempPath, strokePaint);

            // White-hot yellow liquid fire core
            strokePaint.setColor(Color.argb((int) (180 + pulse * 75), 254, 240, 138));
            strokePaint.setStrokeWidth(2.2f * dp);
            canvas.drawPath(tempPath, strokePaint);
        }

        // Central White-Hot Upwelling Magma Eye
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawOval(new RectF(-maxR * 0.22f, -maxR * 0.18f, maxR * 0.22f, maxR * 0.18f), paint);
        paint.setColor(Color.WHITE);
        canvas.drawOval(new RectF(-maxR * 0.10f, -maxR * 0.08f, maxR * 0.10f, maxR * 0.08f), paint);

        canvas.restore();
    }

    /**
     * Renders the jagged, lightning-like network of glowing crimson/orange/yellow magma veins across the crust (Image 1).
     */
    private void drawCalderaMagmaVeins(Canvas canvas, Paint strokePaint, float cx, float cy, float rx, float ry, float starPhase, float pulse) {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        int mainVeins = 12;
        for (int v = 0; v < mainVeins; v++) {
            double baseAng = v * (Math.PI * 2.0 / mainVeins) + 0.2;
            tempPath.reset();
            float curX = cx + (float) Math.cos(baseAng) * (rx * 0.18f);
            float curY = cy + (float) Math.sin(baseAng) * (ry * 0.18f);
            tempPath.moveTo(curX, curY);

            int segs = 5;
            float midBranchX = curX, midBranchY = curY;
            for (int s = 1; s <= segs; s++) {
                float t = 0.18f + (s / (float) segs) * 0.78f;
                float jag = ((s + v) % 2 == 0 ? 1f : -1f) * 0.16f + (float) Math.sin(starPhase * 2.2f + v + s) * 0.04f;
                double a = baseAng + jag;
                curX = cx + (float) Math.cos(a) * (rx * t);
                curY = cy + (float) Math.sin(a) * (ry * t);
                tempPath.lineTo(curX, curY);
                if (s == 2) { midBranchX = curX; midBranchY = curY; }
            }

            // Outer blood-red magma halo
            strokePaint.setColor(Color.argb(190, 185, 28, 28));
            strokePaint.setStrokeWidth(6.0f * dp);
            canvas.drawPath(tempPath, strokePaint);

            // Mid fiery orange vein
            strokePaint.setColor(Color.argb((int) (195 + pulse * 60), 249, 115, 22));
            strokePaint.setStrokeWidth(3.2f * dp);
            canvas.drawPath(tempPath, strokePaint);

            // Inner blinding yellow-white core crack
            strokePaint.setColor(Color.argb((int) (165 + pulse * 90), 254, 240, 138));
            strokePaint.setStrokeWidth(1.4f * dp);
            canvas.drawPath(tempPath, strokePaint);

            // Forked sub-vein connecting across the crust plates
            double forkAng = baseAng + ((v % 2 == 0) ? 0.55 : -0.55);
            float fx = midBranchX + (float) Math.cos(forkAng) * (rx * 0.28f);
            float fy = midBranchY + (float) Math.sin(forkAng) * (ry * 0.28f);
            strokePaint.setColor(Color.parseColor("#EA580C"));
            strokePaint.setStrokeWidth(2.4f * dp);
            canvas.drawLine(midBranchX, midBranchY, fx, fy, strokePaint);
            strokePaint.setColor(Color.parseColor("#FEF08A"));
            strokePaint.setStrokeWidth(1.0f * dp);
            canvas.drawLine(midBranchX, midBranchY, fx, fy, strokePaint);
        }
    }

    /**
     * Renders a vertical glowing lava cascade dripping down the steep inner caldera wall (Image 2).
     */
    private void drawInnerWallLavaCascade(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float calRX, float calRY,
                                          float ang, float startScale, float endScale, float wiggle, float pulse) {
        int segs = 5;
        tempPath.reset();
        float firstX = 0f, firstY = 0f, lastX = 0f, lastY = 0f;
        for (int s = 0; s <= segs; s++) {
            float t = s / (float) segs;
            float sc = startScale - t * (startScale - endScale);
            double a = ang + Math.sin(t * Math.PI * 2.0) * wiggle;
            float[] pt = evalCalderaRimPoint(a, calRX, calRY, sc);
            float px = cx + pt[0], py = cy + pt[1] + t * 14f * dp;
            if (s == 0) { tempPath.moveTo(px, py); firstX = px; firstY = py; }
            else tempPath.lineTo(px, py);
            if (s == segs) { lastX = px; lastY = py; }
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(Color.argb(190, 153, 27, 27));
        strokePaint.setStrokeWidth(11f * dp);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.argb((int) (200 + pulse * 55), 249, 115, 22));
        strokePaint.setStrokeWidth(6.5f * dp);
        canvas.drawPath(tempPath, strokePaint);

        strokePaint.setColor(Color.parseColor("#FEF08A"));
        strokePaint.setStrokeWidth(2.8f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Splash pool where the cascade hits the magma lake
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#FACC15"));
        canvas.drawCircle(lastX, lastY, 5.5f * dp, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(firstX, firstY, 2.5f * dp, paint);
    }

    /**
     * Renders the breached South-East caldera spillway & braided incandescent lava river (Bottom-Right of Image 2).
     */
    private void drawBreachedRimLavaRiver(Canvas canvas, Paint paint, Paint strokePaint, float volCX, float volCY,
                                          float calRX, float calRY, float coneRX, float coneRY, float starPhase, float pulse) {
        double spillAng = 0.62; // South-East breach angle (matching bottom-right of Image 2)
        float[] startPt = evalCalderaRimPoint(spillAng, calRX, calRY, 0.88f);
        float[] midPt   = evalCalderaRimPoint(spillAng + 0.08, coneRX, coneRY, 0.72f);
        float[] endPt   = evalCalderaRimPoint(spillAng - 0.05, coneRX, coneRY, 1.08f);

        float sx = volCX + startPt[0], sy = volCY + startPt[1];
        float mx = volCX + midPt[0],   my = volCY + midPt[1];
        float ex = volCX + endPt[0],   ey = volCY + endPt[1];

        tempPath.reset();
        tempPath.moveTo(sx, sy);
        tempPath.quadTo(mx, my, ex, ey);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);

        // Scorched black basalt levee banks
        strokePaint.setColor(Color.parseColor("#140705"));
        strokePaint.setStrokeWidth(48f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Deep crimson outer lava channel
        strokePaint.setColor(Color.parseColor("#991B1B"));
        strokePaint.setStrokeWidth(36f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Blazing orange molten river body
        strokePaint.setColor(Color.parseColor("#F97316"));
        strokePaint.setStrokeWidth(24f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Incandescent yellow-white central lava current
        strokePaint.setColor(Color.argb((int) (210 + pulse * 45), 254, 240, 138));
        strokePaint.setStrokeWidth(12f * dp);
        canvas.drawPath(tempPath, strokePaint);

        // Dark Floating Basalt Crust Rafts & Animated Flowing Lavalobes Inside the River
        paint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 7; k++) {
            float prog = ((starPhase * 0.22f) + k / 7.0f) % 1.0f;
            float inv = 1f - prog;
            float lx = inv * inv * sx + 2f * inv * prog * mx + prog * prog * ex;
            float ly = inv * inv * sy + 2f * inv * prog * my + prog * prog * ey;
            paint.setColor((k % 2 == 0) ? Color.parseColor("#27100B") : Color.parseColor("#FFFFFF"));
            float rad = ((k % 2 == 0) ? 5.5f : 3.5f) * dp;
            canvas.drawOval(new RectF(lx - rad * 1.4f, ly - rad, lx + rad * 1.4f, ly + rad), paint);
        }

        // Pooled Molten Lava Delta Fan at the Toe of the Slope
        buildVolcanicFlowPath(tempPath, ex, ey, 46f * dp, 34f * dp, 2.4f);
        paint.setColor(Color.parseColor("#7F1D1D"));
        canvas.drawPath(tempPath, paint);
        buildVolcanicFlowPath(tempPath, ex - 4f * dp, ey - 4f * dp, 32f * dp, 24f * dp, 1.1f);
        paint.setColor(Color.parseColor("#F97316"));
        canvas.drawPath(tempPath, paint);
        paint.setColor(Color.parseColor("#FEF08A"));
        canvas.drawCircle(ex - 6f * dp, ey - 5f * dp, 10f * dp, paint);
    }

    /**
     * Renders a glowing surface lava fracture stream winding down the outer dark basalt cone.
     */
    private void drawSlopeLavaStream(Canvas canvas, Paint paint, Paint strokePaint, float startX, float startY,
                                     float baseAng, float length, float bend, float startW, float pulse) {
        int segs = 6;
        float[] px = new float[segs + 1];
        float[] py = new float[segs + 1];
        px[0] = startX;
        py[0] = startY;

        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            double a = baseAng + bend * t + Math.sin(t * Math.PI * 2.5) * 0.14;
            px[i] = startX + (float) Math.cos(a) * (length * t);
            py[i] = startY + (float) Math.sin(a) * (length * t * 0.84f);
        }

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < segs; i++) {
            float wFrac = 1f - (i / (float) segs) * 0.72f;
            strokePaint.setColor(Color.parseColor("#2A0A06"));
            strokePaint.setStrokeWidth(startW * wFrac);
            canvas.drawLine(px[i], py[i], px[i + 1], py[i + 1], strokePaint);

            strokePaint.setColor(Color.argb((int) (185 + pulse * 70), 234, 88, 12));
            strokePaint.setStrokeWidth(startW * 0.52f * wFrac);
            canvas.drawLine(px[i], py[i], px[i + 1], py[i + 1], strokePaint);

            strokePaint.setColor(Color.argb((int) (160 + pulse * 95), 254, 240, 138));
            strokePaint.setStrokeWidth(startW * 0.22f * wFrac);
            canvas.drawLine(px[i], py[i], px[i + 1], py[i + 1], strokePaint);
        }
    }

    /**
     * Renders the billowing 2.5D pyroclastic ash & white/grey steam plume rising from the upper-left rim (Image 2).
     */
    private void drawVolcanicPyroclasticPlume(Canvas canvas, Paint paint, Paint strokePaint, float ventX, float ventY, float maxSpan, float starPhase, float pulse) {
        paint.setStyle(Paint.Style.FILL);
        int puffs = 14;
        for (int i = 0; i < puffs; i++) {
            float p = ((starPhase * 0.18f) + (i / (float) puffs)) % 1.0f;
            // Plume drifts North-West (up and left) just like the smoke column in Image 2
            float driftX = ventX - p * maxSpan * 0.85f + (float) Math.sin(p * 5f + i) * 14f * dp;
            float driftY = ventY - p * maxSpan * 0.72f + (float) Math.cos(p * 4f + i) * 10f * dp;
            float rad = (18f + p * 54f) * dp;
            int alpha = Math.max(0, Math.round((1f - p * 0.75f) * 215f));

            // 1. Drop shadow of the smoke cloud on the volcano slope
            paint.setColor(Color.argb(Math.min(110, alpha / 2), 0, 0, 0));
            canvas.drawCircle(driftX + 14f * dp, driftY + 18f * dp, rad * 1.05f, paint);

            // 2. Warm orange-red magma reflection on the lower underbelly of the smoke puffs near the rim
            if (p < 0.45f) {
                int glowAlpha = Math.max(0, Math.round((0.45f - p) * 260f));
                paint.setColor(Color.argb(glowAlpha, 234, 88, 12));
                canvas.drawCircle(driftX + 4f * dp, driftY + 6f * dp, rad * 0.92f, paint);
            }

            // 3. Dense grey-ash & billowing white steam cloud layers (matching Image 2's bright white/grey plume)
            paint.setColor(Color.argb(alpha, 148, 163, 184));
            canvas.drawCircle(driftX, driftY, rad, paint);

            paint.setColor(Color.argb(alpha, 226, 232, 240));
            canvas.drawCircle(driftX - rad * 0.18f, driftY - rad * 0.18f, rad * 0.74f, paint);

            paint.setColor(Color.argb(alpha, 248, 250, 252));
            canvas.drawCircle(driftX - rad * 0.28f, driftY - rad * 0.28f, rad * 0.45f, paint);
        }
        paint.setAlpha(255);
    }

    private float[] evalCalderaRimPoint(double t, float rx, float ry, float scale) {
        double r = 0.90
                + 0.075 * Math.cos(3.0 * t + 0.4)
                + 0.055 * Math.sin(5.0 * t - 0.6)
                + 0.038 * Math.cos(8.0 * t + 1.1)
                + 0.022 * Math.sin(13.0 * t);
        return new float[]{
                (float) (rx * scale * r * Math.cos(t)),
                (float) (ry * scale * r * Math.sin(t))
        };
    }

    private void buildCalderaPath(Path out, float cx, float cy, float rx, float ry) {
        out.reset();
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            float[] pt = evalCalderaRimPoint(t, rx, ry, 1.0f);
            if (i == 0) out.moveTo(cx + pt[0], cy + pt[1]);
            else out.lineTo(cx + pt[0], cy + pt[1]);
        }
        out.close();
    }

    private void buildVolcanicFlowPath(Path out, float cx, float cy, float rx, float ry, float seed) {
        out.reset();
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            double t = i * (Math.PI * 2.0 / steps);
            double r = 0.88
                    + 0.085 * Math.cos(4.0 * t + seed)
                    + 0.065 * Math.sin(7.0 * t - seed * 0.8)
                    + 0.042 * Math.cos(11.0 * t + seed * 1.4)
                    + 0.025 * Math.sin(17.0 * t);
            float px = cx + (float) (rx * r * Math.cos(t));
            float py = cy + (float) (ry * r * Math.sin(t));
            if (i == 0) out.moveTo(px, py);
            else out.lineTo(px, py);
        }
        out.close();
    }

    /**
     * Renders a severed city road stub engulfed by glowing black/orange pahoehoe lava lobes & hazard barricades.
     */
    private void drawLavaEngulfedRoadStub(Canvas canvas, Paint paint, Paint strokePaint, float edgeX, float edgeY, float roadW, float stubLen, float rotDeg, float starPhase) {
        canvas.save();
        canvas.translate(edgeX, edgeY);
        canvas.rotate(rotDeg);

        float halfW = roadW * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(sidewalkColor);
        canvas.drawRect(-halfW - 14f * dp, 0f, halfW + 14f * dp, stubLen * 0.76f, paint);
        paint.setColor(roadAsphaltColor);
        canvas.drawRect(-halfW, 0f, halfW, stubLen * 0.82f, paint);

        // Glowing Magma Edge & Black Basalt Pahoehoe Lava Tongue Burying the Road
        paint.setColor(Color.parseColor("#EA580C"));
        canvas.drawOval(new RectF(-halfW * 1.32f, stubLen * 0.48f, halfW * 1.32f, stubLen * 1.18f), paint);
        paint.setColor(Color.parseColor("#FEF08A"));
        canvas.drawOval(new RectF(-halfW * 1.24f, stubLen * 0.52f, halfW * 1.24f, stubLen * 1.12f), paint);
        paint.setColor(Color.parseColor("#14171F"));
        canvas.drawOval(new RectF(-halfW * 1.20f, stubLen * 0.56f, halfW * 1.20f, stubLen * 1.24f), paint);

        // Civil-Defense Hazard Barricades & Flashing Strobe Beacons
        boolean blink = ((int) (starPhase * 8f)) % 2 == 0;
        for (int b = -1; b <= 1; b++) {
            float bx = b * (halfW * 0.56f), by = stubLen * 0.36f;
            paint.setColor(Color.parseColor("#FACC15"));
            canvas.drawRoundRect(new RectF(bx - 12f * dp, by - 3.5f * dp, bx + 12f * dp, by + 3.5f * dp), 1.5f * dp, 1.5f * dp, paint);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawRect(bx - 5f * dp, by - 3.5f * dp, bx + 5f * dp, by + 3.5f * dp, paint);
            paint.setColor(blink ? Color.parseColor("#EF4444") : Color.parseColor("#F97316"));
            canvas.drawCircle(bx, by - 4.8f * dp, 3.2f * dp, paint);
        }
        canvas.restore();
    }




    private void drawDetailedCricketStadium25D(Canvas canvas, Paint paint, Paint strokePaint, float cx, float cy, float w, float h, float starPhase) {
        int plazaBaseCol   = isSunnyDay ? Color.parseColor("#D6D3CD") : (isMoonlightMode ? Color.parseColor("#1E293B") : Color.parseColor("#0F172A"));
        int plazaTileCol   = isSunnyDay ? Color.parseColor("#E5E2DC") : (isMoonlightMode ? Color.parseColor("#283548") : Color.parseColor("#182232"));
        int plazaJointCol  = isSunnyDay ? Color.parseColor("#B8B4AC") : Color.parseColor("#334155");
        int turfDarkCol    = isSunnyDay ? Color.parseColor("#3B6E28") : (isMoonlightMode ? Color.parseColor("#193E1E") : Color.parseColor("#112E15"));
        int turfLightCol   = isSunnyDay ? Color.parseColor("#4C8535") : (isMoonlightMode ? Color.parseColor("#214E27") : Color.parseColor("#173B1C"));

        // =========================================================================
        // 1. OUTER STADIUM CONCOURSE PLAZA, WALKWAYS, TREES & GREEN GLASS CANOPY
        // =========================================================================
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#44000000"));
        canvas.drawRoundRect(new RectF(cx - w + 10f * dp, cy - h + 12f * dp, cx + w + 10f * dp, cy + h + 12f * dp), 28f * dp, 28f * dp, paint);

        tempRect.set(cx - w, cy - h, cx + w, cy + h);
        paint.setColor(plazaBaseCol);
        canvas.drawRoundRect(tempRect, 28f * dp, 28f * dp, paint);

        canvas.save();
        tempPath.reset();
        tempPath.addRoundRect(tempRect, 28f * dp, 28f * dp, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Raised Inner Concourse Apron
        paint.setColor(plazaTileCol);
        canvas.drawOval(new RectF(cx - w * 0.96f, cy - h * 0.96f, cx + w * 0.96f, cy + h * 0.96f), paint);

        // Radial Plaza Expansion Joints & Upper Corner Stepped Walkways (from reference top corners)
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(plazaJointCol);
        strokePaint.setStrokeWidth(1.4f * dp);
        for (int i = 0; i < 24; i++) {
            double ang = i * (Math.PI * 2.0 / 24.0);
            float cosA = (float) Math.cos(ang), sinA = (float) Math.sin(ang);
            canvas.drawLine(cx + cosA * w * 0.78f, cy + sinA * h * 0.82f, cx + cosA * w * 1.05f, cy + sinA * h * 1.05f, strokePaint);
        }

        // Top-Left & Top-Right Paved PromenadeTerraces (matching reference top corners)
        for (int side = -1; side <= 1; side += 2) {
            canvas.save();
            canvas.translate(cx + side * w * 0.62f, cy - h * 0.88f);
            canvas.rotate(side * 24f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isSunnyDay ? Color.parseColor("#C7C3BA") : Color.parseColor("#334155"));
            canvas.drawRoundRect(new RectF(-w * 0.28f, -14f * dp, w * 0.28f, 14f * dp), 3f * dp, 3f * dp, paint);
            strokePaint.setColor(Color.parseColor("#64748B"));
            strokePaint.setStrokeWidth(1.2f * dp);
            for (float sx = -w * 0.24f; sx <= w * 0.24f; sx += 12f * dp) {
                canvas.drawLine(sx, -13f * dp, sx, 13f * dp, strokePaint);
            }
            canvas.restore();
        }

        // Top-Center Emerald Green Glass Entrance Canopy (12 o'clock in reference image)
        RectF glassCanopy = new RectF(cx - w * 0.09f, cy - h * 0.99f, cx + w * 0.09f, cy - h * 0.82f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#38000000"));
        canvas.drawRect(glassCanopy.left + 4f * dp, glassCanopy.top + 4f * dp, glassCanopy.right + 4f * dp, glassCanopy.bottom + 4f * dp, paint);
        paint.setColor(Color.parseColor("#2E7D32"));
        canvas.drawRect(glassCanopy, paint);
        paint.setColor(Color.parseColor("#4CAF50"));
        canvas.drawRect(glassCanopy.left + 2.5f * dp, glassCanopy.top + 2.5f * dp, glassCanopy.right - 2.5f * dp, glassCanopy.bottom - 2.5f * dp, paint);
        strokePaint.setColor(Color.parseColor("#A5D6A7"));
        strokePaint.setStrokeWidth(1.1f * dp);
        for (float gx = glassCanopy.left + 6f * dp; gx < glassCanopy.right; gx += 6f * dp) {
            canvas.drawLine(gx, glassCanopy.top, gx, glassCanopy.bottom, strokePaint);
        }
        for (float gy = glassCanopy.top + 6f * dp; gy < glassCanopy.bottom; gy += 6f * dp) {
            canvas.drawLine(glassCanopy.left, gy, glassCanopy.right, gy, strokePaint);
        }

        // Concourse Trees & Animated Spectators Walking into Turnstiles
        float[][] plazaTrees = {
                { -0.22f, -0.92f }, { -0.34f, -0.88f }, { 0.22f, -0.92f }, { 0.35f, -0.87f },
                { -0.86f, -0.52f }, {  0.86f, -0.52f }, { -0.86f, 0.54f }, { 0.86f,  0.54f },
                { -0.28f,  0.91f }, {  0.28f,  0.91f }
        };
        paint.setStyle(Paint.Style.FILL);
        for (float[] pt : plazaTrees) {
            float tx = cx + pt[0] * w, ty = cy + pt[1] * h;
            paint.setColor(Color.parseColor("#38000000"));
            canvas.drawCircle(tx + 4f * dp, ty + 5f * dp, 9f * dp, paint);
            paint.setColor(Color.parseColor("#2E5A27"));
            canvas.drawCircle(tx, ty, 8.5f * dp, paint);
            paint.setColor(Color.parseColor("#558B2F"));
            canvas.drawCircle(tx - 2f * dp, ty - 2f * dp, 5.5f * dp, paint);
        }

        // Animated Fans Walking Along the Outer Concourse Plaza
        int[] fanShirtCols = { Color.parseColor("#EF4444"), Color.parseColor("#3B82F6"), Color.parseColor("#FACC15"), Color.parseColor("#10B981"), Color.WHITE };
        for (int f = 0; f < 28; f++) {
            double baseAng = f * (Math.PI * 2.0 / 28.0);
            float walkShift = (float) Math.sin(starPhase * 1.8f + f * 0.7f) * 0.025f;
            float rDist = 0.89f + (f % 3) * 0.028f + walkShift;
            float fx = cx + (float) Math.cos(baseAng + walkShift * 0.4f) * (w * rDist);
            float fy = cy + (float) Math.sin(baseAng + walkShift * 0.4f) * (h * rDist);
            paint.setColor(Color.parseColor("#44000000"));
            canvas.drawCircle(fx + 1.5f * dp, fy + 2f * dp, 2.6f * dp, paint);
            paint.setColor(fanShirtCols[f % fanShirtCols.length]);
            canvas.drawCircle(fx, fy, 2.5f * dp, paint);
        }

        // =========================================================================
        // 2. STADIUM DIMENSIONS & 2.5D MEGA-STRUCTURE DROP SHADOW
        // =========================================================================
        float roofOuterRX = w * 0.85f, roofOuterRY = h * 0.90f;
        float roofRimRX   = w * 0.81f, roofRimRY   = h * 0.86f;
        float roofInnerRX = w * 0.36f, roofInnerRY = h * 0.42f;
        float fieldRX     = w * 0.25f, fieldRY     = h * 0.31f;

        // Heavy 2.5D Stadium Shadow on the Concourse
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawOval(new RectF(cx - roofOuterRX + 14f * dp, cy - roofOuterRY + 16f * dp, cx + roofOuterRX + 14f * dp, cy + roofOuterRY + 16f * dp), paint);

        // =========================================================================
        // 3. MULTI-TIERED SPECTATOR SEATING BOWL, VOMITORIES & LIVE CROWD WAVE
        // =========================================================================
        // Dark Concrete Bowl Base
        paint.setColor(Color.parseColor("#1E293B"));
        canvas.drawOval(new RectF(cx - roofInnerRX * 1.18f, cy - roofInnerRY * 1.18f, cx + roofInnerRX * 1.18f, cy + roofInnerRY * 1.18f), paint);

        int bowlSectors = 60;
        float sectorSweep = 360f / bowlSectors;
        RectF upperTierRect = new RectF(cx - roofInnerRX * 1.12f, cy - roofInnerRY * 1.12f, cx + roofInnerRX * 1.12f, cy + roofInnerRY * 1.12f);
        RectF vipRingRect   = new RectF(cx - roofInnerRX * 0.95f, cy - roofInnerRY * 0.95f, cx + roofInnerRX * 0.95f, cy + roofInnerRY * 0.95f);
        RectF lowerTierRect = new RectF(cx - roofInnerRX * 0.90f, cy - roofInnerRY * 0.90f, cx + roofInnerRX * 0.90f, cy + roofInnerRY * 0.90f);

        // Mexican Wave angle sweeping around the stadium
        float waveCenterAng = (starPhase * 85f) % 360f;

        for (int s = 0; s < bowlSectors; s++) {
            float startAng = s * sectorSweep;
            float angDiff = Math.abs(((startAng - waveCenterAng + 540f) % 360f) - 180f);
            boolean inWave = angDiff < 24f;

            // Golden-Yellow & Amber Stadium Seating Tiers (matching reference photo)
            int upperSeatCol = inWave ? Color.parseColor("#FEF08A") : ((s % 2 == 0) ? Color.parseColor("#D99B26") : Color.parseColor("#C4881B"));
            int lowerSeatCol = inWave ? Color.parseColor("#FDE047") : ((s % 2 == 0) ? Color.parseColor("#EAB308") : Color.parseColor("#CA8A04"));

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(upperSeatCol);
            canvas.drawArc(upperTierRect, startAng, sectorSweep - 0.9f, true, paint);

            paint.setColor(Color.parseColor("#334155")); // Mid-bowl concrete concourse / VIP ring
            canvas.drawArc(vipRingRect, startAng, sectorSweep, true, paint);

            paint.setColor(lowerSeatCol);
            canvas.drawArc(lowerTierRect, startAng, sectorSweep - 0.9f, true, paint);
        }

        // Concentric Seating Row Striations for Fine Architectural Texture
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#441E293B"));
        strokePaint.setStrokeWidth(1.0f * dp);
        for (float rFactor = 0.74f; rFactor <= 1.08f; rFactor += 0.045f) {
            if (Math.abs(rFactor - 0.92f) < 0.03f) continue;
            canvas.drawOval(new RectF(cx - roofInnerRX * rFactor, cy - roofInnerRY * rFactor, cx + roofInnerRX * rFactor, cy + roofInnerRY * rFactor), strokePaint);
        }

        // 4 Vomitory Entry Portals (Dark Recessed Tunnels in the Seating Bowl)
        float[] vomAngles = { 40f, 140f, 220f, 320f };
        paint.setStyle(Paint.Style.FILL);
        for (float vAng : vomAngles) {
            canvas.save();
            canvas.translate(cx, cy);
            canvas.rotate(vAng);
            paint.setColor(Color.parseColor("#0F172A"));
            canvas.drawRect(roofInnerRX * 0.76f, -5.5f * dp, roofInnerRX * 0.92f, 5.5f * dp, paint);
            paint.setColor(Color.parseColor("#94A3B8"));
            canvas.drawRect(roofInnerRX * 0.75f, -6.5f * dp, roofInnerRX * 0.78f, 6.5f * dp, paint);
            canvas.restore();
        }

        // Animated Spectators & Sparkling Camera Flashes in the Stands
        int[] crowdCols = { Color.parseColor("#EF4444"), Color.parseColor("#38BDF8"), Color.parseColor("#FFFFFF"), Color.parseColor("#22C55E"), Color.parseColor("#F97316") };
        for (int c = 0; c < 96; c++) {
            double cAng = (c * 137.5) * (Math.PI / 180.0);
            float band = 0.74f + (c % 7) * 0.048f;
            float px = cx + (float) Math.cos(cAng) * (roofInnerRX * band);
            float py = cy + (float) Math.sin(cAng) * (roofInnerRY * band);
            paint.setColor(crowdCols[c % crowdCols.length]);
            canvas.drawCircle(px, py, 1.5f * dp, paint);

            // Periodic Smartphone / Camera Strobe Flashes in the Crowd
            float flashWave = (float) Math.sin(starPhase * 7.5f + c * 1.9f);
            if (flashWave > 0.92f) {
                paint.setColor(Color.parseColor("#88FFFFFF"));
                canvas.drawCircle(px, py, 4.5f * dp, paint);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(px, py, 2.0f * dp, paint);
            }
        }

        // =========================================================================
        // 4. CRICKET OUTFIELD, LED BOUNDARY ROPE, 30-YARD CIRCLE & LIVE MATCH
        // =========================================================================
        // Inner Bowl Retaining Wall & Perimeter Track
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawOval(new RectF(cx - fieldRX - 8f * dp, cy - fieldRY - 8f * dp, cx + fieldRX + 8f * dp, cy + fieldRY + 8f * dp), paint);

        // Base Cricket Outfield Turf
        RectF fieldOval = new RectF(cx - fieldRX, cy - fieldRY, cx + fieldRX, cy + fieldRY);
        paint.setColor(turfDarkCol);
        canvas.drawOval(fieldOval, paint);

        // Clip to the Cricket Field Oval for Striped Mower Bands
        canvas.save();
        tempPath.reset();
        tempPath.addOval(fieldOval, Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Horizontal Mowed Grass Stripes (matching reference turf bands)
        int numStripes = 18;
        float stripeH = (fieldRY * 2f) / numStripes;
        paint.setColor(turfLightCol);
        for (int i = 0; i < numStripes; i += 2) {
            float topY = (cy - fieldRY) + i * stripeH;
            canvas.drawRect(cx - fieldRX, topY, cx + fieldRX, topY + stripeH, paint);
        }

        // Subtle Vertical Cross-Cut Mower Shading
        paint.setColor(Color.parseColor("#14FFFFFF"));
        float vStripeW = (fieldRX * 2f) / 12f;
        for (int j = 0; j < 12; j += 2) {
            float leftX = (cx - fieldRX) + j * vStripeW;
            canvas.drawRect(leftX, cy - fieldRY, leftX + vStripeW, cy + fieldRY, paint);
        }
        canvas.restore(); // End outfield clip

        // Animated Perimeter LED Advertising Boards & White Boundary Rope
        float boundRX = fieldRX * 0.90f, boundRY = fieldRY * 0.90f;
        RectF boundaryOval = new RectF(cx - boundRX, cy - boundRY, cx + boundRX, cy + boundRY);

        int[] ledSponsorCols = { Color.parseColor("#EF4444"), Color.parseColor("#38BDF8"), Color.parseColor("#FACC15"), Color.parseColor("#22C55E"), Color.parseColor("#A855F7") };
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3.2f * dp);
        int ledShift = (int) (starPhase * 2.5f);
        for (int b = 0; b < 36; b++) {
            strokePaint.setColor(ledSponsorCols[(b + ledShift) % ledSponsorCols.length]);
            canvas.drawArc( new RectF(cx - boundRX - 3.5f * dp, cy - boundRY - 3.5f * dp, cx + boundRX + 3.5f * dp, cy + boundRY + 3.5f * dp), b * 10f + 1f, 8f, false, strokePaint);
        }

        // Crisp White Boundary Rope
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(1.6f * dp);
        canvas.drawOval(boundaryOval, strokePaint);

        // North & South White Sightscreens behind the Boundary
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#F8FAFC"));
        canvas.drawRoundRect(new RectF(cx - 16f * dp, cy - boundRY - 6f * dp, cx + 16f * dp, cy - boundRY - 2f * dp), 1.5f * dp, 1.5f * dp, paint);
        canvas.drawRoundRect(new RectF(cx - 16f * dp, cy + boundRY + 2f * dp, cx + 16f * dp, cy + boundRY + 6f * dp), 1.5f * dp, 1.5f * dp, paint);

        // Right-Sideline Player Dugouts & White Cantilevered Players' Tunnel (matching reference right sideline)
        paint.setColor(Color.WHITE);
        canvas.drawRect(cx + boundRX + 1.5f * dp, cy - 28f * dp, cx + boundRX + 4.5f * dp, cy - 8f * dp, paint);
        canvas.drawRect(cx + boundRX + 1.5f * dp, cy + 16f * dp, cx + boundRX + 4.5f * dp, cy + 36f * dp, paint);
        // Projecting White Tunnel Roof on the Right Bowl Edge
        paint.setColor(Color.parseColor("#E2E8F0"));
        canvas.drawRect(cx + fieldRX * 0.88f, cy + 2f * dp, cx + roofInnerRX * 1.05f, cy + 14f * dp, paint);

        // 30-Yard Inner Fielding Restriction Circle (Dashed White Oval)
        float circleRX = fieldRX * 0.56f, circleRY = fieldRY * 0.56f;
        strokePaint.setColor(Color.parseColor("#EEFFFFFF"));
        strokePaint.setStrokeWidth(1.5f * dp);
        strokePaint.setPathEffect(kartDash);
        canvas.drawOval(new RectF(cx - circleRX, cy - circleRY, cx + circleRX, cy + circleRY), strokePaint);
        strokePaint.setPathEffect(null);

        // Central Cricket Square: Practice Pitches + Main 22-Yard Clay Pitch
        float pitchHalfW = 7.5f * dp, pitchHalfH = 34f * dp;
        paint.setStyle(Paint.Style.FILL);
        // 2 Adjacent Practice Turf Strips on the Square
        paint.setColor(isSunnyDay ? Color.parseColor("#88A85D") : Color.parseColor("#3B5230"));
        canvas.drawRoundRect(new RectF(cx - 21f * dp, cy - pitchHalfH * 0.92f, cx - 10f * dp, cy + pitchHalfH * 0.92f), 2f * dp, 2f * dp, paint);
        canvas.drawRoundRect(new RectF(cx + 10f * dp, cy - pitchHalfH * 0.92f, cx + 21f * dp, cy + pitchHalfH * 0.92f), 2f * dp, 2f * dp, paint);

        // Main Central 22-Yard Rolled Clay Pitch
        paint.setColor(isSunnyDay ? Color.parseColor("#E2C799") : Color.parseColor("#9C8663"));
        canvas.drawRoundRect(new RectF(cx - pitchHalfW, cy - pitchHalfH, cx + pitchHalfW, cy + pitchHalfH), 2f * dp, 2f * dp, paint);
        // Subtle Bowler Footmarks / Pitch Wear Patches
        paint.setColor(isSunnyDay ? Color.parseColor("#CCA974") : Color.parseColor("#7D694B"));
        canvas.drawOval(new RectF(cx - 4.5f * dp, cy - pitchHalfH * 0.65f, cx + 4.5f * dp, cy - pitchHalfH * 0.30f), paint);
        canvas.drawOval(new RectF(cx - 4.5f * dp, cy + pitchHalfH * 0.30f, cx + 4.5f * dp, cy + pitchHalfH * 0.65f), paint);

        // White Popping Creases, Bowling Creases & Return Creases
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeWidth(1.2f * dp);
        float northStumpY = cy - pitchHalfH + 5f * dp;
        float southStumpY = cy + pitchHalfH - 5f * dp;
        float northPopY   = northStumpY + 4.5f * dp;
        float southPopY   = southStumpY - 4.5f * dp;
        // North end creases
        canvas.drawLine(cx - pitchHalfW, northStumpY, cx + pitchHalfW, northStumpY, strokePaint);
        canvas.drawLine(cx - pitchHalfW, northPopY, cx + pitchHalfW, northPopY, strokePaint);
        canvas.drawLine(cx - 5.5f * dp, northStumpY - 3f * dp, cx - 5.5f * dp, northPopY, strokePaint);
        canvas.drawLine(cx + 5.5f * dp, northStumpY - 3f * dp, cx + 5.5f * dp, northPopY, strokePaint);
        // South end creases
        canvas.drawLine(cx - pitchHalfW, southStumpY, cx + pitchHalfW, southStumpY, strokePaint);
        canvas.drawLine(cx - pitchHalfW, southPopY, cx + pitchHalfW, southPopY, strokePaint);
        canvas.drawLine(cx - 5.5f * dp, southPopY, cx - 5.5f * dp, southStumpY + 3f * dp, strokePaint);
        canvas.drawLine(cx + 5.5f * dp, southPopY, cx + 5.5f * dp, southStumpY + 3f * dp, strokePaint);

        // 3 Golden Stumps at Both Ends
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#FACC15"));
        for (int st = -1; st <= 1; st++) {
            canvas.drawCircle(cx + st * 2.0f * dp, northStumpY, 1.1f * dp, paint);
            canvas.drawCircle(cx + st * 2.0f * dp, southStumpY, 1.1f * dp, paint);
        }

        // =========================================================================
        // 5. LIVE ANIMATED CRICKET MATCH (BOWLER, BATTERS, FIELDERS, UMPIRES & BALL)
        // =========================================================================
        float matchCycle = (starPhase * 0.22f) % 1.0f;
        int shotIndex = ((int) (starPhase * 0.22f)) % 4;
        float[] shotAngles = { -0.55f, 0.72f, 2.35f, -2.15f }; // Cover drive, pull shot, fine leg, extra cover
        float targetShotAng = shotAngles[shotIndex];

        // Bowler Run-up (North End) & Ball Trajectory
        float bowlerX = cx - 3.0f * dp;
        float bowlerY;
        float ballX, ballY;
        boolean ballHit = false;
        float hitProgress = 0f;

        if (matchCycle < 0.35f) {
            // Bowler sprinting in from North run-up to popping crease
            float runP = matchCycle / 0.35f;
            bowlerY = (northStumpY - 22f * dp) + runP * 24f * dp;
            ballX = bowlerX + 1.5f * dp;
            ballY = bowlerY;
        } else if (matchCycle < 0.52f) {
            // Ball bowled fast down the 22-yard pitch toward striker at South crease
            float bowlP = (matchCycle - 0.35f) / 0.17f;
            bowlerY = northPopY + 2f * dp;
            ballX = cx + (1f - bowlP) * (-1.5f * dp);
            ballY = northPopY + bowlP * (southPopY - northPopY);
        } else {
            // Striker smashes the ball toward the boundary!
            ballHit = true;
            hitProgress = (matchCycle - 0.52f) / 0.48f;
            bowlerY = northPopY + 1f * dp;
            float maxShotX = (float) Math.cos(targetShotAng) * (boundRX * 0.98f);
            float maxShotY = (float) Math.sin(targetShotAng) * (boundRY * 0.98f);
            float easeOut = 1f - (1f - hitProgress) * (1f - hitProgress);
            ballX = cx + easeOut * maxShotX;
            ballY = southPopY + easeOut * (maxShotY - (southPopY - cy));
        }

        // Batters Running Between Wickets when Ball is Hit
        float runSwap = ballHit ? (float) Math.sin(hitProgress * Math.PI * 2.0) * 0.5f + 0.5f : 0f;
        float strikerY    = southPopY - runSwap * (southPopY - northPopY);
        float nonStrikerY = northPopY + runSwap * (southPopY - northPopY);

        // Draw 9 Fielders + Wicketkeeper (Blue & Cyan Kit)
        float[][] fielderCoords = {
                {  0.00f,  0.52f }, // Wicketkeeper behind south stumps
                { -0.10f,  0.48f }, // First slip
                { -0.42f,  0.18f }, // Point
                { -0.44f, -0.16f }, // Extra cover
                { -0.26f, -0.48f }, // Mid-off
                {  0.28f, -0.48f }, // Mid-on
                {  0.44f,  0.14f }, // Mid-wicket / Square leg
                {  0.52f,  0.68f }, // Fine leg boundary
                { -0.68f, -0.56f }, // Deep cover boundary
                {  0.66f, -0.58f }  // Long-on boundary
        };
        for (int fIdx = 0; fIdx < fielderCoords.length; fIdx++) {
            float fx = cx + fielderCoords[fIdx][0] * fieldRX;
            float fy = cy + fielderCoords[fIdx][1] * fieldRY;
            // Nearest outfielder chases the ball when hit
            if (ballHit && fIdx == (6 + shotIndex)) {
                fx += (ballX - fx) * hitProgress * 0.65f;
                fy += (ballY - fy) * hitProgress * 0.65f;
            }
            drawCricketPlayerDot(canvas, paint, fx, fy, Color.parseColor("#1D4ED8"), Color.parseColor("#38BDF8"), false, 0f);
        }

        // Draw Bowler
        drawCricketPlayerDot(canvas, paint, bowlerX, bowlerY, Color.parseColor("#1D4ED8"), Color.parseColor("#38BDF8"), false, 0f);

        // Draw 2 Umpires (White Coats / Black Trousers at Bowler's End & Square Leg)
        drawCricketPlayerDot(canvas, paint, cx, northStumpY - 5.5f * dp, Color.WHITE, Color.parseColor("#0F172A"), false, 0f);
        drawCricketPlayerDot(canvas, paint, cx + 26f * dp, southPopY, Color.WHITE, Color.parseColor("#0F172A"), false, 0f);

        // Draw Striker & Non-Striker Batters (Yellow/Gold Batting Kit + Wooden Cricket Bat)
        float batSwingAng = (ballHit && hitProgress < 0.25f) ? -65f : 25f;
        drawCricketPlayerDot(canvas, paint, cx + 2.2f * dp, strikerY, Color.parseColor("#FACC15"), Color.WHITE, true, batSwingAng);
        drawCricketPlayerDot(canvas, paint, cx + 4.5f * dp, nonStrikerY, Color.parseColor("#FACC15"), Color.WHITE, true, 15f);

        // Draw Cricket Ball + White Trajectory Trail & Boundary 4/6 Shockwave Ring
        if (ballHit) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.argb((int) ((1f - hitProgress) * 180f), 255, 255, 255));
            strokePaint.setStrokeWidth(1.6f * dp);
            canvas.drawLine(cx, southPopY, ballX, ballY, strokePaint);

            if (hitProgress > 0.78f) {
                float ringP = (hitProgress - 0.78f) / 0.22f;
                strokePaint.setColor(Color.argb(Math.max(0, (int) ((1f - ringP) * 240f)), 250, 204, 21));
                strokePaint.setStrokeWidth(2.4f * dp);
                canvas.drawCircle(ballX, ballY, (3f + ringP * 16f) * dp, strokePaint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(ballX, ballY, 2.1f * dp, paint);
        paint.setColor(Color.parseColor("#EF4444"));
        canvas.drawCircle(ballX, ballY, 1.4f * dp, paint);

        // =========================================================================
        // 6. DRAMATIC CURVED 2.5D ROOF SHADOW ACROSS THE TURF & BOWL (IMAGE 2)
        // =========================================================================
        canvas.save();
        tempPath.reset();
        tempPath.addOval(new RectF(cx - roofInnerRX * 1.12f, cy - roofInnerRY * 1.12f, cx + roofInnerRX * 1.12f, cy + roofInnerRY * 1.12f), Path.Direction.CW);
        canvas.clipPath(tempPath);

        // Cut out the sunlit left-center oval so the shadow forms the exact crescent on the top/right side
        tempPath.reset();
        tempPath.addOval(new RectF(cx - roofInnerRX * 1.15f, cy - roofInnerRY * 1.15f, cx + roofInnerRX * 1.15f, cy + roofInnerRY * 1.15f), Path.Direction.CW);
        tempPath.addOval(new RectF(cx - fieldRX * 1.15f, cy - fieldRY * 0.88f, cx + fieldRX * 0.66f, cy + fieldRY * 1.06f), Path.Direction.CCW);
        tempPath.setFillType(Path.FillType.EVEN_ODD);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(isSunnyDay ? Color.parseColor("#54000000") : Color.parseColor("#38000000"));
        canvas.drawPath(tempPath, paint);
        tempPath.setFillType(Path.FillType.WINDING);
        canvas.restore();

        // =========================================================================
        // 7. PLEATED WHITE TENSILE ROOF CANOPY, SOLAR COLLAR & SUSPENSION CABLES
        // =========================================================================
        // Outer Dark Steel & Solar Panel Compression Ring (matching outer ring in reference photo)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#334155"));
        canvas.drawOval(new RectF(cx - roofOuterRX, cy - roofOuterRY, cx + roofOuterRX, cy + roofOuterRY), paint);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(5.5f * dp);
        RectF solarRingRect = new RectF(cx - (roofOuterRX + roofRimRX) * 0.5f, cy - (roofOuterRY + roofRimRY) * 0.5f, cx + (roofOuterRX + roofRimRX) * 0.5f, cy + (roofOuterRY + roofRimRY) * 0.5f);
        for (int s = 0; s < 60; s++) {
            strokePaint.setColor((s % 2 == 0) ? Color.parseColor("#1E293B") : Color.parseColor("#475569"));
            canvas.drawArc(solarRingRect, s * 6f + 0.4f, 5.2f, false, strokePaint);
        }

        // 60 Radial Pleated White Tensile Membrane Roof Segments (3D Origami Valley/Ridge Shading)
        int roofPleats = 60;
        double dTheta = (Math.PI * 2.0) / roofPleats;

        for (int i = 0; i < roofPleats; i++) {
            double a1 = i * dTheta;
            double aMid = (i + 0.5) * dTheta;
            double a2 = (i + 1.0) * dTheta;

            float ox1 = cx + (float) Math.cos(a1) * roofRimRX,   oy1 = cy + (float) Math.sin(a1) * roofRimRY;
            float oxM = cx + (float) Math.cos(aMid) * roofRimRX, oyM = cy + (float) Math.sin(aMid) * roofRimRY;
            float ox2 = cx + (float) Math.cos(a2) * roofRimRX,   oy2 = cy + (float) Math.sin(a2) * roofRimRY;

            float ix1 = cx + (float) Math.cos(a1) * roofInnerRX,   iy1 = cy + (float) Math.sin(a1) * roofInnerRY;
            float ixM = cx + (float) Math.cos(aMid) * (roofInnerRX * 1.02f), iyM = cy + (float) Math.sin(aMid) * (roofInnerRY * 1.02f);
            float ix2 = cx + (float) Math.cos(a2) * roofInnerRX,   iy2 = cy + (float) Math.sin(a2) * roofInnerRY;

            // Directional sunlight factor for authentic 3D pleated membrane shading
            float sunDot = (float) Math.cos(a1 - 2.35);
            int litSlopeCol, shadedSlopeCol;
            if (isSunnyDay) {
                litSlopeCol    = (sunDot > 0) ? Color.parseColor("#FBFBF9") : Color.parseColor("#F1EFEA");
                shadedSlopeCol = (sunDot > 0) ? Color.parseColor("#E4E2DD") : Color.parseColor("#D5D3CE");
            } else {
                litSlopeCol    = Color.parseColor("#CBD5E1");
                shadedSlopeCol = Color.parseColor("#94A3B8");
            }

            // Left Half-Facet of the Pleat (Sunlit slope)
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(litSlopeCol);
            tempPath.reset();
            tempPath.moveTo(ox1, oy1);
            tempPath.lineTo(oxM, oyM);
            tempPath.lineTo(ixM, iyM);
            tempPath.lineTo(ix1, iy1);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Right Half-Facet of the Pleat (Shaded slope)
            paint.setColor(shadedSlopeCol);
            tempPath.reset();
            tempPath.moveTo(oxM, oyM);
            tempPath.lineTo(ox2, oy2);
            tempPath.lineTo(ix2, iy2);
            tempPath.lineTo(ixM, iyM);
            tempPath.close();
            canvas.drawPath(tempPath, paint);

            // Radial Structural Ridge Cable Line
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(isSunnyDay ? Color.parseColor("#C7C5C0") : Color.parseColor("#64748B"));
            strokePaint.setStrokeWidth(1.1f * dp);
            canvas.drawLine(ox1, oy1, ix1, iy1, strokePaint);
        }

        // Mid-Span Concentric Suspension Cable Ring & 60 Anchor Clamp Nodes (from reference photo)
        float midCableRX = (roofRimRX + roofInnerRX) * 0.48f;
        float midCableRY = (roofRimRY + roofInnerRY) * 0.48f;
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(isSunnyDay ? Color.parseColor("#94A3B8") : Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(1.6f * dp);
        canvas.drawOval(new RectF(cx - midCableRX, cy - midCableRY, cx + midCableRX, cy + midCableRY), strokePaint);

        // Outer Structural Rim Collar
        strokePaint.setColor(Color.parseColor("#64748B"));
        strokePaint.setStrokeWidth(2.4f * dp);
        canvas.drawOval(new RectF(cx - roofRimRX, cy - roofRimRY, cx + roofRimRX, cy + roofRimRY), strokePaint);

        // Inner Catwalk Tension Ring
        strokePaint.setColor(Color.parseColor("#475569"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawOval(new RectF(cx - roofInnerRX, cy - roofInnerRY, cx + roofInnerRX, cy + roofInnerRY), strokePaint);

        // 60 Mid-Span Cable Clamp Nodes & Inner Ring Floodlight Projectors
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < roofPleats; i++) {
            double a = i * dTheta;
            float cosA = (float) Math.cos(a), sinA = (float) Math.sin(a);

            // Mid-roof cable clamp node
            float mx = cx + cosA * midCableRX, my = cy + sinA * midCableRY;
            paint.setColor(Color.parseColor("#64748B"));
            canvas.drawCircle(mx, my, 2.0f * dp, paint);

            // Inner roof edge floodlight pod
            float lx = cx + cosA * roofInnerRX, ly = cy + sinA * roofInnerRY;
            paint.setColor(Color.parseColor("#1E293B"));
            canvas.drawCircle(lx, ly, 2.2f * dp, paint);
            if (!isSunnyDay || (i % 3 == 0)) {
                paint.setColor(Color.parseColor("#FEF08A"));
                canvas.drawCircle(lx, ly, 1.3f * dp, paint);
            }
        }

        // =========================================================================
        // 8. VOLUMETRIC STADIUM FLOODLIGHT BEAMS & BOUNDARY CELEBRATION GLOW
        // =========================================================================
        float[] beamAngles = { -2.4f, -0.75f, 0.75f, 2.4f };
        paint.setStyle(Paint.Style.FILL);
        int beamAlpha = isSunnyDay ? 26 : 58;
        if (ballHit && hitProgress > 0.75f) {
            beamAlpha += (int) (Math.sin(starPhase * 16f) * 18f);
        }
        paint.setColor(Color.argb(Math.max(10, beamAlpha), 240, 249, 255));

        for (float bAng : beamAngles) {
            float srcX = cx + (float) Math.cos(bAng) * roofInnerRX;
            float srcY = cy + (float) Math.sin(bAng) * roofInnerRY;
            float perpX = (float) -Math.sin(bAng) * (36f * dp);
            float perpY = (float) Math.cos(bAng) * (36f * dp);
            tempPath.reset();
            tempPath.moveTo(srcX, srcY);
            tempPath.lineTo(cx + perpX, cy + perpY);
            tempPath.lineTo(cx - perpX, cy - perpY);
            tempPath.close();
            canvas.drawPath(tempPath, paint);
        }

        canvas.restore();

        // Crisp Outer Stadium Estate Border
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.parseColor("#1E293B"));
        strokePaint.setStrokeWidth(3.0f * dp);
        canvas.drawRoundRect(new RectF(cx - w, cy - h, cx + w, cy + h), 28f * dp, 28f * dp, strokePaint);
    }

    private void drawCricketPlayerDot(Canvas canvas, Paint paint, float x, float y, int jerseyCol, int helmetCol, boolean hasBat, float batAngleDeg) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#55000000"));
        canvas.drawCircle(x + 1.2f * dp, y + 1.5f * dp, 2.8f * dp, paint);

        if (hasBat) {
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(batAngleDeg);
            paint.setColor(Color.parseColor("#D97706"));
            canvas.drawRoundRect(new RectF(1.2f * dp, -1.0f * dp, 5.8f * dp, 1.0f * dp), 0.8f * dp, 0.8f * dp, paint);
            canvas.restore();
        }

        paint.setColor(jerseyCol);
        canvas.drawCircle(x, y, 2.7f * dp, paint);
        paint.setColor(helmetCol);
        canvas.drawCircle(x - 0.4f * dp, y - 0.4f * dp, 1.4f * dp, paint);
    }

    public static class Lake {
        public final float cx, cy, rx, ry, pondA, pondB;
        public final RectF rect;
        public final List<StoneNode> shoreBoulders = new ArrayList<>();
        public final List<StoneNode> submergedStones = new ArrayList<>();
        public final List<FlowerNode> shoreFlowers = new ArrayList<>();
        public final List<LilyPadNode> lilyPads = new ArrayList<>();

        public Lake(float cx, float cy, float w, float h, float dp) {
            this.cx = cx; this.cy = cy; this.rx = w / 2f; this.ry = h / 2f;
            this.rect = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
            this.pondA = rx * 0.76f; this.pondB = ry * 0.48f;

            Random rng = new Random((long) (cx * 19 + cy * 37));
            int[] stoneCols = { Color.parseColor("#B8B4AC"), Color.parseColor("#A6A29A"), Color.parseColor("#C9C5BD"), Color.parseColor("#949088") };
            int[] subCols   = { Color.parseColor("#5499C7"), Color.parseColor("#66ABD4"), Color.parseColor("#4889B5") };

            for (int i = 0; i < 64; i++) {
                double t = i * (Math.PI * 2.0 / 64.0);
                float ringScale = (i % 2 == 0) ? 1.03f : 0.98f;
                float[] pt = evalKidneyPoint(t, ringScale);
                float brx = (11f + rng.nextFloat() * 8f) * dp, bry = (8f + rng.nextFloat() * 6f) * dp;
                shoreBoulders.add(new StoneNode(pt[0], pt[1], brx, bry, rng.nextFloat() * 360f, stoneCols[i % stoneCols.length]));
            }

            for (int i = 0; i < 48; i++) {
                double t = i * (Math.PI * 2.0 / 48.0) + 0.05;
                float ringScale = (i % 2 == 0) ? 0.90f : 0.83f;
                float[] pt = evalKidneyPoint(t, ringScale);
                float srx = (8f + rng.nextFloat() * 6f) * dp, sry = (6f + rng.nextFloat() * 5f) * dp;
                submergedStones.add(new StoneNode(pt[0], pt[1], srx, sry, 0f, subCols[i % subCols.length]));
            }

            for (int i = 0; i < 28; i++) {
                double t = i * (Math.PI * 2.0 / 28.0);
                float[] pt = evalKidneyPoint(t, 1.12f);
                int fCol = (i % 3 == 0) ? Color.parseColor("#FF7043") : Color.parseColor("#FDD835");
                shoreFlowers.add(new FlowerNode(pt[0], pt[1], (7f + rng.nextFloat() * 4f) * dp, fCol));
            }

            float[][] padCoords = {
                    { -0.44f,  0.22f, 24f, 1f }, { -0.48f,  0.34f, 22f, 0f }, { -0.32f,  0.38f, 20f, 0f },
                    {  0.08f, -0.32f, 28f, 1f }, {  0.24f, -0.24f, 22f, 1f }, {  0.22f, -0.44f, 19f, 0f },
                    {  0.36f, -0.22f, 18f, 0f }, {  0.18f, -0.08f, 18f, 0f }, {  0.42f, -0.04f, 18f, 0f },
                    {  0.34f,  0.04f, 25f, 0f }, {  0.16f,  0.12f, 20f, 0f }, {  0.12f,  0.24f, 26f, 1f },
                    {  0.28f,  0.26f, 22f, 0f }, { -0.14f, -0.12f, 17f, 0f }
            };
            for (int i = 0; i < padCoords.length; i++) {
                lilyPads.add(new LilyPadNode(
                        padCoords[i][0] * rx, padCoords[i][1] * ry,
                        padCoords[i][2] * dp, rng.nextFloat() * 360f, padCoords[i][3] > 0.5f
                ));
            }
        }



        public float[] evalKidneyPoint(double t, float scale) {
            double r = 0.88 + 0.17 * Math.cos(2.0 * t) + 0.06 * Math.sin(t - 0.4);
            double u = pondA * scale * r * Math.cos(t);
            double v = pondB * scale * r * Math.sin(t) + 0.14 * pondB * scale * Math.cos(2.0 * t);
            double tilt = Math.toRadians(-32.0);
            double cosT = Math.cos(tilt), sinT = Math.sin(tilt);
            return new float[]{ (float) (u * cosT - v * sinT), (float) (u * sinT + v * cosT) };
        }

        public void buildKidneyPath(Path out, float centerX, float centerY, float scale) {
            out.reset();
            int steps = 64;
            for (int i = 0; i < steps; i++) {
                double t = i * (Math.PI * 2.0 / steps);
                float[] pt = evalKidneyPoint(t, scale);
                if (i == 0) out.moveTo(centerX + pt[0], centerY + pt[1]);
                else out.lineTo(centerX + pt[0], centerY + pt[1]);
            }
            out.close();
        }

        public static class StoneNode {
            public final float ox, oy, rx, ry, angleDeg; public final int col;
            public StoneNode(float ox, float oy, float rx, float ry, float ang, int c) {
                this.ox = ox; this.oy = oy; this.rx = rx; this.ry = ry; this.angleDeg = ang; this.col = c;
            }
        }


        public static class FlowerNode {
            public final float ox, oy, r; public final int petalCol;
            public FlowerNode(float ox, float oy, float r, int c) { this.ox = ox; this.oy = oy; this.r = r; this.petalCol = c; }
        }

        public static class LilyPadNode {
            public final float ox, oy, r, rotDeg; public final boolean hasLotus;
            public LilyPadNode(float ox, float oy, float r, float rot, boolean lotus) {
                this.ox = ox; this.oy = oy; this.r = r; this.rotDeg = rot; this.hasLotus = lotus;
            }
        }
    }

    public static class TreeObstacle {
        public final float x, y, radius;
        public TreeObstacle(float x, float y, float r) { this.x = x; this.y = y; this.radius = r; }
    }

    public static class OrbPickup {
        public final float x, y; public final int kind, altitude;
        public OrbPickup(float x, float y, int kind, int altitude) { this.x = x; this.y = y; this.kind = kind; this.altitude = altitude; }
    }

    public static class AmbientStar {
        public final float x, y, radius, phase; public final int color;
        public AmbientStar(float x, float y, float r, int c, float p) { this.x = x; this.y = y; this.radius = r; this.color = c; this.phase = p; }
    }

    public static class SoundWave {
        public final float x, y, maxRadius; public float radius = 16f; public int alpha = 220;
        public SoundWave(float x, float y, float maxR) { this.x = x; this.y = y; this.maxRadius = maxR; }
    }
}


