package com.toshi.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.toshi.R
import com.toshi.view.adapter.viewholder.ThreadViewHolder
import com.toshi.model.local.Conversation
import com.toshi.model.local.User
import com.toshi.model.sofa.SofaAdapters
import com.toshi.model.sofa.SofaMessage
import com.toshi.model.sofa.SofaType
import com.toshi.util.logging.LogUtil
import com.toshi.view.BaseApplication
import com.toshi.view.adapter.listeners.OnItemClickListener
import java.io.IOException

class ConversationAdapter(
        private val onItemClickListener: (Conversation) -> Unit,
        private val onItemLongClickListener: (Conversation) -> Unit
    ): RecyclerView.Adapter<ThreadViewHolder>() {

    private lateinit var conversations: List<Conversation>

    fun setConversations(conversations: List<Conversation>) {
        this.conversations = conversations
    }


    override fun getItemCount(): Int {
        return conversations.size
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.list_item__recent, parent, false)
        return ThreadViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        val conversation = conversations[position]
        holder.setThread(conversation)

        val formattedLatestMessage = formatLastMessage(conversation.latestMessage)

        holder.setLatestMessage(formattedLatestMessage)
        holder.setOnItemClickListener(conversation, onItemClickListener)
        holder.setOnItemLongClickListener(conversation, onItemLongClickListener)
    }

    private fun getCurrentLocalUser(): User {
        // Yes, this blocks. But realistically, a value should be always ready for returning.
        return BaseApplication
                .get()
                .userManager
                .getCurrentUser()
                .toBlocking()
                .value()
    }

    private fun formatLastMessage(sofaMessage: SofaMessage?): String {
        if (sofaMessage == null) return ""
        val localUser = getCurrentLocalUser()
        val sentByLocal = sofaMessage.isSentBy(localUser)

        try {
            when (sofaMessage.type) {
                SofaType.PLAIN_TEXT -> {
                    val message = SofaAdapters.get().messageFrom(sofaMessage.payload)
                    return message.toUserVisibleString(sentByLocal, sofaMessage.hasAttachment())
                }
                SofaType.PAYMENT -> {
                    val payment = SofaAdapters.get().paymentFrom(sofaMessage.payload)
                    return payment.toUserVisibleString(sentByLocal, sofaMessage.sendState)
                }
                SofaType.PAYMENT_REQUEST -> {
                    val request = SofaAdapters.get().txRequestFrom(sofaMessage.payload)
                    return request.toUserVisibleString(sentByLocal, sofaMessage.sendState)
                }
                SofaType.LOCAL_STATUS_MESSAGE -> {
                    val localStatusMessage = SofaAdapters.get().localStatusMessageRequestFrom(sofaMessage.payload)
                    val sender = localStatusMessage.sender
                    val isSenderLocalUser = localUser != null && sender != null && localUser.toshiId == sender.toshiId
                    return localStatusMessage.loadString(isSenderLocalUser)
                }
                SofaType.COMMAND_REQUEST,
                SofaType.INIT_REQUEST,
                SofaType.INIT,
                SofaType.TIMESTAMP,
                SofaType.UNKNOWN -> return ""
                else -> return ""
            }
        } catch (ex: IOException) {
            LogUtil.w("Error parsing SofaMessage. $ex")

        }

        return ""
    }

    fun updateConversations(updatedConversations: List<Conversation>) {
        if (updatedConversations.isEmpty()) return

        // Only include conversations with valid recipients
        conversations = updatedConversations.filter { !it.isRecipientInvalid }

        notifyDataSetChanged()
    }
}