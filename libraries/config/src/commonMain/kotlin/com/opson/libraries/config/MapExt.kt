package com.opson.libraries.config

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.throwIfDebug
import kotlin.reflect.KClass

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
                // the declared default. A kill switch is
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
