/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
    `idea`
    id("java-platform")
    id("org.sonarqube")
}

// Can't use typed variable syntax due to Dependabot limitations
extra.apply {
    set("gson.version", "2.8.9") // Temporary until Apache jclouds supports gson 2.9
    set("protobufVersion", "3.21.9")
    set("reactorGrpcVersion", "1.2.3")
    set("snakeyaml.version", "1.33") // Temporary fix for transient dependency security issue
    set("spring-security.version", "5.7.5") // Temporary fix for transient dependency security issue
    set("testcontainersSpringBootVersion", "2.2.11")
}

// Creates a platform/BOM with specific versions so subprojects don't need to specify a version when using a dependency
dependencies {
    constraints {
        val protobufVersion: String by rootProject.extra
        val reactorGrpcVersion: String by rootProject.extra
        val testcontainersSpringBootVersion: String by rootProject.extra

        api("com.esaulpaugh:headlong:7.0.0")
        api("com.github.meanbeanlib:meanbean:3.0.0-M9")
        api("com.github.vertical-blank:sql-formatter:2.0.3")
        api("com.google.cloud:spring-cloud-gcp-dependencies:3.4.0")
        api("com.google.guava:guava:31.1-jre")
        api("com.google.protobuf:protobuf-java:$protobufVersion")
        api("com.hedera.evm:hedera-evm-api:0.31.0-SNAPSHOT")
        api("com.hedera.hashgraph:hedera-protobuf-java-api:0.31.0-SNAPSHOT")
        api("com.hedera.hashgraph:sdk:2.18.0")
        api("com.ongres.scram:client:2.1")
        api("com.playtika.testcontainers:embedded-google-pubsub:$testcontainersSpringBootVersion")
        api("com.playtika.testcontainers:embedded-postgresql:$testcontainersSpringBootVersion")
        api("com.playtika.testcontainers:embedded-redis:$testcontainersSpringBootVersion")
        api("com.salesforce.servicelibs:reactor-grpc-stub:$reactorGrpcVersion")
        api("com.vladmihalcea:hibernate-types-55:2.20.0")
        api("commons-beanutils:commons-beanutils:1.9.4")
        api("commons-io:commons-io:2.11.0")
        api("io.cucumber:cucumber-bom:7.8.1")
        api("io.github.mweirauch:micrometer-jvm-extras:0.2.2")
        api("io.grpc:grpc-bom:1.50.2")
        api("io.swagger:swagger-annotations:1.6.8")
        api("io.vertx:vertx-pg-client:4.3.4")
        api("javax.inject:javax.inject:1")
        api("net.devh:grpc-spring-boot-starter:2.13.1.RELEASE")
        api("net.java.dev.jna:jna:5.12.1")
        api("org.apache.commons:commons-compress:1.21")
        api("org.apache.commons:commons-math3:3.6.1")
        api("org.apache.tuweni:tuweni-bytes:2.3.0")
        api("org.apache.velocity:velocity-engine-core:2.3")
        api("org.gaul:s3proxy:2.0.0")
        api("org.hyperledger.besu:secp256k1:0.6.1")
        api("org.hyperledger.besu:evm:22.7.6")
        api("org.msgpack:jackson-dataformat-msgpack:0.9.3")
        api("org.springdoc:springdoc-openapi-webflux-ui:1.6.12")
        api("org.springframework.cloud:spring-cloud-dependencies:2021.0.5")
        api("org.testcontainers:junit-jupiter:1.17.5")
        api("org.web3j:core:5.0.0")
        api("software.amazon.awssdk:bom:2.18.3")
    }
}

allprojects {
    apply(plugin = "jacoco")
}

idea {
    module.isDownloadJavadoc = true
    module.isDownloadSources = true
}

sonarqube {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "hashgraph")
        property("sonar.projectKey", project.name)
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
