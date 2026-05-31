package com.example.universalsearch.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalsearch.R
import com.example.universalsearch.data.ActionType
import com.example.universalsearch.data.SafDirectory
import com.example.universalsearch.data.SearchResult
import com.example.universalsearch.utils.SearchHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchCategory {
  ALL, CONTACTS, MEDIA, DOCUMENTS, ACTIONS
}

// Data class to represent a settings shortcut definition
data class ShortcutDef(
  val actionType: ActionType,
  val labelRes: Int,
  val keywords: List<String>
)

// Data class to represent a launcher app shortcut definition
data class AppShortcutDef(
  val packageName: String,
  val defaultLabel: String,
  val keywords: List<String>
)


class SearchViewModel : ViewModel() {

  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  private val _selectedCategory = MutableStateFlow(SearchCategory.ALL)
  val selectedCategory: StateFlow<SearchCategory> = _selectedCategory.asStateFlow()

  private val _contactsPermissionGranted = MutableStateFlow(false)
  val contactsPermissionGranted: StateFlow<Boolean> = _contactsPermissionGranted.asStateFlow()

  private val _mediaPermissionGranted = MutableStateFlow(false)
  val mediaPermissionGranted: StateFlow<Boolean> = _mediaPermissionGranted.asStateFlow()

  private val _safDirectories = MutableStateFlow<List<SafDirectory>>(emptyList())
  val safDirectories: StateFlow<List<SafDirectory>> = _safDirectories.asStateFlow()

  private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
  val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _searchDuration = MutableStateFlow(0L)
  val searchDuration: StateFlow<Long> = _searchDuration.asStateFlow()

  private var searchJob: Job? = null

  fun setQuery(newQuery: String, context: Context) {
    _query.value = newQuery
    performSearch(context)
  }

  fun setCategory(category: SearchCategory) {
    _selectedCategory.value = category
  }

  fun setContactsPermissionGranted(granted: Boolean, context: Context) {
    _contactsPermissionGranted.value = granted
    performSearch(context)
  }

  fun setMediaPermissionGranted(granted: Boolean, context: Context) {
    _mediaPermissionGranted.value = granted
    performSearch(context)
  }

  fun setSafDirectories(directories: List<SafDirectory>, context: Context) {
    _safDirectories.value = directories
    performSearch(context)
  }

  private fun performSearch(context: Context) {
    val currentQuery = _query.value.trim()
    searchJob?.cancel()

    if (currentQuery.isEmpty()) {
      _searchResults.value = emptyList()
      _isSearching.value = false
      _searchDuration.value = 0L
      return
    }

    searchJob = viewModelScope.launch {
      _isSearching.value = true
      val startTime = System.currentTimeMillis()
      val q = currentQuery.lowercase()

      val results = mutableListOf<SearchResult>()

      // 1. Search Contacts (on Dispatchers.IO)
      val contactsList = if (_contactsPermissionGranted.value) {
        withContext(Dispatchers.IO) {
          SearchHelper.searchContacts(context, currentQuery)
        }
      } else {
        emptyList()
      }
      results.addAll(contactsList)

      // 2. Search Media files via MediaStore (on Dispatchers.IO)
      val filesList = if (_mediaPermissionGranted.value) {
        withContext(Dispatchers.IO) {
          SearchHelper.searchMediaStore(context, currentQuery)
        }
      } else {
        emptyList()
      }
      results.addAll(filesList)

      // 3. Search documents via SAF directories (on Dispatchers.IO)
      //    SAF is the only way to search non-media files on Android 13+ without MANAGE_EXTERNAL_STORAGE
      val safDirectoriesList = _safDirectories.value
      if (safDirectoriesList.isNotEmpty()) {
        val safList = withContext(Dispatchers.IO) {
          val allSafFiles = safDirectoriesList.flatMap { directory ->
            SearchHelper.searchDocumentsInDirectory(context, directory.uri, currentQuery)
          }
          allSafFiles.distinctBy { it.uri }
        }
        results.addAll(safList)
      }

      // 3.5. Search launcher apps (on Dispatchers.IO)
      val appsList = withContext(Dispatchers.IO) {
        val matchedApps = mutableListOf<SearchResult.AppItem>()
        val pm = context.packageManager
        for (app in APP_SHORTCUTS) {
          try {
            // Verify if app is installed by fetching its launch Intent
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            if (launchIntent != null) {
              // Get localized app name if possible
              val appInfo = pm.getApplicationInfo(app.packageName, 0)
              val label = pm.getApplicationLabel(appInfo).toString()
              val resolvedLabel = if (label.isNotEmpty()) label else app.defaultLabel
              
              // Perform query matching on packageName, resolved label and keywords
              val matches = resolvedLabel.lowercase().contains(q) ||
                            app.packageName.lowercase().contains(q) ||
                            app.keywords.any { keyword -> keyword.lowercase().contains(q) }
                            
              if (matches) {
                matchedApps.add(SearchResult.AppItem(resolvedLabel, app.packageName))
              }
            }
          } catch (e: Exception) {
            // App not installed or name not found
          }
        }
        matchedApps
      }
      results.addAll(appsList)

      // 4. Action Items (always add settings shortcuts if matched, plus search engines)
      val showAllSettings = q.contains("settings") || q.contains("设置")

      for (shortcut in SHORTCUTS_DICT) {
        val matches = showAllSettings || shortcut.keywords.any { keyword ->
          q.contains(keyword.lowercase())
        }
        if (matches) {
          results.add(
            SearchResult.ActionItem(
              label = context.getString(shortcut.labelRes),
              query = currentQuery,
              actionType = shortcut.actionType
            )
          )
        }
      }

      _searchResults.value = results
      _searchDuration.value = System.currentTimeMillis() - startTime
      _isSearching.value = false
    }
  }

