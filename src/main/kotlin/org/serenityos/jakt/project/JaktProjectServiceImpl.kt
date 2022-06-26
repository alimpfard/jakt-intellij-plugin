package org.serenityos.jakt.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScopes
import org.intellij.sdk.language.psi.JaktTopLevelDefinition
import org.serenityos.jakt.JaktFile
import org.serenityos.jakt.psi.declaration.JaktDeclaration
import org.serenityos.jakt.psi.declaration.isTypeDeclaration
import org.serenityos.jakt.psi.findChildrenOfType
import org.serenityos.jakt.utils.runInReadAction
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.deleteIfExists

class JaktProjectServiceImpl(private val project: Project) : JaktProjectService {
    @Volatile
    private var prelude: JaktFile? = null

    private var preludeDeclarations = mutableMapOf<String, JaktDeclaration>()

    init {
        CompletableFuture.supplyAsync {
            val preludePath = Paths.get(project.workspaceFile!!.parent.path, "prelude.jakt")
            preludePath.deleteIfExists()

            try {
                URL(PRELUDE_URL).openStream().use {
                    Files.copy(it, preludePath)
                }
            } catch (e: IOException) {
                error("Unable to load prelude; did its location in the repository change?")
            }

            val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(preludePath)
                ?: error("Unable to get VirtualFile from prelude.jakt at $preludePath")

            runInReadAction {
                prelude = PsiManager.getInstance(project).findFile(virtualFile) as? JaktFile
                    ?: error("Unable to get JaktFile from prelude.jakt at $preludePath")

                prelude!!.findChildrenOfType<JaktTopLevelDefinition>()
                    .filterIsInstance<JaktDeclaration>()
                    .forEach {
                        preludeDeclarations[it.name] = it
                    }
            }
        }
    }

    override fun getPreludeTypes() = preludeDeclarations.values.toList()

    override fun findPreludeDeclaration(type: String): JaktDeclaration? = preludeDeclarations[type]

    override fun findPreludeTypeDeclaration(type: String): JaktDeclaration? = preludeDeclarations[type]?.takeIf {
        it.isTypeDeclaration
    }

    override fun resolveImportedFile(from: VirtualFile, name: String): JaktFile? {
        val scope = GlobalSearchScopes.directoryScope(project, from.parent ?: return null, false)
        val virtualFiles = FilenameIndex.getVirtualFilesByName("$name.jakt", scope)
        return virtualFiles.firstOrNull()?.let {
            PsiManager.getInstance(project).findFile(it) as? JaktFile
        }
    }

    companion object {
        private const val PRELUDE_URL = "https://raw.githubusercontent.com/SerenityOS/jakt/main/runtime/prelude.jakt"
    }
}
