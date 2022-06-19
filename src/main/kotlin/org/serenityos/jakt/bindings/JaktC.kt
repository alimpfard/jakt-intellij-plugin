package org.serenityos.jakt.bindings

import com.sun.jna.Library
import com.sun.jna.Native
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json


interface JaktC : Library {
    fun typecheck(path: String): String

    @Suppress("UnnecessaryOptInAnnotation")
    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        private val INSTANCE = Native.load("/libjakt.so", JaktC::class.java) as JaktC

        fun typecheck(path: String): TypecheckResult {
            return Json.decodeFromString(INSTANCE.typecheck(path))
        }
    }
}
