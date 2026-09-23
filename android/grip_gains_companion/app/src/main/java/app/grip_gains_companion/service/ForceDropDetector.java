package app.grip_gains_companion.service;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Pure sample-driven detector: no missing-packet-to-zero conversion; once per active rep. */
public final class ForceDropDetector {
    private String repKey = "";
    private long lastTime = -1, loadedSince = -1, belowSince = -1;
    private boolean armed, fired;
    private double peak;
    private final ArrayDeque<Double> window = new ArrayDeque<>();

    public void reset() {
        repKey = "";
        lastTime = loadedSince = belowSince = -1;
        armed = fired = false;
        peak = 0;
        window.clear();
    }

    /** nowMs must be a monotonic phone clock; dropFraction 0.1..0.8, confirmationMs 100..1000. */
    public boolean sample(double kg, long nowMs, String key, boolean active,
                          double dropFraction, long confirmationMs) {
        if (!active || key == null || !Double.isFinite(kg) || !Double.isFinite(dropFraction)) {
            reset();
            return false;
        }
        if (!key.equals(repKey)) { reset(); repKey = key; }
        if (fired) return false;
        // A disconnect/gap requires a new stable load before arming, never ends a rep.
        if (lastTime >= 0 && (nowMs < lastTime || nowMs - lastTime > 1500)) {
            reset(); repKey = key;
        }
        lastTime = nowMs;
        window.addLast(Math.max(0, kg));
        if (window.size() > 3) window.removeFirst();
        if (window.size() < 3) return false;
        double[] sorted = window.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        double force = sorted[1]; // Reject isolated sensor spikes.
        if (!armed) {
            if (force < 3.0) { loadedSince = -1; peak = 0; return false; }
            if (loadedSince < 0) loadedSince = nowMs;
            peak = Math.max(peak, force);
            if (nowMs - loadedSince >= 300) armed = true;
            return false;
        }
        peak = Math.max(peak, force);
        double threshold = peak * (1.0 - Math.max(0.1, Math.min(0.8, dropFraction)));
        if (force > threshold) { belowSince = -1; return false; }
        if (belowSince < 0) belowSince = nowMs;
        if (nowMs - belowSince >= Math.max(100, Math.min(1000, confirmationMs))) {
            fired = true;
            return true;
        }
        return false;
    }
}
