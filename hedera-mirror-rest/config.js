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

const extend = require('extend');
const fs = require('fs');
const yaml = require('js-yaml');
const path = require('path');

let configName = 'application';
if (process.env.CONFIG_NAME) {
  configName = process.env.CONFIG_NAME;
}

let config = {};
let loaded = false;

function load(configPath) {
  if (!configPath) {
    return;
  }

  let configFile = path.join(configPath, configName + '.yml');
  if (fs.existsSync(configFile)) {
    loadYaml(configFile);
  }

  configFile = path.join(configPath, configName + '.yaml');
  if (fs.existsSync(configFile)) {
    loadYaml(configFile);
  }
}

function loadYaml(configFile) {
  try {
    let doc = yaml.safeLoad(fs.readFileSync(configFile, 'utf8'));
    console.log(`Loaded configuration source: ${configFile}`);
    extend(true, config, doc);
  } catch (err) {
    console.log(`Skipping configuration ${configFile}: ${err}`);
  }
}

function loadEnvironment() {
  for (const [key, value] of Object.entries(process.env)) {
    setConfigValue(key, value);
  }
}

/*
 * Sets a config property from an environment variable by converting HEDERA_MYFOO_BAR to an object path notation hedera.myFoo.bar
 * using a case insensitive search. If more than one property matches with a different case, it will choose the first.
 */
function setConfigValue(propertyPath, value) {
  let current = config;
  let properties = propertyPath.toLowerCase().split('_');

  // Ignore properties that don't start with HEDERA_
  if (properties.length <= 1 || properties[0] !== 'hedera') {
    return;
  }

  for (let i in properties) {
    let property = properties[i];

    for (let [k, v] of Object.entries(current)) {
      if (property === k.toLowerCase()) {
        if (i < properties.length - 1) {
          current = v;
          break;
        } else {
          current[k] = convertType(value);
          console.log(`Override config with environment variable ${propertyPath}=${value}`);
          return;
        }
      }
    }
  }
}

function convertType(value) {
  let parsedValue = value;

  if (value !== null && value !== '' && !isNaN(value)) {
    parsedValue = +value;
  } else if (value === 'true' || value === 'false') {
    parsedValue = value === 'true';
  }

  return parsedValue;
}

if (!loaded) {
  load(path.join(__dirname, 'config'));
  load(__dirname);
  load(process.env.CONFIG_PATH);
  loadEnvironment();
  loaded = true;
}

module.exports = config.hedera && config.hedera.mirror ? config.hedera.mirror.rest : config;
