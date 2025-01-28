package gal.usc.citius.processmining.datasources

import gal.usc.citius.processmining.datasources.exceptions.UnsupportedFileTypeException
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.zip.GZIPInputStream

/**
 * A data source to read from.
 *
 * A data source is a place from where you can obtain data. In this case, a data source can be a file, a database, etc.
 */
interface Source<T> {
    fun read(): T
}

/**
 * A data source that takes a file has its input.
 *
 * Represents a data source where the data is read from a file that contains a serialized object in some format.
 *
 * @property file The file that you want to parse
 */
interface FileSource<T> : Source<T> {
    val file: File
    val SUPPORTED_TYPES: Collection<String>
}

/**
 * A data source that takes a [Reader] has its input.
 *
 * Represents a data source where the data is read from a reader that contains a serialized object in some format.
 *
 * @property reader The reader you want get the data to parse
 */
interface ReaderSource<T> : Source<T> {
    val reader: Reader
}

/**
 * Create a reader from a file either in GZIP format or not.
 */
fun createGZIPCompatibleReader(file: File, SUPPORTED_TYPES: Collection<String>): Reader = when {
    SUPPORTED_TYPES.none { file.name.endsWith(it, true) } ->
        throw UnsupportedFileTypeException(file.extension)
    file.extension.lowercase() == "gz" -> InputStreamReader(GZIPInputStream(file.inputStream())).buffered()
    else -> file.bufferedReader()
}

/**
 * A data source that reads events from a database.
 *
 * Represents a data source where the data is read from a database containing an object in some serialized format.
 *
 * @property host The database server hostname.
 * @property port The database server port to connect to.
 * @property database The database name.
 * @property user The user to authenticate the connection.
 * @property password The password to authenticate the connection.
 */
interface DBConnection<T> : Source<T> {
    val host: String
    val port: Int
    val database: String
    val user: String?
    val password: String?
}
