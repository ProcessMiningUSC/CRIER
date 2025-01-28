package gal.usc.citius.processmining.model.process

import gal.usc.citius.processmining.model.Printable
import java.io.Serializable

interface ProcessModel : Printable, Serializable {
    val id: String
    val activities: Set<Activity>
}

interface Activity : Serializable {
    val id: String
    val name: String

    companion object {
        fun from(id: String, name: String = id): Activity = object : Activity {
            override val id = id
            override val name = name

            override fun hashCode(): Int = (id.hashCode() * 31 + name.hashCode()) * 31

            override fun equals(other: Any?): Boolean =
                this === other ||
                    (
                        other != null &&
                            other is Activity &&
                            this.id == other.id &&
                            this.name == other.name
                        )

            override fun toString(): String = "$id : $name"
        }
    }
}

/**
 * Arc of a process model
 */
open class Arc(open val source: Activity, open val target: Activity) {

    override fun hashCode(): Int = (source.hashCode() * 31 + target.hashCode()) * 31

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other != null &&
                    other is Arc &&
                    this.source == other.source &&
                    this.target == other.target
                )

    override fun toString(): String = "$source -> $target"

    fun copy(source: Activity = this.source, target: Activity = this.target): Arc = Arc(source, target)
}

/**
 * Arc of a process model with a weight assigned
 */
data class WeightedArc(override val source: Activity, override val target: Activity, val weight: Double = 1.0) :
    Arc(source, target)

enum class ConnectionType {
    INPUT,
    OUTPUT
}

/**
 * Extension function over String to easily create activities
 */
fun String.toActivity(): Activity = Activity.from(this)
