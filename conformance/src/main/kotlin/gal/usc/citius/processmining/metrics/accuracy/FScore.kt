package gal.usc.citius.processmining.metrics.accuracy

import gal.usc.citius.processmining.metrics.AccuracyMetric
import gal.usc.citius.processmining.metrics.CachedModelAgainstLogMetric
import gal.usc.citius.processmining.metrics.FitnessMetric
import gal.usc.citius.processmining.metrics.PrecisionMetric
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import kotlin.math.pow

class FScore(
    override val precisionMetric: PrecisionMetric,
    override val fitnessMetric: FitnessMetric,
    private val beta: Double = 1.0
) : AccuracyMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val precision = precisionMetric.compute(reference, data)
        val recall = fitnessMetric.compute(reference, data)

        return ((1 + beta.pow(2)) * precision * recall) / ((beta.pow(2) * precision) + recall)
    }
}
