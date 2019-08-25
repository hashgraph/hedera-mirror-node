'uses strict';
require("dotenv").config({
    path: './.env'
});

const express = require('express');
const bodyParser = require('body-parser');
const Pool = require('pg').Pool
const app = express();
const cors = require('cors')
const log4js = require('log4js');
const logger = log4js.getLogger();

const transactions = require('./transactions.js');
const balances = require('./balances.js');
const events = require('./events.js');
const accounts = require('./accounts.js');
const eventAnalytics = require('./eventAnalytics.js');
const cacher = require('./cacher.js');
var compression = require('compression');


// Logger
log4js.configure({
    appenders: {
        everything: {
            type: 'file',
            filename: '../logs/hedera_mirrornode_api_temp.log'
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

const port = process.env.PORT;
if (port === undefined) {
    logger.error('Server started with unknown port');
    console.log('Please specify the port');
    process.exit(1);
}


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



let apiPrefix = '/api/v1';

// routes 
app.get(apiPrefix + '/transactions', (req, res) => cacher.getResponse(req, res, transactions.getTransactions));
app.get(apiPrefix + '/transactions/:id', transactions.getOneTransaction);
app.get(apiPrefix + '/balances', (req, res) => cacher.getResponse(req, res, balances.getBalances));
app.get(apiPrefix + '/events', (req, res) => cacher.getResponse(req, res, events.getEvents));
app.get(apiPrefix + '/events/:id', events.getOneEvent);
app.get(apiPrefix + '/events/analytics', eventAnalytics.getEventAnalytics);
app.get(apiPrefix + '/accounts', (req, res) => cacher.getResponse(req, res, accounts.getAccounts));
app.get(apiPrefix + '/accounts/:id', accounts.getOneAccount);

if (process.env.NODE_ENV !== 'test') {
    app.listen(port, () => {
        console.log(`Server running on port: ${port}`);
    });
}

module.exports = app;
