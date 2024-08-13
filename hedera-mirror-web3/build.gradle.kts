/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import java.net.HttpURLConnection
import java.net.URI

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

description = "Hedera Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("spring-conventions")
    id("org.web3j")
    id("org.web3j.solidity")
}

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.esaulpaugh:headlong")
    implementation("com.hedera.evm:hedera-evm")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("javax.inject:javax.inject:1")
    implementation("net.java.dev.jna:jna")
    implementation("org.bouncycastle:bcprov-jdk18on")
    implementation("org.springframework:spring-context-support")
    implementation("org.springframework.boot:spring-boot-actuator-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-kubernetes-fabric8-config")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation(project(path = ":common", configuration = "testClasses"))
    testImplementation("io.vertx:vertx-core")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.mockito:mockito-inline")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}

tasks.bootRun { jvmArgs = listOf("--enable-preview") }

tasks.compileJava { options.compilerArgs.add("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }

// Task to download OpenZeppelin contracts
val openZeppelinVersion = "4.9.3"
val openZeppelinUrl =
    "https://github.com/OpenZeppelin/openzeppelin-contracts/archive/v${openZeppelinVersion}.zip"
val zipFile = layout.buildDirectory.file("openzeppelin.zip")
val outputDir = layout.buildDirectory.dir("../src/test/solidity_historical/openzeppelin")

tasks.register("downloadOpenZeppelinContracts") {
    doFirst {
        layout.buildDirectory.get().asFile.mkdirs()
        outputDir.get().asFile.mkdirs()
    }
    doLast {
        val uri = URI(openZeppelinUrl)
        val url = uri.toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.inputStream.use { input ->
            zipFile.get().asFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
}

tasks.register<Copy>("extractOpenZeppelinContracts") {
    dependsOn("downloadOpenZeppelinContracts")
    from(zipTree(zipFile.get().asFile))
    into(outputDir)
    include("openzeppelin-contracts-${openZeppelinVersion}/contracts/**/*.sol")
    eachFile {
        path = path.replaceFirst("openzeppelin-contracts-${openZeppelinVersion}/contracts", "")
    }
}

sourceSets { test { solidity { version = "0.8.24" } } }

web3j {
    generatedPackageName = "com.hedera.mirror.web3.web3j.generated"
    excludedContracts =
        listOf("DynamicEthCallsHistorical", "EthCallHistorical", "EvmCodesHistorical")
    useNativeJavaTypes = true
    generateBoth = true
}

tasks.openApiGenerate { mustRunAfter(tasks.named("resolveSolidity")) }

tasks.processTestResources {
    dependsOn(tasks.named("generateTestContractWrappers"))
    dependsOn(tasks.named("compileHistoricalSolidityContracts"))
}

// Task to compile Solidity contracts and generate Java files
tasks.register<Exec>("compileHistoricalSolidityContracts") {
    mustRunAfter(tasks.named("generateTestContractWrappers"))
    dependsOn(tasks.named("downloadOpenZeppelinContracts"))
    dependsOn(tasks.named("extractOpenZeppelinContracts"))
    dependsOn(tasks.named("compileTestSolidity"))
    // Define the path to your shell script
    val scriptPath = file("./scripts/compile_solidity.sh").absolutePath
    // Ensure the script is executable
    doFirst { file(scriptPath).setExecutable(true) }
    commandLine("bash", scriptPath)
}

tasks.named("assemble") { dependsOn(tasks.named("processTestResources")) }

tasks.compileTestJava {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.removeIf { it == "-Werror" }
    dependsOn(tasks.named("compileHistoricalSolidityContracts"))
}
