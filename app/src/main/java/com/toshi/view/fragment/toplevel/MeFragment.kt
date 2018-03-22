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

package com.toshi.view.fragment.toplevel

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Build
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.toshi.R
import com.toshi.extensions.addHorizontalLineDivider
import com.toshi.extensions.getColorById
import com.toshi.extensions.getPxSize
import com.toshi.extensions.startActivity
import com.toshi.extensions.startActivityAndFinish
import com.toshi.extensions.toast
import com.toshi.model.local.User
import com.toshi.model.network.Balance
import com.toshi.util.ImageUtil
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.view.activity.AdvancedSettingsActivity
import com.toshi.view.activity.BackupPhraseInfoActivity
import com.toshi.view.activity.BalanceActivity
import com.toshi.view.activity.CurrencyActivity
import com.toshi.view.activity.QrCodeActivity
import com.toshi.view.activity.SignOutActivity
import com.toshi.view.activity.TrustedFriendsActivity
import com.toshi.view.activity.ViewProfileActivity
import com.toshi.view.adapter.MeAdapter
import com.toshi.view.adapter.listeners.OnItemClickListener
import com.toshi.viewModel.MeViewModel
import kotlinx.android.synthetic.main.fragment_me.avatar
import kotlinx.android.synthetic.main.fragment_me.backupPhrase
import kotlinx.android.synthetic.main.fragment_me.balanceContainer
import kotlinx.android.synthetic.main.fragment_me.checkboxBackupPhrase
import kotlinx.android.synthetic.main.fragment_me.ethBalance
import kotlinx.android.synthetic.main.fragment_me.localCurrencyBalance
import kotlinx.android.synthetic.main.fragment_me.myProfileCard
import kotlinx.android.synthetic.main.fragment_me.myQrCode
import kotlinx.android.synthetic.main.fragment_me.name
import kotlinx.android.synthetic.main.fragment_me.securityStatus
import kotlinx.android.synthetic.main.fragment_me.settings
import kotlinx.android.synthetic.main.fragment_me.trustedFriends
import kotlinx.android.synthetic.main.fragment_me.username
import java.math.BigInteger

class MeFragment : TopLevelFragment() {

    companion object {
        private const val TAG = "MeFragment"
    }

    override fun getFragmentTag() = TAG

    private lateinit var meAdapter: MeAdapter
    private lateinit var viewModel: MeViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, inState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = init()

    private fun init() {
        val activity = activity ?: return
        setStatusBarColor(activity)
        initViewModel(activity)
        setSecurityState()
        initClickListeners()
        initRecyclerView()
        initObservers()
    }

    private fun setStatusBarColor(activity: FragmentActivity) {
        if (Build.VERSION.SDK_INT < 21) return
        activity.window.statusBarColor = getColorById(R.color.colorPrimaryDark) ?: 0
    }

    private fun initViewModel(activity: FragmentActivity) {
        viewModel = ViewModelProviders.of(activity).get(MeViewModel::class.java)
    }

    private fun setSecurityState() {
        if (SharedPrefs.hasBackedUpPhrase()) {
            checkboxBackupPhrase.isChecked = true
            securityStatus.visibility = View.GONE
        }
    }

    private fun initClickListeners() {
        myProfileCard.setOnClickListener { startActivity<ViewProfileActivity>() }
        trustedFriends.setOnClickListener { startActivity<TrustedFriendsActivity>() }
        backupPhrase.setOnClickListener { startActivity<BackupPhraseInfoActivity>() }
        myQrCode.setOnClickListener { startActivity<QrCodeActivity>() }
        balanceContainer.setOnClickListener { startActivity<BalanceActivity>() }
    }

    private fun initRecyclerView() {
        meAdapter = MeAdapter()
                .apply { onItemClickListener = OnItemClickListener { handleItemClickListener(it) } }

        settings.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = meAdapter
            addHorizontalLineDivider(leftPadding = getPxSize(R.dimen.activity_horizontal_margin))
        }
    }

    private fun handleItemClickListener(option: Int) {
        when (option) {
            MeAdapter.LOCAL_CURRENCY -> startActivity<CurrencyActivity>()
            MeAdapter.ADVANCED -> startActivity<AdvancedSettingsActivity>()
            MeAdapter.SIGN_OUT -> viewModel.getBalance()
            else -> toast(R.string.option_not_supported)
        }
    }

    private fun initObservers() {
        viewModel.user.observe(this, Observer {
            user -> user?.let { updateUi(it) } ?: handleNoUser()
        })
        viewModel.balance.observe(this, Observer {
            balance -> balance?.let { ethBalance.text = balance.formattedUnconfirmedBalance }
        })
        viewModel.formattedBalance.observe(this, Observer {
            formattedBalance -> formattedBalance?.let { localCurrencyBalance.text = it }
        })
        viewModel.singelBalance.observe(this, Observer {
            balance -> balance?.let { showDialog(it) }
        })
    }

    private fun updateUi(user: User) {
        name.text = user.displayName
        username.text = user.username
        ImageUtil.load(user.avatar, avatar)
    }

    private fun handleNoUser() {
        name.text = getString(R.string.profile__unknown_name)
        username.text = ""
        avatar.setImageResource(R.drawable.ic_unknown_user_24dp)
    }

    private fun showDialog(balance: Balance) {
        val isWalletEmpty = balance.unconfirmedBalance.compareTo(BigInteger.ZERO) == 0
        val shouldCancelSignOut = !SharedPrefs.hasBackedUpPhrase() && !isWalletEmpty
        if (shouldCancelSignOut) {
            showSignOutCancelledDialog()
        } else {
            showSignOutWarning()
        }
    }

    private fun showSignOutCancelledDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setTitle(R.string.sign_out_cancelled_title)
                .setMessage(R.string.sign_out_cancelled_message)
                .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun showSignOutWarning() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setTitle(R.string.sign_out_warning_title)
                .setMessage(R.string.sign_out_warning_message)
                .setPositiveButton(R.string.yes) { dialog, _ ->
                    dialog.dismiss()
                    showSignOutConfirmationDialog()
                }
                .setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun showSignOutConfirmationDialog() {
        val context = context ?: return
        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
                .setTitle(R.string.sign_out_confirmation_title)
                .setPositiveButton(R.string.sign_out) { dialog, _ ->
                    dialog.dismiss()
                    startActivityAndFinish<SignOutActivity>()
                }
                .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    override fun onStart() {
        super.onStart()
        updateBalance()
    }

    private fun updateBalance() {
        viewModel.balance.value?.let { viewModel.getFormattedBalance(it) }
        meAdapter.notifyDataSetChanged()
    }
}