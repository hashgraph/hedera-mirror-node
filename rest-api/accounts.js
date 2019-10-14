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
"use strict";
const Parser = require("./Parser.js");
const utils = require("./utils.js");
const config = require("./config.js");
const transactions = require("./transactions.js");
var knex = require("knex")({
    client: "pg"
});

// ------------------------------ API: /accounts ------------------------------

/**
 * Handler function for /accounts API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for the api return value
 */
const getAccounts = function(req) {
    const query = reqToSql(req);

    if (!query.isValid) {
        return new Promise((resolve, reject) => {
            resolve({
                code: utils.httpStatusCodes.BAD_REQUEST,
                contents: {
                    _status: {
                        messages: query.badParams
                    }
                }
            });
        });
    }
    logger.debug("getAccounts query: " + query.query);

    // Execute query
    return pool.query(query.query).then(results => {
        return processDbQueryResponse(req, query, results);
    });
};

/**
 * Converts the accounts query to SQL
 * @param {Request} req HTTP request object
 * @return {Object} Valid flag, SQL query, and the parser object
 */
const reqToSql = function(req) {
    const parser = new Parser(req, "accounts");
    const parsedReq = parser.getParsedReq();

    if (!parsedReq.isValid) {
        return parsedReq;
    }

    const order = parsedReq.queryIntents.hasOwnProperty("order") ? parsedReq.queryIntents["order"][0].val : "desc";
    const limit = parsedReq.queryIntents.hasOwnProperty("limit")
        ? parsedReq.queryIntents["limit"][0].val
        : config.limits.RESPONSE_ROWS;

    let sqlQuery = getAccountsQuery(parsedReq.queryIntents, order, limit);

    return {
        isValid: true,
        query: sqlQuery.toString(),
        parser: parser
    };
};

/**
 * Constructs the SQL query
 * @param {Object} intents Intents (filters) from the query
 * @param {String} order Sorting order
 * @param {Number} limit Limit
 * @return {String} SQL query
 */
const getAccountsQuery = function(intents, order, limit) {
    let accountsQuery = knex({
        ab: "account_balances"
    })
        .select(
            { account_balance: "ab.balance" },
            { consensus_timestamp: " ab.consensus_timestamp" },
            { entity_shard: knex.raw("?", [process.env.SHARD_NUM]) },
            { entity_realm: knex.raw("coalesce(ab.account_realm_num, e.entity_realm)") },
            { entity_num: knex.raw("coalesce(ab.account_num, e.entity_num)") },
            "e.exp_time_ns",
            "e.auto_renew_period",
            "e.key",
            "e.deleted"
        )
        .fullOuterJoin({ e: "t_entities" }, function() {
            this.on(knex.raw("?", [process.env.SHARD_NUM]), "=", "e.entity_shard")
                .andOn(" ab.account_realm_num", "=", "e.entity_realm")
                .andOn("ab.account_num", "=", "e.entity_num")
                .andOn("e.fk_entity_type_id", "<", utils.ENTITY_TYPE_FILE);
        })
        .where("ab.consensus_timestamp", "=", knex("account_balances").max("consensus_timestamp"));

    for (const intent of intents["account.id"] || []) {
        accountsQuery = accountsQuery.where(function() {
            this.where(function() {
                this.where("ab.account_realm_num", "=", intent.val.realm).where(
                    "ab.account_num",
                    utils.opsMap[intent.op],
                    intent.val.num
                );
            }).orWhere(function() {
                this.where("e.entity_realm", "=", intent.val.realm).where(
                    "e.entity_num",
                    utils.opsMap[intent.op],
                    intent.val.num
                );
            });
        });
    }

    for (const intent of intents["account.balance"] || []) {
        accountsQuery = accountsQuery.where("ab.balance", utils.opsMap[intent.op], intent.val);
    }

    for (const intent of intents["account.publickey"] || []) {
        accountsQuery = accountsQuery.where("e.ed25519_public_key_hex", utils.opsMap[intent.op], intent.val);
    }

    accountsQuery = accountsQuery.orderBy(knex.raw("coalesce(ab.account_num, e.entity_num)"), order).limit(limit);

    return accountsQuery.toString();
};

/**
 * Process the database query response
 * @param {Request} req HTTP request object
 * @param {Object} query Parsed query
 * @param {Object} results Results from the database query
 * @return {Object} Processed response to be returned as the api response
 */
