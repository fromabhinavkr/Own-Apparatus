package com.abhinav.ownapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.animation.PathInterpolatorCompat;

/**
 * Animated Search Icon that morphs into a cracking egg with a chicken using a fluid liquid transition.
 * Features zero-allocation onDraw loop for buttery smooth 60fps+ rendering.
 */
public class SearchCubeView extends View {
    private boolean isLoading = false;
    private Drawable searchIcon;
    private int themeColor = Color.WHITE;

    // Paints
    private Paint eggPaint, strokePaint, chickenPaint, beakPaint, eyePaint;

    // Animation Controllers
    private ValueAnimator loopAnim; // Runs the continuous egg animation
    private ValueAnimator transitionAnim; // Handles the liquid glass morphing
    private float transitionProgress = 0f; // 0.0 = Search Icon, 1.0 = Egg Animation
    private float loopProgress = 0f;

    // Pre-allocated Paths to prevent memory allocations during rendering
    private final Path bottomEgg = new Path();
    private final Path topEgg = new Path();
    private final Path beakPath = new Path();

    public SearchCubeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SearchCubeView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        eggPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eggPaint.setStyle(Paint.Style.FILL);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        chickenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        chickenPaint.setStyle(Paint.Style.FILL);
        chickenPaint.setColor(Color.parseColor("#FFD54F")); // Cute Yellow

        beakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        beakPaint.setStyle(Paint.Style.FILL);
        beakPaint.setColor(Color.parseColor("#FF9800")); // Orange beak and comb

        eyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eyePaint.setStyle(Paint.Style.FILL);
        eyePaint.setColor(Color.parseColor("#1C1C1E")); // Always dark for contrast

