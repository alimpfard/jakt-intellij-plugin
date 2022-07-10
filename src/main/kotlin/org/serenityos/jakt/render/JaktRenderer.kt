package org.serenityos.jakt.render

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import org.intellij.sdk.language.psi.JaktFieldAccessExpression
import org.serenityos.jakt.psi.api.JaktTypeable
import org.serenityos.jakt.psi.api.jaktType
import org.serenityos.jakt.syntax.Highlights
import org.serenityos.jakt.type.*
import org.serenityos.jakt.utils.unreachable

fun renderElement(element: PsiElement, asHtml: Boolean): String {
    val renderer = if (asHtml) JaktRenderer.HTML else JaktRenderer.Plain
    return renderer.renderElement(element)
}

fun renderType(type: Type, asHtml: Boolean): String {
    val renderer = if (asHtml) JaktRenderer.HTML else JaktRenderer.Plain
    return renderer.renderType(type)
}

/**
 * Renders a type to displayable text.
 *
 * When rendering as an expression, declarations have their keywords omitted
 * (i.e. "Foo" rather than "struct Foo"). When rendering as short, namespaces
 * are omitted.
 */
sealed class JaktRenderer {
    protected abstract val builder: Builder

    fun renderElement(element: PsiElement): String = withSynchronized(builder) {
        clear()

        when (element) {
            is JaktFieldAccessExpression -> {
                appendStyled(element.name!!, Highlights.STRUCT_FIELD)
                append(": ")
                appendType(element.jaktType, emptyMap())
            }
            is JaktTypeable -> appendType(element.jaktType, emptyMap())
            else -> append("TODO: JaktRenderer(${element::class.simpleName})")
        }

        toString()
    }

    fun renderType(type: Type): String = withSynchronized(builder) {
        clear()
        appendType(type, emptyMap())
        toString()
    }

    private fun appendType(type: Type, specializations: Map<TypeParameter, Type>): Unit = withSynchronized(builder) {
        renderNamespaces(type)

        when (type) {
            is UnknownType -> append("??")
            is PrimitiveType -> {
                if (type != PrimitiveType.Void)
                    appendStyled(type.typeName, Highlights.TYPE_NAME)
            }
            is NamespaceType -> appendStyled(type.name, Highlights.NAMESPACE_NAME)
            is WeakType -> {
                appendStyled("weak ", Highlights.KEYWORD_MODIFIER)
                appendType(type.underlyingType, specializations)
                appendStyled("?", Highlights.TYPE_OPTIONAL_QUALIFIER)
            }
            is RawType -> {
                appendStyled("raw ", Highlights.KEYWORD_MODIFIER)
                appendType(type.underlyingType, specializations)
            }
            is OptionalType -> {
                appendType(type.underlyingType, specializations)
                appendStyled("?", Highlights.TYPE_OPTIONAL_QUALIFIER)
            }
            is ArrayType -> {
                appendStyled("[", Highlights.DELIM_BRACKET)
                appendType(type.underlyingType, specializations)
                appendStyled("]", Highlights.DELIM_BRACKET)
            }
            is SetType -> {
                appendStyled("{", Highlights.DELIM_BRACE)
                appendType(type.underlyingType, specializations)
                appendStyled("}", Highlights.DELIM_BRACE)
            }
            is DictionaryType -> {
                appendStyled("[", Highlights.DELIM_BRACE)
                appendType(type.keyType, specializations)
                appendStyled(":", Highlights.COLON)
                appendType(type.valueType, specializations)
                appendStyled("]", Highlights.DELIM_BRACE)
            }
            is TupleType -> {
                appendStyled("(", Highlights.DELIM_PARENTHESIS)
                type.types.forEachIndexed { index, it ->
                    appendType(it, specializations)
                    if (index != type.types.lastIndex)
                        append(", ")
                }
                appendStyled(")", Highlights.DELIM_PARENTHESIS)
            }
            is TypeParameter -> {
                val specializedType = specializations[type]
                if (specializedType != null) {
                    appendType(specializedType, specializations)
                } else appendStyled(type.name, Highlights.TYPE_GENERIC_NAME)
            }
            is StructType -> {
                appendStyled("struct ", Highlights.KEYWORD_DECLARATION)
                appendStyled(type.name, Highlights.STRUCT_NAME)
                renderGenerics(type, specializations)
            }
            is EnumType -> {
                appendStyled("enum ", Highlights.KEYWORD_DECLARATION)
                appendStyled(type.name, Highlights.ENUM_NAME)
                renderGenerics(type, specializations)
            }
            is EnumVariantType -> {
                appendStyled(type.parent.name, Highlights.ENUM_NAME)
                appendStyled("::", Highlights.NAMESPACE_QUALIFIER)
                appendStyled(type.name, Highlights.ENUM_VARIANT_NAME)
                // TODO: Members?
            }
            is FunctionType -> {
                appendStyled("function ", Highlights.KEYWORD_DECLARATION)
                appendStyled(type.name, Highlights.FUNCTION_DECLARATION)
                renderGenerics(type, specializations)
                append("(")

                if (type.parameters.isNotEmpty()) {
                    type.parameters.forEachIndexed { index, it ->
                        appendStyled(it.name, Highlights.FUNCTION_PARAMETER)
                        appendStyled(": ", Highlights.COLON)
                        appendType(it.type, specializations)

                        if (index != type.parameters.lastIndex)
                            append(",")
                    }
                }

                append(")")

                appendStyled(": ", Highlights.COLON)
                appendType(type.returnType, specializations)
            }
            is BoundType -> appendType(type.type, specializations + type.specializations)
            else -> unreachable()
        }
    }

