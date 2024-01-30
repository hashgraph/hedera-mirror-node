/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

tasks.named("dockerBuild") { dependsOn(tasks.bootJar) }
tasks.register("run") { dependsOn(tasks.bootRun) }
