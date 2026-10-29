package com.easyhooon.dari.desktop.app.ios

import org.w3c.dom.Element
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** The XML property lists usbmuxd and lockdownd speak. Only dicts, arrays, strings, integers and booleans. */
internal object XmlPlist {
    fun encode(values: Map<String, Any>): ByteArray = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">""")
        append("""<plist version="1.0">""")
        appendValue(values)
        append("</plist>")
    }.encodeToByteArray()

    fun decode(bytes: ByteArray): Map<String, Any?> {
        val document = try {
            factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: SAXException) {
            throw IOException("Malformed plist", e)
        }
        val root = document.documentElement.children().firstOrNull() ?: throw IOException("Empty plist")
        @Suppress("UNCHECKED_CAST")
        return value(root) as? Map<String, Any?> ?: throw IOException("Plist root is not a dict")
    }

    private fun StringBuilder.appendValue(value: Any) {
        when (value) {
            is Map<*, *> -> {
                append("<dict>")
                value.forEach { (key, item) ->
                    append("<key>").append(escape(key.toString())).append("</key>")
                    appendValue(requireNotNull(item))
                }
                append("</dict>")
            }
            is List<*> -> {
                append("<array>")
                value.forEach { appendValue(requireNotNull(it)) }
                append("</array>")
            }
            is Boolean -> append(if (value) "<true/>" else "<false/>")
            is Int, is Long -> append("<integer>").append(value).append("</integer>")
            else -> append("<string>").append(escape(value.toString())).append("</string>")
        }
    }

    private fun value(element: Element): Any? = when (element.tagName) {
        "dict" -> element.children().chunked(2).associate { (key, item) -> key.textContent to value(item) }
        "array" -> element.children().map(::value)
        "string" -> element.textContent
        "integer" -> element.textContent.trim().toLong()
        "true" -> true
        "false" -> false
        // Data, dates and reals aren't needed by Dari.
        else -> null
    }

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>()

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // Replies carry Apple's DOCTYPE; never fetch it or expand external entities.
    private val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        isExpandEntityReferences = false
    }
}
