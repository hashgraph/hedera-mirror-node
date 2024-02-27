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

import fs from 'fs';
import os from 'os';
import path from 'path';
import {PostgreSqlContainer} from '@testcontainers/postgresql';
import {Wait} from 'testcontainers';

const dbName = 'mirror_node';
const dockerNamePrefix = 'INTEGRATION_DATABASE_URL';
const v1DatabaseImage = 'postgres:14-alpine';
const v2DatabaseImage = 'gcr.io/mirrornode/citus:12.1.1';

const isV2Schema = () => process.env.MIRROR_NODE_SCHEMA === 'v2';

let dockerDbs = [];

const createDbContainers = async (maxWorkers) => {
  const image = isV2Schema() ? v2DatabaseImage : v1DatabaseImage;
  const initSqlPath = path.join('..', 'hedera-mirror-common', 'src', 'test', 'resources', 'init.sql');
  const initSqlCopy = {
    source: initSqlPath,
    target: '/docker-entrypoint-initdb.d/init.sql',
  };

  console.info('Creating PostgreSQL containers for integration tests');
  dockerDbs = await Promise.all(
    Array(maxWorkers)
      .fill()
      .map(() =>
        new PostgreSqlContainer(image)
          .withCopyFilesToContainer([initSqlCopy])
          .withDatabase(dbName)
          .withPassword('mirror_node_pass')
          .withUsername('mirror_node')
          .withWaitStrategy(Wait.forLogMessage('database system is ready to accept connections', 2))
          .start()
      )
  );

  dockerDbs.forEach((dockerDb, index) => setJestEnvironment(dockerDb, index + 1));
  console.info(`Started ${maxWorkers} PostgreSQL containers with image ${image}, one for each Jest worker`);

  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'migration-'));
  process.env.MIGRATION_TMP_DIR = tmpDir;
  console.info(`Created temp directory ${tmpDir} for migration status`);
};

const getDatabases = () => dockerDbs;
const getDatabaseName = () => dbName;
const getReadOnlyUser = () => 'mirror_rest';
const getReadOnlyPassword = () => 'mirror_rest_pass';

const setJestEnvironment = (dockerDb, workerId) => {
  const ownerUri = `${dockerNamePrefix}_${workerId}`;
  process.env[ownerUri] = dockerDb.getConnectionUri();
};

const getOwnerConnectionUri = (workerId) => process.env[`${dockerNamePrefix}_${workerId}`];

export default async function (globalConfig) {
  await createDbContainers(globalConfig.maxWorkers);
}

export {getDatabases, getDatabaseName, getOwnerConnectionUri, getReadOnlyUser, getReadOnlyPassword};
