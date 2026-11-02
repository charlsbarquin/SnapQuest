package com.charlsemilbarquin.frameandfact.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapQuestBackButtonTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun accessibleBackButtonInvokesNavigationCallback() {
        val backCalls = AtomicInteger()
        compose.setContent {
            FrameFactTheme {
                SnapQuestBackButton(onClick = { backCalls.incrementAndGet() })
            }
        }

        compose.onNodeWithContentDescription("Navigate back")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, backCalls.get())
    }
}
