package com.said.flightdatagrid

class ItemModel(
    val flightNumber: String,
    val airline: String,
    val destination: String,
    val date: String
) {
    fun result(): Map<String, String> {
        val map = hashMapOf<String, String>()
        map[this::flightNumber.name] = flightNumber
        map[this::airline.name] = airline
        map[this::destination.name] = destination
        map[this::date.name] = date
        return map
    }
}