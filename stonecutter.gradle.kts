import java.util.Properties

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.3" // [ACTIVE_VERSION]

// Sodium's own API surface — not the Minecraft version — decides whether a build should target
// the old SodiumOptionsGUI mixin, the new sodium:config_api_user entrypoint, or (on versions where
// Sodium ships both a stable old-API line and a concurrent new-API beta line, e.g. 1.21.1) both at
// once. Source of truth is that version's own gradle.properties: `deps.sodium` (primary, dev/runtime
// pin) and the optional `deps.sodium_new_api` (a second, compile-only-only pin used just to expose
// the new API's classes when the primary pin predates them — see that key's comment).
fun sodiumPinIsNewApi(pin: String?): Boolean {
    val m = pin?.let { Regex("-(\\d+)\\.(\\d+)\\.\\d+").find(it) } ?: return false
    return m.groupValues[1].toInt() == 0 && m.groupValues[2].toInt() >= 8
}

fun sodiumPin(project: String, key: String): String? {
    val propsFile = file("versions/$project/gradle.properties")
    if (!propsFile.exists()) return null
    val props = Properties()
    propsFile.inputStream().use { props.load(it) }
    return props.getProperty(key)
}

stonecutter parameters {
    constants.put("UNOBFUSCATED", node.metadata.project.startsWith("26."))
    constants.put("SODIUM", true)
    // True if the new API's classes are available to compile against, from either pin.
    constants.put("SODIUM_NEW_API", sodiumPinIsNewApi(sodiumPin(node.metadata.project, "deps.sodium"))
        || sodiumPinIsNewApi(sodiumPin(node.metadata.project, "deps.sodium_new_api")))
    // True if the old API's classes are available — only the primary pin ever provides these.
    constants.put("SODIUM_OLD_API", !sodiumPinIsNewApi(sodiumPin(node.metadata.project, "deps.sodium")))
    // VulkanMod is an optional, opt-in renderer backend. Keep this set in sync with the versions
    // whose gradle.properties define `deps.vulkanmod` (that property drives the build.gradle
    // dependency + mixin-overlay gating; this constant drives the //? if VULKANMOD source guards).
    // 0.5.x API: 1.21.2-1.21.5. 0.6.x API: 1.21, 1.21.1, 1.21.9-1.21.11.
    // (1.21.6-1.21.8 absent — VulkanMod never released for them.)
    // 26.1.x (unobfuscated line) supported via VulkanMod 0.6.8 = CEmnv55N (covers 26.1/26.1.1/26.1.2).
    constants.put("VULKANMOD", node.metadata.project in setOf(
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
        "1.21.9", "1.21.10", "1.21.11",
        "26.1", "26.1.1", "26.1.2"
    ))
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
