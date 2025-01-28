/**
 * A set of utilities to translate a log to different formats.
 *
 * @author Víctor José Gallego Fontenla
 * @author David Chapela de la Campa
 *
 * @since 0.1.0
 */

@file:JvmName("LogTranslator")

package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.log.BehaviouralLog
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.Arc
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNetActivity
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraphBuilder
import gal.usc.citius.processmining.utils.simplifiers.collapseByActivitySequence
import org.deckfour.xes.classification.XEventNameClassifier
import org.deckfour.xes.extension.std.XConceptExtension
import org.deckfour.xes.extension.std.XLifecycleExtension
import org.deckfour.xes.extension.std.XTimeExtension
import org.deckfour.xes.factory.XFactoryBufferedImpl
import org.deckfour.xes.model.XAttribute
import org.deckfour.xes.model.XLog
import org.deckfour.xes.model.impl.XAttributeMapImpl
import org.deckfour.xes.extension.std.XConceptExtension.KEY_NAME as CONCEPT_NAME
import org.deckfour.xes.extension.std.XLifecycleExtension.KEY_MODEL as LIFECYCLE_MODEL_KEY
import org.deckfour.xes.extension.std.XLifecycleExtension.KEY_TRANSITION as LIFECYCLE_TRANSITION
import org.deckfour.xes.extension.std.XLifecycleExtension.VALUE_MODEL_STANDARD as LIFECYCLE_MODEL_VALUE
import org.deckfour.xes.extension.std.XTimeExtension.KEY_TIMESTAMP as TIMESTAMP

/**
 * Translate a Log to an instance of XLog, the format of the standard OpenXES library.
 *
 * @receiver [Log]
 *
 * @return a [XLog] with the traces of the [Log].
 */
fun Log<*>.toXLog(): XLog {
    val factory = XFactoryBufferedImpl()

    val logAttrs = XAttributeMapImpl(
        mapOf<String, XAttribute>(
            CONCEPT_NAME to factory.createAttributeLiteral(CONCEPT_NAME, this.process, XConceptExtension.instance()),
            LIFECYCLE_MODEL_KEY to factory.createAttributeLiteral(
                LIFECYCLE_MODEL_KEY,
                LIFECYCLE_MODEL_VALUE,
                XLifecycleExtension.instance()
            ),
            "source" to factory.createAttributeLiteral("source", this.javaClass.canonicalName, null)
        )
    )

    val log = factory.createLog(logAttrs)

    log.classifiers.add(XEventNameClassifier())

    log.addAll(
        this.traces.map {
            val traceAttrs = XAttributeMapImpl(
                mapOf(
                    CONCEPT_NAME to factory.createAttributeLiteral(CONCEPT_NAME, it.id, XConceptExtension.instance()),
                    *it.attributes.mapValues { attr ->
                        factory.createAttributeLiteral(attr.key, attr.value, null)
                    }.toList().toTypedArray()
                )
            )

            factory.createTrace(traceAttrs).apply {
                addAll(
                    it.events.map { event ->
                        val eventAttrs = factory.createAttributeMap()

                        eventAttrs[CONCEPT_NAME] = factory.createAttributeLiteral(
                            CONCEPT_NAME,
                            event.activity.name,
                            XConceptExtension.instance()
                        )
                        eventAttrs[LIFECYCLE_TRANSITION] = factory.createAttributeLiteral(
                            LIFECYCLE_TRANSITION,
                            event.lifecycle.name.lowercase(),
                            XLifecycleExtension.instance()
                        )
                        eventAttrs["time:start"] = factory.createAttributeTimestamp(
                            "time:start",
                            event.start.toEpochMilli(),
                            XTimeExtension.instance()
                        )
                        eventAttrs[TIMESTAMP] = factory.createAttributeTimestamp(
                            TIMESTAMP,
                            event.end.toEpochMilli(),
                            XTimeExtension.instance()
                        )
                        event.attributes.forEach { (name, value) ->
                            eventAttrs[name] = factory.createAttributeLiteral(
                                name,
                                value,
                                null
                            )
                        }

                        factory.createEvent(eventAttrs)
                    }
                )
            }
        }
    )

    return log
}

