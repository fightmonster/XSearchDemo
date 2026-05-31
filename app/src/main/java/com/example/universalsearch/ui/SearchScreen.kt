package com.example.universalsearch.ui

import android.content.Context
import android.net.Uri
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import com.example.universalsearch.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.universalsearch.data.ActionType
import com.example.universalsearch.data.SearchResult
import com.example.universalsearch.utils.SearchHelper
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  viewModel: SearchViewModel,
  onRequestContactsPermission: () -> Unit,
  onRequestMediaPermissions: () -> Unit,
  onRequestAddSafDirectory: () -> Unit,
  onRequestDocumentsDirectory: () -> Unit,
  onRemoveSafDirectory: (com.example.universalsearch.data.SafDirectory) -> Unit,
  onStartVoiceSearch: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val query by viewModel.query.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
  val contactsPermissionGranted by viewModel.contactsPermissionGranted.collectAsStateWithLifecycle()
  val mediaPermissionGranted by viewModel.mediaPermissionGranted.collectAsStateWithLifecycle()
  val safDirectories by viewModel.safDirectories.collectAsStateWithLifecycle()
  val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val searchDuration by viewModel.searchDuration.collectAsStateWithLifecycle()

  // Auto-prompt Documents SAF picker on first Documents-category switch or first search
  val hasAutoPromptedDocuments = remember { mutableStateOf(false) }
  LaunchedEffect(selectedCategory, query) {
    if (!hasAutoPromptedDocuments.value && safDirectories.isEmpty()) {
      val shouldPrompt = selectedCategory == SearchCategory.DOCUMENTS ||
        (query.isNotEmpty() && selectedCategory == SearchCategory.ALL)
      if (shouldPrompt) {
        hasAutoPromptedDocuments.value = true
        onRequestDocumentsDirectory()
      }
    }
  }

  val pm = remember(context) { context.packageManager }
  val fromYourAppsList = remember(query, pm) {
    listOf(
      SearchAppShortcut(
        R.string.app_google,
        "com.google.android.googlequicksearchbox",
        Icons.Default.Search
      ) { ctx, q -> SearchHelper.searchWeb(ctx, q) },
      SearchAppShortcut(
        R.string.app_play_store,
        "com.android.vending",
        Icons.Default.ShoppingCart
      ) { ctx, q -> SearchHelper.searchPlayStore(ctx, q) },
      SearchAppShortcut(
        R.string.app_settings,
        "com.android.settings",
        Icons.Default.Settings
      ) { ctx, q -> SearchHelper.searchSettings(ctx, q) },
      SearchAppShortcut(
        R.string.app_youtube,
        "com.google.android.youtube",
        Icons.Default.PlayArrow
      ) { ctx, q -> SearchHelper.searchYouTube(ctx, q) },
      SearchAppShortcut(
        R.string.app_maps,
        "com.google.android.apps.maps",
        Icons.Default.LocationOn
      ) { ctx, q -> SearchHelper.searchGoogleMaps(ctx, q) },
      SearchAppShortcut(
        R.string.app_gmail,
        "com.google.android.gm",
        Icons.Default.Email
      ) { ctx, q -> SearchHelper.searchGmail(ctx, q) }
    )
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      )
    },
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    ) {
      // 1. Search Box
      OutlinedTextField(
        value = query,
        onValueChange = { viewModel.setQuery(it, context) },
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_icon_desc)) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { viewModel.setQuery("", context) }) {
              Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_icon_desc))
            }
          } else {
            IconButton(onClick = onStartVoiceSearch) {
              Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.mic_icon_desc))
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MaterialTheme.colorScheme.primary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
      )

      // 2. Category Chips Row
      CategoryChips(
        selectedCategory = selectedCategory,
        onCategorySelected = { viewModel.setCategory(it) }
      )

      // 3. Stats Card
      if (query.isNotEmpty()) {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val count = remember(searchResults, selectedCategory) {
              filterResults(searchResults, selectedCategory).size
            }
            Text(
              text = stringResource(R.string.results_count, count),
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.Medium
            )
            if (isSearching) {
              CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
              )
            } else {
              Text(
                text = stringResource(R.string.search_time, searchDuration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 4. Quick Permission Banners
      if (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.CONTACTS) {
        if (!contactsPermissionGranted) {
          PermissionBanner(
            title = stringResource(R.string.contacts_disabled_title),
            description = stringResource(R.string.contacts_disabled_desc),
            onClick = onRequestContactsPermission
          )
        }
      }

      if (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.MEDIA) {
        if (!mediaPermissionGranted) {
          PermissionBanner(
            title = stringResource(R.string.media_disabled_title),
            description = stringResource(R.string.media_disabled_desc),
            onClick = onRequestMediaPermissions
          )
        }
      }

      if (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.DOCUMENTS) {
        if (safDirectories.isEmpty()) {
          PermissionBanner(
            title = stringResource(R.string.documents_disabled_title),
            description = stringResource(R.string.documents_disabled_desc),
            onClick = onRequestDocumentsDirectory
          )
        } else {
          var isFoldersExpanded by remember { mutableStateOf(false) }
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .clickable { isFoldersExpanded = !isFoldersExpanded }
                    .padding(vertical = 4.dp)
                ) {
                  Text(
                    text = "${stringResource(R.string.documents_active_title)} (${safDirectories.size})",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    style = MaterialTheme.typography.titleSmall
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = if (isFoldersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                      if (isFoldersExpanded) R.string.collapse_folders_desc else R.string.expand_folders_desc
                    ),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                  )
                }
                TextButton(onClick = onRequestAddSafDirectory) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      Icons.Default.Add,
                      contentDescription = stringResource(R.string.add_dir_desc),
                      modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_add))
                  }
                }
              }
              if (isFoldersExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                safDirectories.forEach { directory ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      Icons.Default.Folder,
                      contentDescription = stringResource(R.string.folder_desc),
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = directory.name,
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.weight(1f)
                    )
                    IconButton(
                      onClick = { onRemoveSafDirectory(directory) },
                      modifier = Modifier.size(24.dp)
                    ) {
                      Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.remove_dir_desc),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 5. Search Results List
      val filteredList = remember(searchResults, selectedCategory) {
        filterResults(searchResults, selectedCategory)
      }

      LazyColumn(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (query.isNotEmpty() && filteredList.isEmpty()) {
          item {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(stringResource(R.string.no_results_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        } else {
          items(filteredList) { item ->
            SearchResultItem(
              item = item,
              query = query,
              onItemClick = { clickedItem ->
                handleItemClick(context, clickedItem)
              }
            )
          }
        }

        if (query.isNotEmpty() && (selectedCategory == SearchCategory.ALL || selectedCategory == SearchCategory.ACTIONS) && fromYourAppsList.isNotEmpty()) {
          item {
            FromYourAppsCard(
              fromYourAppsList = fromYourAppsList,
              query = query
            )
          }
        }
      }
    }
  }
}

@Composable
fun CategoryChips(
  selectedCategory: SearchCategory,
  onCategorySelected: (SearchCategory) -> Unit
) {
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    items(SearchCategory.values()) { category ->
      val label = when (category) {
        SearchCategory.ALL -> stringResource(R.string.category_all)
        SearchCategory.CONTACTS -> stringResource(R.string.category_contacts)
        SearchCategory.MEDIA -> stringResource(R.string.category_media)
        SearchCategory.DOCUMENTS -> stringResource(R.string.category_documents)
        SearchCategory.ACTIONS -> stringResource(R.string.category_shortcuts)
      }
      FilterChip(
        selected = selectedCategory == category,
        onClick = { onCategorySelected(category) },
        label = { Text(label) },
        shape = RoundedCornerShape(16.dp)
      )
    }
  }
}

