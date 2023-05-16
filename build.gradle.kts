/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

description = "Hedera Mirror Node imports data from consensus nodes and serves it via an API"

plugins {
    id("idea")
    id("java-platform")
    id("org.sonarqube")
    id("snykcode-extension")
}

// Can't use typed variable syntax due to Dependabot limitations
extra.apply {
    set("gson.version", "2.8.9") // Temporary until Apache jclouds supports gson 2.9
    set("mapStructVersion", "1.5.5.Final")
    set("postgresql.version", "42.6.0") // Temporary fix for transient dependency security issue
    set("protobufVersion", "3.23.0")
    set("reactorGrpcVersion", "1.2.4")
    set("snakeyaml.version", "1.33") // Temporary fix for transient dependency security issue
    set("testcontainersSpringBootVersion", "2.3.2")
    set("vertxVersion", "4.4.2")
}

// Creates a platform/BOM with specific versions so subprojects don't need to specify a version when using a dependency
dependencies {
    constraints {
        val mapStructVersion: String by rootProject.extra
        val protobufVersion: String by rootProject.extra
        val reactorGrpcVersion: String by rootProject.extra
        val testcontainersSpringBootVersion: String by rootProject.extra
        val vertxVersion: String by rootProject.extra

        api("com.esaulpaugh:headlong:9.2.0")
        api("com.github.meanbeanlib:meanbean:3.0.0-M9")
        api("com.github.vertical-blank:sql-formatter:2.0.3")
        api("com.github.vladimir-bukhtoyarov:bucket4j-core:7.6.0")
        api("com.google.cloud:spring-cloud-gcp-dependencies:3.5.0")
        api("com.google.guava:guava:31.1-jre")
        api("com.google.protobuf:protobuf-java:$protobufVersion")
        api("com.graphql-java-generator:graphql-java-client-runtime:2.0")
        api("com.graphql-java:graphql-java-extended-scalars:20.2")
        api("com.graphql-java:graphql-java-extended-validation:20.0-validator-6.2.0.Final")
        api("com.hedera.evm:hedera-evm:0.37.0")
        api("com.hedera.hashgraph:hedera-protobuf-java-api:0.38.9")
        api("com.hedera.hashgraph:sdk:2.24.1")
        api("com.ongres.scram:client:2.1")
        api("com.playtika.testcontainers:embedded-google-pubsub:$testcontainersSpringBootVersion")
        api("com.playtika.testcontainers:embedded-postgresql:$testcontainersSpringBootVersion")
        api("com.playtika.testcontainers:embedded-redis:$testcontainersSpringBootVersion")
        api("com.salesforce.servicelibs:reactor-grpc-stub:$reactorGrpcVersion")
        api("com.vladmihalcea:hibernate-types-55:2.21.1")
        api("commons-beanutils:commons-beanutils:1.9.4")
        api("commons-io:commons-io:2.11.0")
        api("io.cucumber:cucumber-bom:7.12.0")
        api("io.github.mweirauch:micrometer-jvm-extras:0.2.2")
        api("io.grpc:grpc-bom:1.55.1")
        api("io.swagger:swagger-annotations:1.6.10")
        api("io.vertx:vertx-pg-client:$vertxVersion")
        api("io.vertx:vertx-codegen:$vertxVersion")
        api("javax.inject:javax.inject:1")
        api("net.devh:grpc-spring-boot-starter:2.14.0.RELEASE")
        api("net.java.dev.jna:jna:5.13.0")
        api("org.apache.commons:commons-compress:1.23.0")
        api("org.apache.commons:commons-math3:3.6.1")
        api("org.apache.tuweni:tuweni-bytes:2.3.1")
        api("org.apache.velocity:velocity-engine-core:2.3")
        api("org.gaul:s3proxy:2.0.0")
        api("org.hyperledger.besu:secp256k1:0.6.1")
        api("org.hyperledger.besu:evm:22.7.6")
        api("org.jetbrains:annotations:24.0.1")
        api("org.mapstruct:mapstruct:$mapStructVersion")
        api("org.mapstruct:mapstruct-processor:$mapStructVersion")
        api("org.msgpack:jackson-dataformat-msgpack:0.9.3")
        api("org.springdoc:springdoc-openapi-webflux-ui:1.7.0")
        api("org.springframework.cloud:spring-cloud-dependencies:2021.0.5")
        api("org.testcontainers:junit-jupiter:1.18.1")
        api("org.mockito:mockito-inline:5.2.0")
        api("software.amazon.awssdk:bom:2.20.65")
        api("uk.org.webcompere:system-stubs-jupiter:2.0.2")
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
            property("sonar.issue.ignore.multicriteria", "e1,e2,e3,e4,e5")
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
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}

fun replaceVersion(files: String, match: String) {
    ant.withGroovyBuilder {
        "replaceregexp"(
            "match" to match,
            "replace" to project.version,
            "flags" to "gm"
        ) {
            "fileset"(
                "dir" to rootProject.projectDir,
                "includes" to files,
                "excludes" to "**/node_modules/"
            )
        }
    }
}

// Replace release version in files
project.tasks.register("release") {
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

tasks.sonar {
    dependsOn(tasks.build)
}
