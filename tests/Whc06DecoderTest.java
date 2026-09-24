import app.grip_gains_companion.service.ble.Whc06Decoder;
public class Whc06DecoderTest {
    static int checks;
    static void check(boolean c) { checks++; if (!c) throw new AssertionError("Check " + checks); }
    static void close(double actual, double expected) { check(Math.abs(actual-expected)<.000001); }
    public static void main(String[] args) {
        check(Whc06Decoder.decode(null) == null);
        for (int n=0;n<12;n++) check(Whc06Decoder.decode(new byte[n]) == null);
        byte[] b = new byte[15]; b[10] = 7; b[11] = (byte)208; b[14] = 1;
        close(Whc06Decoder.decode(b),20.0);
        close(Whc06Decoder.decode(b,true),20.0); // Known units beat fallback.
        b[14]=2; close(Whc06Decoder.decode(b),9.0718474);
        b[14]=3; close(Whc06Decoder.decode(b),127.0058636);
        b[14]=4; close(Whc06Decoder.decode(b),10);
        b[14]=0; close(Whc06Decoder.decode(b),20);
        close(Whc06Decoder.decode(b,true),9.0718474);
        b[14]=(byte)0xf1; close(Whc06Decoder.decode(b),20); // Stability bits do not change unit.
        b[14]=15; close(Whc06Decoder.decode(b),20);
        close(Whc06Decoder.decode(b,true),9.0718474);
        for (int n=12;n<=14;n++) {
            byte[] shortPacket=java.util.Arrays.copyOf(b,n);
            close(Whc06Decoder.decode(shortPacket),20);
            close(Whc06Decoder.decode(shortPacket,true),9.0718474);
        }
        b[10]=(byte)255; b[11]=(byte)156; b[14]=1;
        close(Whc06Decoder.decode(b),-1);
        b[10]=0;b[11]=0;close(Whc06Decoder.decode(b),0);
        System.out.println("WHC06: " + checks + " assertions passed");
    }
}
