package gal.usc.citius.processmining.model.log

/**
 * A [CollapsedLog] of [BehaviouralTrace]s where the traces with a similar COLLAPSE_ENTITY are stored in one instance
 * of a [Variant].
 *
 * @param COLLAPSE_ENTITY type of the element common for all [BehaviouralTrace]s under a [Variant].
 */
typealias CollapsedBehaviouralLog<COLLAPSE_ENTITY> = CollapsedLog<BehaviouralTrace, COLLAPSE_ENTITY>
