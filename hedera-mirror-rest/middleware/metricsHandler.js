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
'use strict';

const config = require('../config');
const extend = require('extend');
const swStats = require('swagger-stats');

const metricsHandler = () => {
  let defaultMetricsConfig = {
    name: process.env.npm_package_name,
    version: process.env.npm_package_version,
    onAuthenticate: onMetricsAuthenticate,
  };

  // combine defaultMetricsConfig with file defined configs
  extend(true, defaultMetricsConfig, config.metrics.config);

  return swStats.getMiddleware(defaultMetricsConfig);
};

const onMetricsAuthenticate = async (req, username, password) => {
  return new Promise(function (resolve, reject) {
    const match = username === config.metrics.config.username && password === config.metrics.config.password;
    resolve(match);
  }).catch((err) => {
    logger.debug(`Auth error: ${err}`);
    throw err;
  });
};

module.exports = {
  metricsHandler,
};
