plugins {
    id("java")
}

group = "io.img2pdf"
version = "1.0-SNAPSHOT"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

allprojects {
    group = "io.img2pdf"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21

            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release = 21
        }
    }

    tasks.withType<Jar>().configureEach {
        archiveBaseName = "img2pdf-${project.name}"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
