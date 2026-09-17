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

package com.jnd.ngdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomSkillsTest {

    @Test
    fun validateRejectsShort() {
        assertNotNull(validateCustomSkill("A", "Long enough description here", "Do things properly here"))
        assertNotNull(validateCustomSkill("Ok", "Desc ok here!", "short"))
        assertNotNull(validateCustomSkill("Power", "short", "Always explain resistor power ratings clearly"))
        assertNull(validateCustomSkill("Power", "Use when explaining power ratings", "Always explain resistor power ratings clearly"))
    }

    @Test
    fun codecRoundTrips() {
        val skills = listOf(
            CustomSkill(id = "1", name = "Power", description = "Use for power", instructions = "Explain power here!", enabled = true),
            CustomSkill(id = "2", name = "Off", description = "Hidden skill desc", instructions = "Skip this body text", enabled = false)
        )
        val decoded = CustomSkillCodec.decode(CustomSkillCodec.encode(skills))
        assertEquals(2, decoded.size)
        assertEquals("Power", decoded[0].name)
        assertEquals("Use for power", decoded[0].description)
        assertFalse(decoded[1].enabled)
    }

    @Test
    fun codecBackCompatMissingDescription() {
        val raw = """[{"id":"1","name":"Power","instructions":"Explain power here!","enabled":true}]"""
        val decoded = CustomSkillCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].description.isNotBlank())
    }

    @Test
    fun catalogPromptCarriesDescriptionsNotBodies() {
        val base = "BASE"
        assertEquals(base, skillCatalogPrompt(base, emptyList()))
        val body = "Step 1 do X. Step 2 do Y with full secret procedure."
        val out = skillCatalogPrompt(
            base,
            listOf(
                CustomSkill(id = "1", name = "Power", description = "Use when explaining power", instructions = body, enabled = true),
                CustomSkill(id = "2", name = "Off", description = "Hidden desc here", instructions = "Hidden body text!", enabled = false)
            )
        )
        assertTrue(out.startsWith(base))
        assertTrue("Power" in out)
        assertTrue("Use when explaining power" in out)
        assertFalse(body in out)
        assertFalse("Hidden body" in out)
        assertTrue("read_skill" in out)
    }

    @Test
    fun skillMarkdownRoundTrips() {
        val s = CustomSkill(id = "1", name = "Power Ratings", description = "Use when explaining power", instructions = "Always explain power clearly here.", enabled = true)
        assertEquals("power-ratings", s.slug)
        val md = s.toSkillMarkdown()
        assertTrue(md.startsWith("---"))
        assertTrue("name: power-ratings" in md)
        val parsed = parseSkillMarkdown(md)
        assertNotNull(parsed)
        assertEquals("power-ratings", parsed!!.first.ifEmpty { "x" }.ifEmpty { "y" }.let { skillSlug(parsed.first) })
        assertTrue(parsed.third.contains("Always explain"))
    }

    @Test
    fun agentSettingsSkillToggle() {
        val s = AgentSettings()
        assertTrue(s.isSkillEnabled("web_search"))
        val off = s.withSkill("web_search", false)
        assertFalse(off.isSkillEnabled("web_search"))
        assertTrue(off.isSkillEnabled("fetch_url"))
        assertEquals(12, AgentSettings().coercedMaxIterations())
        assertEquals(20, AgentSettings(maxIterations = 99).coercedMaxIterations())
        assertEquals(4, AgentSettings(maxIterations = 1).coercedMaxIterations())
        assertEquals(0, AgentSettings(stubRetries = -1).coercedStubRetries())
    }

    @Test
    fun builtinCatalogCoversTools() {
        assertTrue(BUILTIN_SKILL_IDS.containsAll(listOf("web_search", "fetch_url", "read_file", "validate_netlist", "run_simulation", "render_plot")))
        assertEquals(11, BUILTIN_SKILLS.size)
    }

    @Test
    fun previewMarkdownMatchesSkillShape() {
        val md = previewSkillMarkdown(
            "Power Ratings",
            "Use when explaining power",
            "Always explain power clearly here."
        )
        assertTrue(md.startsWith("---"))
        assertTrue("name: power-ratings" in md)
        assertTrue("# Power Ratings" in md)
        assertTrue("Always explain power clearly" in md)
        // Draft-tolerant: blanks still render a preview, never crash.
        val draft = previewSkillMarkdown("", "", "")
        assertTrue(draft.startsWith("---"))
        assertTrue("Untitled skill" in draft)
    }
}
