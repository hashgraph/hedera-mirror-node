#!/usr/bin/env node
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

const fs = require('fs');
const yaml = require('js-yaml');

// Load config
process.env.CONFIG_NAME = 'perfTestConfig';
process.env.CONFIG_PATH = __dirname;
const config = require('../config');

const mustLoadYaml = (fileName) => {
  try {
    console.log(`Loading yaml file ${fileName}`);
    return yaml.load(fs.readFileSync(fileName, 'utf8'));
  } catch (err) {
    console.log(`Failed to load yaml file ${fileName}: ${err}`);
    process.exit(1);
  }
};

module.exports = {
  config: config,
  mustLoadYaml: mustLoadYaml,
};
