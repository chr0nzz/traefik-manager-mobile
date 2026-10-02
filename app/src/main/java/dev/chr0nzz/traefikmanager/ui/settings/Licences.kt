package dev.chr0nzz.traefikmanager.ui.settings

internal data class Attribution(val name: String, val licence: String)

internal val ATTRIBUTIONS = listOf(
    Attribution("AndroidX (core, activity, lifecycle, navigation, datastore, biometric, window, glance, work)", "Apache-2.0"),
    Attribution("Jetpack Compose and Material 3", "Apache-2.0"),
    Attribution("Kotlin standard library, coroutines and kotlinx.serialization", "Apache-2.0"),
    Attribution("Dagger Hilt", "Apache-2.0"),
    Attribution("Retrofit", "Apache-2.0"),
    Attribution("OkHttp and Okio", "Apache-2.0"),
    Attribution("Coil", "Apache-2.0"),
    Attribution("MaterialKolor", "MIT"),
    Attribution("Google material-color-utilities", "Apache-2.0"),
    Attribution("Inter", "SIL Open Font License 1.1"),
    Attribution("JetBrains Mono", "SIL Open Font License 1.1"),
)

internal val OSS_NOTICES = listOf(
    "MaterialKolor is distributed under the MIT License. Copyright (c) Jordon de Hoog. " +
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
        "software and associated documentation files (the \"Software\"), to deal in the Software " +
        "without restriction, including without limitation the rights to use, copy, modify, merge, " +
        "publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons " +
        "to whom the Software is furnished to do so, subject to the following conditions: the above " +
        "copyright notice and this permission notice shall be included in all copies or substantial " +
        "portions of the Software. THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND.",
    "Inter is Copyright (c) The Inter Project Authors, and JetBrains Mono is Copyright (c) " +
        "The JetBrains Mono Project Authors. Both are licensed under the SIL Open Font License, " +
        "Version 1.1. The fonts and derivatives may be bundled, embedded, redistributed and sold " +
        "with any software provided that reserved names are not used, and that the licence text " +
        "accompanies the fonts. The full OFL 1.1 text is shipped with this app.",
)
