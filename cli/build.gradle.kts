plugins {
    kotlin("multiplatform")
}

kotlin.linuxX64 {
    compilations["main"].apply {
        defaultSourceSet {
            dependencies { implementation(project(":core")) }
            languageSettings.useExperimentalAnnotation("kotlin.Experimental")
        }
        enableEndorsedLibs = true
    }

    binaries.executable { entryPoint = "com.serebit.wraith.cli.main" }
}

tasks.register("package") {
    dependsOn("build")
    dependsOn(":core:package")

    doLast {
        buildDir.resolve("bin/linuxX64/releaseExecutable/cli.kexe")
            .copyTo(rootProject.buildDir.resolve("package/wraith-master"), overwrite = true)
            .setExecutable(true)
    }
}

tasks.register("install") {
    dependsOn("package")
    dependsOn(":core:package")

    doLast {
        val installDir = file(properties["installdir"] as? String ?: "/usr/local")

        rootProject.buildDir.resolve("package/wraith-master")
            .copyTo(installDir.resolve("bin/wraith-master"), overwrite = true)
            .also { exec { commandLine("chmod", "00755", it.absolutePath) } }
    }
}
