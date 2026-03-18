plugins {
    `java-library`
    `maven-publish`
    id("com.diffplug.spotless") version "6.25.0"
    id("org.jreleaser") version "1.15.0"
}


group = "io.flamingock"
version = "1.0.0-beta.4"

val flamingockVersion = "1.2.0-beta.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.flamingock:flamingock-core-api:${flamingockVersion}")//we need nullable
    implementation("io.flamingock:flamingock-template-api:$flamingockVersion")
    implementation("org.slf4j:slf4j-api:1.7.36")
    compileOnly("org.mongodb:mongodb-driver-sync:4.0.0")

    testAnnotationProcessor("io.flamingock:flamingock-processor:$flamingockVersion")
    testImplementation("io.flamingock:flamingock-auditstore-mongodb-sync:$flamingockVersion")
    testImplementation("io.flamingock:mongodb-sync-target-system:$flamingockVersion")
    testImplementation("io.flamingock:test-util:$flamingockVersion")
    testImplementation("io.flamingock:mongodb-util:$flamingockVersion")
    testImplementation("org.testcontainers:testcontainers-mongodb:2.0.2")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

description = "MongoDB change templates for document database operations"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    if (name.contains("Test", ignoreCase = true)) {
        options.compilerArgs.addAll(listOf(
            "-Asources=${projectDir}/src/test/java",
            "-Aresources=${projectDir}/src/test/resources"
        ))
    }
}

configurations.testImplementation {
    extendsFrom(configurations.compileOnly.get())
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("Flamingock MongoDB Sync Template")
                description.set("MongoDB change templates for document database operations using Flamingock")
                url.set("https://flamingock.io")

                organization {
                    name.set("Flamingock")
                    url.set("https://flamingock.io")
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/flamingock/flamingock-java-template-mongodb/issues")
                }

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("dieppa")
                        name.set("Antonio Perez Dieppa")
                        email.set("dieppa@flamingock.io")
                    }
                    developer {
                        id.set("osantana")
                        name.set("Oscar Santana")
                        email.set("osantana@flamingock.io")
                    }
                    developer {
                        id.set("bercianor")
                        name.set("Berciano Ramiro")
                        email.set("bercianor@flamingock.io")
                    }
                    developer {
                        id.set("dfrigolet")
                        name.set("Daniel Frigolet")
                        email.set("dfrigolet@flamingock.io")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/flamingock/flamingock-java-template-mongodb.git")
                    developerConnection.set("scm:git:ssh://github.com/flamingock/flamingock-java-template-mongodb.git")
                    url.set("https://github.com/flamingock/flamingock-java-template-mongodb")
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

val licenseHeaderText = """/*
 * Copyright ${'$'}YEAR Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */"""

spotless {
    java {
        target("src/**/*.java")
        licenseHeader(licenseHeaderText)
    }

    kotlin {
        target("src/**/*.kt")
        licenseHeader(licenseHeaderText)
    }
}

afterEvaluate {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn.removeIf { it.toString().contains("spotless") }
    }
    tasks.matching { it.name.startsWith("spotless") && it.name.contains("Check") }.configureEach {
        group = "verification"
        description = "Check license headers (manual task - not part of build)"
    }
}

jreleaser {
    project {
        inceptionYear = "2024"
        author("dieppa")
        author("osantana")
        author("bercianor")
        author("dfrigolet")
        description = "MongoDB change templates for document database operations using Flamingock"
    }
    signing {
        active = org.jreleaser.model.Active.ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active = org.jreleaser.model.Active.NEVER
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                    applyMavenCentralRules = true
                    maxRetries = 90
                    retryDelay = 20
                }
            }
        }
    }
    release {
        github {
            overwrite = true
            changelog {
                formatted = org.jreleaser.model.Active.ALWAYS
                preset = "conventional-commits"
            }
            prerelease {
                pattern = ".*(?:beta|alpha|rc|snapshot).*"
            }
        }
    }
}

tasks.register("createStagingDeployFolder") {
    doLast {
        mkdir(layout.buildDirectory.dir("staging-deploy"))
    }
}

tasks.matching { it.name == "publish" }.configureEach {
    finalizedBy("createStagingDeployFolder")
}
