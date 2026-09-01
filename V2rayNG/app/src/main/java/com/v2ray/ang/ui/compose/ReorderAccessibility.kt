package com.v2ray.ang.ui.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.v2ray.ang.R

internal enum class ReorderCommand(@StringRes val labelRes: Int) {
    MoveToTop(R.string.acc_move_to_top),
    MoveUp(R.string.acc_move_up),
    MoveDown(R.string.acc_move_down),
    MoveToBottom(R.string.acc_move_to_bottom);

    fun targetIndex(currentIndex: Int, itemCount: Int): Int? {
        if (currentIndex !in 0 until itemCount) return null
        val targetIndex = when (this) {
            MoveToTop -> 0
            MoveUp -> currentIndex - 1
            MoveDown -> currentIndex + 1
            MoveToBottom -> itemCount - 1
        }
        return targetIndex.takeIf { it in 0 until itemCount && it != currentIndex }
    }

    companion object {
        fun availableAt(currentIndex: Int, itemCount: Int): List<ReorderCommand> =
            entries.filter { it.targetIndex(currentIndex, itemCount) != null }
    }
}

@Composable
internal fun reorderAccessibilityActions(
    currentIndex: Int,
    itemCount: Int,
    onMove: (ReorderCommand) -> Boolean,
): List<CustomAccessibilityAction> = ReorderCommand.availableAt(currentIndex, itemCount).map { command ->
    CustomAccessibilityAction(
        label = stringResource(command.labelRes),
        action = { onMove(command) },
    )
}
