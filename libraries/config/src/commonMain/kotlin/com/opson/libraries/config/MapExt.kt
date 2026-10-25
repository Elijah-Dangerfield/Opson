package com.opson.libraries.config

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.throwIfDebug
import kotlin.reflect.KClass

/**
 * Resolves a value at `[root].[path]` from a nested map, casting it to [T] when possible.
 */
inline fun <reified T : Any> Map<String, *>.getValueForPath(root: String, vararg path: String) =
    getValueRecursive<T>(listOf(root) + path.toList(), T::class)

/** Same as the vararg overload but accepts the child [path] as a list. */
inline fun <reified T : Any> Map<String, *>.getValueForPath(root: String, path: List<String>) =
    getValueRecursive<T>(listOf(root) + path.toList(), T::class)

/** Resolves a value using a dot-delimited [fullPath]. */
inline fun <reified T : Any> Map<String, *>.getValueForPath(fullPath: String) =
    getValueRecursive<T>(fullPath.split('.'), T::class)

/**
 * Recursively traverses a nested map structure and attempts to coerce the final value to [clazz].
 */
@Suppress("UNCHECKED_CAST", "ReturnCount")
fun <T : Any> Map<String, *>.getValueRecursive(path: List<String>, clazz: KClass<*>): T? {
    if (path.size == 1) {
        val rawValue = this[path.first()] ?: return null
        return Catching {
            when (clazz) {
                String::class -> rawValue.toString() as? T
                // Not `toBoolean()`. That maps *everything* that is not "true"
                // to `false`, so a string typed into a boolean key in the admin
                // console would turn that feature off on every device that
                // fetched the config — silently, and without falling back to
                // the declared default. A kill switch or a monetization flag is
                // meant to fail open, and this is the one path that could
                // quietly make one fail closed. The general rule: a parse that
                // invents an answer is worse than no answer. The default was
                // chosen deliberately; the invented value is whatever the
                // coercion happened to produce.
                //
                // Case stays forgiving, because the console lets an operator
                // type a raw value and "True" is not a mistake worth punishing.
                // Anything else resolves to null and the caller's default wins.
                Boolean::class -> when (rawValue.toString().lowercase()) {
                    "true" -> true as T
                    "false" -> false as T
                    else -> null
                }
                Int::class -> rawValue.toString().toDoubleOrNull()?.toInt() as? T
                Number::class -> rawValue.toString().toDoubleOrNull() as? T
                Double::class -> rawValue.toString().toDoubleOrNull() as? T
                Float::class -> rawValue.toString().toDoubleOrNull()?.toFloat() as? T
                Byte::class -> rawValue.toString().toByteOrNull() as? T
                Short::class -> rawValue.toString().toShortOrNull() as? T
                Long::class -> rawValue.toString().toDoubleOrNull()?.toLong() as? T
                else -> rawValue as? T
            }
        }
            .logOnFailure { "Failed to cast config value for path=${path.joinToString(".")}" }
            .throwIfDebug()
            .getOrNull()
    }
    val subMap = this[path.first()] as? Map<String, *> ?: return null
    return subMap.getValueRecursive(path.drop(1), clazz)
}

/** Adds [key] -> [value] when [condition] is true. */
fun <K, V> Map<K, V>.plusIf(condition: Boolean, key: K, value: V?): Map<K, V> =
    if (condition) plusIfNotNull(key, value) else this

/** Adds [pair] when the value is not null; otherwise returns the receiver. */
fun <T, U> Map<T, U>.plusIfNotNull(pair: Pair<T, U?>): Map<T, U> =
    pair.second?.let { plus(pair.first to it) } ?: this

/** Adds `[key] -> [value]` when [value] is not null; otherwise returns the receiver. */
fun <T, U> Map<T, U>.plusIfNotNull(key: T, value: U?): Map<T, U> =
    plusIfNotNull(key to value)

@Suppress("UNCHECKED_CAST")
fun <K, V> Map<K, V?>.filterNotNullValues(): Map<K, V> =
    filterValues { it != null } as Map<K, V>

@Suppress("UNCHECKED_CAST")
fun <K> Map<K, *>?.mergeWith(map: Map<K, *>?): Map<K, *>? {
    if (this == null) return map
    val mutableBaseMap = toMutableMap()
    map?.keys?.forEach { key ->
        val baseValue = this[key]
        val newValue = map[key]
        mutableBaseMap[key] = when {
            newValue is Map<*, *> && baseValue is Map<*, *> ->
                (baseValue as Map<K, *>).mergeWith(newValue as Map<K, *>)
            else -> newValue
        }
    }
    return mutableBaseMap
}

/**
 * Reduces a list of maps into a single map using [mergeWith], allowing later maps to override earlier ones.
 */
fun <K> List<Map<K, *>>.toMergedMap(): Map<K, *> {
    var merged: Map<K, Any?> = emptyMap()
    for (entry in this) {
        merged = merged.mergeWith(entry) as Map<K, Any?>
    }
    return merged
}
