package com.easyhooon.dari.gradle

import org.gradle.api.GradleException
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter
import org.objectweb.asm.commons.Method
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.io.Serializable

internal const val HOOKS = "com/easyhooon/dari/auto/DariHooks"
internal const val WEB_VIEW = "android/webkit/WebView"
private const val INTERCEPTOR_PACKAGE = "com/easyhooon/dari/interceptor/"
private const val ADD_JS_INTERFACE = "addJavascriptInterface"
private const val ADD_JS_INTERFACE_DESC = "(Ljava/lang/Object;Ljava/lang/String;)V"
private const val VISITED_HISTORY = "doUpdateVisitedHistory"
private const val VISITED_HISTORY_DESC = "(Landroid/webkit/WebView;Ljava/lang/String;Z)V"
private const val CONTINUATION = "kotlin/coroutines/Continuation"
private const val LABEL = "label"
private val ARG_NAME = Regex("""\{"argName":"([^"]*)"\}""")

private val HOOKS_TYPE: Type = Type.getObjectType(HOOKS)
private val ON_MAPPED_CALL = Method("onMappedCall", "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)V")
private val ON_VISITED_HISTORY = Method("onVisitedHistory", "(L$WEB_VIEW;Ljava/lang/String;)V")

/** A mapped method and the spec the runtime hook evaluates. Class names use dots, `$` or `.` for nesting. */
data class MappingTarget(val className: String, val methodName: String, val spec: String) : Serializable {
    fun matches(binaryClassName: String): Boolean = normalizeClassName(className) == normalizeClassName(binaryClassName)

    private companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Rewrites one class:
 * - mapped methods call `DariHooks.onMappedCall(spec, this, args)` on entry;
 * - `WebViewClient.doUpdateVisitedHistory` overrides report the page URL;
 * - `addJavascriptInterface` call sites go through `DariHooks` so bridges know their WebView.
 */
internal class DariClassVisitor(
    api: Int,
    next: ClassVisitor,
    private val className: String,
    targets: List<MappingTarget>,
    private val isWebViewClient: Boolean,
    private val isWebView: (internalName: String) -> Boolean,
    private val log: DariLog,
) : ClassVisitor(api, next) {

    private val targets = targets.filter { it.matches(className) }
    private val matched = mutableSetOf<String>()

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? {
        val next = super.visitMethod(access, name, descriptor, signature, exceptions) ?: return null
        val rewriter = CallSiteRewriter(api, next, isWebView)
        val hasBody = access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) == 0
        // Kotlin's synthetic `$default` bridges forward to the real method, which gets the hook itself.
        val specs = targets.filter { it.methodName == name }.map { it.spec }
        return when {
            specs.isNotEmpty() && hasBody && access and Opcodes.ACC_SYNTHETIC == 0 -> {
                matched += name
                MappedMethod(api, rewriter, access, name, descriptor, signature, exceptions, specs)
            }
            isWebViewClient && hasBody && name == VISITED_HISTORY && descriptor == VISITED_HISTORY_DESC ->
                VisitedHistoryAdvice(api, rewriter, access, name, descriptor)
            else -> rewriter
        }
    }

    override fun visitEnd() {
        // DariCheckMappingsTask reports this for every variant build; here it only shows up with --info.
        targets.filter { it.methodName !in matched }.forEach {
            log.info("Dari: $className has no method '${it.methodName}' with a body; that mapping captures nothing")
        }
        super.visitEnd()
    }

