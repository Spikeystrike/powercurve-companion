package app.grip_gains_companion.service.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class FrezProtocolTest {
    private fun packet(adc: Int,first: Long): ByteArray = ByteBuffer.allocate(74).order(ByteOrder.LITTLE_ENDIAN).apply {
        put(1);put(0)
        repeat(9) {putInt(adc);putInt((first+it*10).toInt())}
    }.array()
    @Test fun noAutomaticTareAndExactly100SamplesBeforeForceOutput() {
        val protocol=FrezProtocol(0.01)
        assertTrue(protocol.packet(packet(1000,0)).isEmpty());assertFalse(protocol.ready)
        protocol.tare()
        repeat(11) {assertTrue(protocol.packet(packet(1000,100L+it*90)).isEmpty())}
        val first=protocol.packet(packet(1000,1090))
        assertEquals(8,first.size);assertTrue(protocol.ready)
        val loaded=protocol.packet(packet(3000,1180))
        assertEquals(9,loaded.size);assertEquals(20.0,loaded.first().first,0.0)
        assertEquals(1180000L,loaded.first().second)
        protocol.tare();assertFalse(protocol.ready)
        assertTrue(protocol.packet(packet(3000,1270)).isEmpty())
    }
    @Test fun badPacketsAreIgnoredWithoutAdvancingTheClock() {
        val protocol=FrezProtocol(1.0);protocol.tare()
        assertNull(FrezProtocol.decode(byteArrayOf(1,0)))
        val invalid=packet(1,100).apply {this[1]=1}
        assertTrue(protocol.packet(invalid).isEmpty())
        repeat(11) {protocol.packet(packet(10,it*90L))}
        assertEquals(8,protocol.packet(packet(10,990)).size)
        assertTrue(protocol.packet(packet(20,990)).isEmpty())
        assertEquals(10.0,protocol.packet(packet(20,1080)).first().first,0.0)
    }
    @Test fun adcIsSignedAndTimeIsUnsigned() {
        val sample=FrezProtocol.decode(packet(-1234,0x80000000L))!!.first()
        assertEquals(-1234,sample.adc);assertEquals(0x80000000L,sample.milliseconds)
        val protocol=FrezProtocol(-0.01);protocol.tare()
        repeat(12) {protocol.packet(packet(1000,it*90L))}
        assertEquals(20.0,protocol.packet(packet(-1000,1080)).first().first,0.0)
    }
    @Test fun invalidCoefficientsAreRejected() {
        for(value in listOf(0.0,Double.NaN,Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {FrezProtocol(value)}
        }
    }
}
