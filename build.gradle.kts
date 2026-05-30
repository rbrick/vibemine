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
    implementation("org.graalvm.polyglot:polyglot:25.0.3")
    implementation("org.graalvm.polyglot:js:25.0.3")
    implementation("org.graalvm.regex:regex:25.0.3")

    implementation("org.xerial:sqlite-jdbc:3.53.1.0")



    // for syntax highlighting
    implementation("io.github.bonede:tree-sitter:0.26.6")
    implementation("io.github.bonede:tree-sitter-json:0.24.8")
    implementation("io.github.bonede:tree-sitter-javascript:0.23.1")

    // langchain4j - better tool calling
    implementation("dev.langchain4j:langchain4j-open-ai:1.15.0")
    implementation("dev.langchain4j:langchain4j:1.15.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

 apply(plugin = "com.gradleup.shadow")

tasks {

    shadowJar {
        // Graal languages are discovered with ServiceLoader. Allow duplicate service
        // descriptors through so Shadow can merge them, but keep normal duplicate files
        // like META-INF/LICENSE excluded for Paper's plugin remapper.
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filesMatching("META-INF/services/**") {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        mergeServiceFiles()

        dependencies {
            exclude(dependency("org.graalvm.js:js"))
        }
    }

    named("build") {
        dependsOn("shadowJar")
    }

    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
