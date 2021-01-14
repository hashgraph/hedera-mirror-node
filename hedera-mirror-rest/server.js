/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
const {createTerminus} = require('@godaddy/terminus');
const {addAsync} = require('@awaitjs/express');
const bodyParser = require('body-parser');
const cors = require('cors');
const httpContext = require('express-http-context');
const log4js = require('log4js');
const compression = require('compression');
const _ = require('lodash');

// local files
const accounts = require('./accounts');
const balances = require('./balances');
const config = require('./config');
const constants = require('./constants');
const health = require('./health');
const stateproof = require('./stateproof');
const tokens = require('./tokens');
const topicmessage = require('./topicmessage');
const transactions = require('./transactions');
const {handleError} = require('./middleware/httpErrorHandler');
const {metricsHandler} = require('./middleware/metricsHandler');
const {serveSwaggerDocs} = require('./middleware/openapiHandler');
const {responseHandler} = require('./middleware/responseHandler');
const {requestLogger, requestQueryParser} = require('./middleware/requestHandler');

// Logger
const logger = log4js.getLogger();
log4js.configure({
  appenders: {
    console: {
      layout: {
        pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %x{requestId} %m',
        type: 'pattern',
        tokens: {
          requestId: (e) => httpContext.get(constants.requestIdLabel) || 'Startup',
        },
      },
      type: 'stdout',
    },
  },
  categories: {
    default: {
      appenders: ['console'],
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
  Pool = require('./__tests__/mockpool'); // Use a mocked up DB for jest unit tests
}

const pool = new Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
  connectionTimeoutMillis: config.db.pool.connectionTimeout,
  max: config.db.pool.maxConnections,
  statement_timeout: config.db.pool.statementTimeout,
});
global.pool = pool;

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const app = addAsync(express());
const apiPrefix = '/api/v1';

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.set('query parser', requestQueryParser);

serveSwaggerDocs(app);

// middleware functions, Prior to v0.5 define after sets
app.use(
  bodyParser.urlencoded({
    extended: false,
  })
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

// logging middleware
app.use(httpContext.middleware);
app.use(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  app.use(metricsHandler());
}

// accounts routes
app.getAsync(`${apiPrefix}/accounts`, accounts.getAccounts);
app.getAsync(`${apiPrefix}/accounts/:id`, accounts.getOneAccount);

// balances routes
app.getAsync(`${apiPrefix}/balances`, balances.getBalances);

// transactions routes
app.getAsync(`${apiPrefix}/transactions`, transactions.getTransactions);
app.getAsync(`${apiPrefix}/transactions/:id`, transactions.getOneTransaction);

// stateproof route
if (config.stateproof.enabled || process.env.NODE_ENV === 'test') {
  logger.info('stateproof REST API is enabled, install handler');
  app.getAsync(`${apiPrefix}/transactions/:id/stateproof`, stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// topics routes
app.getAsync(`${apiPrefix}/topics/:id/messages`, topicmessage.getTopicMessages);
app.getAsync(`${apiPrefix}/topics/:id/messages/:sequencenumber`, topicmessage.getMessageByTopicAndSequenceRequest);
app.getAsync(`${apiPrefix}/topics/messages/:consensusTimestamp`, topicmessage.getMessageByConsensusTimestamp);

// tokens routes
app.getAsync(`${apiPrefix}/tokens`, tokens.getTokensRequest);
app.getAsync(`${apiPrefix}/tokens/:id`, tokens.getTokenInfoRequest);
app.getAsync(`${apiPrefix}/tokens/:id/balances`, tokens.getTokenBalances);

// response data handling middleware
app.use(responseHandler);

// response error handling middleware
app.use(handleError);

if (process.env.NODE_ENV !== 'test') {
  const server = app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });

  // Health check endpoints
  createTerminus(server, {
    healthChecks: {
      '/health/readiness': health.readinessCheck,
      '/health/liveness': health.livenessCheck,
    },
    beforeShutdown: health.beforeDown,
  });
}

module.exports = app;
