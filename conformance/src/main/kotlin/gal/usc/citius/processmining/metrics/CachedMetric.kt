package gal.usc.citius.processmining.metrics

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class CachedModelAgainstLogMetric : ModelAgainstLogMetric {
    private val cache: ConcurrentMap<Pair<ProcessModel, Log<*>>, Double> = ConcurrentHashMap()

    override fun compute(reference: ProcessModel, data: Log<*>, timeout: Long): Double {
        return cache.computeIfAbsent(reference to data) {
            this.doComputation(reference, data, timeout)
        }
    }
}

abstract class CachedModelMetric : ModelMetric {
    private val cache: ConcurrentMap<ProcessModel, Double> = ConcurrentHashMap()

    override fun compute(reference: ProcessModel, timeout: Long): Double {
        return cache.computeIfAbsent(reference) {
            this.doComputation(reference, timeout)
        }
    }
}
