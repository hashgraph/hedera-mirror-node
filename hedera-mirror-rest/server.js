/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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
 */

// external libraries
import express from 'express';

import {createTerminus} from '@godaddy/terminus';
import {addAsync} from '@awaitjs/express';
import cors from 'cors';
import httpContext from 'express-http-context';
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
  openApiValidator,
  recordIpAndEndpoint,
  requestLogger,
  requestQueryParser,
  responseCacheCheckHandler,
  responseCacheUpdateHandler,
  responseHandler,
  serveSwaggerDocs,
} from './middleware';

// routes
import {AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes} from './routes';
import {handleRejection, handleUncaughtException} from './middleware/httpErrorHandler';
import {initializePool} from "./dbpool.js";

// use a dummy port for jest unit tests
const port = isTestEnv() ? 3000 : config.port;
if (port === undefined || Number.isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  process.exit(1);
}

// Postgres pool
initializePool();

// Express configuration. Prior to v0.5 all sets should be configured before use or they won't be picked up
const app = addAsync(express());
const {apiPrefix} = constants;
const applicationCacheEnabled = config.cache.response.enabled && config.redis.enabled;
const openApiValidatorEnabled = config.openapi.validation.enabled;

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.set('query parser', requestQueryParser);

serveSwaggerDocs(app);
if (openApiValidatorEnabled || isTestEnv()) {
  openApiValidator(app);
}

// middleware functions, Prior to v0.5 define after sets
app.use(
  express.urlencoded({
    extended: false,
  })
);
app.use(express.json());
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

// Check for cached response
if (applicationCacheEnabled) {
  logger.info('Response caching is enabled');
  app.useAsync(responseCacheCheckHandler);
}

// accounts routes
app.getAsync(`${apiPrefix}/accounts`, accounts.getAccounts);
app.getAsync(`${apiPrefix}/accounts/:${constants.filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}`, accounts.getOneAccount);
app.useAsync(`${apiPrefix}/${AccountRoutes.resource}`, AccountRoutes.router);

// balances routes
app.getAsync(`${apiPrefix}/balances`, balances.getBalances);

// contracts routes
app.useAsync(`${apiPrefix}/${ContractRoutes.resource}`, ContractRoutes.router);

// network routes
app.useAsync(`${apiPrefix}/${NetworkRoutes.resource}`, NetworkRoutes.router);

// block routes
app.useAsync(`${apiPrefix}/${BlockRoutes.resource}`, BlockRoutes.router);

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
app.getAsync(`${apiPrefix}/transactions/:transactionIdOrHash`, transactions.getTransactionsByIdOrHash);

// record ip metrics if enabled
if (config.metrics.ipMetrics) {
  app.useAsync(recordIpAndEndpoint);
}

// response data handling middleware
app.useAsync(responseHandler);

// Update Cache with response
if (applicationCacheEnabled) {
  app.useAsync(responseCacheUpdateHandler);
}

// response error handling middleware
app.useAsync(handleError);

process.on('unhandledRejection', handleRejection);
process.on('uncaughtException', handleUncaughtException);

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
    onShutdown: health.onShutdown,
  });
}

export default app;
