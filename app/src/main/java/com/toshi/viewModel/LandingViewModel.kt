package com.toshi.viewModel

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import com.toshi.R
import com.toshi.manager.OnboardingManager
import com.toshi.model.local.Conversation
import com.toshi.util.sharedPrefs.SharedPrefs
import com.toshi.util.SingleLiveEvent
import com.toshi.util.logging.LogUtil
import com.toshi.view.BaseApplication
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import rx.subscriptions.CompositeSubscription
import java.util.concurrent.TimeUnit

class LandingViewModel : ViewModel() {

    private val subscriptions by lazy { CompositeSubscription() }
    private val sofaMessageManager by lazy { BaseApplication.get().sofaMessageManager }
    private val toshiManager by lazy { BaseApplication.get().toshiManager }

    val isLoading by lazy { MutableLiveData<Boolean>() }
    val onboardingBotId by lazy { SingleLiveEvent<String>() }
    val walletError by lazy { SingleLiveEvent<Int>() }
    val onboardingError by lazy { SingleLiveEvent<Unit>() }

    fun handleCreateNewAccountClicked() {
        if (isLoading.value == true) return
        isLoading.value = true

        val sub = toshiManager
                .initNewWallet()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { startListeningToBotConversation() },
                        { handleWalletError(it) }
                )

        this.subscriptions.add(sub)
    }

    private fun handleWalletError(throwable: Throwable) {
        isLoading.value = false
        walletError.value = R.string.unable_to_create_wallet
        LogUtil.exception("Error while creating new wallet $throwable")
    }

    private fun startListeningToBotConversation() {
        val sub = sofaMessageManager
                .registerForAllConversationChanges()
                .filter { isOnboardingBot(it) }
                .timeout(10, TimeUnit.SECONDS)
                .first()
                .toSingle()
                .flatMap { sofaMessageManager.acceptConversation(it) }
                .map { conversation -> conversation.recipient.user.toshiId }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { handleOnboardingSuccess(it) },
                        { handleOnboardingFailure(it) }
                )

        subscriptions.add(sub)
    }

    private fun handleOnboardingSuccess(botId: String) {
        onboardingBotId.value = botId
        isLoading.value = false
        SharedPrefs.setSignedIn()
    }

    private fun handleOnboardingFailure(throwable: Throwable) {
        onboardingError.value = Unit
        isLoading.value = false
        SharedPrefs.setSignedIn()
        LogUtil.exception("Error while waiting for onboarding bot response $throwable")
    }

    private fun isOnboardingBot(conversation: Conversation): Boolean {
        return conversation.recipient.user.usernameForEditing == OnboardingManager.getOnboardingBotName()
    }

    override fun onCleared() {
        super.onCleared()
        subscriptions.clear()
    }
}