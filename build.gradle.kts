plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.compose") version "1.7.3" apply false
}

// Root Task Proxies for Desktop Compose Application
fun syncComposeBinaries(srcDir: java.io.File, destDir: java.io.File) {
    if (!srcDir.exists()) return
    destDir.mkdirs()
    srcDir.listFiles()?.forEach { sub ->
        val target = java.io.File(destDir, sub.name)
        if (target.exists()) {
            target.walkBottomUp().forEach { it.setWritable(true) }
            target.deleteRecursively()
        }
        sub.copyRecursively(target, overwrite = true)
        // Preserve executable permissions on copied binaries and scripts
        sub.walkTopDown().forEach { srcFile ->
            if (srcFile.isFile && srcFile.canExecute()) {
                val relPath = srcFile.relativeTo(sub).path
                val targetFile = java.io.File(target, relPath)
                if (targetFile.exists()) {
                    targetFile.setExecutable(true, false)
                }
            }
        }
    }
}

tasks.register("run") {
    group = "application"
    description = "Runs the desktop Compose application."
    dependsOn(":desktop:run")
}

tasks.register("createDistributable") {
    group = "distribution"
    description = "Packages the desktop application distributable and copies to root build directory."
    dependsOn(":desktop:createDistributable")
    doLast {
        val srcDir = project(":desktop").layout.buildDirectory.dir("compose/binaries/main").get().asFile
        val destDir = layout.buildDirectory.dir("compose/binaries/main").get().asFile
        syncComposeBinaries(srcDir, destDir)
    }
}

tasks.register("packageDistributionForCurrentOS") {
    group = "distribution"
    description = "Packages native installer for current OS and copies to root build directory."
    dependsOn(":desktop:packageDistributionForCurrentOS")
    doLast {
        val srcDir = project(":desktop").layout.buildDirectory.dir("compose/binaries/main").get().asFile
        val destDir = layout.buildDirectory.dir("compose/binaries/main").get().asFile
        syncComposeBinaries(srcDir, destDir)
    }
}

// Also ensure whenever desktop packaging tasks run directly, outputs sync to root build/
gradle.projectsEvaluated {
    val desktopProj = findProject(":desktop")
    if (desktopProj != null) {
        desktopProj.tasks.matching {
            it.name in listOf(
                "createDistributable",
                "packageDistributionForCurrentOS",
                "packageDeb",
                "packageDmg",
                "packageMsi",
                "packageExe"
            )
        }.configureEach {
            doLast {
                val srcDir = desktopProj.layout.buildDirectory.dir("compose/binaries/main").get().asFile
                val destDir = rootProject.layout.buildDirectory.dir("compose/binaries/main").get().asFile
                syncComposeBinaries(srcDir, destDir)
            }
        }
    }
}
