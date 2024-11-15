/*
 * Copyright (C) 2015 Paul Burke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.v2ray.ang.helper

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.Canvas
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * An implementation of [ItemTouchHelper.Callback] that enables basic drag & drop and
 * swipe-to-dismiss. Drag events are automatically started by an item long-press.<br></br>
 *
 * Expects the `RecyclerView.Adapter` to listen for [ ] callbacks and the `RecyclerView.ViewHolder` to implement
 * [ItemTouchHelperViewHolder].
 *
 * @author Paul Burke (ipaulpro)
 */
class SimpleItemTouchHelperCallback(private val mAdapter: ItemTouchHelperAdapter) : ItemTouchHelper.Callback() {
    private var mReturnAnimator: ValueAnimator? = null

    override fun isLongPressDragEnabled(): Boolean = true

    override fun isItemViewSwipeEnabled(): Boolean = true

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags: Int
        val swipeFlags: Int
        if (recyclerView.layoutManager is GridLayoutManager) {
            dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        } else {
            dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            swipeFlags = ItemTouchHelper.START or ItemTouchHelper.END
        }
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        source: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return if (source.itemViewType != target.itemViewType) {
            false
        } else {
            mAdapter.onItemMove(source.bindingAdapterPosition, target.bindingAdapterPosition)
            true
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Do not delete; simply return item to original position
        returnViewToOriginalPosition(viewHolder)
    }

    override fun onChildDraw(
        c: Canvas, recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val maxSwipeDistance = viewHolder.itemView.width * SWIPE_THRESHOLD
            val swipeAmount = abs(dX)
            val direction = sign(dX)

            // Limit maximum swipe distance
            val translationX = min(swipeAmount, maxSwipeDistance) * direction
            val alpha = ALPHA_FULL - min(swipeAmount, maxSwipeDistance) / maxSwipeDistance

            viewHolder.itemView.translationX = translationX
            viewHolder.itemView.alpha = alpha

            if (swipeAmount >= maxSwipeDistance && isCurrentlyActive) {
                returnViewToOriginalPosition(viewHolder)
            }
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private fun returnViewToOriginalPosition(viewHolder: RecyclerView.ViewHolder) {
        mReturnAnimator?.takeIf { it.isRunning }?.cancel()

        mReturnAnimator = ValueAnimator.ofFloat(viewHolder.itemView.translationX, 0f).apply {
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                viewHolder.itemView.translationX = value
                viewHolder.itemView.alpha = (1f - abs(value) / (viewHolder.itemView.width * SWIPE_THRESHOLD))
            }
            interpolator = DecelerateInterpolator()
            duration = ANIMATION_DURATION
            start()
        }
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder is ItemTouchHelperViewHolder) {
            viewHolder.onItemSelected()
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = ALPHA_FULL
        if (viewHolder is ItemTouchHelperViewHolder) {
            viewHolder.onItemClear()
        }
        mAdapter.onItemMoveCompleted()
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
        return 1.1f // Set a value greater than 1 to prevent default swipe delete
    }

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
        return defaultValue * 10 // Increase swipe escape velocity to make swipe harder to trigger
    }

    companion object {
        private const val ALPHA_FULL = 1.0f
        private const val SWIPE_THRESHOLD = 0.25f
        private const val ANIMATION_DURATION: Long = 200
    }
}