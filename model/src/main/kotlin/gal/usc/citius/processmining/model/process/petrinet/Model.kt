package gal.usc.citius.processmining.model.process.petrinet

import gal.usc.citius.processmining.model.Direction
import gal.usc.citius.processmining.model.EdgeType
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import java.io.Serializable

@Suppress("MemberVisibilityCanBePrivate")
data class PetriNet internal constructor(
    override val id: String,
    override val activities: Set<Activity>,
    val transitions: Set<Transition>,
    val places: Set<Place>,
    val arcs: Set<Arc>
) : ProcessModel {
    val initialPlaces: Set<Place> get() = this.places.filter { it.isInitial }.toSet()
    val finalPlaces: Set<Place> get() = this.places.filter { it.isFinal }.toSet()

    override fun toDOT(edges: EdgeType, direction: Direction): String = """
            |digraph {
            |   splines=${edges.value};
            |   rankdir = "${direction.name}"
            |
            |   //TRANSITIONS
                ${this.transitions.filterNot { it.isSilent }.joinToString("\n") { "|   \"${it.id}\" [shape=box, width=2, style=solid, label=\"${it.name}\"];" }}
            |
            |   //SILENT TRANSITIONS
                ${this.transitions.filter { it.isSilent }.joinToString("\n") { "|   \"${it.id}\" [shape=box, fixedsize=true,${if (direction == Direction.TB || direction == Direction.BT) "height = 0.1," else ""} width=${if (direction == Direction.TB || direction == Direction.BT) "0.5" else "0.1"}, label=\"\", style=filled, fillcolor=\"black\"];" }}
            |
            |   //PLACES
                ${this.places.filter { !it.isInitial && !it.isFinal }.joinToString("\n") { "|   \"${it.id}\" [shape=circle, fixedsize=true, width=0.5, label=\"\", style=solid];" }}
            |
            |   //INITIAL PLACES
                ${this.places.filter { it.isInitial }.joinToString("\n") { "|   \"${it.id}\" [shape=circle, fixedsize=true, width=0.6, label=\"●\", style=bold];" }}
            |
            |   //FINAL PLACES
                ${this.places.filter { it.isFinal }.joinToString("\n") { "|   \"${it.id}\" [shape=circle, fixedsize=true, width=0.6, label=\"○\", style=bold];" }}
            |
            |   //ARCS
                ${this.arcs.joinToString("\n") { "|   \"${it.from.id}\" -> \"${it.to.id}\";" }}
            |}""".trimMargin()
}

interface Arc : Serializable {
    val from: Node
    val to: Node
}

interface Node : Activity

data class Transition internal constructor(
    override val id: String,
    override val name: String = id,
    var isSilent: Boolean = false
) : Node

data class Place internal constructor(
    override val id: String,
    override val name: String = id,
    var isInitial: Boolean = false,
    var isFinal: Boolean = false
) : Node

data class TransitionToPlaceArc(override val from: Transition, override val to: Place) : Arc
data class PlaceToTransitionArc(override val from: Place, override val to: Transition) : Arc
