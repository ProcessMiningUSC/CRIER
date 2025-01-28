package gal.usc.citius.processmining.utils.repairing

import gal.usc.citius.processmining.model.log.ProcessLog

/**
 * Repair the events of each trace in the log when their start instant is after the end instant. The repair strategy
 * type is specified by [repair].
 *
 * @receiver [ProcessLog]
 *
 * @param repair strategy to follow in the repair.
 *
 * @return the process log with the events having a negative duration repaired.
 */
fun ProcessLog.repairEventsWithNegativeDurations(repair: NegativeDurationRepairType = NegativeDurationRepairType.SWITCH): ProcessLog =
    this.copy(
        traces = this.traces.map { it.repairEventsWithNegativeDurations(repair) }
    )
