package com.example.universalsearch.data

import android.net.Uri

sealed interface SearchResult {
  data class ContactItem(
    val id: String,
    val name: String,
    val phoneNumber: String
  ) : SearchResult

  data class MediaItem(
    val name: String,
    val path: String,
    val size: Long,
    val uri: Uri,
    val lastModified: Long
  ) : SearchResult

  data class DocumentItem(
    val name: String,
    val path: String,
    val size: Long,
    val uri: Uri,
    val lastModified: Long
  ) : SearchResult

  data class ActionItem(
    val label: String,
    val query: String,
    val actionType: ActionType
  ) : SearchResult

  // Represents an installed desktop application result
  data class AppItem(
    val label: String,
    val packageName: String
  ) : SearchResult
}

enum class ActionType {
  WEB,
  PLAY_STORE,
  SETTINGS_WIFI,
  SETTINGS_BLUETOOTH,
  SETTINGS_BATTERY,
  SETTINGS_DISPLAY,
  SETTINGS_SOUND,
  SETTINGS_STORAGE,
  SETTINGS_LOCATION,
  SETTINGS_DATE,
  SETTINGS_DEVELOPER,
  SETTINGS_APPS,
  SETTINGS_SECURITY,
  SETTINGS_INFO,
  SETTINGS_LANGUAGE
}
