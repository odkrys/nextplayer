package dev.anilbeesetti.nextplayer.core.common.extensions

import kotlin.math.pow
import kotlin.math.roundToInt

fun Float.round(decimalPlaces: Int): Float {
    return (this * 10.0.pow(decimalPlaces.toDouble()))
        .roundToInt() / 10.0.pow(decimalPlaces.toDouble()).toFloat()
}

fun Float.roundToNearestStep(step: Float): Float {
    return ((this / step).roundToInt()) * step
}