/**
 * Extracts, from a [BehaviouralLog] with the stored relations between the activities, the Causal net modeling the
 * behaviour of the log. The input bindings of an activity A are the joint of the sources of its behavioural events,
 * because each of them store a single input binding. The output bindings are built based in the behavioural events
 * which has A as input (those with the same behavioural event as source are joint as parallel bindings).
 *
 * @return the [CausalNet] storing the behaviour captured in the [BehaviouralLog].
 */
fun BehaviouralLog.extractModel(): CausalNet =
    CausalNet(
        id = this.process,
        activities = this.activities
            .map { activity ->
                CausalNetActivity(
                    id = activity.id,
                    // The input bindings of [activity] are the sources of each event registering its execution.
                    inputs = this.traces
                        .flatMap { it.events }
                        .filter { it.activity == activity } // the events registering the execution of [activity]
                        .filter { it.sources.isNotEmpty() } // to avoid empty sources in the initial activity
                        .map { event ->
                            event.sources.map { it.activity.id }.toSet()
                        } // map to store the IDs of the inputs, not the events
                        .toSet(),
                    // The output bindings of [activity] are those activities whose events
                    // have [activity] as source, if two activities have the same event of
                    // [activity] as source they belong to the same binding set (parallel)
                    outputs = this.traces
                        .flatMap { it.events }
                        .filter { event ->
                            activity in event.sources.map { it.activity }
                        } // events having [activity] in its source events
                        .groupBy { event ->
                            event.sources.first { it.activity == activity }
                        } // group by [activity] events to build parallel bindings
                        .values
                        .map { outputSet ->
                            outputSet.map { it.activity.id }.toSet()
                        } // map to store the IDs of the inputs, not the events
                        .toSet(),
                    name = activity.name
                )
            }.toSet()
    )

/**
 * Create a behavioural log from [this] log by inferring the relations among the activities (i.e. the arcs) using the
 * relations in [dependencies].
 *
 * @param dependencies set with the arcs between the activities to create the relations in the behavioral log.
 * @param connectPendingToLimits postprocessing to connect the behavioural events without sources, or those not
 * appearing in any sources, to the start and end events, respectively (to produce a behavioural trace with one start
 * and one end).
 */
fun Log<*>.toBehaviouralLog(
    dependencies: Set<Arc>,
    connectPendingToLimits: Boolean = false
): BehaviouralLog =
    BehaviouralLog(
        process = this.process,
        traces = this.traces.map { it.toBehaviouralTrace(dependencies, connectPendingToLimits) },
        source = this.source
    )

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
fun Log<*>.toBehaviouralLog(
    parallelRelations: Map<Activity, Set<Activity>>,
    connectPendingToLimits: Boolean = false
): BehaviouralLog =
    BehaviouralLog(
        process = this.process,
        traces = this.traces.map { it.toBehaviouralTrace(parallelRelations, connectPendingToLimits) },
        source = this.source
    )

/**
 * Transform the current event log into a behavioral log storing as relations in each behavioral event the
 * directly-follows relations between the activities.
 *
 * @return the behavioral log corresponding this log and storing as relations the directly-follows relations between the
 * events.
 */
fun Log<*>.toDirectlyFollowsBehaviouralLog(): BehaviouralLog =
    BehaviouralLog(
        process = this.process,
        traces = this.traces.map { it.toDirectlyFollowsBehaviouralTrace() },
        source = this.source
    )

/**
 * Translate a log instance to a [DirectlyFollowsGraph], based only on the directly follows relations present on the
 * traces.
 *
 * @receiver [Log]
 *
 * @return the [DirectlyFollowsGraph] that represents the directly follows relations for the traces contained in the
 * given log.
 */
fun Log<*>.toDirectlyFollowsGraph(): DirectlyFollowsGraph = this.collapseByActivitySequence()
    .variants
    .map {
        it.individuals.first().toDirectlyFollowsGraph()
    }
    .fold(DirectlyFollowsGraphBuilder()) { builder, graph ->
        graph.activities.forEach { builder.activity(it.id) }
        graph.arcs.forEach { builder.arc().from(it.source.id).to(it.target.id) }

        builder
    }
    .build()
