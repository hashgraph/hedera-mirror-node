/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

const extend = require('extend');
const fs = require('fs');
const yaml = require('js-yaml');
const log4js = require('log4js');
const path = require('path');

const logger = log4js.getLogger('config');
let config = {};
let loaded = false;

function load(configPath) {
  if (!configPath) {
    return;
  }

  let configFile = path.join(configPath, 'application.yml');
  if (!fs.existsSync(configFile)) {
    return;
  }

  try {
    let doc = yaml.safeLoad(fs.readFileSync(configFile, 'utf8'));
    logger.info(`Loaded configuration source: ${configFile}`);
    extend(true, config, doc);
  } catch (err) {
    logger.error(`Skipping configuration ${configFile}: ${err}`);
  }
}

if (!loaded) {
  load(path.join(__dirname, 'config'));
  load(__dirname);
  load(process.env.CONFIG_PATH);
  loaded = true;
}

module.exports = config.hedera ? config.hedera.mirror : config;
