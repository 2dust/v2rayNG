package com.v2ray.ang.shizuku

import android.os.Parcel
import android.os.Parcelable

/** The shell-side tethering status returned to the UI in one Binder call. */
data class TetheringStatusSnapshot(
    val routingState: Int,
    val routingDetail: String,
    val activeTetheringTypes: Int,
    val ipv6TetheringTypes: Int,
    val warning: Int,
    val hasRoutingSession: Boolean = false,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        routingState = parcel.readInt(),
        routingDetail = parcel.readString().orEmpty(),
        activeTetheringTypes = parcel.readInt(),
        ipv6TetheringTypes = parcel.readInt(),
        warning = parcel.readInt(),
        hasRoutingSession = parcel.readInt() != 0,
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(routingState)
        parcel.writeString(routingDetail)
        parcel.writeInt(activeTetheringTypes)
        parcel.writeInt(ipv6TetheringTypes)
        parcel.writeInt(warning)
        parcel.writeInt(if (hasRoutingSession) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TetheringStatusSnapshot> {
        override fun createFromParcel(parcel: Parcel) = TetheringStatusSnapshot(parcel)

        override fun newArray(size: Int): Array<TetheringStatusSnapshot?> = arrayOfNulls(size)
    }
}
