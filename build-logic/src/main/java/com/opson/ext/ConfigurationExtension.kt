package com.opson.ext

import androidx.room.gradle.RoomExtension
import com.opson.util.addKspDependencyForAllTargets
import com.opson.util.configureKotlinInject
import com.opson.util.getModule
import com.opson.util.libs
import com.opson.util.optInKotlinMarkers
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import javax.inject.Inject

@ExtDsl
abstract class ConfigurationExtension {
    @get:Inject
    internal abstract val project: Project

    fun optIn(vararg markerClasses: String) {
        project.optInKotlinMarkers(*markerClasses)
    }

    fun storage() {
        project.pluginManager.apply(project.libs.plugins.androidxRoom.get().pluginId)
        project.extensions.configure(RoomExtension::class.java) {
            schemaDirectory("${project.projectDir}/schemas")
        }
        project.dependencies {
            add("implementation", getModule("libraries:storage"))
        }
        project.addKspDependencyForAllTargets(project.libs.androidx.room.compiler)
    }


    // Most modules will get di() from the convention plugin. But if needed this is there.
    fun di() {
        project.extensions.configure(KotlinMultiplatformExtension::class.java) {
            project.configureKotlinInject()
        }
    }


    fun ksp(configure: KspExtension.() -> Unit = {}) {
        project.pluginManager.apply("com.google.devtools.ksp")
        project.extensions.configure(configure)
    }
}
