package com.comp3606.lostlink.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.comp3606.lostlink.R;

// Pulsing "radar" rings drawn behind the shield icon.
public class RadarView extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float progress;
    private int color;

    public RadarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        color = ContextCompat.getColor(context, R.color.mint);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(getResources().getDisplayMetrics().density * 2.5f);
        animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void setColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setActive(boolean active) {
        setColor(ContextCompat.getColor(getContext(), active ? R.color.mint : R.color.grey));
        if (active && !animator.isStarted()) animator.start();
        if (!active) {
            animator.cancel();
            progress = 0.35f;
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float max = Math.min(cx, cy) - ring.getStrokeWidth();
        int rgb = color & 0x00FFFFFF;

        for (int k = 0; k < 3; k++) {
            float p = (progress + k / 3f) % 1f;
            float radius = max * (0.38f + 0.62f * p);
            int alpha = (int) ((1f - p) * 150);
            ring.setColor((alpha << 24) | rgb);
            canvas.drawCircle(cx, cy, radius, ring);
        }
        core.setColor(0x30000000 | rgb);
        canvas.drawCircle(cx, cy, max * 0.4f, core);
        core.setColor(0x22000000 | rgb);
        canvas.drawCircle(cx, cy, max * 0.55f, core);
    }
}
