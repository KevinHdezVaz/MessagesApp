package com.goodwy.smsmessenger.activities

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import androidx.work.BackoffPolicy
import androidx.work.WorkRequest
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

import android.os.Bundle
import android.provider.Telephony
import android.speech.RecognizerIntent
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.content.res.AppCompatResources
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.goodwy.commons.dialogs.PermissionRequiredDialog
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.*
import com.goodwy.commons.models.Release
import com.goodwy.smsmessenger.BuildConfig
import com.goodwy.smsmessenger.R
import com.goodwy.smsmessenger.adapters.ConversationsAdapter
import com.goodwy.smsmessenger.adapters.SearchResultsAdapter
import com.goodwy.smsmessenger.data.PreferencesManager
import com.goodwy.smsmessenger.databinding.ActivityMainBinding
import com.goodwy.smsmessenger.dialogs.LoginRequiredDialog
import com.goodwy.smsmessenger.extensions.*
import com.goodwy.smsmessenger.helpers.SEARCHED_MESSAGE_ID
import com.goodwy.smsmessenger.helpers.THREAD_ID
import com.goodwy.smsmessenger.helpers.THREAD_TITLE
import com.goodwy.smsmessenger.models.Conversation
import com.goodwy.smsmessenger.models.Events
import com.goodwy.smsmessenger.models.Message
import com.goodwy.smsmessenger.models.SearchResult
import com.goodwy.smsmessenger.workers.DeviceRegistrationWorker
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class MainActivity : SimpleActivity() {
    private val MAKE_DEFAULT_APP_REQUEST = 1

    private var storedPrimaryColor = 0
    private var storedTextColor = 0
    private var storedBackgroundColor = 0
    private var storedFontSize = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var isSpeechToTextAvailable = false

    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var preferencesManager: PreferencesManager

    private val binding by viewBinding(ActivityMainBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        preferencesManager = PreferencesManager.getInstance(this)

        setupNavigationDrawer()
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()

        requestSmsPermissions()
        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.mainCoordinator,
            nestedView = binding.conversationsList,
            useTransparentNavigation = true,
            useTopSearchMenu = true
        )
        initializeFCM()

        if (config.changeColourTopBar) {
            val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
            setupSearchMenuScrollListener(
                scrollingView = binding.conversationsList,
                searchMenu = binding.mainMenu,
                surfaceColor = useSurfaceColor
            )
        }

        checkWhatsNewDialog()
        storeStateVariables()
        checkAndDeleteOldRecycleBinMessages()

        clearAllMessagesIfNeeded {
            loadMessages()
        }

        binding.mainMenu.updateTitle(getString(R.string.messages))
    }


    private fun initializeFCM() {
        try {
            // ✅ Verificar si Firebase ya está inicializado
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            // ✅ Si no está inicializado, inicializarlo ahora
            Log.w("MainActivity", "Firebase no estaba inicializado, inicializando ahora...")
            FirebaseApp.initializeApp(this)
        }

        // Ahora sí, obtener el token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("MainActivity", "FCM Token: $token")

            // Guardar token
            lifecycleScope.launch {
                preferencesManager.saveFcmToken(token)

                // Si no está registrado, registrar ahora
                if (!preferencesManager.isDeviceRegisteredSync()) {
                    enqueueDeviceRegistration(token)
                }
            }
        }
    }
    // ✅ AGREGAR ESTA FUNCIÓN
    private fun enqueueDeviceRegistration(token: String) {
        val workData = workDataOf(
            DeviceRegistrationWorker.KEY_FCM_TOKEN to token
        )

        val workRequest = OneTimeWorkRequestBuilder<DeviceRegistrationWorker>()
            .setInputData(workData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun requestSmsPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissionsToRequest = mutableListOf<String>()

            if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.RECEIVE_SMS)
            }

            if (checkSelfPermission(android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_SMS)
            }

            if (checkSelfPermission(android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.SEND_SMS)
            }

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissions(permissionsToRequest.toTypedArray(), 100)
            }
        }
    }


    @SuppressLint("UnsafeIntentLaunch")
    override fun onResume() {
        super.onResume()

        checkLoginStatus()
        updateDrawerColors()

        if (config.needRestart || storedBackgroundColor != getProperBackgroundColor()) {
            finish()
            startActivity(intent)
            return
        }

        updateMenuColors()
        refreshMenuItems()

        getOrCreateConversationsAdapter().apply {
            if (storedPrimaryColor != getProperPrimaryColor()) {
                updatePrimaryColor()
            }

            if (storedTextColor != getProperTextColor()) {
                updateTextColor(getProperTextColor())
            }

            if (storedBackgroundColor != getProperBackgroundColor()) {
                updateBackgroundColor(getProperBackgroundColor())
            }

            if (storedFontSize != config.fontSize) {
                updateFontSize()
            }

            updateDrafts()
        }

        updateTextColors(binding.mainCoordinator)
        binding.searchHolder.setBackgroundColor(getProperBackgroundColor())

        val properPrimaryColor = getProperPrimaryColor()
        binding.noConversationsPlaceholder2.setTextColor(properPrimaryColor)
        binding.noConversationsPlaceholder2.underlineText()
        binding.conversationsFastscroller.updateColors(properPrimaryColor)
        binding.conversationsProgressBar.setIndicatorColor(properPrimaryColor)
        binding.conversationsProgressBar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)
        checkShortcut()
        binding.conversationsFastscroller.trackMarginEnd = navigationBarHeight
        (binding.conversationsFab.layoutParams as? CoordinatorLayout.LayoutParams)?.bottomMargin =
            navigationBarHeight + resources.getDimension(com.goodwy.commons.R.dimen.activity_margin).toInt()

        binding.conversationsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hideKeyboard()
            }
        })

        binding.searchResultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                hideKeyboard()
            }
        })
    }


    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.needRestart = false
        bus?.unregister(this)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else if (binding.mainMenu.isSearchOpen) {
            binding.mainMenu.closeSearch()
        } else {
            appLockManager.lock()
            super.onBackPressed()
        }
    }

    private fun checkLoginStatus() {
        val isLoggedIn = preferencesManager.isLoggedInSync()

        if (!isLoggedIn) {
        //    showLoginRequiredDialog()
        } else {
          //  updateDrawerUserInfo()
        }
    }

    private fun showLoginRequiredDialog() {
        LoginRequiredDialog(
            activity = this,
            onSignInClick = {
                Intent(this, LoginActivity::class.java).apply {
                    startActivity(this)
                }
            },
            onSkipClick = {
                toast(R.string.limited_features_without_login)
            }
        )
    }

    private fun updateDrawerUserInfo() {
        val headerView = binding.navView.getHeaderView(0)
        val titleView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_title)
        val subtitleView = headerView.findViewById<android.widget.TextView>(R.id.nav_header_subtitle)

        val email = preferencesManager.getUserEmailSync()
        val displayName = preferencesManager.getDisplayNameSync()

        if (email != null) {
            titleView?.text = displayName ?: email
            subtitleView?.text = getString(R.string.premium_account)
        } else {
            titleView?.text = getString(R.string.app_name)
            subtitleView?.text = getString(R.string.tap_to_sign_in)
        }
    }

    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.mainMenu.getToolbar(),
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        drawerToggle.drawerArrowDrawable.color = getProperTextColor()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            handleNavigationItemSelected(menuItem)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val headerView = binding.navView.getHeaderView(0)
        headerView.setOnClickListener {
            val isLoggedIn = preferencesManager.isLoggedInSync()
            if (!isLoggedIn) {
                Intent(this, LoginActivity::class.java).apply {
                    startActivity(this)
                }
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        updateDrawerColors()
    }

    private fun handleNavigationItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.nav_messages -> true
            R.id.nav_archived -> {
                launchArchivedConversations()
                true
            }
            R.id.nav_recycle_bin -> {
                launchRecycleBin()
                true
            }
            R.id.nav_blocked -> {
                showBlockedNumbers()
                true
            }
            R.id.nav_settings -> {
                launchSettings()
                true
            }
            R.id.nav_about -> {
                launchAbout()
                true
            }
            else -> false
        }
    }

    private fun updateDrawerColors() {
        val headerView = binding.navView.getHeaderView(0)
        headerView.setBackgroundColor(getProperPrimaryColor())

        binding.navView.apply {
            setBackgroundColor(getProperBackgroundColor())
            itemTextColor = ColorStateList.valueOf(getProperTextColor())
            itemIconTintList = ColorStateList.valueOf(getProperTextColor())
        }
    }

    private fun setupOptionsMenu() {
        binding.apply {
            mainMenu.getToolbar().inflateMenu(R.menu.menu_main)
            mainMenu.toggleHideOnScroll(config.hideTopBarWhenScroll)

            if (baseConfig.useSpeechToText) {
                isSpeechToTextAvailable = isSpeechToTextAvailable()
                mainMenu.showSpeechToText = isSpeechToTextAvailable
            }
            mainMenu.setupMenu()

            mainMenu.onSpeechToTextClickListener = {
                speechToText()
            }

            mainMenu.onSearchClosedListener = {
                fadeOutSearch()
            }

            mainMenu.onSearchTextChangedListener = { text ->
                if (text.isNotEmpty()) {
                    if (binding.searchHolder.alpha < 1f) {
                        binding.searchHolder.fadeIn()
                    }
                } else {
                    fadeOutSearch()
                }
                searchTextChanged(text)
                mainMenu.clearSearch()
            }

            mainMenu.getToolbar().setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.show_recycle_bin -> launchRecycleBin()
                    R.id.show_archived -> launchArchivedConversations()
                    R.id.show_blocked_numbers -> showBlockedNumbers()
                    R.id.settings -> launchSettings()
                    R.id.about -> launchAbout()
                    else -> return@setOnMenuItemClickListener false
                }
                return@setOnMenuItemClickListener true
            }

            mainMenu.clearSearch()
        }
    }

    private fun refreshMenuItems() {
        binding.mainMenu.getToolbar().menu.apply {
            findItem(R.id.show_recycle_bin).isVisible = config.useRecycleBin
            findItem(R.id.show_archived).isVisible = config.isArchiveAvailable
            findItem(R.id.show_blocked_numbers).title =
                if (config.showBlockedNumbers) getString(com.goodwy.strings.R.string.hide_blocked_numbers)
                else getString(com.goodwy.strings.R.string.show_blocked_numbers)
        }
    }

    private fun showBlockedNumbers() {
        config.showBlockedNumbers = !config.showBlockedNumbers
        binding.mainMenu.getToolbar().menu.findItem(R.id.show_blocked_numbers).title =
            if (config.showBlockedNumbers) getString(com.goodwy.strings.R.string.hide_blocked_numbers)
            else getString(com.goodwy.strings.R.string.show_blocked_numbers)
        initMessenger()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (resultCode == RESULT_OK) {
                askPermissions()
            } else {
                finish()
            }
        } else if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK) {
            if (resultData != null) {
                val res: ArrayList<String> =
                    resultData.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>
                val speechToText = Objects.requireNonNull(res)[0]
                if (speechToText.isNotEmpty()) {
                    binding.mainMenu.setText(speechToText)
                }
            }
        }
    }

    private fun storeStateVariables() {
        storedPrimaryColor = getProperPrimaryColor()
        storedTextColor = getProperTextColor()
        storedBackgroundColor = getProperBackgroundColor()
        storedFontSize = config.fontSize
        config.needRestart = false
    }

    private fun updateMenuColors() {
        val useSurfaceColor = isDynamicTheme() && !isSystemInDarkMode()
        val backgroundColor = if (useSurfaceColor) getSurfaceColor() else getProperBackgroundColor()
        val statusBarColor = if (config.changeColourTopBar) getRequiredStatusBarColor(useSurfaceColor) else backgroundColor

        updateStatusbarColor(backgroundColor)
        binding.mainMenu.updateColors(statusBarColor, scrollingView?.computeVerticalScrollOffset() ?: 0)
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager!!.isRoleAvailable(RoleManager.ROLE_SMS)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else {
                toast(com.goodwy.commons.R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) {
            if (it) {
                handlePermission(PERMISSION_SEND_SMS) {
                    if (it) {
                        handlePermission(PERMISSION_READ_CONTACTS) {
                            handleNotificationPermission { granted ->
                                if (!granted) {
                                    PermissionRequiredDialog(
                                        activity = this,
                                        textId = com.goodwy.commons.R.string.allow_notifications_incoming_messages,
                                        positiveActionCallback = { openNotificationSettings() })
                                }
                            }

                            initMessenger()
                            bus = EventBus.getDefault()
                            try {
                                bus!!.register(this)
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        finish()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun initMessenger() {
        getCachedConversations()
        binding.noConversationsPlaceholder2.setOnClickListener {
            launchNewConversation()
        }

        binding.conversationsFab.setOnClickListener {
            launchNewConversation()
        }
    }

    private fun getCachedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) {
                ArrayList()
            }

            val archived = try {
                conversationsDB.getAllArchived()
            } catch (_: Exception) {
                listOf()
            }

            runOnUiThread {
                setupConversations(conversations, cached = true)
                getNewConversations(
                    (conversations + archived).toMutableList() as ArrayList<Conversation>
                )
            }
            conversations.forEach {
                clearExpiredScheduledMessages(it.threadId)
            }
        }
    }

    private fun getNewConversations(cachedConversations: ArrayList<Conversation>) {
        val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
        ensureBackgroundThread {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)

            conversations.forEach { clonedConversation ->
                val threadIds = cachedConversations.map { it.threadId }
                if (!threadIds.contains(clonedConversation.threadId)) {
                    conversationsDB.insertOrUpdate(clonedConversation)
                    cachedConversations.add(clonedConversation)
                }
            }

            cachedConversations.forEach { cachedConversation ->
                val threadId = cachedConversation.threadId

                val isTemporaryThread = cachedConversation.isScheduled
                val isConversationDeleted = !conversations.map { it.threadId }.contains(threadId)
                if (isConversationDeleted && !isTemporaryThread) {
                    conversationsDB.deleteThreadId(threadId)
                }

                val newConversation =
                    conversations.find { it.phoneNumber == cachedConversation.phoneNumber }
                if (isTemporaryThread && newConversation != null) {
                    conversationsDB.deleteThreadId(threadId)
                    messagesDB.getScheduledThreadMessages(threadId)
                        .forEach { message ->
                            messagesDB.insertOrUpdate(
                                message.copy(threadId = newConversation.threadId)
                            )
                        }
                    insertOrUpdateConversation(newConversation, cachedConversation)
                }
            }

            cachedConversations.forEach { cachedConv ->
                val conv = conversations.find {
                    it.threadId == cachedConv.threadId && !Conversation.areContentsTheSame(
                        old = cachedConv, new = it
                    )
                }
                if (conv != null) {
                    insertOrUpdateConversation(conv)
                }
            }

            val allConversations = conversationsDB.getNonArchived() as ArrayList<Conversation>
            runOnUiThread {
                setupConversations(allConversations)
            }

            if (config.appRunCount == 1) {
                conversations.map { it.threadId }.forEach { threadId ->
                    val messages = getMessages(
                        threadId = threadId,
                        getImageResolutions = false,
                        includeScheduledMessages = false
                    )
                    messages.chunked(30).forEach { currentMessages ->
                        messagesDB.insertMessages(*currentMessages.toTypedArray())
                    }
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        if (isDynamicTheme() && !isSystemInDarkMode()) {
            binding.conversationsList.setBackgroundColor(getSurfaceColor())
        }

        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as ConversationsAdapter
    }

    private fun setupConversations(
        conversations: ArrayList<Conversation>,
        cached: Boolean = false,
    ) {
        val sortedConversations = if (config.unreadAtTop) {
            conversations.sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }
                    .thenBy { it.read }
                    .thenByDescending { it.date }
            ).toMutableList() as ArrayList<Conversation>
        } else {
            conversations.sortedWith(
                compareByDescending<Conversation> {
                    config.pinnedConversations.contains(it.threadId.toString())
                }
                    .thenByDescending { it.date }
                    .thenByDescending { it.isGroupConversation }
            ).toMutableList() as ArrayList<Conversation>
        }

        if (cached && config.appRunCount == 1) {
            showOrHideProgress(conversations.isEmpty())
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations) {
                    if (!cached) {
                        showOrHidePlaceholder(currentList.isEmpty())
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = getString(R.string.no_conversations_found)
        binding.noConversationsPlaceholder2.beVisibleIf(show)
    }

    private fun fadeOutSearch() {
        binding.searchHolder.animate()
            .alpha(0f)
            .setDuration(SHORT_ANIMATION_DURATION)
            .withEndAction {
                binding.searchHolder.beGone()
                searchTextChanged("", true)
            }.start()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        Intent(this, NewConversationActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun checkShortcut() {
        val iconColor = getProperPrimaryColor()
        if (config.lastHandledShortcutColor != iconColor) {
            val newConversation = getCreateNewContactShortcut(iconColor)

            val manager = getSystemService(ShortcutManager::class.java)
            try {
                manager.dynamicShortcuts = listOf(newConversation)
                config.lastHandledShortcutColor = iconColor
            } catch (_: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getCreateNewContactShortcut(iconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.new_conversation)
        val drawable = AppCompatResources.getDrawable(this, R.drawable.shortcut_plus)

        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_plus_background)
            .applyColorFilter(iconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, NewConversationActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "new_conversation")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun searchTextChanged(text: String, forceUpdate: Boolean = false) {
        if (!binding.mainMenu.isSearchOpen && !forceUpdate) {
            return
        }

        lastSearchedText = text
        binding.searchPlaceholder2.beGoneIf(text.length >= 2)
        if (text.length >= 2) {
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                val messages = messagesDB.getMessagesWithText(searchQuery)
                val conversations = conversationsDB.getConversationsWithText(searchQuery)
                if (text == lastSearchedText) {
                    showSearchResults(messages, conversations, text)
                }
            }
        } else {
            binding.searchPlaceholder.beVisible()
            binding.searchResultsList.beGone()
        }
        binding.mainMenu.clearSearch()
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
    ) {
        val searchResults = ArrayList<SearchResult>()
        conversations.forEach { conversation ->
            val date = (conversation.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )

            val searchResult = SearchResult(
                messageId = -1,
                title = conversation.title,
                phoneNumber = conversation.phoneNumber,
                snippet = conversation.phoneNumber,
                date = date,
                threadId = conversation.threadId,
                photoUri = conversation.photoUri,
                isCompany = conversation.isCompany,
                isBlocked = conversation.isBlocked
            )
            searchResults.add(searchResult)
        }

        messages.sortedByDescending { it.id }.forEach { message ->
            var recipient = message.senderName
            if (recipient.isEmpty() && message.participants.isNotEmpty()) {
                val participantNames = message.participants.map { it.name }
                recipient = TextUtils.join(", ", participantNames)
            }

            val phoneNumber = message.participants.firstOrNull()!!.phoneNumbers.firstOrNull()!!.normalizedNumber
            val date = (message.date * 1000L).formatDateOrTime(
                context = this,
                hideTimeOnOtherDays = true,
                showCurrentYear = true
            )
            val isCompany =
                if (message.participants.size == 1) message.participants.first().isABusinessContact() else false

            val searchResult = SearchResult(
                messageId = message.id,
                title = recipient,
                phoneNumber = phoneNumber,
                snippet = message.body,
                date = date,
                threadId = message.threadId,
                photoUri = message.senderPhotoUri,
                isCompany = isCompany
            )
            searchResults.add(searchResult)
        }

        runOnUiThread {
            binding.searchResultsList.beVisibleIf(searchResults.isNotEmpty())
            binding.searchPlaceholder.beVisibleIf(searchResults.isEmpty())

            val currAdapter = binding.searchResultsList.adapter
            if (currAdapter == null) {
                SearchResultsAdapter(this, searchResults, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.apply {
                    binding.searchResultsList.adapter = this
                }
            } else {
                (currAdapter as SearchResultsAdapter).updateItems(searchResults, searchedText)
            }
        }
    }

    private fun launchRecycleBin() {
        hideKeyboard()
        startActivity(Intent(applicationContext, LoginActivity::class.java))
    }

    private fun launchArchivedConversations() {
        hideKeyboard()
        startActivity(Intent(applicationContext, LoginActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        initMessenger()
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(420, R.string.release_420))
            add(Release(421, R.string.release_421))
            add(Release(423, R.string.release_423))
            add(Release(500, R.string.release_500))
            add(Release(501, R.string.release_501))
            add(Release(510, R.string.release_510))
            add(Release(511, R.string.release_511))
            add(Release(512, R.string.release_512))
            add(Release(513, R.string.release_513))
            add(Release(514, R.string.release_514))
            add(Release(515, R.string.release_515))
            add(Release(520, R.string.release_520))
            add(Release(521, R.string.release_521))
            add(Release(610, R.string.release_610))
            add(Release(611, R.string.release_611))
            add(Release(620, R.string.release_620))
            add(Release(630, R.string.release_630))
            add(Release(631, R.string.release_631))
            add(Release(632, R.string.release_632))
            add(Release(633, R.string.release_633))
            add(Release(700, R.string.release_700))
            add(Release(701, R.string.release_701))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
