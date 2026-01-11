package com.cortlandwalker.ghettoxide

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class StoreViewModelTest {

    private lateinit var reducer: TestReducer
    private lateinit var viewModel: StoreViewModel<TestState, TestAction, TestEffect>

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        reducer = TestReducer()
        viewModel = StoreViewModel(
            initial = TestState(),
            reducerImpl = reducer
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correctly set`() = runTest {
        assertThat(viewModel.state.value).isEqualTo(TestState())
    }

    @Test
    fun `postAction should update state via reducer`() = runTest {
        // Initial state
        assertThat(viewModel.state.value.count).isEqualTo(0)

        // When
        viewModel.postAction(TestAction.Increment)
        advanceUntilIdle() // Run the coroutine launched by postAction

        // Then
        assertThat(viewModel.state.value.count).isEqualTo(1)
    }

    @Test
    fun `reducer effects should be emitted by viewModel`() = runTest {
        viewModel.effects.test {
            // When
            viewModel.postAction(TestAction.AddItem("hello"))

            // Then
            val emittedEffect = awaitItem()
            assertThat(emittedEffect).isEqualTo(TestEffect.ShowToast("Added hello"))

            // Clean up
            cancelAndIgnoreRemainingEvents()
        }
    }
}
