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

import com.github.gradle.node.npm.task.NpmTask
import org.gradle.internal.io.NullOutputStream

plugins {
    id("com.github.node-gradle.node")
    id("common-conventions")
    id("jacoco")
}

tasks.register<NpmTask>("run") {
    dependsOn(tasks.npmInstall)
    args = listOf("start")
}

val test =
    tasks.register<NpmTask>("test") {
        dependsOn(tasks.npmInstall)
        args = listOf("test")
        execOverrides {
            // Gradle is logging all NPM output to stdout, so this change makes it behave like other
            // tasks and not log
            if (gradle.startParameter.logLevel >= LogLevel.LIFECYCLE) {
                standardOutput = NullOutputStream.INSTANCE
            }
        }
    }

tasks.register("build") { dependsOn(test) }

tasks.dependencyCheckAggregate { dependsOn(tasks.npmInstall) }

tasks.dependencyCheckAnalyze { dependsOn(tasks.npmInstall) }
