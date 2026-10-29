package com.mohitrathod.kitchentimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

public final class TimerService extends Service {
    public static final String ACTION_START = "com.mohitrathod.kitchentimer.action.START";
    public static final String ACTION_PAUSE = "com.mohitrathod.kitchentimer.action.PAUSE";
    public static final String ACTION_RESUME = "com.mohitrathod.kitchentimer.action.RESUME";
    public static final String ACTION_RESET = "com.mohitrathod.kitchentimer.action.RESET";
    public static final String ACTION_STOP_ALARM = "com.mohitrathod.kitchentimer.action.STOP_ALARM";

    private static final String EXTRA_MINUTES = "minutes";
    private static final String CHANNEL_ID = "kitchen_timer";
    private static final int NOTIFICATION_ID = 42;
    private static final String CATEGORY_STOPWATCH = "stopwatch";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private NotificationManager notificationManager;
    private TimerSounds sounds;
    private PowerManager.WakeLock wakeLock;
    private Runnable tickRunnable;
    private boolean foregroundStarted;
    private long endpointMillis;
    private long pausedMillis;
    private int selectedMinutes = TimerState.DEFAULT_MINUTES;
    private int lastTickSecond = -1;
    private int lastNotificationSecond = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = getSystemService(NotificationManager.class);
        sounds = new TimerSounds(this);
        createNotificationChannel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_START.equals(action)) {
            int minutes = intent.getIntExtra(
                    EXTRA_MINUTES,
                    TimerRuntime.getState().getSelectedMinutes()
            );
            startTimer(Math.max(TimerState.MIN_MINUTES, Math.min(minutes, TimerState.MAX_MINUTES)));
        } else if (ACTION_PAUSE.equals(action)) {
            pauseTimer();
        } else if (ACTION_RESUME.equals(action)) {
            resumeTimer();
        } else if (ACTION_RESET.equals(action) || ACTION_STOP_ALARM.equals(action)) {
            resetTimer();
        } else if (TimerRuntime.getState().getPhase() == TimerState.Phase.IDLE) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startTimer(int minutes) {
        selectedMinutes = minutes;
        pausedMillis = minutes * 60_000L;
        TimerPreferences.saveMinutes(this, minutes);
        TimerRuntime.setState(new TimerState(
                TimerState.Phase.RUNNING,
                minutes,
                pausedMillis
        ));
        ensureForeground();
        acquireWakeLock();
        updateNotification(true);
        beginCountdown();
    }

    private void beginCountdown() {
        endpointMillis = SystemClock.elapsedRealtime() + pausedMillis;
        lastTickSecond = -1;
        lastNotificationSecond = -1;
        handler.removeCallbacksAndMessages(null);
        tickRunnable = new Runnable() {
            @Override
            public void run() {
                long remaining = Math.max(endpointMillis - SystemClock.elapsedRealtime(), 0L);
                TimerRuntime.updateRemaining(remaining);

                int seconds = (int) ((remaining + 999L) / 1000L);
                if (seconds != lastTickSecond) {
                    lastTickSecond = seconds;
                    if (remaining > 0L) {
                        sounds.playTick();
                    }
                }

                if (remaining <= 0L) {
                    finishTimer();
                    return;
                }

                maybeUpdateNotification(seconds);
                handler.postDelayed(this, 80L);
            }
        };
        handler.post(tickRunnable);
    }

    private void pauseTimer() {
        if (TimerRuntime.getState().getPhase() != TimerState.Phase.RUNNING) {
            stopIfIdle();
            return;
        }

        long remaining = Math.max(endpointMillis - SystemClock.elapsedRealtime(), 0L);
        handler.removeCallbacksAndMessages(null);
        tickRunnable = null;
        releaseWakeLock();

        if (remaining <= 0L) {
            finishTimer();
            return;
        }

        pausedMillis = remaining;
        TimerRuntime.setState(new TimerState(
                TimerState.Phase.PAUSED,
                selectedMinutes,
                remaining
        ));
        updateNotification(true);
    }

    private void resumeTimer() {
        if (TimerRuntime.getState().getPhase() != TimerState.Phase.PAUSED) {
            stopIfIdle();
            return;
        }

        pausedMillis = TimerRuntime.getState().getRemainingMillis();
        TimerRuntime.setState(new TimerState(
                TimerState.Phase.RUNNING,
                selectedMinutes,
                pausedMillis
        ));
        ensureForeground();
        acquireWakeLock();
        updateNotification(true);
        beginCountdown();
    }

    private void finishTimer() {
        handler.removeCallbacksAndMessages(null);
        tickRunnable = null;
        TimerRuntime.setState(new TimerState(
                TimerState.Phase.FINISHED,
                selectedMinutes,
                0L
        ));
        acquireWakeLock();
        sounds.startAlarm();
        ensureForeground();
        updateNotification(true);
    }

    private void resetTimer() {
        handler.removeCallbacksAndMessages(null);
        tickRunnable = null;
        sounds.stopAlarm();
        releaseWakeLock();
        TimerRuntime.setState(new TimerState(
                TimerState.Phase.IDLE,
                selectedMinutes,
                selectedMinutes * 60_000L
        ));
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            foregroundStarted = false;
        }
        stopSelf();
    }

    private void stopIfIdle() {
        if (TimerRuntime.getState().getPhase() == TimerState.Phase.IDLE) {
            stopSelf();
        }
    }

    private void maybeUpdateNotification(int seconds) {
        if (seconds % 5 != 0 || seconds == lastNotificationSecond) {
            return;
        }
        updateNotification(false);
    }

    private void updateNotification(boolean force) {
        if (!foregroundStarted) {
            return;
        }
        TimerState state = TimerRuntime.getState();
        int seconds = (int) ((state.getRemainingMillis() + 999L) / 1000L);
        if (!force && seconds == lastNotificationSecond) {
            return;
        }
        lastNotificationSecond = seconds;
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state));
    }

    private void ensureForeground() {
        if (foregroundStarted) {
            return;
        }
        startForeground(NOTIFICATION_ID, buildNotification(TimerRuntime.getState()));
        foregroundStarted = true;
    }

    private Notification buildNotification(TimerState state) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(getColor(R.color.light_red))
                .setContentIntent(openAppIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setWhen(0L);

        switch (state.getPhase()) {
            case RUNNING:
                builder.setCategory(CATEGORY_STOPWATCH)
                        .setContentTitle(getString(R.string.notification_running))
                        .setContentText(getString(
                                R.string.notification_remaining,
                                TimerState.formatRemainingTime(state.getRemainingMillis())
                        ))
                        .addAction(0, getString(R.string.notification_pause),
                                commandIntent(ACTION_PAUSE, 1))
                        .addAction(0, getString(R.string.notification_reset),
                                commandIntent(ACTION_RESET, 2));
                break;
            case PAUSED:
                builder.setCategory(CATEGORY_STOPWATCH)
                        .setContentTitle(getString(R.string.notification_paused))
                        .setContentText(getString(
                                R.string.notification_remaining,
                                TimerState.formatRemainingTime(state.getRemainingMillis())
                        ))
                        .addAction(0, getString(R.string.notification_resume),
                                commandIntent(ACTION_RESUME, 3))
                        .addAction(0, getString(R.string.notification_reset),
                                commandIntent(ACTION_RESET, 2));
                break;
            case FINISHED:
                builder.setCategory(Notification.CATEGORY_ALARM)
                        .setContentTitle(getString(R.string.notification_finished))
                        .setContentText(getString(R.string.notification_finished_text))
                        .addAction(0, getString(R.string.notification_stop),
                                commandIntent(ACTION_STOP_ALARM, 4));
                break;
            default:
                builder.setCategory(CATEGORY_STOPWATCH)
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(
                                R.string.notification_remaining,
                                TimerState.formatRemainingTime(state.getRemainingMillis())
                        ));
                break;
        }

        return builder.build();
    }

    private PendingIntent openAppIntent() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent commandIntent(String action, int requestCode) {
        Intent intent = new Intent(this, TimerService.class).setAction(action);
        return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager powerManager = getSystemService(PowerManager.class);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "KitchenTimer::countdown"
        );
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(90 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (TimerRuntime.getState().getPhase() == TimerState.Phase.IDLE) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        tickRunnable = null;
        if (sounds != null) {
            sounds.release();
        }
        releaseWakeLock();
        super.onDestroy();
    }

    public static void start(Context context, int minutes) {
        Intent intent = new Intent(context, TimerService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MINUTES, minutes);
        context.startForegroundService(intent);
    }

    public static void send(Context context, String action) {
        context.startService(new Intent(context, TimerService.class).setAction(action));
    }
}
