package gal.usc.citius.processmining.model.process.dfg

import gal.usc.citius.processmining.model.Direction
import gal.usc.citius.processmining.model.EdgeType
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.WeightedArc
import java.util.LinkedList
import java.util.Queue

open class DirectlyFollowsGraph(
    override val id: String = "DefaultDFGName",
    override val activities: Set<Activity>,
    open val arcs: Set<WeightedArc>
) : ProcessModel {

    operator fun contains(activity: Activity): Boolean = activity in this.activities
    operator fun contains(arc: WeightedArc): Boolean = arc in this.arcs

    /**
     * Get the set of activities with an outgoing arc to [activity].
     *
     * @return a set with the nodes with an outgoing arc to [activity].
     */
    fun predecessors(activity: Activity): Set<Activity> =
        this.arcs.filter { it.target == activity }.map { it.source }.toSet()

    /**
     * Get the set nodes with an incoming arc from [activity].
     *
     * @return a set with the nodes with an incoming arc from [activity].
     */
    fun successors(activity: Activity): Set<Activity> =
        this.arcs.filter { it.source == activity }.map { it.target }.toSet()

    /**
     * Get the set nodes with an outgoing arc to the node with [id] as id.
     *
     * @return a set with the nodes with an outgoing arc to the node with [id] as id.
     */
    fun predecessors(id: String): Set<Activity> = this.predecessors(this.activities.first { it.id == id })

    /**
     * Get the set nodes with an incoming arc from the node with [id] as id.
     *
     * @return a set with the nodes with an incoming arc from the node with [id] as id.
     */
    fun successors(id: String): Set<Activity> = this.successors(this.activities.first { it.id == id })

    /**
     * Invert the weights of all the arcs in this graph.
     *
     * @return an instance of this graph with the weights of all arcs inverted.
     */
    fun invertWeights(): DirectlyFollowsGraph =
        this.copy(
            arcs = this.arcs.map { arc ->
                arc.copy(weight = -arc.weight)
            }.toSet()
        )

    /**
     * Check if the graph is connected, i.e., for all two vertexes u and v in the graph, there is a weak path (a path
     * with arcs in any direction) from u to v.
     *
     * @return true if all the nodes of this graph are weakly connected between them.
     */
    fun isConnected(): Boolean =
        this.getSubgraphConnectedTo(this.activities.first()).activities == this.activities

    /**
     * Get the graph result of reverting all the direction of the edges in the current graph.
     *
     * @return an instance of this graph with the direction of all edges reverted.
     */
    fun reverse(): DirectlyFollowsGraph =
        this.copy(
            arcs = this.arcs.map { arc ->
                arc.copy(
                    source = arc.target,
                    target = arc.source,
                    weight = arc.weight
                )
            }.toSet()
        )

    /**
     * Get the connected subgraph which vertices and edges are reachable (backward and forward) from [start], i.e., the
     * connected component of the graph containing [start].
     *
     * @param start the node to which all the elements in the returned subgraph are connected.
     *
     * @return the connected component of this graph containing [start].
     */
    fun getSubgraphConnectedTo(start: Activity): DirectlyFollowsGraph {
        val visitedActivities = mutableSetOf<Activity>()
        // Create FIFO to store the nodes to explore
        val fifo: Queue<Activity> = LinkedList()
        fifo.add(start)
        // Depth-search
        while (fifo.isNotEmpty()) {
            val node = fifo.remove()
            visitedActivities.add(node)
            // Expand forward
            fifo += this.arcs
                .filter { it.source == node }
                .map { it.target }
                .filter { it !in visitedActivities }
            // Expand backward
            fifo += this.arcs
                .filter { it.target == node }
                .map { it.source }
                .filter { it !in visitedActivities }
        }

        return DirectlyFollowsGraph(
            id = "${this.id}-subgraph",
            activities = visitedActivities,
            arcs = this.arcs.filter { it.source in visitedActivities && it.target in visitedActivities }.toSet()
        )
    }

    /**
     * Get the set of vertices reachable from [start].
     *
     * @param start the vertex which all the elements in the returned subgraph must have a directed path from.
     *
     * @return the set of vertices with a directed path from [start].
     */
    fun getReachableActivitiesFrom(start: Activity): Set<Activity> {
        val visitedActivities = mutableSetOf<Activity>()
        // Create FIFO to store the nodes to explore
        val fifo: Queue<Activity> = LinkedList()
        fifo.add(start)
        // Depth-search
        while (fifo.isNotEmpty()) {
            val node = fifo.remove()
            // Expand forward
            fifo += this.arcs
                .filter { it.source == node }
                .map { it.target }
                .filter { it !in visitedActivities }
                .also { visitedActivities.addAll(it) }
        }
        // rReturn the reached nodes
        return visitedActivities
    }

    /**
     * Remove the self-cycles from the current graph.
     *
     * @return an instance of the current graph without the self cycles.
     */
    fun removeSelfCycles(): DirectlyFollowsGraph = this.copy(
        arcs = this.arcs.filter { arc ->
            arc.source != arc.target
        }.toSet()
    )

    /**
     * Return the current directly-follows graph replacing the arcs and activities of the cycle denoted by [cycle] with
     * an activity denoted by [activity].
     *
     * @param cycle the cycle to collapse.
     * @param activity the activity to replace the cycle with.
     *
     * @return the current graph with the arcs and activities of [cycle] replaced by [activity].
     */
    fun collapseCycle(cycle: DirectlyFollowsGraph, activity: Activity): DirectlyFollowsGraph = this.copy(
        activities = this.activities - cycle.activities + activity,
        arcs = (this.arcs - cycle.arcs).map { arc ->
            when {
                arc.source in cycle.activities && arc.target in cycle.activities ->
                    arc.copy(source = activity, target = activity, weight = arc.weight)
                arc.source in cycle.activities ->
                    arc.copy(source = activity, weight = arc.weight)
                arc.target in cycle.activities ->
                    arc.copy(target = activity, weight = arc.weight)
                else -> arc
            }
        }.toSet()
    )

    /**
     * Collapse all the cycles of this directly-follows graph by replacing all the activities and arcs of each one with
     * new artificial activities.
     */
    fun collapseAllCycles(): DirectlyFollowsGraph {
        var reduced = this.removeSelfCycles()
        var i = 0
        // Reduce each cycle to a single activity
        while (reduced.hasCycle()) {
            // Get a cycle of the DFP
            val cycle = reduced.getRandomCycle()
            if (cycle.activities.isNotEmpty()) {
                val collapsedActivity = Activity.from("cycle-${i++}")
                // Reduce the cycle in the net and remove self-cycles
                reduced = reduced.collapseCycle(cycle, collapsedActivity).removeSelfCycles()
            }
        }
        return reduced
    }

    /**
     * Check if the current graph has a cycle assuming it is a connected graph.
     *
     * @return true if the current graph has a cycle.
     */
    fun hasCycle(): Boolean =
        this.activities.any { activity ->
            this.hasCycle(activity, setOf(activity)) || this.reverse().hasCycle(activity, setOf(activity))
        }

    /**
     * Explore recursively through each of the outputs of [activity] to check if there is a cycle in the graph, i.e.,
     * an activity that has already been visited and its in [visited].
     *
     * @param activity activity to continue the exploration with.
     * @param visited set of already visited activities.
     *
     * @return true if there is a cycle in the current graph, false otherwise.
     */
    private fun hasCycle(activity: Activity, visited: Set<Activity>): Boolean {
        var found = false
        val connectedActivities = this.arcs.filter { it.source == activity }.map { it.target }.toMutableList()
        // Check if any of the connected activities has been already visited
        if (connectedActivities.any { it in visited }) {
            found = true
        } else {
            // If not, continue exploring
            while (!found && connectedActivities.isNotEmpty()) {
                val activityToExplore = connectedActivities.removeFirst()
                found = this.hasCycle(activityToExplore, visited + activityToExplore)
            }
        }
        return found
    }

    /**
     * Get a random cycle from the current DFPattern.
     *
     * @return a [DirectlyFollowsGraph] instance with the arcs of the cycle if there exists any, or with empty activities
     * and arcs if not.
     */
    fun getRandomCycle(): DirectlyFollowsGraph {
        lateinit var dfg: DirectlyFollowsGraph
        val cycleArcs = mutableSetOf<WeightedArc>()
        this.activities.any { activity ->
            // Get cycle
            dfg = when {
                // Search forward and return cycle if found
                this.findCycle(activity, cycleArcs) -> {
                    DirectlyFollowsGraph(
                        id = "$id-cycle",
                        activities = cycleArcs.map { it.source }.toSet(),
                        arcs = cycleArcs
                    )
                }
                // Search backward if not found
                this.reverse().findCycle(activity, cycleArcs) -> {
                    DirectlyFollowsGraph(
                        id = "$id-cycle",
                        activities = cycleArcs.map { it.source }.toSet(),
                        arcs = cycleArcs
                    ).reverse()
                }
                // Empty DFG if not found
                else -> {
                    DirectlyFollowsGraph(
                        id = "$id-cycle",
                        activities = emptySet(),
                        arcs = emptySet()
                    )
                }
            }
            // If cycle found, stop the iterative process
            dfg.arcs.isNotEmpty()
        }
        return dfg
    }

    /**
     * Continue the exploration through each of the outputs of [activity] individually, and recursively, until ending the
     * exploration or finding a cycle. If a cycle is found return true and store in [arcs] the arcs forming the cycle, if
     * not, return false and empty [arcs].
     *
     * @param activity activity to continue the exploration with.
     * @param arcs set of arcs with the already explored ones, and where the cycle arcs are stored if found.
     *
     * @return true if a cycle has been found (its arcs are stored in [arcs]), and false if not.
     */
    private fun findCycle(activity: Activity, arcs: MutableSet<WeightedArc>): Boolean {
        var found = false
        // Check if any arc closes the a cycle
        val connectedArcs = this.arcs.filter { it.source == activity }.toMutableList()
        val closingArc = connectedArcs.find { connectedArc -> arcs.any { it.source == connectedArc.target } }
        if (closingArc != null) {
            // Cut other entrance to the cycle and preceding arcs
            var arcToRemove = arcs.find { arc -> arc.target == closingArc.target }
            while (arcToRemove != null) {
                arcs.remove(arcToRemove)
                arcToRemove = arcs.find { arc -> arc.target == arcToRemove!!.source }
            }
            // Add closing arc to the cycle
            arcs.add(closingArc)
            found = true
        } else {
            // Continue exploring
            while (!found && connectedArcs.isNotEmpty()) {
                val exploringArc = connectedArcs.removeFirst()
                val newArcs = (arcs + exploringArc).toMutableSet()
                if (this.findCycle(exploringArc.target, newArcs)) {
                    // Mark as found
                    found = true
                    // Update arcs with cycle arcs
                    arcs.clear()
                    arcs.addAll(newArcs)
                }
            }
        }
        return found
    }

    override fun toDOT(edges: EdgeType, direction: Direction): String = """
            |digraph {
            |   splines=${edges.value};
            |   rankdir = "${direction.name}"
            |
            |   //ACTIVITIES
                ${this.activities.joinToString("\n") { "|   \"${it.id}\" [shape=box, width=2, style=solid, label=\"${it.name}\"];" }}
            |
            |   //ARCS
                ${this.arcs.joinToString("\n") { "|   \"${it.source.id}\" -> \"${it.target.id}\";" }}
            |}""".trimMargin()

    override fun toString(): String =
        "${this.activities.joinToString(separator = "\n") { it.id }}\n${arcs.joinToString(separator = "\n") { "${it.source.id} -> ${it.target.id}" }}"

    override fun hashCode(): Int = ((id.hashCode() * 31 + activities.hashCode()) * 31 + arcs.hashCode()) * 31

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is DirectlyFollowsGraph &&
                    this.id == other.id &&
                    this.activities == other.activities &&
                    this.arcs == other.arcs
                )

    fun copy(
        id: String = this.id,
        activities: Set<Activity> = this.activities,
        arcs: Set<WeightedArc> = this.arcs
    ) = DirectlyFollowsGraph(id, activities, arcs)
}
