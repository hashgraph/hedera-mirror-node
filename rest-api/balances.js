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
var knex = require("knex")({
    client: "pg"
});

// ------------------------------ API: /balances ------------------------------

/**
 * Handler function for /balances API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getBalances = function(req, res) {
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

    logger.debug("getBalances query: " + query.query);

    // Execute query
    return pool.query(query.query).then(results => {
        return processDbQueryResponse(req, query, results);
    });
};

/**
 * Converts the query to SQL
 * @param {Request} req HTTP request object
 * @return {Object} Valid flag, SQL query, and the parser object
 */
const reqToSql = function(req) {
    const parser = new Parser(req, "balances");
    const parsedReq = parser.getParsedReq();

    if (!parsedReq.isValid) {
        return parsedReq;
    }

    const order = parsedReq.queryIntents.hasOwnProperty("order") ? parsedReq.queryIntents["order"][0].val : "desc";
    const limit = parsedReq.queryIntents.hasOwnProperty("limit")
        ? parsedReq.queryIntents["limit"][0].val
        : config.limits.RESPONSE_ROWS;

    let innerQuery = getBalancesInnerQuery(parsedReq.queryIntents);
    let sqlQuery = getBalancesOuterQuery(parsedReq.queryIntents, innerQuery, order, limit);

    return {
        isValid: true,
        query: sqlQuery.toString(),
        parser: parser
    };
};

/**
 * Balances queries are orgnaized as follows: First there's an inner query that finds the appopriate
 * consensus_timestamp of the balances snapshot based on the user's query filter if one is supplied.
 * If no filter is specified in the query, then select the latest consensus_timestamp available in
 * the account_balances table.
 * Then an outer query searches within that snapshot of balances with the rest of the query parameters
 * such as account.id, account.publickey, account.balance, etc.
 * This function returns the inner query.
 * Also see: getBalancesOuterQuery function
 * @param {Object} intents Intents from the rest api query
 * @return {String} innerQuery SQL query that filters balances based on consensus_timestamp
 */
const getBalancesInnerQuery = function(intents) {
    let innerQuery = knex({ ab: "account_balances" }).select("consensus_timestamp");

    for (const intent of intents["timestamp"] || []) {
        // if the request has a timestamp=xxxx or timestamp=eq:xxxxx, then modify that to be
        // timestamp <= xxxx, so we return the latest balances as of the user-supplied timestamp.
        if (intent.op === "eq") {
            intent.op = "lte";
        }
        innerQuery = innerQuery.where("ab.consensus_timestamp", utils.opsMap[intent.op], intent.val);
    }
    innerQuery = innerQuery.orderBy("consensus_timestamp", "desc").limit(1);
    return innerQuery;
};

/**
 * Balances queries are orgnaized as follows: First there's an inner query that finds the appopriate
 * consensus_timestamp of the balances snapshot based on the user's query filter if one is supplied.
 * If no filter is specified in the query, then select the latest consensus_timestamp available in
 * the account_balances table.
 * Then an outer query searches within that snapshot of balances with the rest of the query parameters
 * such as account.id, account.publickey, account.balance, etc.
 * This function returns the outer query.
 * Also see: getBalancesInnerQuery function
 * @param {Object} intents Intents (filters) from the query
 * @param {String} innerQuery SQL query that provides the conensus_timestamp that matches the query criteria
 * @param {String} order Sorting order
 * @param {Number} limit Limit
 * @return {String} SQL query
 */
const getBalancesOuterQuery = function(intents, innerQuery, order, limit) {
    let outerQuery = knex({ ab: "account_balances" }).select(
        "ab.consensus_timestamp",
        { realm_num: "ab.account_realm_num" },
        { entity_num: "ab.account_num" },
        "ab.balance"
    );

    if (intents.hasOwnProperty("account.publickey")) {
        // Only need to join t_entites if we're selecting on publickey.
        outerQuery = outerQuery.join(
            {
                e: "t_entities"
            },
            function() {
                this.on("e.entity_realm", "=", "ab.account_realm_num")
                    .andOn("e.entity_num", "=", "ab.account_num")
                    .andOn("e.entity_shard", "=", knex.raw("?", [process.env.SHARD_NUM]))
                    .andOn("e.fk_entity_type_id", "<", utils.ENTITY_TYPE_FILE);
            }
        );
    }

    outerQuery = outerQuery.where("consensus_timestamp", "=", innerQuery);

    for (const intent of intents["account.id"] || []) {
        outerQuery = outerQuery
            .where("ab.account_realm_num", "=", intent.val.realm)
            .where("ab.account_num", utils.opsMap[intent.op], intent.val.num);
    }

    for (const intent of intents["account.publickey"] || []) {
        outerQuery = outerQuery.where("e.ed25519_public_key_hex", utils.opsMap[intent.op], intent.val);
    }

    for (const intent of intents["account.balance"] || []) {
        outerQuery = outerQuery.where("ab.balance", utils.opsMap[intent.op], intent.val);
    }
    outerQuery = outerQuery
        .orderBy("consensus_timestamp", "desc")
        .orderBy("account_realm_num", order)
        .orderBy("account_num", order)
        .limit(limit);

    return outerQuery;
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
        timestamp: null,
        balances: [],
        links: {
            next: null
        }
    };

    // Go through all results, and collect them by seconds.
    for (let row of results.rows) {
        let ns = utils.nsToSecNs(row.consensus_timestamp);
        const account = `${process.env.SHARD_NUM}.${row.realm_num}.${row.entity_num}`;

        if (ret.timestamp === null) {
            ret.timestamp = ns;
        }
        ret.balances.push({
            account: account,
            balance: Number(row.balance)
        });
    }

    const anchorAccountId = ret.balances.length > 0 ? ret.balances[ret.balances.length - 1].account : "0.0.0";
    ret.links = {
        next: utils.getPaginationLink(
            req.path,
            query.parser,
            ret.balances.length !== query.parser.limit,
            "account.id",
            anchorAccountId,
            query.parser.order
        )
    };

    logger.debug(`getBalancess returning ${ret.balances.length} entries`);

    return {
        code: utils.httpStatusCodes.OK,
        contents: ret
    };
};

module.exports = {
    getBalances: getBalances,
    reqToSql: reqToSql,
    processDbQueryResponse: processDbQueryResponse
};
