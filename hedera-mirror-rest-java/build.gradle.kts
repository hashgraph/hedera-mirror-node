/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import org.openapitools.generator.gradle.plugin.extensions.OpenApiGeneratorGenerateExtension

description = "Hedera Mirror Node Rest Java"

plugins {
    id("org.openapi.generator")
    id("spring-conventions")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    // Temporary until spring-boot-starter-webflux bumps up the dependency
    implementation("io.projectreactor.netty:reactor-netty-http:1.1.13")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly(
        group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("com.playtika.testcontainers:embedded-postgresql")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito:mockito-inline")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation(project(path = ":common", configuration = "testClasses"))
}

val configureOpenApi: (OpenApiGeneratorGenerateExtension) -> Void by extra

configureOpenApi(openApiGenerate)

tasks.withType<JavaCompile> { dependsOn("openApiGenerate") }

java.sourceSets["main"].java { srcDir(openApiGenerate.outputDir) }
