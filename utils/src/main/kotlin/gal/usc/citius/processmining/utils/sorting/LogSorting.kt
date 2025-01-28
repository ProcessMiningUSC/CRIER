/**
 * A set of utilities to sort the traces from a log using different criteria.
 *
 * @author Víctor José Gallego Fontenla
 * @author David Chapela de la Campa
 *
 * @since 0.1.0
 */

@file:JvmName("LogSorter")

package gal.usc.citius.processmining.utils.sorting

import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace

/**
 * Sort the traces of the [Log] by a given function ([fn]).
 *
 * @receiver [Log]
 *
 * @param reverse If true, the traces will be sorted in a reverse order. Default is false.
 *
 * @return a [Log] with the sorted traces.
 */
fun <T : Trace<*>, R : Comparable<R>> Log<T>.tracesSortedBy(fn: (T) -> R, reverse: Boolean = false): Log<T> = this.copy(
    traces = if (reverse) this.traces.sortedByDescending(fn) else this.traces.sortedBy(fn)
)

/**
 * Sort the traces of the [Log] by its start timestamp.
 *
 * @receiver [Log]
 *
 * @param reverse If false, the traces will be sorted older to newer. Else, the reverse order is applied. Default is
 * false.
 *
 * @return a [Log] with the sorted traces.
 */
fun <T : Trace<*>> Log<T>.sortedByTraceStart(reverse: Boolean = false): Log<T> =
    this.tracesSortedBy({ p -> p.start }, reverse)

/**
 * Sort the traces of the [Log] by its end timestamp
 *
 * @receiver [Log]
 *
 * @param reverse If false, the traces will be sorted older to newer. Else, the reverse order is applied. Default is
 * false.
 *
 * @return a [Log] with the sorted traces.
 */
fun <T : Trace<*>> Log<T>.sortedByTraceEnd(reverse: Boolean = false): Log<T> =
    this.tracesSortedBy({ p -> p.end }, reverse)

/**
 * Sort the traces of the [Log] by its duration
 *
 * @receiver [Log]
 *
 * @param descending If true, the traces will be sorted descending, from longer to shorter. Else, the reverse order is
 *                  applied. Default is false.
 *
 * @return a [Log] with the sorted traces.
 */
fun <T : Trace<*>> Log<T>.sortedByTraceDuration(descending: Boolean = false): Log<T> =
    this.tracesSortedBy({ p -> p.duration }, descending)

/**
 * Sort the traces of the [Log] by its number of events.
 *
 * @receiver [Log]
 *
 * @param descending If true, the traces will be sorted descending, from longer to shorter. Else, the reverse order is
 *                  applied. Default is false.
 *
 * @return a [Log] with the sorted traces.
 */
fun <T : Trace<*>> Log<T>.sortedByTraceLength(descending: Boolean = false): Log<T> =
    this.tracesSortedBy({ p -> p.size }, descending)

/**
 * Sort the events of each [Trace] in the [Log] by a given function ([fn]).
 *
 * @receiver [Log]
 *
 * @param reverse If true, the events will be sorted in a reverse order. Default is false.
 *
 * @return a [Log] with the traces with the sorted events.
 */
fun <E : Event, R : Comparable<R>> Log<Trace<E>>.eventsSortedBy(fn: (E) -> R, reverse: Boolean = false): Log<Trace<E>> =
    this.copy(
        traces = when (reverse) {
            true -> this.traces.map { trace ->
                trace.copy(
                    events = trace.events.sortedByDescending(fn)
                )
            }
            false -> this.traces.map { trace ->
                trace.copy(
                    events = trace.events.sortedBy(fn)
                )
            }
        }
    )
