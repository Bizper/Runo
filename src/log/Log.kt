package log

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class Log {

    companion object {
        val DETAIL = 0x0a
        val SIMPLE = 0x0b
    }

    private val default_format = "z E a hh:mm:ss:SS"
    private val default_simple_format = "HH:mm:ss:SS"
    private val filename_format = "yyyy-MM-dd"
    private var sf: SimpleDateFormat = SimpleDateFormat(filename_format)

    private var f: File = File("./src/log/${sf.format(Date())}.txt")
    private var fw: FileWriter? = null

    constructor() {
        sf = SimpleDateFormat(default_simple_format)
        init()
    }

    constructor(format: String): this() {
        sf = SimpleDateFormat(format)
        init()
    }

    constructor(st: Int) {
        when(st) {
            DETAIL ->  sf = SimpleDateFormat(default_format)
            SIMPLE -> sf = SimpleDateFormat(default_simple_format)
        }
        init()
    }

    private fun init() {
        if (f.exists().not()) {
            f.createNewFile()
        }
        fw = FileWriter(f)
    }

    fun record(l: Level, state: String) {
        fw?.append("${l.name}:[${sf.format(Date())}] $state\n")
        fw?.flush()
    }

    fun end() {
        fw?.flush()
        fw?.close()
    }

}