package com.donut.mixfile.util

import java.util.*

data class FileDataLog(
    val shareInfoData: String,
    val name: String,
    val size: Long,
    val time: Date = Date(),
) {

    override fun hashCode(): Int {
        return shareInfoData.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileDataLog) return false
        return shareInfoData.contentEquals(other.shareInfoData)
    }
}