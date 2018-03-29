package com.toshi.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup

abstract class BaseCompoundableAdapter<VH: RecyclerView.ViewHolder, T>: RecyclerView.Adapter<VH>(), CompoundableAdapter {
    var parent: CompoundAdapter? = null

    private var items: List<T> = listOf()

    override fun setCompoundParent(parent: CompoundAdapter?) {
        this.parent = parent
    }

    fun itemAt(index: Int): T {
        return items[index]
    }

    fun safelyAt(index: Int): T? {
        if (!items.indices.contains(index)) {
            return null
        }

        return itemAt(index)
    }

    open fun setItemList(items: List<T>) {
        this.items = items
        parent?.notifyDataSetChanged(this)
        notifyDataSetChanged()
    }

    private fun mutateItems(action: (MutableList<T>) -> Unit) {
        val mutableCopy = items.toMutableList()
        action(mutableCopy)
        items = mutableCopy.toList()
    }

    fun addItem(item: T) {
        mutateItems { it.add(item) }
        parent?.notifyDataSetChanged(this)
        notifyDataSetChanged()
    }

    fun insertItem(item: T, index: Int) {
        mutateItems { it.add(index, item) }
        parent?.notifyItemInserted(this, index)
        notifyItemInserted(index)
    }

    fun removeItem(item: T) {
        if (!items.contains(item)) {
            return
        }

        val removalIndex = items.indexOf(item)
        mutateItems { it.remove(item) }
        parent?.notifyItemRemoved(this, removalIndex)
        notifyItemRemoved(removalIndex)
    }

    // ADAPTER OVERRIDES

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getCompoundableItemCount(): Int {
        return itemCount
    }

    override fun compoundableCreateViewHolder(parent: ViewGroup): RecyclerView.ViewHolder {
        return onCreateViewHolder(parent, 0)
    }

}
