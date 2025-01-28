@file:JvmName("LogFilterer")

package gal.usc.citius.processmining.utils.filter

import gal.usc.citius.processmining.model.log.CollapsedLog
import gal.usc.citius.processmining.model.log.CollapsedProcessLog
import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Lifecycle
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.utils.analytics.booleanReplay
import gal.usc.citius.processmining.utils.filter.FilterTimeConstraint.ENDS_IN
import gal.usc.citius.processmining.utils.filter.FilterTimeConstraint.ENTIRELY_EXECUTED_IN
import gal.usc.citius.processmining.utils.filter.FilterTimeConstraint.STARTS_IN
import gal.usc.citius.processmining.utils.simplifiers.collapseByActivitySequence
import gal.usc.citius.processmining.utils.translators.toPetriNet
import java.time.Duration
import java.time.Instant
import java.util.Collections
import kotlin.math.floor

/**
 * Filter the traces of a log depending on the value obtained by applying a given function on each trace.
 *
 * @receiver [Log]
 *
 * @param fn function receiving a [Trace] and returning a [Boolean] indicating if the trace must be retained or not.
 *
 * @return a [Log] with the filtered traces.
 */
fun <T : Trace<*>> Log<T>.filterTracesBy(fn: (T) -> Boolean): Log<T> = this.copy(
    traces = this.traces.filter(fn)
)

/**
 * Types of time filtering specifying if the filtering takes into account the start time ([STARTS_IN]), end time
 * ([ENDS_IN]) or both ([ENTIRELY_EXECUTED_IN]) times of the element to filter.
 */
enum class FilterTimeConstraint {
    STARTS_IN,
    ENDS_IN,
    ENTIRELY_EXECUTED_IN
}

/**
 * Filter the traces of a log retaining those whose start time, end time or both are between the specified interval.
 *
 * @receiver [Log]
 *
 * @param min start instant of the interval (excluded).
 * @param max end instant of the interval (excluded).
 * @param type type of filtering from [FilterTimeConstraint] to define whether to retain those traces with the start time, the end
 * time or both of them between the [min] and [max].
 *
 * @return a [Log] with those traces which start time, end time or both are between the defined interval.
 */
fun <T : Trace<*>> Log<T>.filterTracesBetween(
    min: Instant,
    max: Instant,
    type: FilterTimeConstraint = ENTIRELY_EXECUTED_IN
): Log<T> =
    this.filterTracesBy { trace ->
        when (type) {
            STARTS_IN ->
                trace.start.isAfter(min) && trace.start.isBefore(max)
            ENDS_IN ->
                trace.end.isAfter(min) && trace.end.isBefore(max)
            ENTIRELY_EXECUTED_IN ->
                trace.start.isAfter(min) && trace.end.isBefore(max)
        }
    }

/**
 * Filter a [Log] retaining those traces with a duration between [min] and [max], if they are defined.
 *
 * @receiver [Log]
 *
 * @param min if defined it establishes the minimum duration to maintain a trace.
 * @param max if defined it establishes the maximum duration to maintain a trace.
 *
 * @return a [Log] with the traces with a duration between [min] and [max].
 */
fun <T : Trace<*>> Log<T>.filterTracesByDuration(min: Duration? = null, max: Duration? = null): Log<T> =
    this.filterTracesBy { trace ->
        if (min != null && max != null)
            Duration.between(trace.start, trace.end) >= min && Duration.between(trace.start, trace.end) <= max
        else if (min != null)
            Duration.between(trace.start, trace.end) >= min
        else if (max != null)
            Duration.between(trace.start, trace.end) <= max
        else
            true
    }

/**
 * Filter a [Log] retaining those traces with a size between [min] and [max], if they are defined.
 *
 * @receiver [Log]
 *
 * @param min if defined it establishes the minimum size to maintain a trace.
 * @param max if defined it establishes the maximum size to maintain a trace.
 *
 * @return a [Log] with the traces with a size between [min] and [max].
 */
fun <T : Trace<*>> Log<T>.filterTracesBySize(min: Int? = null, max: Int? = null): Log<T> =
    this.filterTracesBy {
        if (min != null && max != null)
            it.size in (min + 1) until max
        else if (min != null)
            it.size > min
        else if (max != null)
            it.size < max
        else
            true
    }

