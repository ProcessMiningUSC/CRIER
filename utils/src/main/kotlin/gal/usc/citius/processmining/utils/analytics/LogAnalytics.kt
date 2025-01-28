/**
 * A set of utilities to make certain analytics over logs.
 *
 * @author Víctor José Gallego Fontenla
 * @author David Chapela de la Campa
 *
 * @since 0.1.0
 */

@file:JvmName("LogAnalyzer")

package gal.usc.citius.processmining.utils.analytics

import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.utils.translators.toActivitySequence
import java.time.Duration

/**
 * Add artificial [Event]s at the beginning and end of all traces of the [ProcessLog].
 * traces.
 *
 * @receiver [ProcessLog]
 *
 * @param start If true, [Event.DUMMY_START] will be added at the beginning of each trace. Default is true.
 * @param end If true, [Event.DUMMY_END] will be added at the end of each trace. Default is true.
 *
 * @return the log with artificial initial and end events for all traces.
 */
fun ProcessLog.addArtificialEventsOnLimits(start: Boolean = true, end: Boolean = true): ProcessLog = this.copy(
    traces = this.traces.map {
        it.copy(
            events = mutableListOf<Event>().apply {
                if (start) add(Event.DUMMY_START.withTime(it.start.minusMillis(1)))
                addAll(it.events)
                if (end) add(Event.DUMMY_END.withTime(it.end.plusMillis(1)))
            }.toList()
        )
    }
)

/**
 * Ensure that all traces in the [ProcessLog] start with the same activity and end with the same activity.
 *
 * @receiver [ProcessLog]
 *
 * @return the log with an artificial activity in the start of all traces if there were more than one, and another
 * artificial activity in the end if there were more than one.
 */
fun ProcessLog.ensureSingleEntryExitPoint(): ProcessLog {
    val distinctStarts = this.distinctInitialActivitiesCount()
    val distinctEnds = this.distinctFinalActivitiesCount()

    return this.addArtificialEventsOnLimits(distinctStarts != 1, distinctEnds != 1)
}

/**
 * Check if all the traces in the [Log] starts and ends with the same activity.
 *
 * @receiver [Log]
 *
 * @param start If true, the initial activity for each trace will be checked. Default is true.
 * @param end If true, the final activity for each trace will be checked. Default is true.
 *
 * @return a boolean indicating if all traces start with the same activity and end with the same activity.
 */
fun Log<*>.checkSingleActivityOnLimits(start: Boolean = true, end: Boolean = true): Boolean {
    val distinctStarts = this.distinctInitialActivitiesCount()
    val distinctEnds = this.distinctFinalActivitiesCount()

    return when {
        start && end -> distinctStarts == 1 && distinctEnds == 1
        start -> distinctStarts == 1
        end -> distinctEnds == 1
        else -> false
    }
}

/**
 * Count the number of total events in the [Log].
 *
 * @receiver [Log]
 *
 * @return the number of total events in the log.
 */
fun Log<*>.totalEventsCount(): Int = this.traces.map { it.size }.sum()

/**
 * Count the number of distinct initial activities in the log.
 *
 * @receiver [Log]
 *
 * @return the number of distinct initial activities in the log.
 */
fun Log<*>.distinctInitialActivitiesCount(): Int = this.traces.mapNotNull { it.events.firstOrNull()?.activity }.distinct().size

/**
 * Count the number of distinct final activities in the log.
 *
 * @receiver [Log]
 *
 * @return the number of distinct final activities in the log.
 */
fun Log<*>.distinctFinalActivitiesCount(): Int = this.traces.map { it.events.lastOrNull()?.activity }.distinct().size

/**
 * Get the average length of the traces in the [Log].
 *
 * @receiver [Log]
 *
 * @return a double indicating the average length for the traces in this log.
 */
fun Log<*>.averageTraceLength(): Double = this.traces.map { it.size }.average()

/**
 * Computes simple summary statistics from the [Log]
 *
 * @receiver [Log]
 *
 * @return a [LogSummary] containing accessors for basic summary statistics over this log.
 */
fun Log<*>.summarize(): LogSummary = LogSummary(this)

/**
 * Basic log summary statistics implemented as lazy values
 *
 * @property activityExecutions a map with pairs activity -> absolute activity execution count
 * @property activityFrequency a map with pairs activity -> activity frequency in the log
 * @property averageTraceLength the average event count per trace as a double
 * @property minTraceLength the minimum event count per trace in the log
 * @property maxTraceLength the maximum event count per trace in the log
 * @property activityCount the count of different activities in the log
 * @property averageTraceDuration the average duration of the traces (with a nanosecond precision)
 * @property minTraceDuration the minimum duration of the traces
 * @property maxTraceDuration the maximum duration of the traces
 */
class LogSummary internal constructor(private val log: Log<*>) {
    val activityExecutions: Map<Activity, Int> by lazy {
        log.traces
            .flatMap { it.toActivitySequence() }
            .groupBy { it }
            .mapValues { it.value.size }
    }

    val activityFrequency: Map<Activity, Double> by lazy {
        this.activityExecutions.mapValues { it.value.toDouble() / this.eventCount }
    }

    val averageTraceLength: Double by lazy {
        log.averageTraceLength()
    }

    val minTraceLength: Int by lazy {
        log.traces.map { it.size }.minOrNull() ?: 0
    }

    val maxTraceLength: Int by lazy {
        log.traces.map { it.size }.maxOrNull() ?: 0
    }

    val eventCount: Int by lazy {
        log.totalEventsCount()
    }

    val activityCount: Int by lazy {
        log.activities.size
    }

    val averageTraceDuration: Duration by lazy {
        Duration.ofNanos(log.traces.map { it.duration.toNanos() }.average().toLong())
    }

    val minTraceDuration: Duration by lazy {
        log.traces.map { it.duration }.minOrNull() ?: Duration.ofMillis(0)
    }

    val maxTraceDuration: Duration by lazy {
        log.traces.map { it.duration }.maxOrNull() ?: Duration.ofMillis(0)
    }
}
