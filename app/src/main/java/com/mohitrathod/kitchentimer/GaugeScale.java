package com.mohitrathod.kitchentimer;

public final class GaugeScale {
    public static final float START_ANGLE = 135f;
    public static final float SWEEP_ANGLE = 270f;

    private static final float SHORT_MINUTES = 15f;
    private static final float SHORT_FRACTION = 0.60f;
    private static final float LONG_RANGE_MINUTES = 45f;
    private static final float LONG_FRACTION = 0.40f;

    private GaugeScale() {
    }

    public static float fractionForMinutes(float minutes) {
        float clamped = Math.max(0f, Math.min(minutes, 60f));
        if (clamped <= SHORT_MINUTES) {
            return (clamped / SHORT_MINUTES) * SHORT_FRACTION;
        }
        return SHORT_FRACTION + ((clamped - SHORT_MINUTES) / LONG_RANGE_MINUTES) * LONG_FRACTION;
    }

    public static float minutesForFraction(float fraction) {
        float clamped = Math.max(0f, Math.min(fraction, 1f));
        if (clamped <= SHORT_FRACTION) {
            return (clamped / SHORT_FRACTION) * SHORT_MINUTES;
        }
        return SHORT_MINUTES + ((clamped - SHORT_FRACTION) / LONG_FRACTION) * LONG_RANGE_MINUTES;
    }

    public static float angleForMinutes(float minutes) {
        return START_ANGLE + fractionForMinutes(minutes) * SWEEP_ANGLE;
    }

    public static float minutesAfterDrag(int startMinutes, float deltaDegrees) {
        float startFraction = fractionForMinutes(startMinutes);
        float nextFraction = startFraction + deltaDegrees / SWEEP_ANGLE;
        return minutesForFraction(nextFraction);
    }

    public static float signedAngleDelta(float fromDegrees, float toDegrees) {
        float delta = (toDegrees - fromDegrees) % 360f;
        if (delta > 180f) {
            delta -= 360f;
        } else if (delta < -180f) {
            delta += 360f;
        }
        return delta;
    }
}
