package com.cortlandwalker.tabshowcase.data.demo

import com.cortlandwalker.tabshowcase.data.models.ClipCard
import com.cortlandwalker.tabshowcase.data.models.CollectionCard
import com.cortlandwalker.tabshowcase.data.models.CreatorProfile
import com.cortlandwalker.tabshowcase.data.models.FeedPage
import com.cortlandwalker.tabshowcase.data.models.NotificationCard
import com.cortlandwalker.tabshowcase.data.models.UploadFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object DemoRepositories {
    val home = FakeHomeRepository()
    val search = FakeSearchRepository()
    val notifications = FakeNotificationsRepository()
    val profile = FakeProfileRepository()
}

class FakeHomeRepository {
    private val homeFeed = listOf(
        ClipCard("clip-1", "Pocketable Color Grading", "@maya", "For You", "0:31", "12.4K likes"),
        ClipCard("clip-2", "Three-Point Lighting On A Budget", "@rowan", "Studio", "0:48", "9.2K likes"),
        ClipCard("clip-3", "Looping Product Shot Setup", "@briar", "Gear", "0:52", "16.8K likes"),
        ClipCard("clip-4", "A Better Talking Head Background", "@sol", "Creators", "0:44", "7.5K likes"),
        ClipCard("clip-5", "Fast Motion Blur Trick", "@violet", "Edit", "0:27", "18.9K likes"),
        ClipCard("clip-6", "Tiny Apartment Audio Fix", "@jules", "Audio", "0:39", "6.4K likes"),
        ClipCard("clip-7", "Camera Roll Organization Pass", "@milo", "Workflow", "0:58", "11.1K likes"),
        ClipCard("clip-8", "Thumbnail Framing Formula", "@lena", "Growth", "0:35", "5.6K likes"),
        ClipCard("clip-9", "Cinematic Desk B-Roll", "@cora", "B-roll", "0:42", "14.2K likes"),
        ClipCard("clip-10", "Contrast Without Crunch", "@omar", "Color", "0:33", "8.2K likes"),
        ClipCard("clip-11", "Lens Wipes That Do Not Smear", "@tess", "Gear", "0:29", "4.8K likes"),
        ClipCard("clip-12", "How I Batch Shorts Captions", "@rhea", "Workflow", "0:46", "13.3K likes")
    )

    suspend fun loadFeed(cursor: Int?): FeedPage<ClipCard> {
        delay(350)
        return homeFeed.page(cursor = cursor, pageSize = 6)
    }
}

class FakeSearchRepository {
    private val searchCorpus = listOf(
        ClipCard("search-1", "Street portrait framing", "@maya", "Portrait", "0:41", "3.4K likes"),
        ClipCard("search-2", "Best export settings for socials", "@sol", "Export", "0:56", "10.7K likes"),
        ClipCard("search-3", "Collection cover animation", "@rowan", "Design", "0:38", "2.9K likes"),
        ClipCard("search-4", "Creator onboarding checklist", "@kira", "Teams", "1:02", "6.6K likes"),
        ClipCard("search-5", "Lighting a laptop product demo", "@jules", "Studio", "0:34", "4.2K likes"),
        ClipCard("search-6", "Profile header motion pass", "@tess", "Profile", "0:49", "1.8K likes"),
        ClipCard("search-7", "Draft review rhythm", "@omar", "Workflow", "0:28", "5.1K likes"),
        ClipCard("search-8", "Minimal home set build", "@violet", "Studio", "0:45", "3.9K likes")
    )

    suspend fun search(
        query: String,
        cursor: Int?
    ): FeedPage<ClipCard> {
        delay(300)
        if (query.trim().equals("error", ignoreCase = true)) {
            error("The fake search backend is intentionally failing for \"error\".")
        }

        val filtered = searchCorpus.filter { clip ->
            clip.title.contains(query, ignoreCase = true) ||
                clip.creator.contains(query, ignoreCase = true) ||
                clip.tag.contains(query, ignoreCase = true)
        }
        return filtered.page(cursor = cursor, pageSize = 5)
    }
}

