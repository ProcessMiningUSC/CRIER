package gal.usc.citius.processmining.datasources.processmodel

import gal.usc.citius.processmining.datasources.FileSource
import gal.usc.citius.processmining.datasources.createGZIPCompatibleReader
import gal.usc.citius.processmining.model.process.ProcessModel
import gal.usc.citius.processmining.model.process.causal.CausalConnections
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrix
import gal.usc.citius.processmining.model.process.causal.causalmatrix.CausalMatrixActivity
import java.io.File
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

class CMParser(
    override val reader: Reader,
    modelId: String = "CMModel"
) : CausalModelParser<CausalMatrixActivity>(reader, modelId) {

    override val SET_SEPARATOR: String = "&"
    override val IDS_SEPARATOR: String = "|"

    override fun createCausalModel(id: String, activities: Set<CausalMatrixActivity>): CausalMatrix =
        CausalMatrix(
            id = id,
            activities = activities
        )

    override fun createCausalActivity(
        id: String,
        name: String,
        inputs: CausalConnections,
        outputs: CausalConnections
    ): CausalMatrixActivity = CausalMatrixActivity(
        id = id,
        name = name,
        inputs = inputs,
        outputs = outputs
    )
}

class CMFileParser(override val file: File, val modelId: String = "CMModel") : FileSource<ProcessModel> {
    constructor(filePath: Path, modelId: String = "CMModel") : this(filePath.toFile(), modelId)
    constructor(filePath: String, modelId: String = "CMModel") : this(Paths.get(filePath), modelId)

    override val SUPPORTED_TYPES: Collection<String> = setOf("cm", "cm.gz", "hn", "hn.gz")

    override fun read(): ProcessModel = CMParser(createGZIPCompatibleReader(file, SUPPORTED_TYPES), modelId).read()
}
