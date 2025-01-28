package gal.usc.citius.processmining.utils.transformers

import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.WeightedArc
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import java.util.Deque
import java.util.LinkedList

/**
 * Given a connected digraph with one source and one sink vertices, and where all vertices are in, at least, a path from
 * root to sink (i.e., a Directly Follows Graph), obtains an approximation to the maximally filtered directly follows
 * graph (MF-DFG) maximizing (or minimizing) the total weight of the DFG by combining two arborescence with maximum
 * (or minimum weight) using Edmonds' Algorithm.
 *
 * @param minimum if set to true search for the minimum equivalent graph with the lowest overall weight.
 *
 * @return an approximation to the MF-DFG problem with a maximum total weight (minimum if [minimum] is true).
 */
fun DirectlyFollowsGraph.filterEdgesTWE(minimum: Boolean = false): DirectlyFollowsGraph {
    this.checkDFGCorrectness()
    val digraph = this.removeSelfCycles()
        .let { if (minimum) it.invertWeights() else it }

    // Call Edmond's algorithm forward
    val root = digraph.activities.first { activity ->
        activity !in this.arcs.map { it.target }
    }
    val fwMSA = digraph.getSpanningArborescence(root)

    // Call Edmond's algorithm backwards
    val sink = digraph.activities.first { activity ->
        activity !in this.arcs.map { it.source }
    }
    val bwMSA = digraph.reverse().getSpanningArborescence(sink).reverse()

    // Merge
    val mfdfg = DirectlyFollowsGraph(
        id = this.id,
        activities = digraph.activities,
        arcs = fwMSA.arcs + bwMSA.arcs
    )

    return if (minimum) mfdfg.invertWeights() else mfdfg
}

/**
 * Edmonds' Algorithm: get the spanning arborescence with the maximum weight (the sum of all arc weights). An
 * arborescence is a directed graph in which, for a vertex u called the root and any other vertex v, there is exactly
 * one directed path from u to v. An arborescence is thus the directed-graph form of a rooted tree.
 *
 * @param root activity from the graph to be the root of the arborescence.
 * @param minimum if set to true search for the minimum spanning arborescence with the lowest overall weight.
 *
 * @return the spanning arborescence rooted in [root] and with a maximum total weight (minimum if [minimum] is set to
 * true).
 */
