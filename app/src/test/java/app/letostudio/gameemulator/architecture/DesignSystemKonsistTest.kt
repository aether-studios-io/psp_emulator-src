package app.letostudio.gameemulator.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Design-system enforcement (design_system.md §7): screens are built ONLY from
 * ui/components/ — raw Material 3 widgets for covered patterns may appear nowhere
 * else under ui/. Layering: ui/ never touches Room or Retrofit directly.
 */
class DesignSystemKonsistTest {

    // Call-site substrings that identify a raw M3 widget regardless of import style.
    private val forbiddenCalls = listOf(
        "material3.Button(",
        "FilledTonalButton(",
        "OutlinedTextField(",
        "FilterChip(",
        "material3.Slider(",
        "material3.Badge(",
        "AlertDialog(",
        "DropdownMenu(",
        "DropdownMenuItem(",
    )

    // Imports that give a file direct access to a covered raw widget under a bare name.
    private val forbiddenImports = listOf(
        "androidx.compose.material3.Button",
        "androidx.compose.material3.FilledTonalButton",
        "androidx.compose.material3.OutlinedTextField",
        "androidx.compose.material3.FilterChip",
        "androidx.compose.material3.Slider",
        "androidx.compose.material3.Badge",
        "androidx.compose.material3.AlertDialog",
        "androidx.compose.material3.DropdownMenu",
        "androidx.compose.material3.DropdownMenuItem",
    )

    private fun uiFilesOutsideComponents() = Konsist
        .scopeFromProject()
        .files
        .filter { it.path.replace('\\', '/').contains("/ui/") }
        .filter { it.packagee?.name?.contains("ui.components") != true }

    @Test
    fun `raw material components only inside ui-components`() {
        uiFilesOutsideComponents().assertFalse { file ->
            forbiddenCalls.any { raw -> file.text.contains(raw) } ||
                file.imports.any { import -> import.name in forbiddenImports }
        }
    }

    @Test
    fun `ui does not import data-local or data-remote directly`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.packagee?.name?.contains(".ui") == true }
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("app.letostudio.gameemulator.data.local") ||
                        import.name.startsWith("app.letostudio.gameemulator.data.remote")
                }
            }
    }

    // Analytics is logged from ViewModels/managers, never from composables (FR-018,
    // specs/006-analytics). AppNavHost is the ONE named exception: screen_view
    // tracking spans destinations and has no ViewModel that could own it (R5).
    // ViewModel files under ui/ are excluded — they are exactly where logging belongs.
    @Test
    fun `analytics stays out of ui composables except AppNavHost`() {
        uiFilesOutsideComponents()
            // Production sources only — test fixtures legitimately mock the managers.
            .filter { it.path.replace('\\', '/').contains("/src/main/") }
            .filterNot { it.name == "AppNavHost" || it.name == "AppNavHost.kt" }
            .filterNot { it.name.endsWith("ViewModel") || it.name.endsWith("ViewModel.kt") }
            .assertFalse { file ->
                file.imports.any { import ->
                    import.name.startsWith("com.google.firebase.analytics") ||
                        import.name.startsWith("app.letostudio.gameemulator.analytics.")
                }
            }
    }

    // The Crashlytics SDK lives in crash/ (the consent-gated manager) and di/ (its
    // provider) only. Everything else injects the CrashReporter seam, so no call site
    // can bypass the consent gate, the routine-failure filter or message redaction.
    @Test
    fun `crashlytics sdk stays inside crash and di`() {
        Konsist
            .scopeFromProject()
            .files
            .filter { it.path.replace('\\', '/').contains("/src/main/") }
            .filterNot {
                val pkg = it.packagee?.name.orEmpty()
                pkg.endsWith(".crash") || pkg.endsWith(".di")
            }
            .assertFalse { file ->
                file.imports.any { it.name.startsWith("com.google.firebase.crashlytics") }
            }
    }
}
