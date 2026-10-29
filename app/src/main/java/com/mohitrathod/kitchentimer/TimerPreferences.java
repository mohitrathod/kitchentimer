package com.mohitrathod.kitchentimer;

import android.content.Context;

public final class TimerPreferences {
    private static final String FILE_NAME = "kitchen_timer";
    private static final String KEY_MINUTES = "selected_minutes";

    private TimerPreferences() {
    }

    public static int loadMinutes(Context context) {
        int minutes = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MINUTES, TimerState.DEFAULT_MINUTES);
        return Math.max(TimerState.MIN_MINUTES, Math.min(minutes, TimerState.MAX_MINUTES));
    }

    public static void saveMinutes(Context context, int minutes) {
        int clamped = Math.max(TimerState.MIN_MINUTES, Math.min(minutes, TimerState.MAX_MINUTES));
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_MINUTES, clamped)
                .apply();
    }
}
