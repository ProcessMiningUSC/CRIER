package gal.usc.citius.processmining.datasources.log

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.ReaderSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.datasources.exceptions.UnsupportedFileTypeException
import gal.usc.citius.processmining.datasources.log.common.Attributes
import gal.usc.citius.processmining.datasources.log.common.Mappings
import gal.usc.citius.processmining.datasources.log.common.Producers
import gal.usc.citius.processmining.datasources.log.common.toProcessLog
import gal.usc.citius.processmining.datasources.log.exceptions.BadMappingException
import gal.usc.citius.processmining.datasources.log.exceptions.ProducerNotFoundException
import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Lifecycle
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.model.log.Trace
import org.deckfour.xes.`in`.XParser
import org.deckfour.xes.`in`.XesXmlGZIPParser
import org.deckfour.xes.`in`.XesXmlParser
import org.deckfour.xes.model.XElement
import org.dom4j.Element
import org.dom4j.io.SAXReader
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A [ReaderSource] for the **XES log format**.
 *
 * This class allows you to parse a log in XES format reading it from a [Reader] object and obtain a Log class instance
 * with it's content
 *
 * @property reader The reader you want get the data to parse
 * @property dateFormat Optional. The format for parsing the dates in the file. Default value is
'yyyy-MM-dd'T'HH:mm:ssXXX'
 * @property mapping Optional. A [Mappings] instance with mappings from log attributes to [Log], [Trace] and [Event].
attributes.
 * @property producers Optional. A [Producers] instance with generators for the missing values in the log.
 *
 * @constructor Creates a parser for the reader [reader]. Optionally, you can specify custom [dateFormat],
[mapping] and [producers].
 *
 * @see XESFileParser
 * @see CSVParser
 * @see MXMLParser
 */
class XESParser @JvmOverloads constructor(
    override val reader: Reader,
    private val dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
    private val mapping: Mappings<String> = DEFAULT_XES_MAPPING,
    private val producers: Producers<Element> = Producers(),
    private val source: String = "unknown"
) : ReaderSource<ProcessLog> {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat)
    private val fallbackDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC)

    /**
     * Parses the log file specified in the constructor.
     *
     * @return The parsed log as an instance of [ProcessLog]
     * @throws UnsupportedFileTypeException
     */
    override fun read(): ProcessLog {
        val log = SAXReader().read(reader).rootElement
        val traces = log.elements("trace")

        return ProcessLog(
            source = source,
            process = when {
                this.mapping.process != null ->
                    log.elements("string")
                        .firstOrNull { it.attribute("key")?.value == this.mapping.process }
                        ?.attributeValue("value") ?: throw BadMappingException("process" to this.mapping.process)
                this.producers.process != null ->
                    this.producers.process.invoke(log)
                else ->
                    throw ProducerNotFoundException("process")
            },
            traces = traces.map { trace ->
                ProcessTrace(
                    id = when {
                        this.mapping.case != null && this.mapping.case in trace.elements("string").map { it.attribute("key")?.value } ->
                            trace.elements("string")
                                .first { it.attribute("key")?.value == this.mapping.case }
                                .attributeValue("value")
                        this.producers.case != null ->
                            this.producers.case.invoke(trace)
                        else ->
                            throw ProducerNotFoundException("case")
                    },
                    events = trace.elements("event").map { event ->
                        Event(
                            activityId = when {
                                this.mapping.activity != null && this.mapping.activity in event.elements("string").map { it.attribute("key")?.value } ->
                                    event.elements("string")
                                        .first { it.attribute("key")?.value == this.mapping.activity }
                                        .attributeValue("value")
                                this.producers.activity != null ->
                                    this.producers.activity.invoke(event)
                                else ->
                                    throw ProducerNotFoundException("activity")
                            },

                            start = when {
                                this.mapping.start != null && this.mapping.start in event.elements("date").map { it.attribute("key")?.value } ->
                                    try {
                                        Instant.from(
                                            dateFormatter.parse(
                                                event.elements("date")
                                                    .first { it.attribute("key")?.value == this.mapping.start }
                                                    .attributeValue("value")
                                            )
                                        )
                                    } catch (e: DateTimeException) {
                                        Instant.from(
                                            fallbackDateFormatter.parse(
                                                event.elements("date")
                                                    .first { it.attribute("key")?.value == this.mapping.start }
                                                    .attributeValue("value")
                                            )
                                        )
                                    }
                                this.producers.start != null ->
                                    this.producers.start.invoke(event)
                                else ->
                                    throw ProducerNotFoundException("startTimestamp")
                            },

                            end = when {
                                this.mapping.end != null && this.mapping.end in event.elements("date").map { it.attribute("key")?.value } ->
                                    try {
                                        Instant.from(
                                            dateFormatter.parse(
                                                event.elements("date")
                                                    .first { it.attribute("key")?.value == this.mapping.end }
                                                    .attributeValue("value")
                                            )
                                        )
                                    } catch (e: DateTimeException) {
                                        Instant.from(
                                            fallbackDateFormatter.parse(
                                                event.elements("date")
                                                    .first { it.attribute("key")?.value == this.mapping.end }
                                                    .attributeValue("value")
                                            )
                                        )
                                    }
                                this.producers.end != null ->
                                    this.producers.end.invoke(event)
                                else ->
                                    throw ProducerNotFoundException("endTimestamp")
                            },

                            lifecycle = when {
                                this.mapping.lifecycle != null && this.mapping.lifecycle in event.elements("string").map { it.attribute("key")?.value } ->
                                    Lifecycle.from(
                                        event.elements("string")
                                            .first { it.attribute("key")?.value == this.mapping.lifecycle }
                                            .attributeValue("value")
                                    )
                                this.producers.lifecycle != null ->
                                    this.producers.lifecycle.invoke(event)
                                else -> throw ProducerNotFoundException("lifecycle")
                            },

                            attributes = this.mapping.attributes.event.map { (name, identifier) ->
                                when {
                                    identifier != null && identifier in event.elements().map { it.attribute("key")?.value } ->
                                        name to event.elements()
                                            .first { it.attribute("key")?.value == identifier }
                                            .attributeValue("value")
                                    name in this.producers.attributes.event ->
                                        name to producers.attributes.event.getValue(name).invoke(event)
                                    else -> throw ProducerNotFoundException(
                                        "Optional event attribute named $name"
                                    )
                                }
                            }.toMap()
                        )
                    },
                    attributes = this.mapping.attributes.trace.map { (name, identifier) ->
                        when {
                            identifier != null && identifier in trace.elements().map { it.attribute("key")?.value } ->
                                name to trace.elements()
                                    .first { it.attribute("key")?.value == identifier }
                                    .attributeValue("value")
                            name in this.producers.attributes.trace ->
                                name to producers.attributes.trace.getValue(name).invoke(trace)
                            else -> throw ProducerNotFoundException(
                                "Optional trace attribute named $name"
                            )
                        }
                    }.toMap()
                )
            }
        )
    }
}

