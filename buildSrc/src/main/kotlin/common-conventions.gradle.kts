import com.github.gradle.node.npm.task.NpmSetupTask
import org.owasp.dependencycheck.gradle.extension.AnalyzerExtension
import java.nio.file.Paths

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

plugins {
    id("com.diffplug.spotless")
    id("com.github.node-gradle.node")
    id("org.owasp.dependencycheck")
}

repositories {
    mavenCentral()
}

val licenseHeader = """
   /*
    * Copyright (C) ${'$'}YEAR Hedera Hashgraph, LLC
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
    */${"\n\n"}
""".trimIndent()
val resources = rootDir.resolve("buildSrc").resolve("src").resolve("main").resolve("resources")

dependencyCheck {
    failBuildOnCVSS = 8f
    suppressionFile = resources.resolve("suppressions.xml").toString()
    analyzers(closureOf<AnalyzerExtension> {
        experimentalEnabled = true
        golangModEnabled = false // Too many vulnerabilities in transitive dependencies currently
    })
}

// Spotless uses Prettier and it requires Node.js
node {
    download.set(true)
    version.set("18.16.0")
    workDir.set(rootDir.resolve(".gradle").resolve("nodejs"))
}

spotless {
    val npmExec = when (System.getProperty("os.name").lowercase().contains("windows")) {
        true -> Paths.get("npm.cmd")
        else -> Paths.get("bin", "npm")
    }
    val npmSetup = tasks.named("npmSetup").get() as NpmSetupTask
    val npmExecutable = npmSetup.npmDir.get().asFile.toPath().resolve(npmExec)

    isEnforceCheck = false
    if (!System.getenv().containsKey("CI")) {
        ratchetFrom("origin/main")
    }
    format("javascript", {
        indentWithSpaces(2)
        licenseHeader(licenseHeader, "$").updateYearWithLatest(true)
        prettier()
            .npmExecutable(npmExecutable)
            .npmInstallCache(Paths.get("${rootProject.rootDir}", ".gradle", "spotless"))
            .config(
                mapOf(
                    "bracketSpacing" to false,
                    "printWidth" to 120,
                    "singleQuote" to true,
                )
            )
        target("**/*.js")
        targetExclude("**/node_modules/**", "**/__tests__/integration/*.test.js")
    })
    java {
        addStep(StripOldLicenseFormatterStep.create())
        palantirJavaFormat()
        licenseHeader(licenseHeader, "package").updateYearWithLatest(true)
        target("**/*.java")
        targetExclude("build/**")
        toggleOffOn()
    }
    kotlinGradle({
        ktfmt().dropboxStyle()
        licenseHeader(licenseHeader, "(description|import|plugins)").updateYearWithLatest(true)
    })
    format("miscellaneous", {
        endWithNewline()
        indentWithSpaces(2)
        prettier().npmExecutable(npmExecutable)
        target("**/*.json", "**/*.md", "**/*.yml", "**/*.yaml")
        targetExclude("**/node_modules/**", "**/package-lock.json")
        trimTrailingWhitespace()
    })
    sql {
        endWithNewline()
        indentWithSpaces()
        target("**/*.sql")
        targetExclude("**/db/migration/v1/*.sql") // Modifying executed SQL will change its checksum
        trimTrailingWhitespace()
    }
    format("xml", {
        endWithNewline()
        indentWithSpaces()
        target("**/*.xml")
        targetExclude("**/node_modules/**", "**/package-lock.json")
        trimTrailingWhitespace()
    })
}

tasks.nodeSetup {
    onlyIf { !this.nodeDir.get().asFile.exists() }
}

tasks.spotlessApply {
    dependsOn(tasks.nodeSetup)
}

tasks.spotlessCheck {
    dependsOn(tasks.nodeSetup)
}
