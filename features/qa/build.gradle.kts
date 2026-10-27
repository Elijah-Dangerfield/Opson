plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.qa"
}

// The QA menu. :apps:compose depends on this and :features:qa:impl only off the store channel
// (isStoreBuild in build-logic), and nothing else may depend on either, so a store binary has no
// QA code in it at all.
