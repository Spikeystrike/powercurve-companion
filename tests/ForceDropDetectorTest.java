import app.grip_gains_companion.service.ForceDropDetector;

public class ForceDropDetectorTest {
    static int assertions;
    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    static boolean feed(ForceDropDetector d, double kg, long ms, String key, boolean active) {
        return d.sample(kg, ms, key, active, .5, 250);
    }
    static void arm(ForceDropDetector d, String key, long start) {
        for (long t = start; t <= start + 600; t += 50) check(!feed(d, 20, t, key, true), "stable load");
    }
    public static void main(String[] args) {
        ForceDropDetector d = new ForceDropDetector();
        for (long t = 0; t < 1000; t += 50) check(!feed(d, 0, t, "1", true), "unloaded start");
        arm(d, "1", 1000);
        check(!feed(d, 0, 1650, "1", true), "isolated drop");
        check(!feed(d, 20, 1700, "1", true), "recovered");
        for (long t = 1750; t < 2000; t += 50) check(!feed(d, 5, t, "1", true), "debounce");
        check(feed(d, 5, 2000, "1", true), "sustained drop ends rep");
        for (long t = 2100; t < 3000; t += 50) check(!feed(d, 0, t, "1", true), "once per rep");
        for (long t = 3000; t < 4000; t += 50) check(!feed(d, 20, t, "rest", false), "rest ignored");
        arm(d, "2", 4000);
        check(!feed(d, 0, 7000, "2", true), "gap must disarm");
        for (long t = 7050; t < 8000; t += 50) check(!feed(d, 0, t, "2", true), "no failure after disconnect");
        arm(d, "3", 8000);
        check(!feed(d, Double.NaN, 8650, "3", true), "invalid packet");
        check(!feed(d, Double.POSITIVE_INFINITY, 8700, "3", true), "invalid packet");
        d.reset(); arm(d, "4", 0);
        for (long t = 650; t < 1000; t += 50) check(!d.sample(12, t, "4", true, .5, 250), "40 percent drop below 50 percent criterion");
        boolean ended = false;
        for (long t = 1000; t < 1500; t += 50) ended |= d.sample(12, t, "4", true, .3, 250);
        check(ended, "configurable 30 percent drop");
        d.reset(); arm(d, "5", 0);
        check(!feed(d, 200, 650, "5", true), "peak outlier");
        for (long t = 700; t < 1300; t += 50) check(!feed(d, 20, t, "5", true), "outlier does not inflate peak");
        System.out.println("ForceDropDetector: " + assertions + " assertions passed");
    }
}
