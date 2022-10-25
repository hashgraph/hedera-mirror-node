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

description = "Hedera Mirror Node Web3"

plugins {
    id("spring-conventions")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("com.hedera.evm:hedera-evm-api")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    implementation("javax.inject:javax.inject")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("com.playtika.testcontainers:embedded-postgresql")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.flywaydb:flyway-core")
}
