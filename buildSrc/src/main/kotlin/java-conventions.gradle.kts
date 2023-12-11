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

import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id("common-conventions")
    id("io.freefair.lombok")
    id("io.spring.dependency-management")
    id("jacoco")
    id("java-library")
    id("org.gradle.test-retry")
}

configurations.all {
    exclude(group = "commons-logging", "commons-logging")
    exclude(group = "org.slf4j", module = "slf4j-nop")
}

repositories {
    maven {
        url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven/")
    }
    maven {
        url = uri("https://artifacts.consensys.net/public/maven/maven/")
    }
}

dependencyManagement {
    imports {
        val grpcVersion: String by rootProject.extra
        mavenBom("io.grpc:grpc-bom:${grpcVersion}")
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    annotationProcessor(platform(project(":")))
    implementation(platform(project(":")))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.compileJava {
    dependsOn("generateEffectiveLombokConfig")
    // Disable serial and this-escape warnings due to errors in generated code
    options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all", "-Xlint:-serial,-this-escape"))
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.compileTestJava {
    dependsOn("generateEffectiveLombokConfig")
    options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all", "-Xlint:-this-escape"))
    options.encoding = "UTF-8"
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.javadoc {
    options.encoding = "UTF-8"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    maxHeapSize = "4096m"
    minHeapSize = "1024m"
    if (System.getenv().containsKey("CI")) {
        retry {
            maxRetries = 3
        }
    }
    useJUnitPlatform {
        excludeTags("largedbperf", "performance")
    }
}

tasks.register<Test>("performanceTest") {
    maxHeapSize = "4096m"
    minHeapSize = "1024m"
    useJUnitPlatform {
        includeTags("performance")
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required = true
        xml.required = true
    }
}

rootProject.tasks.named("sonarqube") {
    dependsOn(tasks.jacocoTestReport)
}
