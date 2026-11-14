package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local app sign-in account; deliberately separate from multiplayer player profiles. */
@Entity(
    tableName = "auth_accounts",
    indices = [
        Index(value = ["normalizedUsername"], unique = true),
        Index(value = ["normalizedEmail"], unique = true)
    ]
)
data class AuthAccountEntity(
    @PrimaryKey val accountId: String,
    val fullName: String,
    val username: String,
    val normalizedUsername: String,
    val normalizedEmail: String?,
    val passwordSaltBase64: String,
    val passwordHashBase64: String,
    val createdAtEpochMillis: Long
)
