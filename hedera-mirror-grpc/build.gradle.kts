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

description = "Hedera Mirror Node GRPC API"

plugins { id("spring-conventions") }

dependencies {
    implementation(project(":common"))
    implementation(project(":protobuf"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.ongres.scram:client")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.grpc:grpc-core")
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-services")
    implementation("io.micrometer:micrometer-registry-elastic")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.projectreactor.addons:reactor-extra")
    implementation("io.projectreactor:reactor-core-micrometer")
    implementation("io.vertx:vertx-pg-client")
    implementation("io.vertx:vertx-codegen")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("net.devh:grpc-spring-boot-starter")
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("org.msgpack:jackson-dataformat-msgpack")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly(
        group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("com.playtika.testcontainers:embedded-postgresql")
    testImplementation("com.playtika.testcontainers:embedded-redis")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.flywaydb:flyway-core")
}

tasks.assemble { dependsOn("package") }
