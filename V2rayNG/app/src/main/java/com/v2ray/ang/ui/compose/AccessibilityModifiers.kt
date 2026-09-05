package com.v2ray.ang.ui.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

internal fun Modifier.accessibilityTraversalGroup(): Modifier = semantics {
    isTraversalGroup = true
}

internal fun Modifier.accessibilityTraversalIndex(index: Float): Modifier = semantics {
    traversalIndex = index
}
