package com.v2ray.ang.ui.main

import android.app.UiAutomation
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.ui.compose.ReorderCommand
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerRowAccessibilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val selected = mutableStateOf(false)
    private val doubleColumn = mutableStateOf(false)
    private val profileType = mutableStateOf(EConfigType.VMESS)
    private val dispatched = mutableListOf<MainAction>()
    private val selectedGuids = mutableListOf<String>()
    private val removalRequests = mutableListOf<Pair<String, String>>()
    private val menus = mutableListOf<String>()
    private val moves = mutableListOf<Pair<String, ReorderCommand>>()
    private val guid = "accessibility-fixture-guid"
    private val name = "Accessibility fixture"

    @Before
    fun showRow() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                MaterialTheme {
                    Surface {
                        Column {
                            key(guid) {
                                ServerListItem(
                                    row = ServerRowUiModel(
                                        guid = guid,
                                        profile = ProfileItem(configType = profileType.value, remarks = name),
                                        remarks = name,
                                        statistics = "Fixture description",
                                        typeDescription = profileType.value.name,
                                        testDelayMillis = 21,
                                        subscriptionBadge = "",
                                    ),
                                    isSelected = selected.value,
                                    doubleColumnDisplay = doubleColumn.value,
                                    reorderIndex = 1,
                                    itemCount = 3,
                                    actions = ServerRowActions(
                                        select = selectedGuids::add,
                                        onAction = dispatched::add,
                                        share = { _, _ -> menus.add("share") },
                                        more = { _, _ -> menus.add("more") },
                                        remove = { id, memberName -> removalRequests.add(id to memberName) },
                                        move = { itemGuid, command -> moves.add(itemGuid to command) },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        awaitRow()
    }

    @After
    fun closeFixture() {
        scenario.close()
    }

    @Test
    fun rowIsOneNativeClickTargetWithOrderedActionsInBothLayouts() {
        for (columns in listOf(false, true)) {
            update { doubleColumn.value = columns }
            val node = awaitRow()
            assertTrue(node.isClickable)
            assertFalse(node.isCheckable)
            assertFalse(node.isSelected)
            assertEquals("android.view.View", node.className.toString())
            assertNull(AccessibilityNodeInfoCompat.wrap(node).stateDescription)
            assertEquals(0, node.childCount)
            assertEquals(expectedLabels(complex = false), node.actionList.mapNotNull { it.label?.toString() })
            assertEquals(1, nodes().count { it.contentDescription?.contains(name) == true })
        }
    }

    @Test
    fun selectedPrefixDoesNotDependOnConnectionStateAndKeepsNodeIdentity() {
        val original = awaitRow()
        assertTrue(original.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        assertFalse(original.contentDescription.toString().startsWith(selectedPrefix()))
        update { selected.value = true }
        awaitRow { it.contentDescription.toString().startsWith("${selectedPrefix()}. $name") }
        assertEquals(original, awaitRow())
        assertTrue(awaitRow().isAccessibilityFocused)
        SystemClock.sleep(1000)
        update { selected.value = false }
        awaitRow { !it.contentDescription.toString().startsWith(selectedPrefix()) }
        update { selected.value = true }
        awaitRow { it.contentDescription.toString().startsWith("${selectedPrefix()}. $name") }
    }

    @Test
    fun customActionsDispatchOriginalOperationsByGuid() {
        val node = awaitRow()
        node.actionList.filter { it.label != null }.forEach {
            assertTrue(node.performAction(it.id))
            instrumentation.waitForIdleSync()
        }
        assertEquals(listOf(guid to name), removalRequests)
        assertEquals(
            listOf(
                MainAction.EditServer::class,
                MainAction.ShareQRCode::class,
                MainAction.ShareClipboard::class,
                MainAction.ShareFullContent::class,
            ),
            dispatched.map { it::class },
        )
        assertEquals(guid, (dispatched.first() as MainAction.EditServer).guid)
        assertEquals(guid, (dispatched[1] as MainAction.ShareQRCode).guid)
        assertEquals(guid, (dispatched[2] as MainAction.ShareClipboard).guid)
        assertEquals(guid, (dispatched[3] as MainAction.ShareFullContent).guid)
        assertTrue(selectedGuids.isEmpty())
        assertTrue(menus.isEmpty())
        assertEquals(ReorderCommand.entries.map { guid to it }, moves)
    }

    @Test
    fun complexProfilesExposeOnlySupportedActions() {
        for (type in listOf(EConfigType.CUSTOM, EConfigType.POLICYGROUP, EConfigType.PROXYCHAIN)) {
            update { profileType.value = type }
            awaitRow { it.actionList.mapNotNull { action -> action.label?.toString() } == expectedLabels(complex = true) }
        }
    }

    @Test
    fun visibleButtonsRemainKeyboardOperableOutsideAccessibilityTraversal() {
        assertTrue(awaitRow().performAction(AccessibilityNodeInfo.ACTION_FOCUS))
        repeat(3) {
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
            instrumentation.waitForIdleSync()
        }
        assertEquals(listOf("share"), menus)
        assertEquals(listOf(guid to name), removalRequests)
        assertEquals(guid, (dispatched.single() as MainAction.EditServer).guid)
        assertTrue(selectedGuids.isEmpty())
    }

    @Test
    fun accessibilityTouchAndKeyboardUseTheSameActivation() {
        assertTrue(awaitRow().performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
        assertEquals(listOf(guid), selectedGuids)

        val bounds = Rect().also { awaitRow().getBoundsInScreen(it) }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), (bounds.bottom - 10).toFloat(), 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        instrumentation.waitForIdleSync()
        assertEquals(2, selectedGuids.size)
        for (keyCode in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertTrue(awaitRow().performAction(AccessibilityNodeInfo.ACTION_FOCUS))
            instrumentation.sendKeyDownUpSync(keyCode)
            instrumentation.waitForIdleSync()
        }
        assertEquals(List(5) { guid }, selectedGuids)
    }

    private fun selectedPrefix() = instrumentation.targetContext.getString(R.string.acc_selected_server)

    private fun expectedLabels(complex: Boolean): List<String> = with(instrumentation.targetContext) {
        buildList {
            add(getString(R.string.acc_edit_config_named, name))
            add(getString(R.string.acc_delete_config_named, name))
            if (!complex) {
                add(getString(R.string.share_method_qrcode))
                add(getString(R.string.share_method_clipboard))
            }
            add(getString(R.string.share_method_full_content))
            addAll(ReorderCommand.entries.map { getString(it.labelRes) })
        }
    }

    private fun update(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        automation.waitForIdle(100, 5000)
    }

    private fun awaitRow(predicate: (AccessibilityNodeInfo) -> Boolean = { true }): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 5000
        do {
            nodes().firstOrNull { it.contentDescription?.contains(name) == true && predicate(it) }?.let { return it }
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Server row did not reach the expected state in the native accessibility tree")
    }

    private fun nodes(): List<AccessibilityNodeInfo> = buildList {
        fun visit(node: AccessibilityNodeInfo) {
            add(node)
            repeat(node.childCount) { index -> node.getChild(index)?.let(::visit) }
        }
        automation.rootInActiveWindow?.let(::visit)
    }
}
