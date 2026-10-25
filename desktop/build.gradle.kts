import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.test {
    useJUnit()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jmdns:jmdns:3.5.9")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

compose.desktop {
    application {
        mainClass = "com.ankiride.bridge.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe
            )
            packageName = "AnkiRideBridge"
            packageVersion = "4.0.0"
            description = "Zwift Click v2 Bridge for Desktop Anki and MyWhoosh"
            vendor = "AnkiRide"

            modules(
                "java.instrument",
                "java.management",
                "java.naming",
                "java.prefs",
                "java.desktop",
                "jdk.unsupported"
            )

            linux {
                shortcut = true
                menuGroup = "Utility"
                debMaintainer = "ankiride@example.com"
            }

            windows {
                shortcut = true
                menuGroup = "AnkiRide Bridge"
                upgradeUuid = "d273f680-d548-419d-b9d1-fa0472345229"
            }

            macOS {
                bundleID = "com.ankiride.bridge.desktop"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSBluetoothAlwaysUsageDescription</key>
                        <string>AnkiRide Bridge connects to your Zwift Click v2 handlebar controller.</string>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>AnkiRide Bridge advertises OpenBikeControl (_openbikecontrol._tcp) to MyWhoosh.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_openbikecontrol._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
