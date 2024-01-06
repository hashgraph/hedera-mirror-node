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

import com.github.gradle.node.npm.task.NpmTask
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

tasks.named("dockerBuild") {
    dependsOn(tasks.bootJar)
}

tasks.register<NpmTask>("run") {
    dependsOn(tasks.bootRun)
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
        extension.library = "resttemplate"
        extension.modelPackage = "${openApiPackage}.model"
        extension.typeMappings = mapOf("Timestamp" to "String")
    }
)