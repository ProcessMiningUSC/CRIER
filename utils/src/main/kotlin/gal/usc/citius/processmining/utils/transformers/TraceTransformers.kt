package gal.usc.citius.processmining.utils.transformers

import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.Activity
import kotlin.math.floor

/**
 * Transform a [Trace] to anything else using the given function [transformer].
 *
 * @receiver [Trace]
 *
 * @param transformer a function that accepts a Trace instance and returns anything after applying the function.
 *
 * @return the result [RESULT] of applying the transformer function to the trace instance.
 */
fun <E : Event, RESULT> Trace<E>.transform(transformer: (Trace<E>) -> RESULT): RESULT = transformer(this)

/**
 * Apply a transformation [fn] to every event in the [Trace].
 *
 * @receiver [Trace]
 *
 * @param fn a function that accepts an Event instance and returns the event resulting of applying the function.
 *
 * @return a [Trace] with the events resulting of applying [fn] to the each event in the original trace.
 */
fun <E : Event> Trace<E>.transformEvents(fn: (E) -> E): Trace<E> = this.copy(events = this.events.map(fn))

/**
 * Rename the activities executed in events of the current trace replacing each key in [mapping] with the corresponding
 * value.
 *
 * @receiver [ProcessTrace]
 *
 * @param mapping map with the activities to rename as key and the new activities as value.
 *
 * @return a [ProcessTrace] with the renamed activities.
 */
fun ProcessTrace.renameActivities(mapping: Map<Activity, Activity>): ProcessTrace = this.transformEvents { event ->
    event.copy(
        activity = mapping.getOrDefault(event.activity, event.activity)
    )
}

/**
 * Split the events in the trace representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and maintaining the start and end times in every created event.
 *
 * @receiver [ProcessTrace]
 *
 * @param mapping map with the activities to split as key and the new activities as value.
 *
 * @return a [ProcessTrace] with the split activities.
 */
fun ProcessTrace.splitActivitiesToParallel(mapping: Map<Activity, List<Activity>>): ProcessTrace =
    this.transform { trace ->
        trace.copy(
            events = trace.events.flatMap { event ->
                mapping[event.activity]?.map { activity ->
                    event.copy(
                        activity = activity
                    )
                } ?: listOf(event)
            }
        )
    }

/**
 * Split the events in the trace representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and establishing their start and end times sequentially w.r.t. the
 * times in the original event.
 *
 * For instance: an event starting in 4 and ending in 10 split into 2 events would result in:
 *  - An event starting in 4 and ending in 7.
 *  - An event starting in 7 and ending in 10.
 *
 * @receiver [ProcessTrace]
 *
 * @param mapping map with the activities to split as key and the new activities as value.
 *
 * @return a [ProcessTrace] with the split activities.
 */
fun ProcessTrace.splitActivitiesToSequence(mapping: Map<Activity, List<Activity>>): ProcessTrace =
    this.transform { trace ->
        trace.copy(
            events = trace.events.flatMap { event ->
                val numOfSplits = mapping[event.activity]?.size ?: 1
                val increment = floor(event.duration.toNanos() / numOfSplits.toDouble()).toLong()
                mapping[event.activity]?.mapIndexed { index, activity ->
                    event.copy(
                        activity = activity,
                        start = event.start.plusNanos(index * increment),
                        end = event.end.minusNanos((numOfSplits - index - 1) * increment)
                    )
                } ?: listOf(event)
            }
        )
    }
