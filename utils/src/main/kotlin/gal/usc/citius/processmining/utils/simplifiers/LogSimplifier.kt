package gal.usc.citius.processmining.utils.simplifiers

import gal.usc.citius.processmining.model.log.BehaviouralLog
import gal.usc.citius.processmining.model.log.BehaviouralTrace
import gal.usc.citius.processmining.model.log.CollapsedBehaviouralLog
import gal.usc.citius.processmining.model.log.CollapsedLog
import gal.usc.citius.processmining.model.log.CollapsedProcessLog
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.log.Variant
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.utils.translators.toActivitySequence
import gal.usc.citius.processmining.utils.translators.toDirectlyFollowsGraph
import gal.usc.citius.processmining.utils.translators.toExecutionGraph

/**
 * Transform the [ProcessLog] to a [CollapsedProcessLog] collapsing the traces with a similar activity sequence.
 *
 * @receiver [ProcessLog]
 *
 * @return a [CollapsedProcessLog] with the traces grouped by its activity sequence as variants.
 */
fun <T : Trace<*>> Log<T>.collapseByActivitySequence(): CollapsedLog<T, List<Activity>> =
    this.collapseBy { it.toActivitySequence() }

/**
 * Transform the [ProcessLog] to a [CollapsedProcessLog] collapsing the traces with the same directly follows graph
 *
 * @receiver [ProcessLog]
 *
 * @return a [CollapsedProcessLog] with the traces grouped by its directly follows graph.
 */
fun <T : Trace<*>> Log<T>.collapseByDirectlyFollowsGraph(): CollapsedLog<T, DirectlyFollowsGraph> =
    this.collapseBy { it.toDirectlyFollowsGraph() }

/**
 * Transform the [BehaviouralLog] to a [CollapsedBehaviouralLog] collapsing the traces with a similar execution graph.
 *
 * @receiver [BehaviouralLog]
 *
 * @return a [CollapsedBehaviouralLog] with the traces grouped by its execution graph as variants.
 */
fun BehaviouralLog.collapseByExecutionGraph(): CollapsedBehaviouralLog<DirectlyFollowsGraph> =
    this.collapseBy(BehaviouralTrace::toExecutionGraph)

/**
 * Transform the [ProcessLog] to a [CollapsedProcessLog] collapsing the traces with a similar [ENTITY].
 *
 * @receiver [Log]
 *
 * @param ENTITY the type of the common element in all traces considered a variant.
 * @param fn a function transforming each trace into its [ENTITY].
 *
 * @return a [CollapsedProcessLog] with the traces grouped by [ENTITY], result of applying [fn] to it.
 */
fun <T : Trace<*>, ENTITY> Log<T>.collapseBy(fn: (T) -> ENTITY): CollapsedLog<T, ENTITY> =
    CollapsedLog(
        this.process,
        this.traces
            .groupBy(fn, { it })
            .map { Variant(it.value.first().id, it.key, it.value.toSet()) },
        this.source
    )

/**
 * Uncollapse a [CollapsedLog] returning a [Log] with all the traces ungrouped.
 *
 * @receiver [CollapsedLog]
 *
 * @return a [Log] with all the traces without grouping.
 */
fun <T : Trace<*>> CollapsedLog<T, *>.uncollapse(): Log<T> =
    Log(this.process, this.traces, this.source)
