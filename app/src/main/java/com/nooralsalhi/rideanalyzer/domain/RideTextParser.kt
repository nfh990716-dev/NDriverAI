package com.nooralsalhi.rideanalyzer.domain

/** Conservative parser: it requires currency/fare context and an explicit distance unit. */
class RideTextParser {
    private val fareLabel = Regex("(?i)(?:الأجرة|الاجرة|أجره|اجرة|السعر|المبلغ|قيمة\\s*الرحلة|fare|price)\\D{0,22}([0-9][0-9,.٬٫]*)")
    private val currencyAfter = Regex("(?i)([0-9][0-9,.٬٫]*)\\s*(?:د\\.?\\s*ع|د\\s*ع|IQD|دينار)")
    private val currencyBefore = Regex("(?i)(?:د\\.?\\s*ع|د\\s*ع|IQD|دينار)\\s*([0-9][0-9,.٬٫]*)")
    private val distanceAfter = Regex("(?i)([0-9][0-9,.٬٫]*)\\s*(?:كم|كيلومتر(?:ات)?|km|kms)")
    private val tripDistanceLabel = Regex("(?i)(?:مسافة\\s*الرحلة|المسافة|المسافه|distance(?!\\s+to\\s+pickup))\\D{0,18}([0-9][0-9,.٬٫]*)")
    private val pickupLabel = Regex("(?i)(?:مسافة\\s*الوصول|المسافة\\s*إليك|يبعد\\s*عنك|distance\\s*to\\s*pickup|pickup\\s*distance)\\D{0,18}([0-9][0-9,.٬٫]*)")

    fun parse(input: String): ParsedRide? {
        if (input.isBlank()) return null
        val text = ArabicNumberNormalizer.normalize(input)
            .replace("٬", ",")
            .replace(Regex("[\\u0640\\u200f\\u200e]"), " ")
        val fareMatch = fareLabel.find(text) ?: currencyAfter.find(text) ?: currencyBefore.find(text) ?: return null
        val fareValue = ArabicNumberNormalizer.parseNumber(fareMatch.groupValues[1]) ?: return null
        if (fareValue <= 0.0 || fareValue > 5_000_000.0) return null

        val pickupMatch = pickupLabel.find(text)
        // Ignore the kilometre value that belongs to the pickup-distance label.
        val unitMatches = distanceAfter.findAll(text).toList()
        val eligibleDistances = unitMatches.filter { candidate ->
            pickupMatch == null || candidate.range.last < pickupMatch.range.first || candidate.range.first > pickupMatch.range.last
        }
        val distanceMatch = when {
            eligibleDistances.size == 1 -> eligibleDistances.first()
            eligibleDistances.size > 1 -> {
                val labelMatch = tripDistanceLabel.find(text) ?: return null
                val numberPosition = labelMatch.groups[1]?.range?.first ?: return null
                eligibleDistances.firstOrNull { it.range.contains(numberPosition) } ?: return null
            }
            else -> return null
        }
        val distanceValue = ArabicNumberNormalizer.parseNumber(distanceMatch.groupValues[1]) ?: return null
        if (distanceValue <= 0.0 || distanceValue > 500.0) return null

        val fareHasLabel = fareLabel.containsMatchIn(text)
        val hasCurrency = currencyAfter.containsMatchIn(text) || currencyBefore.containsMatchIn(text)
        val distanceHasUnit = distanceAfter.containsMatchIn(text)
        val fareConfidence = when { fareHasLabel && hasCurrency -> 0.98; fareHasLabel -> 0.88; hasCurrency -> 0.82; else -> 0.0 }
        val distanceConfidence = if (distanceHasUnit) 0.95 else 0.82
        val confidence = minOf(fareConfidence, distanceConfidence)
        val pickup = pickupMatch?.groupValues?.get(1)?.let(ArabicNumberNormalizer::parseNumber)
            ?.takeIf { it in 0.0..100.0 }
        if (confidence < 0.80) return null

        return ParsedRide(
            fareIqd = kotlin.math.round(fareValue).toLong(),
            tripDistanceKm = distanceValue,
            pickupDistanceKm = pickup,
            confidence = confidence,
            fareEvidence = fareMatch.value,
            distanceEvidence = distanceMatch.value
        )
    }
}
