package gal.usc.citius.processmining.model.log

/**
 * A [Log] where each trace has an assigned weight.
 */
typealias WeightedLog = Log<WeightedTrace>

/**
 * Data class storing the information of a [Trace] of [Event]s with an attribute to assign it a [weight].
 *
 * @property id identifier of the trace.
 * @property events list of events of the trace.
 * @property weight the weight of the trace.
 */
data class WeightedTrace(
    override val id: String,
    override val events: List<Event>,
    val weight: Number
) : Trace<Event>(id, events) {
    override fun toString(): String = super.toString()
}
