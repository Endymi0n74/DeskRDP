package com.iiordanov.bVNC.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectPolicyTest {

    @Test
    public void firstDelayIsShort() {
        assertEquals(2000, ReconnectPolicy.delayMillisFor(1));
        assertEquals(2, ReconnectPolicy.delaySecondsFor(1));
    }

    @Test
    public void delaysGrowThenStop() {
        int previous = 0;
        for (int attempt = 1; attempt <= ReconnectPolicy.MAX_ATTEMPTS; attempt++) {
            int delay = ReconnectPolicy.delayMillisFor(attempt);
            assertTrue("le délai doit croître", delay >= previous);
            previous = delay;
        }
        assertEquals(30000, ReconnectPolicy.delayMillisFor(ReconnectPolicy.MAX_ATTEMPTS));
    }

    @Test
    public void lastDelayRepeatsPastTheTable() {
        assertEquals(
                ReconnectPolicy.delayMillisFor(ReconnectPolicy.MAX_ATTEMPTS),
                ReconnectPolicy.delayMillisFor(ReconnectPolicy.MAX_ATTEMPTS + 3));
    }

    @Test
    public void nonsenseAttemptFallsBackOnTheFirstDelay() {
        assertEquals(2000, ReconnectPolicy.delayMillisFor(0));
        assertEquals(2000, ReconnectPolicy.delayMillisFor(-4));
    }

    @Test
    public void budgetIsSpentAfterMaxAttempts() {
        assertTrue(ReconnectPolicy.shouldRetry(0));
        assertTrue(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS - 1));
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS));
        assertFalse(ReconnectPolicy.shouldRetry(ReconnectPolicy.MAX_ATTEMPTS + 1));
    }
}
