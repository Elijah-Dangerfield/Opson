@file:OptIn(ExperimentalSerializationApi::class)

package com.opson.libraries.navigation

import androidx.navigation.NavDestination
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.descriptors.SerialDescriptor

/**
 * Marks a [Route] whose name must never leave the phone: crash reports don't tag it as the
 * current route and the jank monitor doesn't name it, so attribution stays with the screen
 * beneath it. For screens whose being open says something about the person's health (Find
 * support, the kind note after a hard stretch, "Why not cut back?").
 *
 * An annotation rather than an interface: the app shell only ever has a [NavDestination], never
 * a route instance, and Kotlin/Native can't ask a class for its supertypes. A [SerialInfo]
 * annotation lives on the route's serial descriptor, which every destination is built from, so
 * [screen], [dialog] and [bottomSheet] see it when the graph is built.
 */
@SerialInfo
@Target(AnnotationTarget.CLASS)
annotation class UnloggedRoute

/** The serial names of every [UnloggedRoute] the graph registered. Written while the graph builds, read after. */
@PublishedApi
internal object UnloggedRoutes {
    private val names = mutableSetOf<String>()

    fun note(descriptor: SerialDescriptor) {
        if (descriptor.annotations.any { it is UnloggedRoute }) names += descriptor.serialName
    }

    fun contains(destination: NavDestination): Boolean {
        val name = destination.route?.substringBefore('/')?.substringBefore('?') ?: return false
        return name in names
    }
}

/** Whether this destination is an [UnloggedRoute]'s, so its name stays out of crash and jank reports. */
fun NavDestination.isUnlogged(): Boolean = UnloggedRoutes.contains(this)
