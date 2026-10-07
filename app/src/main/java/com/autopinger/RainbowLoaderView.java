package com.autopinger;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class RainbowLoaderView extends View {
    private Paint ringPaint, glowPaint, bgPaint;
    private float rotation = 0f;
    private ValueAnimator animator;
    private RectF oval = new RectF();
    private final float STROKE = 22f;
    private boolean loading = false;

    private final int[] COLORS = {
        0xFFFF2E93, 0xFFFF6B00, 0xFFFFEB00, 0xFF00F0A8,
        0xFF00F0FF, 0xFF4B6BFF, 0xFF8B00FF, 0xFFFF2E93
    };

    public RainbowLoaderView(Context c) { super(c); init(); }
    public RainbowLoaderView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(STROKE + 14);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAlpha(80);

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(3f);
        bgPaint.setColor(0x25FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - STROKE - 6;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        canvas.drawArc(oval, 0, 360, false, bgPaint);

        SweepGradient sg = new SweepGradient(cx, cy, COLORS, null);
        Matrix m = new Matrix();
        m.setRotate(rotation, cx, cy);
        sg.setLocalMatrix(m);
        ringPaint.setShader(sg);
        glowPaint.setShader(sg);

        canvas.drawArc(oval, 0, 280, false, glowPaint);
        canvas.drawArc(oval, 0, 280, false, ringPaint);
    }

    public void setLoading(boolean load) {
        if (load == loading) return; // Prevents restart glitch
        loading = load;
        if (load) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 360f);
                animator.setDuration(1800);
                animator.setRepeatCount(ValueAnimator.INFINITE);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(a -> {
                    rotation = (float) a.getAnimatedValue();
                    invalidate();
                });
            }
            if (!animator.isRunning()) animator.start();
        } else {
            if (animator != null && animator.isRunning()) animator.cancel();
        }
    }
}
