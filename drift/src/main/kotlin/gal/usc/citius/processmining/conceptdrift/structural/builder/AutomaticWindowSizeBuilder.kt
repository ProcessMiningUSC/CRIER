package gal.usc.citius.processmining.conceptdrift.structural.builder

import gal.usc.citius.processmining.conceptdrift.ConceptDriftAlgorithm
import gal.usc.citius.processmining.conceptdrift.structural.Algorithm
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.utils.sampling.subsampleTraces
import gal.usc.citius.processmining.utils.translators.toCausalNet
import mu.KotlinLogging

private val LOGGER = KotlinLogging.logger {}

class AutomaticWindowSizeBuilder internal constructor() : BaseBuilder<AutomaticWindowSizeBuilder>() {
    private var minWindowSize: Int = 25
    private var maxWindowSize: Int = Int.MAX_VALUE
    private var windowGenerator: (Int) -> Int = { it + 1 }

    fun withMinimumWindowSize(size: Int): AutomaticWindowSizeBuilder {
        this.minWindowSize = size
        return this
    }

    fun withMaximumWindowSize(size: Int): AutomaticWindowSizeBuilder {
        this.maxWindowSize = size
        return this
    }

    fun withWindowGenerator(windowGenerator: (Int) -> Int): AutomaticWindowSizeBuilder {
        this.windowGenerator = windowGenerator
        return this
    }

    fun build(): ConceptDriftAlgorithm {
        return Algorithm(
            originalLog = this.log,
            windowSizeOptimizer = ::computeOptimalWindowSize,
            step = this.step,
            discoveryAlgorithm = this.discovery,
            fitnessMetric = this.fitnessMetric,
            precisionMetric = this.precisionMetric,
            significance = this.significance,
            measurementsToConsider = this.measurementsToConsider,
            minDriftsToConfirm = this.minDriftsToConfirm,
            classifyResults = this.classify,
        )
    }

    private fun computeOptimalWindowSize(log: Log<*>): Int {
        LOGGER.trace { "Finding best window size (min=$minWindowSize, max=$maxWindowSize)" }
        val size = generateSequence(minWindowSize, windowGenerator)
            .takeWhile { it <= minOf(maxWindowSize, log.size / 3) }
            .map {
                val l1 = log.subsampleTraces(0, it)
                val l2 = log.subsampleTraces(it, 2 * it)
                val l3 = log.subsampleTraces(2 * it, 3 * it)

                val m1 = this.discovery.discover(l1).toCausalNet().activities
                val m2 = this.discovery.discover(l2).toCausalNet().activities
                val m3 = this.discovery.discover(l3).toCausalNet().activities

                LOGGER.trace { "Window size = $it" }
                LOGGER.trace {
                    when {
                        m1 == m2 && m2 == m3 -> "M1 == M2 == M3"
                        m1 == m2 && m2 != m3 -> "M1 == M2 != M3"
                        m1 == m3 && m1 != m2 -> "M1 == M3 != M2"
                        else -> "M1 != M2 != M3"
                    }
                }

                it to (m1 == m2 && m1 == m3 && m2 == m3)
            }
            .dropWhile { !it.second }
            .takeWhile { it.second }
            .ifEmpty { sequenceOf(minWindowSize to true) }
            .last()
            .first

        LOGGER.info { "Chosen window size = $size" }

        return size
    }
}
