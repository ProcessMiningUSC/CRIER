package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder
import gal.usc.citius.processmining.model.process.processtree.ProcessTree
import gal.usc.citius.processmining.utils.simplifiers.reduce

/**
 * Translate the current [DirectlyFollowsGraph] to the equivalent [PetriNet]. Replace each activity in the DFG for a
 * structure Place->Transition->Place and connect the output place of U to the input place of V with an invisible
 * transition for each arc (U, V) in the DFG.
 *
 * @return The equivalent [PetriNet] to the current [DirectlyFollowsGraph].
 */
fun DirectlyFollowsGraph.toPetriNet(): PetriNet {
    val pnBuilder = PetriNetBuilder.forProcess(this.id)
    // Add for each activity its transition, an input place and an output place.
    this.activities.forEach { activity ->
        // Input place
        pnBuilder.place().id(inputPlaceOf(activity.id)).add()
        // Activity transition
        pnBuilder.transition().id(activity.id).name(activity.name).add()
        // Output place
        pnBuilder.place().id(outputPlaceOf(activity.id)).add()
        // Arcs
        pnBuilder.arc().fromPlace(inputPlaceOf(activity.id)).to(activity.id).add()
        pnBuilder.arc().fromTransition(activity.id).to(outputPlaceOf(activity.id)).add()
    }
    // Connect with an invisible transition the source's output place of each arc with the target's input place
    this.arcs.forEach { arc ->
        val transitionId = "${arc.source.id}->${arc.target.id}"
        pnBuilder.transition().id(transitionId).name("${arc.source.name}->${arc.target.name}").silent(true).add()
        pnBuilder.arc().fromPlace(outputPlaceOf(arc.source.id)).to(transitionId).add()
        pnBuilder.arc().fromTransition(transitionId).to(inputPlaceOf(arc.target.id)).add()
    }
    // Start place
    pnBuilder.place().id("StartPlace").initial(true).add()
    (this.activities - this.arcs.map { it.target }).forEach { startActivity ->
        val transitionId = "start->${startActivity.id}"
        pnBuilder.transition().id(transitionId).name("start->${startActivity.name}").silent(true).add()
        pnBuilder.arc().fromPlace("StartPlace").to(transitionId).add()
        pnBuilder.arc().fromTransition(transitionId).to(inputPlaceOf(startActivity.id)).add()
    }
    // End place
    pnBuilder.place().id("EndPlace").final(true).add()
    (this.activities - this.arcs.map { it.source }).forEach { endActivity ->
        val transitionId = "${endActivity.id}->end"
        pnBuilder.transition().id(transitionId).name("${endActivity.name}->end").silent(true).add()
        pnBuilder.arc().fromPlace(outputPlaceOf(endActivity.id)).to(transitionId).add()
        pnBuilder.arc().fromTransition(transitionId).to("EndPlace").add()
    }

    return pnBuilder.build().reduce()
}

private fun inputPlaceOf(name: String): String = "I_$name"
private fun outputPlaceOf(name: String): String = "O_$name"

fun DirectlyFollowsGraph.toProcessTree(): ProcessTree = TODO()

fun DirectlyFollowsGraph.toCausalNet(): CausalNet = TODO()

fun DirectlyFollowsGraph.toCausalMatrix(): CausalMatrix = TODO()

fun DirectlyFollowsGraph.toBPMN(): BPMN = TODO()