        searchIcon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_search);
        if (searchIcon != null) {
            searchIcon = searchIcon.mutate();
        }

        buildEggPaths();
    }

    private void buildEggPaths() {
        // Base coordinate grid is 100x100

        // --- Bottom Half of Egg ---
        bottomEgg.reset();
        bottomEgg.moveTo(20, 55);
        bottomEgg.cubicTo(20, 100, 80, 100, 80, 55);
        // Zig-zag crack from right to left
        bottomEgg.lineTo(72.5f, 45);
        bottomEgg.lineTo(65f, 55);
        bottomEgg.lineTo(57.5f, 45);
        bottomEgg.lineTo(50f, 55);
        bottomEgg.lineTo(42.5f, 45);
        bottomEgg.lineTo(35f, 55);
        bottomEgg.lineTo(27.5f, 45);
        bottomEgg.lineTo(20f, 55);
        bottomEgg.close();

        // --- Top Half of Egg ---
        topEgg.reset();
        topEgg.moveTo(20, 55);
        topEgg.cubicTo(20, 5, 80, 5, 80, 55);
        // Zig-zag crack perfectly matching the bottom half to create a seamless seal
        topEgg.lineTo(72.5f, 45);
        topEgg.lineTo(65f, 55);
        topEgg.lineTo(57.5f, 45);
        topEgg.lineTo(50f, 55);
        topEgg.lineTo(42.5f, 45);
        topEgg.lineTo(35f, 55);
        topEgg.lineTo(27.5f, 45);
        topEgg.lineTo(20f, 55);
        topEgg.close();
    }

    public void setColor(int color) {
        this.themeColor = color;
        if (searchIcon != null) {
            searchIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
        strokePaint.setStrokeWidth(dp(1.5f));
        invalidate();
    }

    public void setLoading(boolean loading) {
        if (isLoading == loading) return;
        isLoading = loading;

        // Premium Liquid Fluid Animation Transition (iPhone style glass morph)
        if (transitionAnim != null) transitionAnim.cancel();
        transitionAnim = ValueAnimator.ofFloat(transitionProgress, loading ? 1f : 0f);
        transitionAnim.setDuration(600); // 600ms for elegant, stable crossfade
        // Custom butter-smooth spring cubic bezier interpolator
        transitionAnim.setInterpolator(PathInterpolatorCompat.create(0.25f, 0.8f, 0.25f, 1f));

        transitionAnim.addUpdateListener(a -> {
            transitionProgress = (float) a.getAnimatedValue();
            invalidate();
        });

        transitionAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!isLoading && loopAnim != null) {
                    loopAnim.cancel(); // Save battery by stopping loop when hidden
                    loopProgress = 0f;
                }
            }
        });
        transitionAnim.start();

        if (loading) {
            if (loopAnim == null) {
                // Loop is exactly 3000ms (3 seconds) for the full story cycle
                loopAnim = ValueAnimator.ofFloat(0f, 1f);
                loopAnim.setDuration(3000);
                loopAnim.setRepeatCount(ValueAnimator.INFINITE);
                loopAnim.setInterpolator(new LinearInterpolator());
                loopAnim.addUpdateListener(a -> {
                    loopProgress = (float) a.getAnimatedValue();
                    if (transitionProgress > 0) invalidate(); // Only redraw if visible
                });
            }
            if (!loopAnim.isRunning()) loopAnim.start();
        }
    }

    // Helper for buttery smooth ease-in-out physics mapping
    private float smoothStep(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3 - 2 * t);
    }

    // Draw the chicken body, eyes, and beak (Zero Allocation)
    private void drawChicken(Canvas canvas, float chickY, float lookX, int alpha) {
        chickenPaint.setAlpha(alpha);
        beakPaint.setAlpha(alpha);
        eyePaint.setAlpha(alpha);

        // Chicken Body/Head
        canvas.drawCircle(50, chickY, 20, chickenPaint);

        // Chicken Eyes (offset by look direction)
        canvas.drawCircle(43 + lookX, chickY - 4, 2.5f, eyePaint);
        canvas.drawCircle(57 + lookX, chickY - 4, 2.5f, eyePaint);

        // Chicken Beak (Dynamically adjusts based on look position)
        beakPath.reset();
        beakPath.moveTo(46 + lookX, chickY + 2);
        beakPath.lineTo(54 + lookX, chickY + 2);
        beakPath.lineTo(50 + lookX, chickY + 8);
        beakPath.close();
        canvas.drawPath(beakPath, beakPaint);

        // Chicken Comb (Head Feathers)
        canvas.drawCircle(47 + (lookX*0.5f), chickY - 20, 4, beakPaint);
        canvas.drawCircle(50 + (lookX*0.5f), chickY - 22, 5, beakPaint);
        canvas.drawCircle(53 + (lookX*0.5f), chickY - 20, 4, beakPaint);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        float cx = w / 2f;
        float cy = h / 2f;

        // Clamp transition bound to prevent visual crashes if interpolator slightly overshoots
        float safeProgress = Math.max(0f, Math.min(1f, transitionProgress));

        // 1. Draw Search Icon (Liquid twist, shrink, and fade out)
        if (transitionProgress < 1f && searchIcon != null) {
            canvas.save();
            float iconScale = 1f - (safeProgress * 0.5f); // Shrinks to 50%
            canvas.scale(iconScale, iconScale, cx, cy);

            canvas.rotate(-90f * safeProgress, cx, cy); // Liquid exit twist

            // Fast fade out for precise crossover
            float iconAlpha = Math.max(0f, 1f - (safeProgress * 2f));
            searchIcon.setAlpha((int) (255 * iconAlpha));

            int pad = dp(8);
            searchIcon.setBounds(pad, pad, w - pad, h - pad);
            searchIcon.draw(canvas);
            canvas.restore();
        }

        // 2. Draw The Interactive Egg Sequence (Grows, untwists, and blooms into view)
        if (transitionProgress > 0f) {
            canvas.save();

            // Fluid pop-in scale matching exactly the bounding size of the search icon
            float eggZoom = 0.5f + (safeProgress * 0.5f);
            canvas.scale(eggZoom, eggZoom, cx, cy);

            canvas.rotate(90f * (1f - safeProgress), cx, cy); // Liquid entry twist

            // Translate 100x100 virtual grid to screen center perfectly
            float scale = Math.min(w, h) * 0.75f / 100f;
            canvas.translate(cx - 50 * scale, cy - 45 * scale);
            canvas.scale(scale, scale);

            // Timeline Map Controller
            float t = loopProgress;
            float topY = 0;
            float chickY = 65; // Hidden behind bottom shell default
            float lookX = 0;
            float rotZ = 0;

            // Mathematical Storyboard Timeline
            if (t < 0.1f) {
                float wt = t / 0.1f;
                rotZ = (float) Math.sin(wt * Math.PI * 4) * 12f; // Wobble 2 times
            } else if (t < 0.2f) {
                float wt = (t - 0.1f) / 0.1f;
                topY = -smoothStep(wt) * 35f; // Top cracks open
            } else if (t < 0.3f) {
                topY = -35f;
                float wt = (t - 0.2f) / 0.1f;
                chickY = 65f - smoothStep(wt) * 25f; // Chicken pops up to Y=40
            } else if (t < 0.45f) {
                topY = -35f; chickY = 40f;
                float wt = (t - 0.3f) / 0.15f;
                lookX = -smoothStep(wt) * 10f; // Looks Left
            } else if (t < 0.6f) {
                topY = -35f; chickY = 40f;
                float wt = (t - 0.45f) / 0.15f;
                lookX = -10f + smoothStep(wt) * 20f; // Scans to the Right
            } else if (t < 0.7f) {
                topY = -35f; chickY = 40f;
                float wt = (t - 0.6f) / 0.1f;
                lookX = 10f - smoothStep(wt) * 10f; // Looks back to Center
            } else if (t < 0.8f) {
                topY = -35f; lookX = 0f;
                float wt = (t - 0.7f) / 0.1f;
                chickY = 40f + smoothStep(wt) * 25f; // Ducks back down
            } else if (t < 0.9f) {
                chickY = 65f; lookX = 0f;
                float wt = (t - 0.8f) / 0.1f;
                topY = -35f + smoothStep(wt) * 35f; // Shell seamlessly closes
            }

            // Apply Wobble to entire egg sequence
            canvas.rotate(rotZ, 50, 60);

            // Calculate fade logic for liquid morph
            float fadeProgress = Math.min(1f, safeProgress * 2f);
            int transitionAlpha = (int) (255 * fadeProgress);

            eggPaint.setColor(themeColor);
            eggPaint.setAlpha(transitionAlpha);

            strokePaint.setColor(Color.argb((int)(100 * fadeProgress), Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor)));

            // Layer 1: The Chicken (Pops out from behind bottom shell)
            drawChicken(canvas, chickY, lookX, transitionAlpha);

            // Layer 2: Bottom Egg Shell (Covers lower half of chicken)
            canvas.drawPath(bottomEgg, eggPaint);
            canvas.drawPath(bottomEgg, strokePaint);

            // Layer 3: Top Egg Shell (Moves up and down to reveal chicken)
            canvas.save();
            canvas.translate(0, topY);
            canvas.drawPath(topEgg, eggPaint);
            canvas.drawPath(topEgg, strokePaint);
            canvas.restore();

            canvas.restore();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (loopAnim != null) loopAnim.cancel();
        if (transitionAnim != null) transitionAnim.cancel();
    }

    private int dp(float px) {
        return (int) (px * getResources().getDisplayMetrics().density + 0.5f);
    }
}