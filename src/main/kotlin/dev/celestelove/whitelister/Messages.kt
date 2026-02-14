package dev.celestelove.whitelister

import fluent.bundle.FluentBundle
import fluent.functions.icu.ICUFunctionFactory
import fluent.syntax.parser.FTLParser
import fluent.syntax.parser.FTLStream
import java.io.File
import java.util.Locale

class Messages(private val dataFolder: File) {
    private lateinit var bundle: FluentBundle

    fun load(language: String) {
        val messagesDir = dataFolder.resolve("messages")
        val locale = Locale.forLanguageTag(language)
        val builder = FluentBundle.builder(locale, ICUFunctionFactory.INSTANCE)

        val file = messagesDir.resolve("$language.ftl")
        if (file.exists()) {
            val resource = FTLParser.parse(FTLStream.of(file.readText()))
            builder.addResourceOverriding(resource)
        }

        bundle = builder.build()
    }

    fun get(key: String, vararg args: Pair<String, Any>): String {
        return if (args.isEmpty()) {
            bundle.format(key)
        } else {
            bundle.format(key, args.toMap())
        }
    }
}
