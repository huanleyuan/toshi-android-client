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

package com.toshi.view.activity

import android.app.Activity
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.toshi.BuildConfig
import com.toshi.R
import com.toshi.crypto.util.TypeConverter
import com.toshi.exception.CurrencyException
import com.toshi.extensions.isVisible
import com.toshi.extensions.setActivityResultAndFinish
import com.toshi.extensions.toast
import com.toshi.model.local.Networks
import com.toshi.util.BuildTypes
import com.toshi.util.CurrencyUtil
import com.toshi.util.EthUtil
import com.toshi.util.LocaleUtil
import com.toshi.util.OnSingleClickListener
import com.toshi.util.PaymentType
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.view.adapter.AmountInputAdapter
import com.toshi.viewModel.AmountViewModel
import kotlinx.android.synthetic.main.activity_amount.amountInputView
import kotlinx.android.synthetic.main.activity_amount.btnContinue
import kotlinx.android.synthetic.main.activity_amount.closeButton
import kotlinx.android.synthetic.main.activity_amount.ethValue
import kotlinx.android.synthetic.main.activity_amount.localCurrencyCode
import kotlinx.android.synthetic.main.activity_amount.localCurrencySymbol
import kotlinx.android.synthetic.main.activity_amount.localValueView
import kotlinx.android.synthetic.main.activity_amount.networkView
import kotlinx.android.synthetic.main.activity_amount.toolbarTitle
import java.math.BigDecimal

class AmountActivity : AppCompatActivity() {

    companion object {
        const val VIEW_TYPE = "type"
        const val INTENT_EXTRA__ETH_AMOUNT = "eth_amount"
    }

    private lateinit var viewModel: AmountViewModel

    private val separator by lazy { LocaleUtil.getDecimalFormatSymbols().monetaryDecimalSeparator }
    private val zero by lazy { LocaleUtil.getDecimalFormatSymbols().zeroDigit }
    private var encodedEthAmount: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_amount)
        init()
    }

    private fun init() {
        initViewModel()
        initToolbar()
        initClickListeners()
        initNetworkView()
        updateEthAmount()
        setCurrency()
        initObservers()
    }

    private fun initViewModel() {
        viewModel = ViewModelProviders.of(this).get(AmountViewModel::class.java)
    }

    private fun initToolbar() {
        val viewType = getViewTypeFromIntent()
        val title = if (viewType == PaymentType.TYPE_SEND) getString(R.string.send) else getString(R.string.request)
        toolbarTitle.text = title
    }

    private fun getViewTypeFromIntent() = intent.getIntExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_SEND)

    private fun initClickListeners() {
        closeButton.setOnClickListener { finish() }
        btnContinue.setOnClickListener(continueClickListener)
        amountInputView.setOnAmountClickedListener(amountClickedListener)
    }

    private val continueClickListener = object : OnSingleClickListener() {
        override fun onSingleClick(v: View?) { setActivityResultAndFinish() }
    }

    private fun setActivityResultAndFinish() {
        encodedEthAmount?.let {
            setActivityResultAndFinish(
                    Activity.RESULT_OK,
                    { putExtra(INTENT_EXTRA__ETH_AMOUNT, encodedEthAmount) }
            )
        }
    }

    private val amountClickedListener = object : AmountInputAdapter.OnKeyboardItemClicked {
        override fun onValueClicked(value: Char) { handleValueClicked(value) }
        override fun onBackSpaceClicked() { handleBackspaceClicked() }
    }

    private fun handleValueClicked(value: Char) {
        if (value == this.separator) handleSeparatorClicked() else updateValue(value)
    }

    private fun handleBackspaceClicked() {
        val currentLocalValue = localValueView.text.toString()
        val endIndex = Math.max(0, currentLocalValue.length - 1)
        val newLocalValue = currentLocalValue.substring(0, endIndex)
        val localValue = if (newLocalValue == zero.toString()) "" else newLocalValue
        localValueView.text = localValue
        updateEthAmount()
    }

    private fun initNetworkView() {
        val showCurrentNetwork = BuildConfig.BUILD_TYPE == BuildTypes.DEBUG
        networkView.isVisible(showCurrentNetwork)

        if (showCurrentNetwork) {
            val network = Networks.getInstance().currentNetwork
            networkView.text = network.name
        }
    }

    private fun getLocalValueAsBigDecimal(): BigDecimal {
        val currentLocalValue = localValueView.text.toString()
        if (currentLocalValue.isEmpty() || currentLocalValue == this.separator.toString()) {
            return BigDecimal.ZERO
        }

        val parts = currentLocalValue.split(separator.toString())
        val integerPart = if (parts.isEmpty()) currentLocalValue else parts[0]
        val fractionalPart = if (parts.size < 2) "0" else parts[1]
        val fullValue = integerPart + "." + fractionalPart
        val trimmedValue = if (fullValue.endsWith(".0")) fullValue.substring(0, fullValue.length - 2) else fullValue
        return BigDecimal(trimmedValue)
    }

    private fun handleSeparatorClicked() {
        val currentLocalValue = localValueView.text.toString()
        // Only allow a single decimal separator
        if (currentLocalValue.indexOf(separator) >= 0) return
        updateValue(separator)
    }

    private fun updateValue(value: Char) {
        appendValueInUi(value)
        updateEthAmount()
    }

    private fun appendValueInUi(value: Char) {
        val currentLocalValue = localValueView.text.toString()
        val isCurrentLocalValueEmpty = currentLocalValue.isEmpty() && value == zero
        if (currentLocalValue.length >= 10 || isCurrentLocalValueEmpty) return
        if (currentLocalValue.isEmpty() && value == separator) {
            val localValue = String.format("%s%s", zero.toString(), separator.toString())
            localValueView.text = localValue
            return
        }

        val newLocalValue = currentLocalValue + value
        localValueView.text = newLocalValue
    }

    private fun updateEthAmount() {
        val localValue = getLocalValueAsBigDecimal()
        viewModel.getEthAmount(localValue)
    }

    private fun setCurrency() {
        try {
            val currency = SharedPrefs.getCurrency()
            val currencyCode = CurrencyUtil.getCode(currency)
            val currencySymbol = CurrencyUtil.getSymbol(currency)
            localCurrencySymbol.text = currencySymbol
            localCurrencyCode.text = currencyCode
        } catch (e: CurrencyException) {
            toast(R.string.unsupported_currency_message)
        }
    }

    private fun initObservers() {
        viewModel.ethAmount.observe(this, Observer {
            ethAmount -> ethAmount
                ?.let { handleEth(it) }
                ?: toast(R.string.local_currency_to_eth_error)
        })
    }

    private fun handleEth(ethAmount: BigDecimal) {
        ethValue.text = EthUtil.ethAmountToUserVisibleString(ethAmount)
        btnContinue.isEnabled = EthUtil.isLargeEnoughForSending(ethAmount)
        val weiAmount = EthUtil.ethToWei(ethAmount)
        encodedEthAmount = TypeConverter.toJsonHex(weiAmount)
    }
}