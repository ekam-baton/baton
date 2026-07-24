package com.ekam.baton.core.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekam.baton.core.data.db.entity.GroupEntity

@Dao
interface GroupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: GroupEntity)

    @Query("SELECT * FROM groups")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: String): GroupEntity?

    @Delete
    suspend fun delete(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
