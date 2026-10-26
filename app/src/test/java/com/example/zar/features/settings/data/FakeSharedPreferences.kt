package com.example.zar.features.settings.data

import android.content.SharedPreferences

/** An in-memory SharedPreferences, so storage behaviour can be tested on the JVM. [failCommits] simulates a failed write. */
class FakeSharedPreferences(initial: Map<String, Any?> = emptyMap()) : SharedPreferences {
    val values = initial.toMutableMap()
    var failCommits = false
    var commitCount = 0

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key].let { if (it == null) defValue else it as String }
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key]?.let { it as Boolean } ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { values.clear() }
        override fun commit(): Boolean {
            commitCount++
            if (failCommits) return false
            apply()
            return true
        }
        override fun apply() {
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            pending.clear()
        }
    }
}