const processDbQueryResponse = function(req, query, results) {
    let ret = {
        accounts: [],
        links: {
            next: null
        }
    };

    for (let row of results.rows) {
        ret.accounts.push(processRow(row));
    }

    const anchorAccountId = ret.accounts.length > 0 ? ret.accounts[ret.accounts.length - 1].account : "0.0.0";
    ret.links = {
        next: utils.getPaginationLink(
            req.path,
            query.parser,
            ret.accounts.length !== query.parser.limit,
            "account.id",
            anchorAccountId,
            query.parser.order
        )
    };

    logger.debug(`getAccounts returning ${ret.accounts.length} entries`);

    return {
        code: utils.httpStatusCodes.OK,
        contents: ret
    };
};

/**
 * Processes one row of the results of the SQL query and format into API return format
 * @param {Object} row One row of the SQL query result
 * @return {Object} accRecord Processed account record
 */
const processRow = function(row) {
    let accRecord = {};
    accRecord.balance = {};
    accRecord.account = row.entity_shard + "." + row.entity_realm + "." + row.entity_num;
    accRecord.balance.timestamp = row.consensus_timestamp === null ? null : utils.nsToSecNs(row.consensus_timestamp);
    accRecord.balance.balance = row.account_balance === null ? null : Number(row.account_balance);
    accRecord.expiry_timestamp = row.exp_time_ns === null ? null : utils.nsToSecNs(row.exp_time_ns);
    accRecord.auto_renew_period = row.auto_renew_period === null ? null : Number(row.auto_renew_period);
    accRecord.key = row.key === null ? null : utils.encodeKey(row.key);
    accRecord.deleted = row.deleted;

    return accRecord;
};

// ------------------------------ API: /accounts/:id ------------------------------

/**
 * Handler function for /account/:id API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for the api return value
 */
const getOneAccount = function(req) {
    const query = reqToSqlOneAccount(req);
    if (!query.isValid) {
        return new Promise((resolve, reject) => {
            resolve({
                code: utils.httpStatusCodes.BAD_REQUEST,
                contents: {
                    _status: {
                        messages: query.badParams
                    }
                }
            });
        });
    }

    logger.debug("getOneAccount query: " + query.query);

    // Execute query
    const entityPromise = pool.query(query.query).then(results => {
        return processDbQueryResponse(req, query, results);
    });

    // Transactions for this account
    const transactionsPromise = transactions.getTransactions(req);

    // After the promises for all of the above queries have been resolved...
    return Promise.all([entityPromise, transactionsPromise]).then(function(values) {
        return processEntityAndTransactionsDbQueryResponse(values);
    });
};

/**
 * Converts the /accounts/:id query to SQL
 * @param {Request} req HTTP request object
 * @return {Object} Valid flag, SQL query, the parser object, and bad params, if any
 */
const reqToSqlOneAccount = function(req) {
    const parser = new Parser(req, "oneAccount");
    const parsedReq = parser.getParsedReq();

    if (!parsedReq.isValid) {
        return parsedReq;
    }

    parser.limit = 1;
    parser.order = "asc";
    let sqlQuery = getAccountsQuery(parsedReq.queryIntents, parser.order, parser.limit);

    return {
        isValid: true,
        query: sqlQuery.toString(),
        parser: parser,
        badParams: []
    };
};

/**
 * Process the database query response for /accounts/:id query
 * @param {Array} promiseValues An Array of promises for accounts and transactions queries
 * @return {Object} Processed response to be returned as the api response
 */
const processEntityAndTransactionsDbQueryResponse = function(promiseValues) {
    let ret = {
        code: null,
        contents: {}
    };

    const entityResults = promiseValues[0];
    const transactionsResults = promiseValues[1];

    if (entityResults.contents.accounts.length !== 1) {
        ret.code = utils.httpStatusCodes.NOT_FOUND;
    } else {
        if (entityResults.code === utils.httpStatusCodes.OK) {
            ret.code = transactionsResults.code;
        } else {
            ret.code = entityResults.code;
        }

        ret.contents = entityResults.contents.accounts[0];
        ret.contents.transactions = transactionsResults.contents.transactions;
        ret.contents.links = transactionsResults.contents.links;

        logger.debug("getOneAccount returning " + ret.contents.transactions.length + " transactions entries");
    }

    return ret;
};

module.exports = {
    getAccounts: getAccounts,
    getOneAccount: getOneAccount,
    reqToSql: reqToSql,
    reqToSqlOneAccount: reqToSqlOneAccount,
    processDbQueryResponse: processDbQueryResponse
};
