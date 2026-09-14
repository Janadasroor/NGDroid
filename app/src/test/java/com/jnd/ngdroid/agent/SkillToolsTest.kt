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
