@file:JvmName("ProcessModelAnalytics")

package gal.usc.citius.processmining.utils.analytics

import es.usc.citius.hipster.algorithm.Hipster
import es.usc.citius.hipster.model.problem.ProblemBuilder
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.log.ProcessLog
import gal.usc.citius.processmining.model.log.ProcessTrace
import gal.usc.citius.processmining.model.log.Trace
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.petrinet.Place
import gal.usc.citius.processmining.model.process.petrinet.Transition
import gal.usc.citius.processmining.utils.translators.toPetriNet
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Perform a replay of a [ProcessTrace] in the [ProcessModel] returning a boolean indicating if the replay was perfect
 * or there was any problem in it.
 *
 * @receiver [ProcessModel]
 *
 * @param trace the trace to replay in the model.
 *
 * @return a boolean indicating if [trace] can be perfectly executed in [ProcessModel] or not.
 */
fun ProcessModel.booleanReplay(trace: Trace<*>): Boolean = this.toPetriNet().booleanReplay(trace)

/**
 * Perform a replay of a [ProcessLog] in the [ProcessModel] returning the fraction of perfect fitting traces.
 *
 * @receiver [ProcessModel]
 *
 * @param log the log to replay in the model.
 *
 * @return the relation between the number of perfect fitting traces and all the traces.
 */
fun ProcessModel.replay(log: Log<*>): Float = this.replay(log.traces)

fun ProcessModel.replay(traces: List<Trace<*>>): Float {
    val processModel = this

    val fittingTraces = runBlocking {
        traces.map { trace ->
            async {
                processModel.booleanReplay(trace)
            }
        }.map {
            it.await()
        }
    }.count { it }

    return fittingTraces / traces.size.toFloat()
}

private fun PetriNet.booleanReplay(trace: Trace<*>): Boolean = ReplayablePetriNet.from(this).booleanReplay(trace)
private class ReplayablePetriNet private constructor(val petrinet: PetriNet) {
    private data class ReplayState(val placesWithTokens: Set<Place> = emptySet(), val trace: List<String> = emptyList())

    companion object {
        @JvmStatic
        fun from(petrinet: PetriNet) = ReplayablePetriNet(petrinet)
    }

    private fun findActivableTransitions(state: ReplayState): Set<Transition> = this.petrinet.arcs
        .asSequence()
        .filter { it.to is Transition }
        .groupBy({ it.to as Transition }, { it.from as Place })
        .filter { (_, places) -> places.toSet().minus(state.placesWithTokens).isEmpty() }
        .map { (transition, _) -> transition }
        .toSet()

    private fun executeTransition(transition: Transition, state: ReplayState): ReplayState {
        val consumedTokens = this.petrinet.arcs
            .filter { it.to is Transition }
            .filter { it.to.id == transition.id }
            .map { it.from as Place }
            .toSet()

        val producedTokens = this.petrinet.arcs
            .filter { it.from is Transition }
            .filter { it.from.id == transition.id }
            .map { (it.to as Place) }
            .toSet()

        return ReplayState(
            trace = if (transition.isSilent) state.trace else listOf(*state.trace.toTypedArray(), transition.id),
            placesWithTokens = state.placesWithTokens.minus(consumedTokens).plus(producedTokens)
        )
    }

    fun booleanReplay(trace: Trace<*>): Boolean {
        val initialState = ReplayState(placesWithTokens = this.petrinet.places.filter { it.isInitial }.toSet())
        val traceActivities = trace.events.map { it.activity.id }

        // If there are activities in the trace that are not present in the petri net, fitting = false
        if (!this.petrinet.transitions.map { it.id }.containsAll(traceActivities)) return false

        val problem = ProblemBuilder.create()
            .initialState(initialState)
            .defineProblemWithExplicitActions()
            .useActionFunction {
                when {
                    it.trace.size > traceActivities.size -> emptySet()
                    traceActivities.subList(0, it.trace.size) != it.trace -> emptySet()
                    else -> this.findActivableTransitions(it)
                }
            }
            .useTransitionFunction { transition, currentState -> this.executeTransition(transition, currentState) }
            .useCostFunction { 0.0 }
            .useHeuristicFunction {
                if (it.trace.size > traceActivities.size || traceActivities.subList(0, it.trace.size) != it.trace)
                    Double.NaN
                else
                    (traceActivities.size - it.trace.size).toDouble()
            }
            .build()

        val search = Hipster.createAStar(problem)

        val result = search.search(
            ReplayState(
                trace = trace.events.map { it.activity.id },
                placesWithTokens = this.petrinet.places.filter { it.isFinal }.toSet()
            )
        )

        return result.goalNode.score == 0.0
    }
}
