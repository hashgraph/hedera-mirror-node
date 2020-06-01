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

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class CustomPostgresContainer {
    public static ImageFromDockerfile createDockerImage(String dockerFilePath, String pgDumpFile) {
        return new ImageFromDockerfile()
                .withFileFromClasspath("Dockerfile", dockerFilePath)
                .withFileFromClasspath("bucket-download-key.json", "data/bucket-download-key.json")
                .withFileFromClasspath("postgresql.conf", "data/postgresql.conf")
                .withBuildArg("dumpfile", pgDumpFile)
                .withBuildArg("jsonkeyfile", "bucket-download-key.json")
                .withFileFromClasspath("restore.sh", "data/restore.sh");
    }

    public static GenericContainer createContainer(String dockerFilePath, String pgDumpFile, int exposedPort) {
        return new GenericContainer(createDockerImage(dockerFilePath, pgDumpFile))
                .withExposedPorts(exposedPort)
                .waitingFor(Wait.forListeningPort());
    }
}
