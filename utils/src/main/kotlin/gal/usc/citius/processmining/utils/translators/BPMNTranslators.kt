package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.processtree.ProcessTree

internal fun BPMN.toPetriNet(): PetriNet = TODO()

internal fun BPMN.toProcessTree(): ProcessTree = TODO()

internal fun BPMN.toCausalNet(): CausalNet = TODO()

internal fun BPMN.toDirectlyFollowsGraph(): DirectlyFollowsGraph = TODO()

internal fun BPMN.toCausalMatrix(): CausalMatrix = TODO()
