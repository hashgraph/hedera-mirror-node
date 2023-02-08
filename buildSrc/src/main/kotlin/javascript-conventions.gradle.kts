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
import org.gradle.internal.io.NullOutputStream

plugins {
    id("com.github.node-gradle.node")
    id("common-conventions")
    id("jacoco")
}

// Temporary until we move completely off VMs
tasks.register<NpmTask>("package") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("pack"))
    doLast {
        buildDir.mkdirs()
        fileTree(projectDir).matching { include("*.tgz") }.forEach {
            val newName = it.name.replace("hashgraph-mirror-rest-", "hedera-mirror-rest-v")
            it.renameTo(buildDir.resolve(newName))
        }
    }
}

tasks.register<NpmTask>("start") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("start"))
}

val test = tasks.register<NpmTask>("test") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("test"))
    execOverrides {
        // Gradle is logging all NPM output to stdout, so this change makes it behave like other tasks and not log
        if (gradle.startParameter.logLevel >= LogLevel.LIFECYCLE) {
            standardOutput = NullOutputStream.INSTANCE
        }
    }
}

tasks.build {
    dependsOn(test)
}

tasks.dependencyCheckAggregate {
    dependsOn(tasks.npmInstall)
}

tasks.dependencyCheckAnalyze {
    dependsOn(tasks.npmInstall)
}
