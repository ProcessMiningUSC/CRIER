package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.processtree.ProcessTree

internal fun ProcessTree.toPetriNet(): PetriNet = TODO()

internal fun ProcessTree.toCausalNet(): CausalNet = TODO()

internal fun ProcessTree.toCausalMatrix(): CausalMatrix = TODO()

internal fun ProcessTree.toDirectlyFollowsGraph(): DirectlyFollowsGraph = TODO()

internal fun ProcessTree.toBPMN(): BPMN = TODO()
