/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.js.test

import com.google.dart.compiler.backend.js.ast.JsProgram
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.common.output.outputUtils.writeAllTo
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.js.JavaScript
import org.jetbrains.kotlin.js.config.EcmaVersion
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.js.config.LibrarySourcesConfig
import org.jetbrains.kotlin.js.facade.K2JSTranslator
import org.jetbrains.kotlin.js.facade.MainCallParameters
import org.jetbrains.kotlin.js.facade.TranslationResult
import org.jetbrains.kotlin.js.test.rhino.RhinoFunctionResultChecker
import org.jetbrains.kotlin.js.test.rhino.RhinoUtils
import org.jetbrains.kotlin.js.test.utils.DirectiveTestUtils
import org.jetbrains.kotlin.js.test.utils.JsTestUtils
import org.jetbrains.kotlin.js.test.utils.verifyAst
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.KotlinTestUtils.TestFileFactory
import org.jetbrains.kotlin.test.KotlinTestWithEnvironment
import org.jetbrains.kotlin.utils.DFS
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.PrintStream
import java.nio.charset.Charset

abstract class BasicBoxTest(
        private val pathToTestDir: String,
        private val pathToOutputDir: String
) : KotlinTestWithEnvironment() {
    private val COMMON_FILES_NAME = "_common"
    private val COMMON_FILES_DIR = "_commonFiles/"
    private val MODULE_EMULATION_FILE = TEST_DATA_DIR_PATH + "/moduleEmulation.js"
    protected val additionalCommonFileDirectories = mutableListOf<String>()

    private val TEST_MODULE = "JS_TESTS"
    private val DEFAULT_MODULE = "main"
    private val TEST_FUNCTION = "box"

    fun doTest(filePath: String) {
        val file = File(filePath)
        val outputDir = getOutputDir(file)
        val sourceCode = KotlinTestUtils.doLoadFile(file)

        TestFileFactoryImpl().use { testFactory ->
            val inputFiles = KotlinTestUtils.createTestFiles(file.name, sourceCode, testFactory)
            val modules = inputFiles
                    .map { it.module }.distinct()
                    .associate { it.name to it }

            val orderedModules = DFS.topologicalOrder(modules.values) { module -> module.dependencies.mapNotNull { modules[it] } }

            val generatedJsFiles = orderedModules.asReversed().map { module ->
                val dependencies = module.dependencies.mapNotNull { modules[it]?.outputFileName(outputDir) + ".meta.js" }

                val outputFileName = module.outputFileName(outputDir) + ".js"
                generateJavaScriptFile(file.parent, module, outputFileName, dependencies, modules.size > 1)
                outputFileName
            }
            val mainModuleName = if (TEST_MODULE in modules) TEST_MODULE else DEFAULT_MODULE
            val mainModule = modules[mainModuleName]!!

            val checker = RhinoFunctionResultChecker(mainModuleName, testFactory.testPackage, TEST_FUNCTION, "OK")
            val globalCommonFiles = JsTestUtils.getFilesInDirectoryByExtension(
                    TEST_DATA_DIR_PATH + COMMON_FILES_DIR, JavaScript.EXTENSION)
            val localCommonFile = file.parent + "/" + COMMON_FILES_NAME + JavaScript.DOT_EXTENSION
            val localCommonFiles = if (File(localCommonFile).exists()) listOf(localCommonFile) else emptyList()

            val additionalCommonFiles = additionalCommonFileDirectories.flatMap { baseDir ->
                JsTestUtils.getFilesInDirectoryByExtension(baseDir + "/", JavaScript.EXTENSION)
            }
            val inputJsFiles = inputFiles
                    .filter { it.fileName.endsWith(".js") }
                    .map { file ->
                        val sourceFile = File(file.fileName)
                        val targetFile = File(outputDir, file.module.outputFileSimpleName() + "-js-" + sourceFile.name)
                        FileUtil.copy(File(file.fileName), targetFile)
                        targetFile.absolutePath
                    }

            val additionalFiles = mutableListOf<String>()
            if (modules.size > 1) {
                additionalFiles += MODULE_EMULATION_FILE
            }
            val additionalJsFile = filePath.removeSuffix("." + KotlinFileType.EXTENSION) + JavaScript.DOT_EXTENSION
            if (File(additionalJsFile).exists()) {
                additionalFiles += additionalJsFile
            }

            val allJsFiles = additionalFiles + inputJsFiles + generatedJsFiles + globalCommonFiles + localCommonFiles +
                             additionalCommonFiles

            if (!SKIP_NODE_JS.containsMatchIn(sourceCode)) {
                val nodeRunnerName = mainModule.outputFileName(outputDir) + ".node.js"
                val nodeRunnerText = generateNodeRunner(allJsFiles, outputDir, mainModuleName, testFactory.testPackage)
                FileUtil.writeToFile(File(nodeRunnerName), nodeRunnerText)
            }

            RhinoUtils.runRhinoTest(allJsFiles, checker)
        }
    }

    private fun generateNodeRunner(files: Collection<String>, dir: File, moduleName: String, testPackage: String?): String {
        val sb = StringBuilder()
        sb.append("module.exports = function(runBoxTest) {\n")

        val fileNameList = files.map { fileName ->
            "__dirname + \"/" + FileUtil.getRelativePath(dir, File(fileName)) + "\""
        }.joinToString(", ")
        val fqn = testPackage?: ""

        sb.append("    return runBoxTest([$fileNameList], \"$moduleName\", \"$fqn\");\n")
        sb.append("};\n")
        return sb.toString()
    }

    private fun getOutputDir(file: File) = File(pathToOutputDir, file.parentFile.relativeTo(File(pathToTestDir)).path)

    private fun TestModule.outputFileSimpleName(): String {
        val outputFileSuffix = if (this.name == TEST_MODULE) "" else "-$name"
        return getTestName(true) + outputFileSuffix
    }

    private fun TestModule.outputFileName(directory: File): String {
        return directory.absolutePath + "/" + outputFileSimpleName() + "_v5"
    }

    private fun generateJavaScriptFile(
            directory: String,
            module: TestModule,
            outputFileName: String,
            dependencies: List<String>,
            multiModule: Boolean
    ) {
        val testFiles = module.files.map { it.fileName }.filter { it.endsWith(".kt") }
        val globalCommonFiles = JsTestUtils.getFilesInDirectoryByExtension(
                TEST_DATA_DIR_PATH + COMMON_FILES_DIR, KotlinFileType.EXTENSION)
        val localCommonFile = directory + "/" + COMMON_FILES_NAME + "." + KotlinFileType.EXTENSION
        val localCommonFiles = if (File(localCommonFile).exists()) listOf(localCommonFile) else emptyList()
        val additionalCommonFiles = additionalCommonFileDirectories.flatMap { baseDir ->
            JsTestUtils.getFilesInDirectoryByExtension(baseDir + "/", KotlinFileType.EXTENSION)
        }
        val psiFiles = createPsiFiles(testFiles + globalCommonFiles + localCommonFiles + additionalCommonFiles)

        val config = createConfig(module, dependencies, multiModule)
        val outputFile = File(outputFileName)

        translateFiles(psiFiles, outputFile, config)
    }

    protected fun translateFiles(psiFiles: List<KtFile>, outputFile: File, config: JsConfig) {
        val translator = K2JSTranslator(config)
        val translationResult = translator.translate(psiFiles, MainCallParameters.noCall())

        if (translationResult !is TranslationResult.Success) {
            val outputStream = ByteArrayOutputStream()
            val collector = PrintingMessageCollector(PrintStream(outputStream), MessageRenderer.PLAIN_FULL_PATHS, true)
            AnalyzerWithCompilerReport.reportDiagnostics(translationResult.diagnostics, collector)
            val messages = outputStream.toByteArray().toString(Charset.forName("UTF-8"))
            throw AssertionError("The following errors occurred compiling test:\n" + messages)
        }

        val outputFiles = translationResult.getOutputFiles(outputFile, null, null)
        val outputDir = outputFile.parentFile ?: error("Parent file for output file should not be null, outputFilePath: " + outputFile.path)
        outputFiles.writeAllTo(outputDir)

        if (config.moduleKind == ModuleKind.COMMON_JS) {
            val content = FileUtil.loadFile(outputFile, true)
            val wrappedContent = "__beginModule__();\n" +
                                 "$content\n" +
                                 "__endModule__(\"${StringUtil.escapeStringCharacters(config.moduleId)}\");"
            FileUtil.writeToFile(outputFile, wrappedContent)
        }

        processJsProgram(translationResult.program, psiFiles)
    }

    protected fun processJsProgram(program: JsProgram, psiFiles: List<KtFile>) {
        psiFiles.asSequence()
                .map { it.text }
                .forEach { DirectiveTestUtils.processDirectives(program, it) }
        program.verifyAst()
    }

    private fun createPsiFiles(fileNames: List<String>): List<KtFile> {
        val psiManager = PsiManager.getInstance(project)
        val fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

        return fileNames.map { fileName -> psiManager.findFile(fileSystem.findFileByPath(fileName)!!) as KtFile }
    }

    private fun createConfig(module: TestModule, dependencies: List<String>, multiModule: Boolean): JsConfig {
        val configuration = environment.configuration.copy()

        configuration.put(CommonConfigurationKeys.DISABLE_INLINE, module.inliningDisabled)

        configuration.put(JSConfigurationKeys.LIBRARY_FILES, LibrarySourcesConfig.JS_STDLIB + dependencies)

        configuration.put(CommonConfigurationKeys.MODULE_NAME, module.name)
        configuration.put(JSConfigurationKeys.MODULE_KIND, module.moduleKind)
        configuration.put(JSConfigurationKeys.TARGET, EcmaVersion.v5)

        configuration.put(JSConfigurationKeys.META_INFO, multiModule)

        return LibrarySourcesConfig(project, configuration)
    }

    private inner class TestFileFactoryImpl() : TestFileFactory<TestModule, TestFile>, Closeable {
        var testPackage: String? = null
        val tmpDir = KotlinTestUtils.tmpDir("js-tests")
        val defaultModule = TestModule(TEST_MODULE, emptyList())

        override fun createFile(module: TestModule?, fileName: String, text: String, directives: Map<String, String>): TestFile? {
            val ktFile = KtPsiFactory(project).createFile(text)
            val boxFunction = ktFile.declarations.find { it is KtNamedFunction && it.name == TEST_FUNCTION  }
            if (boxFunction != null) {
                testPackage = ktFile.packageFqName.asString()
                if (testPackage?.isEmpty() ?: false) {
                    testPackage = null
                }
            }

            if (module != null) {
                MODULE_KIND_PATTERN.matchEntire(text)?.let { matchResult ->
                    module.moduleKind = ModuleKind.valueOf(matchResult.groupValues[1])
                }
            }

            val containingModule = module ?: defaultModule
            if (NO_INLINE_PATTERN.containsMatchIn(text)) {
                containingModule.inliningDisabled = true
            }

            val temporaryFile = File(tmpDir, fileName)
            KotlinTestUtils.mkdirs(temporaryFile.parentFile)
            temporaryFile.writeText(text, Charsets.UTF_8)

            return TestFile(temporaryFile.absolutePath, containingModule)
        }

        override fun createModule(name: String, dependencies: List<String>): TestModule? {
            return TestModule(name, dependencies)
        }

        override fun close() {
            FileUtil.delete(tmpDir)
        }
    }

    private class TestFile(val fileName: String, val module: TestModule) {
        init {
            module.files += this
        }
    }

    private class TestModule(
            val name: String,
            dependencies: List<String>
    ) {
        val dependencies = dependencies.toMutableList()
        var moduleKind = ModuleKind.PLAIN
        var inliningDisabled = false
        val files = mutableListOf<TestFile>()
    }

    override fun createEnvironment(): KotlinCoreEnvironment {
        return KotlinCoreEnvironment.createForTests(testRootDisposable, CompilerConfiguration(), EnvironmentConfigFiles.JS_CONFIG_FILES)
    }

    companion object {
        val TEST_DATA_DIR_PATH = "js/js.translator/testData/"

        private val MODULE_KIND_PATTERN = Regex("^//\\s*MODULE_KIND:\\s*(.+)$", RegexOption.MULTILINE)
        private val NO_INLINE_PATTERN = Regex("^//\\s*NO_INLINE\\s*$", RegexOption.MULTILINE)
        private val SKIP_NODE_JS = Regex("^//\\s*SKIP_NODE_JS\\s*$", RegexOption.MULTILINE)
    }
}
