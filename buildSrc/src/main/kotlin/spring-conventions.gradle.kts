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

import org.openapitools.generator.gradle.plugin.extensions.OpenApiGeneratorGenerateExtension

plugins {
    id("com.gorylenko.gradle-git-properties")
    id("docker-conventions")
    id("java-conventions")
    id("org.springframework.boot")
}

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo()
}

// Temporary for backwards compatibility with tgz
tasks.bootJar {
    archiveFileName = "${projectDir.name}-v${project.version}.jar"
}

tasks.named("dockerBuild") {
    dependsOn(tasks.bootJar)
}

// Temporary until we move completely off VMs
tasks.register<Tar>("package") {
    val name = "${projectDir.name}-v${project.version}"
    dependsOn(tasks.bootJar)
    archiveFileName = "${name}.tgz"
    compression = Compression.GZIP
    into("/${name}") {
        from("${layout.buildDirectory.asFile.get()}/libs")
        exclude("*-plain.jar")
        include("*.jar")
    }
    into("/${name}/scripts") {
        from("scripts")
        include("**/*")
    }
}

// Export the function as an extra property
val configureOpenApi by extra(
    fun(extension: OpenApiGeneratorGenerateExtension) {
        val openApiPackage = "com.hedera.mirror.rest"
        extension.apiPackage = "${openApiPackage}.api"
        extension.configOptions =
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
            )
        extension.generateApiTests = false
        extension.generateModelTests = false
        extension.generatorName = "java"
        extension.inputSpec = rootDir
            .resolve("hedera-mirror-rest")
            .resolve("api")
            .resolve("v1")
            .resolve("openapi.yml")
            .absolutePath
        extension.invokerPackage = "${openApiPackage}.handler"
        extension.library = "webclient"
        extension.modelPackage = "${openApiPackage}.model"
        extension.typeMappings = mapOf("Timestamp" to "String")
    }
)