package io.specmatic.gradle.exec

import io.specmatic.gradle.pluginDebug
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractExecTask
import org.gradle.api.tasks.JavaExec
import org.gradle.process.BaseExecSpec

class ConfigureExecTask(project: Project) {

    init {
        project.allprojects.forEach(::configure)
    }

    fun configure(project: Project) {
        project.afterEvaluate {
            project.tasks.whenObjectAdded {
                if (this is AbstractExecTask<*>) {
                    configureTask(this)
                }

                if (this is JavaExec) {
                    configureTask(this)
                }
            }

            project.tasks.withType(AbstractExecTask::class.java) {
                configureTask(this)
            }

            project.tasks.withType(JavaExec::class.java) {
                configureTask(this)
            }
        }
    }

    private fun configureTask(task: Task) {
        if (task is BaseExecSpec) {
            if (task.inputs.properties["specmatic.exec.configured"] == true) {
                return
            }
            pluginDebug("Configuring exec task ${task.path}")
            task.inputs.property("specmatic.exec.configured", true)
            task.apply {
                standardOutput = System.out
                errorOutput = System.err
                doFirst {
                    val cliArgs = mutableListOf<String>()
                    if (task is JavaExec) {
                        cliArgs.add("java")
                        if (task.jvmArgs.isNotEmpty()) {
                            cliArgs.addAll(task.jvmArgs)
                        }
                        cliArgs.add("-cp")
                        cliArgs.add(task.classpath.joinToString(":"))
                        if (task.mainClass.isPresent) {
                            cliArgs.add(task.mainClass.get())
                        }
                        cliArgs.addAll(task.args)
                    } else {
                        cliArgs.addAll(task.commandLine)
                    }

                    pluginDebug("[${workingDir}]\$ ${shellEscapedArgs(cliArgs)}")
                }
            }
        }
    }

}

fun shellEscapedArgs(args: List<String?>): String {
    val escapedArgs = args.filterNotNull().map { shellEscape(it) }
    return buildString {
        escapedArgs.forEachIndexed { index, arg ->
            if (index > 0) {
                append(" \\\n   ")
            }
            append(arg)
        }
    }
}

val SAFE_PUNCTUATION: String = "@%-_+:,./"

fun shellEscape(word: String): String {
    val len = word.length
    if (len == 0) {
        // Empty string is a special case: needs to be quoted to ensure that it gets
        // treated as a separate argument.
        return "''"
    }
    for (ii in 0 until len) {
        val c = word[ii]
        // We do this positively so as to be sure we don't inadvertently forget
        // any unsafe characters.
        if (!Character.isLetterOrDigit(c) && SAFE_PUNCTUATION.indexOf(c) == -1) {
            // replace() actually means "replace all".
            return "'" + word.replace("'", "'\\''") + "'"
        }
    }
    return word
}
