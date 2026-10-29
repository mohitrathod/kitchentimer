package com.mohitrathod.kitchentimer;

import java.util.Locale;

public final class TimerState {
    public enum Phase {
        IDLE,
        RUNNING,
        PAUSED,
        FINISHED,
    }

    public static final int DEFAULT_MINUTES = 60;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 60;

    private final Phase phase;
    private final int selectedMinutes;
    private final long remainingMillis;

    public TimerState() {
        this(Phase.IDLE, DEFAULT_MINUTES, DEFAULT_MINUTES * 60_000L);
    }

    public TimerState(Phase phase, int selectedMinutes, long remainingMillis) {
        this.phase = phase;
        this.selectedMinutes = selectedMinutes;
        this.remainingMillis = remainingMillis;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getSelectedMinutes() {
        return selectedMinutes;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }

    public boolean isActive() {
        return phase == Phase.RUNNING || phase == Phase.PAUSED;
    }

    public static String formatRemainingTime(long millis) {
        long totalSeconds = (Math.max(millis, 0L) + 999L) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
