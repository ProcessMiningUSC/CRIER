package gal.usc.citius.processmining.metrics.fitness

import be.kuleuven.econ.cbf.metrics.recall.AryaFitness
import be.kuleuven.econ.cbf.metrics.recall.NegativeEventRecallMetric
import gal.usc.citius.processmining.metrics.CachedModelAgainstLogMetric
import gal.usc.citius.processmining.metrics.EnhancedMemoryMapping
import gal.usc.citius.processmining.metrics.FitnessMetric
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.utils.analytics.booleanReplay
import gal.usc.citius.processmining.utils.translators.toActivitySequence
import gal.usc.citius.processmining.utils.translators.toCausalNet
import gal.usc.citius.processmining.utils.translators.toDirectlyFollowsGraph
import gal.usc.citius.processmining.utils.translators.toPetriNet
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithoutILP
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class AlignmentBasedFitness : FitnessMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val petriNet = reference.toPetriNet()
        val mapping = EnhancedMemoryMapping(data, petriNet)
        mapping.assignVisibilityFromModel()

        val algorithm = AryaFitness()
        // Values based in the default configuration of CoBeFra stored in cbf-metrics.cbfm
        algorithm.chosenAlgorithm = PetrinetReplayerWithoutILP()
        algorithm.isCreateFinalMarking = true
        algorithm.isCreateInitialMarking = true
        algorithm.resultType = 0
        algorithm.load(mapping)
        algorithm.calculate()
        return algorithm.result
    }
}

class NegativeEventBasedFitness : FitnessMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val petriNet = reference.toPetriNet()
        val mapping = EnhancedMemoryMapping(data, petriNet)
        mapping.assignVisibilityFromModel()

        val algorithm = NegativeEventRecallMetric()
        // Values based in the default configuration of CoBeFra stored in cbf-metrics.cbfm
        algorithm.genWindow = -1
        algorithm.inducer = 0
        algorithm.isMultiThreaded = false
        algorithm.negWindow = -1
        algorithm.replayer = 0
        algorithm.isUnmappedGeneralization = true
        algorithm.isUnmappedPrecision = true
        algorithm.isUnmappedRecall = true
        algorithm.isUseBothRatios = false
        algorithm.isUseCutOff = false
        algorithm.isUseWeighted = true
        algorithm.load(mapping)
        algorithm.calculate()
        return algorithm.result
    }
}

class NaiveReplayFitness : FitnessMetric, CachedModelAgainstLogMetric() {
    private val cache: ConcurrentMap<Pair<ProcessModel, List<Activity>>, Boolean> = ConcurrentHashMap()

    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val fittingTraces = runBlocking {
            data.traces.map { trace ->
                async {
                    cache.computeIfAbsent(reference to trace.toActivitySequence()) { reference.booleanReplay(trace) }
                }
            }.map {
                it.await()
            }
        }.count { it }

        return fittingTraces / data.traces.size.toDouble()
    }
}

class LogCoverage : FitnessMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val processRelations = reference
            .toCausalNet()
            .activities
            .map { activity -> activity.outputs.flatten().map { activity.id to it } }
            .flatten()
            .distinct()
        val logRelations = data
            .traces
            .flatMap { trace ->
                trace
                    .toDirectlyFollowsGraph()
                    .arcs
                    .map { arc -> arc.source.id to arc.target.id }
            }

        val notCovered = logRelations - processRelations

        return 1.0 - (notCovered.size.toDouble() / logRelations.size.toDouble())
    }
}
