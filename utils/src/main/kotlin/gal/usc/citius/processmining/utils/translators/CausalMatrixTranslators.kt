package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNetActivity
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.processtree.ProcessTree

internal fun CausalMatrix.toPetriNet(): PetriNet = TODO()
internal fun CausalMatrix.toProcessTree(): ProcessTree = TODO()

/**
 * Transform the current Causal Matrix to a Causal net applying the transformation to each of its activities.
 *
 * @return the current Causal Matrix as a Causal net.
 */
internal fun CausalMatrix.toCausalNet(): CausalNet =
    CausalNet(
        this.id,
        this.activities.map {
            CausalNetActivity(
                it.id,
                it.inputs.toCausalNet(),
                it.outputs.toCausalNet(),
                it.name
            )
        }.toSet()
    )

/**
 * Transform the current [CausalConnections] from a Causal matrix format to a Causal net format. Perform a cartesian
 * product avoiding the subsets where there is an already chosen element to save combinations. For instance,
 *  [[1, 2, 3],[1, 2, 4]]
 * The '1' taken from the first subset is not combined with the second subset because it is already in it, producing [1]
 * as CN combination. And the '3' is not combined with the '1' nor the '2' from the second subset because they are in
 * previous analyzed subsets, producing [3, 4].
 *
 * @return the current [CausalConnections] in a Causal net format.
 */
fun CausalConnections.toCausalNet(): CausalConnections {
    var causalNetConnections: MutableSet<Set<String>> = mutableSetOf()

    if (!this.isEmpty()) {
        causalNetConnections.add(setOf())
        // Already analyzed elements.
        val previousElements: MutableSet<String> = mutableSetOf()
        // For each subset, create a copy of the current cartesian subsets
        // and add to them each of the elements in the subset
        this.forEach { subset ->
            // Create a temporary copy of the current cartesian subsets
            val tmpCausalNetConnections = causalNetConnections
            causalNetConnections = mutableSetOf()
            // For each current cartesian subset, clone it and add each element from the subset
            tmpCausalNetConnections.forEach { tmpCartesianSubset ->
                if (tmpCartesianSubset.any { it in subset }) {
                    // If there are is an element from the subset already in the set, add the set as it is
                    causalNetConnections.add(tmpCartesianSubset)
                } else {
                    // If not, add each element to a clone of the current set
                    subset
                        .filter {
                            // Keeping only those elements which are not in previous subsets
                            it !in previousElements
                        }
                        .forEach { element ->
                            val clone = tmpCartesianSubset + element
                            causalNetConnections.add(clone)
                        }
                }
            }
            previousElements.addAll(subset)
        }
    }

    return causalNetConnections
}

internal fun CausalMatrix.toDirectlyFollowsGraph(): DirectlyFollowsGraph = TODO()

internal fun CausalMatrix.toBPMN(): BPMN = TODO()
