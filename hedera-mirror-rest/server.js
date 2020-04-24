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
'uses strict';

const express = require('express');
const {addAsync} = require('@awaitjs/express');
const bodyParser = require('body-parser');
let Pool;
if (process.env.NODE_ENV !== 'test') {
  Pool = require('pg').Pool;
} else {
  Pool = require('./__tests__/mockpool.js'); // Use a mocked up DB for jest unit tests
}
const app = addAsync(express());
const cors = require('cors');
const log4js = require('log4js');
const logger = log4js.getLogger();

const config = require('./config.js');
const transactions = require('./transactions.js');
const balances = require('./balances.js');
const accounts = require('./accounts.js');
const topicmessage = require('./topicmessage.js');
const {handleError} = require('./middleware/httpErrorHandler');
const {responseHandler} = require('./middleware/responseHandler');
const {metricsHandler} = require('./middleware/metricsHandler');

var compression = require('compression');

let port = config.port;
if (process.env.NODE_ENV == 'test') {
  port = 3000; // Use a dummy port for jest unit tests
}
if (port === undefined || isNaN(Number(port))) {
  logger.error('Server started with unknown port');
  console.log('Please specify the port');
  process.exit(1);
}

// Logger
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

// Postgres pool
const pool = new Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});
global.pool = pool;

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

// metrics middleware
if (config.metrics.enabled) {
  app.use(metricsHandler());
}

let apiPrefix = '/api/v1';

// routes
app.getAsync(apiPrefix + '/transactions', transactions.getTransactions);
app.getAsync(apiPrefix + '/transactions/:id', transactions.getOneTransaction);
app.getAsync(apiPrefix + '/balances', balances.getBalances);
app.getAsync(apiPrefix + '/accounts', accounts.getAccounts);
app.getAsync(apiPrefix + '/accounts/:id', accounts.getOneAccount);
app.getAsync(apiPrefix + '/topic/message/:consensusTimestamp', topicmessage.getMessageByConsensusTimestamp);

// support singular and plural resource naming for single topic message via id and sequence
app.getAsync(apiPrefix + '/topic/:id/message/:sequencenumber', topicmessage.getMessageByTopicAndSequenceRequest);
app.getAsync(apiPrefix + '/topics/:id/messages/:sequencenumber', topicmessage.getMessageByTopicAndSequenceRequest);

app.getAsync(apiPrefix + '/topics/:id', topicmessage.getTopicMessages);
app.getAsync(apiPrefix + '/topic/:id', topicmessage.getTopicMessages);

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
