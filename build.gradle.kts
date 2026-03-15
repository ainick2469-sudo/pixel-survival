plugins {
    application
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.ainick2469"
version = "0.007"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.github.ainick2469.pixelsurvival.app.PixelSurvivalLauncher"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

dependencies {
    implementation("org.jmonkeyengine:jme3-core:3.7.0-stable")
    implementation("org.jmonkeyengine:jme3-desktop:3.7.0-stable")
    implementation("org.jmonkeyengine:jme3-lwjgl3:3.7.0-stable")
    implementation("org.jmonkeyengine:jme3-plugins:3.7.0-stable")
    implementation("org.jmonkeyengine:jme3-jogg:3.7.0-stable")

    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from("data") {
        into("data")
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("pixel-survival-desktop.jar")
    archiveClassifier.set("desktop")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

distributions {
    main {
        contents {
            from("data") {
                into("data")
            }
        }
    }
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir
}
