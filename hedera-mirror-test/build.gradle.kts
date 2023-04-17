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

description = "Hedera Mirror Node Test"

plugins {
    id("java-conventions")
}

dependencies {
    implementation(platform("io.cucumber:cucumber-bom"))
    implementation("io.cucumber:cucumber-java")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    testImplementation("com.esaulpaugh:headlong")
    testImplementation("com.google.guava:guava")
    testImplementation("com.hedera.hashgraph:sdk")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("io.cucumber:cucumber-spring")
    testImplementation("io.grpc:grpc-okhttp")
    testImplementation(group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    testImplementation("javax.inject:javax.inject")
    testImplementation("org.apache.commons:commons-lang3")
    testImplementation("org.awaitility:awaitility")
    testImplementation("org.junit.platform:junit-platform-suite")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.retry:spring-retry")
    testImplementation("org.apache.tuweni:tuweni-bytes")
    testImplementation("commons-codec:commons-codec")
}

// Disable the default test task and only run acceptance tests during the standalone "acceptance" task
tasks.named("test") {
    enabled = false
}

tasks.register<Test>("acceptance") {
    val maxParallelism = project.property("maxParallelism") as String
    jvmArgs = listOf("-Xmx1024m", "-Xms1024m")
    maxParallelForks =
        if (maxParallelism.isNotBlank()) maxParallelism.toInt()!! else Runtime.getRuntime().availableProcessors()
    useJUnitPlatform {}

    // Copy relevant system properties to the forked test process
    System.getProperties().filter { it.key.toString().matches(Regex("^(cucumber|hedera|spring)\\..*")) }.forEach {
        systemProperty(it.key.toString(), it.value)
    }
}
