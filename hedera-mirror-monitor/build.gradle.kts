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

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

description = "Hedera Mirror Node Monitor"

plugins {
    id("org.openapi.generator")
    id("spring-conventions")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.google.guava:guava")
    implementation("com.hedera.hashgraph:sdk")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-inprocess")
    implementation("io.grpc:grpc-netty")
    implementation("io.grpc:grpc-stub")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.swagger:swagger-annotations")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-math3")
    implementation("org.springdoc:springdoc-openapi-webflux-ui")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-kubernetes-fabric8-autoconfig")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly(
        group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    testImplementation("com.github.meanbeanlib:meanbean")
    testImplementation("io.fabric8:kubernetes-server-mock")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("uk.org.webcompere:system-stubs-jupiter")
}

val configureOpenApi: (OpenApiGeneratorGenerateExtension) -> Void by extra

configureOpenApi(openApiGenerate)

tasks.withType<JavaCompile> { dependsOn("openApiGenerate") }

java.sourceSets["main"].java { srcDir(openApiGenerate.outputDir) }
