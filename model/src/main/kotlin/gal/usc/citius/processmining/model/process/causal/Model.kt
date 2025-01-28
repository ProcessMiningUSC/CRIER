package gal.usc.citius.processmining.model.process.causal

import gal.usc.citius.processmining.model.Direction
import gal.usc.citius.processmining.model.EdgeType
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.Arc
import gal.usc.citius.processmining.model.process.ProcessModel
import java.util.LinkedList
import java.util.Queue
import kotlin.random.Random

/**
 * Process model with the relations of the activities in a causal format.
 *
 * @param id process model identifier
 * @param activities list with the activities of the model
 */
abstract class CausalModel<A : CausalActivity>(
    override val id: String,
    override val activities: Set<A>
) : ProcessModel {

    val startActivity: A
        get() = activities.first { it.inputs.isEmpty() }
    val endActivity: A
        get() = activities.first { it.outputs.isEmpty() }

    val arcs
        get() = activities.flatMap { activity ->
            // Arcs from each input activity to this activity
            activity.inputs.flatten().map { inputId ->
                val input = activities.first { it.id == inputId }
                Arc(
                    Activity.from(input.id, input.name),
                    Activity.from(activity.id, activity.name)
                )
            }
        }.toSet()

    operator fun contains(activityId: String): Boolean = activities.any { it.id == activityId }

    operator fun contains(activity: Activity): Boolean = activity.id in this

    operator fun contains(arcIds: Pair<String, String>): Boolean =
        arcIds.first in this &&
            arcIds.second in this &&
            this[arcIds.first].outputs.flatten().any { it == arcIds.second } &&
            this[arcIds.second].inputs.flatten().any { it == arcIds.first }

    operator fun contains(arc: Arc): Boolean = (arc.source.id to arc.target.id) in this

    operator fun get(activityId: String): A {
        return if (activityId in this) {
            activities.first { it.id == activityId }
        } else {
            throw NoSuchElementException("The activity $activityId is not present in the model.")
        }
    }

    operator fun get(activity: Activity): A = this[activity.id]

    operator fun get(arcIds: Pair<String, String>): Arc {
        return if (arcIds in this) {
            Arc(this[arcIds.first], this[arcIds.second])
        } else {
            throw NoSuchElementException("The arc $arcIds is not present in the model.")
        }
    }

    fun getActivity(id: String): A = this[id]

    fun getArc(arcIds: Pair<String, String>): Arc = this[arcIds]

    override fun toDOT(edges: EdgeType, direction: Direction): String = """
            |digraph {
            |   splines=${edges.value};
            |   rankdir = "${direction.name}"
            |
            |   //ACTIVITIES
                ${this.activities.joinToString("\n") { "|   \"${it.id}\" [shape=box, width=2, style=solid, label=\"${it.name}\"];" }}
            |
            |   //ARCS
                ${this.activities.flatMap { activity ->
        activity.outputs.flatten().map { activity.id to it }
    }.distinct().joinToString("\n") { "|   \"${it.first}\" -> \"${it.second}\";" }}
            |}""".trimMargin()

    /**
     * Check if the current pattern is connected, i.e., all activities are connected to each other through its inputs
     * or its outputs.
     *
     * @return true if the current pattern is connected, false if not.
     */
    fun isConnected(): Boolean {
        // Reached activities in the expansion
        val reached = mutableSetOf(activities.random(Random(666)))
        // Activities to explore
        val toExplore: Queue<A> = LinkedList()
        toExplore.add(reached.first())

        while (activities != reached && toExplore.isNotEmpty()) {
            val current = toExplore.remove()!!

            toExplore += (current.inputs.flatten() + current.outputs.flatten())
                .map { connectionId ->
                    activities.find { it.id == connectionId }!!
                }.filter { it !in reached }
            reached += toExplore
        }

        return (activities == reached)
    }
}

/**
 * Activity of a causal model.
 *
 * @property inputs activity input connections
 * @property outputs activity output connections
 */
abstract class CausalActivity(
    override val id: String,
    open val inputs: CausalConnections,
    open val outputs: CausalConnections,
    override val name: String = id
) : Activity

/**
 * Connections of an activity in a causal format, i.e., a set of sets of activities.
 */
typealias CausalConnections = Set<Set<String>>

/**
 * Function to build an empty instance of CausalConnections.
 */
fun emptyCausalConnections(): CausalConnections = emptySet()

/**
 * Function to build an instance of CausalConnections with the [elements] in it.
 */
fun causalConnectionsOf(vararg elements: Set<String>): CausalConnections =
    if (elements.isNotEmpty()) elements.toSet() else emptyCausalConnections()
