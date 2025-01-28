package gal.usc.citius.processmining.metrics.simplicity

import gal.usc.citius.processmining.metrics.CachedModelMetric
import gal.usc.citius.processmining.metrics.SimplicityMetric
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.petrinet.PlaceToTransitionArc
import gal.usc.citius.processmining.model.process.petrinet.TransitionToPlaceArc
import gal.usc.citius.processmining.utils.translators.toPetriNet
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory
import org.processmining.plugins.cutvertices.DirectedCutVertexAlgorithm

class ArcCount : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double = reference.toPetriNet().arcs.size.toDouble()
}

class NodeCount : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petriNet = reference.toPetriNet()

        return (petriNet.places union petriNet.transitions).size.toDouble()
    }
}

class TransitionCount : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double = reference.toPetriNet().transitions.size.toDouble()
}

class PlaceCount : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double = reference.toPetriNet().places.size.toDouble()
}

class AverageNodeArcDegree : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petrinet = reference.toPetriNet()
        val nodes = (petrinet.places union petrinet.transitions).map { it.id }

        val inputArcs = petrinet.arcs.groupBy { it.to }.map { it.key.id to it.value.size }.toMap()
        val outputArcs = petrinet.arcs.groupBy { it.from }.map { it.key.id to it.value.size }.toMap()

        return nodes.associate { it to inputArcs.getOrDefault(it, 0) + outputArcs.getOrDefault(it, 0) }.values.average()
    }
}

class AverageTransitionArcDegree : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petrinet = reference.toPetriNet()

        val inputArcs = petrinet.arcs
            .filterIsInstance<PlaceToTransitionArc>()
            .groupBy { it.to }
            .map { it.key.id to it.value.size }
            .toMap()
        val outputArcs = petrinet.arcs
            .filterIsInstance<TransitionToPlaceArc>()
            .groupBy { it.from }
            .map { it.key.id to it.value.size }
            .toMap()

        val nodes = inputArcs.keys union outputArcs.keys

        return nodes.associate { it to inputArcs.getOrDefault(it, 0) + outputArcs.getOrDefault(it, 0) }.values.average()
    }
}

class AveragePlaceArcDegree : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petrinet = reference.toPetriNet()

        val inputArcs = petrinet.arcs
            .filterIsInstance<TransitionToPlaceArc>()
            .groupBy { it.to }
            .map { it.key.id to it.value.size }
            .toMap()
        val outputArcs = petrinet.arcs
            .filterIsInstance<PlaceToTransitionArc>()
            .groupBy { it.from }
            .map { it.key.id to it.value.size }
            .toMap()

        val nodes = inputArcs.keys union outputArcs.keys

        return nodes.associate { it to inputArcs.getOrDefault(it, 0) + outputArcs.getOrDefault(it, 0) }.values.average()
    }
}

class CutVertices : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petrinet = reference.toPetriNet()
        val transformedPetriNet = PetrinetFactory.newPetrinet(petrinet.id)

        for (transition in petrinet.transitions)
            transformedPetriNet.addTransition(transition.id)
        for (place in petrinet.places)
            transformedPetriNet.addPlace(place.id)

        val transitionLookupTable = transformedPetriNet.transitions.map { it.label to it }.toMap()
        val placesLookupTable = transformedPetriNet.places.map { it.label to it }.toMap()

        for (arc in petrinet.arcs)
            if (arc is TransitionToPlaceArc)
                transformedPetriNet.addArc(transitionLookupTable[arc.from.id], placesLookupTable[arc.to.id])
            else
                transformedPetriNet.addArc(placesLookupTable[arc.from.id], transitionLookupTable[arc.to.id])

        return DirectedCutVertexAlgorithm(transformedPetriNet).cutVertices().size.toDouble()
    }
}

class WeighedPlaceTransitionArcDegree(val alpha: Double) : SimplicityMetric, CachedModelMetric() {
    override fun doComputation(reference: ProcessModel): Double {
        val petrinet = reference.toPetriNet()

        return (alpha * AverageTransitionArcDegree().compute(petrinet)) + (
            (1 - alpha) * AveragePlaceArcDegree().compute(
                petrinet
            )
            )
    }
}
