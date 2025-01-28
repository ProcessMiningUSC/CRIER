package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.ConnectionType
import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNetActivity
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.Place
import gal.usc.citius.processmining.model.process.petrinet.Transition
import gal.usc.citius.processmining.model.process.processtree.ProcessTree

internal fun PetriNet.toProcessTree(): ProcessTree = TODO()

/**
 * Transform a process model, in a [PetriNet] format, to a [CausalMatrix] format.
 *
 * @return the current process model in a [CausalMatrix] format.
 */
internal fun PetriNet.toCausalMatrix(): CausalMatrix =
    this.toCausalNet().toCausalMatrix()

/**
 * Transform a process model, in a [PetriNet] format, to a [CausalNet] format.
 *
 * @return the current process model in a [CausalNet] format.
 */
internal fun PetriNet.toCausalNet(): CausalNet =
    CausalNet(
        this.id,
        this.activities
            .map { petriActivity ->
                val inputs = getCausalNetConnections(ConnectionType.INPUT, petriActivity.id)
                val outputs = getCausalNetConnections(ConnectionType.OUTPUT, petriActivity.id)

                CausalNetActivity(petriActivity.id, inputs, outputs, petriActivity.name)
            }.toSet()
    )

/**
 * Get the connections, in causal net format, of an activity in a Petri net. Start in the activity declared and
 * continue the exploration through its in/out connections. The choices of each place connected to the activity are
 * combined with the choices of the other places to form the CN connections.
 * The silent activities are added as visible activities, and replaced after the combination. Each apparition of a
 * silent activity ID is replaced by its connections, performing a recursive call obtaining its connections.
 *
 * For instance, if an activity has two places: one with "A", and the other with "S1", being "S1" a silent activity. Its
 * connections will be the combination of {{A}} in one place, and {{S1}} in the other: {{A, S1}}. Then "S1" will be
 * replaced with its connections, obtained with a recursive call. The silent activity could have a place with two
 * options "B" and "C", giving as result {{B}, {C}}. This will result in {{A, B}, {A, C}} as connections for the
 * activity under analysis.
 *
 * @param type type of connections to get (inputs or outputs).
 * @param activityId the ID of the activity to get the connections from.
 * @param exploredSilentActivities set with the IDs of the silent tasks already explored.
 */
private fun PetriNet.getCausalNetConnections(
    type: ConnectionType,
    activityId: String,
    exploredSilentActivities: MutableSet<String> = mutableSetOf()
): CausalConnections {
    // Get the places connected to the current activity
    val places: Set<Place> = when (type) {
        ConnectionType.INPUT -> this.arcs.filter { it.to.id == activityId }.map { it.from as Place }.toSet()
        ConnectionType.OUTPUT -> this.arcs.filter { it.from.id == activityId }.map { it.to as Place }.toSet()
    }
    // A place contains the OR combinations (two activities connected to a place represents an OR between the
    // activities). The connections of each place must be combined (cartesian product) with the connections of the other
    // places, to form the AND subsets.
    val connections: MutableSet<Set<String>> = mutableSetOf(setOf()) // final connections
    val previousElements: MutableSet<String> = mutableSetOf() // activities already processed
    val silentConnections: MutableMap<String, Set<Set<String>>> = mutableMapOf() // silentActivityId -> its connections
    places.map { place ->
        // Get transitions connected to the current place
        val transitions: Set<Transition> = when (type) {
            ConnectionType.INPUT -> this.arcs.filter { it.to.id == place.id }.map { it.from as Transition }.toSet()
            ConnectionType.OUTPUT -> this.arcs.filter { it.from.id == place.id }.map { it.to as Transition }.toSet()
        }
        // Obtain and store the connections of the silent activities not
        // explored in order to replace them later by its connections.
        val placeExploredSilentActivities = exploredSilentActivities.toMutableSet()
        silentConnections +=
            transitions
                .filter { it.isSilent && it.id !in silentConnections && placeExploredSilentActivities.add(it.id) }
                .map { it.id to getCausalNetConnections(type, it.id, placeExploredSilentActivities) }

        // The place connections are all transitions but the already explored silent (each as a new OR subset):
        //     The already explored silent activities come from a recursive call. When a silent
        //     activity from the place connections has been already explored is because a loop
        //     through silent activities is being closed. Thus, is not necessary to add to the
        //     connections that silent activity as it has been already taken into account.
        transitions
            .filter { it.id !in exploredSilentActivities }
            .map { setOf(it.id) }
    }.forEach { placeConnections ->
        // Perform the cartesian product of the connections of each place with the connections of other places
        val tmpConnections = connections.toSet()
        connections.clear()
        // Each subset from the current place (OR) is going to be combined with the current combinations in
        // [tmpConnections] which does not contain any element from other subsets in the current place, and in case
        // of having an element in some subsets from [tmpConnections], only with those.
        placeConnections.forEach { subset ->
            if (subset.any { it in previousElements }) {
                // One of the elements in [subset] has been already combined, thus, [subset] must be
                // only combined with the already created [tmpConnections] having elements in common.
                connections += tmpConnections
                    .filter { tmpSubset -> tmpSubset.any { it in subset } }
                    .map { tmpSubset ->
                        tmpSubset + subset
                    }
            } else {
                // All elements in [subset] are new, thus, combine with those current combinations in
                // [tmpConnections] not having an element in the other subsets from the current place.
                connections += tmpConnections
                    .filter { tmpSubset -> tmpSubset.none { it in placeConnections.flatten() } }
                    .map { tmpSubset ->
                        subset + tmpSubset
                    }
            }
        }
        // Add processed elements
        previousElements.addAll(placeConnections.flatten())
    }
    // Finally remove empty sets and replace silent IDs with its connections
    return connections
        .filter { it.isNotEmpty() }
        .flatMap { connectionsSubset ->
            var set = setOf(connectionsSubset)
            // For each ID of a silent task, replace in current subsets the ID with its connections in
            // [silentActivitiesToReplace], duplicating the subsets for each connections to replace.
            connectionsSubset
                .filter { it in silentConnections }
                .forEach { silentId ->
                    // For each silent ID in connectionSubset
                    set = set
                        .filter { silentId in it }
                        .flatMap { silentConnectionSubset ->
                            // Replace it with each of its connections
                            silentConnections[silentId]!!.map { silentConnectionSubset + it - silentId }
                        }.toSet()
                }

            set
        }
        .toSet()
}

internal fun PetriNet.toDirectlyFollowsGraph(): DirectlyFollowsGraph = TODO()

internal fun PetriNet.toBPMN(): BPMN = TODO()
