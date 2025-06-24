package com.npv.crsgw.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// Server list
@Entity(tableName = "server")
data class Server(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val guid: String,
    val signature: String,
    val link: String,
    val email: String,
    // 1: community, 2: enterprise
    val type: Int
)