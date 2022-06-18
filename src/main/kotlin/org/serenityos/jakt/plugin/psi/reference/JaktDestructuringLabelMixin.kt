package org.serenityos.jakt.plugin.psi.reference

import com.intellij.lang.ASTNode
import org.intellij.sdk.language.psi.JaktDestructuringLabel
import org.intellij.sdk.language.psi.JaktMatchPattern
import org.intellij.sdk.language.psi.JaktNormalEnumVariant
import org.serenityos.jakt.plugin.psi.named.JaktNamedElement
import org.serenityos.jakt.utils.ancestorOfType

abstract class JaktDestructuringLabelMixin(
    node: ASTNode,
) : JaktNamedElement(node), JaktDestructuringLabel {
    override fun getReference() = singleRef {
        val head = ancestorOfType<JaktMatchPattern>()?.plainQualifier ?: return@singleRef null
        val resolved = head.reference?.resolve() as? JaktNormalEnumVariant

        @Suppress("FoldInitializerAndIfToElvis")
        if (resolved == null) {
            // TODO: Use more complex lookup here, since Jakt allows one to omit the Enum name for
            //       members in a match pattern head
            return@singleRef null
        }

        resolved.structEnumMemberBodyPartList.find {
            it.identifier.text == name
        }
    }
}