fun DirectlyFollowsGraph.getSpanningArborescence(root: Activity, minimum: Boolean = false): DirectlyFollowsGraph {
    var arborescence: DirectlyFollowsGraph
    // If we search for the minimum invert the edge weights
    var digraph = if (minimum) this.invertWeights() else this

    val cyclesLIFO: Deque<Pair<Activity, DirectlyFollowsGraph>> = LinkedList()
    val arcsHistoryLIFO: Deque<MutableMap<WeightedArc, WeightedArc>> = LinkedList()
    var cycleCounter = 0
    // Iterative arborescence construction until no cycles remaining
    do {
        // Get, for each vertex except the root, the incoming arc with more weight
        arborescence = digraph.copy(
            arcs = digraph.activities
                .filter { it != root }
                .mapNotNull { currentActivity ->
                    digraph.arcs
                        .filter { it.target == currentActivity }
                        .maxByOrNull { it.weight }
                }
                .toSet()
        )

        // Detect cycles
        val cycles = arborescence.getArborescenceCycles()

        // If there is any cycle, copy the current digraph collapsing the cycles into
        // single activities, and updating the weights of the arcs entering the cycle
        cycles.forEach { cycle ->
            // Activity to collapse the cycle
            val collapsedCycle = Activity.from("cycle${cycleCounter++}")
            // Save the cycle to re-expand it later
            cyclesLIFO.push(collapsedCycle to cycle)
            // Arcs renaming history for this cycle
            val arcsHistory = mutableMapOf<WeightedArc, WeightedArc>()
            // Arc inside the cycle with minimum weight
            val minArc = cycle.arcs.minByOrNull { it.weight }!!
            // Update digraph collapsing the cycle
            digraph = DirectlyFollowsGraph(
                id = digraph.id,
                activities = digraph.activities - cycle.activities + collapsedCycle,
                arcs = digraph.arcs.mapNotNull { arc ->
                    when {
                        arc.source !in cycle.activities && arc.target !in cycle.activities -> {
                            // Arc outside the cycle
                            arc
                        }
                        arc.source !in cycle.activities && arc.target in cycle.activities -> {
                            // Arc entering the cycle
                            val newArc = arc.copy(
                                target = collapsedCycle,
                                weight = arc.weight + minArc.weight - cycle.arcs.find { it.target == arc.target }!!.weight
                            )
                            arcsHistory += newArc to arc
                            newArc
                        }
                        arc.source in cycle.activities && arc.target !in cycle.activities -> {
                            // Arc leaving the cycle
                            val newArc = arc.copy(
                                source = collapsedCycle,
                                weight = arc.weight
                            )
                            arcsHistory += newArc to arc
                            newArc
                        }
                        else -> {
                            // Arc inside the cycle
                            null
                        }
                    }
                }.toSet()
            )
            // Save this arc renaming history
            arcsHistoryLIFO.push(arcsHistory)
        }
    } while (cycles.isNotEmpty())

    // Re-expand the cycles
    while (cyclesLIFO.isNotEmpty()) {
        // Retrieve last collapsed cycle
        val (collapsedCycle, cycle) = cyclesLIFO.pop()
        // Retrieve arc renaming history for this cycle
        val arcsHistory = arcsHistoryLIFO.pop()
        // Update the arcs in the arborescence going to/from the collapsed cycle
        val newArcs = arborescence.arcs.map { arc ->
            when (arc) {
                in arcsHistory.keys -> arcsHistory[arc]!!
                else -> arc
            }
        }.toSet()
        // Update the arborescence including the re-expanded cycle, and removing
        // the arc going to the same activity as the new arc entering the cycle
        arborescence = arborescence.copy(
            activities = arborescence.activities - collapsedCycle + cycle.activities,
            arcs = newArcs + cycle.arcs.filter { arc ->
                arc.target !in newArcs.map { it.target }
            }
        )
    }

    return if (minimum) arborescence.invertWeights() else arborescence
}

/**
 * Given an arborescence, a directed graph where each activity contains only one incoming edge, except for the root which
 * contains no incoming edges, search the cycles, if any.
 *
 * For this, the vertexes with no incoming or outgoing arcs are removed iteratively, along with the arcs connected to
 * them. Once there are no more vertexes without incoming or outgoing edges, the remaining structures are the cycles, if
 * any.
 *
 * @return a set with the cycles in the arborescence, if any.
 */
private fun DirectlyFollowsGraph.getArborescenceCycles(): Set<DirectlyFollowsGraph> {
    var digraph = this.copy()

    // Remove iteratively the arcs of those activities with no incoming or outgoing edges
    do {
        val fullyConnectedActivities = digraph.arcs.map { it.source }.intersect(digraph.arcs.map { it.target })
        digraph = DirectlyFollowsGraph(
            activities = fullyConnectedActivities,
            arcs = digraph.arcs.filter { it.source in fullyConnectedActivities && it.target in fullyConnectedActivities }.toSet()
        )
        // Check if there is still orphan activities remaining
        val orphanRemaining = fullyConnectedActivities.any { activity ->
            activity !in digraph.arcs.map { it.source } ||
                activity !in digraph.arcs.map { it.target }
        }
    } while (orphanRemaining)

    // Retrieve each connected component, i.e., each cycle
    val visitedActivities = mutableSetOf<Activity>()
    val cycles = mutableSetOf<DirectlyFollowsGraph>()
    while (visitedActivities != digraph.activities) {
        // get one unvisited activity and get the activities reaching it
        val unvisitedActivity = digraph.activities.first { it !in visitedActivities }
        val cycle = digraph.getSubgraphConnectedTo(unvisitedActivity)
        // mark all cycle activities as visited
        visitedActivities += cycle.activities
        // save the cycle
        cycles += cycle
    }

    return cycles
}

