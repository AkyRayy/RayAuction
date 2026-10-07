
plugins {
    java
    id("com.gradleup.shadow") version "8.3.9"
    id("com.diffplug.spotless") version "7.0.2"
}

val pluginVersion: String = property("version") as String

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.mysql:mysql-connector-j:9.2.0")
    compileOnly("org.apache.maven.resolver:maven-resolver-api:2.0.18")

    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("org.postgresql:postgresql:42.7.7")

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

spotless {
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        indentWithSpaces(4)
        importOrder("java", "javax", "", "\\#")
    }
    format("resources") {
        target("src/main/resources/**/*.yml", "src/main/resources/**/*.sql")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks {
    processResources {
        filesMatching("paper-plugin.yml") {
            filter { line -> line.replace("\${version}", pluginVersion) }
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing,-serial", "-Werror"))
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release = 21
    }

    test {
        useJUnitPlatform()
        maxHeapSize = "768m"
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    shadowJar {
        archiveClassifier.set("full")
        relocate("com.zaxxer.hikari", "ray.labs.rayauction.libs.hikari")
        relocate("com.github.benmanes.caffeine", "ray.labs.rayauction.libs.caffeine")
        relocate("org.h2", "ray.labs.rayauction.libs.h2")
        relocate("org.postgresql", "ray.labs.rayauction.libs.postgresql")
        relocate("org.checkerframework", "ray.labs.rayauction.libs.checkerframework")
        relocate("org.intellij.lang.annotations", "ray.labs.rayauction.libs.intellij")
        relocate("org.jetbrains.annotations", "ray.labs.rayauction.libs.jetbrains")
        mergeServiceFiles()
        minimize {
            exclude(dependency("com.h2database:h2:.*"))
            exclude(dependency("org.postgresql:postgresql:.*"))
        }
    }
}
