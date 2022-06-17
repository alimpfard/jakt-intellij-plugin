package org.serenityos.jakt.plugin.psi.declaration

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.intellij.sdk.language.psi.JaktFunctionDeclaration
import org.serenityos.jakt.plugin.psi.JaktPsiFactory
import org.serenityos.jakt.plugin.psi.api.JaktModificationTracker
import org.serenityos.jakt.plugin.psi.api.JaktTypeable
import org.serenityos.jakt.plugin.type.Type

abstract class JaktFunctionDeclarationMixin(
    node: ASTNode,
) : ASTWrapperPsiElement(node), JaktFunctionDeclaration, JaktNameIdentifierOwner, JaktDeclaration {
    override val tracker = JaktModificationTracker()

    override val jaktType: Type
        get() {
            val name = identifier.text

            val typeParameters = if (genericBounds != null) {
                getDeclGenericBounds().map { Type.TypeVar(it.identifier.text) }
            } else emptyList()

            val parameters = parameterList.map {
                Type.Function.Parameter(
                    it.identifier.text,
                    it.typeAnnotation.jaktType,
                    it.anonKeyword != null,
                    it.mutKeyword != null,
                )
            }

            val returnType = functionReturnType.type?.jaktType ?: Type.Primitive.Void

            return Type.Function(
                name,
                null,
                parameters,
                returnType
            ).let {
                it.declaration = this

                if (thisParameter != null) {
                    it.hasThis = true
                    it.thisIsMutable = thisParameter!!.mutKeyword != null
                }

                if (typeParameters.isNotEmpty()) {
                    Type.Parameterized(it, typeParameters)
                } else it
            }
        }

    override fun getDeclarations(): List<JaktDeclaration> = parameterList

    override fun getDeclGenericBounds() = genericBounds?.genericBoundList ?: emptyList()

    override fun getNameIdentifier(): PsiElement = identifier

    override fun getName(): String = nameIdentifier.text

    override fun setName(name: String) = apply {
        nameIdentifier.replace(JaktPsiFactory(project).createIdentifier(name))
    }

    override fun getTextOffset(): Int = nameIdentifier.textOffset
}
