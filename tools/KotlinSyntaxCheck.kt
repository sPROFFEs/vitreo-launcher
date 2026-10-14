// Optional syntax-only check for environments without an Android SDK.
// It cannot validate Android/Compose types, resource linking or shader execution.
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project, false)
        val root = File(args.single()).canonicalFile
        val files = root.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".git") }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }.toList()
        var errors = 0
        for (file in files) {
            val text = file.readText()
            val parsed = factory.createFile(file.name, text)
            for (error in PsiTreeUtil.collectElementsOfType(parsed, PsiErrorElement::class.java)) {
                val line = text.take(error.textOffset).count { it == '\n' } + 1
                println("${file.relativeTo(root)}:$line: ${error.errorDescription}"); errors++
            }
        }
        check(errors == 0) { "$errors Kotlin syntax errors" }
        println("KOTLIN SYNTAX: ${files.size} files; zero parser errors. NOT an Android compilation.")
    } finally { Disposer.dispose(disposable) }
}
