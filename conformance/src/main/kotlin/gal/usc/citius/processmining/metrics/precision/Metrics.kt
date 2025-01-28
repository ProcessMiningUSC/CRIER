package gal.usc.citius.processmining.metrics.precision

import be.kuleuven.econ.cbf.metrics.precision.CustomNegativeEventPrecisionMetric
import be.kuleuven.econ.cbf.metrics.precision.ETConformanceBestAlign
import be.kuleuven.econ.cbf.metrics.precision.NegativeEventPrecisionMetric
import be.kuleuven.econ.cbf.metrics.precision.SimpleBehaviouralAppropriateness
import gal.usc.citius.processmining.metrics.CachedModelAgainstLogMetric
import gal.usc.citius.processmining.metrics.EnhancedMemoryMapping
import gal.usc.citius.processmining.metrics.PrecisionMetric
import gal.usc.citius.processmining.metrics.runSilently
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.utils.translators.toCausalNet
import gal.usc.citius.processmining.utils.translators.toDirectlyFollowsGraph
import gal.usc.citius.processmining.utils.translators.toPetriNet
import org.processmining.plugins.petrinet.replayer.matchinstances.algorithms.express.NBestPrefixAlignmentsGraphGuessMarkingAlg
import be.kuleuven.econ.cbf.metrics.precision.AdvancedBehaviouralAppropriateness as ABA

class ETCBestAlignPrecision() : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        val algorithm = ETConformanceBestAlign()
        // Values based in the default configuration of CoBeFra stored in cbf-metrics.cbfm
        algorithm.chosenAlgorithm = NBestPrefixAlignmentsGraphGuessMarkingAlg()
        algorithm.gamma = 0.0
        algorithm.isCreateFinalMarking = true
        algorithm.isCreateInitialMarking = true
        algorithm.load(mapping)
        algorithm.calculate()

        return algorithm.result
    }
}

class NegativeEventPrecision(private val multiThreaded: Boolean = true) : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        val algorithm = NegativeEventPrecisionMetric()
        // Values based in the default configuration of CoBeFra stored in cbf-metrics.cbfm
        algorithm.genWindow = -1
        algorithm.inducer = 0
        algorithm.isMultiThreaded = multiThreaded
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

class CustomNegativeEventPrecision(
    private val precisionLimit: Double = 0.0,
    private val multiThreaded: Boolean = true
) : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        val algorithm = CustomNegativeEventPrecisionMetric()

        algorithm.isMultiThreaded = multiThreaded
        algorithm.negWindow = -1
        algorithm.replayer = 0
        algorithm.isUnmappedPrecision = true
        algorithm.isUseBothRatios = false
        algorithm.isUseCutOff = false
        algorithm.isUseWeighted = true
        algorithm.precisionLimit = precisionLimit
        algorithm.load(mapping)
        algorithm.calculate()

        return algorithm.result
    }
}

class SimpleBehaviouralAppropriateness(
    private val punishUnmapped: Boolean = false,
    private val findBestShortestSequence: Boolean = false,
    private val maxDepth: Int = 0,
    private val timeoutStateSpaceExploration: Int = 0,
    private val timeoutLogReplay: Int = 0
) : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        return runSilently {
            val algorithm = SimpleBehaviouralAppropriateness()

            algorithm.isPunishUnmapped = punishUnmapped
            algorithm.findBestShortestSequence = findBestShortestSequence
            algorithm.maxDepth = maxDepth
            algorithm.timeoutStateSpaceExploration = timeoutStateSpaceExploration
            algorithm.timeoutLogReplay = timeoutLogReplay
            algorithm.load(mapping)
            algorithm.calculate()

            algorithm.result
        }
    }
}

class AdvancedBehaviouralAppropriateness(
    private val punishUnmapped: Boolean = false,
    private val findBestShortestSequence: Boolean = false,
    private val maxDepth: Int = 0,
    private val timeoutStateSpaceExploration: Int = 0,
    private val timeoutLogReplay: Int = 0
) : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        return runSilently {
            val algorithm = ABA()

            algorithm.isPunishUnmapped = punishUnmapped
            algorithm.findBestShortestSequence = findBestShortestSequence
            algorithm.maxDepth = maxDepth
            algorithm.timeoutStateSpaceExploration = timeoutStateSpaceExploration
            algorithm.timeoutLogReplay = timeoutLogReplay
            algorithm.load(mapping)
            algorithm.calculate()

            algorithm.result
        }
    }
}

class ModelCoverage : PrecisionMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val processRelations = reference
            .toCausalNet()
            .activities
            .map { activity -> activity.outputs.flatten().map { activity.id to it } }
            .flatten()
            .distinct()
        val logRelations = data
            .toDirectlyFollowsGraph()
            .arcs
            .map { it.source.id to it.target.id }

        val notCovered = processRelations - logRelations

        return 1.0 - (notCovered.size.toDouble() / processRelations.size.toDouble())
    }
}
