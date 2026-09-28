package app.grip_gains_companion.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

fun displayNumber(value: Double): String = if(value.isFinite()) BigDecimal.valueOf(value).setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() else ""
fun decimalInput(text: String): String {
    val separator=text.indexOfFirst {it=='.' || it==','}
    return if(separator<0) text else text.take(separator+4)
}
