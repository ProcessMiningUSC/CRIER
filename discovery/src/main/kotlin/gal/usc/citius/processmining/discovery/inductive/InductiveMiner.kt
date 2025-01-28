package gal.usc.citius.processmining.discovery.inductive

import gal.usc.citius.processmining.discovery.DiscoveryAlgorithm
import gal.usc.citius.processmining.discovery.runSilently
import gal.usc.citius.processmining.discovery.toPetriNet
import gal.usc.citius.processmining.model.log.Log
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.utils.translators.toXLog
import org.processmining.contexts.cli.CLIContext
import org.processmining.contexts.cli.CLIPluginContext
import org.processmining.framework.plugin.GlobalContext
import org.processmining.models.graphbased.directed.petrinet.Petrinet
import org.processmining.models.semantics.petrinet.Marking
import org.processmining.plugins.InductiveMiner.mining.MiningParametersIMf
import org.processmining.plugins.InductiveMiner.plugins.IM

class InductiveMiner(private val noiseThreshold: Double = 0.0) : DiscoveryAlgorithm {
    override fun discover(log: Log<*>): ProcessModel {
        val params = MiningParametersIMf()
        params.noiseThreshold = noiseThreshold.toFloat()

        val (petrinet, initialMarking, finalMarking) = runSilently {
            IM.minePetriNet(
                CLIPluginContext(CLIPluginContext(CLIContext(), "") as GlobalContext, ""),
                log.toXLog(),
                params
            )
        }

        return (petrinet as Petrinet).toPetriNet(initialMarking as Marking, finalMarking as Marking, log)
    }
}
