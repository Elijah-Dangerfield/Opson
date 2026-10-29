plugins {
  id("opson.feature")
}

android {
  namespace = "com.opson.features.onboarding"
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      // OnboardingSetupEnabled (release.onboardingSetup) is a FlagConfigValue, read by onboarding
      // and by Targets' weight goal row, so its supertype is part of this module's API.
      api(projects.libraries.config)
    }
  }
}
