package com.mohitrathod.kitchentimer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class GaugeScaleTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void fractionStartsAtZeroAndEndsAtOne() {
        assertEquals(0f, GaugeScale.fractionForMinutes(0f), EPSILON);
        assertEquals(1f, GaugeScale.fractionForMinutes(60f), EPSILON);
    }

    @Test
    public void shortRangeUsesSixtyPercentOfArc() {
        assertEquals(0.36f, GaugeScale.fractionForMinutes(9f), EPSILON);
        assertEquals(9f, GaugeScale.minutesForFraction(0.36f), EPSILON);
    }

    @Test
    public void longRangeUsesRemainingFortyPercent() {
        assertEquals(0.6f, GaugeScale.fractionForMinutes(15f), EPSILON);
        assertEquals(0.7333333f, GaugeScale.fractionForMinutes(30f), EPSILON);
        assertEquals(60f, GaugeScale.minutesForFraction(1f), EPSILON);
    }

    @Test
    public void dragUsesGaugeAngleAndNonlinearScale() {
        assertEquals(37.5f, GaugeScale.minutesAfterDrag(15, 54f), EPSILON);
        assertEquals(13.5f, GaugeScale.minutesAfterDrag(1, 135f), EPSILON);
    }

    @Test
    public void angleDeltaTakesShortestPathAcrossZero() {
        assertEquals(20f, GaugeScale.signedAngleDelta(350f, 10f), EPSILON);
        assertEquals(-20f, GaugeScale.signedAngleDelta(10f, 350f), EPSILON);
    }

    @Test
    public void dragClampsAtGaugeEnds() {
        assertEquals(60f, GaugeScale.minutesAfterDrag(60, 30f), EPSILON);
        assertEquals(0f, GaugeScale.minutesAfterDrag(1, -30f), EPSILON);
    }
}
