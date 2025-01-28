@file:JvmName("LogPartitioner")

package gal.usc.citius.processmining.utils.partitioning

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.utils.sampling.subsampleTraces
import kotlin.math.ceil

/**
 * Split a [Log] in a set of [Log]s using as split points the trace indices given by [indices].
 *
 * @receiver [Log]
 *
 * @param indices indices of the traces where to split the [Log].
 *
 * @return a set with the sublogs result of the split process.
 */
fun <T : Trace<*>> Log<T>.partitionInSubLogs(vararg indices: Int): Set<Log<T>> =
    setOf(0, *indices.toTypedArray(), this.size)
        .zipWithNext()
        .map { this.subsampleTraces(it.first, it.second) }
        .toSet()

/**
 * Split a [Log] in a set of [Log]s with size [chunkSize]. If the size of the log is not divisible by [chunkSize] the
 * last [Log] will have a size lower than [chunkSize].
 *
 * @receiver [Log]
 *
 * @param chunkSize the size for the sublogs.
 *
 * @return a set with the sublogs result of the chunk process.
 */
fun <T : Trace<*>> Log<T>.chunked(chunkSize: Int): Set<Log<T>> = this.traces
    .asSequence()
    .windowed(chunkSize, chunkSize, true)
    .mapIndexed { index, traces ->
        this.copy(
            process = "${this.process}-chunk($index)",
            traces = traces
        )
    }
    .toSet()

/**
 * Split the [Log] in [count] sublogs of equal size. If the number of partitions is not a divisor of the log size, the
 * last sublog will be smaller.
 *
 * @receiver [Log]
 *
 * @param partitions the number of partitions.
 *
 * @return a set with the sublogs result of the splitting process.
 */
operator fun <T : Trace<*>> Log<T>.div(partitions: Int): Set<Log<T>> = this.chunked(ceil(this.size / partitions.toFloat()).toInt())
