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
import pg from 'pg';
import {PostgreSqlContainer} from '@testcontainers/postgresql';

const dbNamePrefix = 'test';
const v1DatabaseImage = 'postgres:14-alpine';
const v2DatabaseImage = 'gcr.io/mirrornode/citus:12.1.1';

const isV2Schema = () => process.env.MIRROR_NODE_SCHEMA === 'v2';

let dockerDb;

const createDbContainer = async (maxWorkers) => {
  const image = isV2Schema() ? v2DatabaseImage : v1DatabaseImage;
  dockerDb = await new PostgreSqlContainer(image).start();
  console.info(`Started PostgreSQL container ${image}`);

  process.env.INTEGRATION_DATABASE_URL = dockerDb.getConnectionUri();

  const poolConfig = {
    database: dockerDb.getDatabase(),
    host: dockerDb.getHost(),
    password: dockerDb.getPassword(),
    port: dockerDb.getPort(),
    sslmode: 'DISABLE',
    user: dockerDb.getUsername(),
  };
  const pool = new pg.Pool(poolConfig);

  for (let i = 1; i <= maxWorkers; i++) {
    // JEST_WORKER_ID starts from 1
    const dbName = getDatabaseNameForWorker(i);
    await pool.query(`create database ${dbName} with owner ${poolConfig.user}`);

    if (isV2Schema()) {
      // create extensions needed for v2
      const query = `
        create extension if not exists btree_gist;
        create extension if not exists citus;
        grant create on database ${dbName} to ${poolConfig.user};
        grant all on schema public to ${poolConfig.user};
        grant temporary on database ${dbName} to ${poolConfig.user};
        alter type timestamptz owner to ${poolConfig.user}`;

      const workerPool = new pg.Pool({...poolConfig, database: dbName});
      await workerPool.query(query);
      await workerPool.end();
    }
  }

  await pool.end();
  console.info(`Created separate databases for each of ${maxWorkers} jest workers`);

  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'migration-'));
  process.env.MIGRATION_TMP_DIR = tmpDir;
  console.info(`Created temp directory ${tmpDir} for migration status`);
};

const getDatabase = () => dockerDb;
const getDatabaseName = () => getDatabaseNameForWorker(process.env.JEST_WORKER_ID);

const getDatabaseNameForWorker = (workerId) => `${dbNamePrefix}_${workerId}`;

export default async function (globalConfig) {
  await createDbContainer(globalConfig.maxWorkers);
}

export {getDatabase, getDatabaseName};
