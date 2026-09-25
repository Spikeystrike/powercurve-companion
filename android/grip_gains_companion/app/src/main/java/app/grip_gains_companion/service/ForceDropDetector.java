package app.grip_gains_companion.service;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Pure sample-driven detector: no missing-packet-to-zero conversion; once per active rep. */
public final class ForceDropDetector {
    private String repKey = "";
    private long lastTime = -1, loadedSince = -1, belowSince = -1;
    private boolean armed, fired;
    private double targetWeight = Double.NaN;
    private final ArrayDeque<Double> window = new ArrayDeque<>();

    public void reset() {
        repKey = "";
        lastTime = loadedSince = belowSince = -1;
        armed = fired = false;
        targetWeight = Double.NaN;
        window.clear();
    }

    /** targetKg is the current Powercurve target in kg; arm at 90% for 300 ms.
     * nowMs must be monotonic; dropFraction 0.1..0.8, confirmationMs 100..1000. */
    public boolean sample(double kg, long nowMs, String key, boolean active,
                          double targetKg, double dropFraction, long confirmationMs) {
        if (!active || key == null || !Double.isFinite(kg) || !Double.isFinite(dropFraction) || !Double.isFinite(targetKg) || targetKg <= 0) {
            reset();
            return false;
        }
        if (!key.equals(repKey)) { reset(); repKey = key; }
        if (fired) return false;
        // A changed target requires a fresh stable load; never reuse the old arming state.
        if (Double.compare(targetKg, targetWeight) != 0) {
            reset(); repKey = key;
        }
        // A disconnect/gap requires a new stable load before arming, never ends a rep.
        if (lastTime >= 0 && (nowMs < lastTime || nowMs - lastTime > 1500)) {
            reset(); repKey = key;
        }
        targetWeight = targetKg;
        lastTime = nowMs;
        window.addLast(Math.max(0, kg));
        if (window.size() > 3) window.removeFirst();
        if (window.size() < 3) return false;
        double[] sorted = window.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        double force = sorted[1]; // Reject isolated sensor spikes.
        if (!armed) {
            if (force < targetWeight * 0.9) { loadedSince = -1; return false; }
            if (loadedSince < 0) loadedSince = nowMs;
            if (nowMs - loadedSince >= 300) armed = true;
            return false;
        }
        double threshold = targetWeight * (1.0 - Math.max(0.1, Math.min(0.8, dropFraction)));
        if (force > threshold) { belowSince = -1; return false; }
        if (belowSince < 0) belowSince = nowMs;
        if (nowMs - belowSince >= Math.max(100, Math.min(1000, confirmationMs))) {
            fired = true;
            return true;
        }
        return false;
    }
}
