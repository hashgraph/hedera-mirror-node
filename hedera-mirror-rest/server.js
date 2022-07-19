/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

// external libraries
import express from 'express';

import {createTerminus} from '@godaddy/terminus';
import {addAsync} from '@awaitjs/express';
import bodyParser from 'body-parser';
import cors from 'cors';
import httpContext from 'express-http-context';
import fs from 'fs';
import log4js from 'log4js';
import compression from 'compression';

// local files
import accounts from './accounts';
import balances from './balances';
import config from './config';
import * as constants from './constants';
import health from './health';
import schedules from './schedules';
import stateproof from './stateproof';
import tokens from './tokens';
import topicmessage from './topicmessage';
import transactions from './transactions';
import {getPoolClass, isTestEnv} from './utils';

import {
  handleError,
  metricsHandler,
  recordIpAndEndpoint,
  requestLogger,
  requestQueryParser,
  responseHandler,
  serveSwaggerDocs,
  openApiValidator,
} from './middleware';

// routes
import {AccountRoutes, ContractRoutes, NetworkRoutes, BlockRoutes} from './routes';

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

const Pool = await getPoolClass(isTestEnv());
const pool = new Pool(poolConfig);
pool.on('error', (error) => {
  logger.error(`error event emitted on pg pool. ${error.stack}`);
});
global.pool = pool;

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const server = addAsync(express());
const apiPrefix = '/api/v1';

server.disable('x-powered-by');
server.set('trust proxy', true);
server.set('port', port);
server.set('query parser', requestQueryParser);

serveSwaggerDocs(server);
if (isTestEnv()) {
  openApiValidator(server);
}

// middleware functions, Prior to v0.5 define after sets
server.use(
  bodyParser.urlencoded({
    extended: false,
  })
);
server.use(bodyParser.json());
server.use(cors());

if (config.response.compression) {
  logger.info('Response compression is enabled');
  server.use(compression());
}

// logging middleware
server.use(httpContext.middleware);
server.useAsync(requestLogger);

// metrics middleware
if (config.metrics.enabled) {
  server.use(metricsHandler());
}

// accounts routes
server.getAsync(`${apiPrefix}/accounts`, accounts.getAccounts);
server.getAsync(`${apiPrefix}/accounts/:${constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}`, accounts.getOneAccount);
server.useAsync(`${apiPrefix}/${AccountRoutes.resource}`, AccountRoutes.router);

// balances routes
server.getAsync(`${apiPrefix}/balances`, balances.getBalances);

// contracts routes
server.useAsync(`${apiPrefix}/${ContractRoutes.resource}`, ContractRoutes.router);

// network routes
server.useAsync(`${apiPrefix}/${NetworkRoutes.resource}`, NetworkRoutes.router);

// block routes
server.useAsync(`${apiPrefix}/${BlockRoutes.resource}`, BlockRoutes.router);

// schedules routes
server.getAsync(`${apiPrefix}/schedules`, schedules.getSchedules);
server.getAsync(`${apiPrefix}/schedules/:scheduleId`, schedules.getScheduleById);

// stateproof route
if (config.stateproof.enabled || isTestEnv()) {
  logger.info('stateproof REST API is enabled, install handler');
  server.getAsync(`${apiPrefix}/transactions/:transactionId/stateproof`, stateproof.getStateProofForTransaction);
} else {
  logger.info('stateproof REST API is disabled');
}

// tokens routes
server.getAsync(`${apiPrefix}/tokens`, tokens.getTokensRequest);
server.getAsync(`${apiPrefix}/tokens/:tokenId`, tokens.getTokenInfoRequest);
server.getAsync(`${apiPrefix}/tokens/:tokenId/balances`, tokens.getTokenBalances);
server.getAsync(`${apiPrefix}/tokens/:tokenId/nfts`, tokens.getNftTokensRequest);
server.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber`, tokens.getNftTokenInfoRequest);
server.getAsync(`${apiPrefix}/tokens/:tokenId/nfts/:serialNumber/transactions`, tokens.getNftTransferHistoryRequest);

// topics routes
server.getAsync(`${apiPrefix}/topics/:topicId/messages`, topicmessage.getTopicMessages);
server.getAsync(
  `${apiPrefix}/topics/:topicId/messages/:sequenceNumber`,
  topicmessage.getMessageByTopicAndSequenceRequest
);
server.getAsync(`${apiPrefix}/topics/messages/:consensusTimestamp`, topicmessage.getMessageByConsensusTimestamp);

// transactions routes
server.getAsync(`${apiPrefix}/transactions`, transactions.getTransactions);
server.getAsync(`${apiPrefix}/transactions/:transactionId`, transactions.getTransactionsById);

// record ip metrics if enabled
if (config.metrics.ipMetrics) {
  server.useAsync(recordIpAndEndpoint);
}

// response data handling middleware
server.useAsync(responseHandler);

// response error handling middleware
server.useAsync(handleError);

if (!isTestEnv()) {
  const server = server.listen(port, () => {
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

export default server;
