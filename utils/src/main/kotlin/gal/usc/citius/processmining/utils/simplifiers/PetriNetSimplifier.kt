package gal.usc.citius.processmining.utils.simplifiers

import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder
import gal.usc.citius.processmining.model.process.petrinet.PlaceToTransitionArc
import gal.usc.citius.processmining.model.process.petrinet.TransitionToPlaceArc

/**
 * Reduce the Petri net removing the useless places and silent activities. There are three different types of structures
 * that can be removed:
 *      - Self-loop: a place or silent activity with the same input and output connections. Its execution does not alter
 *      the marking nor the execution register.
 *      - Parallel: multiple places or multiple silent activities with exactly the same input and output connections.
 *      These elements are redundant and the same structure can be modeled with only one.
 *      - Serial reduction: 'place->silent' or 'silent->place' where the first node has only that output arc and the
 *      second node has only that input arc. These structure can be removed connecting the inputs of the structure with
 *      its outputs.
 *
 * @return the reduced version of the current Petri net removing unnecessary places and silent transitions.
 */
fun PetriNet.reduce(): PetriNet {
    // Initialize builder
    var builder = PetriNetBuilder.forProcess(this.id)
    builder.places.addAll(this.places)
    builder.transitions.addAll(this.transitions)
    builder.arcs.addAll(this.arcs)

    // Reduce iteratively until the petri net does not suffer any reduction
    do {
        val previousBuilder = builder
        builder = selfLoopReduction(builder)
        builder = parallelReduction(builder)
        builder = serialReduction(builder)
    } while (previousBuilder != builder)

    return builder.build()
}

/**
 * Remove the places or silent activities with the same input and output connections. The execution of those nodes does
 * not alter the marking nor the execution register storing the executed visible transitions.
 *
 * @param builder a Petri net builder with the elements of the Petri net to reduce.
 *
 * @return a [PetriNetBuilder] with the self-loop reductions applied on the received builder.
 */
internal fun selfLoopReduction(builder: PetriNetBuilder): PetriNetBuilder {
    val reducedBuilder = PetriNetBuilder.forProcess(builder.name)
    reducedBuilder.places.addAll(builder.places)
    reducedBuilder.transitions.addAll(builder.transitions)
    reducedBuilder.arcs.addAll(builder.arcs)
    // Delete self loop silent transitions
    reducedBuilder.transitions
        .filter { it.isSilent }
        .filter { transition ->
            (reducedBuilder.arcs.filter { it.to == transition }.map { it.from }) == (reducedBuilder.arcs.filter { it.from == transition }.map { it.to })
        }
        .forEach { transition ->
            reducedBuilder.transitions.remove(transition)
            reducedBuilder.arcs.removeIf { it.from == transition || it.to == transition }
        }
    // Delete self loop places
    reducedBuilder.places
        .filter { place ->
            (reducedBuilder.arcs.filter { it.to == place }.map { it.from }) == (reducedBuilder.arcs.filter { it.from == place }.map { it.to })
        }
        .forEach { place ->
            reducedBuilder.places.remove(place)
            reducedBuilder.arcs.removeIf { it.from == place || it.to == place }
        }

    return reducedBuilder
}

/**
 * Reduce to one the multiple places or multiple silent activities with exactly the same input and output connections.
 * These elements are redundant as they model exactly the same behaviour. The same structure can be modeled with only
 * one of them.
 *
 * @param builder a Petri net builder with the elements of the Petri net to reduce.
 *
 * @return a [PetriNetBuilder] with the parallel reductions applied on the received builder.
 */
internal fun parallelReduction(builder: PetriNetBuilder): PetriNetBuilder {
    val reducedBuilder = PetriNetBuilder.forProcess(builder.name)
    reducedBuilder.places.addAll(builder.places)
    reducedBuilder.transitions.addAll(builder.transitions)
    reducedBuilder.arcs.addAll(builder.arcs)
    // Delete redundant silent transitions
    reducedBuilder.transitions
        .filter { it.isSilent }
        .groupBy { transition ->
            (reducedBuilder.arcs.filter { it.to == transition }.map { it.from }) to (reducedBuilder.arcs.filter { it.from == transition }.map { it.to })
        }
        .values
        .filter { it.size > 1 }
        .flatMap { it.take(it.size - 1) }
        .forEach { transitionToDelete ->
            reducedBuilder.transitions.remove(transitionToDelete)
            reducedBuilder.arcs.removeIf { it.from == transitionToDelete || it.to == transitionToDelete }
        }
    // Delete redundant places
    reducedBuilder.places
        .groupBy { place ->
            (reducedBuilder.arcs.filter { it.to == place }.map { it.from }) to (reducedBuilder.arcs.filter { it.from == place }.map { it.to })
        }
        .values
        .filter { it.size > 1 }
        .flatMap { it.take(it.size - 1) }
        .forEach { placeToDelete ->
            reducedBuilder.places.remove(placeToDelete)
            reducedBuilder.arcs.removeIf { it.from == placeToDelete || it.to == placeToDelete }
        }

    return reducedBuilder
}

