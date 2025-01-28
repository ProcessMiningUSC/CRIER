package gal.usc.citius.processmining.metrics

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.ProcessModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

interface ModelMetric {
    fun compute(reference: ProcessModel, timeout: Long = Long.MAX_VALUE): Double
    fun doComputation(reference: ProcessModel): Double
    @Throws(TimeoutCancellationException::class)
    fun doComputation(reference: ProcessModel, timeout: Long = Long.MAX_VALUE): Double {
        return runBlocking {
            withTimeout(timeout) {
                doComputation(reference)
            }
        }
    }
}

interface ModelAgainstLogMetric {
    fun compute(reference: ProcessModel, data: Log<*>, timeout: Long = Long.MAX_VALUE): Double
    fun compute(reference: ProcessModel, data: Collection<Trace<*>>, timeout: Long = Long.MAX_VALUE): Double =
        compute(reference, Log(source = "", process = "", traces = data.toList()), timeout)

    fun compute(reference: ProcessModel, data: Trace<*>, timeout: Long = Long.MAX_VALUE): Double =
        compute(reference, listOf(data), timeout)

    fun doComputation(reference: ProcessModel, data: Log<*>): Double
    @Throws(TimeoutCancellationException::class)
    fun doComputation(reference: ProcessModel, data: Log<*>, timeout: Long = Long.MAX_VALUE): Double {
        return runBlocking {
            withTimeout(timeout) {
                doComputation(reference, data)
            }
        }
    }
}

interface SimplicityMetric : ModelMetric

interface PrecisionMetric : ModelAgainstLogMetric

interface GeneralizationMetric : ModelAgainstLogMetric

interface FitnessMetric : ModelAgainstLogMetric {
    companion object {
        const val COMPLETE = 1.0
    }
}

interface AccuracyMetric : ModelAgainstLogMetric {
    val precisionMetric: PrecisionMetric
    val fitnessMetric: FitnessMetric
}
