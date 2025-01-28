package gal.usc.citius.processmining.datasources.log

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.exceptions.UnsupportedFileTypeException
import gal.usc.citius.processmining.datasources.log.common.Attributes
import gal.usc.citius.processmining.datasources.log.common.Mappings
import gal.usc.citius.processmining.datasources.log.common.Producers
import gal.usc.citius.processmining.datasources.log.common.toProcessLog
import gal.usc.citius.processmining.model.log.ProcessLog
import org.deckfour.xes.`in`.XMxmlGZIPParser
import org.deckfour.xes.`in`.XMxmlParser
import org.deckfour.xes.`in`.XParser
import org.deckfour.xes.model.XElement
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class MXMLParser @JvmOverloads constructor(
    override val file: File,
    private val mapping: Mappings<String> = DEFAULT_MXML_MAPPING,
    private val producers: Producers<XElement> = Producers()
) : FileSource<ProcessLog> {
    constructor(
        filePath: Path,
        mapping: Mappings<String> = DEFAULT_MXML_MAPPING,
        producers: Producers<XElement> = Producers()
    ) : this(filePath.toFile(), mapping, producers)
    constructor(
        filePath: String,
        mapping: Mappings<String> = DEFAULT_MXML_MAPPING,
        producers: Producers<XElement> = Producers()
    ) : this(Paths.get(filePath), mapping, producers)

    override val SUPPORTED_TYPES: Collection<String> = setOf("mxml", "mxml.gz")

    override fun read(): ProcessLog {
        val path = file.absolutePath.split('.')
        val fileType = path.last().uppercase()
        val fileType2 = path.dropLast(1).last().uppercase()

        val parser: XParser = when {
            fileType == "GZ" && fileType2 == "MXML" -> XMxmlGZIPParser()
            fileType == "MXML" -> XMxmlParser()
            else -> throw UnsupportedFileTypeException(fileType)
        }

        return parser.parse(file).first().toProcessLog(mapping, producers, "file://${file.absolutePath}")
    }
}

val DEFAULT_MXML_MAPPING = Mappings(
    process = "concept:name",
    case = "concept:name",
    activity = "concept:name",
    start = "time:timestamp",
    end = "time:timestamp",
    lifecycle = "lifecycle:transition",
    attributes = Attributes()
)
