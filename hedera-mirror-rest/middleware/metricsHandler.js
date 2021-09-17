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

// ext libraries
const extend = require('extend');
const client = require('prom-client');
const swStats = require('swagger-stats');
const url = require('url');

// files
const config = require('../config');
const oasHandler = require('./openapiHandler');
const {ipMask} = require('../utils');

const onMetricsAuthenticate = async (req, username, password) => {
  return new Promise(function (resolve, reject) {
    const match = username === config.metrics.config.username && password === config.metrics.config.password;
    resolve(match);
  }).catch((err) => {
    logger.debug(`Auth error: ${err}`);
    throw err;
  });
};

const ipEndpointHistogram = new client.Counter({
  name: 'hedera_mirror_rest_request_count',
  help: 'a counter mapping ip addresses to the endpoints they hit',
  labelNames: ['endpoint', 'ip'],
});

const recordIpAndEndpoint = async (req, res, next) => {
  if (req.route !== undefined) {
    ipEndpointHistogram.labels(req.route.path, ipMask(req.ip)).inc();
  }
};

const metricsHandler = () => {
  const defaultMetricsConfig = {
    name: process.env.npm_package_name,
    onAuthenticate: onMetricsAuthenticate,
    swaggerSpec: oasHandler.getV1OpenApiObject(),
    version: process.env.npm_package_version,
  };

  // combine defaultMetricsConfig with file defined configs
  extend(true, defaultMetricsConfig, config.metrics.config);

  const swaggerPath = `${config.metrics.config.uriPath}`;
  const metricsPath = `${swaggerPath}/metrics/`;
  const swaggerStats = swStats.getMiddleware(defaultMetricsConfig);

  return function filter(req, res, next) {
    let {pathname} = url.parse(req.url, false);
    pathname += pathname.endsWith('/') ? '' : '/';

    // Ignore all the other swagger stat endpoints
    if (pathname.startsWith(swaggerPath) && pathname !== metricsPath) {
      return next();
    }

    return swaggerStats(req, res, next);
  };
};

module.exports = {
  metricsHandler,
  recordIpAndEndpoint,
};
