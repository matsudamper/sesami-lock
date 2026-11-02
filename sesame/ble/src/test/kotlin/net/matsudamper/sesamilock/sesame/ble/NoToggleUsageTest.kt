package net.matsudamper.sesamilock.sesame.ble

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoToggleUsageTest {
    @Test
    fun productionSourcesNeverCallToggle() {
        val projectRoot = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val sourceRoots = listOf("app", "sesame").map { File(projectRoot, it) }
        val sources = sourceRoots.flatMap { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { "${File.separator}src${File.separator}main${File.separator}" in it.path }
                .toList()
        }
        assertTrue(sources.isNotEmpty())

        val violations = sources.filter { file ->
            val text = file.readText()
            Regex("""\.toggle\s*\(""").containsMatchIn(text) || Regex("""\b88\b""").containsMatchIn(text)
        }

        assertEquals(listOf(), violations.map { it.relativeTo(projectRoot).path })
    }
}
