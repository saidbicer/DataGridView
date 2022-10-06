package com.said.datagridview

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.collect.ComparisonChain
import java.util.*

class SortUtil : Comparator<Map<String, String>> {
    companion object {
        private var keyStore: List<String> = listOf()
        fun sortByTag(data: List<Map<String, String>>, keyStore_: List<String>) {
            keyStore = keyStore_
            Collections.sort(data, SortUtil())
        }
    }

    override fun compare(a1: Map<String, String>, a2: Map<String, String>): Int {
        return if (keyStore.size == 1) {
            return a1[keyStore[0]]!!.compareTo(a2[keyStore[0]]!!, ignoreCase = true)
        } else {
            ComparisonChain.start()
                .compare(a1[keyStore[0]], a2[keyStore[0]])
                .compare(a1[keyStore[1]], a2[keyStore[1]])
                .result()
        }
    }
}
