package com.example.universalsearch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.universalsearch.data.SafDirectory
import com.example.universalsearch.theme.UniversalSearchTheme
import com.example.universalsearch.ui.SearchScreen
import com.example.universalsearch.ui.SearchViewModel

class MainActivity : ComponentActivity() {
  
  private val viewModel: SearchViewModel by viewModels()

  private val contactsPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    viewModel.setContactsPermissionGranted(isGranted, this)
  }

  private val voiceSearchLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == RESULT_OK) {
      val spokenText: String? =
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
      if (!spokenText.isNullOrEmpty()) {
        viewModel.setQuery(spokenText, this)
      }
    }
  }

  private val mediaPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    // Check each granular media permission (API 33+ only)
    val imagesGranted = permissions[android.Manifest.permission.READ_MEDIA_IMAGES] ?: false
    val videosGranted = permissions[android.Manifest.permission.READ_MEDIA_VIDEO] ?: false
    val audioGranted  = permissions[android.Manifest.permission.READ_MEDIA_AUDIO]  ?: false

    viewModel.setMediaPermissionGranted(
      imagesGranted || videosGranted || audioGranted,
      this
    )
  }

  private val safDirectoryLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
  ) { uri ->
    if (uri != null) {
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      try {
        contentResolver.takePersistableUriPermission(uri, takeFlags)
      } catch (e: Exception) {
        e.printStackTrace()
      }
      
      val dirName = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)?.name ?: getString(R.string.selected_folder_fallback)
      val sharedPrefs = getSharedPreferences("universal_search_prefs", Context.MODE_PRIVATE)
      val set = sharedPrefs.getStringSet("saf_directories", emptySet())?.toMutableSet() ?: mutableSetOf()
      
      val hasDuplicate = set.any { 
        try {
          Uri.parse(it.substringBefore("|")) == uri
        } catch (e: Exception) {
          false
        }
      }
      
      if (!hasDuplicate) {
        set.add("${uri}|${dirName}")
        sharedPrefs.edit().putStringSet("saf_directories", set).apply()
      }
      
      checkSafDirectories()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    setContent {
      UniversalSearchTheme {
        Surface(
          modifier = Modifier.fillMaxSize()
        ) {
          SearchScreen(
            viewModel = viewModel,
            onRequestContactsPermission = {
              contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            },
            onRequestMediaPermissions = {
              // Request granular media permissions (API 33+)
              mediaPermissionsLauncher.launch(
                arrayOf(
                  android.Manifest.permission.READ_MEDIA_IMAGES,
                  android.Manifest.permission.READ_MEDIA_VIDEO,
                  android.Manifest.permission.READ_MEDIA_AUDIO
                )
              )
            },
            onRequestAddSafDirectory = {
              safDirectoryLauncher.launch(null)
            },
            onRequestDocumentsDirectory = {
              // Pre-navigate SAF picker to the Documents folder (primary:Documents)
              val documentsHintUri = android.net.Uri.parse(
                "content://com.android.externalstorage.documents/document/primary%3ADocuments"
              )
              safDirectoryLauncher.launch(documentsHintUri)
            },
            onRemoveSafDirectory = { dir ->
              removeSafDirectory(dir)
            },
            onStartVoiceSearch = {
              startVoiceSearch()
            },
            modifier = Modifier.safeDrawingPadding()
          )
        }
      }
    }
  }

  private fun startVoiceSearch() {
    try {
      val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
          android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(
          android.speech.RecognizerIntent.EXTRA_PROMPT,
          getString(R.string.voice_search_prompt)
        )
      }
      voiceSearchLauncher.launch(intent)
    } catch (e: Exception) {
      e.printStackTrace()
      android.widget.Toast.makeText(
        this,
        getString(R.string.voice_search_not_supported),
        android.widget.Toast.LENGTH_SHORT
      ).show()
    }
  }

  override fun onResume() {
    super.onResume()
    checkPermissions()
  }

  private fun checkPermissions() {
    val contactsGranted = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    viewModel.setContactsPermissionGranted(contactsGranted, this)

    // Check granular media permissions (API 33+, READ_EXTERNAL_STORAGE not used)
    val mediaGranted =
      checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
      checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO)  == PackageManager.PERMISSION_GRANTED ||
      checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO)  == PackageManager.PERMISSION_GRANTED
    viewModel.setMediaPermissionGranted(mediaGranted, this)

    checkSafDirectories()
  }

  private fun removeSafDirectory(directory: SafDirectory) {
    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    try {
      contentResolver.releasePersistableUriPermission(directory.uri, takeFlags)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    
    val sharedPrefs = getSharedPreferences("universal_search_prefs", Context.MODE_PRIVATE)
    val set = sharedPrefs.getStringSet("saf_directories", emptySet())?.toMutableSet() ?: mutableSetOf()
    
    val targetItem = set.firstOrNull { 
      try {
        Uri.parse(it.substringBefore("|")) == directory.uri
      } catch (e: Exception) {
        false
      }
    }
    
    if (targetItem != null) {
      set.remove(targetItem)
      sharedPrefs.edit().putStringSet("saf_directories", set).apply()
    }
    
    checkSafDirectories()
  }

  private fun checkSafDirectories() {
    val sharedPrefs = getSharedPreferences("universal_search_prefs", Context.MODE_PRIVATE)
    val set = sharedPrefs.getStringSet("saf_directories", emptySet()) ?: emptySet()
    
    val validDirectories = mutableListOf<SafDirectory>()
    val updatedSet = mutableSetOf<String>()
    
    for (item in set) {
      try {
        val uriStr = item.substringBefore("|")
        val name = item.substringAfter("|", getString(R.string.selected_folder_fallback))
        val uri = Uri.parse(uriStr)
        
        val hasPermission = contentResolver.persistedUriPermissions.any { 
          it.uri == uri && it.isReadPermission 
        }
        
        if (hasPermission) {
          validDirectories.add(SafDirectory(uri, name))
          updatedSet.add(item)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
    
    if (updatedSet.size != set.size) {
      sharedPrefs.edit().putStringSet("saf_directories", updatedSet).apply()
    }
    
    viewModel.setSafDirectories(validDirectories, this)
  }
}

