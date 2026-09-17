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

package com.jnd.ngdroid.agent

import com.jnd.ngdroid.ui.assistant.extractFileLinks
import com.jnd.ngdroid.ui.assistant.extractLocalFileNames
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileToolsTest {

    @Test
    fun sanitizeStripsPathsAndAddsExtension() {
        assertEquals("tl494.pdf", sanitizeFileName("../../tl494.pdf"))
        assertEquals("doc.pdf", sanitizeFileName("doc", "application/pdf"))
        assertEquals("a.png", sanitizeFileName("a", "image/png"))
        assertTrue(sanitizeFileName("").isNotEmpty())
        // Length cap keeps extension.
        val long = "x".repeat(100)
        val capped = sanitizeFileName("$long.pdf")
        assertTrue(capped.length <= 80 && capped.endsWith(".pdf"))
    }

    @Test
    fun fileNameForDownloadPrefersHintThenUrl() {
        assertEquals(
            "custom.pdf",
            fileNameForDownload("https://example.com/x", "custom.pdf", "application/pdf")
        )
        assertEquals(
            "ne555.pdf",
            fileNameForDownload("https://www.ti.com/lit/ds/symlink/ne555.pdf", "", "")
        )
    }

    @Test
    fun downloadRoundTripSavesAndLists() = runTest {
        val store = InMemoryFileStore()
        val tool = DownloadFileTool(
            httpBytes = { url, _ -> FetchedFile("hello".toByteArray(), "text/plain", url) },
            store = store
        )
        val out = tool.execute("""{"url":"https://example.com/notes.txt"}""")
        assertTrue("notes.txt" in out)
        assertTrue("Downloads/NGDroid" in out)
        assertTrue("read_file" in out)
        assertEquals(1, store.list().size)
    }

    @Test
    fun downloadRejectsNonHttp() = runTest {
        val tool = DownloadFileTool(
            httpBytes = { _, _ -> FetchedFile(ByteArray(0), "", "") },
            store = InMemoryFileStore()
        )
        assertTrue(tool.execute("""{"url":"ftp://example.com/x.pdf"}""").startsWith("ERROR:"))
    }

    @Test
    fun readTextFileReturnsExcerpt() = runTest {
        val store = InMemoryFileStore()
        store.save("notes.txt", "line1\nline2\nline3".toByteArray(), "text/plain")
        val tool = ReadFileTool(store)
        val out = tool.execute("""{"fileName":"notes.txt"}""")
        assertTrue("line1" in out && "Downloads/NGDroid" in out)
    }

    @Test
    fun readBlankListsFiles() = runTest {
        val store = InMemoryFileStore()
        assertTrue("No downloaded files" in ReadFileTool(store).execute("{}"))
        store.save("a.pdf", "%PDF-1.4".toByteArray(), "application/pdf")
        assertTrue("a.pdf" in ReadFileTool(store).execute("{}"))
    }

    @Test
    fun readMissingSuggestsList() = runTest {
        val store = InMemoryFileStore()
        store.save("a.txt", "hi".toByteArray(), "text/plain")
        val out = ReadFileTool(store).execute("""{"fileName":"nope.pdf"}""")
        assertTrue(out.startsWith("ERROR:") && "a.txt" in out)
    }

    @Test
    fun readImageReportsMetadata() = runTest {
        val store = InMemoryFileStore()
        // Minimal PNG: signature + IHDR with 2x3 dimensions.
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52,
            0, 0, 0, 2, 0, 0, 0, 3, 8, 2, 0, 0, 0
        )
        store.save("pinout.png", png, "image/png")
        val out = ReadFileTool(store).execute("""{"fileName":"pinout.png"}""")
        assertTrue("PNG 2x3" in out && "Downloads/NGDroid" in out)
    }

    @Test
    fun pdfSnippetExtractsParenthesizedText() {
        val pdf = ("%PDF-1.4\n1 0 obj\n<< /Type /Page /Count 2 >>\n" +
            "BT (Hello TL494) Tj ET\nBT (PWM control) Tj ET\n").toByteArray(Charsets.ISO_8859_1)
        val out = extractPdfTextSnippet(pdf)
        assertTrue("~2 page" in out)
        assertTrue("Hello TL494" in out && "PWM control" in out)
    }

    @Test
    fun extractFileLinksFindsDocsNotImages() {
        val text = "Datasheet [tl494](https://example.com/tl494.pdf) and " +
            "https://example.com/x.zip plus ![pin](https://example.com/p.png)"
        val links = extractFileLinks(text)
        assertEquals(2, links.size)
        assertEquals("tl494.pdf", links[0].fileName)
        assertTrue(links.any { it.url.endsWith("x.zip") })
        assertFalse(links.any { it.url.endsWith("p.png") })
    }

    @Test
    fun extractLocalNamesFindsSavedPaths() {
        val names = extractLocalFileNames("Saved to Downloads/NGDroid/tl494.pdf, open it.")
        assertEquals(listOf("tl494.pdf"), names)
        assertTrue(extractLocalFileNames("no files here").isEmpty())
    }

    @Test
    fun payloadCountsSavedFiles() {
        assertTrue(finalHasPayload("Saved tl494.pdf to Downloads/NGDroid/tl494.pdf"))
        assertFalse(isPromiseWithoutPayload("Saved tl494.pdf to Downloads/NGDroid/tl494.pdf"))
    }
}
