@file:JvmName("ProcessTranslator")

package gal.usc.citius.processmining.utils.translators

import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.bpmn.BPMN
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import gal.usc.citius.processmining.model.process.petrinet.PetriNet
import gal.usc.citius.processmining.model.process.processtree.ProcessTree
import gal.usc.citius.processmining.utils.errors.TranslationNotAvailableError

fun ProcessModel.toPetriNet(): PetriNet {
    return when (this) {
        is PetriNet -> this
        is ProcessTree -> this.toPetriNet()
        is CausalNet -> this.toPetriNet()
        is CausalMatrix -> this.toPetriNet()
        is DirectlyFollowsGraph -> this.toPetriNet()
        is BPMN -> this.toPetriNet()
        else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.petrinet.PetriNet")
    }
}

fun ProcessModel.toProcessTree(): ProcessTree {
    return when (this) {
        is PetriNet -> this.toProcessTree()
        is ProcessTree -> this
        is CausalNet -> this.toProcessTree()
        is CausalMatrix -> this.toProcessTree()
        is DirectlyFollowsGraph -> this.toProcessTree()
        is BPMN -> this.toProcessTree()
        else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.processtree.ProcessTree")
    }
}

fun ProcessModel.toCausalNet(): CausalNet {
    return when (this) {
        is PetriNet -> this.toCausalNet()
        is ProcessTree -> this.toCausalNet()
        is CausalNet -> this
        is CausalMatrix -> this.toCausalNet()
        is DirectlyFollowsGraph -> this.toCausalNet()
        is BPMN -> this.toCausalNet()
        else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.causalnet.CausalNet")
    }
}

fun ProcessModel.toCausalMatrix(): CausalMatrix {
    return when (this) {
        is PetriNet -> this.toCausalMatrix()
        is ProcessTree -> this.toCausalMatrix()
        is CausalNet -> this.toCausalMatrix()
        is CausalMatrix -> this
        is DirectlyFollowsGraph -> this.toCausalMatrix()
        is BPMN -> this.toCausalMatrix()
        else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.causalnet.CausalNet")
    }
}

fun ProcessModel.toDirectlyFollowsGraph(): DirectlyFollowsGraph = when (this) {
    is PetriNet -> this.toDirectlyFollowsGraph()
    is ProcessTree -> this.toDirectlyFollowsGraph()
    is CausalNet -> this.toDirectlyFollowsGraph()
    is CausalMatrix -> this.toDirectlyFollowsGraph()
    is DirectlyFollowsGraph -> this
    is BPMN -> this.toDirectlyFollowsGraph()
    else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph")
}

fun ProcessModel.toBPMN(): BPMN = when (this) {
    is PetriNet -> this.toBPMN()
    is ProcessTree -> this.toBPMN()
    is CausalNet -> this.toBPMN()
    is CausalMatrix -> this.toBPMN()
    is DirectlyFollowsGraph -> this.toBPMN()
    is BPMN -> this
    else -> throw TranslationNotAvailableError("Can not translate from ${this::class.java.name} to gal.usc.citius.processmining.model.process.bpmn.BPMN")
}
