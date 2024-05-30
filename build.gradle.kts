plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "store.kmpd"
version = layout.projectDirectory.file("VERSION").asFile.readText().trim()
description = "Apple binaries deployment plugin for KMP"

dependencies {
    val ktor_version = "2.3.10"
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-auth:$ktor_version")
    implementation("commons-codec:commons-codec:1.13")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // FIXME: Completion performance???
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.9.23") { isTransitive = false }
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23") { isTransitive = false }
    compileOnly("org.jetbrains.kotlin:kotlin-native-utils:1.9.23") { isTransitive = false }

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.ktor:ktor-server-core:$ktor_version")
    testImplementation("io.ktor:ktor-server-netty:$ktor_version")
    testImplementation("io.ktor:ktor-network-tls-certificates:$ktor_version")
}

java {
    withJavadocJar()
    withSourcesJar()
}

gradlePlugin {
    website.set("https://github.com/abdulowork/KMPAppleBinariesDeployment")
    vcsUrl.set("https://github.com/abdulowork/KMPAppleBinariesDeployment")

    plugins {
        create("kmp_apple_binaries_deployment") {
            id = "store.kmpd.plugin"
            implementationClass = "store.kmpd.KMPAppleBinariesDeploymentPlugin"
            displayName = "Apple binaries deployment plugin for KMP"
            description = "Plugin for deploying SPM packages and CocoaPods specs from Kotlin Multiplatform projects"
            tags.set(listOf("KMP", "Kotlin Multiplatform", "XCFramework", "publication", "deployment", "SPM", "Swift Package Manager", "CocoaPods"))
        }
    }
}

val functionalTest by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())

val clean = tasks.register<Delete>("cleanFT") {
    delete(
        layout.buildDirectory.dir("functionalTest"),
    )
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] + functionalTest.output
    dependsOn(
        clean,
        tasks.named("publishAllPublicationsToTestsRepository")
    )
    useJUnitPlatform()
}

val predeployment = layout.projectDirectory.dir("predeployment")
tasks.register<Zip>("zipDeployment") {
    from(predeployment)
    destinationDirectory.set(layout.projectDirectory)
    dependsOn("publishAllPublicationsToPredeploymentRepository")
}

publishing {
    repositories {
        maven(layout.projectDirectory.dir("repo")) {
            name = "Tests"
        }

        maven(layout.projectDirectory.dir("predeployment")) {
            name = "Predeployment"
        }
    }

    publications.withType(MavenPublication::class.java).all {
        pom {
            name = "Apple binaries deployment plugin for KMP"
            description = "Apple binaries deployment plugin for KMP"
            url = "https://github.com/abdulowork/KMPAppleBinariesDeployment"
            licenses {
                license {
                    name = "MIT License"
                    url = "https://opensource.org/license/mit"
                }
            }
            developers {
                developer {
                    id = "Tim"
                    name = "Tim"
                    email = "abdulowork@gmail.com"
                }
            }
            scm {
                connection = "scm:git:git@github.com:abdulowork/KMPAppleBinariesDeployment.git"
                developerConnection = "scm:git:git@github.com:abdulowork/KMPAppleBinariesDeployment.git"
                url = "https://github.com/abdulowork/KMPAppleBinariesDeployment"
            }
        }

    }
}

signing {
    if (project.properties["signPublications"] != null) {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}