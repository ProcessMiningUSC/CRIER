@file:JvmName("TraceTranslator")

/**
 * A set of utilities to translate a trace to different formats.
 *
 * @author Víctor José Gallego Fontenla
 * @author David Chapela de la Campa
 *
 * @since 0.1.0
 */

package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.log.BehaviouralEvent
import gal.usc.citius.processmining.model.log.BehaviouralTrace
import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.Arc
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraphBuilder
import gal.usc.citius.processmining.utils.transformers.transform

/**
 * Translate a trace instance to a [DirectlyFollowsGraph], based only on the directly follows relations present on the
 * trace.
 *
 * @receiver [Trace]
 *
 * @return the [DirectlyFollowsGraph] that represents the directly follows relations for the given trace.
 */
fun <E : Event> Trace<E>.toDirectlyFollowsGraph(): DirectlyFollowsGraph = this.transform {
    val builder = DirectlyFollowsGraphBuilder()

    this.events.map { it.activity.id }
        .distinct().toList()
        .forEach { builder.activity(it) }
    this.events.zipWithNext()
        .map { Pair(it.first.activity.id, it.second.activity.id) }
        .distinct().toList()
        .forEach { builder.arc().from(it.first).to(it.second) }

    builder.build()
}

/**
 * Translate a [Trace] to a sequence of [Activity]s.
 *
 * @receiver [Trace]
 *
 * @return the [List] of [Activity] that represents the trace.
 */
fun <E : Event> Trace<E>.toActivitySequence(): List<Activity> =
    this.transform { trace -> trace.events.map { it.activity } }

/**
 * Translate a behavioural trace instance to a [DirectlyFollowsGraph] not representing the directly-follows
 * relations, but the relations present on every behavioural event sources.
 *
 * @receiver [BehaviouralTrace]
 *
 * @return the [DirectlyFollowsGraph] that represents the execution graph for the given behavioral trace.
 */
fun BehaviouralTrace.toExecutionGraph(): DirectlyFollowsGraph = this.transform {
    val builder = DirectlyFollowsGraphBuilder()

    this.events
        .map { it.activity.id }
        .distinct()
        .forEach { builder.activity(it) }

    this.events
        .flatMap { event -> event.sources.map { it.activity.id to event.activity.id } }
        .distinct()
        .forEach { builder.arc().from(it.first).to(it.second) }

    builder.build()
}

/**
 * Create a behavioural log from [this] log by inferring the relations among the activities (i.e. the arcs) using the
 * function [existDependency].
 *
 * @param existDependency function to check if, between two activites, there is a dependency or not.
 * @param connectPendingToLimits postprocessing to connect the behavioural events without sources, or those not
 * appearing in any sources, to the start and end events, respectively (to produce a behavioural trace with one start
 * and one end).
 */
private fun Trace<*>.toBehaviouralTrace(
    existDependency: (Arc) -> Boolean,
    connectPendingToLimits: Boolean = false
): BehaviouralTrace {
    // Build connections forward
    val parsedBEvents = mutableListOf<BehaviouralEvent>() // already created behavioural events
    val forwardBEvents = this.events.map { event ->
        val bEvent = BehaviouralEvent(
            sources = setOf(
                parsedBEvents.reversed().firstOrNull { existDependency(Arc(it.activity, event.activity)) }
            ).filterNotNull().toSet(),
            event = event
        )
        // Add the event to the already executed behavioural events
        parsedBEvents.add(bEvent)
        // Save it
        bEvent
    }

    // Fulfill connections backwards
    val pendingBEvents = mutableSetOf<BehaviouralEvent>() // Behavioural events not connected as inputs to other events
    val updatedBEvents = mutableSetOf<BehaviouralEvent>() // Each updated behavioural event to update in other inputs
    val bEvents = forwardBEvents.mapIndexed { index, bEvent ->
        // bEvents not connected as input to other bEvents, which have a dependency with current bEvent
        val connectedPendingBEvents =
            pendingBEvents.filter { existDependency(Arc(it.activity, bEvent.activity)) }.toSet()
        val sources = connectedPendingBEvents + bEvent.sources.map { source -> // The updated corresponding bEvents
            updatedBEvents.first { source.copy(sources = emptySet()) == it.copy(sources = emptySet()) }
        }
        // New instance of bEvent with possible new connections with pending bEvents
        val updatedBEvent = BehaviouralEvent(
            sources =
            if (connectPendingToLimits && index != 0 && sources.isEmpty())
            // If it is not the initial event and has no sources, add the initial event as source
                setOf(updatedBEvents.first())
            else if (connectPendingToLimits && index == forwardBEvents.size - 1)
            // If is the last event and there are pending events, add them to its sources
                sources + pendingBEvents
            else
                sources,
            event = bEvent
        )
        // Remove already connected pending bEvents
        pendingBEvents.removeAll(connectedPendingBEvents)
        // If current bEvent does not appear in any inputs store it
        if (bEvent !in forwardBEvents.flatMap { it.sources }) {
            pendingBEvents.add(updatedBEvent)
        }
        // Save new behavioural event
        updatedBEvents.add(updatedBEvent)
        updatedBEvent
    }

    return BehaviouralTrace(
        id = this.id,
        events = bEvents,
        attributes = this.attributes
    )
}

/**
 * Create a behavioural log from [this] log by inferring the relations among the activities (i.e. the arcs) using the
 * relations in [dependencies].
 *
 * @param dependencies set with the arcs between the activities to create the relations in the behavioral trace.
 * @param connectPendingToLimits postprocessing to connect the behavioural events without sources, or those not
 * appearing in any sources, to the start and end events, respectively (to produce a behavioural trace with one start
 * and one end).
 */
fun Trace<*>.toBehaviouralTrace(
    dependencies: Set<Arc>,
    connectPendingToLimits: Boolean = false
): BehaviouralTrace =
    this.toBehaviouralTrace({ it in dependencies }, connectPendingToLimits)

/**
 * Create a behavioural log from [this] log by inferring the relations among the activities (i.e. the arcs) using the
 * relations in [parallelRelations].
 *
 * @param parallelRelations map with, for each activity, the set of its concurrent activities (i.e., the activities
 * that must not have a connection to it).
 * @param connectPendingToLimits postprocessing to connect the behavioural events without sources, or those not
 * appearing in any sources, to the start and end events, respectively (to produce a behavioural trace with one start
 * and one end).
 */
fun Trace<*>.toBehaviouralTrace(
    parallelRelations: Map<Activity, Set<Activity>>,
    connectPendingToLimits: Boolean = false
): BehaviouralTrace =
    this.toBehaviouralTrace(
        { it.target !in parallelRelations.getOrElse(it.source) { emptySet() } },
        connectPendingToLimits
    )

/**
 * Transform the current trace into a behavioral trace storing as relations in each behavioral event the
 * directly-follows relations between them.
 *
 * @return the behavioral trace corresponding this trace and storing the directly-follows relations between the events.
 */
fun Trace<*>.toDirectlyFollowsBehaviouralTrace(): BehaviouralTrace {
    var previousEvent: BehaviouralEvent? = null
    val bEvents = this.events.map { event ->
        BehaviouralEvent(
            sources = if (previousEvent == null) emptySet() else setOf(previousEvent!!),
            event = event
        ).also { previousEvent = it }
    }

    return BehaviouralTrace(
        id = this.id,
        events = bEvents,
        attributes = this.attributes
    )
}
