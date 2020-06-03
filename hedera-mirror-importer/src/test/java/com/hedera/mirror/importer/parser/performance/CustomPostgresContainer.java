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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.hedera.mirror.importer.db.DBProperties;

@Log4j2
public class CustomPostgresContainer {

    public static ImageFromDockerfile createBaseDockerImage(String dockerFilePath) {
        return new ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", dockerFilePath);
    }

    public static ImageFromDockerfile createSeededDockerImage(String dockerFilePath, String pgDumpFile) {
        return createBaseDockerImage(dockerFilePath)
                .withFileFromClasspath("postgresql.conf", "data/postgresql.conf")
                .withFileFromClasspath("restore-seed.sh", "data/restore-seed.sh")
                .withBuildArg("dumpfile", pgDumpFile)
                .withBuildArg("jsonkeyfile", "bucket-download-key.json")
                .withFileFromClasspath("bucket-download-key.json", "data/bucket-download-key.json");
    }

    public static ImageFromDockerfile createDockerRestoreImage(String dockerFilePath, String pgDumpFile) {
        return createBaseDockerImage(dockerFilePath)
                .withFileFromClasspath("bucket-download-key.json", "data/bucket-download-key.json")
                .withBuildArg("dumpfile", pgDumpFile)
                .withFileFromClasspath("restore.sh", "data/restore.sh")
                .withBuildArg("jsonkeyfile", "bucket-download-key.json");
    }

    public static GenericContainer createBaseContainer(String dockerFilePath, int exposedPort) {
        return new GenericContainer(createBaseDockerImage(dockerFilePath))
                .withExposedPorts(exposedPort)
                .waitingFor(Wait.forListeningPort());
    }

    public static GenericContainer createSeededContainer(String dockerFilePath, String pgDumpFile, int exposedPort) {
        return new GenericContainer(createSeededDockerImage(dockerFilePath, pgDumpFile))
                .withExposedPorts(exposedPort)
                .waitingFor(Wait.forListeningPort());
    }

    public static GenericContainer createRestoreContainer(String dockerFilePath, String pgDumpFile, DBProperties db) {
        return new GenericContainer(createDockerRestoreImage(dockerFilePath, pgDumpFile))
                .withEnv("DB_NAME", db.getName())
                .withEnv("DB_USER", db.getUsername())
                .withEnv("DB_PASS", db.getPassword())
                .withEnv("DB_PORT", Integer.toString(db.getPort()))
                .withNetworkMode("host")
                .withStartupCheckStrategy(
                        new IndefiniteWaitOneShotStartupCheckStrategy()
                );
    }
}
