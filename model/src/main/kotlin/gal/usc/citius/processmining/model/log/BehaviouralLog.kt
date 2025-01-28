package gal.usc.citius.processmining.model.log

import gal.usc.citius.processmining.model.process.Activity
import java.time.Instant

/**
 * Process log formed by a set of [BehaviouralTrace]s.
 */
typealias BehaviouralLog = Log<BehaviouralTrace>

/**
 * Case instance of a [Log] storing its identifier and the corresponding sequence of [BehaviouralEvent]s where bot the
 * executed activity and its source events are known.
 */
typealias BehaviouralTrace = Trace<BehaviouralEvent>

/**
 * Behavioural event representing the execution of an event and its sources.
 *
 * @property sources set with the firing [BehaviouralEvent]s.
 * @property activity the [Activity] executed in this event.
 * @property start the instant in which the activity started the execution, by default the current time.
 * @property end the instant in which the activity finished the execution, by default the current time.
 * @property lifecycle the lifecycle of the execution, by default [Lifecycle.UNKNOWN].
 * @property attributes other optional attributes of the event stored as [String]s in a map with the identifier as key.
 */
data class BehaviouralEvent(
    val sources: Set<BehaviouralEvent>,
    override val activity: Activity,
    override val start: Instant = Instant.now(),
    override val end: Instant = Instant.now(),
    override val lifecycle: Lifecycle = Lifecycle.UNKNOWN,
    override val attributes: Map<String, String> = emptyMap()
) : Event(activity, start, end, lifecycle, attributes) {

    constructor(
        sources: Set<BehaviouralEvent>,
        activityID: String,
        start: Instant = Instant.now(),
        end: Instant = Instant.now(),
        lifecycle: Lifecycle = Lifecycle.UNKNOWN,
        attributes: Map<String, String> = emptyMap()
    ) : this(sources, Activity.from(activityID), start, end, lifecycle, attributes)

    constructor(
        sources: Set<BehaviouralEvent>,
        event: Event
    ) : this(sources, event.activity, event.start, event.end, event.lifecycle, event.attributes)

    override fun toString(): String = super.toString()

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is BehaviouralEvent &&
                    (
                        this.sources.map { it.copy(sources = emptySet()) }.toSet()
                            == other.sources.map { it.copy(sources = emptySet()) }.toSet()
                        ) &&
                    super.equals(other)
                )

    override fun hashCode(): Int =
        sources.map { it.copy(sources = emptySet()) }.hashCode() * 31 + super.hashCode()
}
