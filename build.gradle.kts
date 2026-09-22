// Top-level build file for PSBDx DevBrowser.
// Copyright (C) 2024 PSBDx DevBrowser Contributors
// Licensed under the GNU General Public License v3.0 (see LICENSE).

plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
