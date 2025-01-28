package gal.usc.citius.processmining.metrics

import be.kuleuven.econ.cbf.input.MemoryMapping
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.PlaceToTransitionArc
import gal.usc.citius.processmining.model.process.petrinet.TransitionToPlaceArc
import gal.usc.citius.processmining.utils.translators.toXLog
import org.processmining.models.graphbased.directed.petrinet.Petrinet
import org.processmining.models.graphbased.directed.petrinet.elements.Place
import org.processmining.models.graphbased.directed.petrinet.elements.Transition
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory
import org.processmining.models.semantics.petrinet.Marking
import java.io.OutputStream
import java.io.PrintStream

internal class EnhancedMemoryMapping(log: Log<*>, private val net: PetriNet) : MemoryMapping(log.toXLog(), net.toProMPetrinet()) {
    override fun getPetrinetWithMarking(): Array<Any> {
        val marking = Marking()

        val initialPlaces = net.initialPlaces.map { it.name }
        val availablePlaces = super.getPetrinet().places
        marking.addAll(availablePlaces.filter { it.label in initialPlaces })

        return arrayOf(super.getPetrinet(), marking)
    }

    fun assignVisibilityFromModel() {
        this.assignUnmappedToVisible()

        // The activities marked as silent in the Petri net are remapped as silent
        for (silent in net.transitions.filter { it.isSilent }) {
            this.setActivity(silent.id, this.getActivity(silent.id), silent.isSilent)
        }
    }
}

private fun PetriNet.toProMPetrinet(): Petrinet {
    val transformedPetriNet = PetrinetFactory.newPetrinet(this.id)
    val transitions = mutableMapOf<String, Transition>()
    val places = mutableMapOf<String, Place>()

    for (transition in this.transitions) {
        if (transition.isSilent) {
            val t: Transition = transformedPetriNet.addTransition(transition.id)
            t.isInvisible = true
            transitions[t.label] = t
        } else {
            val t = transformedPetriNet.addTransition(transition.name)
            transitions[t.label] = t
        }
    }
    for (place in this.places) {
        val p = transformedPetriNet.addPlace(place.name)
        places[p.label] = p
    }
    for (arc in this.arcs) {
        if (arc is TransitionToPlaceArc) {
            if (arc.from.isSilent)
                transformedPetriNet.addArc(transitions[arc.from.id], places[arc.to.name])
            else
                transformedPetriNet.addArc(transitions[arc.from.name], places[arc.to.name])
        } else if (arc is PlaceToTransitionArc) {
            if (arc.to.isSilent)
                transformedPetriNet.addArc(places[arc.from.name], transitions[arc.to.id])
            else
                transformedPetriNet.addArc(places[arc.from.name], transitions[arc.to.name])
        }
    }

    return transformedPetriNet
}

fun <T : Any> runSilently(fn: () -> T): T {
    val output = System.out
    val error = System.err

    System.setErr(NullPrintStream)
    System.setOut(NullPrintStream)
    val result = fn()
    System.setErr(error)
    System.setOut(output)

    return result
}
private object NullPrintStream : PrintStream(NullOutputStream)
private object NullOutputStream : OutputStream() {
    override fun write(b: Int) {}
}
