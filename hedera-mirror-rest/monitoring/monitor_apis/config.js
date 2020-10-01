/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

'use strict';

const fs = require('fs');
const _ = require('lodash');
const path = require('path');

const REQUIRED_FIELDS = [
  'servers',
  'interval',
  'shard',
  'account.intervalMultiplier',
  'account.limit',
  'balance.freshnessThreshold',
  'balance.intervalMultiplier',
  'balance.limit',
  'stateproof.intervalMultiplier',
  'transaction.freshnessThreshold',
  'transaction.intervalMultiplier',
  'transaction.limit',
  'topic.freshnessThreshold',
  'topic.intervalMultiplier',
  'topic.limit',
];
const SERVERLIST_FILE = path.join(__dirname, 'config', 'serverlist.json');
let config = {};
let loaded = false;

if (!loaded) {
  config = JSON.parse(fs.readFileSync(SERVERLIST_FILE).toString('utf-8'));
  for (const field of REQUIRED_FIELDS) {
    if (!_.has(config, field)) {
      throw new Error(`required field "${field}" not found in configuration file ${SERVERLIST_FILE}`);
    }
  }

  if (!Array.isArray(config.servers) || config.servers.length === 0) {
    throw new Error(`invalid servers "${JSON.stringify(config.servers)}" in configuration file ${SERVERLIST_FILE}`);
  }

  loaded = true;
}

module.exports = config;
