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

const extend = require('extend');
const fs = require('fs');
const yaml = require('js-yaml');
const path = require('path');
const {InvalidConfigError} = require('./errors/invalidConfigError');
const {cloudProviders, networks, defaultBucketNames} = require('./constants');

let configName = 'application';
if (process.env.CONFIG_NAME) {
  configName = process.env.CONFIG_NAME;
}

const config = {};
let loaded = false;

function load(configPath) {
  if (!configPath) {
    return;
  }

  let configFile = path.join(configPath, `${configName}.yml`);
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
    const doc = yaml.safeLoad(fs.readFileSync(configFile, 'utf8'));
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
 * Sets a config property from an environment variable by converting HEDERA_MIRROR_REST_FOO_BAR to an object path
 * notation hedera.mirror.rest.foo.bar using a case insensitive search. If more than one property matches with a
 * different case, it will choose the first.
 */
function setConfigValue(propertyPath, value) {
  let current = config;
  let properties = propertyPath.toLowerCase().split('_');

  // Ignore properties that don't start with HEDERA_MIRROR_REST
  if (properties.length < 4 || properties[0] !== 'hedera' || properties[1] !== 'mirror' || properties[2] !== 'rest') {
    return;
  }

  for (let i in properties) {
    let property = properties[i];
    let found = false;

    for (let [k, v] of Object.entries(current)) {
      if (property === k.toLowerCase()) {
        if (i < properties.length - 1) {
          current = v;
          found = true;
          break;
        } else {
          current[k] = convertType(value);
          console.log(`Override config with environment variable ${propertyPath}=${value}`);
          return;
        }
      }
    }

    if (!found) {
      return;
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

function getConfig() {
  return config.hedera && config.hedera.mirror ? config.hedera.mirror.rest : config;
}

function parseStateProofStreamsConfig() {
  const {stateproof} = getConfig();
  if (!stateproof || !stateproof.enabled) {
    return;
  }

  const {streams: streamsConfig} = stateproof;
  if (!Object.values(networks).includes(streamsConfig.network)) {
    throw new InvalidConfigError(`unknown network ${streamsConfig.network}`);
  }

  if (!streamsConfig.bucketName) {
    streamsConfig.bucketName = defaultBucketNames[streamsConfig.network];
  }

  if (!streamsConfig.bucketName) {
    // the default for network 'OTHER' is null, throw err if it's not configured
    throw new InvalidConfigError('stateproof.streams.bucketName must be set');
  }

  if (!Object.values(cloudProviders).includes(streamsConfig.cloudProvider)) {
    throw new InvalidConfigError(`unsupported object storage service provider ${streamsConfig.cloudProvider}`);
  }
}

if (!loaded) {
  load(path.join(__dirname, 'config'));
  load(__dirname);
  load(process.env.CONFIG_PATH);
  loadEnvironment();
  parseStateProofStreamsConfig();
  loaded = true;
}

module.exports = getConfig();
