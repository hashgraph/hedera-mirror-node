/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

'use strict';

const fs = require('fs');
const log4js = require('log4js');
const path = require('path');
const yaml = require('js-yaml');

const logger = log4js.getLogger();
let config = {};
let loaded = false;

const loadYaml = () => {
  try {
    const configFile = path.join(__dirname, 'config', `application.yml`);
    logger.info(`Loaded configuration source: ${configFile}`);
    config = yaml.load(fs.readFileSync(configFile, 'utf-8'));
  } catch (err) {
    logger.info(err);
  }
};

if (!loaded) {
  loadYaml();
  loaded = true;
}

const getConfig = () => {
  return config.hedera && config.hedera.mirror ? config.hedera.mirror.entityUpdate : config;
};

module.exports = getConfig();
