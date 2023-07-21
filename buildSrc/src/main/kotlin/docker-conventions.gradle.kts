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

import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage

plugins {
    id("com.bmuschko.docker-remote-api")
}

val latest = "latest"

// Get the Docker images to tag by splitting dockerTag property and adding the project version
fun dockerImages(): Collection<String> {
    val dockerImageName =
        if (project.extra.has("dockerImageName")) project.extra.get("dockerImageName") else projectDir.name
    val dockerRegistry: String by project
    val dockerTag: String by project
    val dockerImage = "${dockerRegistry}/${dockerImageName}:"
    val customTags = dockerTag.split(',').map { dockerImage.plus(it) }
    val versionTag = dockerImage.plus(project.version)
    val tags = customTags.plus(versionTag).toMutableSet()

    // Don't tag pre-release versions as latest
    if (tags.contains(latest) && project.version.toString().contains('-')) {
        tags.remove(latest)
    }

    return tags.toList()
}

val dockerBuild = tasks.register<DockerBuildImage>("dockerBuild") {
    onlyIf {
        projectDir.resolve("Dockerfile").exists()
    }
    buildArgs.put("VERSION", project.version.toString())
    images.addAll(dockerImages())
    inputDir = file(projectDir)
    pull = true
}

tasks.register<DockerPushImage>("dockerPush") {
    onlyIf {
        projectDir.resolve("Dockerfile").exists()
    }
    dependsOn(dockerBuild)
    images.addAll(dockerImages())
}
