package com.mohitrathod.kitchentimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

public final class TimerGaugeView extends View {
    public interface Listener {
        void onMinutesChanged(int minutes);
    }

    private static final int INK = 0xFF202020;
    private static final int MUTED = 0xFF77716F;
    private static final float MIN_DRAG_RADIUS_RATIO = 0.35f;
    private static final float MAX_DRAG_RADIUS_RATIO = 1.65f;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private final float density;
    private final float scaledDensity;
    private final int lightRed;
    private final int softRed;

    private TimerState state = new TimerState();
    private Listener listener;
    private boolean dragging;
    private float dragLastAngle;
    private float dragTravelDegrees;
    private int dragStartMinutes;
    private float timeTextSize;
    private ToneGenerator adjustmentTone;

    public TimerGaugeView(Context context) {
        this(context, null);
    }

    public TimerGaugeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TimerGaugeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        lightRed = context.getColor(R.color.light_red);
        softRed = context.getColor(R.color.soft_red);

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        labelPaint.setColor(MUTED);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        labelPaint.setTextSize(12f * scaledDensity);

        needlePaint.setColor(INK);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        timePaint.setColor(INK);
        timePaint.setTextAlign(Paint.Align.CENTER);
        timePaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        hintPaint.setColor(MUTED);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setLetterSpacing(0.10f);

        dotPaint.setStyle(Paint.Style.FILL);

        setFocusable(true);
        setContentDescription(getContext().getString(
                R.string.gauge_description,
                state.getSelectedMinutes()
        ));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setState(TimerState state) {
        this.state = state;
        setContentDescription(getContext().getString(
                R.string.gauge_description,
                state.getSelectedMinutes()
        ));
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float min = Math.min(w, h);
        timeTextSize = min * 0.09f;
        timePaint.setTextSize(timeTextSize);
        float hintSize = Math.max(9f * scaledDensity, Math.min(12f * scaledDensity, min * 0.038f));
        hintPaint.setTextSize(hintSize);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float centerX = width / 2f;
        float centerY = height * 0.54f;
        float min = Math.min(width, height);
        float radius = min * 0.36f;
        float trackWidth = 18f * density;

        float arcFraction;
        switch (state.getPhase()) {
            case FINISHED:
                arcFraction = 1f;
                break;
            case IDLE:
                arcFraction = GaugeScale.fractionForMinutes(state.getSelectedMinutes());
                break;
            default:
                arcFraction = GaugeScale.fractionForMinutes(state.getRemainingMillis() / 60_000f);
                break;
        }

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius);

        arcPaint.setColor(softRed);
        arcPaint.setStrokeWidth(trackWidth);
        canvas.drawArc(arcRect, GaugeScale.START_ANGLE, GaugeScale.SWEEP_ANGLE, false, arcPaint);

        if (arcFraction > 0f) {
            arcPaint.setColor(lightRed);
            canvas.drawArc(
                    arcRect,
                    GaugeScale.START_ANGLE,
                    GaugeScale.SWEEP_ANGLE * arcFraction,
                    false,
                    arcPaint
            );
        }

        float tickOuter = radius - trackWidth / 2f - 4f * density;
        tickPaint.setStrokeWidth(1f * density);
        for (int minute = 0; minute <= 60; minute++) {
            boolean major = minute % 5 == 0;
            double angle = Math.toRadians(GaugeScale.angleForMinutes(minute));
            float tickInner = tickOuter - (major ? 10f : 5f) * density;
            tickPaint.setColor(major ? INK : MUTED);
            tickPaint.setAlpha(major ? 148 : 77);
            tickPaint.setStrokeWidth((major ? 1.6f : 1f) * density);
            canvas.drawLine(
                    centerX + (float) Math.cos(angle) * tickInner,
                    centerY + (float) Math.sin(angle) * tickInner,
                    centerX + (float) Math.cos(angle) * tickOuter,
                    centerY + (float) Math.sin(angle) * tickOuter,
                    tickPaint
            );
        }

