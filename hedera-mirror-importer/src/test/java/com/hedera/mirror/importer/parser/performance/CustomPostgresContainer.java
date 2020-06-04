package com.hedera.mirror.importer.parser.performance;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import lombok.extern.log4j.Log4j2;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.IndefiniteWaitOneShotStartupCheckStrategy;
import org.testcontainers.containers.startupcheck.IsRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.hedera.mirror.importer.db.DBProperties;

@Log4j2
public class CustomPostgresContainer {

    private static final String dbNameEnvVar = "DB_NAME";
    private static final String dbUserEnvVar = "DB_USER";
    private static final String dbPasswordEnvVar = "DB_PASS";
    private static final String dbPortEnvVar = "DB_PORT";
    private static final String dockerFileName = "Dockerfile";
    private static final String dumpFileArgName = "dumpfile";
    private static final String jsonKeyFileArgName = "jsonkeyfile";
    private static final String jsonKeyFileName = "bucket-download-key.json";
    private static final String jsonKeyFilePath = "data/bucket-download-key.json";
    private static final String postgresConfFileName = "postgresql.conf";
    private static final String postgresConfPath = "data/postgresql.conf";
    private static final String restoreScriptFileName = "restore.sh";
    private static final String restoreScriptFilePath = "data/restore.sh";
    private static final String restoreSeedScriptFilePath = "data/restore-seed.sh";

    public static ImageFromDockerfile createBaseDockerImage(String dockerFilePath) {
        return new ImageFromDockerfile()
                .withFileFromClasspath(dockerFileName, dockerFilePath);
    }

    public static ImageFromDockerfile createSeededDockerImage(String dockerFilePath, String pgDumpFile) {
        return createBaseDockerImage(dockerFilePath)
                .withFileFromClasspath(postgresConfFileName, postgresConfPath)
                .withFileFromClasspath(restoreScriptFileName, restoreSeedScriptFilePath)
                .withBuildArg(dumpFileArgName, pgDumpFile)
                .withBuildArg(jsonKeyFileArgName, jsonKeyFileName)
                .withFileFromClasspath(jsonKeyFileName, jsonKeyFilePath);
    }

    public static ImageFromDockerfile createDockerRestoreImage(String dockerFilePath, String pgDumpFile) {
        return createBaseDockerImage(dockerFilePath)
                .withFileFromClasspath(jsonKeyFileName, jsonKeyFilePath)
                .withBuildArg(dumpFileArgName, pgDumpFile)
                .withFileFromClasspath(restoreScriptFileName, restoreScriptFilePath)
                .withBuildArg(jsonKeyFileArgName, jsonKeyFileName);
    }

    public static GenericContainer createBaseContainer(String dockerFilePath, int exposedPort) {
        return new GenericContainer(createBaseDockerImage(dockerFilePath))
                .withExposedPorts(exposedPort)
                .waitingFor(Wait.forListeningPort());
    }

    public static GenericContainer createSeededContainer(String dockerFilePath, String pgDumpFile, int exposedPort,
                                                         DBProperties db) {
        return new GenericContainer(createSeededDockerImage(dockerFilePath, pgDumpFile))
                .withEnv(dbNameEnvVar, db.getName())
                .withEnv(dbUserEnvVar, db.getUsername())
                .withEnv(dbPasswordEnvVar, db.getPassword())
                .withExposedPorts(exposedPort)
                .withStartupCheckStrategy(
                        new IsRunningStartupCheckStrategy()
                );
    }

    public static GenericContainer createRestoreContainer(String dockerFilePath, String pgDumpFile, DBProperties db) {
        return new GenericContainer(createDockerRestoreImage(dockerFilePath, pgDumpFile))
                .withEnv(dbNameEnvVar, db.getName())
                .withEnv(dbUserEnvVar, db.getUsername())
                .withEnv(dbPasswordEnvVar, db.getPassword())
                .withEnv(dbPortEnvVar, Integer.toString(db.getPort()))
                .withNetworkMode("host")
                .withStartupCheckStrategy(
                        new IndefiniteWaitOneShotStartupCheckStrategy()
                );
    }
}
