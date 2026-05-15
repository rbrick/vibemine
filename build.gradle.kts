 val openAiVersion = "4.35.0" // latest as of 05/13/2026

plugins {

    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.1"
}


buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("com.gradleup.shadow:shadow-gradle-plugin:9.4.1")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // include openai
    implementation("com.openai:openai-java:4.35.0")
    // for javascript
    implementation(platform("org.graalvm.js:js:25.0.3"))
    // hibernate orm instead of writing raw sql lol
    implementation("org.hibernate.orm:hibernate-core:7.3.4.Final")

    // for syntax highlighting
    implementation("io.github.bonede:tree-sitter:0.26.6")
    implementation("io.github.bonede:tree-sitter-json:0.24.8")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}



 apply(plugin = "com.gradleup.shadow")

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
