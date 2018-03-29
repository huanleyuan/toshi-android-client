package com.toshi.adapter

import android.app.Activity
import android.content.Context
import android.support.test.runner.AndroidJUnit4
import android.support.test.rule.ActivityTestRule
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import com.toshi.view.activity.SplashActivity
import com.toshi.view.adapter.CompoundAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompoundAdapterTests {

    @Rule @JvmField
    val activityRule = ActivityTestRule(SplashActivity::class.java)

    private val stringList = listOf(
            "working",
            "around",
            "kotlin's",
            "type",
            "system"
    )

    private val intList = listOf(
            0,
            1,
            2
    )

    private val intAdapter = IntCompoundableAdapter(intList)
    private val stringAdapter = StringCompoundableAdapter(stringList)
    private val testCompoundAdapter = CompoundAdapter(listOf(
            intAdapter,
            stringAdapter
    ))


    private fun context(): Context {
        return activityRule.activity
    }

    @Test
    fun compoundAdapterHasCorrectTotalNumberOfItems() {
        val totalItems = intList.size + stringList.size
        assertEquals(testCompoundAdapter.itemCount, totalItems)
    }
    @Test
    fun compoundAdapterReturnsCorrectViewHolderForSectionIndex() {

        val recyclerView = RecyclerView(context())
        recyclerView.adapter = testCompoundAdapter

        val indexOfIntAdapter = testCompoundAdapter.indexOf(intAdapter)
        if (indexOfIntAdapter == null) {
            fail("Int section index was null")
            return
        }
        assertEquals(indexOfIntAdapter, 0)

        val shouldBeAnIntViewHolder =  testCompoundAdapter.onCreateViewHolder(recyclerView, indexOfIntAdapter)
        assertTrue(shouldBeAnIntViewHolder is IntViewHolder)

        val indexOfStringAdapter = testCompoundAdapter.indexOf(stringAdapter)
        if (indexOfStringAdapter == null) {
            fail("String section index was null")
            return
        }
        assertEquals(indexOfStringAdapter, 1)

        val shouldBeAStringViewHolder = testCompoundAdapter.onCreateViewHolder(recyclerView, indexOfStringAdapter)
        assertTrue(shouldBeAStringViewHolder is StringViewHolder)
    }

    @Test
    fun compoundAdapterReturnsCorrectConfiguredViewForPosition() {
        val compoundAdapter = testCompoundAdapter

        val recyclerView = RecyclerView(context())
        recyclerView.adapter = compoundAdapter
        recyclerView.layoutManager = LinearLayoutManager(context())

        val intSectionIndex = compoundAdapter.indexOf(intAdapter)
        if (intSectionIndex == null) {
            fail("Section index was null")
            return
        }
        val intViewHolder =  compoundAdapter.onCreateViewHolder(recyclerView, intSectionIndex)
        compoundAdapter.onBindViewHolder(intViewHolder, intList.lastIndex)

        val intView = intViewHolder as? IntViewHolder
        if (intView == null) {
            fail("Int view not correct type!")
            return
        }
        assertEquals(intView.textView.text, "${intList.last()}")

        val stringSectionIndex = compoundAdapter.indexOf(stringAdapter)
        if (stringSectionIndex == null) {
            fail("String section index was null")
            return
        }

        val stringViewHolder = compoundAdapter.onCreateViewHolder(recyclerView, stringSectionIndex)
        val firstStringIndex = intList.size
        compoundAdapter.onBindViewHolder(stringViewHolder, firstStringIndex)

        val stringView = stringViewHolder as? StringViewHolder
        if (stringView == null) {
            fail("Couldn't get correct type to cehck for string view holder")
            return
        }
        assertEquals(stringView.textView.text, stringList.first())
    }
}