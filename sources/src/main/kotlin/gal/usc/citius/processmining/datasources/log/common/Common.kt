package gal.usc.citius.processmining.datasources.log.common

import gal.usc.citius.processmining.datasources.log.exceptions.BadMappingException
import gal.usc.citius.processmining.datasources.log.exceptions.ProducerNotFoundException
import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Lifecycle
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.model.log.Trace
import org.deckfour.xes.model.XAttributeTimestamp
import org.deckfour.xes.model.XElement
import org.deckfour.xes.model.XLog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalQueries

/**
 * Container class used to indicate the optional attributes read from the log.
 * This class allows to specify optional attributes that must be read from the log at the trace and event levels.
 *
 * @property event A map that stores, as the key, the attribute name for the event; and as value the attribute name in
 * the log file.
 * @property trace A map that stores, as the key, the attribute name for the trace; and as value the attribute name in
 * the log file.
 */
data class Attributes<T>(
    val event: Map<String, T?> = emptyMap(),
    val trace: Map<String, T?> = emptyMap()
)

/**
 * Container class used to indicate the optional attributes generators, invoked if the attribute specified in the mapping
 * is not found in the log.
 *
 * @property event A map that stores, as the key, the attribute name for the event; and as value the attribute generator
 * @property trace A map that stores, as the key, the attribute name for the trace; and as value the attribute generator
 */
data class AttributeProducers<T>(
    val event: Map<String, ((T) -> String)> = emptyMap(),
    val trace: Map<String, ((T) -> String)> = emptyMap()
)

/**
 * A class providing generators for the missing values in the log.
 *
 * This class allows you to define generators for when a value is missing in the XES event log. The generator will be
 * used when the mapping defined in the [Mappings] instance for an attribute is null.
 *
 * @property case A function that generates a case ID if it does not exist in the log.
 * @property activity A function that generates an activity name if it does not exist in the log.
 * @property start A function that generates a initla timestamp if it does not exist in the log.
 * @property end A function that generates an end timestamp if it does not exist in the log.
 * @property lifecycle A function that generates a [lifecycle value][Lifecycle] if it does not exist in the log.
 * @property attributes Optional. A map with a function (value) for every additional attribute (XES identifier as key)
 * to be produced if it does not exist in the log.
 */
class Producers<T>(
    val process: ((T) -> String)? = null,
    val case: ((T) -> String)? = null,
    val activity: ((T) -> String)? = null,
    val start: ((T) -> Instant)? = null,
    val end: ((T) -> Instant)? = null,
    val lifecycle: ((T) -> Lifecycle)? = null,
    val attributes: AttributeProducers<T> = AttributeProducers()
)

/**
 * Mapping class to specify the attribute names to map with in the parsed log.
 *
 * This class allows to set mappings from XES element attributes to [Log], [Trace] and [Event] attributes. If an
 * attribute mapping is null the parser will try to use the generator defined in the [Producers] instance to get the
 * value for such attribute.
 *
 * @property process The attribute from the XES log to map to the process name.
 * @property case The attribute from the XES log to map to the case ID.
 * @property activity The attribute from the XES log to map to the activity name.
 * @property start The attribute from the XES log to map to the start timestamp.
 * @property end The attribute from the XES log to map to the end timestamp.
 * @property lifecycle The attribute from the XES log to map to the activity lifecycle.
 * @property attributes Optional. The name (key) and XES identifier (value) for each optional attribute.
 */
data class Mappings<T> (
    val process: T?,
    val case: T?,
    val activity: T?,
    val start: T?,
    val end: T?,
    val lifecycle: T?,
    val attributes: Attributes<T> = Attributes()
)

/**
 * Utility function to transform an [XLog] object in a [ProcessLog] instance
 */
