package gal.usc.citius.processmining.utils.transformers

import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Lifecycle
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.Activity

/**
 * Apply a transformation [fn] to every trace in the [Log].
 *
 * @receiver [Log]
 *
 * @param fn a function that accepts a [Trace] instance and returns the trace resulting of applying the function.
 *
 * @return a [Log] with the traces resulting of applying [fn] to the each trace in the original trace.
 */
fun <T : Trace<*>> Log<T>.transformTraces(fn: (T) -> T) = this.copy(traces = this.traces.map(fn))

/**
 * Rename the activities executed in events of the log replacing each key in [mapping] with the corresponding value.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping map with the activities to rename as key and the new activities as value.
 *
 * @return a [ProcessLog] with the renamed activities.
 */
fun ProcessLog.renameActivities(mapping: Map<Activity, Activity>): ProcessLog =
    this.transformTraces { it.renameActivities(mapping) }

/**
 * Rename the activities executed in events of the log replacing each key in [mapping] with the corresponding value.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping map with the activities id to rename as key and the new activities id as value.
 *
 * @return a [ProcessLog] with the renamed activities.
 */
@JvmName("renameActivitiesString")
fun ProcessLog.renameActivities(mapping: Map<String, String>): ProcessLog =
    this.transformTraces { it.renameActivities(mapping.map { Activity.from(it.key) to Activity.from(it.value) }.toMap()) }

/**
 * Rename the activities executed in events of the log replacing the key in each [mapping] with the corresponding value.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping pairs with the activities to rename as key and the new activities as value.
 *
 * @return a [ProcessLog] with the renamed activities.
 */
fun ProcessLog.renameActivities(vararg mapping: Pair<Activity, Activity>): ProcessLog =
    this.renameActivities(mapping.toMap())

/**
 * Rename the activities executed in events of the log replacing the key in each [mapping] with the corresponding value.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping pairs with the activities to rename as key and the new activities as value.
 *
 * @return a [ProcessLog] with the renamed activities.
 */
@JvmName("renameActivitiesString")
fun ProcessLog.renameActivities(vararg mapping: Pair<String, String>): ProcessLog =
    this.renameActivities(mapping.toMap())

/**
 * Change the [Activity] of each [Event] adding its [Lifecycle] after [separator]. Also set its lifecycle to
 * [newLifecycle] if it is specified, and leave current lifecycle if not.
 *
 * @receiver [ProcessLog]
 *
 * @param separator string used to separate the [Activity] name and id from the [Lifecycle].
 * @param newLifecycle lifecycle to set to the modified [Event]s, maintain the current if not specified.
 *
 * @return a [ProcessLog] with all event activities with the lifecycle added to its name and id.
 */
fun ProcessLog.addEventLifecycleToConceptName(
    separator: String = "+",
    newLifecycle: Lifecycle? = null
): ProcessLog =
    this.transformTraces { trace ->
        trace.transformEvents { event ->
            event.copy(
                activity = Activity.from(
                    "${event.activity.id}$separator${event.lifecycle}",
                    "${event.activity.name}$separator${event.lifecycle}"
                ),
                lifecycle = newLifecycle ?: event.lifecycle
            )
        }
    }

/**
 * Split the events in the log representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and maintaining the start and end times in every created event.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping pairs of the activity to split as key and the new activities as value.
 *
 * @return a [ProcessLog] with the split activities.
 */
fun ProcessLog.splitActivitiesToParallel(vararg mapping: Pair<Activity, List<Activity>>): ProcessLog =
    this.splitActivitiesToParallel(mapping.toMap())

/**
 * Split the events in the log representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and maintaining the start and end times in every created event.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping map with the activities to split as key and the new activities as value.
 *
 * @return a [ProcessLog] with the split activities.
 */
fun ProcessLog.splitActivitiesToParallel(mapping: Map<Activity, List<Activity>>): ProcessLog =
    this.transformTraces { it.splitActivitiesToParallel(mapping) }

/**
 * Split the events in the trace representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and establishing their start and end times sequentially w.r.t. the
 * times in the original event.
 *
 * For instance: an event starting in 4 and ending in 10 split into 2 events would result in:
 *  - An event starting in 4 and ending in 7.
 *  - An event starting in 7 and ending in 10.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping pairs of the activity to split as key and the new activities as value.
 *
 * @return a [ProcessLog] with the split activities.
 */
fun ProcessLog.splitActivitiesToSequence(vararg mapping: Pair<Activity, List<Activity>>): ProcessLog =
    this.splitActivitiesToSequence(mapping.toMap())

/**
 * Split the events in the trace representing the execution of an activity contained in [mapping].keys, creating an
 * event for each activity in its [mapping].value and establishing their start and end times sequentially w.r.t. the
 * times in the original event.
 *
 * For instance: an event starting in 4 and ending in 10 split into 2 events would result in:
 *  - An event starting in 4 and ending in 7.
 *  - An event starting in 7 and ending in 10.
 *
 * @receiver [ProcessLog]
 *
 * @param mapping map with the activities to split as key and the new activities as value.
 *
 * @return a [ProcessLog] with the split activities.
 */
fun ProcessLog.splitActivitiesToSequence(mapping: Map<Activity, List<Activity>>): ProcessLog =
    this.transformTraces { it.splitActivitiesToSequence(mapping) }
