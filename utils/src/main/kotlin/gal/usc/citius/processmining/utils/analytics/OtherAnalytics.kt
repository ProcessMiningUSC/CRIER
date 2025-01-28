package gal.usc.citius.processmining.utils.analytics

/**
 * Get the area covered by the dominant points, i.e., the area of points dominated in both dimensions by
 * one of the the dominate points.
 */
fun Collection<Pair<Double, Double>>.areaUnderDominantPoints(): Double {
    var previousFirst = 0.0
    return this.dominantPoints()
        .sortedBy { it.first }
        .asSequence()
        .map { currentPoint ->
            val currentArea = (currentPoint.first - previousFirst) * currentPoint.second
            previousFirst = currentPoint.first
            currentArea
        }.sum()
}

/**
 * Get the dominant points of the collection, i.e., those which overcome each other point individually in, at least,
 * one dimension.
 */
fun Collection<Pair<Double, Double>>.dominantPoints(): Collection<Pair<Double, Double>> {
    val orderedPoints = this.sortedWith(compareBy({ it.first }, { it.second }))

    return orderedPoints
        .filterIndexed { i, point ->
            i + 1 == this.size || // Last element
                point.second > orderedPoints.subList(i + 1, this.size).map { it.second }.maxOrNull()!! // Second element
            // greater than the rest of points with greater or equal first element
        }
}
