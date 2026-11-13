package com.v2ray.ang.ui.main

import androidx.compose.runtime.saveable.SaverScope
import com.v2ray.ang.ui.compose.DpadReorderActivation
import com.v2ray.ang.ui.compose.DpadReorderDirection
import com.v2ray.ang.ui.compose.DpadReorderState
import com.v2ray.ang.ui.compose.TvTextFieldRecoveryAction
import com.v2ray.ang.ui.compose.dpadRepeatReleaseGuardMillis
import com.v2ray.ang.ui.compose.tvTextFieldRecoveryAction
import com.v2ray.ang.ui.compose.twoColumnDpadReorderTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TvMainInteractionTest {
    @Test
    fun drawerOriginRestoresById() {
        val saver = TvMainFocusTarget.DrawerOrigin.Saver
        val scope = SaverScope { true }
        val targets = listOf(
            TvMainFocusTarget.Toolbar,
            TvMainFocusTarget.GroupTab("subscription-id"),
            TvMainFocusTarget.GroupTab(""),
            TvMainFocusTarget.Profile("profile-guid"),
            TvMainFocusTarget.Profile("profile-guid", ProfileRowAction.More)
        )
        for (target in targets) {
            val saved = with(saver) { scope.save(target) }
            assertEquals(target, saver.restore(checkNotNull(saved)))
        }
        assertNull(with(saver) { scope.save(null) })
    }

    @Test
    fun toolbarKeepsRestartAndOnlyShowsConnectivityTestWhenRunning() {
        assertEquals(
            listOf(TvToolbarAction.Start, TvToolbarAction.Search, TvToolbarAction.Add, TvToolbarAction.Restart, TvToolbarAction.More),
            TvToolbarAction.visible(isRunning = false)
        )
        assertEquals(
            listOf(TvToolbarAction.Start, TvToolbarAction.Test, TvToolbarAction.Search, TvToolbarAction.Add, TvToolbarAction.Restart, TvToolbarAction.More),
            TvToolbarAction.visible(isRunning = true)
        )
    }

    @Test
    fun profileReturnKeepsTheActionOrUsesTheEquivalentInTheNewLayout() {
        assertEquals(ProfileRowAction.Share, profileReturnAction(ProfileRowAction.Share, doubleColumn = false))
        assertEquals(ProfileRowAction.Edit, profileReturnAction(ProfileRowAction.Edit, doubleColumn = false))
        assertEquals(ProfileRowAction.Delete, profileReturnAction(ProfileRowAction.Delete, doubleColumn = false))
        assertEquals(ProfileRowAction.More, profileReturnAction(ProfileRowAction.More, doubleColumn = true))
        assertEquals(ProfileRowAction.More, profileReturnAction(ProfileRowAction.Edit, doubleColumn = true))
        assertEquals(ProfileRowAction.Edit, profileReturnAction(ProfileRowAction.More, doubleColumn = false))
    }

    @Test
    fun removalFocusPrefersNextProfileThenPrevious() {
        val keys = listOf("first", "middle", "last")
        assertEquals("middle", focusAfterProfileRemoval(keys, "first"))
        assertEquals("last", focusAfterProfileRemoval(keys, "middle"))
        assertEquals("middle", focusAfterProfileRemoval(keys, "last"))
        assertNull(focusAfterProfileRemoval(listOf("only"), "only"))
        assertNull(focusAfterProfileRemoval(emptyList(), "missing"))
        assertNull(focusAfterProfileRemoval(keys, "missing"))
    }

    @Test
    fun heldCenterCannotDropOrSelectUntilKeyRepeatsHaveStopped() {
        val state = DpadReorderState()
        val guard = dpadRepeatReleaseGuardMillis(50)
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, false, 0, guard))
        assertEquals(DpadReorderActivation.StartedMoving, state.onActivationKeyDown("profile", 0, true, 600, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("profile", 620, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, false, 700, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("profile", 730, guard))
        assertEquals(DpadReorderActivation.Dropped, state.onActivationKeyDown("profile", 0, false, 730 + guard + 1, guard))
    }

    @Test
    fun centerDropIgnoresTranslatedRepeatsUntilAnotherQuietPeriod() {
        val state = DpadReorderState()
        val guard = dpadRepeatReleaseGuardMillis(50)
        state.onActivationKeyDown("profile", 0, true, 0, guard)
        state.onActivationKeyUp("profile", 20, guard)
        val drop = 20 + guard + 1
        assertEquals(DpadReorderActivation.Dropped, state.onActivationKeyDown("profile", 0, false, drop, guard))
        assertNull(state.movingKey)
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("profile", drop + 20, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, true, drop + 25, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, false, drop + 100, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyUp("profile", drop + 120, guard))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, false, drop + guard + 121, guard))
        assertEquals(DpadReorderActivation.Click, state.onActivationKeyUp("profile", drop + guard + 140, guard))
    }

    @Test
    fun backDropDoesNotSwallowTheNextCenterPress() {
        val state = DpadReorderState()
        val guard = dpadRepeatReleaseGuardMillis(50)
        state.onActivationKeyDown("profile", 0, true, 0, guard)
        assertEquals(DpadReorderActivation.Dropped, state.onDropRequest("profile"))
        assertEquals(DpadReorderActivation.None, state.onActivationKeyDown("profile", 0, false, 100, guard))
        assertEquals(DpadReorderActivation.Click, state.onActivationKeyUp("profile", 120, guard))
    }

    @Test
    fun gridMovementFollowsMirroredColumns() {
        assertEquals(1, twoColumnDpadReorderTarget(0, DpadReorderDirection.Right, isRtl = false))
        assertEquals(0, twoColumnDpadReorderTarget(0, DpadReorderDirection.Right, isRtl = true))
        assertEquals(1, twoColumnDpadReorderTarget(0, DpadReorderDirection.Left, isRtl = true))
        assertEquals(0, twoColumnDpadReorderTarget(1, DpadReorderDirection.Right, isRtl = true))
    }

    @Test
    fun closingImeEndsEditingButAttachingItDoesNot() {
        assertEquals(TvTextFieldRecoveryAction.None, tvTextFieldRecoveryAction(false, false, false, false))
        assertEquals(TvTextFieldRecoveryAction.RequestEditorFocus, tvTextFieldRecoveryAction(true, false, false, false))
        assertEquals(TvTextFieldRecoveryAction.MaintainEditor, tvTextFieldRecoveryAction(true, true, false, false))
        assertEquals(TvTextFieldRecoveryAction.FinishEditing, tvTextFieldRecoveryAction(true, true, true, false))
    }

    @Test
    fun tvFocusScrollMovesWholePixelsInEitherDirection() {
        assertEquals(1f, pixelAlignedScrollDistance(1.4f))
        assertEquals(-1f, pixelAlignedScrollDistance(-1.4f))
        assertEquals(0f, pixelAlignedScrollDistance(0.2f))
    }
}
