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

description = "Hedera Mirror Node Test"

plugins {
    id("java-conventions")
}

dependencies {
    implementation(platform("io.cucumber:cucumber-bom"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("com.google.guava:guava")
    testImplementation("com.hedera.hashgraph:sdk")
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.grpc:grpc-okhttp")
    testImplementation(group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    testImplementation("javax.inject:javax.inject")
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation("org.springframework.boot:spring-boot-starter-log4j2")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.retry:spring-retry")

    // TODO: Figure out why these hedera-sdk-java transitive dependencies need to be explicitly defined to compile
    testImplementation("com.google.code.gson:gson")
    testImplementation("com.github.spotbugs:spotbugs-annotations:4.7.1")
}

// Disable the default test task and only run acceptance tests during the standalone "acceptance" task
tasks.named("test") {
    enabled = false
}

tasks.register<Test>("acceptance") {
    jvmArgs = listOf("-Xmx1024m", "-Xms1024m")
    maxParallelForks = Runtime.getRuntime().availableProcessors()
    useJUnitPlatform {}
}