/**
 * Remove the structures 'place->silent' or 'silent->place' where the first node has only the output arc to the second
 * node, and the second node has only the input arc from the first node. The execution of these structures only
 * propagate the tokens from its inputs to activate its outputs, they can be removed connecting its inputs with its
 * outputs.
 *
 * @param builder a Petri net builder with the elements of the Petri net to reduce.
 *
 * @return a [PetriNetBuilder] with the serial reductions applied on the received builder.
 */
internal fun serialReduction(builder: PetriNetBuilder): PetriNetBuilder {
    val reducedBuilder = PetriNetBuilder.forProcess(builder.name)
    reducedBuilder.places.addAll(builder.places)
    reducedBuilder.transitions.addAll(builder.transitions)
    reducedBuilder.arcs.addAll(builder.arcs)
    // Reduce place->transition useless structures
    reducedBuilder.arcs
        .filterIsInstance<PlaceToTransitionArc>()
        .filter { arc -> reducedBuilder.arcs.count { it.to == arc.to } == 1 }
        .filter { arc -> reducedBuilder.arcs.count { it.from == arc.from } == 1 }
        .filter { !it.from.isInitial }
        .filter { it.to.isSilent }
        .forEach { (place, transition) ->
            reducedBuilder.places.remove(place)
            reducedBuilder.transitions.remove(transition)
            // Add arcs binding each place input with each transition output
            reducedBuilder.arcs.addAll(
                reducedBuilder.arcs
                    .filterIsInstance<TransitionToPlaceArc>()
                    .filter { it.from == transition }
                    .flatMap { (_, retainingPlace) ->
                        reducedBuilder.arcs
                            .filterIsInstance<TransitionToPlaceArc>()
                            .filter { it.to == place }
                            .map { (retainingTransition, _) ->
                                TransitionToPlaceArc(
                                    from = retainingTransition,
                                    to = retainingPlace
                                )
                            }
                    }
            )

            // Remove arcs connected with place->transition
            reducedBuilder.arcs.removeIf { it.from == place && it.to == transition }
            reducedBuilder.arcs.removeIf { it.from == transition }
            reducedBuilder.arcs.removeIf { it.to == place }
        }

    // Reduce transition->place useless structures
    reducedBuilder.arcs
        .asSequence()
        .filterIsInstance<TransitionToPlaceArc>()
        .filter { arc -> reducedBuilder.arcs.count { it.to == arc.to } == 1 }
        .filter { arc -> reducedBuilder.arcs.count { it.from == arc.from } == 1 }
        .filter { it.from.isSilent }
        .filter { !it.to.isFinal }
        .toList()
        .forEach { (transition, place) ->
            reducedBuilder.places.remove(place)
            reducedBuilder.transitions.remove(transition)
            // Add arcs binding each place input with each transition output
            reducedBuilder.arcs.addAll(
                reducedBuilder.arcs
                    .filterIsInstance<PlaceToTransitionArc>()
                    .filter { it.from == place }
                    .flatMap { (_, retainingTransition) ->
                        reducedBuilder.arcs
                            .filterIsInstance<PlaceToTransitionArc>()
                            .filter { it.to == transition }
                            .map { (retainingPlace, _) ->
                                PlaceToTransitionArc(
                                    from = retainingPlace,
                                    to = retainingTransition
                                )
                            }
                    }
            )

            // Remove arcs connected with place->transition
            reducedBuilder.arcs.removeIf { it.from == transition && it.to == place }
            reducedBuilder.arcs.removeIf { it.from == place }
            reducedBuilder.arcs.removeIf { it.to == transition }
        }

    return reducedBuilder
}
