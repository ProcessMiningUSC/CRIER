package gal.usc.citius.processmining.conceptdrift.structural.builder

object Builder {
    fun fixedWindowSize(): FixedWindowSizeBuilder = FixedWindowSizeBuilder()
    fun findingBestWindowSize(): AutomaticWindowSizeBuilder = AutomaticWindowSizeBuilder()
}
