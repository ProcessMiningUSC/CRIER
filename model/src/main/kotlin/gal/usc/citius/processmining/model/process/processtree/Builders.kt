package gal.usc.citius.processmining.model.process.processtree

class ProcessTreeBuilder private constructor(val process: String) {
    companion object {
        @JvmStatic fun activity(name: String): ActivityBuilder = ActivityBuilder(name)
        @JvmStatic fun parallel(): ParallelBuilder = ParallelBuilder()
        @JvmStatic fun loop(): LoopBuilder = LoopBuilder()
        @JvmStatic fun choice(): ChoiceBuilder = ChoiceBuilder()
        @JvmStatic fun sequence(): SequenceBuilder = SequenceBuilder()
    }
}

class ActivityBuilder internal constructor(private val id: String) {
    fun build(): Task = Task(id)
}

class LoopBuilder internal constructor() {
    private lateinit var goingForwardSuccessor: ProcessTree
    private var goingBackwardSuccessors: List<ProcessTree> = emptyList()
    fun goingForward(generator: () -> ProcessTree): LoopBuilder {
        this.goingForwardSuccessor = generator.invoke()
        return this
    }
    fun goingBackward(generator: () -> ProcessTree): LoopBuilder {
        this.goingBackwardSuccessors = this.goingBackwardSuccessors.plus(generator.invoke())
        return this
    }
    fun build(): Loop = Loop(this.goingForwardSuccessor, this.goingBackwardSuccessors)
}

class ParallelBuilder internal constructor() {
    private val successors: MutableList<ProcessTree> = mutableListOf()
    fun successor(generator: () -> ProcessTree): ParallelBuilder {
        this.successors.add(generator.invoke())
        return this
    }
    fun build(): Parallel = Parallel(this.successors)
}

class ChoiceBuilder internal constructor() {
    private val successors: MutableList<ProcessTree> = mutableListOf()
    fun successor(generator: () -> ProcessTree): ChoiceBuilder {
        this.successors.add(generator.invoke())
        return this
    }
    fun build(): Choice = Choice(this.successors)
}

class SequenceBuilder internal constructor() {
    private val successors: MutableList<ProcessTree> = mutableListOf()
    fun successor(generator: () -> ProcessTree): SequenceBuilder {
        this.successors.add(generator.invoke())
        return this
    }
    fun build(): Sequence = Sequence(this.successors)
}
