package com.pr0gramm.app.parcel

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable

inline fun <reified T : Parcelable> Bundle.getParcelableOrNull(key: String): T? {
    return getParcelableOrThrow(key)
}

inline fun <reified T : Parcelable?> Bundle.getParcelableOrThrow(key: String): T {
    val value = getParcelable<Parcelable?>(key)

    require(value is T) {
        if (value == null) {
            "No parcelable found for '$key'"
        } else {
            "Parcelable '$key' not of expected type"
        }
    }

    return value
}

inline fun <reified T : Parcelable> Intent.getExtraParcelableOrThrow(key: String): T {
    val extras = this.extras ?: throw IllegalArgumentException("no extras set on intent")
    return extras.getParcelableOrThrow<T>(key)
}

private val Class<*>.directName: String
    get() {
        return name.takeLastWhile { it != '.' }.replace('$', '.')
    }
