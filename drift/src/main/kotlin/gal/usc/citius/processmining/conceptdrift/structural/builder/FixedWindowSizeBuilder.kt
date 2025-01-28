package gal.usc.citius.processmining.conceptdrift.structural.builder

import gal.usc.citius.processmining.conceptdrift.ConceptDriftAlgorithm
import gal.usc.citius.processmining.conceptdrift.structural.Algorithm

class FixedWindowSizeBuilder internal constructor() : BaseBuilder<FixedWindowSizeBuilder>() {
    private var windowSize: Int = 0

    fun withWindowSize(size: Int): FixedWindowSizeBuilder {
        this.windowSize = size
        return this
    }

    fun build(): ConceptDriftAlgorithm = Algorithm(
        originalLog = super.log,
        windowSizeOptimizer = { this.windowSize },
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
