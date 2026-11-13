package com.v2ray.ang.ui.main

import com.v2ray.ang.ui.compose.DpadReorderActivation
import com.v2ray.ang.ui.compose.DpadReorderDirection
import com.v2ray.ang.ui.compose.DpadReorderState
import com.v2ray.ang.ui.compose.TvTextFieldRecoveryAction
import com.v2ray.ang.ui.compose.dpadRepeatReleaseGuardMillis
import com.v2ray.ang.ui.compose.tvTextFieldRecoveryAction
import com.v2ray.ang.ui.compose.twoColumnDpadReorderTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TvMainInteractionTest {
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
