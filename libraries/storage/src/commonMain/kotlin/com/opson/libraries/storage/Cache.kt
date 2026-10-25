package com.opson.libraries.storage

interface Cache<T : Any> {
    val updates: kotlinx.coroutines.flow.Flow<T>

    suspend fun get(): T

    suspend fun set(value: T)

    suspend fun clear()

    /**
     * Read-modify-write.
     *
     * **Every implementation must override this with an atomic one.** The
     * default below is a convenience for a single-writer cache and nothing
     * more: it reads, then writes, and two overlapping callers each transform a
     * snapshot the other has already replaced, so the later write silently
     * reverts the earlier one.
     *
     * `AppData` is one record shared by every toggle, counter and boot-time
     * field, and several of its writers are live at once on a cold start. A
     * downstream app shipped the default and saw it as a setting the user
     * flipped that was back off next launch, alongside counters at zero.
     * Three unrelated features losing a write at once, which reads like the
     * file never saved rather than like a race.
     *
     * Auditing call sites to avoid concurrent writes is not the fix. The
     * writers live in different modules and none of them can know about the
     * others.
     */
    suspend fun update(transform: (T) -> T): T {
        val newValue = transform(get())
        set(newValue)
        return newValue
    }
}

