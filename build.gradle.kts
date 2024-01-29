/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.github.gradle.node.npm.task.NpmSetupTask
import java.nio.file.Paths

description = "Hedera Mirror Node imports data from consensus nodes and serves it via an API"

plugins {
    id("com.diffplug.spotless")
    id("com.github.node-gradle.node")
    id("idea")
    id("java-platform")
    id("org.sonarqube")
    id("snykcode-extension")
}

// Can't use typed variable syntax due to Dependabot limitations
extra.apply {
    set("grpcVersion", "1.61.0")
    set("gson.version", "2.8.9") // Temporary until Apache jclouds supports gson 2.9
    set("json-path.version", "2.9.0") // Temporary until next Spring Boot version
    set("mapStructVersion", "1.5.5.Final")
    set("protobufVersion", "3.25.2")
    set("reactorGrpcVersion", "1.2.4")
    set("vertxVersion", "4.5.1")
}

// Temporarily override json version until snyk/gradle-plugin has an update with a fix
configurations["dataFiles"].dependencies.add(dependencies.create("org.json:json:20231013"))

// Creates a platform/BOM with specific versions so subprojects don't need to specify a version when
// using a dependency
dependencies {
    constraints {
        val grpcVersion: String by rootProject.extra
        val mapStructVersion: String by rootProject.extra
        val protobufVersion: String by rootProject.extra
        val reactorGrpcVersion: String by rootProject.extra
        val testcontainersSpringBootVersion: String by rootProject.extra
        val vertxVersion: String by rootProject.extra

        api("com.esaulpaugh:headlong:10.0.2")
        api("com.github.meanbeanlib:meanbean:3.0.0-M9")
        api("com.github.vertical-blank:sql-formatter:2.0.4")
        api("org.bouncycastle:bcprov-jdk15to18:1.77")
        api("com.bucket4j:bucket4j-core:8.7.0")
        api("com.google.cloud:spring-cloud-gcp-dependencies:5.0.0")
        api("com.google.guava:guava:33.0.0-jre")
        api("com.google.protobuf:protobuf-java:$protobufVersion")
        api("com.graphql-java-generator:graphql-java-client-runtime:2.4")
        api("com.graphql-java:graphql-java-extended-scalars:21.0")
        api("com.graphql-java:graphql-java-extended-validation:21.0")
        api("com.hedera.evm:hedera-evm:0.45.1")
        api("com.hedera.hashgraph:hedera-protobuf-java-api:0.45.3")
        api("com.hedera.hashgraph:sdk:2.29.0")
        api("com.ongres.scram:client:2.1")
        api("com.playtika.testcontainers:embedded-google-pubsub:3.1.4")
        api("com.redis.testcontainers:testcontainers-redis-junit-jupiter:1.4.6")
        api("com.salesforce.servicelibs:reactor-grpc-stub:$reactorGrpcVersion")
        api("commons-beanutils:commons-beanutils:1.9.4")
        api("commons-io:commons-io:2.15.1")
        api("io.cucumber:cucumber-bom:7.15.0")
        api("io.github.mweirauch:micrometer-jvm-extras:0.2.2")
        api("io.grpc:grpc-bom:$grpcVersion")
        api("io.hypersistence:hypersistence-utils-hibernate-63:3.7.0")
        api("io.projectreactor:reactor-core-micrometer:1.1.2")
        api("io.swagger:swagger-annotations:1.6.12")
        api("io.vertx:vertx-pg-client:$vertxVersion")
        api("io.vertx:vertx-codegen:$vertxVersion")
        api("jakarta.inject:jakarta.inject-api:2.0.1")
        api("net.devh:grpc-spring-boot-starter:2.15.0.RELEASE")
        api("net.java.dev.jna:jna:5.14.0")
        api("org.apache.commons:commons-compress:1.25.0")
        api("org.apache.commons:commons-math3:3.6.1")
        api("org.apache.tuweni:tuweni-bytes:2.3.1")
        api("org.apache.velocity:velocity-engine-core:2.3")
        api("org.eclipse.jetty.toolchain:jetty-jakarta-servlet-api:5.0.2")
        api("org.gaul:s3proxy:2.1.0")
        api("org.hyperledger.besu:secp256k1:0.8.0")
        api("org.hyperledger.besu:evm:23.10.0")
        api("org.jetbrains:annotations:24.1.0")
        api("org.mapstruct:mapstruct:$mapStructVersion")
        api("org.mapstruct:mapstruct-processor:$mapStructVersion")
        api("org.msgpack:jackson-dataformat-msgpack:0.9.8")
        api("org.springdoc:springdoc-openapi-webflux-ui:1.7.0")
        api("org.springframework.cloud:spring-cloud-dependencies:2023.0.0")
        api("org.testcontainers:junit-jupiter:1.19.4")
        api("org.mockito:mockito-inline:5.2.0")
        api("software.amazon.awssdk:bom:2.23.7")
        api("uk.org.webcompere:system-stubs-jupiter:2.1.6")
    }
}

