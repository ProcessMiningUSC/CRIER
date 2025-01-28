package gal.usc.citius.processmining.model.process.dfg

import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.WeightedArc

class DirectlyFollowsGraphBuilder(val name: String = "DefaultDFGName") {

    private val activities: MutableSet<Activity> = mutableSetOf()
    private val arcs: MutableSet<DirectedArcBuilder> = mutableSetOf()

    fun activity(id: String): DirectlyFollowsGraphBuilder {
        this.activities.add(Activity.from(id))
        return this
    }

    fun arc(): DirectedArcBuilder {
        val builder = DirectedArcBuilder(this)
        this.arcs.add(builder)
        return builder
    }

    fun build(): DirectlyFollowsGraph = DirectlyFollowsGraph(this.name, this.activities, this.arcs.map { it.build() }.toSet())
}

class DirectedArcBuilder(val gb: DirectlyFollowsGraphBuilder) {
    private lateinit var from: Activity
    private lateinit var to: Activity
    private var weight: Double = 1.0

    fun weight(weight: Double): DirectedArcBuilder {
        this.weight = weight
        return this
    }

    fun from(node: String): DirectedArcBuilder {
        this.from = Activity.from(node)
        return this
    }

    fun to(node: String): DirectlyFollowsGraphBuilder {
        this.to = Activity.from(node)
        return this.gb
    }

    fun build(): WeightedArc = WeightedArc(from, to, weight)
}
