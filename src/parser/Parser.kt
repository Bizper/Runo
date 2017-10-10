package parser

import log.*
import log.Log.Level
import resource.*
import java.io.File
import java.net.URL

class Parser {

    private var stream: String = ""
    private var pointer = 0
    private var length = 0
    private var root = Node(Type.OBJECT, "")

    private var list = ArrayList<String>()

    private val keywords = arrayOf(' ', '{', '[', '}', ']', ',', '\n', ':', '"', '\r')

    private var value_flag = false
    private var log_flag = true

    constructor() {
        if(log_flag) {
            Log.mode(Log.DETAIL)//使用SIMPLE模式
            Log.addUnrecordedLevel(Level.NORMAL)//NORMAL级别的log将不会被记录
            Log.record(Level.INFO, "Parser initialized")
        }
    }

    constructor(flag: Boolean): this() {
        this.log_flag = flag
    }

    constructor(type: String, path: String): this() {
        when(type) {
            "file" -> parseFile(path)
            "string" -> parse(path)
            "url" -> parse(URL(path))
            else -> Log.record(Level.ERROR, "type error")
        }
    }

    fun parse(string: String) {
        if(log_flag) {
            Log.record(Level.INFO, "input string: $string")
            Log.record(Level.INFO, "start parsing...")
        }
        pointer = 0//confirm pointer location, ready for parsing
        stream = string
        length = stream.length
        while(pointer < length) {
            val c = stream[pointer]//get the character at the pointer
            stateLog(parse(c))
        }
        print(root!!, 0)
        if(log_flag) Log.record(Level.INFO, "parse complete")
    }

    fun parseFile(path: String) {
        val file = File(path)
        if(log_flag) {
            Log.record(Level.INFO, "input file: ${file.canonicalPath}")
            Log.record(Level.INFO, "start parsing")
        }
        pointer = 0
        stream = file.readText()
        length = stream.length
        while(pointer < length) {
            val c = stream[pointer]//get the character at the local pointer
            stateLog(parse(c))
        }
        print(root!!, 0)
        if(log_flag) Log.record(Level.INFO, "parse complete")
    }

    fun parse(url: URL) {
        //TODO
    }

    private fun stateLog(sp: Sp) {
        when(sp.state) {
            State.PARSE_EXPECT_VALUE -> if(log_flag) Log.record(Level.ERROR, sp) else println(sp)
            State.PARSE_INVALID_VALUE -> if(log_flag) Log.record(Level.ERROR, sp) else println(sp)
            State.PARSE_ROOT_NOT_SINGULAR -> if(log_flag) Log.record(Level.WARN, sp) else println(sp)
            State.PARSE_SUCCESS -> if(log_flag) Log.record(Level.NORMAL, sp) else println(sp)
        }
    }

    private fun parse(c: Char): Sp {//分派器
        pointer++//此处指针值比char所在的值增加了1
        return if(c == '{') {
            parseObject()
        } else if(c == '[') {
            parseArray()
        } else if(c == '"') {
            parseString()
        } else if(c == 'f' || c == 't') {
            parseBoolean()
        } else if(Content.isNumber(c)) {//confirm the character is number format
            parseNumber()
        } else if(c == 'n') {
            parseNull()
        } else {
            if(c in keywords) return Sp(State.PARSE_SUCCESS, Type.KEYWORDS, c)
            else Sp(State.PARSE_EXPECT_VALUE, c)
        }
    }

    private fun parseObject(): Sp {//解析对象
        var point = pointer
        if(root.type == Type.STRING) root.type = Type.OBJECT
        value_flag = false
        var i = point
        while(true) {
            if(stream[i] == '}') {
                point = pointer
                break
            }
            stateLog(parse(stream[i]))
            i = pointer
        }
        pointer = point + 1
        root = if(root.parent == null) root else root.parent!!
        return Sp(State.PARSE_SUCCESS, Type.OBJECT)
    }

    private fun parseArray(): Sp {//解析数组
        var point = pointer
        if(root.type == Type.STRING) root.type = Type.ARRAY
        val cache = root
        var i = point
        while(true) {
            if(stream[i] == ']') {
                point = pointer
                break
            }
            value_flag = true//add buffer as value in array
            root = cache
            stateLog(parse(stream[i]))
            i = pointer
        }
        pointer = point + 1
        root = if(root.parent == null) root else root.parent!!
        value_flag = false//reset value flag
        return Sp(State.PARSE_SUCCESS, Type.ARRAY)
    }

