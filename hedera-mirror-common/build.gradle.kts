/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

description = "Hedera Mirror Node Common"

plugins { id("java-conventions") }

dependencies {
    val testClasses by configurations.creating
    annotationProcessor(group = "com.querydsl", name = "querydsl-apt", classifier = "jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.google.guava:guava")
    api("com.google.protobuf:protobuf-java")
    api("com.hedera.hashgraph:hedera-protobuf-java-api") { isTransitive = false }
    api("com.querydsl:querydsl-apt")
    api("com.querydsl:querydsl-jpa")
    api("io.hypersistence:hypersistence-utils-hibernate-62")
    api("commons-codec:commons-codec")
    api("io.projectreactor:reactor-core")
    api("org.apache.commons:commons-lang3")
    api("org.apache.tuweni:tuweni-bytes")
    api("org.jetbrains:annotations")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-log4j2")
    testImplementation("org.hyperledger.besu:evm")
    testClasses(sourceSets["test"].output)
}

java.sourceSets["main"].java { srcDir("build/generated/sources/annotationProcessor/java/main") }
