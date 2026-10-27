package com.opson.util

import org.gradle.api.GradleException
import org.junit.Assert.assertThrows
import org.junit.Test

/** Only :apps:* reaches :features:qa, so dropping :apps:compose's dependency drops QA entirely. */
class QaBoundaryTest {

    @Test
    fun anotherFeature_mayNotDependOnTheQaMenu() {
        assertThrows(GradleException::class.java) { checkQaDependency(":features:today:impl", ":features:qa") }
        assertThrows(GradleException::class.java) { checkQaDependency(":libraries:ui", ":features:qa:impl") }
    }

    @Test
    fun theQaFeatureItself_andUnrelatedModules_pass() {
        checkQaDependency(":features:qa:impl", ":features:qa")
        checkQaDependency(":features:settings:impl", ":features:settings")
        checkQaDependency(":features:qaux", ":features:today")
    }
}