    private fun renderGenerics(
        type: GenericType,
        specializations: Map<TypeParameter, Type>,
    ): Unit = withSynchronized(builder) {
        val parameters = type.typeParameters.map { specializations[it] ?: it }
        if (parameters.isEmpty())
            return@withSynchronized

        append("<")
        for ((index, parameter) in parameters.withIndex()) {
            appendType(parameter, emptyMap())
            if (index != parameters.lastIndex)
                append(", ")
        }
        append(">")
    }

    private fun renderNamespaces(type: Type): Unit = withSynchronized(builder) {
        type.namespace?.also {
            if (".jakt" in it.name) {
                // Hack: Files are treated as namespaces in the type system, but we
                // definitely don't want to prefix a type with "foo.jakt::". Perhaps
                // files should have their own type?
                return@withSynchronized
            }

            renderNamespaces(it)
            appendStyled(it.name, Highlights.NAMESPACE_NAME)
            appendStyled("::", Highlights.NAMESPACE_QUALIFIER)
        }
    }

    private fun <T : Any, R> withSynchronized(obj: T, block: T.() -> R) = synchronized(obj) {
        with(obj, block)
    }

    abstract class Builder {
        protected val builder = StringBuilder()

        abstract fun appendStyled(value: String, key: TextAttributesKey)

        open fun append(v: String) = apply { builder.append(v) }
        fun append(v: Char) = apply { builder.append(v) }
        fun append(v: Int) = apply { builder.append(v) }
        fun append(v: Float) = apply { builder.append(v) }
        fun append(v: Double) = apply { builder.append(v) }
        fun clear() = apply { builder.clear() }
        override fun toString() = builder.toString()
    }

    object HTML : JaktRenderer() {
        override val builder = object : Builder() {
            override fun append(v: String) = apply { builder.append(StringUtil.escapeXmlEntities(v)) }

            override fun appendStyled(value: String, key: TextAttributesKey) {
                HtmlSyntaxInfoUtil.appendStyledSpan(
                    builder,
                    key,
                    value,
                    DocumentationSettings.getHighlightingSaturation(false),
                )
            }
        }
    }

    object Plain : JaktRenderer() {
        override val builder = object : Builder() {
            override fun appendStyled(value: String, key: TextAttributesKey) {
                builder.append(value)
            }
        }
    }
}
