/*
 * Copyright (C) 2018-2025 Hedera Hashgraph, LLC
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

plugins { id("org.owasp.dependencycheck") }

repositories { mavenCentral() }

val resources = rootDir.resolve("buildSrc").resolve("src").resolve("main").resolve("resources")

dependencyCheck {
    if (System.getenv().containsKey("NVD_API_KEY")) {
        nvd.apiKey = System.getenv("NVD_API_KEY")
    }

    failBuildOnCVSS = 8f
    suppressionFile = resources.resolve("suppressions.xml").toString()
    analyzers {
        experimentalEnabled = true
        golangModEnabled = false // Too many vulnerabilities in transitive dependencies currently
    }
}
