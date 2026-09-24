package com.josemilk.kimicode.android.agent

import java.io.File

/** Android skill discovery: Markdown skill files live under the app workspace's .kimi/skills directory. */
class SkillManager(private val workspace: File) {
    data class Skill(val name: String, val description: String, val file: File, val content: String)

    fun discover(): List<Skill> {
        val root = File(workspace, ".kimi/skills")
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown().filter { it.isFile && it.name.equals("SKILL.md", true) }.mapNotNull { file ->
            val content = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            val name = file.parentFile?.name ?: return@mapNotNull null
            Skill(name, content.lineSequence().firstOrNull { it.startsWith("description:") }?.substringAfter(":")?.trim().orEmpty(), file, content)
        }.toList()
    }

    fun systemPromptAddition(): String = discover().joinToString("\n\n") { skill ->
        "## Skill: ${skill.name}\n${skill.content.take(20_000)}"
    }
}