@Composable
fun PermissionBanner(
  title: String,
  description: String,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 4.dp)
      .clickable { onClick() },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        Icons.Default.Warning,
        contentDescription = "Warning",
        tint = MaterialTheme.colorScheme.error
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }
    }
  }
}

@Composable
fun SearchResultItem(
  item: SearchResult,
  query: String,
  onItemClick: (SearchResult) -> Unit
) {
  val context = LocalContext.current
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onItemClick(item) },
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(all = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      when (item) {
        is SearchResult.ContactItem -> {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
          ) {
            val initial = if (item.name.isNotEmpty()) item.name.first().toString() else "?"
            Text(
              text = initial.uppercase(Locale.getDefault()),
              color = MaterialTheme.colorScheme.onPrimary,
              fontWeight = FontWeight.Bold
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            HighlightedText(text = item.name, query = query)
            Text(
              text = item.phoneNumber,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            IconButton(onClick = { SearchHelper.openDialer(context, item.phoneNumber) }) {
              Icon(
                Icons.Default.Phone,
                contentDescription = stringResource(R.string.action_call_desc),
                tint = MaterialTheme.colorScheme.primary
              )
            }
            IconButton(onClick = { SearchHelper.openSmsComposer(context, item.phoneNumber) }) {
              Icon(
                Icons.Default.Email,
                contentDescription = stringResource(R.string.action_sms_desc),
                tint = MaterialTheme.colorScheme.secondary
              )
            }
          }
        }
        is SearchResult.MediaItem -> {
          val fileIcon = remember(item.name) { getFileIcon(item.name) }
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              fileIcon,
              contentDescription = "File Type Icon",
              tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            HighlightedText(text = item.name, query = query)
            Text(
              text = item.path,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
              )
              Text(
                text = formatDate(context, item.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
              )
            }
          }
        }
        is SearchResult.DocumentItem -> {
          val fileIcon = remember(item.name) { getFileIcon(item.name) }
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              fileIcon,
              contentDescription = "File Type Icon",
              tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            HighlightedText(text = item.name, query = query)
            Text(
              text = item.path,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = formatFileSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
              )
              Text(
                text = formatDate(context, item.lastModified),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
              )
            }
          }
        }
        is SearchResult.AppItem -> {
          val appIcon = remember(item.packageName) {
            try {
              context.packageManager.getApplicationIcon(item.packageName)
            } catch (e: Exception) {
              null
            }
          }
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
          ) {
            if (appIcon != null) {
              AndroidView(
                factory = { ctx ->
                  ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                  }
                },
                update = { imageView ->
                  imageView.setImageDrawable(appIcon)
                },
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Icon(
                Icons.Default.Android,
                contentDescription = "App Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            HighlightedText(text = item.label, query = query)
            Text(
              text = item.packageName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1
            )
          }
        }
        is SearchResult.ActionItem -> {
          val actionIcon = when (item.actionType) {
            ActionType.PLAY_STORE -> Icons.Default.ShoppingCart
            ActionType.WEB -> Icons.Default.Search
            ActionType.SETTINGS_WIFI -> Icons.Default.Wifi
            ActionType.SETTINGS_BLUETOOTH -> Icons.Default.Bluetooth
            ActionType.SETTINGS_BATTERY -> Icons.Default.BatteryChargingFull
            ActionType.SETTINGS_DISPLAY -> Icons.Default.BrightnessMedium
            ActionType.SETTINGS_SOUND -> Icons.Default.VolumeUp
            ActionType.SETTINGS_STORAGE -> Icons.Default.Storage
            ActionType.SETTINGS_LOCATION -> Icons.Default.LocationOn
            ActionType.SETTINGS_DATE -> Icons.Default.DateRange
            ActionType.SETTINGS_DEVELOPER -> Icons.Default.Code
            ActionType.SETTINGS_APPS -> Icons.Default.Apps
            ActionType.SETTINGS_SECURITY -> Icons.Default.Lock
            ActionType.SETTINGS_INFO -> Icons.Default.Info
            ActionType.SETTINGS_LANGUAGE -> Icons.Default.Translate
          }
          val iconTint = when (item.actionType) {
            ActionType.SETTINGS_WIFI -> MaterialTheme.colorScheme.primary
            ActionType.SETTINGS_BLUETOOTH -> MaterialTheme.colorScheme.secondary
            ActionType.SETTINGS_BATTERY -> Color(0xFF4CAF50) // Green
            ActionType.SETTINGS_DISPLAY -> Color(0xFFFF9800) // Orange
            ActionType.SETTINGS_SOUND -> Color(0xFF00BCD4) // Cyan
            ActionType.SETTINGS_STORAGE -> Color(0xFF9C27B0) // Purple
            ActionType.SETTINGS_LOCATION -> Color(0xFFE91E63) // Pink
            ActionType.SETTINGS_DATE -> Color(0xFF3F51B5) // Indigo
            ActionType.SETTINGS_DEVELOPER -> Color(0xFF607D8B) // Blue Grey
            ActionType.SETTINGS_APPS -> Color(0xFF673AB7) // Deep Purple
            ActionType.SETTINGS_SECURITY -> Color(0xFFF44336) // Red
            ActionType.SETTINGS_INFO -> Color(0xFF009688) // Teal
            ActionType.SETTINGS_LANGUAGE -> Color(0xFF03A9F4) // Light Blue
            else -> MaterialTheme.colorScheme.onSecondaryContainer
          }
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              actionIcon,
              contentDescription = "Action Icon",
              tint = iconTint
            )
          }
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = item.label,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyLarge
            )
            val isSetting = item.actionType.name.startsWith("SETTINGS_")
            Text(
              text = if (isSetting) stringResource(R.string.category_shortcuts) else stringResource(R.string.search_online_desc, item.query),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}


