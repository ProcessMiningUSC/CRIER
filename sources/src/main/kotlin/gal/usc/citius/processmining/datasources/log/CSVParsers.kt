package gal.usc.citius.processmining.datasources.log

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.ReaderSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.datasources.log.common.Attributes
import gal.usc.citius.processmining.datasources.log.common.Mappings
import gal.usc.citius.processmining.datasources.log.common.Producers
import gal.usc.citius.processmining.datasources.log.common.toInstant
import gal.usc.citius.processmining.datasources.log.exceptions.BadMappingException
import gal.usc.citius.processmining.datasources.log.exceptions.ProducerNotFoundException
import gal.usc.citius.processmining.model.log.Event
import gal.usc.citius.processmining.model.log.Lifecycle
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.model.log.Trace
import org.apache.commons.csv.CSVFormat
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.apache.commons.csv.CSVRecord as ApacheCSVRecord

/**
 * This class allows you to parse a log CSV format, one event per line, and obtain a Log class instance with it's content.
 *
 * @property reader The reader you want get the data to parse
 * @property dateFormat Optional. The format for parsing the dates in the file. Optional values are surrounded by [].
 *                      Default value is 'yyyy-MM-dd'T'HH:mm:ss\[.SSS\]\[XXX\]'
 * @property mapping Optional. A [Mappings] instance with mappings from CSV columns to [Log], [Trace] and [Event]
attributes.
 * @property producers Optional. A [Producers] instance with generators for the missing values in the log.
 * @property options Optional. A [CSVOptions] instance with parser options, like the separator or the presence of
 *                      headers.
 *
 * @constructor Creates a parser for the reader [reader]. Optionally, you can specify custom [dateFormat],
[mapping], [producers] and [options].
 *
 * @see XESParser
 */
class CSVParser @JvmOverloads constructor(
    override val reader: Reader,
    private val dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
    private val mapping: Mappings<Int> = DEFAULT_CSV_MAPPING,
    private val producers: Producers<ApacheCSVRecord> = Producers(),
    private val options: CSVOptions = CSVOptions(),
    private val source: String = "unknown"
) : ReaderSource<ProcessLog> {

    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat)
    private val fallbackDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat).withZone(ZoneOffset.UTC)

    /**
     * Parses the log file specified in the constructor.
     *
     * @return The parsed log as an instance of [ProcessLog]
     */
    override fun read(): ProcessLog {
        val parser: CSVFormat =
            if (options.header)
                CSVFormat.DEFAULT
                    .withIgnoreSurroundingSpaces()
                    .withDelimiter(options.separator)
                    .withQuote(options.quotes)
                    .withRecordSeparator(options.recordSeparator)
                    .withFirstRecordAsHeader()
            else
                CSVFormat.DEFAULT
                    .withIgnoreSurroundingSpaces()
                    .withDelimiter(options.separator)
                    .withQuote(options.quotes)
                    .withRecordSeparator(options.recordSeparator)

        val records = parser.parse(reader).toList()

        val firstRecord = records.first()
        val process = when {
            this.mapping.process != null && firstRecord[this.mapping.process] != null -> firstRecord[this.mapping.process].toString().trim()
            this.mapping.process != null -> throw BadMappingException(
                "process" to this.mapping.process.toString()
            )
            this.producers.process != null -> this.producers.process.invoke(firstRecord)
            else -> throw ProducerNotFoundException(
                "process"
            )
        }

        return ProcessLog(
            source = source,
            process = process,
            traces = records
                .map { record ->
                    CSVRecord(
                        case = when {
                            this.mapping.case != null && (this.mapping.case < 0 || this.mapping.case >= record.size()) -> throw BadMappingException(
                                mapping = "case" to this.mapping.case.toString(),
                                details = record.toString()
                            )
                            this.mapping.case != null && record[this.mapping.case].isNotBlank() -> record[this.mapping.case].trim()
                            this.producers.case != null -> producers.case.invoke(record)
                            else -> throw ProducerNotFoundException(
                                "case"
                            )
                        },

                        activity = when {
                            this.mapping.activity != null && (this.mapping.activity < 0 || this.mapping.activity >= record.size()) -> throw BadMappingException(
                                mapping = "activity" to this.mapping.activity.toString(),
                                details = record.toString()
                            )
                            this.mapping.activity != null && record[this.mapping.activity].isNotBlank() -> record[this.mapping.activity].trim()
                            this.producers.activity != null -> producers.activity.invoke(record)
                            else -> throw ProducerNotFoundException(
                                "activity"
                            )
                        },

                        start = when {
                            this.mapping.start != null && (this.mapping.start < 0 || this.mapping.start >= record.size()) -> throw BadMappingException(
                                mapping = "start" to this.mapping.start.toString(),
                                details = record.toString()
                            )
                            this.mapping.start != null && record[this.mapping.start].isNotBlank() ->
                                try {
                                    record[this.mapping.start].trim().toInstant(dateFormatter)
                                } catch (e: Exception) {
                                    record[this.mapping.start].trim().toInstant(fallbackDateFormatter)
                                }
                            this.producers.start != null -> producers.start.invoke(record)
                            else -> throw ProducerNotFoundException(
                                "start"
                            )
                        },

                        end = when {
                            this.mapping.end != null && (this.mapping.end < 0 || this.mapping.end >= record.size()) -> throw BadMappingException(
                                mapping = "end" to this.mapping.end.toString(),
                                details = record.toString()
                            )
                            this.mapping.end != null && record[this.mapping.end].isNotBlank() ->
                                try {
                                    record[this.mapping.end].trim().toInstant(dateFormatter)
                                } catch (e: Exception) {
                                    record[this.mapping.end].trim().toInstant(fallbackDateFormatter)
                                }
                            this.producers.end != null -> producers.end.invoke(record)
                            else -> throw ProducerNotFoundException(
                                "end"
                            )
                        },

                        lifecycle = when {
                            this.mapping.lifecycle != null && (this.mapping.lifecycle < 0 || this.mapping.lifecycle >= record.size()) -> throw BadMappingException(
                                mapping = "lifecycle" to this.mapping.lifecycle.toString(),
                                details = record.toString()
                            )
                            this.mapping.lifecycle != null && record[this.mapping.lifecycle].isNotBlank() -> Lifecycle.from(record[this.mapping.lifecycle].trim())
                            this.producers.lifecycle != null -> producers.lifecycle.invoke(record)
                            else -> throw ProducerNotFoundException(
                                "lifecycle"
                            )
                        },

                        attributes = Attributes(
                            event = this.mapping.attributes.event.map { (name, value) ->
                                when {
                                    value != null && (value < 0 || value >= record.size()) -> throw BadMappingException(
                                        mapping = "Optional event attribute named $name" to value.toString(),
                                        details = record.toString()
                                    )
                                    value != null && record[value].isNotBlank() -> name to record[value]
                                    name in this.producers.attributes.event -> name to producers.attributes.event.getValue(name).invoke(record)
                                    else -> throw ProducerNotFoundException(
                                        "Optional event attribute named $name"
                                    )
                                }
                            }.toMap(),
                            trace = this.mapping.attributes.trace.map { (name, value) ->
                                when {
                                    value != null && (value < 0 || value >= record.size()) -> throw BadMappingException(
                                        mapping = "Optional trace attribute named $name" to value.toString(),
                                        details = record.toString()
                                    )
                                    value != null && record[value].isNotBlank() -> name to record[value]
                                    name in this.producers.attributes.trace -> name to producers.attributes.trace.getValue(name).invoke(record)
                                    else -> throw ProducerNotFoundException(
                                        "Optional trace attribute named $name"
                                    )
                                }
                            }.toMap()
                        )
                    )
                }
                .groupBy { it.case }
                .map { entry ->
                    ProcessTrace(
                        id = entry.key,
                        events = entry.value.map { record ->
                            Event(
                                activityId = record.activity,
                                start = record.start,
                                end = record.end,
                                lifecycle = record.lifecycle,
                                attributes = record.attributes.event.mapValues { it.value!! }
                            )
                        },
                        attributes = entry.value.first().attributes.trace.mapValues { it.value!! }
                    )
                }
        )
    }
}

