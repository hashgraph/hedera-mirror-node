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
    id("io.snyk.gradle.plugin.snykplugin")
}
abstract class SnykCodeTask : io.snyk.gradle.plugin.SnykTask() {
    @TaskAction
    fun doSnykTest() {
        log.debug("Snyk Code Test Task")
        authentication()
        val output: io.snyk.gradle.plugin.Runner.Result = runSnykCommand("code test")
        log.lifecycle(output.output)
        if (output.exitcode > 0) {
            throw GradleException("Snyk Code Test failed")
        }
    }
}


tasks.register<SnykCodeTask>("snyk-code"){
    dependsOn("snyk-check-binary")
    snyk {
        setSeverity("high")
        setArguments("--all-sub-projects --json-file-output=build/reports/snyk-test.json")
    }
}

tasks.`snyk-monitor`{
    doFirst{
        snyk{
            setArguments("--all-sub-projects")
        }
    }
}

tasks.`snyk-test`{
    snyk {
        setSeverity("high")
        setArguments("--all-sub-projects --json-file-output=build/reports/snyk-test.json")
    }
}