@Composable
fun HighlightedText(
  text: String,
  query: String,
  color: Color = MaterialTheme.colorScheme.primary
) {
  val annotatedString = remember(text, query) {
    buildAnnotatedString {
      if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
      }
      var start = 0
      val lowerText = text.lowercase(Locale.getDefault())
      val lowerQuery = query.lowercase(Locale.getDefault())

      while (true) {
        val index = lowerText.indexOf(lowerQuery, start)
        if (index == -1) {
          append(text.substring(start))
          break
        }
        append(text.substring(start, index))
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
          append(text.substring(index, index + query.length))
        }
        start = index + query.length
      }
    }
  }
  Text(text = annotatedString, style = MaterialTheme.typography.bodyLarge)
}

// Helpers
private fun filterResults(results: List<SearchResult>, category: SearchCategory): List<SearchResult> {
  return when (category) {
    SearchCategory.ALL -> results
    SearchCategory.CONTACTS -> results.filterIsInstance<SearchResult.ContactItem>()
    SearchCategory.MEDIA -> results.filterIsInstance<SearchResult.MediaItem>()
    SearchCategory.DOCUMENTS -> results.filterIsInstance<SearchResult.DocumentItem>()
    SearchCategory.ACTIONS -> results.filter { it is SearchResult.ActionItem || it is SearchResult.AppItem }
  }
}

