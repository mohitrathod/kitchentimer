package com.mohitrathod.kitchentimer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public final class MainActivity extends Activity {
    private TimerGaugeView gauge;
    private LinearLayout controlsRow;
    private Button minusButton;
    private Button plusButton;
    private Button primaryButton;
    private Button resetButton;
    private Button stopAlarmButton;

    private final TimerRuntime.Listener runtimeListener = state -> runOnUiThread(() -> render(state));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR |
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        );
        getWindow().setStatusBarColor(getColor(R.color.paper));
        getWindow().setNavigationBarColor(getColor(R.color.paper));

        if (TimerRuntime.getState().getPhase() == TimerState.Phase.IDLE) {
            TimerRuntime.setSelectedMinutes(TimerPreferences.loadMinutes(this));
        }

        setContentView(R.layout.activity_main);

        gauge = findViewById(R.id.gauge);
        controlsRow = findViewById(R.id.controls_row);
        minusButton = findViewById(R.id.minus_button);
        plusButton = findViewById(R.id.plus_button);
        primaryButton = findViewById(R.id.primary_button);
        resetButton = findViewById(R.id.reset_button);
        stopAlarmButton = findViewById(R.id.stop_alarm_button);

        gauge.setListener(this::changeMinutes);
        minusButton.setOnClickListener(view -> changeMinutes(
                TimerRuntime.getState().getSelectedMinutes() - 1
        ));
        plusButton.setOnClickListener(view -> changeMinutes(
                TimerRuntime.getState().getSelectedMinutes() + 1
        ));
        primaryButton.setOnClickListener(view -> handlePrimaryAction());
        resetButton.setOnClickListener(view -> TimerService.send(this, TimerService.ACTION_RESET));
        stopAlarmButton.setOnClickListener(view -> TimerService.send(
                this,
                TimerService.ACTION_STOP_ALARM
        ));

        TimerRuntime.addListener(runtimeListener);
        render(TimerRuntime.getState());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(TimerRuntime.getState());
    }

    @Override
    protected void onDestroy() {
        TimerRuntime.removeListener(runtimeListener);
        super.onDestroy();
    }

    private void changeMinutes(int minutes) {
        int next = Math.max(TimerState.MIN_MINUTES, Math.min(minutes, TimerState.MAX_MINUTES));
        TimerRuntime.setSelectedMinutes(next);
        TimerPreferences.saveMinutes(this, next);
    }

    private void handlePrimaryAction() {
        TimerState state = TimerRuntime.getState();
        switch (state.getPhase()) {
            case IDLE:
                requestNotificationPermission();
                TimerService.start(this, state.getSelectedMinutes());
                break;
            case RUNNING:
                TimerService.send(this, TimerService.ACTION_PAUSE);
                break;
            case PAUSED:
                TimerService.send(this, TimerService.ACTION_RESUME);
                break;
            default:
                break;
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void render(TimerState state) {
        gauge.setState(state);

        if (state.getPhase() == TimerState.Phase.FINISHED) {
            controlsRow.setVisibility(View.GONE);
            resetButton.setVisibility(View.GONE);
            stopAlarmButton.setVisibility(View.VISIBLE);
        } else {
            controlsRow.setVisibility(View.VISIBLE);
            resetButton.setVisibility(state.isActive() ? View.VISIBLE : View.INVISIBLE);
            stopAlarmButton.setVisibility(View.GONE);
            minusButton.setEnabled(state.getPhase() == TimerState.Phase.IDLE);
            plusButton.setEnabled(state.getPhase() == TimerState.Phase.IDLE);

            switch (state.getPhase()) {
                case RUNNING:
                    primaryButton.setText(R.string.action_pause);
                    break;
                case PAUSED:
                    primaryButton.setText(R.string.action_resume);
                    break;
                default:
                    primaryButton.setText(R.string.action_start);
                    break;
            }
        }

    }
}
