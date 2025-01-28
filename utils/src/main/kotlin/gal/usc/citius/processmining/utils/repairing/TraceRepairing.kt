package gal.usc.citius.processmining.utils.repairing

import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.utils.repairing.NegativeDurationRepairType.SET_AVG
import gal.usc.citius.processmining.utils.repairing.NegativeDurationRepairType.SET_MAX
import gal.usc.citius.processmining.utils.repairing.NegativeDurationRepairType.SET_MIN
import gal.usc.citius.processmining.utils.repairing.NegativeDurationRepairType.SWITCH
import java.time.Instant

/**
 * Types of repair for negative duration elements.
 *  - [SWITCH] switches the start with the end so it becomes positive.
 *  - [SET_MIN] set the minimum of the two values as both start and end.
 *  - [SET_MAX] set the maximum of the two values as both start and end.
 *  - [SET_AVG] set the average of the two values as both start and end.
 */
enum class NegativeDurationRepairType {
    SWITCH,
    SET_MIN,
    SET_MAX,
    SET_AVG
}

/**
 * Repair the events of a trace when their start instant is after the end instant. The repair strategy type is specified
 * by [repair].
 *
 * @receiver [ProcessTrace]
 *
 * @param repair strategy to follow in the repair.
 *
 * @return the process trace with the events having a negative duration repaired.
 */
fun ProcessTrace.repairEventsWithNegativeDurations(repair: NegativeDurationRepairType = NegativeDurationRepairType.SWITCH): ProcessTrace =
    this.copy(
        events = this.events.map { event ->
            if (event.duration.isNegative) {
                val (newStart, newEnd) = getNewInstants(event, repair)
                event.copy(
                    start = newStart,
                    end = newEnd
                )
            } else {
                event
            }
        }
    )

/**
 * Return a pair with the start and end [Instant]s of [event] repaired. It assumes [event] start is after [event] end.
 *
 * @param event the event with the negative duration.
 * @param repair strategy to follow in the repair.
 *
 * @return a pair with the start and end [Instant]s of [event] repaired.
 */
private fun getNewInstants(event: Event, repair: NegativeDurationRepairType): Pair<Instant, Instant> =
    when (repair) {
        NegativeDurationRepairType.SWITCH ->
            event.end to event.start
        NegativeDurationRepairType.SET_MIN ->
            event.end to event.end
        NegativeDurationRepairType.SET_MAX ->
            event.start to event.start
        NegativeDurationRepairType.SET_AVG ->
            event.end.plusNanos(event.duration.abs().nano / 2.toLong()) to event.end.plusNanos(event.duration.abs().nano / 2.toLong())
    }
