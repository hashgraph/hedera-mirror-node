/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import extend from 'extend';
import fs from 'fs';
import _ from 'lodash';
import logger from './logger';
import path from 'path';
import {fileURLToPath} from 'url';

const REQUIRED_FIELDS = [
  'servers',
  'interval',
  'shard',
  'timeout',
  'account.intervalMultiplier',
  'balance.freshnessThreshold',
  'balance.intervalMultiplier',
  'block.freshnessThreshold',
  'block.intervalMultiplier',
  'network.intervalMultiplier',
  'stateproof.intervalMultiplier',
  'transaction.freshnessThreshold',
  'transaction.intervalMultiplier',
  'topic.freshnessThreshold',
  'topic.intervalMultiplier',
];

const load = (configFile) => {
  try {
    const data = JSON.parse(fs.readFileSync(configFile).toString('utf-8'));
    logger.info(`Loaded configuration source: ${configFile}`);
    return data;
  } catch (err) {
    logger.warn(`Skipping configuration source ${configFile}: ${err}`);
    return {};
  }
};

let config = {};
let loaded = false;

if (!loaded) {
  const moduleDirname = path.dirname(fileURLToPath(import.meta.url));
  config = load(path.join(moduleDirname, 'config', 'default.serverlist.json'));
  const customConfig = load(path.join(moduleDirname, 'config', 'serverlist.json'));
  extend(true, config, customConfig);

  for (const field of REQUIRED_FIELDS) {
    if (!_.has(config, field)) {
      throw new Error(`required field "${field}" not found in any configuration file`);
    }
  }

  if (!Array.isArray(config.servers) || config.servers.length === 0) {
    throw new Error(`Invalid servers "${JSON.stringify(config.servers)}" in any configuration file`);
  }

  logger.info(`Loaded configuration: ${JSON.stringify(config)}`);
  loaded = true;
}

export default config;
