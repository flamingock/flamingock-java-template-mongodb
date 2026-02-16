import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.jreleaser.model.Active
import org.jreleaser.model.UpdateSection

plugins {
    `java-library`
    `maven-publish`
    id("org.jreleaser") version "1.15.0"
    id("com.diffplug.spotless") version "6.25.0"
}

fun flamingockVersion(): String {
    var passedAsParameter = false
    val flamingockVersionAsParameter: String? = project.findProperty("flamingockVersion")?.toString()
    val flamingockVersion: String = if (flamingockVersionAsParameter != null) {
        passedAsParameter = true
        flamingockVersionAsParameter
    } else {
        val metadataUrl = "https://repo.maven.apache.org/maven2/io/flamingock/flamingock-core/maven-metadata.xml"
        try {
            val metadata = URL(metadataUrl).readText()
            val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            val inputStream = metadata.byteInputStream()
            val document = documentBuilder.parse(inputStream)
            document.getElementsByTagName("latest").item(0).textContent
        } catch (e: Exception) {
            throw RuntimeException("Cannot obtain Flamingock's latest version", e)
        }
    }
    logger.lifecycle("Building with flamingock version${if (passedAsParameter) "[from parameter]" else ""}: $flamingockVersion")
    return flamingockVersion
}

val flamingockVersion = flamingockVersion()

group = "io.flamingock"
version = flamingockVersion

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("io.flamingock:flamingock-core-commons:$flamingockVersion")
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
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set(project.name)
                description.set(project.description)
                url.set("https://flamingock.io")
                inceptionYear.set("2024")

                organization {
                    name.set("Flamingock")
                    url.set("https://www.flamingock.io")
                }

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://spdx.org/licenses/Apache-2.0.html")
                    }
                }

                developers {
                    developer {
                        id.set("dieppa")
                        name.set("Antonio Perez Dieppa")
                        email.set("aperezdieppa@flamingock.io")
                    }
                    developer {
                        id.set("osantana")
                        name.set("Oliver Santana")
                        email.set("osantana@flamingock.io")
                    }
                    developer {
                        id.set("bercianor")
                        name.set("Ruben Berciano")
                        email.set("bercianor@flamingock.io")
                    }
                    developer {
                        id.set("dfrigolet")
                        name.set("David Frigolet")
                        email.set("dfrigolet@flamingock.io")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/flamingock/flamingock-java-template-mongodb/issues")
                }

                scm {
                    connection.set("scm:git:https://github.com/flamingock/flamingock-java-template-mongodb.git")
                    developerConnection.set("scm:git:ssh://github.com:flamingock/flamingock-java-template-mongodb.git")
                    url.set("https://github.com/flamingock/flamingock-java-template-mongodb")
                }
            }
        }
    }
}

jreleaser {
    project {
        inceptionYear.set("2024")
        authors.set(setOf("dieppa", "osantana", "bercianor", "dfrigolet"))
    }
    signing {
        active.set(Active.ALWAYS)
        armored = true
        enabled = true
    }
    gitRootSearch.set(true)
    release {
        github {
            update {
                enabled.set(true)
                sections.set(setOf(UpdateSection.TITLE, UpdateSection.BODY, UpdateSection.ASSETS))
            }
            prerelease {
                pattern.set("^(0\\..*|.*-(beta\\.?\\d*|snapshot\\.?\\d*|alpha\\.?\\d*|rc\\.?\\d*|RC\\.?\\d*)\$)")
            }
            changelog {
                enabled.set(true)
                formatted.set(Active.ALWAYS)
                sort.set(org.jreleaser.model.Changelog.Sort.DESC)
                links.set(true)
                preset.set("conventional-commits")
                releaseName.set("Release {{tagName}}")
                content.set("""
                        ## Changelog
                        {{changelogChanges}}
                        {{changelogContributors}}
                    """.trimIndent())
                categoryTitleFormat.set("### {{categoryTitle}}")
                format.set(
                    """|- {{commitShortHash}}
                           | {{#commitIsConventional}}
                           |{{#conventionalCommitIsBreakingChange}}:rotating_light: {{/conventionalCommitIsBreakingChange}}
                           |{{#conventionalCommitScope}}**{{conventionalCommitScope}}**: {{/conventionalCommitScope}}
                           |{{conventionalCommitDescription}}
                           |{{#conventionalCommitBreakingChangeContent}} - *{{conventionalCommitBreakingChangeContent}}*{{/conventionalCommitBreakingChangeContent}}
                           |{{/commitIsConventional}}
                           |{{^commitIsConventional}}{{commitTitle}}{{/commitIsConventional}}
                           |{{#commitHasIssues}}, closes{{#commitIssues}} {{issue}}{{/commitIssues}}{{/commitHasIssues}}
                           |{{#contributorName}} ({{contributorName}}){{/contributorName}}
                        |""".trimMargin().replace("\n", "").replace("\r", "")
                )
                contributors {
                    enabled.set(true)
                    format.set("- {{contributorName}} ({{contributorUsernameAsLink}})")
                }
            }
        }
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active.set(Active.ALWAYS)
                    applyMavenCentralRules.set(true)
                    url.set("https://central.sonatype.com/api/v1/publisher")
                    stagingRepository("build/staging-deploy")
                    maxRetries.set(90)
                    retryDelay.set(20)
                }
            }
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
