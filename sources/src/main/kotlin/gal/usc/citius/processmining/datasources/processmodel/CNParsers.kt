package gal.usc.citius.processmining.datasources.processmodel

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNet
import gal.usc.citius.processmining.model.process.causal.causalnet.CausalNetActivity
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

class CNParser(
    override val reader: Reader,
    modelId: String = "CNModel"
) : CausalModelParser<CausalNetActivity>(reader, modelId) {

    override val SET_SEPARATOR: String = "|"
    override val IDS_SEPARATOR: String = "&"

    override fun createCausalModel(id: String, activities: Set<CausalNetActivity>): CausalNet =
        CausalNet(
            id = id,
            activities = activities
        )

    override fun createCausalActivity(
        id: String,
        name: String,
        inputs: CausalConnections,
        outputs: CausalConnections
    ): CausalNetActivity = CausalNetActivity(
        id = id,
        name = name,
        inputs = inputs,
        outputs = outputs
    )
}

class CNFileParser(override val file: File, val modelId: String = "CNModel") : FileSource<ProcessModel> {
    constructor(filePath: Path, modelId: String = "CNModel") : this(filePath.toFile(), modelId)
    constructor(filePath: String, modelId: String = "CNModel") : this(Paths.get(filePath), modelId)

    override val SUPPORTED_TYPES: Collection<String> = setOf("cn", "cn.gz")

    override fun read(): ProcessModel = CNParser(createGZIPCompatibleReader(file, SUPPORTED_TYPES), modelId).read()
}
