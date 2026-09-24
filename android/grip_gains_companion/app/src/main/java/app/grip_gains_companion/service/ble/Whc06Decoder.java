package app.grip_gains_companion.service.ble;

/** Payload after Android removes the two-byte manufacturer ID. Values are returned in kg. */
public final class Whc06Decoder {
    private Whc06Decoder() {}
    public static Double decode(byte[] data) { return decode(data, false); }
    public static Double decode(byte[] data, boolean fallbackLbs) {
        if (data == null || data.length < 12) return null;
        int raw = (short) (((data[10] & 255) << 8) | (data[11] & 255));
        double value = raw / 100.0;
        int unit = data.length > 14 ? data[14] & 15 : 0;
        switch (unit) {
            case 1: return value;
            case 2: return value * 0.45359237;
            case 3: return value * 6.35029318;
            case 4: return value * 0.5;
            default: return fallbackLbs ? value * 0.45359237 : value;
        }
    }
}
