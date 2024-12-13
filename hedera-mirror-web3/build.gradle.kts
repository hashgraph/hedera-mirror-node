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

import java.net.HttpURLConnection
import java.net.URI
import org.web3j.solidity.gradle.plugin.SolidityCompile
import org.web3j.solidity.gradle.plugin.SolidityResolve

description = "Hedera Mirror Node Web3"

plugins {
    id("openapi-conventions")
    id("org.web3j")
    id("org.web3j.solidity")
    id("spring-conventions")
}

val javaxInjectVersion = "1"

dependencies {
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies"))
    implementation(project(":common"))
    implementation("com.bucket4j:bucket4j-core")
    implementation("com.esaulpaugh:headlong")
    implementation("com.hedera.hashgraph:app") { exclude(group = "io.netty") }
    implementation("com.hedera.evm:hedera-evm")
    implementation("io.github.mweirauch:micrometer-jvm-extras")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("jakarta.inject:jakarta.inject-api")
    implementation("javax.inject:javax.inject")
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

web3j {
    generateBoth = true
    generatedPackageName = "com.hedera.mirror.web3.web3j.generated"
    useNativeJavaTypes = true
}

val historicalSolidityVersion = "0.8.7"
val latestSolidityVersion = "0.8.25"

// Define "testHistorical" source set needed for the test historical solidity contracts and web3j
sourceSets {
    val testHistorical by creating {
        java { setSrcDirs(listOf("src/testHistorical/java", "src/testHistorical/solidity")) }
        resources { setSrcDirs(listOf("src/testHistorical/resources")) }
        compileClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += sourceSets["test"].output + configurations["testRuntimeClasspath"]
        solidity { version = historicalSolidityVersion }
    }
    test { solidity { version = latestSolidityVersion } }
}

// Tasks to download OpenZeppelin contracts
val openZeppelinVersion = "4.9.3"
val openZeppelinFile = layout.buildDirectory.file("openzeppelin.zip").get().asFile
val openZeppelinDir =
    layout.projectDirectory.asFile
        .resolve("src")
        .resolve("testHistorical")
        .resolve("solidity")
        .resolve("openzeppelin")

val downloadOpenZeppelin =
    tasks.register("downloadOpenZeppelin") {
        description = "Download OpenZeppelin contracts"
        group = "historical"
        doLast {
            val buildDir = layout.buildDirectory.asFile.get()
            if (!buildDir.exists()) {
                mkdir(buildDir)
            }
            openZeppelinDir.mkdirs()
            val openZeppelinUrl =
                "https://github.com/OpenZeppelin/openzeppelin-contracts/archive/v${openZeppelinVersion}.zip"
            val connection = URI(openZeppelinUrl).toURL().openConnection() as HttpURLConnection
            connection.inputStream.use { input ->
                openZeppelinFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        onlyIf { !openZeppelinFile.exists() }
    }

val extractOpenZeppelin =
    tasks.register<Copy>("extractContracts") {
        description = "Extracts the OpenZeppelin dependencies into the configured output folder"
        group = "historical"
        dependsOn(downloadOpenZeppelin)
        from(zipTree(openZeppelinFile))
        into(openZeppelinDir)
        include("openzeppelin-contracts-${openZeppelinVersion}/contracts/**/*.sol")
        eachFile {
            path = path.replaceFirst("openzeppelin-contracts-${openZeppelinVersion}/contracts", "")
        }
    }

tasks.bootRun { jvmArgs = listOf("--enable-preview") }

tasks.compileJava { options.compilerArgs.add("--enable-preview") }

tasks.test { jvmArgs = listOf("--enable-preview") }

tasks.openApiGenerate { mustRunAfter(tasks.named("resolveSolidity")) }

tasks.processTestResources {
    dependsOn(tasks.named("generateTestContractWrappers"))
    dependsOn(tasks.named("generateTestHistoricalContractWrappers"))
    dependsOn(tasks.named("moveAndCleanTestHistoricalFiles"))
}

tasks.register("resolveSolidityHistorical", SolidityResolve::class) {
    group = "historical"
    description = "Resolves the historical solidity version $historicalSolidityVersion"
    sources = fileTree("src/testHistorical/solidity")
    allowPaths = setOf("src/testHistorical/solidity")

    val packageJsonFile = "./build/node_modules/@openzeppelin/contracts/package.json"
    packageJson = file(packageJsonFile)
    dependsOn("extractContracts")
}

afterEvaluate {
    tasks.named("compileTestHistoricalSolidity", SolidityCompile::class.java).configure {
        group = "historical"
        allowPaths = setOf("src/testHistorical/solidity/openzeppelin")
        ignoreMissing = true
        version = historicalSolidityVersion
        source = fileTree("src/testHistorical/solidity") { include("*.sol") }
        dependsOn("extractContracts")
        dependsOn("resolveSolidityHistorical")
    }
}

afterEvaluate {
    tasks.named("generateTestHistoricalContractWrappers") {
        dependsOn(tasks.named("generateTestContractWrappers"))
        dependsOn(extractOpenZeppelin)
        dependsOn(tasks.named("resolveSolidityHistorical"))
    }
}

val processTestHistoricalResources =
    tasks.named("processTestHistoricalResources") {
        group = "historical"

        dependsOn(tasks.named("generateTestContractWrappers"))
        dependsOn(tasks.named("generateTestHistoricalContractWrappers"))
        dependsOn(tasks.named("moveAndCleanTestHistoricalFiles"))
    }

tasks.register<Copy>("moveAndCleanTestHistoricalFiles") {
    description =
        "Move files from testHistorical to test and then clean up the testHistorical directory."
    group = "historical"

    // Define source and destination directories
    val srcDir =
        layout.buildDirectory
            .dir(
                "generated/sources/web3j/testHistorical/java/com/hedera/mirror/web3/web3j/generated"
            )
            .get()
            .asFile
    val destDir =
        layout.buildDirectory
            .dir("generated/sources/web3j/test/java/com/hedera/mirror/web3/web3j/generated")
            .get()
            .asFile

    // Copy only files that match the pattern "*Historical.java"
    from(srcDir) { include("**/*Historical.java") }
    into(destDir)

    doLast {
        val testHistoricalDir =
            layout.buildDirectory.dir("generated/sources/web3j/testHistorical").get().asFile
        testHistoricalDir.deleteRecursively()
    }

    dependsOn(tasks.named("generateTestContractWrappers"))
    dependsOn(tasks.named("generateTestHistoricalContractWrappers"))
}

afterEvaluate { tasks.named("resolveSolidity") { dependsOn("extractContracts") } }

tasks.compileTestJava {
    options.compilerArgs.add("--enable-preview")
    options.compilerArgs.removeIf { it == "-Werror" }
    dependsOn("moveAndCleanTestHistoricalFiles")
}

tasks.named("compileTestHistoricalJava") { group = "historical" }

tasks.assemble {
    dependsOn(tasks.processTestResources)
    dependsOn(processTestHistoricalResources)
}
