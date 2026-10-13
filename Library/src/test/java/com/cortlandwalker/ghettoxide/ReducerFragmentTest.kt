package com.cortlandwalker.ghettoxide

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReducerFragmentTest {
    private lateinit var activity: ActivityController<ReducerTestActivity>
    @Before fun launch() { activity = Robolectric.buildActivity(ReducerTestActivity::class.java).setup() }
    @After fun close() { activity.pause().stop().destroy() }

    @Test fun `default effects remain active at STARTED and view recreation keeps the store`() {
        val fragment = add(ReducerTestFragment())
        val reducer = fragment.reducer
        activity.get().supportFragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED).commitNow()
        send(fragment, "started")
        assertThat(fragment.received).containsExactly("started")
        activity.get().supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        send(fragment, "detached")
        assertThat(fragment.view).isNull()
        assertThat(reducer.detachCount).isEqualTo(1)
        activity.get().supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        settle()
        send(fragment, "reattached")
        assertThat(fragment.reducer).isSameInstanceAs(reducer)
        assertThat(fragment.state.value).isEqualTo(1)
        assertThat(fragment.received).containsExactly("started", "reattached").inOrder()
    }

    @Test fun `effect gate stops a retained hidden view without replay or duplicate collectors`() {
        val fragment = add(ReducerTestFragment())
        val view = fragment.requireView()
        send(fragment, "visible")
        fragment.effectCollectionEnabled.value = false
        activity.get().supportFragmentManager.beginTransaction().hide(fragment).commitNow()
        settle()
        send(fragment, "hidden")
        assertThat(fragment.requireView()).isSameInstanceAs(view)
        assertThat(fragment.reducer.detachCount).isEqualTo(0)
        repeat(3) {
            fragment.effectCollectionEnabled.value = true
            settle()
            send(fragment, "return-$it")
            fragment.effectCollectionEnabled.value = false
            settle()
        }
        assertThat(fragment.received).containsExactly("visible", "return-0", "return-1", "return-2").inOrder()
    }

    @Test fun `RESUMED effects pause at STARTED while the Fragment view survives`() {
        val fragment = add(ResumedReducerTestFragment())
        val view = fragment.requireView()
        activity.get().supportFragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED).commitNow()
        settle()
        send(fragment, "paused")
        assertThat(fragment.requireView()).isSameInstanceAs(view)
        activity.get().supportFragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.RESUMED).commitNow()
        settle()
        send(fragment, "resumed")
        assertThat(fragment.received).containsExactly("resumed")
    }

    @Test fun `configuration recreation rebinds the original reducer and runs startup once`() {
        val original = add(ReducerTestFragment())
        val reducer = original.reducer
        activity.recreate()
        settle()
        val restored = activity.get().supportFragmentManager.findFragmentByTag("screen") as ReducerTestFragment
        assertThat(restored).isNotSameInstanceAs(original)
        assertThat(restored.reducer).isSameInstanceAs(reducer)
        assertThat(restored.state.value).isEqualTo(1)
        send(restored, "restored")
        assertThat(restored.received).containsExactly("restored")
        assertThat(original.received).isEmpty()
    }

    @Test fun `view lifecycle dispatches resume on every return while loading only once`() {
        val fragment = add(ReducerTestFragment())
        val reducer = fragment.reducer
        assertThat(reducer.lifecycleActions).containsExactly("start", "resume").inOrder()
        activity.get().supportFragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.STARTED).commitNow()
        settle()
        activity.get().supportFragmentManager.beginTransaction()
            .setMaxLifecycle(fragment, Lifecycle.State.RESUMED).commitNow()
        settle()
        activity.get().supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        settle()
        activity.get().supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        settle()
        assertThat(fragment.state.value).isEqualTo(1)
        assertThat(reducer.lifecycleActions).containsExactly(
            "start", "resume", "pause", "resume", "pause", "stop", "start", "resume"
        ).inOrder()
    }

    @Test fun `presentation gate pauses lifecycle hooks for a mounted hidden Fragment`() {
        val fragment = add(ReducerTestFragment())
        val view = fragment.requireView()
        fragment.reducerLifecycleEnabled.value = false
        settle()
        activity.get().supportFragmentManager.beginTransaction().hide(fragment).commitNow()
        settle()
        fragment.reducerLifecycleEnabled.value = true
        activity.get().supportFragmentManager.beginTransaction().show(fragment).commitNow()
        settle()
        assertThat(fragment.requireView()).isSameInstanceAs(view)
        assertThat(fragment.state.value).isEqualTo(1)
        assertThat(fragment.reducer.lifecycleActions).containsExactly(
            "start", "resume", "pause", "stop", "start", "resume"
        ).inOrder()
    }

    private fun <F : ReducerTestFragment> add(fragment: F): F {
        activity.get().supportFragmentManager.beginTransaction().add(12345, fragment, "screen").commitNow()
        settle()
        return fragment
    }
    private fun send(fragment: ReducerTestFragment, effect: String) {
        fragment.reducer.postAction(effect)
        settle()
    }
    private fun settle() { shadowOf(Looper.getMainLooper()).idle() }
}

class ReducerTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply { id = 12345 })
    }
}

open class ReducerTestFragment : ReducerFragment<Int, String, String, FragmentTestReducer>() {
    override var reducer = FragmentTestReducer()
    override val initialState = 0
    public override val effectCollectionEnabled = MutableStateFlow(true)
    public override val reducerLifecycleEnabled = MutableStateFlow(true)
    val received = mutableListOf<String>()
    override fun onEffect(effect: String) { received += effect }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        FrameLayout(requireContext())
}

class ResumedReducerTestFragment : ReducerTestFragment() {
    override val effectMinActiveState = Lifecycle.State.RESUMED
}

class FragmentTestReducer : Reducer<Int, String, String>() {
    var detachCount = 0
    val lifecycleActions = mutableListOf<String>()
    override fun onLoadAction() = "load"
    override fun onStartAction() = "start"
    override fun onResumeAction() = "resume"
    override fun onPauseAction() = "pause"
    override fun onStopAction() = "stop"
    override suspend fun process(action: String) {
        when (action) {
            "load" -> state { it + 1 }
            "start", "resume", "pause", "stop" -> lifecycleActions += action
            else -> emit(action)
        }
    }
    override fun onViewDetached() { detachCount++ }
}
