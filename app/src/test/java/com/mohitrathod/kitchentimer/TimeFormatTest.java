package com.mohitrathod.kitchentimer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeFormatTest {
    @Test
    public void formatsWholeMinutes() {
        assertEquals("00:00", TimerState.formatRemainingTime(0L));
        assertEquals("01:00", TimerState.formatRemainingTime(60_000L));
        assertEquals("60:00", TimerState.formatRemainingTime(3_600_000L));
    }

    @Test
    public void roundsPartialSecondsUp() {
        assertEquals("00:01", TimerState.formatRemainingTime(1L));
        assertEquals("01:00", TimerState.formatRemainingTime(59_001L));
        assertEquals("59:59", TimerState.formatRemainingTime(3_599_000L));
    }
}