    /** Buffers the whole method first, so a body that already calls Dari by hand is left alone. */
    private inner class MappedMethod(
        api: Int,
        private val next: MethodVisitor,
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
        private val specs: List<String>,
    ) : MethodNode(api, access, name, descriptor, signature, exceptions) {
        override fun visitEnd() {
            super.visitEnd()
            val manual = instructions.any { it is MethodInsnNode && it.owner.startsWith(INTERCEPTOR_PACKAGE) }
            if (manual) {
                log.warn("Dari: $className.$name already calls DariInterceptor; skipped to avoid duplicate entries")
                accept(next)
            } else {
                val stateMachine = stateMachineClass()
                val note = when {
                    stateMachine != null -> " (suspend; resumptions skipped)"
                    isSuspend() -> " (suspend without a state machine)"
                    else -> ""
                }
                val resolved = resolveArgNames()
                log.info("Dari: instrumented $className.$name$note")
                accept(MappedCallAdvice(api, next, access, name, desc, resolved, stateMachine))
            }
        }

        /** Rewrites each `{"argName":"x"}` in [specs] to `{"arg":n}`, or fails the build when a name has no parameter. */
        private fun resolveArgNames(): List<String> {
            val wanted = specs.flatMap { spec -> ARG_NAME.findAll(spec).map { it.groupValues[1] } }.distinct()
            if (wanted.isEmpty()) return specs
            val where = "$className.$name$desc"
            val names = parameterNames() ?: fail(
                "Dari: $where has no parameter names (no LocalVariableTable or MethodParameters), " +
                    "so arg(${wanted.joinToString { "\"$it\"" }}) cannot be resolved; use arg(index) instead",
            )
            val missing = wanted.filter { it !in names }
            if (missing.isNotEmpty()) {
                fail("Dari: $where has no parameter named ${missing.joinToString { "'$it'" }}; its parameters are [${names.joinToString()}]")
            }
            return specs.map { spec -> spec.replace(ARG_NAME) { """{"arg":${names.indexOf(it.groupValues[1])}}""" } }
        }

        /** AGP wraps the exception as "Error occurred while instrumenting class", so the reason is logged too. */
        private fun fail(message: String): Nothing {
            log.error(message)
            throw GradleException(message)
        }

        /** Names by argument index. Slots differ: `this` takes slot 0 and long/double take two. */
        private fun parameterNames(): List<String>? {
            val argTypes = Type.getArgumentTypes(desc)
            parameters?.map { it.name }?.takeIf { it.size == argTypes.size && null !in it }?.let { return it }
            val vars = localVariables ?: return null
            var slot = if (access and Opcodes.ACC_STATIC != 0) 0 else 1
            return argTypes.map { type ->
                // A parameter's entry starts with the method; a later local may reuse its slot.
                val name = vars.filter { it.index == slot }.minByOrNull { instructions.indexOf(it.start) }?.name ?: return null
                slot += type.size
                name
            }
        }

        private fun isSuspend(): Boolean = Type.getArgumentTypes(desc).lastOrNull()?.internalName == CONTINUATION

        /**
         * The continuation class of a suspend method, read from the prologue Kotlin compiles into it:
         * `cont instanceof Owner$method$N && (cont.label & MIN_VALUE) != 0` means it is resuming.
         * The `$N` suffix depends on the other classes the method declares, so it is not guessed.
         */
        private fun stateMachineClass(): String? {
            if (!isSuspend()) return null
            val argTypes = Type.getArgumentTypes(desc)
            val slot = argTypes.dropLast(1).sumOf { it.size } + if (access and Opcodes.ACC_STATIC != 0) 0 else 1
            val owner = instructions.firstOrNull { insn ->
                insn is TypeInsnNode && insn.opcode == Opcodes.INSTANCEOF &&
                    insn.previousCode().let { it is VarInsnNode && it.opcode == Opcodes.ALOAD && it.`var` == slot }
            }?.let { (it as TypeInsnNode).desc } ?: return null
            val hasLabel = instructions.any { it is FieldInsnNode && it.owner == owner && it.name == LABEL && it.desc == "I" }
            return owner.takeIf { hasLabel }
        }

        private fun AbstractInsnNode.previousCode(): AbstractInsnNode? {
            var insn = previous
            while (insn != null && insn.opcode < 0) insn = insn.previous
            return insn
        }
    }
}

private class MappedCallAdvice(
    api: Int,
    next: MethodVisitor,
    access: Int,
    name: String,
    descriptor: String,
    private val specs: List<String>,
    private val stateMachine: String?,
) : AdviceAdapter(api, next, access, name, descriptor) {
    override fun onMethodEnter() {
        // A resuming suspend method is called again with null arguments and its own continuation; skip those calls.
        val skip = stateMachine?.let { owner ->
            val type = Type.getObjectType(owner)
            val run = newLabel()
            val skip = newLabel()
            loadArg(argumentTypes.lastIndex)
            instanceOf(type)
            ifZCmp(EQ, run)
            loadArg(argumentTypes.lastIndex)
            checkCast(type)
            getField(type, LABEL, Type.INT_TYPE)
            push(Int.MIN_VALUE)
            math(AND, Type.INT_TYPE)
            ifZCmp(NE, skip)
            mark(run)
            skip
        }
        specs.forEach { spec ->
            push(spec)
            if (methodAccess and Opcodes.ACC_STATIC != 0) visitInsn(Opcodes.ACONST_NULL) else loadThis()
            loadArgArray()
            invokeStatic(HOOKS_TYPE, ON_MAPPED_CALL)
        }
        skip?.let(::mark)
    }
}

private class VisitedHistoryAdvice(
    api: Int,
    next: MethodVisitor,
    access: Int,
    name: String,
    descriptor: String,
) : AdviceAdapter(api, next, access, name, descriptor) {
    override fun onMethodEnter() {
        loadArg(0)
        loadArg(1)
        invokeStatic(HOOKS_TYPE, ON_VISITED_HISTORY)
    }
}

/** `webView.addJavascriptInterface(bridge, name)` becomes `DariHooks.addJavascriptInterface(webView, bridge, name)`. */
private class CallSiteRewriter(
    api: Int,
    next: MethodVisitor,
    private val isWebView: (String) -> Boolean,
) : MethodVisitor(api, next) {
    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
        // INVOKESPECIAL (`super.addJavascriptInterface` in a subclass) stays, or the hook would call back into it.
        if (opcode == Opcodes.INVOKEVIRTUAL && name == ADD_JS_INTERFACE && descriptor == ADD_JS_INTERFACE_DESC && isWebView(owner)) {
            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOKS, name, "(L$WEB_VIEW;${descriptor.substring(1)}", false)
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }
}

internal interface DariLog {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}
