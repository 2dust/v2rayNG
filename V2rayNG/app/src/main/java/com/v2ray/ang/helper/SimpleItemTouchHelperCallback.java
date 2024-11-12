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

package com.v2ray.ang.helper;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An implementation of {@link ItemTouchHelper.Callback} that enables basic drag & drop and
 * swipe-to-dismiss functionality. Drag events are automatically started by an item long-press.
 *
 * This class expects the <code>RecyclerView.Adapter</code> to listen for {@link ItemTouchHelperAdapter}
 * callbacks and the <code>RecyclerView.ViewHolder</code> to implement {@link ItemTouchHelperViewHolder}.
 */
public class SimpleItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private static final float ALPHA_FULL = 1.0f;
    private static final float SWIPE_THRESHOLD = 0.25f;
    private static final long ANIMATION_DURATION = 200;

    private final ItemTouchHelperAdapter mAdapter;
    private ValueAnimator mReturnAnimator;

    public SimpleItemTouchHelperCallback(ItemTouchHelperAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;

        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            dragFlags |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT; // Allow horizontal dragging in GridLayoutManager
        }

        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false; // Prevent moving items of different types
        }
        mAdapter.onItemMove(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Do not perform delete operation; just return item to original position
        returnViewToOriginalPosition(viewHolder);
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            handleSwipe(c, viewHolder, dX);
        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }
    }

    private void handleSwipe(@NonNull Canvas c, @NonNull RecyclerView.ViewHolder viewHolder, float dX) {
        float maxSwipeDistance = viewHolder.itemView.getWidth() * SWIPE_THRESHOLD;
        float swipeAmount = Math.abs(dX);
        float direction = Math.signum(dX);

        // Limit maximum swipe distance
        float translationX = Math.min(swipeAmount, maxSwipeDistance) * direction;
        float alpha = ALPHA_FULL - Math.min(swipeAmount, maxSwipeDistance) / maxSwipeDistance;

        viewHolder.itemView.setTranslationX(translationX);
        viewHolder.itemView.setAlpha(alpha);

        if (swipeAmount >= maxSwipeDistance && isCurrentlyActive) {
            returnViewToOriginalPosition(viewHolder);
        }
    }

    private void returnViewToOriginalPosition(RecyclerView.ViewHolder viewHolder) {
        if (mReturnAnimator != null && mReturnAnimator.isRunning()) {
            mReturnAnimator.cancel();
        }

        mReturnAnimator = ValueAnimator.ofFloat(viewHolder.itemView.getTranslationX(), 0f);
        mReturnAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            viewHolder.itemView.setTranslationX(value);
            viewHolder.itemView.setAlpha(1f - Math.abs(value) / (viewHolder.itemView.getWidth() * SWIPE_THRESHOLD));
        });
        
        mReturnAnimator.setInterpolator(new DecelerateInterpolator());
        mReturnAnimator.setDuration(ANIMATION_DURATION);
        mReturnAnimator.start();
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE && viewHolder instanceof ItemTouchHelperViewHolder) {
            ((ItemTouchHelperViewHolder) viewHolder).onItemSelected();
        }
        
        super.onSelectedChanged(viewHolder, actionState);
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        
        viewHolder.itemView.setAlpha(ALPHA_FULL);
        
        if (viewHolder instanceof ItemTouchHelperViewHolder) {
            ((ItemTouchHelperViewHolder) viewHolder).onItemClear();
        }
        
        mAdapter.onItemMoveCompleted();
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 1.1f; // Set a value greater than 1 to ensure default swipe delete does not trigger
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 10; // Increase escape velocity to make swiping harder to trigger delete
    }
}
