package gal.usc.citius.processmining.datasources.log.exceptions

/**
 * Exception thrown when trying to call a non-existent producer from a parser
 *
 * @property message Explanatory message to log with the exception
 */
class ProducerNotFoundException(producer: String = "", message: String = "Producer not found: $producer") : Exception(message)

/**
 * Exception thrown from a [XESParser][gal.usc.citius.processmining.datasources.parsers.XESParser] when it's not possible
 * to map the attributes defined in the [XESMapping][gal.usc.citius.processmining.datasources.parsers.XESMappings] to the
 * parsed file.
 *
 * @property message Explanatory message to log with the exception
 */
class BadMappingException(mapping: Pair<String, String> = "" to "", details: String = "", message: String = if (details.isNotBlank()) "Bad mapping: $mapping ($details)" else "Bad mapping: $mapping") : Exception(message)
