/*
 * Javalin - https://javalin.io
 * Copyright 2017 David Åse
 * Licensed under Apache 2.0: https://github.com/tipsy/javalin/blob/master/LICENSE
 */

package io.javalin.core.util

import io.javalin.InternalServerErrorResponse
import io.javalin.Javalin
import io.javalin.core.JavalinServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.zip.Adler32
import java.util.zip.CheckedInputStream
import javax.servlet.http.HttpServletResponse

object Util {

    @JvmStatic
    fun normalizeContextPath(contextPath: String) = ("/$contextPath").replace("/{2,}".toRegex(), "/").removeSuffix("/")

    @JvmStatic
    fun prefixContextPath(contextPath: String, path: String) = if (path == "*") path else ("$contextPath/$path").replace("/{2,}".toRegex(), "/")

    private fun classExists(className: String) = try {
        Class.forName(className)
        true
    } catch (e: ClassNotFoundException) {
        false
    }

    private val dependencyCheckCache = HashMap<String, Boolean>()

    fun ensureDependencyPresent(dependency: OptionalDependency) {
        if (dependencyCheckCache[dependency.testClass] == true) {
            return
        }
        if (!classExists(dependency.testClass)) {
            val message = missingDependencyMessage(dependency)
            Javalin.log.warn(message)
            throw InternalServerErrorResponse(message)
        }
        dependencyCheckCache[dependency.testClass] = true
    }

    internal fun missingDependencyMessage(dependency: OptionalDependency) = """
            |Missing dependency '${dependency.displayName}'. Add the dependency.
            |
            |pom.xml:
            |<dependency>
            |    <groupId>${dependency.groupId}</groupId>
            |    <artifactId>${dependency.artifactId}</artifactId>
            |    <version>${dependency.version}</version>
            |</dependency>
            |
            |build.gradle:
            |compile "${dependency.groupId}:${dependency.artifactId}:${dependency.version}"""".trimMargin()

    fun pathToList(pathString: String): List<String> = pathString.split("/").filter { it.isNotEmpty() }

    @JvmStatic
    fun printHelpfulMessageIfLoggerIsMissing() {
        if (!classExists(OptionalDependency.SLF4JSIMPLE.testClass)) {
            System.err.println("""
            |-------------------------------------------------------------------
            |${missingDependencyMessage(OptionalDependency.SLF4JSIMPLE)}
            |-------------------------------------------------------------------
            |Visit https://javalin.io/documentation#logging if you need more help""".trimMargin())
        }
    }

    @JvmStatic
    fun logJavalinBanner(showBanner: Boolean) {
        if (showBanner) Javalin.log.info("\n" + """
          |           __                      __ _
          |          / /____ _ _   __ ____ _ / /(_)____
          |     __  / // __ `/| | / // __ `// // // __ \
          |    / /_/ // /_/ / | |/ // /_/ // // // / / /
          |    \____/ \__,_/  |___/ \__,_//_//_//_/ /_/
          |
          |        https://javalin.io/documentation
          |""".trimMargin())
    }

    fun getChecksumAndReset(inputStream: InputStream): String {
        val cis = CheckedInputStream(inputStream, Adler32())
        val out = ByteArrayOutputStream()
        cis.copyTo(out)
        inputStream.reset()
        return cis.checksum.value.toString()
    }

    fun getResourceUrl(path: String): URL? = this.javaClass.classLoader.getResource(path)

    fun getFileUrl(path: String): URL? = if (File(path).exists()) File(path).toURI().toURL() else null

    fun isKotlinClass(clazz: Class<*>): Boolean {
        try {
            for (annotation in clazz.declaredAnnotations) {
                // Note: annotation.simpleClass can be used if kotlin-reflect is available.
                if (annotation.annotationClass.toString().contains("kotlin.Metadata")) {
                    return true
                }
            }
        } catch (ignored: Exception) {
        }
        return false
    }

    fun writeResponse(response: HttpServletResponse, responseBody: String, status: Int) {
        response.status = status
        ByteArrayInputStream(responseBody.toByteArray()).copyTo(response.outputStream)
        response.outputStream.close()
    }

    @JvmStatic
    fun logWarningIfNotStartedAfterOneSecond(server: JavalinServer) = Thread {
        Thread.sleep(2000)
        if (!server.started) {
            Javalin.log.info("It looks like you created a Javalin instance, but you never started it.")
            Javalin.log.info("Try: Javalin app = Javalin.create().start();")
            Javalin.log.info("For more help, visit https://javalin.io/documentation#starting-and-stopping")
        }
    }.start()

}
