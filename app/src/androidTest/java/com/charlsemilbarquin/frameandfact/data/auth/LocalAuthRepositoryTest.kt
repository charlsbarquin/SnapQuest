package com.charlsemilbarquin.frameandfact.data.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalAuthRepositoryTest {
    private lateinit var database: GameHistoryDatabase
    private lateinit var repository: LocalAuthRepository
    private lateinit var sessionStore: EncryptedSessionStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionStore = EncryptedSessionStore(context, TEST_PREFERENCES, TEST_KEY_ALIAS)
        sessionStore.clear()
        repository = LocalAuthRepository(database.authAccountDao(), sessionStore)
    }

    @After
    fun tearDown() {
        sessionStore.clear()
        database.close()
    }

    @Test
    fun registrationRejectsInvalidAndDuplicateAccountsWithoutSavingPlaintext() = runBlocking {
        val invalid = repository.register("", "player", "", "short", "short")
        assertTrue(invalid is AuthOperation.Failure)

        val first = repository.register("Player One", "Player.One", "one@example.com", "SnapQuest2026", "SnapQuest2026")
        assertTrue(first is AuthOperation.Success)
        val duplicate = repository.register("Player Two", "player.one", "two@example.com", "SnapQuest2027", "SnapQuest2027")
        assertEquals("That username is already registered.", (duplicate as AuthOperation.Failure).message)

        val saved = database.authAccountDao().findByUsername("player.one")
        assertTrue(saved != null)
        assertFalse(saved!!.passwordHashBase64.contains("SnapQuest2026"))
        assertFalse(saved.passwordSaltBase64.contains("SnapQuest2026"))
    }

    @Test
    fun loginRestoresEncryptedSessionAndLogoutClearsIt() = runBlocking {
        repository.register("Player One", "playerone", "", "SnapQuest2026", "SnapQuest2026")
        assertTrue(repository.login("PLAYERONE", "wrongpassword2") is AuthOperation.Failure)

        val signedIn = repository.login("playerone", "SnapQuest2026", rememberMe = true) as AuthOperation.Success
        assertEquals("playerone", signedIn.user.username)
        val recreatedStore = testSessionStore()
        val recreatedRepository = LocalAuthRepository(database.authAccountDao(), recreatedStore)
        assertEquals(signedIn.user, recreatedRepository.restoreSession())
        assertTrue(repository.logout())
        assertEquals(null, repository.restoreSession())
        assertEquals("playerone", database.authAccountDao().findById(signedIn.user.accountId)?.username)
        assertTrue(repository.logout())
        assertEquals(null, repository.restoreSession())
        val signedInAgain = repository.login("playerone", "SnapQuest2026") as AuthOperation.Success
        assertEquals(signedIn.user.accountId, signedInAgain.user.accountId)
        assertEquals(signedInAgain.user, repository.restoreSession())
    }

    @Test
    fun uncheckedRememberMeClearsPreviousSessionAndDoesNotRestoreAfterRepositoryRecreation() = runBlocking {
        repository.register("Player One", "playerone", "", "SnapQuest2026", "SnapQuest2026")
        repository.register("Player Two", "playertwo", "", "SnapQuest2027", "SnapQuest2027")
        val first = repository.login("playerone", "SnapQuest2026", rememberMe = true) as AuthOperation.Success
        assertEquals(first.user, repository.restoreSession())

        val second = repository.login("playertwo", "SnapQuest2027", rememberMe = false) as AuthOperation.Success
        assertEquals("playertwo", second.user.username)
        assertEquals(null, repository.restoreSession())

        val afterProcessRestart = LocalAuthRepository(database.authAccountDao(), testSessionStore())
        assertEquals(null, afterProcessRestart.restoreSession())
        assertEquals("playerone", database.authAccountDao().findById(first.user.accountId)?.username)
        assertEquals("playertwo", database.authAccountDao().findById(second.user.accountId)?.username)
    }

    @Test
    fun corruptedRememberedSessionIsClearedWithoutDeletingAccount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.register("Player One", "playerone", "", "SnapQuest2026", "SnapQuest2026")
        val account = database.authAccountDao().findByUsername("playerone")!!
        context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString("encrypted_account_id", "not-valid-base64!").commit()

        assertEquals(null, repository.restoreSession())
        assertEquals(null, context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE).getString("encrypted_account_id", null))
        assertEquals("playerone", database.authAccountDao().findById(account.accountId)?.username)
    }

    private fun testSessionStore(): EncryptedSessionStore = EncryptedSessionStore(
        ApplicationProvider.getApplicationContext(),
        TEST_PREFERENCES,
        TEST_KEY_ALIAS
    )

    private companion object {
        const val TEST_PREFERENCES = "snapquest_auth_repository_test"
        const val TEST_KEY_ALIAS = "snapquest_auth_repository_test_key"
    }
}
