package com.newoether.agora.templates

import com.newoether.agora.skills.SkillDocument
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Validate the generated portable interface through the actual native format validators. */
class FoodPlannerPackTest {
    @Test fun generatedTemplateAndSkillUseNativeIdentityAndSizeContracts() {
        val directory=sequenceOf(File("../examples/interactive-templates/food-planner"), File("examples/interactive-templates/food-planner"))
            .first { it.isDirectory }
        val template=InteractiveTemplate.decode(File(directory,"food-planner.agora-template.json").readText())
        val skill=SkillDocument.decode("food-planner.md",File(directory,"food-planner.md").readText())
        assertEquals(template.id,skill.configuration.beforeTemplate)
        assertEquals(template.id,skill.configuration.afterTemplate)
        assertNull(skill.configuration.nextSkill)
        assertFalse(template.sourceFilesAuto)
        assertTrue(template.resourceFiles.isEmpty())
        assertTrue(template.answer.eInkAware)
        assertFalse(template.answer.jsFunctions.contains("fetch("))
        assertFalse(template.answer.jsFunctions.contains("apiKey"))
        assertFalse(template.answer.jsFunctions.contains("sourceFiles:"))
    }
}
