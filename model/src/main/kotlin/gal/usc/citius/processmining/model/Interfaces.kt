package gal.usc.citius.processmining.model

import guru.nidi.graphviz.engine.Engine
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import java.nio.file.Paths

interface Printable {
    fun toDOT(edges: EdgeType = EdgeType.AUTO, direction: Direction = Direction.LR): String
    fun toPNG(
        destination: String,
        edges: EdgeType = EdgeType.AUTO,
        direction: Direction = Direction.LR,
        size: Size = Size(),
        layout: Layout = Layout.DOT
    ) {
        Graphviz.fromString(this.toDOT(edges, direction))
            .engine(layout.engine)
            .height(size.height)
            .width(size.width)
            .render(Format.PNG)
            .toFile(Paths.get(destination).toFile())
    }
}

data class Size(val width: Int = 0, val height: Int = 0)
enum class EdgeType(val value: String) {
    NONE("none"), STRAIGHT("line"), ORTHOGONAL("ortho"), CURVED("curved"), POLYLINE("polyline"), AUTO("spline")
}

enum class Direction {
    LR, RL, TB, BT
}

enum class Layout(val engine: Engine) {
    DOT(Engine.DOT), NEATO(Engine.NEATO), FDP(Engine.FDP), OSAGE(Engine.OSAGE), TWOPI(Engine.TWOPI), CIRCO(Engine.CIRCO)
}
