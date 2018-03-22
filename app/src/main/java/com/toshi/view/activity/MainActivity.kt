package com.toshi.view.activity

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.aurelhubert.ahbottomnavigation.AHBottomNavigation
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationAdapter
import com.toshi.R
import com.toshi.extensions.getColorById
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.util.SoundManager
import com.toshi.view.adapter.NavigationAdapter
import com.toshi.view.fragment.toplevel.BackableTopLevelFragment
import com.toshi.view.fragment.toplevel.DappFragment
import com.toshi.view.fragment.toplevel.TopLevelFragment
import com.toshi.viewModel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.fragmentContainer
import kotlinx.android.synthetic.main.activity_main.navBar

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA__ACTIVE_TAB = "active_tab"
        private val CURRENT_ITEM = "current_item"
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var navAdapter: NavigationAdapter

    public override fun onCreate(inState: Bundle?) {
        super.onCreate(inState)
        setContentView(R.layout.activity_main)
        init(inState)
    }

    private fun init(inState: Bundle?) {
        initViewModel()
        initNavAdapter()
        initNavBar()
        trySelectTabFromIntent(inState)
        handleHasBackedUpPhrase()
        showFirstRunDialog()
        initObservers()
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)
    }

    private fun initNavAdapter() {
        navAdapter = NavigationAdapter(this, R.menu.navigation)
    }

    private val tabListener = AHBottomNavigation.OnTabSelectedListener { position, wasSelected ->
        val existingFragment = getExistingFragment(position)
        if (existingFragment == null) {
            transitionToSelectedFragment(position)
            if (!wasSelected) playTabSelectedSound()
        }
        true
    }

    private fun playTabSelectedSound() = SoundManager.getInstance().playSound(SoundManager.TAB_BUTTON)

    private fun getExistingFragment(position: Int): Fragment? {
        val selectedFragment = navAdapter.getItem(position) as TopLevelFragment
        return supportFragmentManager.findFragmentByTag(selectedFragment.getFragmentTag())
    }

    private fun transitionToSelectedFragment(position: Int) {
        val selectedFragment = navAdapter.getItem(position) as TopLevelFragment
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(
                fragmentContainer.id,
                selectedFragment as Fragment,
                selectedFragment.getFragmentTag()
        ).commit()
    }

    private fun initNavBar() {
        val menuInflater = AHBottomNavigationAdapter(this, R.menu.navigation)
        menuInflater.setupWithBottomNavigation(navBar)

        navBar.apply {
            titleState = AHBottomNavigation.TitleState.ALWAYS_SHOW
            accentColor = getColorById(R.color.colorPrimary)
            setInactiveIconColor(getColorById(R.color.inactiveIconColor))
            setInactiveTextColor(getColorById(R.color.inactiveTextColor))
            setOnTabSelectedListener(tabListener)
            isSoundEffectsEnabled = false
            isBehaviorTranslationEnabled = false
            setTitleTextSizeInSp(13.0f, 12.0f)
        }
    }

    private fun trySelectTabFromIntent(inState: Bundle?) {
        val activeTab = intent.getIntExtra(EXTRA__ACTIVE_TAB, 0)
        val currentItem = inState?.getInt(CURRENT_ITEM, 0)
        if (activeTab == 0 && currentItem != null && currentItem != 0) {
            navBar.currentItem = currentItem
        } else {
            navBar.currentItem = activeTab
        }
        intent.removeExtra(EXTRA__ACTIVE_TAB)
    }

    private fun initObservers() {
        viewModel.unreadMessages.observe(this, Observer {
            areUnreadMessages -> areUnreadMessages?.let { handleUnreadMessages(it) }
        })
    }

    private fun handleUnreadMessages(areUnreadMessages: Boolean) {
        if (areUnreadMessages) {
            showUnreadBadge()
        } else {
            hideUnreadBadge()
        }
    }

    private fun showUnreadBadge() = navBar.setNotification(" ", 1)

    private fun hideUnreadBadge() = navBar.setNotification("", 1)

    private fun handleHasBackedUpPhrase() {
        if (SharedPrefs.hasBackedUpPhrase()) {
            hideAlertBadge()
        } else {
            showAlertBadge()
        }
    }

    private fun hideAlertBadge() = navBar.setNotification("", 4)

    private fun showAlertBadge() = navBar.setNotification("!", 4)

    private fun showFirstRunDialog() {
        if (SharedPrefs.hasLoadedApp()) return

        val builder = AlertDialog.Builder(this, R.style.AlertDialogCustom)
        builder.setTitle(R.string.beta_warning_title)
                .setMessage(R.string.init_warning_message)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
        SharedPrefs.setHasLoadedApp()
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        outState?.putInt(CURRENT_ITEM, navBar.currentItem)
        super.onSaveInstanceState(outState)
    }

    override fun onBackPressed() {
        val dappFragment = supportFragmentManager.findFragmentByTag(DappFragment.TAG)
        if (dappFragment != null && dappFragment.isVisible && dappFragment is BackableTopLevelFragment) {
            val isHandled = dappFragment.onBackPressed()
            if (!isHandled) super.onBackPressed()
        } else super.onBackPressed()
    }
}