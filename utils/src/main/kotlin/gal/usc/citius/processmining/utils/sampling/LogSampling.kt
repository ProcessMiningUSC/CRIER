@file:JvmName("LogSampler")

package gal.usc.citius.processmining.utils.sampling

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace

/**
 * Take a random sample of the [Log] with a number of traces corresponding its [percentage]% of the full log.
 *
 * @receiver [Log]
 *
 * @param percentage percentage of traces to maintain.
 *
 * @return a [Log] with a [percentage] of traces w.r.t. the original, chosen randomly.
 */
fun <T : Trace<*>> Log<T>.subsampleRandomTraces(percentage: Float = 0.5f): Log<T> = this.copy(
    traces = this.traces.shuffled().take((this.size * percentage).toInt())
)

/**
 * Take a sample of [count] random traces from the [Log].
 *
 * @receiver [Log]
 *
 * @param count the sample size.
 *
 * @return a [Log] with a sample of [count] traces from the original log, chosen randomly.
 */
fun <T : Trace<*>> Log<T>.subsampleRandomTraces(count: Int = 1): Log<T> = this.copy(
    traces = this.traces.shuffled().take(count)
)

/**
 * Take a sample of the [Log] retaining the interval of traces between [from] (included) and [to] (excluded).
 *
 * @receiver [Log]
 *
 * @param from index to start the sample in (included)
 * @param to index to end the sample in (excluded)
 *
 * @return a [Log] with the traces between [from] (included) and [to] (excluded).
 */
fun <T : Trace<*>> Log<T>.subsampleTraces(from: Int = 0, to: Int = Int.MAX_VALUE): Log<T> = this.copy(
    process = "${this.process}[$from-${to - 1}]",
    traces = this.traces.toList().subList(from, to)
)

/**
 * Take a sample of the [Log] retaining the intervals of traces defined by the [cutPoints] pairs (from, to)
 * including the 'from' and excluding the 'to' indexes.
 *
 * @receiver [Log]
 *
 * @param cutPoints pairs in the form (from, to) defining the intervals to retain.
 *
 * @return a [Log] with the traces which indices are between any of the pairs (from, to) [cutPoints].
 */
fun <T : Trace<*>> Log<T>.subsampleTraces(vararg cutPoints: Pair<Int, Int>): Log<T> = this.copy(
    process = "${this.process}[${cutPoints.joinToString(",") { "(${it.first} - ${it.second})" }}]",
    traces = cutPoints.flatMap { this.subsampleTraces(it.first, it.second).traces }
)
