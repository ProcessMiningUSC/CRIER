package gal.usc.citius.processmining.model.process.processtree

import gal.usc.citius.processmining.model.Direction
import gal.usc.citius.processmining.model.EdgeType
import gal.usc.citius.processmining.model.Printable
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import java.util.UUID

sealed class ProcessTree : ProcessModel, Printable {
    override val id: String get() = ""
    override val activities: Set<Activity> get() = this.reachableNodes.filterIsInstance<Task>().map { it.activity }.toSet()
    abstract val successors: List<ProcessTree>
    val leaf: Boolean get() = this.successors.isEmpty()

    private val reachableNodes: List<ProcessTree>
        get() {
            val visited = mutableListOf<ProcessTree>()
            val pending = mutableListOf(this)

            while (pending.isNotEmpty()) {
                val currentNode = pending.first()
                visited.add(currentNode)
                pending.addAll(currentNode.successors)
                pending.remove(currentNode)
            }

            return visited
        }

    override fun toDOT(edges: EdgeType, direction: Direction): String = """
            |digraph process {
            |   splines=${edges.value};
            |   rankdir = "${direction.name}"
            |   graph [ordering="out"];
            |   node [shape=box, width=2, style=""];
            |   ${reachableNodes.filterIsInstance<Task>().joinToString(";") { "\"${it.id}\"" }}
            |
            |   node [shape=Mcircle, fixedsize=true, width=0.5, label="LOOP", fontsize=6, style=""];
            |   ${reachableNodes.filterIsInstance<Loop>().joinToString(";") { "\"${it.id}\"" }}
            |
            |   node [shape=Mcircle, fixedsize=true, width=0.5, label="PARALLEL", fontsize=6, style=""];
            |   ${reachableNodes.filterIsInstance<Parallel>().joinToString(";") { "\"${it.id}\"" }}
            |
            |   node [shape=Mcircle, fixedsize=true, width=0.5, label="CHOICE", fontsize=6, style=""];
            |   ${reachableNodes.filterIsInstance<Choice>().joinToString(";") { "\"${it.id}\"" }}
            |
            |   node [shape=Mcircle, fixedsize=true, width=0.5, label="SEQUENCE", fontsize=6, style=""];
            |   ${reachableNodes.filterIsInstance<Sequence>().joinToString(";") { "\"${it.id}\"" }}
            |
                ${reachableNodes.flatMap { from -> from.successors.map { to -> """|   "${from.id}" -> "${to.id}"""" } }.joinToString("\n") { it }}
            |}""".trimMargin()
}

data class Choice(override val successors: List<ProcessTree>, override val id: String = UUID.randomUUID().toString()) :
    ProcessTree() {
    override fun toString(): String = "${ProcessTreeOperators.DEFAULT.CHOICE}(${successors.joinToString(", ") { it.toString() }})"
}

data class Parallel(override val successors: List<ProcessTree>, override val id: String = UUID.randomUUID().toString()) :
    ProcessTree() {
    override fun toString(): String = "${ProcessTreeOperators.DEFAULT.PARALLEL}(${successors.joinToString(", ") { it.toString() }})"
}

data class Loop(
    private val goingForward: ProcessTree,
    private val goingBackward: List<ProcessTree>,
    override val id: String = UUID.randomUUID().toString()
) : ProcessTree() {
    override val successors: List<ProcessTree> get() = listOf(goingForward) + goingBackward
    override fun toString(): String = "${ProcessTreeOperators.DEFAULT.LOOP}(${successors.joinToString(", ") { it.toString() }})"
}

data class Sequence(override val successors: List<ProcessTree>, override val id: String = UUID.randomUUID().toString()) :
    ProcessTree() {
    override fun toString(): String = "${ProcessTreeOperators.DEFAULT.SEQUENCE}(${successors.joinToString(", ") { it.toString() }})"
}

data class Task(override val id: String) : ProcessTree() {
    override val successors: List<ProcessTree> get() = emptyList()
    override fun toString(): String = this.id
    val activity: Activity = Activity.from(id)
}

@Suppress("PropertyName")
interface ProcessTreeOperators {
    companion object {
        val DEFAULT
            get() = object : ProcessTreeOperators {
                override val PARALLEL: String get() = "∧"
                override val CHOICE: String get() = "⨉"
                override val LOOP: String get() = "⟲"
                override val SEQUENCE: String get() = "→"
            }
    }

    val PARALLEL: String
    val CHOICE: String
    val LOOP: String
    val SEQUENCE: String
}
