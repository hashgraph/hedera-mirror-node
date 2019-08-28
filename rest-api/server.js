/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
require("dotenv").config({
    path: './.env'
});

const express = require('express');
const bodyParser = require('body-parser');
const Pool = require('pg').Pool;
const app = express();
const cors = require('cors');
const log4js = require('log4js');
const logger = log4js.getLogger();

const config = require('./config.js');
const transactions = require('./transactions.js');
const balances = require('./balances.js');
const events = require('./events.js');
const accounts = require('./accounts.js');
const eventAnalytics = require('./eventAnalytics.js');
const utils = require('./utils.js');
const Cacher = require('./cacher.js');

var compression = require('compression');

const port = process.env.PORT;
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
            level: 'debug'
        }
    }
});
global.logger = log4js.getLogger();


// Postgres pool
const pool = new Pool({
    user: process.env.DB_USER,
    host: process.env.DB_HOST,
    database: process.env.DB_NAME,
    password: process.env.DB_PASS,
    port: 5432
})
global.pool = pool;


app.set('trust proxy', true)
app.set('port', port);
app.use(bodyParser.urlencoded({
    extended: false
}));
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

let caches = {};
for (const api of [
    { name: 'transactions', ttl: config.ttls.transactions },
    { name: 'balances', ttl: config.ttls.balances },
    { name: 'accounts', ttl: config.ttls.accounts },
    { name: 'events', ttl: config.ttls.events }
]) {
    caches[api.name] = new Cacher(api.ttl);
}

let apiPrefix = '/api/v1';

// routes 
app.get(apiPrefix + '/transactions', (req, res) => caches['transactions'].getResponse(req, res, transactions.getTransactions));
app.get(apiPrefix + '/transactions/:id', transactions.getOneTransaction);
app.get(apiPrefix + '/balances', (req, res) => caches['balances'].getResponse(req, res, balances.getBalances));
app.get(apiPrefix + '/events', (req, res) => caches['events'].getResponse(req, res, events.getEvents));
app.get(apiPrefix + '/events/:id', events.getOneEvent);
app.get(apiPrefix + '/events/analytics', eventAnalytics.getEventAnalytics);
app.get(apiPrefix + '/accounts', (req, res) => caches['accounts'].getResponse(req, res, accounts.getAccounts));
app.get(apiPrefix + '/accounts/:id', accounts.getOneAccount);

if (process.env.NODE_ENV !== 'test') {
    app.listen(port, () => {
        console.log(`Server running on port: ${port}`);
    });
}

module.exports = app;
