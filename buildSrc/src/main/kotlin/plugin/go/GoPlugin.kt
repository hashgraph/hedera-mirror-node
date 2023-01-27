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

package plugin.go

import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

// The Golang plugin that registers the extension and the setup task
class GolangPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val go = project.extensions.create<GoExtension>("go")
        go.arch = detectArchitecture()
        go.cacheDir = project.rootDir.resolve(".gradle")
        go.goRoot = go.cacheDir.resolve("go")
        go.goBin = go.goRoot.resolve("bin").resolve("go")
        go.os = detectOs()
        project.tasks.register<GoSetup>("setup")
    }

    fun detectArchitecture(): String {
        val env = System.getProperty("GOARCH", "")

        if (env.isNotBlank()) {
            return env
        } else if (Os.isArch("aarch64")) {
            return "arm64"
        }

        return "amd64"
    }

    fun detectOs(): String {
        val env = System.getProperty("GOOS", "")

        if (env.isNotBlank()) {
            return env
        } else if (Os.isFamily(Os.FAMILY_WINDOWS)) {
            return "windows"
        } else if (Os.isFamily(Os.FAMILY_MAC)) {
            return "darwin"
        }

        return "linux"
    }
}
