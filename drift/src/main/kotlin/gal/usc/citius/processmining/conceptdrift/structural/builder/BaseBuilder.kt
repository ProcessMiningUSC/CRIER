package gal.usc.citius.processmining.conceptdrift.structural.builder

import gal.usc.citius.processmining.discovery.DiscoveryAlgorithm
import gal.usc.citius.processmining.metrics.FitnessMetric
import gal.usc.citius.processmining.metrics.PrecisionMetric
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel

@Suppress("UNCHECKED_CAST")
abstract class BaseBuilder<T> protected constructor() {
    protected lateinit var log: Log<*>
    protected lateinit var discovery: DiscoveryAlgorithm
    protected lateinit var fitnessMetric: FitnessMetric
    protected lateinit var precisionMetric: PrecisionMetric
    protected var classify: Boolean = true
    protected var plot: Boolean = false
    protected var significance: Double = 0.05
    protected var step: Int = 1
    protected var measurementsToConsider: Double = 0.5
    protected var minDriftsToConfirm: Double = 1.0

    fun forLog(log: Log<*>): T {
        this.log = log
        return this as T
    }
    fun withStep(step: Int): T {
        this.step = step
        return this as T
    }
    fun withDiscoveryAlgorithm(discoveryAlgorithm: DiscoveryAlgorithm): T {
        this.discovery = discoveryAlgorithm
        return this as T
    }
    fun withFitnessMetric(fitnessMetric: FitnessMetric): T {
        this.fitnessMetric = fitnessMetric
        return this as T
    }
    fun withFitnessMetric(fn: () -> Double): T {
        this.fitnessMetric = object : FitnessMetric {
            override fun compute(reference: ProcessModel, data: Log<*>, timeout: Long): Double = fn()
            override fun doComputation(reference: ProcessModel, data: Log<*>): Double = fn()
        }

        return this as T
    }
    fun withPrecisionMetric(precisionMetric: PrecisionMetric): T {
        this.precisionMetric = precisionMetric
        return this as T
    }
    fun withPrecisionMetric(fn: () -> Double): T {
        this.precisionMetric = object : PrecisionMetric {
            override fun compute(reference: ProcessModel, data: Log<*>, timeout: Long): Double = fn()
            override fun doComputation(reference: ProcessModel, data: Log<*>): Double = fn()
        }

        return this as T
    }
    fun withSignificanceLevel(significance: Double): T {
        this.significance = significance
        return this as T
    }
    fun withMeasurementsToConsider(percentageOfWindow: Double): T {
        this.measurementsToConsider = percentageOfWindow

        return this as T
    }
    fun withMinimumDriftsToConfirm(percentageOfWindow: Double): T {
        this.minDriftsToConfirm = percentageOfWindow

        return this as T
    }
    fun classifyResults(classify: Boolean): T {
        this.classify = classify

        return this as T
    }
}
