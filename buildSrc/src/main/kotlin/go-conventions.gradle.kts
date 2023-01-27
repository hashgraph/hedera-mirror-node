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

import org.owasp.dependencycheck.gradle.extension.AnalyzerExtension
import plugin.go.Go
import plugin.go.GoExtension
import plugin.go.GolangPlugin

plugins {
    id("common-conventions")
    id("jacoco")
}

apply<GolangPlugin>()

val goBuild = tasks.register<Go>("goBuild") {
    val binary = buildDir.resolve(project.projectDir.name)
    val ldFlags = "-w -s -X main.Version=${project.version}"
    environment["CGO_ENABLED"] = "true"
    args("build", "-ldflags", ldFlags, "-o", binary)
    dependsOn("test")
}

val goClean = tasks.register<Go>("goClean") {
    args("clean")
    buildDir.deleteRecursively()
    projectDir.resolve("coverage.txt").delete()
}

tasks.register<Go>("fix") {
    args("fix", "./...")
}

tasks.register<Go>("fmt") {
    args("fmt", "./...")
    dependsOn("generate")
}

tasks.register<Go>("generate") {
    args("generate", "./...")
    dependsOn("fix")
}

tasks.register<Exec>("run") {
    commandLine(buildDir.resolve(project.projectDir.name))
    dependsOn(goBuild)
}

tasks.register<Go>("test") {
    val go = project.extensions.getByName<GoExtension>("go")
    args("test", "-coverpkg=${go.pkg}", "-coverprofile=coverage.txt", "-covermode=atomic", "-race", "-v", go.pkg)
    dependsOn("fix")
}

tasks.build {
    dependsOn(goBuild)
}

tasks.clean {
    dependsOn(goClean)
}

// Ensure go binary is installed before running dependency check
listOf(tasks.dependencyCheckAggregate, tasks.dependencyCheckAnalyze).forEach {
    it.configure {
        dependsOn("setup")
        doFirst {
            dependencyCheck {
                analyzers(closureOf<AnalyzerExtension> {
                    val go = project.extensions.getByName<GoExtension>("go")
                    pathToGo = go.goBin.toString()
                })
            }
        }
    }
}
