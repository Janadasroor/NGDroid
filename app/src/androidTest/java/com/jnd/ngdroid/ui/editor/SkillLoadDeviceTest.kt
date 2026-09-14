package com.jnd.ngdroid.ui.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jnd.ngdroid.agent.ReadSkillTool
import com.jnd.ngdroid.data.AgentDataStore
import com.jnd.ngdroid.data.CustomSkillCodec
import com.jnd.ngdroid.data.newCustomSkill
import com.jnd.ngdroid.data.skillCatalogPrompt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device skills check: DataStore persistence + progressive-disclosure
 * loading (catalog carries descriptions only, read_skill loads the body).
 */
@RunWith(AndroidJUnit4::class)
class SkillLoadDeviceTest {

    @Test
    fun customSkillsPersistAndLoadProgressively(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = AgentDataStore(context)
        val skills = listOf(
            newCustomSkill(
                "Power Ratings",
                "Use when explaining resistor power or picking parts",
                "Always explain resistor power ratings step by step. Body marker DEVICE-BODY-42."
            )
        )
        store.setCustomSkills(skills)
        store.setSkill("web_search", false)
        store.setMaxIterations(9)

        val loaded = withTimeout(10_000) {
            store.settingsFlow.first { it.customSkills.isNotEmpty() }
        }
        assertEquals(1, loaded.customSkills.size)
        assertEquals("Power Ratings", loaded.customSkills[0].name)
        assertFalse(loaded.isSkillEnabled("web_search"))
        assertTrue(loaded.isSkillEnabled("fetch_url"))
        assertEquals(9, loaded.maxIterations)

        // Stage 1 Discovery: catalog has the description, never the body.
        val catalog = skillCatalogPrompt("BASE", loaded.enabledCustomSkills())
        assertTrue(catalog.startsWith("BASE"))
        assertTrue("Use when explaining resistor power" in catalog)
        assertTrue("read_skill" in catalog)
        assertFalse("DEVICE-BODY-42" in catalog)

        // Stage 2 Activation: read_skill loads the full body on demand.
        val tool = ReadSkillTool { loaded.enabledCustomSkills() }
        val body = tool.execute("""{"skill":"power-ratings"}""")
        assertTrue("DEVICE-BODY-42" in body)

        // Codec sanity on device too.
        val roundTrip = CustomSkillCodec.decode(CustomSkillCodec.encode(loaded.customSkills))
        assertEquals(1, roundTrip.size)
        assertEquals("Power Ratings", roundTrip[0].name)

        // Restore defaults so manual testing is unaffected.
        store.setCustomSkills(emptyList())
        store.setSkill("web_search", true)
        store.setMaxIterations(12)
    }
}
