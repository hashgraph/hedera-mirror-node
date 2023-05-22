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

import com.graphql_java_generator.plugin.conf.CustomScalarDefinition
import com.graphql_java_generator.plugin.conf.PluginMode

description = "Hedera Mirror Node GraphQL"

plugins {
    id("com.graphql-java-generator.graphql-gradle-plugin")
    id("spring-conventions")
}

dependencies {
    annotationProcessor("org.mapstruct:mapstruct-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    compileOnly("com.graphql-java-generator:graphql-java-client-runtime")
    implementation(project(":common"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("com.graphql-java:graphql-java-extended-scalars")
    implementation("com.graphql-java:graphql-java-extended-validation")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("javax.inject:javax.inject")
    implementation("org.mapstruct:mapstruct")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    runtimeOnly(
        group = "io.netty", name = "netty-resolver-dns-native-macos", classifier = "osx-aarch_64")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("com.playtika.testcontainers:embedded-postgresql")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.flywaydb:flyway-core")
    testImplementation("org.springframework.graphql:spring-graphql-test")
}

generatePojoConf {
    isAddRelayConnections = false
    isCopyRuntimeSources = true
    javaTypeForIDType = "java.lang.String"
    mode = PluginMode.server
    packageName = "com.hedera.mirror.graphql.viewmodel"
    schemaFilePattern = "**/*.graphqls"
    setCustomScalars(
        arrayOf(
            CustomScalarDefinition(
                "Duration",
                "java.time.Duration",
                "",
                "com.hedera.mirror.graphql.config.GraphQlDuration.INSTANCE",
                ""),
            CustomScalarDefinition("Long", "java.lang.Long", "", "graphql.scalars.GraphQLLong", ""),
            CustomScalarDefinition("Object", "java.lang.Object", "", "graphql.scalars.Object", ""),
            CustomScalarDefinition(
                "Timestamp",
                "java.time.Instant",
                "",
                "com.hedera.mirror.graphql.config.GraphQlTimestamp.INSTANCE",
                ""),
        ))
}

tasks.withType<JavaCompile> {
    dependsOn(tasks.generatePojo)
    if (name == "compileJava") {
        options.compilerArgs.addAll(
            listOf(
                "-Amapstruct.defaultComponentModel=jsr330",
                "-Amapstruct.defaultInjectionStrategy=constructor",
                "-Amapstruct.disableBuilders=true",
                "-Amapstruct.unmappedTargetPolicy=IGNORE", // Remove once all Account fields have
                // been mapped
            ))
    }
}

java.sourceSets["main"].java { srcDir(tasks.generatePojo) }
