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
const bodyParser = require('body-parser');
let Pool;
if (process.env.NODE_ENV !== 'test') {
  Pool = require('pg').Pool;
} else {
  Pool = require('./__tests__/mockpool.js'); // Use a mocked up DB for jest unit tests
}
const app = express();
const cors = require('cors');
const log4js = require('log4js');
const logger = log4js.getLogger();

const config = require('./config.js');
const transactions = require('./transactions.js');
const balances = require('./balances.js');
const events = require('./events.js');
const accounts = require('./accounts.js');
const message = require('./message.js');
const eventAnalytics = require('./eventAnalytics.js');
const utils = require('./utils.js');
const Cacher = require('./cacher.js');

var compression = require('compression');

let port = config.api.port;
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
      type: 'file',
      filename: '../logs/hedera_mirrornode_api_' + port + '.log' // ensure port is a legit number above
    }
  },
  categories: {
    default: {
      appenders: ['everything'],
      level: config.api.log.level
    }
  }
});
global.logger = log4js.getLogger();

// Postgres pool
const pool = new Pool({
  user: config.db.apiUsername,
  host: config.db.host,
  database: config.db.name,
  password: config.db.apiPassword,
  port: config.db.port
});
global.pool = pool;

app.set('trust proxy', true);
app.set('port', port);
app.use(
        bodyParser.urlencoded({
          extended: false
        })
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

let caches = {};
for (const api of [
  {name: 'transactions', ttl: config.api.ttl.transactions},
  {name: 'balances', ttl: config.api.ttl.balances},
  {name: 'accounts', ttl: config.api.ttl.accounts},
  {name: 'events', ttl: config.api.ttl.events}
]) {
  caches[api.name] = new Cacher(api.ttl);
}

let apiPrefix = '/api/v1';

// routes
app.get(apiPrefix + '/transactions', (req, res) =>
        caches['transactions'].getResponse(req, res, transactions.getTransactions)
);
app.get(apiPrefix + '/transactions/:id', transactions.getOneTransaction);
app.get(apiPrefix + '/balances', (req, res) => caches['balances'].getResponse(req, res, balances.getBalances));
app.get(apiPrefix + '/accounts', (req, res) => caches['accounts'].getResponse(req, res, accounts.getAccounts));
app.get(apiPrefix + '/accounts/:id', accounts.getOneAccount);
app.get(apiPrefix + '/message/:consensusTimestamp', message.getMessageByConsensusTimestamp);

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`Server running on port: ${port}`);
  });
}

module.exports = app;
