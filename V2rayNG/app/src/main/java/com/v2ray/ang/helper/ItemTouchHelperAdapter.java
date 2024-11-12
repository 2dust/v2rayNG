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

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Interface for listening to move and dismissal events from a {@link ItemTouchHelper.Callback}.
 * Implement this interface to handle item touch events in a RecyclerView.
 *
 * @author Paul Burke (ipaulpro)
 */
public interface ItemTouchHelperAdapter {

    /**
     * Called when an item has been dragged far enough to trigger a move.
     * This method is invoked every time an item is shifted, not just at the end of a "drop" event.
     *
     * Implementations should call {@link RecyclerView.Adapter#notifyItemMoved(int, int)} 
     * after adjusting the underlying data to reflect this move.
     *
     * @param fromPosition The starting position of the moved item.
     * @param toPosition   The resolved position of the moved item.
     * @return True if the item was successfully moved to the new adapter position.
     */
    boolean onItemMove(int fromPosition, int toPosition);

    /**
     * Called when an item move operation is completed.
     */
    void onItemMoveCompleted();

    /**
     * Called when an item has been dismissed by a swipe.
     *
     * Implementations should call {@link RecyclerView.Adapter#notifyItemRemoved(int)} 
     * after adjusting the underlying data to reflect this removal.
     *
     * @param position The position of the dismissed item.
     */
    void onItemDismiss(int position);
}
