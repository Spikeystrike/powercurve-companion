import app.grip_gains_companion.service.ble.Whc06Decoder;
public class Whc06DecoderTest {
    static void check(boolean c) {if (!c) throw new AssertionError();}
    public static void main(String[] args) {
        check(Whc06Decoder.decode(new byte[14]) == null);
        byte[] b = new byte[15]; b[10] = 7; b[11] = (byte)208; b[14] = 1;
        check(Whc06Decoder.decode(b) == 20.0);
        b[14]=2; check(Math.abs(Whc06Decoder.decode(b)-9.0718474)<.000001);
        b[10]=(byte)255; b[11]=(byte)156; b[14]=1;
        check(Whc06Decoder.decode(b) == -1.0);
        b[14]=0; check(Whc06Decoder.decode(b)==null);
        System.out.println("WHC06: 5 assertions passed");
    }
}