        float labelRadius = radius + 19f * density;
        float labelBaseline = labelPaint.getTextSize() * 0.34f;
        int[] labels = {0, 5, 10, 15, 30, 45, 60};
        for (int minute : labels) {
            double angle = Math.toRadians(GaugeScale.angleForMinutes(minute));
            canvas.drawText(
                    String.valueOf(minute),
                    centerX + (float) Math.cos(angle) * labelRadius,
                    centerY + (float) Math.sin(angle) * labelRadius + labelBaseline,
                    labelPaint
            );
        }

        double needleAngle = Math.toRadians(
                GaugeScale.START_ANGLE + GaugeScale.SWEEP_ANGLE * arcFraction
        );
        needlePaint.setStrokeWidth(3f * density);
        float needleLength = radius - 28f * density;
        canvas.drawLine(
                centerX,
                centerY,
                centerX + (float) Math.cos(needleAngle) * needleLength,
                centerY + (float) Math.sin(needleAngle) * needleLength,
                needlePaint
        );

        dotPaint.setColor(lightRed);
        canvas.drawCircle(centerX, centerY, 8f * density, dotPaint);
        dotPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(centerX, centerY, 3.2f * density, dotPaint);

        String time = TimerState.formatRemainingTime(state.getRemainingMillis());
        float timeBaseline = centerY - radius - 44f * density;
        canvas.drawText(time, centerX, timeBaseline, timePaint);

        String hint = null;
        switch (state.getPhase()) {
            case RUNNING:
                hint = getContext().getString(R.string.hint_running);
                break;
            case PAUSED:
                hint = getContext().getString(R.string.hint_paused);
                break;
            case FINISHED:
                hint = getContext().getString(R.string.hint_finished);
                break;
            default:
                break;
        }
        if (hint != null) {
            hintPaint.setColor(state.getPhase() == TimerState.Phase.FINISHED ? lightRed : MUTED);
            float hintBaseline = centerY + min * 0.04f + timeTextSize * 0.35f
                    + hintPaint.getTextSize() * 1.7f;
            canvas.drawText(
                    hint.toUpperCase(Locale.US),
                    centerX,
                    hintBaseline,
                    hintPaint
            );
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (state.getPhase() != TimerState.Phase.IDLE) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isInDragRegion(event.getX(), event.getY())) {
                    return false;
                }
                dragging = true;
                dragLastAngle = touchAngle(event.getX(), event.getY());
                dragTravelDegrees = 0f;
                dragStartMinutes = state.getSelectedMinutes();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return true;
                }
                float currentAngle = touchAngle(event.getX(), event.getY());
                dragTravelDegrees += GaugeScale.signedAngleDelta(dragLastAngle, currentAngle);
                dragLastAngle = currentAngle;
                int next = Math.round(GaugeScale.minutesAfterDrag(
                        dragStartMinutes,
                        dragTravelDegrees
                ));
                next = Math.max(TimerState.MIN_MINUTES, Math.min(next, TimerState.MAX_MINUTES));
                if (next != state.getSelectedMinutes() && listener != null) {
                    playAdjustmentClick();
                    listener.onMinutesChanged(next);
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    performClick();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private void playAdjustmentClick() {
        if (adjustmentTone == null) {
            adjustmentTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 40);
        }
        adjustmentTone.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
    }

    private boolean isInDragRegion(float x, float y) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.54f;
        float radius = Math.min(getWidth(), getHeight()) * 0.36f;
        float distance = (float) Math.hypot(x - centerX, y - centerY);
        return distance >= radius * MIN_DRAG_RADIUS_RATIO
                && distance <= radius * MAX_DRAG_RADIUS_RATIO;
    }

    private float touchAngle(float x, float y) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.54f;
        return (float) Math.toDegrees(Math.atan2(y - centerY, x - centerX));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (adjustmentTone != null) {
            adjustmentTone.release();
            adjustmentTone = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
