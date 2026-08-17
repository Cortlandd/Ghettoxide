package com.cortlandwalker.tabshowcase.data.models

enum class UploadFilter {
    All,
    Popular,
    Drafts
}

data class ListPosition(
    val index: Int = 0,
    val offset: Int = 0
)

data class FeedPage<T>(
    val items: List<T>,
    val nextCursor: Int?
)

data class ClipCard(
    val id: String,
    val title: String,
    val creator: String,
    val tag: String,
    val duration: String,
    val likesLabel: String
)

data class NotificationCard(
    val id: String,
    val title: String,
    val message: String,
    val timeAgo: String
)

data class CreatorProfile(
    val handle: String,
    val displayName: String,
    val bio: String,
    val followersLabel: String
)

data class CollectionCard(
    val id: String,
    val title: String,
    val subtitle: String
)
