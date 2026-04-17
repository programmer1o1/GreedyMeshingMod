plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.3" // [ACTIVE_VERSION]

stonecutter parameters {
    constants.put("UNOBFUSCATED", node.metadata.project.startsWith("26."))
    constants.put("SODIUM", true)
}

// Register version switch tasks
for (ver in stonecutter.versions) {
    val taskName = "stonecutterSwitchTo${ver.project}"
    if (tasks.findByName(taskName) == null) {
        tasks.register(taskName) {
            group = "stonecutter"
            description = "Switch active version to ${ver.project}"
            doLast {
                val marker = "[ACTIVE_VERSION]"
                val script = project.file("stonecutter.gradle.kts")
                val lines = script.readLines().toMutableList()
                for (i in lines.indices) {
                    if (lines[i].contains(marker)) {
                        lines[i] = "stonecutter active \"${ver.project}\" // $marker"
                        break
                    }
                }
                script.writeText(lines.joinToString("\n") + "\n")
                println("Switched active version to ${ver.project}. Reload Gradle to apply.")
            }
        }
    }
}
