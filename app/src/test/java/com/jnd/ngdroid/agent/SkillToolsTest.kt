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

import com.jnd.ngdroid.data.CustomSkill
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillToolsTest {

    private val skills = listOf(
        CustomSkill(id = "1", name = "Power Ratings", description = "Use for power", instructions = "BODY-POWER-123", enabled = true),
        CustomSkill(id = "2", name = "Off Skill", description = "Disabled desc", instructions = "BODY-OFF", enabled = false)
    )

    @Test
    fun readsByNameAndSlug() = runTest {
        val tool = ReadSkillTool { skills }
        assertTrue(tool.execute("""{"skill":"Power Ratings"}""").contains("BODY-POWER-123"))
        assertTrue(tool.execute("""{"skill":"power-ratings"}""").contains("BODY-POWER-123"))
    }

    @Test
    fun disabledSkillsHidden() = runTest {
        val tool = ReadSkillTool { skills }
        val out = tool.execute("""{"skill":"Off Skill"}""")
        assertTrue("no skill matches" in out.lowercase() || "no custom skills" in out.lowercase())
    }

    @Test
    fun unknownSkillListsAvailable() = runTest {
        val tool = ReadSkillTool { skills }
        val out = tool.execute("""{"skill":"nope"}""")
        assertTrue("Power Ratings" in out)
    }
}
