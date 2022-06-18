package org.serenityos.jakt.plugin

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.intellij.sdk.language.psi.JaktTopLevelDefinition
import org.intellij.sdk.language.psi.impl.JaktTopLevelDefinitionImpl
import org.serenityos.jakt.JaktTypes
import org.serenityos.jakt.plugin.project.JaktProjectService
import org.serenityos.jakt.plugin.project.jaktProject
import org.serenityos.jakt.plugin.psi.JaktPsiFactory
import org.serenityos.jakt.plugin.psi.api.JaktModificationBoundary
import org.serenityos.jakt.plugin.psi.api.JaktModificationTracker
import org.serenityos.jakt.plugin.psi.api.JaktPsiScope
import org.serenityos.jakt.plugin.psi.api.JaktTypeable
import org.serenityos.jakt.plugin.psi.declaration.JaktDeclaration
import org.serenityos.jakt.plugin.psi.declaration.JaktImportStatementMixin
import org.serenityos.jakt.plugin.psi.declaration.JaktNameIdentifierOwner
import org.serenityos.jakt.plugin.type.Type
import org.serenityos.jakt.utils.descendantOfType
import org.serenityos.jakt.utils.findChildrenOfType

class JaktFile(
    viewProvider: FileViewProvider,
) : PsiFileBase(viewProvider, JaktLanguage), JaktModificationBoundary, JaktPsiScope, JaktDeclaration {
    override val jaktType: Type
        get() = findChildrenOfType<JaktTypeable>()
            .map { it.jaktType }
            .filterIsInstance<Type.TopLevelDecl>()
            .let { Type.Namespace(name, it) }

    override fun getDeclarations(): List<JaktDeclaration> = findChildrenOfType<JaktDeclaration>().flatMap {
        if (it is JaktImportStatementMixin) {
            listOf(it) + it.importBraceEntryList
        } else listOf(it)
    }

    override val tracker = JaktModificationTracker()

    override fun getFileType() = FileType

    override fun toString() = FileType.name

    override fun getNameIdentifier() = null

    object FileType : LanguageFileType(JaktLanguage) {
        override fun getName() = "Jakt file"

        override fun getDescription() = "The Jakt programming language"

        override fun getDefaultExtension() = "jakt"

        override fun getIcon() = JaktLanguage.ICON
    }
}