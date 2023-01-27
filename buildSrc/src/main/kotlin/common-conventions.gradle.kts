import org.owasp.dependencycheck.gradle.extension.AnalyzerExtension

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
    */


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
    version.set("18.12.1")
    workDir.set(rootDir.resolve(".gradle").resolve("nodejs"))
}

spotless {
    isEnforceCheck = false
    if (!System.getenv().containsKey("CI")) {
        ratchetFrom("origin/main")
    }
    format("javascript", {
        indentWithSpaces(2)
        licenseHeader(licenseHeader, "(import|const|//)")
        prettier().config(
            mapOf(
                "bracketSpacing" to false,
                "printWidth" to 120,
                "singleQuote" to true,
            )
        )
        target("**/*.js")
        targetExclude("node_modules/**", ".node-flywaydb/**")
    })
    java {
        addStep(StripOldLicenseFormatterStep.create())
        googleJavaFormat().aosp().reflowLongStrings()
        licenseHeader(licenseHeader, "package")
        target("**/*.java")
        targetExclude("build/**")
        toggleOffOn()
    }
    kotlinGradle({
        ktfmt().dropboxStyle()
        licenseHeader(licenseHeader, "(description|import|plugins)")
    })
    format("miscellaneous", {
        endWithNewline()
        indentWithSpaces(2)
        prettier()
        target("**/*.json", "**/*.md", "**/*.yml", "**/*.yaml")
        trimTrailingWhitespace()
    })
    sql {
        endWithNewline()
        indentWithSpaces()
        target("**/*.sql")
        trimTrailingWhitespace()
    }
    format("xml", {
        endWithNewline()
        indentWithSpaces()
        target("**/*.xml")
        trimTrailingWhitespace()
    })
}

tasks.nodeSetup {
    doLast {
        val npmExecutable = node.workDir.asFile.get().walk().first({ it -> it.name == "npm" })
        spotless.format("javascript", { prettier().npmExecutable(npmExecutable) })
        spotless.format("miscellaneous", { prettier().npmExecutable(npmExecutable) })
    }
    onlyIf { !node.workDir.asFile.get().exists() }
}

tasks.spotlessApply {
    dependsOn(tasks.nodeSetup)
}

tasks.spotlessCheck {
    dependsOn(tasks.nodeSetup)
}
