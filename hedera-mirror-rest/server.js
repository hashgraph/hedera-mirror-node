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

// external libraries
const express = require('express');
const {createTerminus} = require('@godaddy/terminus');
const {addAsync} = require('@awaitjs/express');
const bodyParser = require('body-parser');
const cors = require('cors');
const httpContext = require('express-http-context');
const log4js = require('log4js');
const compression = require('compression');

// local files
const accounts = require('./accounts');
const balances = require('./balances');
const config = require('./config');
const constants = require('./constants');
const health = require('./health');
const network = require('./network');
const schedules = require('./schedules');
const stateproof = require('./stateproof');
const tokens = require('./tokens');
const topicmessage = require('./topicmessage');
const transactions = require('./transactions');
const {getPoolClass, isTestEnv, loadPgRange} = require('./utils');
const {handleError} = require('./middleware/httpErrorHandler');
const {metricsHandler, recordIpAndEndpoint} = require('./middleware/metricsHandler');
const {serveSwaggerDocs, openApiValidator} = require('./middleware/openapiHandler');
const {responseHandler} = require('./middleware/responseHandler');
const {requestLogger, requestQueryParser} = require('./middleware/requestHandler');
const fs = require('fs');

// routes
const {ContractRoutes} = require('./routes');

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

// use a dummy port for jest unit tests
const port = isTestEnv() ? 3000 : config.port;
if (port === undefined || Number.isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  process.exit(1);
}

// Postgres pool
const poolConfig = {
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
  connectionTimeoutMillis: config.db.pool.connectionTimeout,
  max: config.db.pool.maxConnections,
  statement_timeout: config.db.pool.statementTimeout,
};

if (config.db.tls.enabled) {
  poolConfig.ssl = {
    ca: fs.readFileSync(config.db.tls.ca).toString(),
    cert: fs.readFileSync(config.db.tls.cert).toString(),
    key: fs.readFileSync(config.db.tls.key).toString(),
    rejectUnauthorized: false,
  };
}

const Pool = getPoolClass(isTestEnv());
loadPgRange();
const pool = new Pool(poolConfig);
global.pool = pool;

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const app = addAsync(express());
const apiPrefix = '/api/v1';

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.set('query parser', requestQueryParser);

serveSwaggerDocs(app);
if (isTestEnv()) {
  openApiValidator(app);
}

// middleware functions, Prior to v0.5 define after sets
app.use(
  bodyParser.urlencoded({
    extended: false,
  })
);
app.use(bodyParser.json());
app.use(cors());

if (config.response.compression) {
  logger.info('Response compression is enabled');
  app.use(compression());
}

// logging middleware
app.use(httpContext.middleware);
app.useAsync(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  app.use(metricsHandler());
}

// accounts routes
app.getAsync(`${apiPrefix}/accounts`, accounts.getAccounts);
app.getAsync(`${apiPrefix}/accounts/:accountAliasOrAccountId`, accounts.getOneAccount);

// balances routes
app.getAsync(`${apiPrefix}/balances`, balances.getBalances);

// contracts routes
app.useAsync(`${apiPrefix}/${ContractRoutes.resource}`, ContractRoutes.router);

// network routes
app.getAsync(`${apiPrefix}/network/supply`, network.getSupply);

// schedules routes
app.getAsync(`${apiPrefix}/schedules`, schedules.getSchedules);
app.getAsync(`${apiPrefix}/schedules/:scheduleId`, schedules.getScheduleById);

// stateproof route
if (config.stateproof.enabled || isTestEnv()) {
  logger.info('stateproof REST API is enabled, install handler');
  app.getAsync(`${apiPrefix}/transactions/:transactionId/stateproof`, stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// tokens routes
app.getAsync(`${apiPrefix}/tokens`, tokens.getTokensRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId`, tokens.getTokenInfoRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/balances`, tokens.getTokenBalances);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts`, tokens.getNftTokensRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber`, tokens.getNftTokenInfoRequest);
app.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber/transactions`, tokens.getNftTransferHistoryRequest);

// topics routes
app.getAsync(`${apiPrefix}/topics/:topicId/messages`, topicmessage.getTopicMessages);
app.getAsync(`${apiPrefix}/topics/:topicId/messages/:sequenceNumber`, topicmessage.getMessageByTopicAndSequenceRequest);
app.getAsync(`${apiPrefix}/topics/messages/:consensusTimestamp`, topicmessage.getMessageByConsensusTimestamp);

// transactions routes
app.getAsync(`${apiPrefix}/transactions`, transactions.getTransactions);
app.getAsync(`${apiPrefix}/transactions/:transactionId`, transactions.getTransactionsById);

// record ip metrics if enabled
if (config.metrics.ipMetrics) {
  app.useAsync(recordIpAndEndpoint);
}

// response data handling middleware
app.useAsync(responseHandler);

// response error handling middleware
app.useAsync(handleError);

if (!isTestEnv()) {
  const server = app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });

  // Health check endpoints
  createTerminus(server, {
    healthChecks: {
      '/health/readiness': health.readinessCheck,
      '/health/liveness': health.livenessCheck,
    },
    beforeShutdown: health.beforeShutdown,
  });
}

module.exports = app;
