package com.v2ray.ang.ui.userasset

import android.app.UiAutomation
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.AssetUrlCache
import com.v2ray.ang.dto.entities.AssetUrlItem
import com.v2ray.ang.ui.main.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAssetRowAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun rowOwnsOrderedActionsWithoutAnActivationAction() {
        val locked = mutableStateOf(false)
        var edits = 0
        var deletes = 0
        val name = "Accessibility asset"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        Surface {
                            UserAssetItem(
                                item = AssetUrlCache(
                                    guid = "accessibility-asset",
                                    assetUrl = AssetUrlItem(
                                        remarks = name,
                                        url = "https://example.invalid/asset",
                                        locked = locked.value,
                                    ),
                                ),
                                fileMetadata = AssetFileMetadata(1_024, 1_700_000_000_000L),
                                onEdit = { edits++ },
                                onDeleteClick = { deletes++ },
                            )
                        }
                    }
                }
            }

            val editLabel = instrumentation.targetContext.getString(R.string.acc_edit_asset_named, name)
            val deleteLabel = instrumentation.targetContext.getString(R.string.acc_delete_asset_named, name)
            val row = awaitRow(editLabel)
            assertFalse(row.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
            assertEquals(listOf(editLabel, deleteLabel), row.actionList.mapNotNull { it.label?.toString() })
            row.actionList.filter { it.label != null }.forEach { assertTrue(row.performAction(it.id)) }
            assertEquals(1, edits)
            assertEquals(1, deletes)

            instrumentation.runOnMainSync { locked.value = true }
            val lockedRow = awaitRow(deleteLabel) { node ->
                node.actionList.mapNotNull { it.label?.toString() } == listOf(deleteLabel)
            }
            assertFalse(lockedRow.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        }
    }

    private fun awaitRow(
        actionLabel: String,
        predicate: (AccessibilityNodeInfo) -> Boolean = { true },
    ): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (SystemClock.uptimeMillis() < deadline) {
            nodes(automation.rootInActiveWindow).firstOrNull { node ->
                node.actionList.any { it.label?.toString() == actionLabel } && predicate(node)
            }?.let { return it }
            SystemClock.sleep(50)
        }
        throw AssertionError("Asset row did not reach the expected accessibility state")
    }

    private fun nodes(root: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> = sequence {
        if (root == null) return@sequence
        yield(root)
        repeat(root.childCount) { index -> yieldAll(nodes(root.getChild(index))) }
    }
}
