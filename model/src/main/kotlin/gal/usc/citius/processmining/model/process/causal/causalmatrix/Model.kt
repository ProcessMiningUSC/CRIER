package gal.usc.citius.processmining.model.process.causal.causalmatrix

import gal.usc.citius.processmining.model.process.causal.CausalActivity
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.CausalModel

/**
 * Causal matrix, a.k.a. Heuristics net, represents each relation as an AND of ORs. This means each subset in the set
 * of connections is a branch where to choose a path to execute. For instance, an activity A with the following outputs
 * {{B, C}, {D}, {E}} must fire the execution of activities "D", "E" and one out of "B or C".
 * This would be equivalent to A -> (B | C) & D & E
 */
open class CausalMatrix(override val id: String, override val activities: Set<CausalMatrixActivity>) :
    CausalModel<CausalMatrixActivity>(id, activities) {

    override fun hashCode(): Int = (id.hashCode() * 31 + activities.hashCode()) * 31

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is CausalMatrix &&
                    this.id == other.id &&
                    this.activities == other.activities
                )

    fun copy(id: String = this.id, activities: Set<CausalMatrixActivity> = this.activities) =
        CausalMatrix(id, activities)
}

/**
 * [CausalActivity] storing the connections in a Causal Matrix format.
 */
data class CausalMatrixActivity(
    override val id: String,
    override val inputs: CausalConnections,
    override val outputs: CausalConnections,
    override val name: String = id
) : CausalActivity(id, inputs, outputs, name)
