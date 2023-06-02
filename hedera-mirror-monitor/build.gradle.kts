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
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
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

openApiGenerate {
    val openApiPackage = "com.hedera.mirror.rest"

    apiPackage.set("${openApiPackage}.api")
    configOptions.set(
        mapOf(
            "developerEmail" to "mirrornode@hedera.com",
            "developerName" to "Hedera Mirror Node Team",
            "developerOrganization" to "Hedera Hashgraph",
            "developerOrganizationUrl" to "https://github.com/hashgraph/hedera-mirror-node",
            "interfaceOnly" to "true",
            "licenseName" to "Apache License 2.0",
            "licenseUrl" to "https://www.apache.org/licenses/LICENSE-2.0.txt",
            "openApiNullable" to "false",
            "performBeanValidation" to "true",
            "useBeanValidation" to "true",
            "useJakartaEe" to "true",
        ))
    generateApiTests.set(false)
    generateModelTests.set(false)
    generatorName.set("java")
    inputSpec.set(
        rootDir
            .resolve("hedera-mirror-rest")
            .resolve("api")
            .resolve("v1")
            .resolve("openapi.yml")
            .absolutePath)
    invokerPackage.set("${openApiPackage}.handler")
    library.set("webclient")
    modelPackage.set("${openApiPackage}.model")
    typeMappings.set(mapOf("Timestamp" to "String"))
}

tasks.withType<JavaCompile> { dependsOn("openApiGenerate") }

java.sourceSets["main"].java { srcDir(openApiGenerate.outputDir) }
