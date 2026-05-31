package com.example.universalsearch.utils

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.universalsearch.R
import com.example.universalsearch.data.SearchResult
import com.example.universalsearch.data.ActionType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

object SearchHelper {

  // Search contacts using ContentResolver
  fun searchContacts(context: Context, query: String): List<SearchResult.ContactItem> {
    if (query.isBlank()) return emptyList()

    val contacts = mutableListOf<SearchResult.ContactItem>()
    val resolver: ContentResolver = context.contentResolver

    // Query both display name and phone number
    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
      ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
    val selectionArgs = arrayOf("%$query%", "%$query%")
    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

    var cursor: Cursor? = null
    try {
      cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
      cursor?.let {
        val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

        // Keep track of unique contact IDs or name-number combinations to avoid duplicates
        val seen = mutableSetOf<String>()

        while (it.moveToNext() && contacts.size < 50) { // Limit to 50 results
          val id = it.getString(idIdx)
          val name = it.getString(nameIdx) ?: context.getString(R.string.unknown)
          val number = it.getString(numIdx) ?: ""
          
          val key = "$name-$number"
          if (!seen.contains(key)) {
            seen.add(key)
            contacts.add(SearchResult.ContactItem(id, name, number))
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      cursor?.close()
    }

    return contacts
  }

  // Search images, videos, and audio from MediaStore database
  fun searchMediaStore(context: Context, query: String): List<SearchResult.MediaItem> {
    if (query.isBlank()) return emptyList()
    val results = mutableListOf<SearchResult.MediaItem>()
    val q = query.lowercase().trim()
    
    val isImageQuery = q in listOf("图片", "照片", "image", "images", "photo", "photos", "picture", "pictures")
    val isVideoQuery = q in listOf("视频", "录像", "video", "videos", "movie", "movies", "mp4")
    val isAudioQuery = q in listOf("音乐", "音频", "歌曲", "歌", "music", "audio", "song", "songs", "mp3", "aac", "wav", "flac", "m4a", "ogg")
    
    val isSpecificCategoryQuery = isImageQuery || isVideoQuery || isAudioQuery
    
    // 1. Search Images
    if (isImageQuery || !isSpecificCategoryQuery) {
      val selection = if (isImageQuery) null else "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
      val selectionArgs = if (isImageQuery) null else arrayOf("%$query%")
      searchMediaCategory(
        context = context,
        contentUri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection = arrayOf(
          android.provider.MediaStore.Images.Media._ID,
          android.provider.MediaStore.Images.Media.DISPLAY_NAME,
          android.provider.MediaStore.Images.Media.SIZE,
          android.provider.MediaStore.Images.Media.DATE_MODIFIED,
          android.provider.MediaStore.Images.Media.DATA
        ),
        selection = selection,
        selectionArgs = selectionArgs,
        results = results
      )
    }

    // 2. Search Videos
    if (isVideoQuery || !isSpecificCategoryQuery) {
      val selection = if (isVideoQuery) null else "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
      val selectionArgs = if (isVideoQuery) null else arrayOf("%$query%")
      searchMediaCategory(
        context = context,
        contentUri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        projection = arrayOf(
          android.provider.MediaStore.Video.Media._ID,
          android.provider.MediaStore.Video.Media.DISPLAY_NAME,
          android.provider.MediaStore.Video.Media.SIZE,
          android.provider.MediaStore.Video.Media.DATE_MODIFIED,
          android.provider.MediaStore.Video.Media.DATA
        ),
        selection = selection,
        selectionArgs = selectionArgs,
        results = results
      )
    }

    // 3. Search Audio
    if (isAudioQuery || !isSpecificCategoryQuery) {
      val selection = if (isAudioQuery) null else "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
      val selectionArgs = if (isAudioQuery) null else arrayOf("%$query%")
      searchMediaCategory(
        context = context,
        contentUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        projection = arrayOf(
          android.provider.MediaStore.Audio.Media._ID,
          android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
          android.provider.MediaStore.Audio.Media.SIZE,
          android.provider.MediaStore.Audio.Media.DATE_MODIFIED,
          android.provider.MediaStore.Audio.Media.DATA
        ),
        selection = selection,
        selectionArgs = selectionArgs,
        results = results
      )
    }
    
    // Sort by modified date descending
    results.sortByDescending { it.lastModified }
    return results.take(100)
  }

  private fun searchMediaCategory(
    context: Context,
    contentUri: Uri,
    projection: Array<String>,
    selection: String?,
    selectionArgs: Array<String>?,
    results: MutableList<SearchResult.MediaItem>
  ) {
    val resolver = context.contentResolver
    var cursor: Cursor? = null
    try {
      cursor = resolver.query(contentUri, projection, selection, selectionArgs, null)
      cursor?.let {
        val idIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
        val nameIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.SIZE)
        val dateIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
        val dataIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
        
        while (it.moveToNext() && results.size < 200) {
          val id = it.getLong(idIdx)
          val name = it.getString(nameIdx) ?: context.getString(R.string.unknown)
          val size = it.getLong(sizeIdx)
          val date = it.getLong(dateIdx) * 1000 // Convert to ms
          val path = it.getString(dataIdx) ?: ""
          
          val file = java.io.File(path)
          val fileUri = try {
            if (path.isNotEmpty()) {
              androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
              )
            } else {
              android.content.ContentUris.withAppendedId(contentUri, id)
            }
          } catch (e: Exception) {
            android.content.ContentUris.withAppendedId(contentUri, id)
          }
          
          results.add(
            SearchResult.MediaItem(
              name = name,
              path = if (path.isNotEmpty()) path else context.getString(R.string.shared_media_library),
              size = size,
              uri = fileUri,
              lastModified = date
            )
          )
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      cursor?.close()
    }
  }


  // Search non-media documents inside a SAF directory tree
  fun searchDocumentsInDirectory(
    context: Context,
    directoryUri: Uri,
    query: String
  ): List<SearchResult.DocumentItem> {
    if (query.isBlank()) return emptyList()
    val rootDoc = DocumentFile.fromTreeUri(context, directoryUri) ?: return emptyList()
    
    val list = mutableListOf<SearchResult.DocumentItem>()
    traverseDocuments(context, rootDoc, query, list)
    return list
  }

  private fun traverseDocuments(
    context: Context,
    directory: DocumentFile,
    query: String,
    results: MutableList<SearchResult.DocumentItem>
  ) {
    if (results.size >= 50) return // Safety limit
    val files = try {
      directory.listFiles()
    } catch (e: Exception) {
      emptyArray<DocumentFile>()
    }

    for (file in files) {
      if (results.size >= 50) return
      if (file.isDirectory) {
        traverseDocuments(context, file, query, results)
      } else {
        val name = file.name ?: ""
        if (matchesDocumentQuery(name, query)) {
          results.add(
            SearchResult.DocumentItem(
              name = name,
              path = Uri.decode(file.uri.path ?: ""),
              size = file.length(),
              uri = file.uri,
              lastModified = file.lastModified()
            )
          )
        }
      }
    }
  }

  private fun matchesDocumentQuery(fileName: String, query: String): Boolean {
    val name = fileName.lowercase()
    val q = query.lowercase().trim()
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val isDocExtension = ext in listOf("txt", "doc", "docx", "xls", "xlsx", "csv", "pdf", "zip", "rar", "7z", "tar", "gz", "apk")
    
    if (!isDocExtension) return false
    if (name.contains(q)) return true
    
    return when (q) {
      "文档", "文稿", "文件", "doc", "pdf", "txt", "document", "documents", "text" -> true
      "压缩包", "压缩", "zip", "rar", "7z" -> ext in listOf("zip", "rar", "7z", "tar", "gz")
      "apk", "安装包", "程序" -> ext == "apk"
      else -> false
    }
  }

  // Launch the system dialer for a given phone number
  fun openDialer(context: Context, phoneNumber: String) {
    try {
      val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:$phoneNumber")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // Launch the system SMS composer for a given phone number
  fun openSmsComposer(context: Context, phoneNumber: String) {
    try {
      val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:$phoneNumber")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // Open system settings based on action type
  fun openSettings(context: Context, actionType: ActionType) {
    try {
      val action = when (actionType) {
        ActionType.SETTINGS_WIFI -> android.provider.Settings.ACTION_WIFI_SETTINGS
        ActionType.SETTINGS_BLUETOOTH -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
        ActionType.SETTINGS_BATTERY -> Intent.ACTION_POWER_USAGE_SUMMARY
        ActionType.SETTINGS_DISPLAY -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
        ActionType.SETTINGS_SOUND -> android.provider.Settings.ACTION_SOUND_SETTINGS
        ActionType.SETTINGS_STORAGE -> android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS
        ActionType.SETTINGS_LOCATION -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
        ActionType.SETTINGS_DATE -> android.provider.Settings.ACTION_DATE_SETTINGS
        ActionType.SETTINGS_DEVELOPER -> android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        ActionType.SETTINGS_APPS -> android.provider.Settings.ACTION_APPLICATION_SETTINGS
        ActionType.SETTINGS_SECURITY -> android.provider.Settings.ACTION_SECURITY_SETTINGS
        ActionType.SETTINGS_INFO -> android.provider.Settings.ACTION_DEVICE_INFO_SETTINGS
        ActionType.SETTINGS_LANGUAGE -> android.provider.Settings.ACTION_LOCALE_SETTINGS
        else -> return
      }
      val intent = Intent(action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // Copy SAF/Content URI content to app's cache directory
  private fun copyUriToCache(context: Context, uri: Uri, fileName: String): java.io.File? {
    try {
      val cacheFile = java.io.File(context.cacheDir, fileName)
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        java.io.FileOutputStream(cacheFile).use { outputStream ->
          val buffer = ByteArray(4 * 1024)
          var read: Int
          while (inputStream.read(buffer).also { read = it } != -1) {
            outputStream.write(buffer, 0, read)
          }
          outputStream.flush()
        }
      }
      return cacheFile
    } catch (e: Exception) {
      e.printStackTrace()
      return null
    }
  }

  private fun getParentDirectoryUri(fileUri: Uri): Uri? {
    if (fileUri.authority == "com.android.externalstorage.documents") {
      val segments = fileUri.pathSegments
      val docIdIndex = segments.indexOf("document")
      if (docIdIndex != -1 && docIdIndex + 1 < segments.size) {
        val documentId = segments[docIdIndex + 1]
        val lastSlash = documentId.lastIndexOf('/')
        if (lastSlash != -1) {
          val parentDocId = documentId.substring(0, lastSlash)
          return Uri.Builder()
            .scheme("content")
            .authority("com.android.externalstorage.documents")
            .appendPath("document")
            .appendPath(parentDocId)
            .build()
        }
      }
    }
    return null
  }

  // Open a file using ACTION_VIEW directly (no chooser, with FLAG_GRANT_READ_URI_PERMISSION)
  fun openDocument(context: Context, fileUri: Uri, fileName: String) {
    try {
      if (fileName.endsWith(".apk", ignoreCase = true)) {
        val parentUri = getParentDirectoryUri(fileUri)
        if (parentUri != null) {
          android.widget.Toast.makeText(
            context,
            context.getString(R.string.toast_opening_files, fileName),
            android.widget.Toast.LENGTH_LONG
          ).show()

          val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(parentUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          context.startActivity(intent)
          return
        }
      }

      val isFileProviderUri = fileUri.authority == "${context.packageName}.fileprovider"
      var finalUri = fileUri
      
      // If it's not our own FileProvider URI (e.g. it's a SAF Uri), copy it to cache first
      if (!isFileProviderUri && fileUri.scheme == "content") {
        val cachedFile = copyUriToCache(context, fileUri, fileName)
        if (cachedFile != null) {
          finalUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cachedFile
          )
        }
      }

      val mimeType = context.contentResolver.getType(finalUri) ?: getMimeTypeFromExtension(fileName)
      val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(finalUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  // Open Play Store search page
  fun searchPlayStore(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    val marketUri = Uri.parse("market://search?q=$encodedQuery")
    val webUri = Uri.parse("https://play.google.com/store/search?q=$encodedQuery")
    
    val intent = Intent(Intent.ACTION_VIEW, marketUri)
    try {
      context.startActivity(intent)
    } catch (e: Exception) {
      // Fallback to browser if Google Play Store is not installed
      context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
    }
  }

  // Open web search in default browser
  fun searchWeb(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    val webUri = Uri.parse("https://www.google.com/search?q=$encodedQuery")
    val intent = Intent(Intent.ACTION_VIEW, webUri)
    context.startActivity(intent)
  }

  // Search within system settings
  fun searchSettings(context: Context, query: String) {
    try {
      val intent = Intent("com.android.settings.action.SETTINGS_SEARCH").apply {
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
      } catch (ex: Exception) {
        ex.printStackTrace()
      }
    }
  }

  // Search within YouTube
  fun searchYouTube(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try {
      val intent = Intent(Intent.ACTION_SEARCH).apply {
        setPackage("com.google.android.youtube")
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
        val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
      } catch (ex: Exception) {
        ex.printStackTrace()
      }
    }
  }

  // Search within Google Maps
  fun searchGoogleMaps(context: Context, query: String) {
    val encodedQuery = Uri.encode(query)
    try {
      val gmmIntentUri = Uri.parse("geo:0,0?q=$encodedQuery")
      val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      try {
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedQuery")
        val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
      } catch (ex: Exception) {
        ex.printStackTrace()
      }
    }
  }

  // Search within Gmail
  fun searchGmail(context: Context, query: String) {
    try {
      val intent = Intent(Intent.ACTION_SEARCH).apply {
        setPackage("com.google.android.gm")
        putExtra("query", query)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }


  // Get MIME type from file extension
  private fun getMimeTypeFromExtension(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
  }
}
