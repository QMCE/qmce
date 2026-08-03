package rj.qmce.lite.data.notify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single shared Contact/Group notify repository pair used by both the system
 * notifier and [rj.qmce.lite.viewmodel.NotificationCenterViewModel], so kernel
 * listeners are registered once.
 */
object SharedNotifyRepositories {

    private val _friends = MutableStateFlow<List<UiFriendRequest>>(emptyList())
    val friends: StateFlow<List<UiFriendRequest>> = _friends.asStateFlow()

    private val _groups = MutableStateFlow<List<UiGroupNotice>>(emptyList())
    val groups: StateFlow<List<UiGroupNotice>> = _groups.asStateFlow()

    private val friendListeners = mutableSetOf<(List<UiFriendRequest>) -> Unit>()
    private val groupListeners = mutableSetOf<(List<UiGroupNotice>) -> Unit>()

    val friendRepo = ContactNotifyRepository { items ->
        _friends.value = items
        synchronized(friendListeners) {
            friendListeners.toList().forEach { it(items) }
        }
    }

    val groupRepo = GroupNotifyRepository { items ->
        _groups.value = items
        synchronized(groupListeners) {
            groupListeners.toList().forEach { it(items) }
        }
    }

    fun addFriendListener(listener: (List<UiFriendRequest>) -> Unit) {
        synchronized(friendListeners) { friendListeners.add(listener) }
        listener(_friends.value)
    }

    fun removeFriendListener(listener: (List<UiFriendRequest>) -> Unit) {
        synchronized(friendListeners) { friendListeners.remove(listener) }
    }

    fun addGroupListener(listener: (List<UiGroupNotice>) -> Unit) {
        synchronized(groupListeners) { groupListeners.add(listener) }
        listener(_groups.value)
    }

    fun removeGroupListener(listener: (List<UiGroupNotice>) -> Unit) {
        synchronized(groupListeners) { groupListeners.remove(listener) }
    }
}
