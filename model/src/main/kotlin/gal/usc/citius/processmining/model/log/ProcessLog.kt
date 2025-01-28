package gal.usc.citius.processmining.model.log

/**
 * Basic log containing [Trace]s of [Event]s.
 */
typealias ProcessLog = Log<ProcessTrace>

/**
 * Basic trace containing a sequence of [Event]s.
 */
typealias ProcessTrace = Trace<Event>