/**
 * A [FileSource] for the **XES log format**.
 *
 * This class allows you to parse a file containing a log in XES format and obtain a Log class instance with it's content
 *
 * @property file File to parse
 * @property dateFormat Optional. The format for parsing the dates in the file. Default value is
'yyyy-MM-dd'T'HH:mm:ssXXX'
 * @property mapping Optional. A [Mappings] instance with mappings from log attributes to [Log], [Trace] and [Event].
attributes.
 * @property producers Optional. A [Producers] instance with generators for the missing values in the log.
 *
 * @constructor Creates a parser for the given [file]. Optionally, you can specify custom [dateFormat],
[mapping] and [producers].
 *
 * @see XESParser
 * @see CSVParser
 * @see MXMLParser
 */
class XESFileParser @JvmOverloads constructor(
    override val file: File,
    private val dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
    private val mapping: Mappings<String> = DEFAULT_XES_MAPPING,
    private val producers: Producers<Element> = Producers()
) : FileSource<ProcessLog> {
    constructor(
        filePath: Path,
        dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
        mapping: Mappings<String> = DEFAULT_XES_MAPPING,
        producers: Producers<Element> = Producers()
    ) : this(filePath.toFile(), dateFormat, mapping, producers)
    constructor(
        filePath: String,
        dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
        mapping: Mappings<String> = DEFAULT_XES_MAPPING,
        producers: Producers<Element> = Producers()
    ) : this(Paths.get(filePath), dateFormat, mapping, producers)

    override val SUPPORTED_TYPES: Collection<String> = setOf("xes", "xes.gz")

    override fun read(): ProcessLog =
        XESParser(
            createGZIPCompatibleReader(file, SUPPORTED_TYPES),
            dateFormat,
            mapping,
            producers,
            source = "file://${file.absolutePath}"
        ).read()
}

/**
 * A [FileSource] for the **XES log format**.
 *
 * This class allows you to parse a log in XES format and obtain a Log class instance with it's content
 *
 * @property file The file you want to parse
 * @property mapping Optional. A [Mappings] instance with mappings from log attributes to [Log], [Trace] and [Event].
attributes.
 * @property producers Optional. A [Producers] instance with generators for the missing values in the log.
 *
 * @constructor Creates a parser for the file specified in [file]. Optionally, you can specify custom [mapping] and
 * [producers].
 *
 * @see CSVParser
 * @see MXMLParser
 * @see XESParser
 */

class OpenXESParser @JvmOverloads constructor(
    override val file: File,
    private val mapping: Mappings<String> = DEFAULT_XES_MAPPING,
    private val producers: Producers<XElement> = Producers()
) : FileSource<ProcessLog> {
    constructor(
        filePath: Path,
        mapping: Mappings<String> = DEFAULT_XES_MAPPING,
        producers: Producers<XElement> = Producers()
    ) : this(filePath.toFile(), mapping, producers)
    constructor(
        filePath: String,
        mapping: Mappings<String> = DEFAULT_XES_MAPPING,
        producers: Producers<XElement> = Producers()
    ) : this(Paths.get(filePath), mapping, producers)

    override val SUPPORTED_TYPES: Collection<String> = setOf("xes", "xes.gz")

    /**
     * Parses the log file specified in the constructor.
     *
     * @return The parsed log as an instance of [Log]
     * @throws UnsupportedFileTypeException
     */
    override fun read(): ProcessLog {
        val path = file.absolutePath.split('.')
        val fileType = path.last().uppercase()
        val fileType2 = path.dropLast(1).last().uppercase()

        val parser: XParser =
            if (fileType == "GZ" && fileType2 == "XES") XesXmlGZIPParser()
            else if (fileType == "XES") XesXmlParser()
            else throw UnsupportedFileTypeException(fileType)

        return parser.parse(file).first().toProcessLog(mapping, producers)
    }
}

val DEFAULT_XES_MAPPING = Mappings(
    process = "concept:name",
    case = "concept:name",
    activity = "concept:name",
    start = "time:timestamp",
    end = "time:timestamp",
    lifecycle = "lifecycle:transition",
    attributes = Attributes()
)
