package com.v2ray.ang.contracts

/**
 * A common Adapter -> host callback interface that includes common actions: edit, remove and refresh.
 * Extend this interface or define more specific interfaces for different adapters as needed.
 */
interface BaseAdapterListener {
    /**
     * Request the host to edit the specified item.
     * @param guid Unique identifier (GUID) of the item
     * @param position Current position in the adapter (optional; host should validate it)
     */
    fun onEdit(guid: String, position: Int)

    /**
     * Request the host to remove the specified item. Position is provided for optional animation or validation.
     * @param guid Unique identifier (GUID) of the item
     * @param position Current position in the adapter (optional; host should validate it)
     */
    fun onRemove(guid: String, position: Int)

    /**
     * Request the host to share the specified URL.
     * @param url The URL to be shared
     */
    fun onShare(url: String)

    /**
     * Request the host to refresh data (for example, reload from the ViewModel or call notifyDataSetChanged).
     */
    fun onRefreshData()
}
