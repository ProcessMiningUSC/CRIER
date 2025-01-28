package gal.usc.citius.processmining.metrics.generalization

import be.kuleuven.econ.cbf.metrics.generalization.AryaGeneralization
import gal.usc.citius.processmining.metrics.CachedModelAgainstLogMetric
import gal.usc.citius.processmining.metrics.EnhancedMemoryMapping
import gal.usc.citius.processmining.metrics.GeneralizationMetric
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.utils.translators.toPetriNet
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithoutILP

class AlignmentBasedProbabilisticGeneralization() : GeneralizationMetric, CachedModelAgainstLogMetric() {
    override fun doComputation(reference: ProcessModel, data: Log<*>): Double {
        val mapping = EnhancedMemoryMapping(data, reference.toPetriNet())
        mapping.assignVisibilityFromModel()

        val algorithm = AryaGeneralization()
        algorithm.chosenAlgorithm = PetrinetReplayerWithoutILP()
        algorithm.isCreateFinalMarking = true
        algorithm.isCreateInitialMarking = true
        algorithm.isTraceGrouped = true
        algorithm.load(mapping)
        algorithm.calculate()
        return algorithm.result
    }
}
