package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrixActivity
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder
import gal.usc.citius.processmining.model.process.processtree.ProcessTree
import gal.usc.citius.processmining.utils.simplifiers.reduce

/**
 * Transform the current Causal net to a Petri net creating:
 *      - A transition, an input and an output place for each activity.
 *      - A silent transition for each in/output binding.
 *      - A place for each connection between two activities.
 *
 * For instance, for an activity A with outputs {{B}, {C, D}} the translation creates:
 *      a transition with label A,
 *      an input place connected, as source, to the transition A,
 *      an output place connected, as target, to the transition A,
 *      a silent transition, for the {B} output binding, connected as target to the output place of A,
 *      a silent transition, for the {C, D} output binding, connected as target to the output place of A,
 *      a place connected, as target, to the first silent transition being the link between A and B,
 *      a place connected, as target, to the second silent transition being the link between A and C, and
 *      a place connected, as target, to the second silent transition being the link between A and D.
 * the inputs of each activity B, C and D are created with the same algorithm.
 * For instance, if B has {{A}, {E}} as inputs it creates:
 *      a transition with label B,
 *      an input place connected, as source, to the transition B,
 *      an output place connected, as target, to the transition B,
 *      a silent transition, for the {A} input binding, connected as source to the input place of B,
 *      a silent transition, for the {E} input binding, connected as source to the input place of B,
 *      a connection from the link place between A and B (already created) to the first silent transition, and
 *      a place connected, as source, to the second silent transition being the link between E and A.
 *
 * After the transformation, the Petri net is reduced to remove the unnecessary silent activities.
 *
 * @return the Petri net corresponding the current Causal net.
 */
internal fun CausalNet.toPetriNet(): PetriNet {
    val petriNetBuilder = PetriNetBuilder.forProcess(this.id)

    // Create an input and output place for each activity
    this.activities.forEach { activity ->
        petriNetBuilder
            // Create corresponding transition
            .transition().id(activity.id.toTransition()).name(activity.name).add()
            // its associated input place
            .place().id(activity.id.toInputPlace()).initial(activity == this.startActivity).add()
            // The arc connecting them
            .arc().fromPlace(activity.id.toInputPlace()).to(activity.id.toTransition()).add()
            // Its associated output place
            .place().id(activity.id.toOutputPlace()).final(activity == this.endActivity).add()
            // The arc connecting them
            .arc().fromTransition(activity.id.toTransition()).to(activity.id.toOutputPlace()).add()
    }

    // Input bindings
    this.activities.flatMap { activity ->
        activity.inputs.map { inputsSet -> activity.id to inputsSet }
    }.forEachIndexed { index, (activity, inputsSet) ->
        // Create a silent transition for each binding
        petriNetBuilder
            .transition().id(activity.toInputSilent(index)).silent().add()
            .arc().fromTransition(activity.toInputSilent(index)).to(activity.toInputPlace()).add()

        inputsSet.forEach { inputId ->
            if (inputId.arcPlaceTo(activity) !in petriNetBuilder.places.map { it.id }) {
                petriNetBuilder.place().id(inputId.arcPlaceTo(activity)).add()
            }
            petriNetBuilder.arc().fromPlace(inputId.arcPlaceTo(activity)).to(activity.toInputSilent(index)).add()
        }
    }

    // Output bindings
    this.activities.flatMap { activity ->
        activity.outputs.map { outputsSet -> activity.id to outputsSet }
    }.forEachIndexed { index, (activity, outputsSet) ->
        // Create a silent transition for each binding
        petriNetBuilder
            .transition().id(activity.toOutputSilent(index)).silent().add()
            .arc().fromPlace(activity.toOutputPlace()).to(activity.toOutputSilent(index)).add()

        outputsSet.forEach { outputId ->
            if (activity.arcPlaceTo(outputId) !in petriNetBuilder.places.map { it.id }) {
                petriNetBuilder.place().id(activity.arcPlaceTo(outputId)).add()
            }
            petriNetBuilder.arc().fromTransition(activity.toOutputSilent(index)).to(activity.arcPlaceTo(outputId)).add()
        }
    }

    return petriNetBuilder.build().reduce()
}

private fun String.toTransition() = this
private fun String.toInputPlace() = "IN_$this"
private fun String.toOutputPlace() = "OUT_$this"
private fun String.toInputSilent(i: Int) = "tau_IN_${i}_$this"
private fun String.toOutputSilent(i: Int) = "tau_OUT_${i}_$this"
private fun String.arcPlaceTo(target: String) = "PLACE_${this}_to_$target"

internal fun CausalNet.toProcessTree(): ProcessTree = TODO()

/**
 * Transform the current Causal net to a Causal Matrix applying the transformation to each of its activities.
 *
 * @return the current Causal net as a Causal Matrix.
 */
internal fun CausalNet.toCausalMatrix(): CausalMatrix =
    CausalMatrix(
        this.id,
        this.activities.map {
            CausalMatrixActivity(
                it.id,
                it.inputs.toCausalMatrix(),
                it.outputs.toCausalMatrix(),
                it.name
            )
        }.toSet()
    )