internal fun XLog.toProcessLog(mapping: Mappings<String>, producers: Producers<XElement>, source: String = ""): ProcessLog = ProcessLog(
    source = source,
    process = when {
        mapping.process != null && mapping.process !in this.attributes ->
            throw BadMappingException("process" to mapping.process)
        mapping.process != null ->
            this.attributes[mapping.process].toString()
        producers.process != null ->
            producers.process.invoke(this)
        else ->
            throw ProducerNotFoundException("process")
    },
    traces = this.map { trace ->
        ProcessTrace(
            id = when {
                mapping.case != null && mapping.case !in trace.attributes ->
                    throw BadMappingException("case" to mapping.case)
                mapping.case != null ->
                    trace.attributes[mapping.case].toString()
                producers.case != null ->
                    producers.case.invoke(trace)
                else ->
                    throw ProducerNotFoundException("case")
            },
            events = trace.map { event ->
                Event(
                    activityId = when {
                        mapping.activity != null && mapping.activity !in event.attributes ->
                            throw BadMappingException("activity" to mapping.activity)
                        mapping.activity != null ->
                            event.attributes[mapping.activity].toString()
                        producers.activity != null ->
                            producers.activity.invoke(event)
                        else ->
                            throw ProducerNotFoundException("activity")
                    },

                    start = when {
                        mapping.start != null && mapping.start !in event.attributes ->
                            throw BadMappingException("startTimestamp" to mapping.start)
                        mapping.start != null ->
                            (event.attributes[mapping.start] as XAttributeTimestamp).value.toInstant()
                        producers.start != null ->
                            producers.start.invoke(event)
                        else ->
                            throw ProducerNotFoundException("startTimestamp")
                    },

                    end = when {
                        mapping.end != null && mapping.end !in event.attributes ->
                            throw BadMappingException("endTimestamp" to mapping.end)
                        mapping.end != null ->
                            (event.attributes[mapping.end] as XAttributeTimestamp).value.toInstant()
                        producers.end != null ->
                            producers.end.invoke(event)
                        else ->
                            throw ProducerNotFoundException("endTimestamp")
                    },

                    lifecycle = when {
                        mapping.lifecycle != null && mapping.lifecycle !in event.attributes ->
                            throw BadMappingException("lifecycle" to mapping.lifecycle)
                        mapping.lifecycle != null ->
                            Lifecycle.from(event.attributes[mapping.lifecycle].toString())
                        producers.lifecycle != null ->
                            producers.lifecycle.invoke(event)
                        else -> throw ProducerNotFoundException("lifecycle")
                    },

                    attributes = mapping.attributes.event.map { (name, identifier) ->
                        when {
                            identifier != null && identifier !in event.attributes -> throw BadMappingException(
                                "Optional event attribute named $name" to identifier
                            )
                            identifier != null -> name to event.attributes[identifier].toString()
                            name in producers.attributes.event ->
                                name to producers.attributes.event.getValue(name)
                                    .invoke(event)
                            else -> throw ProducerNotFoundException(
                                "Optional event attribute named $name"
                            )
                        }
                    }.toMap()
                )
            },
            attributes = mapping.attributes.trace.map { (name, identifier) ->
                when {
                    identifier != null && identifier !in trace.attributes -> throw BadMappingException(
                        "Optional trace attribute named $name" to identifier
                    )
                    identifier != null -> name to trace.attributes[identifier].toString()
                    name in producers.attributes.trace ->
                        name to producers.attributes.trace.getValue(name)
                            .invoke(trace)
                    else -> throw ProducerNotFoundException(
                        "Optional trace attribute named $name"
                    )
                }
            }.toMap()
        )
    }
)

/**
 *  Utility function to parse an instant from a string setting default time (00:00:00) if not present
 */
internal fun String.toInstant(formatter: DateTimeFormatter): Instant {
    val parsed = formatter.parse(this)

    return when {
        // Undefined time, set the same time for every event (00:00)
        parsed.query(TemporalQueries.localDate()) != null && parsed.query(TemporalQueries.localTime()) == null -> {
            parsed.query(TemporalQueries.localDate())
                .atStartOfDay()
                .toInstant(parsed.query(TemporalQueries.offset()) ?: ZoneOffset.UTC)
        }
        // Undefined date, set the same date for every event (today)
        parsed.query(TemporalQueries.localTime()) != null && parsed.query(TemporalQueries.localDate()) == null -> {
            val dateTime = LocalDateTime.of(LocalDate.now(), parsed.query(TemporalQueries.localTime()))
            Instant.from(dateTime)
        }
        else -> Instant.from(parsed)
    }
}
