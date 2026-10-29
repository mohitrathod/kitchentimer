package com.mohitrathod.kitchentimer;

import java.util.concurrent.CopyOnWriteArrayList;

public final class TimerRuntime {
    public interface Listener {
        void onStateChanged(TimerState state);
    }

    private static final Object LOCK = new Object();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile TimerState state = new TimerState();

    private TimerRuntime() {
    }

    public static TimerState getState() {
        return state;
    }

    public static void addListener(Listener listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void setSelectedMinutes(int minutes) {
        int clamped = Math.max(TimerState.MIN_MINUTES, Math.min(minutes, TimerState.MAX_MINUTES));
        TimerState next;
        synchronized (LOCK) {
            TimerState current = state;
            if (current.getPhase() != TimerState.Phase.IDLE) {
                return;
            }
            if (current.getSelectedMinutes() == clamped) {
                return;
            }
            next = new TimerState(TimerState.Phase.IDLE, clamped, clamped * 60_000L);
            state = next;
        }
        notifyListeners(next);
    }

    public static void setState(TimerState newState) {
        synchronized (LOCK) {
            state = newState;
        }
        notifyListeners(newState);
    }

    public static void updateRemaining(long millis) {
        TimerState next;
        synchronized (LOCK) {
            TimerState current = state;
            next = new TimerState(
                    current.getPhase(),
                    current.getSelectedMinutes(),
                    Math.max(millis, 0L)
            );
            state = next;
        }
        notifyListeners(next);
    }

    private static void notifyListeners(TimerState newState) {
        for (Listener listener : LISTENERS) {
            listener.onStateChanged(newState);
        }
    }
}
