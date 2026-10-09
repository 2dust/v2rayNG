package com.v2ray.ang

import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The app's resources, read from the module's folder the unit tests run in. */
internal object AppResources {

    /** The items of the string array [name] of values/arrays.xml, each trimmed. */
    fun stringArray(name: String): List<String> {
        val arrays = File("src/main/res/values/arrays.xml")
        assertTrue(arrays.isFile, "${arrays.absolutePath} is where the unit tests run from")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arrays).getElementsByTagName("string-array")
        val array = (0 until nodes.length).map { nodes.item(it) }.single { it.attributes.getNamedItem("name").nodeValue == name }
        val items = array.childNodes
        return (0 until items.length).map { items.item(it) }.filter { it.nodeName == "item" }.map { it.textContent.trim() }
    }
}
