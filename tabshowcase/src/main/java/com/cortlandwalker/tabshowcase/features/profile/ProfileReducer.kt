package com.cortlandwalker.tabshowcase.features.profile

import com.cortlandwalker.ghettoxide.TabChildReducer
import com.cortlandwalker.ghettoxide.TabLifecycleContext
import com.cortlandwalker.tabshowcase.data.demo.FakeProfileRepository

class ProfileReducer(
    private val repository: FakeProfileRepository
) : TabChildReducer<ProfileState, ProfileAction, ProfileEffect>() {

    override fun onLoadAction(): ProfileAction? = null

    override fun onTabSelected(context: TabLifecycleContext): ProfileAction? {
        return if (currentState.profile == null) ProfileAction.Load else null
    }

    override suspend fun process(action: ProfileAction) {
        when (action) {
            ProfileAction.Load,
            ProfileAction.Retry -> loadProfile()

            ProfileAction.SettingsTapped -> emit(ProfileEffect.OpenSettings)
        }
    }

    private suspend fun loadProfile() {
        state { it.copy(isLoading = true, errorMessage = null) }

        runCatching {
            repository.loadProfile()
        }.onSuccess { profile ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    profile = profile
                )
            }
        }.onFailure { error ->
            state {
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Profile failed.",
                    profile = null
                )
            }
        }
    }
}
