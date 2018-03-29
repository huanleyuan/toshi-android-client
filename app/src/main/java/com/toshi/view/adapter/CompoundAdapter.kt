package com.toshi.view.adapter

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup


interface CompoundableAdapter {

    fun genericBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int)

    fun genericCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder

    fun genericItemCount(): Int
}


/*
An adapter which takes an array of adapters, so logic for things with different types can be split into
individual adapters.
*/
class CompoundAdapter(
        var adapters: List<CompoundableAdapter>
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    fun indexOf(adapter: CompoundableAdapter): Int? {
        if (adapters.contains(adapter)) {
            return adapters.indexOf(adapter)
        } else {
            return null
        }
    }

    private fun totalItemsBeforeSection(sectionIndex: Int): Int {
        when (sectionIndex) {
            in Int.MIN_VALUE..-1 -> throw AssertionError("No sections at negative indexes!")
            0 ->  /* There wouldn't be any items before section 0 */ return 0
            in 1..sectionIndex -> {
                val previousAdapters = adapters.subList(0, sectionIndex)
                return previousAdapters.fold(0, { acc, adapter -> return acc + adapter.genericItemCount() })
            }
            in (sectionIndex + 1)..Int.MAX_VALUE -> throw AssertionError("Looking for section at $sectionIndex but there are only ${adapters.size} sections")
        }

        // Even though the `when` should theoretically cover all possible values of Int, we apparently still need to do this:
        return -1
    }

    private fun compoundIndexOfItem(adapter: CompoundableAdapter, positionInAdapter: Int): Int {
        val sectionIndex = adapters.indexOf(adapter)
        val previousItems = totalItemsBeforeSection(sectionIndex)
        return previousItems + positionInAdapter
    }

    private fun sectionIndexOfItem(adapter: CompoundableAdapter, compoundIndex: Int): Int {
        val sectionIndex = adapters.indexOf(adapter)
        val previousItems = totalItemsBeforeSection(sectionIndex)
        return compoundIndex - previousItems
    }

    // Add observers when data set changes
    private fun currentSectionAdapter(position: Int): CompoundableAdapter {
        var previousCount = 0
        adapters.forEach { adapter ->
            if (position >= (previousCount + adapter.genericItemCount())) {
                previousCount += adapter.genericItemCount()
            } else {
                return adapter
            }
        }

        // If we've gotten here, we've gone through all the adapters and haven't found anything
        throw AssertionError("No adapter for position $position")
    }

    override fun getItemCount(): Int {
        return adapters.fold(0, { acc, adapter -> acc + adapter.genericItemCount() })
    }

    override fun getItemViewType(position: Int): Int {
        // Use the index of the section adapter to route the view type to the proper place
        return indexOf(currentSectionAdapter(position)) ?: -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val sectionAdapter = adapters[viewType]

        // NOTE: Each section should only handle one particular type of item, or this is gonna cause some screwiness.
        return sectionAdapter.genericCreateViewHolder(parent, 0)
    }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val sectionAdapter = currentSectionAdapter(position)
        val sectionIndex = indexOf(sectionAdapter) ?: return

        when (sectionIndex) {
            0 -> sectionAdapter.genericBindViewHolder(holder, position)
            in (1..(itemCount - 1)) -> sectionAdapter.genericBindViewHolder(holder, sectionIndexOfItem(sectionAdapter, position))
            else -> throw AssertionError("Section index $sectionIndex out of bounds for adapter with $itemCount sections")
        }
    }


    fun notifyItemRemoved(adapter: CompoundableAdapter, removedIndex: Int) {
        notifyItemRemoved(compoundIndexOfItem(adapter, removedIndex))
    }

    fun notifyItemChanged(adapter: CompoundableAdapter, changeIndex: Int) {
        notifyItemChanged(compoundIndexOfItem(adapter, changeIndex))
    }

//    public fun notify
}