    private fun parseString(): Sp {
        var buffer = ""
        var point = pointer
        (point until length)
                .takeWhile { stream[it] != '"' }
                .forEach {
                    buffer += stream[it]
                    point = it
                }
        list.add(buffer.trim())
        if(!value_flag) {
            val node = Node(root, Type.STRING, buffer.trim())
            root.add(node)
            root = node
            value_flag = !value_flag
        } else {
            val node = Node(root, Type.STRING, buffer.trim())
            root.add(node)
            root = if(root.parent == null) root else root.parent!!
            value_flag = !value_flag
        }
        pointer = point + 2//shift the pointer to the location where behind the next quote.
        return Sp(State.PARSE_SUCCESS, buffer)
    }

    private fun parseBoolean(): Sp {//解析布尔值
        var buffer = ""
        var point = pointer - 1
        (point until length)
                .takeWhile { !Content.isEnd(stream[it]) }
                .forEach {
                    if(!Content.isWhiteSpace(stream[it])) {
                        buffer += stream[it]
                        point = it
                    }
                }
        list.add(buffer.trim())
        if(!value_flag) {
            value_flag = !value_flag
            return Sp(State.PARSE_INVALID_VALUE, Type.BOOLEAN, "${buffer.trim()} can not be key")
        } else {
            val node = Node(root, Type.BOOLEAN, buffer.trim())
            root.add(node)
            root = if(root.parent == null) root else root.parent!!
            value_flag = !value_flag
        }
        pointer = point + 1
        return if(buffer == "true" || buffer == "false") {
            Sp(State.PARSE_SUCCESS, Type.BOOLEAN, buffer.trim())
        }
        else {
            Sp(State.PARSE_INVALID_VALUE, Type.BOOLEAN, buffer)
        }
    }

    private fun parseNumber(): Sp {//解析数字
        var buffer = ""
        var point = pointer - 1
        for(i in point until length) {
                if(Content.isEnd(stream[i])) break
                else if(!Content.isWhiteSpace(stream[i])){
                    buffer += stream[i]
                    point = i
                }
        }
        buffer = buffer.trim()
        list.add(buffer)
        if(!value_flag) {
            value_flag = !value_flag
            return Sp(State.PARSE_INVALID_VALUE, Type.NUMBER, "${buffer} can not be key")
        } else {
            val node = Node(root, Type.NUMBER, buffer)
            root.add(node)
            root = if(root.parent == null) root else root.parent!!
            value_flag = !value_flag
        }
        pointer = point + 1
        return if(!Content.isNumber(buffer)) {//校验数字格式是否正确
            Sp(State.PARSE_INVALID_VALUE, Type.NUMBER, buffer)
        }
        else {
            Sp(State.PARSE_SUCCESS, Type.NUMBER, buffer)
        }
    }

    private fun parseNull(): Sp {//解析空值
        var buffer = ""
        var point = pointer - 1
        (point until length)
                .takeWhile { !Content.isEnd(stream[it]) }
                .forEach {
                    if(!Content.isWhiteSpace(stream[it])) {
                        buffer += stream[it]
                        point = it
                    }
                }
        list.add(buffer.trim())
        if(!value_flag) {
            value_flag = !value_flag
            return Sp(State.PARSE_INVALID_VALUE, Type.NULL, "${buffer.trim()} can not be key")
        } else {
            val node = Node(root, Type.NULL, buffer.trim())
            root.add(node)
            root = if(root.parent == null) root else root.parent!!
            value_flag = !value_flag
        }
        pointer = point + 1
        return if(buffer == "null") Sp(State.PARSE_SUCCESS, Type.NULL, buffer.trim())
        else Sp(State.PARSE_INVALID_VALUE, Type.NULL, buffer)
    }

    fun print() {
        for(s in list) println(s)
    }

    private fun print(node: Node, height: Int) {
        (0..height).forEach {
            if(it >= 1) print("\t")
        }
        print("|--<${node.type} ${node.value}>\n")
        if(node.children.size != 0) {
            for(n in node.children) {
                print(n, height + 1)
            }
        }
    }

    private fun println(sp: Sp) {
        println("${sp.state} ${sp.type} ${sp.string}")
    }

}