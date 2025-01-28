package gal.usc.citius.processmining.model.log

/**
 * A [CollapsedLog] of [ProcessTrace]s where the traces with a similar COLLAPSE_ENTITY are stored in one instance
 * of a [Variant].
 *
 * @param COLLAPSE_ENTITY type of the element common for all [ProcessTrace]s under a [Variant].
 */
typealias CollapsedProcessLog<COLLAPSE_ENTITY> = CollapsedLog<ProcessTrace, COLLAPSE_ENTITY>
