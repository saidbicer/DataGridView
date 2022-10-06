package com.said.flightdatagrid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.said.datagridview.DataGridView


class MainActivity : AppCompatActivity() {

    private lateinit var dataGridView: DataGridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        dataGridView = findViewById(R.id.dataGridView)

        createDataGrid()
    }

    private fun createDataGrid() {

        val defaultHeaderOrder = arrayListOf(ItemModel::flightNumber.name, ItemModel::airline.name, ItemModel::destination.name, ItemModel::date.name)

        val headerItem = ItemModel("No", "Airline", "Destination", "Date").result()

        val itemList = arrayListOf<Map<String, String>>()
        itemList.add(ItemModel("8423", "Turkish Airlines", "Berlin", "19.12.2022").result())
        itemList.add(ItemModel("1342", "American Airlines", "Istanbul", "10.12.2022").result())
        itemList.add(ItemModel("3232", "Turkish Airlines", "Amsterdam", "12.12.2022").result())
        itemList.add(ItemModel("1235", "Hawaiian Airlines", "Istanbul", "13.12.2022").result())
        itemList.add(ItemModel("5342", "American Airlines", "Ankara", "13.12.2022").result())
        itemList.add(ItemModel("5123", "Turkish Airlines", "London", "13.12.2022").result())
        itemList.add(ItemModel("5388", "Hawaiian Airlines", "New York", "16.12.2022").result())
        itemList.add(ItemModel("6346", "Hawaiian Airlines", "Amsterdam", "16.12.2022").result())
        itemList.add(ItemModel("8565", "American Airlines", "Amsterdam", "17.12.2022").result())
        itemList.add(ItemModel("9443", "American Airlines", "London", "17.12.2022").result())

        dataGridView.createDataGrid(headerItem, defaultHeaderOrder, itemList)
    }

}
