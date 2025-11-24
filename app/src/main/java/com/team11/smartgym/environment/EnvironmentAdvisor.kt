package com.team11.smartgym.environment

/**
 * Simple advisor for outdoor workout suitability based on temperature (°C) and humidity (%).
 *
 * This is NOT medical advice, just a conservative guideline.
 */
object EnvironmentAdvisor {

    enum class EnvLevel {
        EXCELLENT,   // Great conditions for most people
        GOOD,        // Generally okay, mild caution
        CAUTION,     // Extra caution, hydrate, reduce intensity
        HIGH_RISK,   // High heat stress risk
        DANGEROUS    // Avoid intense outdoor workouts
    }

    data class EnvSuitability(
        val level: EnvLevel,
        val summary: String,
        val detail: String
    )

    /**
     * Returns a suitability assessment, or null if we don't have enough data.
     */
    fun assess(
        tempC: Float?,
        humidityPercent: Float?
    ): EnvSuitability? {
        val t = tempC ?: return null
        val rhRaw = humidityPercent ?: return null

        // Sanitize humidity
        val rh = rhRaw.coerceIn(0f, 100f)

        // Compute a rough Heat Index in °C when warm & humid, otherwise just use tempC.
        val effectiveTempC = computeEffectiveTempC(t, rh)

        return when {
            effectiveTempC < 10f -> EnvSuitability(
                level = EnvLevel.CAUTION,
                summary = "Cold for outdoor training",
                detail = "Temperatures below 10°C can increase strain on joints and breathing. " +
                        "Warm up longer, wear layers, and avoid very intense efforts if you're not acclimated."
            )

            effectiveTempC in 10f..22f && rh < 75f -> EnvSuitability(
                level = EnvLevel.EXCELLENT,
                summary = "Excellent conditions",
                detail = "Cool to mild temperatures with moderate humidity — ideal for most workouts. " +
                        "Still hydrate, but you can generally train as planned."
            )

            effectiveTempC in 22f..28f && rh < 80f -> EnvSuitability(
                level = EnvLevel.GOOD,
                summary = "Good, but stay hydrated",
                detail = "Slightly warm conditions. Most people can train normally, but drink water " +
                        "regularly and back off if you feel unusually fatigued or dizzy."
            )

            effectiveTempC in 28f..32f || (effectiveTempC >= 26f && rh >= 70f) -> EnvSuitability(
                level = EnvLevel.CAUTION,
                summary = "Warm & humid – use caution",
                detail = "Heat stress starts to become significant, especially with high humidity. " +
                        "Shorten intervals, take more breaks, and prefer shaded areas. Watch for signs of heat exhaustion."
            )

            effectiveTempC in 32f..38f -> EnvSuitability(
                level = EnvLevel.HIGH_RISK,
                summary = "High heat stress",
                detail = "These conditions can cause rapid overheating, especially during intense cardio. " +
                        "Reduce intensity and duration, train very early/late in the day, and consider moving indoors."
            )

            effectiveTempC >= 38f -> EnvSuitability(
                level = EnvLevel.DANGEROUS,
                summary = "Dangerous conditions",
                detail = "Very high risk of heat illness. Strongly avoid intense outdoor workouts; " +
                        "keep sessions short, low-intensity, or move your training indoors."
            )

            else -> EnvSuitability(
                level = EnvLevel.GOOD,
                summary = "Okay to train",
                detail = "Conditions are generally acceptable, but always listen to your body and hydrate."
            )
        }
    }

    /**
     * Rough "effective" temperature in °C, using a simplified heat index when it's hot & humid.
     */
    private fun computeEffectiveTempC(tempC: Float, rh: Float): Float {
        // Only apply heat index if it's reasonably warm and humid
        if (tempC < 26f || rh < 40f) {
            return tempC
        }

        // Convert to Fahrenheit for the standard heat index formula
        val tF = tempC * 9f / 5f + 32f

        val hiF =
            -42.379f +
                    2.04901523f * tF +
                    10.14333127f * rh -
                    0.22475541f * tF * rh -
                    0.00683783f * tF * tF -
                    0.05481717f * rh * rh +
                    0.00122874f * tF * tF * rh +
                    0.00085282f * tF * rh * rh -
                    0.00000199f * tF * tF * rh * rh

        // Back to Celsius
        return (hiF - 32f) * 5f / 9f
    }
}
