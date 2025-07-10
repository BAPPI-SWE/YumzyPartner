// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.kotlin.compose) apply false
    id("com.android.application") version "8.10.1" apply false // Or your current version
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false // Or your current version
    id("com.google.gms.google-services") version "4.4.1" apply false
}