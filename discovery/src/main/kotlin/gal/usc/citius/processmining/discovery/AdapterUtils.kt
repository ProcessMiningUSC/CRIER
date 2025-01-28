package gal.usc.citius.processmining.discovery

import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.PetriNetBuilder
import org.processmining.contexts.cli.CLIContext
import org.processmining.contexts.cli.CLIPluginContext
import org.processmining.framework.plugin.GlobalContext
import org.processmining.models.graphbased.directed.petrinet.Petrinet
import org.processmining.models.graphbased.directed.petrinet.elements.Place
import org.processmining.models.graphbased.directed.petrinet.elements.Transition
import org.processmining.models.heuristics.HeuristicsNet
import org.processmining.models.semantics.petrinet.Marking
import org.processmining.plugins.heuristicsnet.miner.heuristics.converter.HeuristicsNetToPetriNetConverter
import java.io.OutputStream
import java.io.PrintStream

internal fun Petrinet.toPetriNet(initialMarking: Marking, finalMarking: Marking, log: Log<*>): PetriNet {
    val activityNameToId = log.activities.map { it.name to it.id }.toMap()

    val builder = PetriNetBuilder.forProcess(this.label)

    val initialPlaces = initialMarking.map { it.id }
    val finalPlaces = finalMarking.map { it.id }

    this.places.forEach {
        builder
            .place()
            .id(it.id.toString())
            .name(it.label)
            .initial(initialPlaces.contains(it.id))
            .final(finalPlaces.contains(it.id))
            .add()
    }
    this.transitions.forEach {
        builder
            .transition()
            .id(activityNameToId[it.label] ?: it.id.toString())
            .name(it.label)
            .silent(it.isInvisible)
            .add()
    }
    this.edges.map { it.source to it.target }.forEach { (from, to) ->
        when (from) {
            is Place -> builder.arc().fromPlace(from.id.toString()).to(
                activityNameToId[to.label] ?: to.id.toString()
            ).add()
            is Transition -> builder.arc().fromTransition(
                activityNameToId[from.label] ?: from.id.toString()
            ).to(to.id.toString()).add()
            else -> println("Error! unrecognized object ${from::class.java}")
        }
    }

    return builder.build()
}

internal fun HeuristicsNet.toPetriNet(log: Log<*>): PetriNet {
    val (petrinet, initialMarking) = HeuristicsNetToPetriNetConverter.converter(
        CLIPluginContext(CLIPluginContext(CLIContext(), "") as GlobalContext, ""),
        this
    )

    return (petrinet as Petrinet).toPetriNet(initialMarking as Marking, Marking(), log)
}

fun <T : Any> runSilently(fn: () -> T): T {
    val output = System.out
    val error = System.err

    System.setErr(NullPrintStream)
    System.setOut(NullPrintStream)
    val result = fn()
    System.setErr(error)
    System.setOut(output)

    return result
}
private object NullPrintStream : PrintStream(NullOutputStream)
private object NullOutputStream : OutputStream() {
    override fun write(b: Int) {}
}