  companion object {
    // Dictionary of settings shortcuts with Chinese and English keywords
    private val SHORTCUTS_DICT = listOf(
      ShortcutDef(
        ActionType.SETTINGS_WIFI,
        R.string.settings_wifi_label,
        listOf("wifi", "wlan", "无线")
      ),
      ShortcutDef(
        ActionType.SETTINGS_BLUETOOTH,
        R.string.settings_bluetooth_label,
        listOf("blue", "bluetooth", "蓝牙")
      ),
      ShortcutDef(
        ActionType.SETTINGS_BATTERY,
        R.string.settings_battery_label,
        listOf("battery", "power", "电池", "电量")
      ),
      ShortcutDef(
        ActionType.SETTINGS_DISPLAY,
        R.string.settings_display_label,
        listOf("display", "screen", "brightness", "屏幕", "显示", "亮度")
      ),
      ShortcutDef(
        ActionType.SETTINGS_SOUND,
        R.string.settings_sound_label,
        listOf("sound", "volume", "audio", "ring", "声音", "音量", "音频", "铃声")
      ),
      ShortcutDef(
        ActionType.SETTINGS_STORAGE,
        R.string.settings_storage_label,
        listOf("storage", "space", "memory", "存储", "空间", "内存")
      ),
      ShortcutDef(
        ActionType.SETTINGS_LOCATION,
        R.string.settings_location_label,
        listOf("location", "gps", "position", "定位", "位置")
      ),
      ShortcutDef(
        ActionType.SETTINGS_DATE,
        R.string.settings_date_label,
        listOf("date", "time", "clock", "calendar", "日期", "时间", "时钟")
      ),
      ShortcutDef(
        ActionType.SETTINGS_DEVELOPER,
        R.string.settings_developer_label,
        listOf("developer", "debug", "usb", "开发者", "调试")
      ),
      ShortcutDef(
        ActionType.SETTINGS_APPS,
        R.string.settings_apps_label,
        listOf("app", "apps", "application", "applications", "应用", "程序")
      ),
      ShortcutDef(
        ActionType.SETTINGS_SECURITY,
        R.string.settings_security_label,
        listOf("security", "password", "lock", "fingerprint", "安全", "密码", "锁定", "指纹")
      ),
      ShortcutDef(
        ActionType.SETTINGS_INFO,
        R.string.settings_info_label,
        listOf("info", "about", "device", "system", "关于", "设备", "系统")
      ),
      ShortcutDef(
        ActionType.SETTINGS_LANGUAGE,
        R.string.settings_language_label,
        listOf("language", "locale", "input", "keyboard", "语言", "输入法", "键盘")
      )
    )

    // Predefined popular launcher app package names and keywords for search queries
    private val APP_SHORTCUTS = listOf(
      AppShortcutDef(
        "com.android.chrome",
        "Chrome",
        listOf("chrome", "browser", "web", "谷歌浏览器", "浏览器")
      ),
      AppShortcutDef(
        "com.android.camera2",
        "Camera",
        listOf("camera", "photo", "video", "相机", "拍照", "照相机")
      ),
      AppShortcutDef(
        "com.android.settings",
        "Settings",
        listOf("settings", "config", "设置", "系统设置", "系统")
      ),
      AppShortcutDef(
        "com.google.android.youtube",
        "YouTube",
        listOf("youtube", "video", "media", "油管", "视频", "播放器")
      ),
      AppShortcutDef(
        "com.google.android.apps.maps",
        "Maps",
        listOf("maps", "google maps", "map", "navigation", "地图", "谷歌地图", "导航")
      ),
      AppShortcutDef(
        "com.google.android.apps.photos",
        "Photos",
        listOf("photos", "gallery", "images", "照片", "相册", "图库")
      ),
      AppShortcutDef(
        "com.google.android.apps.messaging",
        "Messages",
        listOf("messages", "sms", "text", "短信", "信息", "消息")
      ),
      AppShortcutDef(
        "com.google.android.apps.docs",
        "Drive",
        listOf("drive", "docs", "cloud", "云端硬盘", "硬盘", "文档")
      ),
      AppShortcutDef(
        "com.google.android.calendar",
        "Calendar",
        listOf("calendar", "schedule", "date", "日历", "日程", "时间")
      ),
      AppShortcutDef(
        "com.google.android.contacts",
        "Contacts",
        listOf("contacts", "people", "phonebook", "联系人", "电话本", "人名")
      ),
      AppShortcutDef(
        "com.google.android.deskclock",
        "Clock",
        listOf("clock", "alarm", "timer", "时钟", "闹钟", "时间", "秒表")
      ),
      AppShortcutDef(
        "com.google.android.dialer",
        "Phone",
        listOf("phone", "dialer", "call", "电话", "拨号", "通话")
      ),
      AppShortcutDef(
        "com.google.android.documentsui",
        "Files",
        listOf("files", "documents", "explorer", "文件", "文档", "文件管理器")
      ),
      AppShortcutDef(
        "com.google.android.gm",
        "Gmail",
        listOf("gmail", "email", "mail", "邮箱", "邮件", "信箱")
      ),
      AppShortcutDef(
        "com.google.android.googlequicksearchbox",
        "Google",
        listOf("google", "search", "gsa", "谷歌", "搜索")
      ),
      AppShortcutDef(
        "com.google.android.apps.youtube.music",
        "YouTube Music",
        listOf("music", "audio", "song", "音乐", "歌", "播放器")
      ),
      AppShortcutDef(
        "com.android.vending",
        "Play Store",
        listOf("play store", "vending", "market", "shop", "应用商店", "商店", "市场")
      ),
      AppShortcutDef(
        "com.tencent.mm",
        "WeChat",
        listOf("wechat", "weixin", "wx", "微信", "社群", "聊天")
      ),
      AppShortcutDef(
        "com.eg.android.AlipayGphone",
        "Alipay",
        listOf("alipay", "zhifubao", "zfb", "支付宝", "支付", "钱包")
      ),
      AppShortcutDef(
        "com.taobao.taobao",
        "Taobao",
        listOf("taobao", "shopping", "淘宝", "购物", "买")
      ),
      AppShortcutDef(
        "com.tencent.mobileqq",
        "QQ",
        listOf("qq", "chat", "social", "腾讯qq", "聊天", "社交")
      ),
      AppShortcutDef(
        "com.sina.weibo",
        "Weibo",
        listOf("weibo", "social", "news", "微博", "社交")
      ),
      AppShortcutDef(
        "com.android.stk",
        "SIM Toolkit",
        listOf("sim", "stk", "toolkit", "sim卡", "应用")
      ),
      AppShortcutDef(
        "com.google.android.apps.accessibility.voiceaccess",
        "Voice Access",
        listOf("voice access", "accessibility", "语音访问", "辅助功能")
      ),
      AppShortcutDef(
        "com.google.android.apps.safetyhub",
        "Personal Safety",
        listOf("safety", "personal safety", "emergency", "安全", "个人安全", "紧急")
      )
    )
  }
}