private fun handleItemClick(context: Context, item: SearchResult) {
  when (item) {
    is SearchResult.ContactItem -> {
      SearchHelper.openDialer(context, item.phoneNumber)
    }
    is SearchResult.MediaItem -> {
      SearchHelper.openDocument(context, item.uri, item.name)
    }
    is SearchResult.DocumentItem -> {
      SearchHelper.openDocument(context, item.uri, item.name)
    }
    is SearchResult.ActionItem -> {
      when (item.actionType) {
        ActionType.PLAY_STORE -> SearchHelper.searchPlayStore(context, item.query)
        ActionType.WEB -> SearchHelper.searchWeb(context, item.query)
        else -> {
          if (item.actionType.name.startsWith("SETTINGS_")) {
            SearchHelper.openSettings(context, item.actionType)
          }
        }
      }
    }
    is SearchResult.AppItem -> {
      try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (launchIntent != null) {
          context.startActivity(launchIntent)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }
}

private fun getFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
  val ext = fileName.substringAfterLast('.', "").lowercase()
  return when (ext) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp" -> Icons.Default.Image
    "mp4", "mkv", "avi", "webm" -> Icons.Default.Movie
    "mp3", "wav", "ogg", "m4a", "flac" -> Icons.Default.MusicNote
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub" -> Icons.Default.Description
    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Build
    "apk" -> Icons.Default.Android
    else -> Icons.Default.Info
  }
}

private fun formatFileSize(bytes: Long): String {
  if (bytes <= 0) return "0 B"
  val units = arrayOf("B", "KB", "MB", "GB", "TB")
  val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
  return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatDate(context: Context, timestamp: Long): String {
  if (timestamp <= 0) return context.getString(R.string.unknown_date)
  val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
  return sdf.format(Date(timestamp))
}

data class SearchAppShortcut(
  val nameRes: Int,
  val packageName: String,
  val defaultIcon: androidx.compose.ui.graphics.vector.ImageVector,
  val action: (Context, String) -> Unit
)

@Composable
fun FromYourAppsCard(
  fromYourAppsList: List<SearchAppShortcut>,
  query: String,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(all = 16.dp)
    ) {
      Text(
        text = stringResource(R.string.from_your_apps),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
      )
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val rowChunks = remember(fromYourAppsList) { fromYourAppsList.chunked(3) }
        rowChunks.forEach { rowApps ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            rowApps.forEach { app ->
              FromYourAppShortcutItem(app = app, query = query)
            }
            if (rowApps.size < 3) {
              repeat(3 - rowApps.size) {
                Spacer(modifier = Modifier.width(72.dp))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun FromYourAppShortcutItem(
  app: SearchAppShortcut,
  query: String
) {
  val context = LocalContext.current
  val appIcon = remember(app.packageName) {
    try {
      context.packageManager.getApplicationIcon(app.packageName)
    } catch (e: Exception) {
      null
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .width(72.dp)
      .clip(RoundedCornerShape(8.dp))
      .clickable { app.action(context, query) }
      .padding(vertical = 6.dp)
  ) {
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      if (appIcon != null) {
        AndroidView(
          factory = { ctx ->
            ImageView(ctx).apply {
              scaleType = ImageView.ScaleType.FIT_CENTER
            }
          },
          update = { imageView ->
            imageView.setImageDrawable(appIcon)
          },
          modifier = Modifier.fillMaxSize().padding(8.dp)
        )
      } else {
        Icon(
          imageVector = app.defaultIcon,
          contentDescription = stringResource(app.nameRes),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(24.dp)
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = stringResource(app.nameRes),
      style = MaterialTheme.typography.labelSmall,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
