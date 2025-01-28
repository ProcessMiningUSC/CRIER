package gal.usc.citius.processmining.model.process.petrinet

import java.util.UUID

class TransitionBuilder(private val pn: PetriNetBuilder) {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = ""
    private var isSilent: Boolean = false

    fun id(id: String): TransitionBuilder {
        this.id = id
        return this
    }

    fun name(name: String): TransitionBuilder {
        this.name = name
        return this
    }

    fun silent(silent: Boolean = true): TransitionBuilder {
        this.isSilent = silent
        return this
    }

    fun add(): PetriNetBuilder = this.pn.addTransition(
        Transition(
            id = this.id,
            name = if (this.name.isEmpty()) this.id else this.name,
            isSilent = this.isSilent
        )
    )
}

class ArcBuilder(private val pn: PetriNetBuilder) {
    fun fromTransition(name: String): TransitionToPlaceArcBuilder = TransitionToPlaceArcBuilder(this.pn).from(name)

    fun fromPlace(name: String): PlaceToTransitionArcBuilder = PlaceToTransitionArcBuilder(this.pn).from(name)
}

class TransitionToPlaceArcBuilder(private val pn: PetriNetBuilder) {
    private lateinit var from: Transition
    private lateinit var to: Set<Place>

    fun from(name: String): TransitionToPlaceArcBuilder {
        this.from = pn.transitions.find { it.id == name } ?: throw TransitionNotFoundError(name)
        return this
    }

    fun to(name: String): TransitionToPlaceArcBuilder {
        val place = pn.places.find { it.id == name } ?: throw PlaceNotFoundError(
            name
        )
        this.to = setOf(place)
        return this
    }

    fun to(vararg to: String): TransitionToPlaceArcBuilder {
        val places = mutableSetOf<Place>()

        for (place in to) {
            val placeObj = pn.places.find { it.id == place } ?: throw PlaceNotFoundError(
                place
            )
            places.add(placeObj)
        }

        this.to = places
        return this
    }

    fun add(): PetriNetBuilder {
        for (place in to)
            this.pn.addArc(TransitionToPlaceArc(from, place))

        return this.pn
    }
}

class PlaceToTransitionArcBuilder(private val pn: PetriNetBuilder) {
    private lateinit var from: Place
    private lateinit var to: Set<Transition>

    fun from(name: String): PlaceToTransitionArcBuilder {
        this.from = pn.places.find { it.id == name } ?: throw PlaceNotFoundError(
            name
        )
        return this
    }

    fun to(name: String): PlaceToTransitionArcBuilder {
        val transition = pn.transitions.find { it.id == name } ?: throw TransitionNotFoundError(
            name
        )
        this.to = setOf(transition)
        return this
    }

    fun to(vararg to: String): PlaceToTransitionArcBuilder {
        val transitions = mutableSetOf<Transition>()

        for (transition in to) {
            val transitionObj = pn.transitions.find { it.id == transition } ?: throw TransitionNotFoundError(
                transition
            )
            transitions.add(transitionObj)
        }

        this.to = transitions
        return this
    }

    fun add(): PetriNetBuilder {
        for (transition in to)
            this.pn.addArc(PlaceToTransitionArc(from, transition))

        return this.pn
    }
}

class PlaceBuilder(private val pn: PetriNetBuilder) {
    private var id: String = UUID.randomUUID().toString()
    private var name: String = ""
    private var initial: Boolean = false
    private var final: Boolean = false

    fun id(id: String): PlaceBuilder {
        this.id = id
        return this
    }

    fun name(name: String): PlaceBuilder {
        this.name = name
        return this
    }

    fun initial(initial: Boolean = true): PlaceBuilder {
        this.initial = initial
        return this
    }

    fun final(final: Boolean = true): PlaceBuilder {
        this.final = final
        return this
    }

    fun add(): PetriNetBuilder = this.pn.addPlace(
        Place(
            id = this.id,
            name = if (this.name.isEmpty()) this.id else this.name,
            isInitial = this.initial,
            isFinal = this.final
        )
    )
}

class PetriNetBuilder private constructor(val name: String) {
    private val initialNodes: Set<Place> get() = this.places.filter { it.isInitial }.toSet()
    private val finalNodes: Set<Place> get() = this.places.filter { it.isFinal }.toSet()
    val transitions = mutableSetOf<Transition>()
    val places = mutableSetOf<Place>()
    val arcs = mutableSetOf<Arc>()

    companion object {
        @JvmStatic
        fun forProcess(name: String): PetriNetBuilder =
            PetriNetBuilder(name)
    }

    fun transition(): TransitionBuilder =
        TransitionBuilder(this)

    fun arc(): ArcBuilder =
        ArcBuilder(this)

    fun place(): PlaceBuilder =
        PlaceBuilder(this)

    internal fun addTransition(transition: Transition): PetriNetBuilder {
        this.transitions.add(transition)
        return this
    }

    internal fun addArc(arc: Arc): PetriNetBuilder {
        this.arcs.add(arc)
        return this
    }

    internal fun addPlace(place: Place): PetriNetBuilder {
        this.places.add(place)
        return this
    }

    fun build(): PetriNet =
        PetriNet(
            id = this.name,
            activities = this.transitions.filterNot { it.isSilent }.toSet(),
            transitions = this.transitions.toSet(),
            places = this.places.toSet(),
            arcs = this.arcs.toSet()
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PetriNetBuilder) return false

        if (name != other.name) return false
        if (transitions != other.transitions) return false
        if (places != other.places) return false
        if (arcs != other.arcs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + transitions.hashCode()
        result = 31 * result + places.hashCode()
        result = 31 * result + arcs.hashCode()
        return result
    }
}
