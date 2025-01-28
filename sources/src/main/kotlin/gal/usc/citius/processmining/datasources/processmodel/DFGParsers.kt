package gal.usc.citius.processmining.datasources.processmodel

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.ReaderSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.model.process.Activity
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.WeightedArc
import gal.usc.citius.processmining.model.process.dfg.DirectlyFollowsGraph
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Read a directed graph from a source with a custom TGF format (the weight is optional):
 *
 *    node_id node_name
 *    node_id node_name
 *    node_id node_name
 *    #
 *    source_id target_id edge_weight
 *    source_id target_id edge_weight
 *    source_id target_id edge_weight
 *
 * @return an instance of [DirectlyFollowsGraph] with the read graph.
 */
class DFGParser(override val reader: Reader) : ReaderSource<ProcessModel> {

    override fun read(): ProcessModel {
        val activitiesMap = mutableMapOf<String, Activity>()
        val arcs = mutableListOf<WeightedArc>()

        var state = 0
        reader.readLines().forEach { line ->
            if (line.trim().isNotEmpty()) {
                when (state) {
                    0 ->
                        // Reading activities
                        if (line.trim() == "#") {
                            // Finished reading activities
                            state = 1
                        } else {
                            // Read activity
                            val elements = line.trim().split(" ", limit = 2).toTypedArray()
                            activitiesMap[elements[0]] = Activity.from(elements[1])
                        }
                    1 -> {
                        // Reading arcs
                        val elements = line.trim().split(" ").toTypedArray()
                        arcs.add(
                            if (elements.size > 2)
                                WeightedArc(
                                    source = activitiesMap[elements[0]]!!,
                                    target = activitiesMap[elements[1]]!!,
                                    weight = elements[2].toDouble()
                                )
                            else
                                WeightedArc(
                                    source = activitiesMap[elements[0]]!!,
                                    target = activitiesMap[elements[1]]!!
                                )
                        )
                    }
                }
            }
        }

        return DirectlyFollowsGraph(
            activities = activitiesMap.values.toSet(),
            arcs = arcs.sortedBy { it.target.id }.sortedBy { it.source.id }.toSet()
        )
    }
}

class DFGFileParser(override val file: File) : FileSource<ProcessModel> {
    constructor(filePath: Path) : this(filePath.toFile())
    constructor(filePath: String) : this(Paths.get(filePath))

    override val SUPPORTED_TYPES: Collection<String> = setOf("tgf", "tgf.gz")

    override fun read(): ProcessModel =
        DFGParser(createGZIPCompatibleReader(file, SUPPORTED_TYPES)).read()
}
