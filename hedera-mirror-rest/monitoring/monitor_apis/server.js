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

const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
const compression = require('compression');
const log4js = require('log4js');

// Logger
const logger = log4js.getLogger();
log4js.configure({
  appenders: {
    console: {
      layout: {
        pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %m',
        type: 'pattern',
      },
      type: 'stdout',
    },
  },
  categories: {
    default: {
      appenders: ['console'],
      level: 'info',
    },
  },
});
global.logger = log4js.getLogger();

const config = require('./config');
const common = require('./common');
const monitor = require('./monitor');

const app = express();

const port = process.env.PORT || config.port || 3000;
if (port === undefined || isNaN(Number(port))) {
  logger.error('Please specify a valid port');
  process.exit(1);
}

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.use(
  bodyParser.urlencoded({
    extended: false,
  })
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

const apiPrefix = '/api/v1';

common.initResults();

// routes
app.get(`${apiPrefix}/status`, (req, res) => {
  const status = common.getStatus();
  const passed = status.results.map((r) => r.results.numPassedTests).reduce((r, i) => r + i);
  const total = status.results.map((r) => r.results.testResults.length).reduce((r, i) => r + i);
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).send(status.results);
});

app.get(`${apiPrefix}/status/:name`, (req, res) => {
  const status = common.getStatusByName(req.params.name);
  const passed = status.results.numPassedTests;
  const total = status.results.testResults.length;
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).send(status);
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });
}

const runMonitorTests = () => {
  logger.info('Running tests');
  monitor.runEverything(config.servers);
};

runMonitorTests();
setInterval(() => {
  // Run all the tests periodically
  runMonitorTests();
}, config.interval * 1000);

module.exports = app;
