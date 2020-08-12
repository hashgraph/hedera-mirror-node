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

// external libraries
const express = require('express');
const {addAsync} = require('@awaitjs/express');
const bodyParser = require('body-parser');
const cors = require('cors');
const log4js = require('log4js');
const compression = require('compression');

// local files
const config = require('./config.js');
const transactions = require('./transactions.js');
const balances = require('./balances.js');
const accounts = require('./accounts.js');
const topicmessage = require('./topicmessage.js');
const stateproof = require('./stateproof');
const {handleError} = require('./middleware/httpErrorHandler');
const {responseHandler} = require('./middleware/responseHandler');
const {metricsHandler} = require('./middleware/metricsHandler');
const {requestLogger} = require('./middleware/requestLogger');

// Logger
const logger = log4js.getLogger();
log4js.configure({
  appenders: {
    everything: {
      type: 'stdout',
    },
  },
  categories: {
    default: {
      appenders: ['everything'],
      level: config.log.level,
    },
  },
});
global.logger = log4js.getLogger();

let {port} = config;
if (process.env.NODE_ENV === 'test') {
  port = 3000; // Use a dummy port for jest unit tests
}
if (port === undefined || Number.isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  console.log('Please specify the port');
  process.exit(1);
}

// Postgres pool
let Pool;
if (process.env.NODE_ENV !== 'test') {
  Pool = require('pg').Pool;
} else {
  Pool = require('./__tests__/mockpool.js'); // Use a mocked up DB for jest unit tests
}

const pool = new Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});
global.pool = pool;

// Express configuration
const app = addAsync(express());
app.set('trust proxy', true);
app.set('port', port);
app.use(
  bodyParser.urlencoded({
    extended: false,
  }),
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

// logging middleware
app.use(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  app.use(metricsHandler());
}

const apiPrefix = '/api/v1';

// accounts routes
app.getAsync(apiPrefix + '/accounts', accounts.getAccounts);
app.getAsync(apiPrefix + '/accounts/:id', accounts.getOneAccount);

// balances routes
app.getAsync(apiPrefix + '/balances', balances.getBalances);

// transactions routes
app.getAsync(apiPrefix + '/transactions', transactions.getTransactions);
app.getAsync(apiPrefix + '/transactions/:id', transactions.getOneTransaction);

// stateproof route
if (config.stateproof.enabled || process.env.NODE_ENV === 'test') {
  logger.info('stateproof REST API is enabled, install handler');
  app.getAsync(apiPrefix + '/transactions/:id/stateproof', stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// topics routes
app.getAsync(apiPrefix + '/topics/:id/messages', topicmessage.getTopicMessages);
app.getAsync(apiPrefix + '/topics/:id/messages/:sequencenumber', topicmessage.getMessageByTopicAndSequenceRequest);
app.getAsync(apiPrefix + '/topics?/messages?/:consensusTimestamp', topicmessage.getMessageByConsensusTimestamp);

// response data handling middleware
app.use(responseHandler);

// response error handling middleware
app.use(handleError);

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`Server running on port: ${port}`);
  });
}

module.exports = app;
