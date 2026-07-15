package com.lagradost.quicknovel.tachiyomi

interface PreferenceStore {

    fun getString(key: String, defaultValue: String = ""): PreferenceData<String>

    fun getLong(key: String, defaultValue: Long = 0): PreferenceData<Long>

    fun getInt(key: String, defaultValue: Int = 0): PreferenceData<Int>

    fun getFloat(key: String, defaultValue: Float = 0f): PreferenceData<Float>

    fun getBoolean(key: String, defaultValue: Boolean = false): PreferenceData<Boolean>

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): PreferenceData<Set<String>>

    fun <T> getObject(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): PreferenceData<T>

    fun getAll(): Map<String, *>
}

inline fun <reified T : Enum<T>> PreferenceStore.getEnum(
    key: String,
    defaultValue: T,
): PreferenceData<T> {
    return getObject(
        key = key,
        defaultValue = defaultValue,
        serializer = { it.name },
        deserializer = {
            try {
                enumValueOf(it)
            } catch (e: IllegalArgumentException) {
                defaultValue
            }
        },
    )
}