/**
 * Given a connected digraph with one source and one sink vertices, and where all vertices are in, at least, a path from
 * root to sink (i.e., a Directly Follows Graph), obtains an approximation to the maximally filtered directly follows
 * graph (MF-DFG) maximizing (or minimizing) the total weight of the DFG by removing the edges one by one, and ensuring
 * that all vertices remain in a path from source to sink.
 *
 * @param minimum if set to true search for the MF-DFG with the lowest total weight.
 *
 * @return an approximation to the MF-DFG problem with a maximum total weight (minimum if [minimum] is true).
 */
fun DirectlyFollowsGraph.filterEdgesGreedy(minimum: Boolean = false): DirectlyFollowsGraph {
    this.checkDFGCorrectness()
    val digraph = this.removeSelfCycles()

    var meg: DirectlyFollowsGraph = digraph
    val root = meg.activities.first { activity -> activity !in meg.arcs.map { arc -> arc.target } }
    val sink = meg.activities.first { activity -> activity !in meg.arcs.map { arc -> arc.source } }
    // Order the arcs based in the frequency and remove one by one if
    // the removal maintains all activities in a path from start to end
    digraph.arcs.let { arcs ->
        if (minimum) arcs.sortedByDescending { it.weight } else arcs.sortedBy { it.weight }
    }.forEach { arc ->
        // If source and sink have (respectively) only one out/in arc, its deletion will surely lead to unsoundness
        if (meg.arcs.filter { it.source == arc.source }.count() > 1 && meg.arcs.filter { it.target == arc.target }.count() > 1) {
            // Create graph without this arc
            val simplifiedMeg = meg.copy(arcs = meg.arcs - arc)
            // If all activities are in a path from start to end use this graph for next iteration
            if (simplifiedMeg.getReachableActivitiesFrom(root) == (simplifiedMeg.activities - root) &&
                simplifiedMeg.reverse().getReachableActivitiesFrom(sink) == (simplifiedMeg.activities - sink)
            ) {
                meg = simplifiedMeg
            }
        }
    }

    return meg
}

/**
 * Given a connected digraph with one source and one sink vertices, and where all vertices are in, at least, a path from
 * root to sink (i.e., a Directly Follows Graph), obtains an approximation to the maximally filtered directly follows
 * graph (MF-DFG) maximizing (or minimizing) the total weight of the DFG by applying the Greedy technique to the result
 * of the Two Ways Edmonds technique.
 *
 * @param minimum if set to true search for the MF-DFG with the lowest total weight.
 *
 * @return an approximation to the MF-DFG problem with a maximum total weight (minimum if [minimum] is true).
 */
fun DirectlyFollowsGraph.filterEdgesTWEG(minimum: Boolean = false): DirectlyFollowsGraph =
    this.filterEdgesTWE(minimum).filterEdgesGreedy(minimum)

/**
 * Check the properties of the graph to ensure it is valid to calculate the MEG and remove self-cycles.
 *
 * @throws RuntimeException if the current graph violates a DFG correctness measure.
 */
internal fun DirectlyFollowsGraph.checkDFGCorrectness(dfgName: String = "DFG") {
    if (!this.isConnected()) {
        throw RuntimeException("The $dfgName is not connected.")
    }
    if (this.arcs.map { it.target }.toSet().size != (this.activities.size - 1)) {
        throw RuntimeException("The $dfgName has more than one start vertex.")
    }
    if (this.arcs.map { it.source }.toSet().size != (this.activities.size - 1)) {
        throw RuntimeException("The $dfgName has more than one end vertex.")
    }
    val root = this.activities.first { activity -> activity !in this.arcs.map { edge -> edge.target } }
    val sink = this.activities.first { activity -> activity !in this.arcs.map { edge -> edge.source } }
    if (this.getReachableActivitiesFrom(root) != (this.activities - root) ||
        this.reverse().getReachableActivitiesFrom(sink) != (this.activities - sink)
    ) {
        throw RuntimeException("The $dfgName is not sound")
    }
}
