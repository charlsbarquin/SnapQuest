package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuthAccountDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AuthAccountEntity): Long

    @Query("SELECT * FROM auth_accounts WHERE normalizedUsername = :normalizedUsername LIMIT 1")
    suspend fun findByUsername(normalizedUsername: String): AuthAccountEntity?

    @Query("SELECT * FROM auth_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun findById(accountId: String): AuthAccountEntity?

    @Query("SELECT * FROM auth_accounts WHERE normalizedUsername = :identifier OR normalizedEmail = :identifier LIMIT 1")
    suspend fun findByLoginIdentifier(identifier: String): AuthAccountEntity?
}
