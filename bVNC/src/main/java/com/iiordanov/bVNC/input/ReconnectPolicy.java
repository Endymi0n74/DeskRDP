package com.iiordanov.bVNC.input;

/**
 * Backoff of the automatic reconnection.
 *
 * Kept apart from the handler so the delays can be checked without an Android device: the policy is
 * a pure function of the number of attempts already made.
 *
 * The first delay is short because the usual cause is a Wi-Fi hiccup or a laptop waking up, which
 * passes in a second or two. The following ones grow, then stop at 30 s: beyond that the user has
 * long understood that the host is gone, and a dialog is more honest than a spinner. Five attempts
 * span about a minute.
 */
public final class ReconnectPolicy {

    /** Attempts made before giving up and showing the usual failure dialog. */
    public static final int MAX_ATTEMPTS = 5;

    private static final int[] DELAYS_MILLIS = {2000, 5000, 10000, 20000, 30000};

    private ReconnectPolicy() {
    }

    /**
     * Delay before attempt number {@code attempt}, counting from 1.
     *
     * @param attempt 1 for the first retry, 2 for the second, and so on.
     * @return the delay in milliseconds; the last step repeats past the table.
     */
    public static int delayMillisFor(int attempt) {
        if (attempt < 1) {
            return DELAYS_MILLIS[0];
        }
        int index = Math.min(attempt, DELAYS_MILLIS.length) - 1;
        return DELAYS_MILLIS[index];
    }

    /**
     * Whether another attempt is worth making.
     *
     * @param attemptsDone retries already scheduled since the session was last alive.
     * @return true while the budget is not spent.
     */
    public static boolean shouldRetry(int attemptsDone) {
        return attemptsDone < MAX_ATTEMPTS;
    }

    /** The delay in whole seconds, for the message shown to the user. */
    public static int delaySecondsFor(int attempt) {
        return Math.round(delayMillisFor(attempt) / 1000f);
    }
}
