import app.grip_gains_companion.service.ForceDropDetector;

public class ForceDropDetectorTest {
    static int assertions;
    static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    static boolean feed(ForceDropDetector d, double kg, long ms, String key, boolean active) {
        return d.sample(kg, ms, key, active, 20, .5, 250);
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
        check(!feed(d, 0, 7000, "2", true), "gap alone does not finish");
        for (long t = 7050; t < 7350; t += 50) check(!feed(d, 0, t, "2", true), "fresh drop confirmation");
        check(feed(d, 0, 7350, "2", true), "confirmed low readings after gap finish rep");
        arm(d, "3", 8000);
        check(!feed(d, Double.NaN, 8650, "3", true), "invalid packet");
        check(!feed(d, Double.POSITIVE_INFINITY, 8700, "3", true), "invalid packet");
        d.reset(); arm(d, "4", 0);
        for (long t = 650; t < 1000; t += 50) check(!d.sample(12, t, "4", true, 20, .5, 250), "40 percent drop below 50 percent criterion");
        boolean ended = false;
        for (long t = 1000; t < 1500; t += 50) ended |= d.sample(12, t, "4", true, 20, .3, 250);
        check(ended, "configurable 30 percent drop");
        d.reset(); arm(d, "5", 0);
        check(!feed(d, 200, 650, "5", true), "peak outlier");
        for (long t = 700; t < 1300; t += 50) check(!feed(d, 20, t, "5", true), "outlier does not inflate peak");
        // Target 20 kg: 17.9 kg never arms, even after a long hold.
        d.reset();
        for (long t=0;t<1000;t+=50) check(!d.sample(17.9,t,"threshold",true,20,.5,250), "below 90 percent stays disarmed");
        for (long t=1000;t<1600;t+=50) check(!d.sample(0,t,"threshold",true,20,.5,250), "release without arming");
        // Exact 90% activates, with the existing stable-load and confirmation delays.
        for (long t=1600;t<=2200;t+=50) check(!d.sample(18,t,"threshold",true,20,.5,250), "90 percent arms");
        boolean fired = false;
        for (long t=2250;t<2800;t+=50) fired |= d.sample(10,t,"threshold",true,20,.5,250);
        check(fired, "target-based completion at exact limit");

        // Sustained overshoot must not raise the completion limit to half the peak.
        d.reset();
        for (long t=0;t<=600;t+=50) check(!d.sample(40,t,"overshoot",true,20,.5,250), "overshoot arms");
        for (long t=650;t<1400;t+=50) check(!d.sample(12,t,"overshoot",true,20,.5,250), "peak of 40 must not turn 12 into failure");
        fired=false;
        for (long t=1400;t<2000;t+=50) fired |= d.sample(9,t,"overshoot",true,20,.5,250);
        check(fired,"drop below target-based limit");

        // Low targets work: there is no absolute 3 kg floor.
        d.reset();
        for(long t=0;t<=600;t+=50) check(!d.sample(1.8,t,"light",true,2,.5,250), "light target arms at 1.8 kg");
        fired=false;
        for(long t=650;t<1200;t+=50) fired |= d.sample(.9,t,"light",true,2,.5,250);
        check(fired,"light target can finish below 1 kg");

        // Changing the target discards old arming and debounce state.
        d.reset(); arm(d,"change",0);
        for(long t=650;t<1300;t+=50) check(!d.sample(19,t,"change",true,40,.5,250), "new target must rearm first");
        for(long t=1300;t<2000;t+=50) check(!d.sample(36,t,"change",true,40,.5,250), "new 90 percent level");
        fired=false;
        for(long t=2000;t<2600;t+=50) fired |= d.sample(19,t,"change",true,40,.5,250);
        check(fired,"new target threshold used");
        for(long t=2600;t<3200;t+=50) check(!d.sample(0,t,"change",true,20,.5,250), "target change cannot fire twice in same rep");

        for(double target:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            d.reset(); arm(d,"invalid",0);
            for(long t=650;t<1300;t+=50) check(!d.sample(0,t,"invalid",true,target,.5,250), "invalid target disables auto-end");
        }
        // A brief visit to 90% must not arm; the load must remain stable for 300 ms.
        d.reset();
        for(long t=0;t<250;t+=50) check(!d.sample(18,t,"short",true,20,.5,250),"brief loading");
        for(long t=250;t<1000;t+=50) check(!d.sample(5,t,"short",true,20,.5,250),"brief loading did not arm");
        System.out.println("ForceDropDetector: " + assertions + " assertions passed");
    }
}
