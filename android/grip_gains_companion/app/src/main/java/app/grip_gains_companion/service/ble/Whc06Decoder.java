package app.grip_gains_companion.service.ble;

/** Manufacturer 0x0100 payload (Android has removed the two-byte manufacturer ID). */
public final class Whc06Decoder {
    private Whc06Decoder() {}
    public static Double decode(byte[] data) {
        if (data == null || data.length < 15) return null;
        int raw = (short) (((data[10] & 255) << 8) | (data[11] & 255));
        double value = raw / 100.0;
        switch (data[14] & 15) {
            case 1: return value;                 // kg
            case 2: return value * 0.45359237;    // lb
            case 3: return value * 6.35029318;    // stone
            case 4: return value * 0.5;           // jin
            default: return null;                // unknown unit: never guess
        }
    }
}
