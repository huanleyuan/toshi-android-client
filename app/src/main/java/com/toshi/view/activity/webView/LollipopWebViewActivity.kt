/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.activity.webView

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import com.toshi.BuildConfig
import com.toshi.R
import com.toshi.extensions.isVisible
import com.toshi.extensions.isWebUrl
import com.toshi.extensions.setActivityResultAndFinish
import com.toshi.model.local.dapp.DappCategory
import com.toshi.model.network.dapp.Dapp
import com.toshi.presenter.webview.SofaHostWrapper
import com.toshi.presenter.webview.ToshiChromeWebViewClient
import com.toshi.util.FileUtil
import com.toshi.util.KeyboardUtil
import com.toshi.util.PermissionUtil
import com.toshi.view.adapter.SearchDappAdapter
import com.toshi.view.fragment.DialogFragment.ChooserDialog
import com.toshi.viewModel.DappViewModel
import com.toshi.viewModel.ViewModelFactory.WebViewViewModelFactory
import com.toshi.viewModel.WebViewViewModel
import kotlinx.android.synthetic.main.activity_lollipop_view_view.input
import kotlinx.android.synthetic.main.activity_lollipop_view_view.progressBar
import kotlinx.android.synthetic.main.activity_lollipop_view_view.webview
import kotlinx.android.synthetic.main.activity_lollipop_view_view.searchDapps
import kotlinx.android.synthetic.main.activity_lollipop_view_view.swipeToRefresh
import kotlinx.android.synthetic.main.view_address_bar_input.backButton
import kotlinx.android.synthetic.main.view_address_bar_input.forwardButton
import kotlinx.android.synthetic.main.view_address_bar_input.view.userInput
import java.io.File

class LollipopWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA__ADDRESS = "address"
        const val EXIT_ACTION = "exitAction"
        const val RESULT_CODE = 101

        private const val PICK_IMAGE = 1
        private const val CAPTURE_IMAGE = 2
        private const val IMAGE_TYPE = "image/*"
        private const val ALPHA_DISABLED = 0.4f
        private const val ALPHA_ENABLED = 1f
    }

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var capturedImagePath: String? = null
    private lateinit var webViewModel: WebViewViewModel
    private lateinit var dappSearchViewModel: DappViewModel
    private lateinit var sofaHostWrapper: SofaHostWrapper
    private lateinit var searchDappAdapter: SearchDappAdapter
    private var addressBarHasFocus = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lollipop_view_view)
        initWebClient()
        load()
        initSearchAdapter()
    }

    private fun initWebClient() {
        initViewModel()
        initListeners()
        initWebSettings()
        injectEverything()
        initObservers()
    }

    private fun initViewModel() {
        val url = intent.getStringExtra(EXTRA__ADDRESS).orEmpty()
        input.text = url
        webViewModel = ViewModelProviders.of(
                this,
                WebViewViewModelFactory(url)
        ).get(WebViewViewModel::class.java)
        dappSearchViewModel = ViewModelProviders.of(this).get(DappViewModel::class.java)
    }

    private fun initListeners() {
        input.onBackClickedListener = { handleBackButtonClicked() }
        input.onForwardClickedListener = { handleForwardButtonClicked() }
        input.onGoClickedListener = { onGoClicked(it) }
        input.onExitClickedListener = { handleExitClicked() }
        input.onFocusChangedListener = { onAddressBarFocusChanged(it) }
        input.onTextChangedListener = { showSearchUI(it) }
        swipeToRefresh.setOnRefreshListener { webview.reload() }
    }

    private fun handleBackButtonClicked() {
        if (webview.canGoBack()) webview.goBack()
        clearAddressBarFocus()
    }

    private fun handleForwardButtonClicked() {
        if (webview.canGoForward()) webview.goForward()
        clearAddressBarFocus()
    }

    private fun onGoClicked(it: String) {
        webViewModel.url.postValue(it)
        clearAddressBarFocus()
    }

    private fun clearAddressBarFocus() = input.clearFocus()

    private fun handleExitClicked() {
        KeyboardUtil.hideKeyboard(input.userInput)
        val isListeningForExitAction = intent.getBooleanExtra(EXIT_ACTION, false)
        if (isListeningForExitAction) setActivityResultAndFinish(RESULT_CODE)
        else finish()
    }

    private fun onAddressBarFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) searchDapps.isVisible(false)
        addressBarHasFocus = hasFocus
    }

    private fun showSearchUI(input: String) {
        if (!addressBarHasFocus) return
        searchDapps.isVisible(true)
        if (input.isEmpty()) setSearchEmptyState()
        else search(input)
    }

    private fun setSearchEmptyState() {
        dappSearchViewModel.getAllDapps()
        val dapps = dappSearchViewModel.allDapps.value ?: emptyList()
        val category = DappCategory(getString(R.string.dapps), -1)
        searchDappAdapter.setEmptyState(dapps, category)
    }

    private fun search(input: String) {
        searchDappAdapter.addGoogleSearchItems(input)
        dappSearchViewModel.search(input)
        if (input.isWebUrl()) searchDappAdapter.addWebUrlItems(input)
        else searchDappAdapter.removeWebUrl()
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) webview.goBack()
        else super.onBackPressed()
    }

    private fun initWebSettings() {
        val webSettings = webview.settings
        webSettings.javaScriptEnabled = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.domStorageEnabled = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.WEB_DEBUG_ENABLED)
        }
    }

    private fun injectEverything() {
        val address = webViewModel.tryGetAddress()
        sofaHostWrapper = SofaHostWrapper(this, webview, address)
        webview.addJavascriptInterface(sofaHostWrapper.sofaHost, "SOFAHost")
        webview.webViewClient = ToshiWebClient(this)
                .apply {
                    onHistoryUpdatedListener = webViewModel::updateToolbar
                    onUrlUpdatedListener = { webViewModel.url.postValue(it) }
                    onPageLoadingStartedListener = { webview.visibility = View.INVISIBLE }
                    onPageLoadedListener = { onPageLoaded(it) }
                }
        val chromeWebClient = ToshiChromeWebViewClient(this::handleFileChooserCallback)
        chromeWebClient.progressListener = { progressBar.setProgress(it) }
        webview.webChromeClient = chromeWebClient
    }

    private fun onPageLoaded(url: String?) {
        if (url != null) input.text = url
        updateToolbarNavigation()
        swipeToRefresh.isRefreshing = false
        webview.visibility = View.VISIBLE
    }

    private fun initObservers() {
        webViewModel.toolbarUpdate.observe(this, Observer { updateToolbar() })
        webViewModel.url.observe(this, Observer { load() })
        dappSearchViewModel.searchResult.observe(this, Observer {
            if (it != null && input.text.isNotEmpty()) setSearchResult(it.results.dapps)
        })
        dappSearchViewModel.allDapps.observe(this, Observer {
            if (it != null) setSearchResult(it)
        })
    }

    private fun setSearchResult(dapps: List<Dapp>) {
        val dappsCategory = DappCategory(getString(R.string.dapps), -1)
        searchDappAdapter.setDapps(dapps, dappsCategory)
    }

    private fun updateToolbar() {
        input.text = webview.url
        title = webview.title
        updateToolbarNavigation()
    }

    private fun updateToolbarNavigation() {
        backButton.alpha = if (webview.canGoBack()) ALPHA_ENABLED else ALPHA_DISABLED
        forwardButton.alpha = if (webview.canGoForward()) ALPHA_ENABLED else ALPHA_DISABLED
    }

    private fun load() = webview.loadUrl(webViewModel.tryGetAddress())

    private fun initSearchAdapter() {
        searchDappAdapter = SearchDappAdapter().apply {
            onSearchClickListener = { openBrowserAndSearchGoogle(it) }
            onGoToClickListener = { goToUrl(it) }
            onItemClickedListener = { goToDappUrl(it) }
        }
        searchDapps.apply {
            adapter = searchDappAdapter
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            setOnClickListener { clearAddressBarFocus() }
        }
    }

    private fun goToDappUrl(it: Dapp) {
        webViewModel.url.value = it.url
        clearAddressBarFocus()
    }

    private fun goToUrl(it: String) {
        webViewModel.url.value = it
        clearAddressBarFocus()
    }

    private fun openBrowserAndSearchGoogle(searchValue: String) {
        val address = getString(R.string.google_search_url).format(searchValue)
        webViewModel.url.value = address
        clearAddressBarFocus()
    }

    private fun handleFileChooserCallback(valueCallback: ValueCallback<Array<Uri>>?): Boolean {
        this.filePathCallback = valueCallback
        showImageChooserDialog()
        return true
    }

    private fun showImageChooserDialog() {
        val chooserDialog = ChooserDialog.newInstance()
        chooserDialog.setOnChooserClickListener(object : ChooserDialog.OnChooserClickListener {
            override fun captureImageClicked() {
                checkCameraPermission()
            }

            override fun importImageFromGalleryClicked() {
                checkExternalStoragePermission()
            }
        })
        chooserDialog.show(supportFragmentManager, ChooserDialog.TAG)
    }

    private fun checkExternalStoragePermission() {
        PermissionUtil.hasPermission(
                this,
                READ_EXTERNAL_STORAGE,
                PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSION,
                { startGalleryActivity() }
        )
    }

    private fun checkCameraPermission() {
        PermissionUtil.hasPermission(
                this,
                CAMERA,
                PermissionUtil.CAMERA_PERMISSION,
                { startCameraActivity() }
        )
    }

    private fun startCameraActivity() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) == null) return
        val photoFile = FileUtil.createImageFileWithRandomName()
        capturedImagePath = photoFile.absolutePath
        val photoURI = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID + FileUtil.FILE_PROVIDER_NAME,
                photoFile
        )
        PermissionUtil.grantUriPermission(this, cameraIntent, photoURI)
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        startActivityForResult(cameraIntent, CAPTURE_IMAGE)
    }

    private fun startGalleryActivity() {
        val pickPictureIntent = Intent()
                .setType(IMAGE_TYPE)
                .setAction(Intent.ACTION_GET_CONTENT)
        if (pickPictureIntent.resolveActivity(packageManager) == null) return
        val chooserIntent = Intent.createChooser(
                pickPictureIntent,
                getString(R.string.select_picture)
        )
        startActivityForResult(chooserIntent, PICK_IMAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!PermissionUtil.isPermissionGranted(grantResults)) return
        when (requestCode) {
            PermissionUtil.CAMERA_PERMISSION -> startCameraActivity()
            PermissionUtil.READ_EXTERNAL_STORAGE_PERMISSION -> startGalleryActivity()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultIntent: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultIntent)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            PICK_IMAGE -> postFileCallback(resultIntent?.data)
            CAPTURE_IMAGE -> handleCapturesImage(capturedImagePath)
        }
    }

    private fun handleCapturesImage(path: String?) {
        if (capturedImagePath == null) return
        val uri = Uri.fromFile(File(path))
        postFileCallback(uri)
        capturedImagePath = null
    }

    private fun postFileCallback(uri: Uri?) {
        if (uri == null) return
        val uriArray = arrayOf(uri)
        filePathCallback?.onReceiveValue(uriArray)
        filePathCallback = null
    }

    override fun onResume() {
        super.onResume()
        webview.onResume()
        webview.resumeTimers()
    }

    override fun onPause() {
        webview.onPause()
        webview.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        sofaHostWrapper.clear()
        webview.destroy()
        super.onDestroy()
    }
}