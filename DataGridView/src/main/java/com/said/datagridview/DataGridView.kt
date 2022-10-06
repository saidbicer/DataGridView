package com.said.datagridview

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView

class DataGridView constructor(context: Context?, attrs: AttributeSet? = null) :
    TableLayout(context, attrs) {

    private lateinit var tableRowHeader: LinearLayout
    private var itemList: MutableList<Map<String, String>> = ArrayList()
    private var headerKeys = arrayListOf<String>()
    private var shortStore: MutableList<String> = ArrayList()

    init {
        this.isStretchAllColumns = true
    }


    fun createDataGrid(
        headerItem: Map<String, String>,
        defaultHeaderOrder: java.util.ArrayList<String>,
        itemList: java.util.ArrayList<Map<String, String>>
    ) {
        this.headerKeys = defaultHeaderOrder
        this.itemList = itemList

        createTableHeader(headerItem)
        refreshTable()
        sortData()
    }

    /*
     * Adding click feature to header views for column-based sorting
     */
    private fun sortData() {
        for (i in 0 until tableRowHeader.childCount) {
            val view = tableRowHeader.getChildAt(i)
            if (view.tag != null) {
                view.setOnClickListener(View.OnClickListener { v ->
                    val key = v.tag.toString()
                    val textView = v as TextView
                    if (shortStore.contains(key)) {
                        if (shortStore[0] == key) {
                            for (j in 0 until tableRowHeader.childCount) {
                                val view = tableRowHeader.getChildAt(j) as TextView
                                view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                            }
                            shortStore = ArrayList()
                        }
                        return@OnClickListener
                    } else if (shortStore.size > 1) {
                        return@OnClickListener
                    }
                    textView.setCompoundDrawablesWithIntrinsicBounds(
                        0,
                        0,
                        if (shortStore.size == 0) R.drawable.ic_numerical_sorting_1 else R.drawable.ic_numerical_sorting_2,
                        0
                    )
                    shortStore.add(key)

                    SortUtil.sortByTag(itemList, shortStore)
                    refreshTable()
                })
            }
        }
    }

    /*
     * Creating the header
     */
    private fun createTableHeader(header: Map<String, String>) {
        this.removeAllViews()
        createTableRow(
            header,
            true
        )
    }

    /*
     * Items are placed on the table one by one
     */
    private fun refreshTable() {
        this.removeAllViews()
        this.addView(tableRowHeader)

        // data rows
        itemList.forEach { item ->
            createTableRow(
                item,
                false
            )
        }
    }

    /*
     * The item is added to the table
     */
    private fun createTableRow(entry: Map<String, String>, isHeader: Boolean) {
        val tableRow = if (isHeader) DragLinearLayout(context) else TableRow(context)
        val lp = TableRow.LayoutParams(
            TableRow.LayoutParams.MATCH_PARENT,
            TableRow.LayoutParams.WRAP_CONTENT
        )
        tableRow.layoutParams = lp
        tableRow.orientation = LinearLayout.HORIZONTAL
        for (key in headerKeys) {
            val textView = TextView(context)
            textView.text = entry[key]
            textView.tag = key
            textView.layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.MATCH_PARENT,
                0f
            )
            textView.gravity = Gravity.CENTER
            textView.maxLines = 1
            textView.setPadding(5, 15, 5, 15)
            textView.setBackgroundResource(R.drawable.cell_shape_white)
            textView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                resources.getDimension(R.dimen.font_size_small).toInt().toFloat()
            )
            tableRow.addView(textView)
            if (isHeader) {
                tableRowHeader = tableRow
                textView.setBackgroundResource(R.drawable.cell_shape_blue)
                (tableRow as DragLinearLayout).setViewDraggable(
                    textView,
                    textView
                ) // the child is its own drag handle
                textView.layoutParams =
                    TableRow.LayoutParams(0, TableRow.LayoutParams.MATCH_PARENT, 0f)
                val listener: DragLinearLayout.OnViewSwapListener = object :
                    DragLinearLayout.OnViewSwapListener {
                    override fun onSwap(
                        draggedView: View?,
                        initPosition: Int,
                        swappedView: View?,
                        swappedPosition: Int
                    ) {
                    }

                    override fun onSwapFinish() {
                        changeTitleOrder()
                    }
                }
                tableRow.setOnViewSwapListener(listener)
            }
        }
        this.addView(tableRow)
    }

    /*
     * The table is recreated when the header column is dragged and dropped.
     */
    private fun changeTitleOrder() {
        headerKeys = arrayListOf()
        for (i in 0 until tableRowHeader.childCount) {
            val view = tableRowHeader.getChildAt(i)
            headerKeys.add(view.tag.toString())
        }
        refreshTable()
    }
}