allprojects {
    apply(plugin = "jacoco")
    apply(plugin = "org.sonarqube")

    sonarqube {
        properties {
            property("sonar.host.url", "https://sonarcloud.io")
            property("sonar.organization", "hashgraph")
            property("sonar.projectKey", "hedera-mirror-node")
            property("sonar.issue.ignore.multicriteria", "e1,e2,e3,e4,e5,e6")
            property("sonar.issue.ignore.multicriteria.e1.resourceKey", "**/*.java")
            property("sonar.issue.ignore.multicriteria.e1.ruleKey", "java:S6212")
            property("sonar.issue.ignore.multicriteria.e2.resourceKey", "**/*.java")
            property("sonar.issue.ignore.multicriteria.e2.ruleKey", "java:S125")
            property("sonar.issue.ignore.multicriteria.e3.resourceKey", "**/*.java")
            property("sonar.issue.ignore.multicriteria.e3.ruleKey", "java:S2187")
            property("sonar.issue.ignore.multicriteria.e4.resourceKey", "**/*.js")
            property("sonar.issue.ignore.multicriteria.e4.ruleKey", "javascript:S3758")
            property("sonar.issue.ignore.multicriteria.e5.resourceKey", "**/stateproof/*.sql")
            property("sonar.issue.ignore.multicriteria.e5.ruleKey", "plsql:S1192")
            property("sonar.issue.ignore.multicriteria.e6.resourceKey", "**/*.java")
            property("sonar.issue.ignore.multicriteria.e6.ruleKey", "java:S2970")
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

// Spotless uses Prettier and it requires Node.js
node {
    download = true
    version = "18.18.0"
    workDir = rootDir.resolve(".gradle").resolve("nodejs")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

spotless {
    val licenseHeader =
        """
/*
 * Copyright (C) ${'$'}YEAR Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */${"\n\n"}
"""
            .trimIndent()

    val npmExec =
        when (System.getProperty("os.name").lowercase().contains("windows")) {
            true -> Paths.get("npm.cmd")
            else -> Paths.get("bin", "npm")
        }
    val npmSetup = tasks.named("npmSetup").get() as NpmSetupTask
    val npmExecutable = npmSetup.npmDir.get().asFile.toPath().resolve(npmExec)

    isEnforceCheck = false

    if (!System.getenv().containsKey("CI")) {
        ratchetFrom("origin/main")
    }

    format("go") {
        endWithNewline()
        licenseHeader(licenseHeader, "package").updateYearWithLatest(true)
        target("hedera-mirror-rosetta/**/*.go")
        targetExclude("build/**")
        trimTrailingWhitespace()
    }
    format("javascript") {
        endWithNewline()
        indentWithSpaces(2)
        licenseHeader(licenseHeader, "$").updateYearWithLatest(true)
        prettier()
            .npmExecutable(npmExecutable)
            .npmInstallCache(Paths.get("${rootProject.rootDir}", ".gradle", "spotless"))
            .config(
                mapOf(
                    "bracketSpacing" to false,
                    "printWidth" to 120,
                    "singleQuote" to true,
                )
            )
        target("hedera-mirror-rest/**/*.js", "hedera-mirror-test/**/*.js")
        targetExclude("**/build/**", "**/node_modules/**", "**/__tests__/integration/*.test.js")
    }
    java {
        endWithNewline()
        palantirJavaFormat()
        licenseHeader(licenseHeader, "package").updateYearWithLatest(true)
        target("**/*.java")
        targetExclude("**/build/**", "hedera-mirror-rest/**", "hedera-mirror-rosetta/**")
        toggleOffOn()
    }
    kotlin {
        endWithNewline()
        ktfmt().kotlinlangStyle()
        licenseHeader(licenseHeader, "package").updateYearWithLatest(true)
        target("buildSrc/**/*.kt")
        targetExclude("**/build/**")
    }
    kotlinGradle {
        endWithNewline()
        ktfmt().kotlinlangStyle()
        licenseHeader(licenseHeader, "(description|import|plugins)").updateYearWithLatest(true)
        target("*.kts", "*/*.kts", "buildSrc/**/*.kts", "hedera-mirror-rest/*/*.kts")
        targetExclude("**/build/**")
    }
    format("miscellaneous") {
        endWithNewline()
        indentWithSpaces(2)
        prettier().npmExecutable(npmExecutable)
        target("**/*.json", "**/*.md", "**/*.yml", "**/*.yaml")
        targetExclude("**/build/**", "**/charts/**", "**/node_modules/**", "**/package-lock.json")
        trimTrailingWhitespace()
    }
    format("proto") {
        endWithNewline()
        indentWithSpaces(4)
        licenseHeader(licenseHeader, "(package|syntax)").updateYearWithLatest(true)
        target("hedera-mirror-protobuf/**/*.proto")
        targetExclude("build/**")
        trimTrailingWhitespace()
    }
    sql {
        endWithNewline()
        indentWithSpaces()
        target("hedera-mirror-(common|importer|rest)/**/*.sql")
        targetExclude("**/build/**", "**/node_modules/**")
        trimTrailingWhitespace()
    }
}

fun replaceVersion(files: String, match: String) {
    ant.withGroovyBuilder {
        "replaceregexp"("match" to match, "replace" to project.version, "flags" to "gm") {
            "fileset"(
                "dir" to rootProject.projectDir,
                "includes" to files,
                "excludes" to "**/node_modules/"
            )
        }
    }
}

tasks.nodeSetup { onlyIf { !this.nodeDir.get().asFile.exists() } }

// Replace release version in files
tasks.register("release") {
    doLast {
        replaceVersion("charts/**/Chart.yaml", "(?<=^(appVersion|version): ).+")
        replaceVersion("docker-compose.yml", "(?<=gcr.io/mirrornode/hedera-mirror-.+:).+")
        replaceVersion("gradle.properties", "(?<=^version=).+")
        replaceVersion(
            "hedera-mirror-rest/**/package*.json",
            "(?<=\"@hashgraph/(check-state-proof|mirror-rest|mirror-monitor)\",\\s{3,7}\"version\": \")[^\"]+"
        )
        replaceVersion("hedera-mirror-rest/**/openapi.yml", "(?<=^  version: ).+")
    }
}

tasks.sonar { dependsOn(tasks.build) }

tasks.spotlessApply { dependsOn(tasks.nodeSetup) }

tasks.spotlessCheck { dependsOn(tasks.nodeSetup) }
