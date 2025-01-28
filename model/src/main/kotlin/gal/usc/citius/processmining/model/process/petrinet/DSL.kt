package gal.usc.citius.processmining.model.process.petrinet

import java.util.UUID

@DslMarker
annotation class DSLBuilder

fun petrinet(process: String, init: PetriNetBuilderDSL.() -> Unit): PetriNet {
    val builder = PetriNetBuilder.forProcess(process)
    val dslbuilder = PetriNetBuilderDSL(builder)
    dslbuilder.init()
    return dslbuilder.build()
}

@DSLBuilder
class PetriNetBuilderDSL(val builder: PetriNetBuilder) {
    fun place(id: String = UUID.randomUUID().toString(), init: PlaceBuilder.() -> Unit = {}) {
        val builder = PlaceBuilder(builder)
        builder.id(id)
        builder.init()
        builder.add()
    }
    fun transition(id: String, name: String = id, init: TransitionBuilder.() -> Unit = {}) {
        val builder = TransitionBuilder(builder)
        builder.id(id).name(name)
        builder.init()
        builder.add()
    }
    fun arc(arc: Pair<String, String>) {
        if (builder.places.map { it.id }.contains(arc.first)) {
            val builder = PlaceToTransitionArcBuilder(builder)
            builder.from(arc.first).to(arc.second).add()
        } else {
            val builder = TransitionToPlaceArcBuilder(builder)
            builder.from(arc.first).to(arc.second).add()
        }
    }
    fun build(): PetriNet = builder.build()
}
