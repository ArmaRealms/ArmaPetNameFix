plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
}

group = "io.github.tanguygab"
version = "2.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.135-stable")
}

sourceSets.test {
    compileClasspath += sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().compileClasspath
}

val packetCheck = tasks.register<JavaExec>("packetCheck") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.tanguygab.petnamefix.PacketCheck")
    enableAssertions = true
    doFirst {
        workingDir = layout.buildDirectory.dir("packet-check").get().asFile.apply { mkdirs() }
    }
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
}

tasks.check { dependsOn(packetCheck) }

// Checks run through packetCheck without a separate test framework.
tasks.test { failOnNoDiscoveredTests = false }

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