/**
 * This class allows you to parse a log CSV format, one event per line, and obtain a Log class instance with it's content.
 */
class CSVFileParser @JvmOverloads constructor(
    override val file: File,
    private val dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
    private val mapping: Mappings<Int> = DEFAULT_CSV_MAPPING,
    private val producers: Producers<ApacheCSVRecord> = Producers(),
    private val options: CSVOptions = CSVOptions()
) : FileSource<ProcessLog> {
    constructor(
        filePath: Path,
        dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
        mapping: Mappings<Int> = DEFAULT_CSV_MAPPING,
        producers: Producers<ApacheCSVRecord> = Producers(),
        options: CSVOptions = CSVOptions()
    ) : this(filePath.toFile(), dateFormat, mapping, producers, options)

    constructor(
        filePath: String,
        dateFormat: String = "yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX]",
        mapping: Mappings<Int> = DEFAULT_CSV_MAPPING,
        producers: Producers<ApacheCSVRecord> = Producers(),
        options: CSVOptions = CSVOptions()
    ) : this(Paths.get(filePath), dateFormat, mapping, producers, options)

    override val SUPPORTED_TYPES: Collection<String> = listOf("csv", "csv.gz")

    override fun read(): ProcessLog =
        CSVParser(
            createGZIPCompatibleReader(file, SUPPORTED_TYPES),
            dateFormat,
            mapping,
            producers,
            options,
            source = "file://${file.absolutePath}"
        ).read()
}

/**
 * A class providing options for the CSV parser.
 *
 * This class allows you to configure the CSV parser, so it can understand different CSV formats.
 *
 * @property separator Optional. Character used as separator between columns in the CSV.
 * @property header Optional. Flag to indicate that the CSV file has or not a headers row.
 * @property quotes Optional. Character used as quotation marks for the strings in the CSV file.
 * @property recordSeparator Optional. String that separates different records (rows) in the CSV.
 * */
data class CSVOptions(
    val separator: Char = ',',
    val header: Boolean = false,
    val quotes: Char = '"',
    val recordSeparator: String = "\r\n"
)

data class CSVRecord(
    val case: String,
    val activity: String,
    val start: Instant,
    val end: Instant,
    val lifecycle: Lifecycle,
    val attributes: Attributes<String> = Attributes()
)

/**
 * Mapping class to specify the column index to map with the attributes in the parsed log.
 *
 * This class allows to set mappings from CSV columns to [Log], [Trace] and [Event] attributes. If any of the mappings
 * is null the parser will try to use the generator defined in the [Producers] instance to generate the value for the
 * attribute.
 *
 * @property case Optional. The CSV column to map to the case ID. Default is '0'.
 * @property activity Optional. The CSV column to map to the activity name. Default is '1'.
 * @property start Optional. The CSV column to map to the start timestamp. Default is '2'.
 * @property end Optional. The CSV column to map to the end timestamp. Default is '3'.
 * @property lifecycle Optional. The CSV column to map to the activity lifecycle. Default is '4'.
 * @property attributes Optional. The names and CSV columns for optional attributes. Default is an empty map.
 */
val DEFAULT_CSV_MAPPING: Mappings<Int> = Mappings(
    process = null,
    case = 0,
    activity = 1,
    start = 2,
    end = 3,
    lifecycle = 4,
    attributes = Attributes()
)