class FakeNotificationsRepository {
    private val notifications = listOf(
        NotificationCard("note-1", "New comment", "@maya replied to your lens breakdown.", "2m ago"),
        NotificationCard("note-2", "Trending", "Your caption workflow clip is climbing fast.", "18m ago"),
        NotificationCard("note-3", "Saved", "@rowan added your lighting clip to Studio Tricks.", "42m ago"),
        NotificationCard("note-4", "Follow", "@kira started following you.", "1h ago"),
        NotificationCard("note-5", "Mention", "@sol mentioned you in Daily Creator Check-in.", "3h ago"),
        NotificationCard("note-6", "Collection", "Your clip was added to Pocketable Edits.", "Yesterday")
    )

    suspend fun loadNotifications(): List<NotificationCard> {
        delay(325)
        return notifications
    }

    fun observeUnreadPulse(): Flow<Int> = flow {
        var count = 1
        while (true) {
            emit(count)
            count = if (count == 4) 1 else count + 1
            delay(1_500)
        }
    }
}

class FakeProfileRepository {
    private var likedShouldFailOnce = true

    private val uploads = listOf(
        ClipCard("upload-1", "Clean capture setup", "@cortland", "Popular", "0:31", "11.2K likes"),
        ClipCard("upload-2", "When to trim reaction beats", "@cortland", "Popular", "0:37", "9.7K likes"),
        ClipCard("upload-3", "Color pass for skin tone", "@cortland", "Popular", "0:43", "7.9K likes"),
        ClipCard("upload-4", "Draft: profile banner notes", "@cortland", "Draft", "0:22", "Private"),
        ClipCard("upload-5", "Draft: interview intro alt", "@cortland", "Draft", "0:18", "Private"),
        ClipCard("upload-6", "Batching creator replies", "@cortland", "Popular", "0:34", "6.1K likes"),
        ClipCard("upload-7", "Story arc in 20 seconds", "@cortland", "All", "0:29", "8.6K likes"),
        ClipCard("upload-8", "Reference board cleanup", "@cortland", "All", "0:40", "3.2K likes")
    )

    private val liked = listOf(
        ClipCard("liked-1", "Client revision shorthand", "@kira", "Liked", "0:28", "Liked"),
        ClipCard("liked-2", "Better over-shoulder framing", "@maya", "Liked", "0:36", "Liked"),
        ClipCard("liked-3", "Clip sequencing with tension", "@rhea", "Liked", "0:51", "Liked"),
        ClipCard("liked-4", "Micro sound design layers", "@jules", "Liked", "0:26", "Liked"),
        ClipCard("liked-5", "Minimal set prop kit", "@sol", "Liked", "0:33", "Liked")
    )

    suspend fun loadProfile(): CreatorProfile {
        delay(250)
        return CreatorProfile(
            handle = "@cortland",
            displayName = "Cortland Walker",
            bio = "Building a reducer-driven tab shell for creator tools and feed-heavy apps.",
            followersLabel = "18.4K followers"
        )
    }

    suspend fun loadUploads(
        filter: UploadFilter,
        cursor: Int?
    ): FeedPage<ClipCard> {
        delay(280)
        val filtered = when (filter) {
            UploadFilter.All -> uploads
            UploadFilter.Popular -> uploads.filterNot { it.tag == "Draft" }
            UploadFilter.Drafts -> uploads.filter { it.tag == "Draft" }
        }
        return filtered.page(cursor = cursor, pageSize = 4)
    }

    suspend fun loadLiked(cursor: Int?): FeedPage<ClipCard> {
        delay(320)
        if (cursor == null && likedShouldFailOnce) {
            likedShouldFailOnce = false
            error("Liked tab failed once on purpose so Retry has something real to do.")
        }
        return liked.page(cursor = cursor, pageSize = 4)
    }

    suspend fun loadCollections(): List<CollectionCard> {
        delay(240)
        return emptyList()
    }
}

private fun <T> List<T>.page(
    cursor: Int?,
    pageSize: Int
): FeedPage<T> {
    val startIndex = cursor ?: 0
    if (startIndex >= size) {
        return FeedPage(
            items = emptyList(),
            nextCursor = null
        )
    }

    val endIndex = (startIndex + pageSize).coerceAtMost(size)
    val nextCursor = endIndex.takeIf { it < size }
    return FeedPage(
        items = subList(startIndex, endIndex),
        nextCursor = nextCursor
    )
}
