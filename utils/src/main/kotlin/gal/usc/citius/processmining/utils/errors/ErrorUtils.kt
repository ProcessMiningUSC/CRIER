package gal.usc.citius.processmining.utils.errors

import java.io.OutputStream
import java.io.PrintStream

fun suppressErrorOutput() {
    System.setErr(
        PrintStream(object : OutputStream() {
            override fun write(b: Int) {}
        })
    )
}
