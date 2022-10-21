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

plugins {
    id("docker-conventions")
    id("java-conventions")
    id("org.springframework.boot")
}

springBoot {
    // Creates META-INF/build-info.properties for Spring Boot Actuator
    buildInfo()
}

// Copy jar to target/ while we're still maintaining both Gradle and Maven to avoid changing all the Dockerfiles
val copyJar = tasks.register<Copy>("copyJar") {
    dependsOn(tasks.getByName("bootJar"))
    from(layout.buildDirectory.dir("libs")) {
        exclude("*-plain.jar")
    }
    into(layout.projectDirectory.dir("target"))
}

tasks.named("dockerBuild") {
    dependsOn(copyJar)
}
