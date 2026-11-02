package com.charlsemilbarquin.frameandfact.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationViewModelTest {
    @Test
    fun startupPhotoCleanupRunsOnlyOnceForAnActivityViewModel() {
        val state = AppNavigationViewModel()

        assertTrue(state.claimStartupPhotoCleanup())
        assertFalse(state.claimStartupPhotoCleanup())
    }
}
