package com.easyhooon.dari.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor

private const val WEB_VIEW_CLASS = "android.webkit.WebView"
private const val WEB_VIEW_CLIENT_CLASS = "android.webkit.WebViewClient"

// Instrumenting the hooks would turn their forwarding addJavascriptInterface call into infinite recursion.
private const val HOOKS_PACKAGE = "com.easyhooon.dari.auto."

// Platform, Jetpack and Kotlin classes hold no app bridges; skipping them keeps InstrumentationScope.ALL cheaper.
private val SKIPPED_PREFIXES = listOf(
    "android.", "androidx.", "kotlin.", "kotlinx.", "java.", "javax.", "com.google.", "org.jetbrains.", "org.intellij.",
)

/** `io.github.easyhooon.dari`: captures mapped bridge calls in debuggable variants. */
class DariPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("dari", DariExtension::class.java)
        extension.scope.convention(InstrumentationScope.PROJECT)
        listOf("com.android.application", "com.android.library").forEach { id ->
            project.pluginManager.withPlugin(id) { register(project, extension) }
        }
    }

    private fun register(project: Project, extension: DariExtension) {
        val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
        components.onVariants { variant ->
            // Release builds ship no hooks; debuggable ones work with both dari and dari-noop.
            if (!variant.debuggable) return@onVariants
            val tag = extension.tag.orNull
            val targets = extension.mappings.map { MappingTarget(it.className, it.methodName, it.spec.toSpec(tag)) }
            variant.instrumentation.transformClassesWith(DariClassVisitorFactory::class.java, extension.scope.get()) {
                it.targets.set(targets)
            }
            // AdviceAdapter only pushes onto the stack at method entry; frames for those methods are recomputed.
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
        }
    }
}

interface DariParameters : InstrumentationParameters {
    @get:Input
    val targets: ListProperty<MappingTarget>
}

abstract class DariClassVisitorFactory : AsmClassVisitorFactory<DariParameters> {

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        val current = classContext.currentClassData
        return DariClassVisitor(
            api = instrumentationContext.apiVersion.get(),
            next = nextClassVisitor,
            className = current.className,
            targets = parameters.get().targets.get(),
            isWebViewClient = WEB_VIEW_CLIENT_CLASS in current.superClasses,
            // Subclass receivers count too; the hook still dispatches virtually to their override.
            isWebView = { owner ->
                val name = owner.replace('/', '.')
                name == WEB_VIEW_CLASS || classContext.loadClassData(name)?.superClasses?.contains(WEB_VIEW_CLASS) == true
            },
            log = GradleDariLog,
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val name = classData.className
        if (name.startsWith(HOOKS_PACKAGE)) return false
        return parameters.get().targets.get().any { it.matches(name) } || SKIPPED_PREFIXES.none { name.startsWith(it) }
    }
}

private object GradleDariLog : DariLog {
    private val logger = Logging.getLogger(DariPlugin::class.java)

    override fun info(message: String) = logger.info(message)

    override fun warn(message: String) = logger.warn(message)

    override fun error(message: String) = logger.error(message)
}