/**
 * Filter a [Log] retaining those traces containing the activity sequence defined in [sequence].
 *
 * @receiver [Log]
 *
 * @param sequence the desired sequence of activities that must be present in the traces
 *
 * @return a [Log] with the traces containing the activity sequence [sequence].
 */
fun <T : Trace<*>> Log<T>.filterTracesContaining(sequence: List<Activity>): Log<T> = this.filterTracesBy {
    Collections.indexOfSubList(it.events.map { it.activity }, sequence) != -1
}

/**
 * Filter a [Log] retaining those traces where all, or any, of the [activities] are executed in an event.
 *
 * @receiver [Log]
 *
 * @param activities the activities to be executed in the retained traces.
 * @param all if true, retain traces with all [activities] executed at least once; if false, with any of [activities].
 *
 * @return a [Log] with the traces containing, at least, an execution of any or all [activities].
 */
fun <T : Trace<*>> Log<T>.retainTracesWithActivity(vararg activities: Activity, all: Boolean = false): Log<T> =
    this.filterTracesBy { trace ->
        when (all) {
            true -> activities.all { it in trace.events.map { it.activity } }
            false -> trace.events.any { it.activity in activities }
        }
    }

/**
 * Filter the traces of the [Log] retaining those which can be perfectly replayed in [model] (no missing nor
 * pending tokens).
 *
 * @receiver [Log]
 *
 * @param model process model to perform the replay in.
 *
 * @return a [Log] with the traces compliant to [model].
 */
fun <T : Trace<*>> Log<T>.retainCompliantTraces(model: ProcessModel): Log<T> {
    val petriNet = model.toPetriNet()
    return this.filterTracesBy {
        petriNet.booleanReplay(it)
    }
}

/**
 * Filter the traces of the [Log] retaining those which CANNOT be perfectly replayed in [model] (any missing or
 * pending token).
 *
 * @receiver [Log]
 *
 * @param model process model to perform the replay in.
 *
 * @return a [Log] with the traces NOT compliant to [model].
 */
fun <T : Trace<*>> Log<T>.removeCompliantTraces(model: ProcessModel): Log<T> {
    val petriNet = model.toPetriNet()
    return this.filterTracesBy {
        !petriNet.booleanReplay(it)
    }
}

/**
 * Take a sample of the [Log] with the N traces with highest frequency, being the frequency its number of repetitions
 * in the log (w.r.t. the activity sequence). The size of the sample will be the number of traces corresponding its
 * [percentageToRetain]% of the full log.
 *
 * @receiver [Log]
 *
 * @param percentageToRetain percentage of traces to retain.
 *
 * @return a [Log] with the [percentageToRetain]% of the traces with higher number of repetitions.
 */
fun <T : Trace<*>> Log<T>.retainMostFrequentTraces(percentageToRetain: Double): Log<T> =
    this.retainMostFrequentTraces((this.size * percentageToRetain).toInt())

/**
 * Take a sample of the [Log] with the [numberOfTraces] traces with highest frequency, being the frequency its number of
 * repetitions in the log (w.r.t. the activity sequence).
 *
 * @receiver [Log]
 *
 * @param numberOfTraces number of traces to retain.
 *
 * @return a [Log] with the [numberOfTraces] of the traces with higher number of repetitions.
 */
fun <T : Trace<*>> Log<T>.retainMostFrequentTraces(numberOfTraces: Int): Log<T> =
    this.copy(
        traces = this.collapseByActivitySequence().variants
            .flatMap { variant -> variant.individuals.map { variant.individuals.size to it } }
            .sortedByDescending { it.first }
            .take(numberOfTraces)
            .map { it.second }
    )

/**
 * Take a sample of the [Log] with the [num] variants with highest frequency, being the frequency its number of
 * repetitions in the log.
 *
 * @receiver [CollapsedProcessLog]
 *
 * @param num number of variants to retain.
 *
 * @return a [CollapsedProcessLog] with the [num] variants with higher number of repetitions.
 */
fun <T : Trace<*>, COLLAPSE_ENTITY> CollapsedLog<T, COLLAPSE_ENTITY>.retainMostFrequentVariants(num: Int): CollapsedLog<T, COLLAPSE_ENTITY> =
    this.copy(
        variants = this.variants
            .sortedByDescending { it.individuals.size }
            .take(num)
    )

