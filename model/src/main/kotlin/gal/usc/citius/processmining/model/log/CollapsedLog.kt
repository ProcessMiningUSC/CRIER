package gal.usc.citius.processmining.model.log

/**
 * A [Log] where all traces with a common [COLLAPSE_ENTITY] are in one instance of a [Variant] in [variants].
 *
 * @param T class of the traces of the Log.
 * @param COLLAPSE_ENTITY element common for all traces in each [Variant].
 *
 * @property process identifier of the process.
 * @property variants list with the collapsed different traces of the log.
 * @property source where the log was obtained from, e.g., a path to a file or a database URI.
 */
data class CollapsedLog<T : Trace<out Event>, COLLAPSE_ENTITY>(
    override val process: String,
    val variants: List<Variant<T, COLLAPSE_ENTITY>>,
    override val source: String = ""
) : Log<T>(process, variants.flatMap { it.individuals }, source)

/**
 * Data class storing a set of traces ([individuals]) with a common [COLLAPSE_ENTITY] obtained from each [Trace].
 *
 * @param T class of the traces with a similar [COLLAPSE_ENTITY].
 * @param COLLAPSE_ENTITY element common in all traces in [individuals].
 *
 * @property id identifier of the variant.
 * @property commonEntity element of type [COLLAPSE_ENTITY] common in all traces in [individuals].
 * @property individuals set of traces with the same [commonEntity].
 */
data class Variant<T : Trace<out Event>, COLLAPSE_ENTITY>(
    val id: String,
    val commonEntity: COLLAPSE_ENTITY,
    val individuals: Set<T>
) {
    val repetitions by lazy { individuals.size }
}
