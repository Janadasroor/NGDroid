/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the launcher icon against silent loss.
 *
 * Regression: renaming `mipmap-anydpi-v26` to plain `mipmap-anydpi`
 * (ObsoleteSdkInt lint advice) dropped the `<adaptive-icon>` XML from the
 * APK entirely, so devices fell back to the legacy robot webp. Adaptive
 * icons MUST live in the `-v26` folder; this test fails the build otherwise.
 */
class IconResourcesTest {

    private fun resDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("res dir not found from ${File(".").absolutePath}")
    }

    @Test
    fun adaptiveIconInV26Folder() {
        val res = resDir()
        for (name in listOf("ic_launcher.xml", "ic_launcher_round.xml")) {
            val file = File(res, "mipmap-anydpi-v26/$name")
            assertTrue("$name missing from mipmap-anydpi-v26", file.isFile)
            val text = file.readText()
            assertTrue("$name is not an adaptive icon", "<adaptive-icon" in text)
            // Every referenced drawable must exist.
            Regex("""@drawable/([A-Za-z0-9_]+)""").findAll(text).forEach { m ->
                val drawable = File(res, "drawable/${m.groupValues[1]}.xml")
                assertTrue("missing drawable ${m.groupValues[1]} referenced by $name", drawable.isFile)
            }
        }
    }

    @Test
    fun noAdaptiveIconInUnqualifiedAnydpi() {
        val dir = File(resDir(), "mipmap-anydpi")
        if (!dir.isDirectory) return
        dir.listFiles { f -> f.name.startsWith("ic_launcher") }.orEmpty().forEach { f ->
            assertFalse(
                "${f.name} must not be an adaptive icon in unqualified mipmap-anydpi " +
                    "(aapt drops it from the APK; use mipmap-anydpi-v26)",
                "<adaptive-icon" in f.readText()
            )
        }
    }

    @Test
    fun manifestIconTargetExists() {
        val res = resDir()
        val manifest = File(res, "../AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml not found", manifest.isFile)
        val text = manifest.readText()
        assertTrue("manifest must reference @mipmap/ic_launcher", "@mipmap/ic_launcher" in text)
        assertTrue(
            "manifest icon target missing",
            File(res, "mipmap-anydpi-v26/ic_launcher.xml").isFile
        )
    }
}
