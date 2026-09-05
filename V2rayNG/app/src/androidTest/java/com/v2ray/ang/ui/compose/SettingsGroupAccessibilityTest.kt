package com.v2ray.ang.ui.compose

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.ui.settings.SettingsActivity
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsGroupAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun groupHasOneNameFirstLabelAndKeepsItsNativeButtonAction() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            lateinit var title: String
            lateinit var expandedLabel: String
            lateinit var collapsedLabel: String
            scenario.onActivity { activity ->
                title = activity.getString(R.string.title_ui_settings)
                expandedLabel = activity.getString(R.string.acc_settings_group_expanded, title)
                collapsedLabel = activity.getString(R.string.acc_settings_group_collapsed, title)
            }

            val expanded = awaitGroup(expandedLabel)
            assertGroupSemantics(expanded, title)
            assertTrue(expanded.performAction(AccessibilityNodeInfo.ACTION_CLICK))

            val collapsed = awaitGroup(collapsedLabel)
            assertGroupSemantics(collapsed, title)
            assertTrue(collapsed.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertGroupSemantics(awaitGroup(expandedLabel), title)
        }
    }

    @Test
    fun everyLocalePlacesTheGroupNameOnceBeforeItsState() {
        val context = instrumentation.targetContext
        for (languageTag in listOf("en", "ar", "bn", "bqi-IR", "fa", "ru", "vi", "zh-CN", "zh-TW")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(languageTag))
            }
            val localizedContext = context.createConfigurationContext(configuration)
            val title = localizedContext.getString(R.string.title_ui_settings)
            for (resource in listOf(R.string.acc_settings_group_expanded, R.string.acc_settings_group_collapsed)) {
                val label = localizedContext.getString(resource, title)
                assertTrue("$languageTag: $label", label.startsWith(title))
                assertEquals("$languageTag repeats its group name", 0, label.lastIndexOf(title))
                assertTrue("$languageTag is missing the state", label.length > title.length)
            }
        }
    }

    private fun assertGroupSemantics(node: AccessibilityNodeInfo, title: String) {
        // Compose may expose a merged button's label and role through synthetic children.
        val groupNodes = nodes(node).toList()
        assertEquals(1, groupNodes.count { it.className == "android.widget.Button" })
        assertEquals(1, groupNodes.count { it.isClickable })
        assertEquals(1, groupNodes.count { it.contentDescription?.toString()?.contains(title) == true })
        assertFalse(groupNodes.any { it.isCheckable })
        assertTrue(groupNodes.all { AccessibilityNodeInfoCompat.wrap(it).stateDescription == null })
        assertFalse("The visible title must not duplicate the button label", groupNodes.any {
            it.text?.toString()?.contains(title) == true
        })
    }

    private fun awaitGroup(label: String): AccessibilityNodeInfo {
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes(automation.rootInActiveWindow).firstOrNull {
                it.isClickable && nodes(it).any { child -> child.contentDescription?.toString() == label }
            }?.let { return it }
            SystemClock.sleep(50)
        }
        val availableNodes = nodes(automation.rootInActiveWindow).joinToString("\n") {
            "${it.className}: clickable=${it.isClickable}, label=${it.contentDescription}, text=${it.text}"
        }
        throw AssertionError("No accessible group button labelled: $label\n$availableNodes")
    }

    private fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        yield(root)
        for (index in 0 until root.childCount) yieldAll(nodes(root.getChild(index)))
    }
}
