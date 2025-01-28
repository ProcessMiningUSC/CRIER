package gal.usc.citius.processmining.datasources.exceptions

/**
 * Exception thrown when passing an unsupported file type to a parser
 *
 * @property message Explanatory message to log with the exception
 *
 * @author Víctor José Gallego Fontenla
 * @since 0.1.0
 */
class UnsupportedFileTypeException(extension: String = "", message: String = "Unsupported File Extension: $extension") : Exception(message)
