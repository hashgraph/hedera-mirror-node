/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import config from './config.js';
import fs from 'fs';
import {getPoolClass} from './utils.js';

const poolConfig = {
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
  connectionTimeoutMillis: config.db.pool.connectionTimeout,
  max: config.db.pool.maxConnections,
  statement_timeout: config.db.pool.statementTimeout,
};

if (config.db.tls.enabled) {
  poolConfig.ssl = {
    ca: fs.readFileSync(config.db.tls.ca).toString(),
    cert: fs.readFileSync(config.db.tls.cert).toString(),
    key: fs.readFileSync(config.db.tls.key).toString(),
    rejectUnauthorized: false,
  };
}

const Pool = getPoolClass();

const handlePoolError = (dbPool) => {
  dbPool.on('error', (error) => {
    logger.error(`error event emitted on pool for host ${dbPool.options.host}. ${error.stack}`);
  });
};

const initializePool = () => {
  global.pool = new Pool(poolConfig);
  handlePoolError(global.pool);

  if (config.db.primaryHost) {
    const primaryPoolConfig = {...poolConfig};
    primaryPoolConfig.host = config.db.primaryHost;
    global.primaryPool = new Pool(primaryPoolConfig);
    handlePoolError(global.primaryPool);
  } else {
    global.primaryPool = pool;
  }
};

export {initializePool};
