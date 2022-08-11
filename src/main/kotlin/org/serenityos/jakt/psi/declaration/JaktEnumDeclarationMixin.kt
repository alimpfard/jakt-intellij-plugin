package org.serenityos.jakt.psi.declaration

import com.intellij.lang.ASTNode
import org.serenityos.jakt.psi.api.JaktEnumDeclaration
import org.serenityos.jakt.psi.api.JaktGenericBound
import org.serenityos.jakt.psi.api.JaktNormalEnumBody
import org.serenityos.jakt.psi.api.JaktUnderlyingTypeEnumBody
import org.serenityos.jakt.psi.named.JaktNamedElement
import org.serenityos.jakt.psi.reference.function
import org.serenityos.jakt.type.*
import org.serenityos.jakt.utils.recursivelyGuarded

abstract class JaktEnumDeclarationMixin(
    node: ASTNode,
) : JaktNamedElement(node), JaktEnumDeclaration {
    override val jaktType by recursivelyGuarded<EnumType> {
        val variants = mutableMapOf<String, EnumVariantType>()
        val methods = mutableMapOf<String, FunctionType>()

        producer {
            variants.clear()
            methods.clear()

            val typeParameters = getDeclGenericBounds().map { TypeParameter(it.nameNonNull) }

            EnumType(
                nameNonNull,
                boxedKeyword != null,
                null, // TODO: Why does calculating this in the producer cause a StackOverflow?
                typeParameters,
                variants,
                methods,
            ).also {
                it.psiElement = this@JaktEnumDeclarationMixin
            }
        }

        initializer { enum ->
            variants.putAll(underlyingTypeEnumBody?.enumVariantList?.associate {
                it.nameNonNull to it.jaktType as EnumVariantType
            } ?: normalEnumBody?.normalEnumMemberList?.mapNotNull {
                it.enumVariant
            }?.associate {
                it.nameNonNull to it.jaktType as EnumVariantType
            }.orEmpty())

            methods.putAll(normalEnumBody?.normalEnumMemberList?.mapNotNull {
                it.structMethod?.function
            }?.associate {
                it.nameNonNull to it.jaktType as FunctionType
            }.orEmpty())

            enum.underlyingType = underlyingTypeEnumBody?.typeAnnotation?.jaktType as? PrimitiveType
        }
    }

    override fun getDeclarations(): List<JaktDeclaration> {
        return when (val body = normalEnumBody ?: underlyingTypeEnumBody) {
            is JaktNormalEnumBody -> buildList {
                body.genericBounds?.genericBoundList?.let(::addAll)
                body.normalEnumMemberList.forEach {
                    add(it.enumVariant ?: it.structMethod?.function ?: return@forEach)
                }
            }
            is JaktUnderlyingTypeEnumBody -> body.enumVariantList
            else -> emptyList()
        }
    }

    override fun getDeclGenericBounds(): List<JaktGenericBound> =
        normalEnumBody?.genericBounds?.genericBoundList.orEmpty()
}