/**
 * Filter the events in each trace of the [ProcessLog] depending on the value obtained by applying a given function
 * to each [Event]. Remove traces with all events being filtered.
 *
 * @receiver [ProcessLog]
 *
 * @param fn function receiving a [Event] and returning a [Boolean] indicating if the event must be retained or not.
 *
 * @return a [ProcessLog] with the traces and its filtered events.
 */
fun ProcessLog.filterEventsBy(fn: (Event) -> Boolean): ProcessLog =
    this.copy(
        traces = this.traces.mapNotNull {
            val filteredEvents = it.events.filter(fn)
            when (filteredEvents.isEmpty()) {
                true -> null
                false -> it.copy(events = filteredEvents)
            }
        }
    )

/**
 * Filter a [ProcessLog] retaining those [Event]s being its [Lifecycle] one of the lifecycles passed as argument.
 *
 * @receiver [ProcessLog]
 *
 * @param lifecycle lifecycles to identify the events to maintain.
 *
 * @return a [ProcessLog] with the events having one of the lifecycles passed as argument.
 */
fun ProcessLog.filterEventsByLifecycle(vararg lifecycle: Lifecycle): ProcessLog =
    this.filterEventsBy { lifecycle.contains(it.lifecycle) }

/**
 * Filter a [ProcessLog] removing those [Event]s being its [Lifecycle] one of the lifecycles passed as argument.
 *
 * @receiver [ProcessLog]
 *
 * @param lifecycle lifecycles to identify the events to remove.
 *
 * @return a [ProcessLog] with the events having as lifecycle one not passed as argument.
 */
fun ProcessLog.removeEventsWithLifecycle(vararg lifecycle: Lifecycle): ProcessLog =
    this.filterEventsBy { !lifecycle.contains(it.lifecycle) }

/**
 * Filter a [ProcessLog] retaining those [Event]s being its [Activity] one of the activities passed as argument.
 *
 * @receiver [ProcessLog]
 *
 * @param activity activities to identify the events to maintain.
 *
 * @return a [ProcessLog] with the events having one of the activities passed as argument.
 */
fun ProcessLog.filterEventsByActivity(vararg activity: Activity): ProcessLog =
    this.filterEventsBy { activity.contains(it.activity) }

/**
 * Filter a [ProcessLog] removing those [Event]s being its [Activity] one of the activities passed as argument.
 *
 * @receiver [ProcessLog]
 *
 * @param activity activities to identify the events to remove.
 *
 * @return a [ProcessLog] with the events having as activity one not passed as argument.
 */
fun ProcessLog.removeEventsWithActivity(vararg activity: Activity): ProcessLog =
    this.filterEventsBy { !activity.contains(it.activity) }

/**
 * Filter a [ProcessLog] removing those [Event]s being its [Activity] executed in all the log a percentage of times
 * lower than the threshold specified (w.r.t. number of total events).
 *
 * For instance, a log with 100.000 events, if activity A is only executed in 200 events its frequency is 0.2%. All its
 * events will be removed when the threshold is greater than 0.2.
 *
 * @receiver [ProcessLog]
 *
 * @param threshold threshold to establish the percentage (0.0, 1.0) of executions to remove an event.
 *
 * @return a [ProcessLog] with the events which activities are executed a percentage of times over the threshold.
 */
fun ProcessLog.removeEventsByActivityFrequency(threshold: Double): ProcessLog {
    require(threshold > 0.0 && threshold < 1.0) {
        "Wrong threshold $threshold: value must be in interval (0.0, 1.0)"
    }

    return removeEventsByActivityNumExecutions(
        floor(this.events.size * threshold).toInt()
    )
}

/**
 * Filter a [ProcessLog] removing those [Event]s being its [Activity] executed a number of times lower than
 * [numExecutions] in all the log.
 *
 * @receiver [ProcessLog]
 *
 * @param numExecutions numExecutions to establish the maximum number of executions to remove an event.
 *
 * @return a [ProcessLog] with the events which activities are executed less times than the numExecutions.
 */
fun ProcessLog.removeEventsByActivityNumExecutions(numExecutions: Int): ProcessLog =
    this.removeEventsWithActivity(
        *this.events
            .groupBy { it.activity }
            .mapNotNull { (activity, events) ->
                when {
                    events.size < numExecutions -> activity
                    else -> null
                }
            }
            .toTypedArray()
    )
