package com.npv.crsgw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ServerDao {
    @Query("SELECT * FROM server where type=1")
    suspend fun getCommunityServers(): List<Server>

    @Query("SELECT * FROM server where type=2 and email = :email")
    suspend fun getEnterpriseServers(email: String): List<Server>

    @Insert
    suspend fun insertServer(server: Server)

    @Update
    suspend fun updateServer(server: Server)

    @Delete
    suspend fun deleteServer(server: Server)

    @Query("SELECT * FROM server WHERE guid = :guid")
    suspend fun findByGuid(guid: String): Server?

    @Query("SELECT * FROM server WHERE signature = :signature")
    suspend fun findBySignature(signature: String): Server?
}