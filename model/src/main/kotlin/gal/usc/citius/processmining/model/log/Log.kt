package gal.usc.citius.processmining.model.log

import gal.usc.citius.processmining.model.process.Activity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.Objects

/**
 * Process log formed by a set of [Trace]s.
 *
 * @property process identifier of the process.
 * @property traces list with the case instances of type [T] of the log.
 * @property source where the log was obtained from, e.g., a path to a file or a database URI.
 */
open class Log<T : Trace<out Event>>(
    open val process: String,
    open val traces: List<T>,
    open val source: String = ""
) {
    fun contains(element: T): Boolean = this.traces.contains(element)
    fun containsAll(elements: Collection<T>): Boolean = this.traces.containsAll(elements)
    fun isEmpty(): Boolean = this.traces.isEmpty()

    open val size get() = this.traces.size
    val events get() = this.traces.flatMap { it.events }
    val activities get() = this.traces.flatMap { it.activities }.distinct()

    operator fun contains(id: String): Boolean = traces.any { it.id == id }

    operator fun get(id: String): T {
        return if (id in this) {
            traces.first { it.id == id }
        } else {
            throw NoSuchElementException("The trace $id is not present in the log.")
        }
    }

    fun withSource(source: String): Log<T> = Log(this.process, this.traces, source)

    override fun hashCode(): Int = ((process.hashCode() * 31) + this.traces.sortedBy { it.id }.hashCode()) * 31

    override operator fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is Log<*> &&
                    this.process == other.process &&
                    this.traces.sortedBy { it.id } == other.traces.sortedBy { it.id }
                )

    override fun toString(): String = """Process: $process
        |${traces.joinToString(separator = "\n") { it.toString() }}""".trimMargin()

    fun copy(process: String = this.process, traces: List<T> = this.traces, source: String = this.source): Log<T> =
        Log(process, traces, source)
}

/**
 * Case instance of a [Log] storing its identifier and the corresponding sequence of events of type [E].
 *
 * @property id identifier of the case instance.
 * @property events sequence of events of type [E] corresponding the execution of this case instance.
 * @property attributes other optional attributes of the trace stored as [String]s in a map with the identifier as key.
 */
open class Trace<E : Event>(
    open val id: String,
    open val events: List<E>,
    open val attributes: Map<String, String> = emptyMap()
) {
    companion object {
        @JvmStatic
        fun fromString(id: String, trace: String, separator: Char = ' '): Trace<Event> {
            return Trace(id, trace.split(separator).map { Event(it) })
        }
    }

    val start: Instant get() = this.events.minByOrNull { it.start }?.start ?: Instant.ofEpochMilli(0)
    val end: Instant get() = this.events.maxByOrNull { it.end }?.end ?: Instant.ofEpochMilli(0)
    val duration: Duration get() = Duration.between(start, end)
    val activities get() = this.events.map { it.activity }.distinct()
    val size get() = events.size

    fun contains(element: E): Boolean = this.events.contains(element)
    fun containsAll(elements: Collection<E>): Boolean = elements.all { this.contains(it) }
    fun isEmpty(): Boolean = this.events.isEmpty()

    override fun hashCode(): Int = Objects.hash(id, this.events.sortedBy { it.end }, attributes)

    override operator fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is Trace<*> &&
                    this.id == other.id &&
                    this.events.sortedBy { it.end } == other.events.sortedBy { it.end } &&
                    this.attributes == other.attributes
                )

    override fun toString(): String =
        "$id(${attributes.entries.joinToString(";") { "${it.key} -> ${it.value}" }}) : ${events
            .asSequence()
            .sortedBy { it.end }
            .joinToString(prefix = "[", postfix = "]") { "${it.activity.id}:${it.lifecycle}" }}"

    fun copy(id: String = this.id, events: List<E> = this.events, attributes: Map<String, String> = this.attributes): Trace<E> = Trace(id, events, attributes)
}

/**
 * Event storing information about the execution of an [Activity].
 *
 * @property activity the [Activity] executed in this event.
 * @property start the instant in which the activity started the execution, by default the current time.
 * @property end the instant in which the activity finished the execution, by default the current time.
 * @property lifecycle the lifecycle of the execution, by default [Lifecycle.UNKNOWN].
 * @property attributes other optional attributes of the event stored as [String]s in a map with the identifier as key.
 */
open class Event(
    open val activity: Activity,
    open val start: Instant = Instant.now(),
    open val end: Instant = Instant.now(),
    open val lifecycle: Lifecycle = Lifecycle.UNKNOWN,
    open val attributes: Map<String, String> = emptyMap()
) {
    companion object {
        @JvmStatic
        val DUMMY_START: Event = Event(
            activityId = "START_PROCESS",
            lifecycle = Lifecycle.COMPLETE
        )
        @JvmStatic
        val DUMMY_END: Event = Event(
            activityId = "END_PROCESS",
            lifecycle = Lifecycle.COMPLETE
        )
        var instantFormatter: DateTimeFormatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault())
    }

    constructor(
        activityId: String,
        start: Instant = Instant.now(),
        end: Instant = Instant.now(),
        lifecycle: Lifecycle = Lifecycle.UNKNOWN,
        attributes: Map<String, String> = emptyMap()
    ) : this(Activity.from(activityId), start, end, lifecycle, attributes)

    val duration get() = Duration.between(this.start, this.end)

    fun withTime(time: Instant): Event = this.copy(start = time, end = time)

    override fun hashCode(): Int = (
        (
            (
                (
                    activity.hashCode() * 31 + start.hashCode()
                    ) * 31 +
                    end.hashCode()
                ) * 31 +
                lifecycle.hashCode()
            ) * 31 +
            attributes.hashCode()
        ) * 31

    override operator fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is Event &&
                    this.activity == other.activity &&
                    this.start == other.start &&
                    this.end == other.end &&
                    this.lifecycle == other.lifecycle &&
                    this.attributes == other.attributes
                )

    override fun toString(): String =
        "${activity.id}:$lifecycle\t[${instantFormatter.format(start)}]-[${instantFormatter.format(end)}]"

    fun copy(
        activity: Activity = this.activity,
        start: Instant = this.start,
        end: Instant = this.end,
        lifecycle: Lifecycle = this.lifecycle,
        attributes: Map<String, String> = this.attributes
    ): Event = Event(activity, start, end, lifecycle, attributes)
}

/**
 * Life cycles of an event defined by the XES standard.
 */
enum class Lifecycle {
    SCHEDULE, ASSIGN, WITHDRAW, REASSIGN, START, SUSPEND, RESUME, PI_ABORT, ATE_ABORT, COMPLETE, AUTOSKIP, MANUALSKIP, UNKNOWN;

    companion object {
        @JvmStatic
        fun from(value: String): Lifecycle {
            return when (value.uppercase(Locale.getDefault())) {
                "SCHEDULE" -> SCHEDULE
                "ASSIGN" -> ASSIGN
                "WITHDRAW" -> WITHDRAW
                "REASSIGN" -> REASSIGN
                "START" -> START
                "SUSPEND" -> SUSPEND
                "RESUME" -> RESUME
                "PI_ABORT" -> PI_ABORT
                "ATE_ABORT" -> ATE_ABORT
                "COMPLETE" -> COMPLETE
                "AUTOSKIP" -> AUTOSKIP
                "MANUALSKIP" -> MANUALSKIP
                else -> UNKNOWN
            }
        }
    }
}
