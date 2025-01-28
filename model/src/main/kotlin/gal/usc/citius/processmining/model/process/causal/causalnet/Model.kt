package gal.usc.citius.processmining.model.process.causal.causalnet

import gal.usc.citius.processmining.model.process.causal.CausalActivity
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.CausalModel

/**
 * Causal Net represents the relations as an OR of ANDs. This means each subset in the set of connections is a
 * possible path to execute. For instance, an activity A with the following outputs {{B, C}, {D}, {E}} can fire the
 * execution of activities "B and C", "D" or "E".
 * This would be equivalent to A -> (B & C) | D | E
 */
open class CausalNet(override val id: String, override val activities: Set<CausalNetActivity>) :
    CausalModel<CausalNetActivity>(id, activities) {

    override fun hashCode(): Int = (id.hashCode() * 31 + activities.hashCode()) * 31

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is CausalNet &&
                    this.id == other.id &&
                    this.activities == other.activities
                )

    fun copy(id: String = this.id, activities: Set<CausalNetActivity> = this.activities) =
        CausalNet(id, activities)
}

/**
 * [CausalActivity] storing the connections in a Causal Net format.
 */
data class CausalNetActivity(
    override val id: String,
    override val inputs: CausalConnections,
    override val outputs: CausalConnections,
    override val name: String = id
) : CausalActivity(id, inputs, outputs, name)
