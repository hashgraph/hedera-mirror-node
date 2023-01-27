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
    id("com.gradle.enterprise") version ("3.11.1")
}

rootProject.name = "hedera-mirror-node"
include(":hedera-mirror-common")
include(":hedera-mirror-graphql")
include(":hedera-mirror-grpc")
include(":hedera-mirror-importer")
include(":hedera-mirror-monitor")
include(":hedera-mirror-protobuf")
include(":hedera-mirror-rest")
include(":hedera-mirror-rest:check-state-proof")
include(":hedera-mirror-rest:monitoring")
include(":hedera-mirror-rosetta")
include(":hedera-mirror-test")
include(":hedera-mirror-web3")

// Shorten project name to remove verbose "hedera-mirror-" prefix
rootProject.children.forEach { project ->
    project.name = project.name.removePrefix("hedera-mirror-")
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        tag("CI")
    }
}