/**
 * Transform the current [CausalConnections] from a Causal net format to a Causal matrix format.
 *
 * E.g. [[0, 1], [0, 2, 3], [4, 5, 6], [0, 7, 8]]
 *
 * 1 - Take all elements.
 *      [0, 1, 2, 3, 4, 5, 6, 7, 8]
 *
 * 2 - Combine the first element E with all the cartesian products of the other sets not having E (and removing from
 * those subsets the elements present in any set where E is).
 *      E = 0
 *      Remove subsets containing E ([0, 1], [0, 2, 3], [0, 7, 8])
 *      Remove from the rest those elements in the sets containing E ([_])
 *      Combine with cartesian products of [4, 5, 6]           ->      [[0, 4], [0, 5], [0, 6]]
 *
 * 3 - Next element
 *      E = 1
 *      Remove subsets containing E ([0, 1])
 *      Remove from the rest those elements in the subsets containing E ([0], [_], [0])
 *      As all elements of set [4, 5, 6] has been already combined only use one element.
 *      Combine with cartesian products of  [[2, 3], [4], [7, 8]]    ->      [[1, 2, 4, 7], [1, 2, 4, 8], [1, 3, 4, 7],
 *      [1, 3, 4, 8]]
 *
 * 4 - All elements have been combined      ->      End
 *      [[0, 4], [0, 5], [0, 6], [1, 2, 4, 7], [1, 2, 4, 8], [1, 3, 4, 7], [1, 3, 4, 8]]
 *
 * Special Cases:
 *  - If the removal of the elements present in the subsets where the current element (E) is returns an empty subset, a
 *  multiple dependence is present. CM is unable to model this structures without loosing behaviour.
 *  For instance, [[0, 1, 2], [1, 2], [3, 4]]. When 0 is taken as element the set [1, 2] does not contain it, but it is
 *  emptied because 1 and 2 are in the subset where 0 is.
 *  For instance, [[0, 1], [1, 2], [2, 0]]. When 0 is taken as element the set [1, 2] does not contain it, but it is
 *  emptied because 1 and 2 are in the subsets where 0 is.
 *  - If the
 *
 * @return the current [CausalConnections] in a Causal matrix format.
 */
fun CausalConnections.toCausalMatrix(): CausalConnections {
    var removedBehaviour = false // Error flag for a conversion to CM which looses behaviour
    var addedBehaviour = false // Error flag for a conversion to CM which allows more behaviour
    val causalMatrixConnections: MutableSet<Set<String>> = mutableSetOf()
    this.flatten().forEach { element ->
        // for each element which has not been combined yet
        if (element !in causalMatrixConnections.flatten()) {
            val subsetsContainingElement = this.filter { element in it }
            // combine the subsets not containing [element]
            var currentCombinations = setOf(emptySet<String>())
            (this - subsetsContainingElement)
                // also discarding, from these subsets, those elements which are with [element] in any subset
                .map { it - subsetsContainingElement.flatten() }
                .forEach { subset ->
                    if (subset.isEmpty()) {
                        // [subset] does not contain [element] but is entirely formed by elements in those subsets
                        // containing [element]:
                        //  - it is a subset of other subset in the connections (this subset is lost in the conversion)
                        //    e.g. [[0, 1], [0, 1, 2], [3, 4]] : When '2' is taken as [element] the subset [0, 1] is not
                        //    removed because it does not contain '2' but is emptied.
                        //  - there is a cyclic dependency, e.g., [[0, 1], [1, 2], [0, 2]], there is no subset but when
                        //    any element is taken ([0,2]) two of the others are removed ([0, 1], [0, 2]) and the other
                        //    is emptied ([1, 2]).
                        removedBehaviour = true
                    } else {
                        val subsetToCombine =
                            if (subset.all { it in causalMatrixConnections.flatten() }) {
                                // if the subset as been already combined only take one element
                                setOf(subset.first())
                            } else {
                                subset
                            }
                        // combine
                        currentCombinations = currentCombinations
                            .flatMap { combination ->
                                subsetToCombine.map { combination + it }.toSet()
                            }.toSet()
                    }
                }
            // add the element to each combination and keep only those combinations covering all subsets except the
            // subsets with with all elements in subsets containing [element].
            val combinations = currentCombinations
                .map { it + element }
                .filter { combination ->
                    // keep combinations where all [this].subsets
                    this.all { subset ->
                        subset.intersect(combination).size == 1 || // have only one element from subset in them
                            subset.all { it in subsetsContainingElement.flatten() } // or all its elements are part of the subsets containing [element]
                    }
                }.toSet()

            if (
                currentCombinations.size > combinations.size &&
                (currentCombinations.flatten() + element).toSet() != combinations.flatten().toSet()
            ) {
                // When the simplification reduces the number of sets is due to a common element between the subsets to
                // combine which generates wrong combinations in [currentCombinations]
                // e.g., element = '0'
                // [[1, 2], [1, 3]]     ->      currentCombinations = [[1], [1, 3], [2, 1], [2, 3]]
                // the filtering removes [1, 3] and [2, 1]
                addedBehaviour = true
            }

            causalMatrixConnections += combinations
        }
    }

    if (addedBehaviour) {
        println(
            "There is an open dependency between some CN subsets which cannot be exactly translated to CM. I'm going " +
                "to do my best allowing all modeled behaviour, but the translation will also allow more behaviour as " +
                "side effect. Sorry for that :)"
        )
    }
    if (removedBehaviour) {
        println(
            "I'm sorry to inform that CM format is not able to represent all the behaviour. There is either a cyclic " +
                "dependency between 3 or more subsets ([[0, 1], [1, 2], [0, 2]]), or a subset containing another " +
                "subset ([[0, 1], [0, 1, 2]]). I'm gonna do my best, but the translated CM format won't allow some " +
                "of the original CN combinations."
        )
    }

    return causalMatrixConnections
}

internal fun CausalNet.toDirectlyFollowsGraph(): DirectlyFollowsGraph = TODO()

internal fun CausalNet.toBPMN(): BPMN = TODO()
