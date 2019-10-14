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

// ------------------------------ API: /transactions ------------------------------

/**
 * Handler function for /transactions API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for PostgreSQL query
 */
const getTransactions = function(req) {
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

    logger.debug("getTransactions query: " + query.query);

    // Execute query
    return pool.query(query.query).then(results => {
        return processDbQueryResponse(req, query, results, false);
    });
};

/**
 * Converts the transactions query to SQL
 * @param {Request} req HTTP request object
 * @return {Object} Valid flag, SQL query, and the parser object
 */
const reqToSql = function(req) {
    const parser = new Parser(req, "transactions");
    const parsedReq = parser.getParsedReq();

    if (!parsedReq.isValid) {
        return parsedReq;
    }

    const order = parsedReq.queryIntents.hasOwnProperty("order") ? parsedReq.queryIntents["order"][0].val : "desc";
    const limit = parsedReq.queryIntents.hasOwnProperty("limit")
        ? parsedReq.queryIntents["limit"][0].val
        : config.limits.RESPONSE_ROWS;

    let innerQuery = getTransactionsInnerQuery(parsedReq.queryIntents, order, limit);
    let sqlQuery = getTransactionsOuterQuery(innerQuery, order);

    return {
        isValid: true,
        query: sqlQuery.toString(),
        parser: parser
    };
};

/**
 * Cryptotransfer transactions queries are orgnaized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function forms the inner query base based on all the query criteria specified in the REST URL
 * It selects a list of unique transactions (consensus_timestamps).
 * Also see: getTransactionsOuterQuery function
 * @param {Object} intents Intents from the rest api query
 * @param {String} order Sorting order
 * @param {Number} limit Limit
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getTransactionsInnerQuery = function(intents, order, limit) {
    let innerQuery = knex({ ctl: "t_cryptotransferlists" })
        .select("ctl.consensus_timestamp")
        .distinct()
        .join({ t: "t_transactions" }, { "t.consensus_ns": "ctl.consensus_timestamp" })
        .join({ tr: "t_transaction_results" }, { "t.fk_result_id": "tr.id" })
        .join({ eaccount: "t_entities" }, { "eaccount.id": "ctl.account_id" });

    if (intents.hasOwnProperty("account.id")) {
        let accountQuery = knex("t_entities").select("id");
        for (const intent of intents["account.id"]) {
            accountQuery = accountQuery
                .where("entity_shard", "=", intent.val.shard)
                .where("entity_realm", "=", intent.val.realm)
                .where("entity_num", utils.opsMap[intent.op], intent.val.num);
        }
        accountQuery = accountQuery.where("fk_entity_type_id", "<", utils.ENTITY_TYPE_FILE).limit(1000);
        innerQuery = innerQuery.whereIn("ctl.account_id", accountQuery);
    }

    for (const intent of intents["timestamp"] || []) {
        innerQuery = innerQuery.where("t.consensus_ns", utils.opsMap[intent.op], intent.val);
    }

    for (const intent of intents["result"] || []) {
        innerQuery = innerQuery.where("result", intent.val == "success" ? "=" : "!=", "SUCCESS");
    }

    for (const intent of intents["type"] || []) {
        if ("credit" === intent.val) {
            innerQuery = innerQuery.where("ctl.amount", ">", 0);
        } else if ("debit" === intent.val) {
            innerQuery = innerQuery.where("ctl.amount", "<", 0);
        }
    }

    innerQuery = innerQuery.orderBy("ctl.consensus_timestamp", order).limit(limit);

    return innerQuery;
};

/**
 * Cryptotransfer transactions queries are orgnaized as follows: First there's an inner query that selects the
 * required number of unique transactions (identified by consensus_timestamp). And then queries other tables to
 * extract all relevant information for those transactions.
 * This function returns the outer query base on the consensus_timestamps list returned by the inner query.
 * Also see: getTransactionsInnerQuery function
 * @param {String} innerQuery SQL query that provides a list of unique transactions that match the query criteria
 * @param {String} order Sorting order
 * @return {String} outerQuery Fully formed SQL query
 */
const getTransactionsOuterQuery = function(innerQuery, order) {
    let outerQuery = knex
        .table({ tlist: innerQuery })
        .select("etrans.entity_shard", "etrans.entity_realm", "etrans.entity_num")
        .select("t.consensus_ns", "valid_start_ns", "t.memo", "t.fk_trans_type_id", "t.fk_node_acc_id")
        .select(
            { node_shard: "enode.entity_shard" },
            { node_realm: "enode.entity_realm" },
            { node_num: "enode.entity_num" }
        )
        .select("ttr.result", "ttt.name", "account_id", "amount")
        .select(
            { account_shard: "eaccount.entity_shard" },
            { account_realm: "eaccount.entity_realm" },
            { account_num: "eaccount.entity_num" }
        )
        .select("t.charged_tx_fee")
        .join({ t: "t_transactions" }, { "tlist.consensus_timestamp": "t.consensus_ns" })
        .join({ ttr: "t_transaction_results" }, { " ttr.id": "t.fk_result_id" })
        .join({ enode: "t_entities" }, { "enode.id": "t.fk_node_acc_id" })
        .join({ etrans: "t_entities" }, { "etrans.id": "t.fk_payer_acc_id" })
        .join({ ttt: "t_transaction_types" }, { "ttt.id": "t.fk_trans_type_id" })
        .leftOuterJoin({ ctl: "t_cryptotransferlists" }, { "tlist.consensus_timestamp": "ctl.consensus_timestamp" })
        .join({ eaccount: "t_entities" }, { "eaccount.id": "ctl.account_id" })
        .orderBy("t.consensus_ns", order);

    return outerQuery;
};

/**
 * Process the database query response
 * @param {Request} req HTTP request object
 * @param {Object} query Parsed query
 * @param {Object} results Results from the database query
 * @param {boolean} isOneTransactionApi Boolean to indicate if the response is for /transactions/:id api
 *                  or the /transactions api
 * @return {Object} Processed response to be returned as the api response
 */
const processDbQueryResponse = function(req, query, results, isOneTransactionApi) {
    let ret = {
        transactions: [],
        links: {
            next: null
        }
    };

    const tl = createTransferLists(results.rows, ret);
    ret = tl.ret;

    if (isOneTransactionApi) {
        if (ret.transactions.length === 0) {
            return {
                code: utils.httpStatusCodes.NOT_FOUND,
                contents: ret
            };
        }
        logger.debug(`getOneTransaction returning ${ret.transactions.length} entries`);
    } else {
        let anchorSecNs = tl.anchorSecNs;

        ret.links = {
            next: utils.getPaginationLink(
                req.path,
                query.parser,
                ret.transactions.length !== query.parser.limit,
                "timestamp",
                anchorSecNs,
                query.parser.order
            )
        };
        logger.debug(`getTransactions returning ${ret.transactions.length} entries`);
    }

    return {
        code: utils.httpStatusCodes.OK,
        contents: ret
    };
};

/**
 * Create transferlists from the output of SQL queries. The SQL table has different
 * rows for each of the transfers in a single transaction. This function collates all
 * transfers into a single list.
 * @param {Array of objects} rows Array of rows returned as a result of an SQL query
 * @param {Array} arr REST API return array
 * @return {Array} arr Updated REST API return array
 */
const createTransferLists = function(rows, arr) {
    // If the transaction has a transferlist (i.e. list of individual trasnfers, it
    // will show up as separate rows. Combine those into a single transferlist for
    // a given consensus_ns (Note that there could be two records for the same
    // transaction-id where one would pass and others could fail as duplicates)
    let transactions = {};

    for (let row of rows) {
        if (!(row.consensus_ns in transactions)) {
            var validStartTimestamp = utils.nsToSecNs(row.valid_start_ns);
            transactions[row.consensus_ns] = {};
            transactions[row.consensus_ns]["consensus_timestamp"] = utils.nsToSecNs(row["consensus_ns"]);
            transactions[row.consensus_ns]["valid_start_timestamp"] = validStartTimestamp;
            transactions[row.consensus_ns]["charged_tx_fee"] = Number(row["charged_tx_fee"]);
            transactions[row.consensus_ns]["id"] = row["id"];
            transactions[row.consensus_ns]["memo_base64"] = utils.encodeBase64(row["memo"]);
            transactions[row.consensus_ns]["result"] = row["result"];
            transactions[row.consensus_ns]["result"] = row["result"];
            transactions[row.consensus_ns]["name"] = row["name"];
            transactions[row.consensus_ns]["node"] = row.node_shard + "." + row.node_realm + "." + row.node_num;

            // Construct a transaction id using format: shard.realm.num-sssssssssss-nnnnnnnnn
            transactions[row.consensus_ns]["transaction_id"] =
                `${row.entity_shard}.${row.entity_realm}.${row.entity_num}-` + validStartTimestamp.replace(".", "-");
            transactions[row.consensus_ns].transfers = [];
        }

        transactions[row.consensus_ns].transfers.push({
            account: row.account_shard + "." + row.account_realm + "." + row.account_num,
            amount: Number(row.amount)
        });
    }

    const anchorSecNs = rows.length > 0 ? utils.nsToSecNs(rows[rows.length - 1].consensus_ns) : 0;

    arr.transactions = Object.values(transactions);

    return {
        ret: arr,
        anchorSecNs: anchorSecNs
    };
};

// ------------------------------ API: /transactions/:id ------------------------------

/**
 * Handler function for /transactions/:id API.
 * @param {Request} req HTTP request object
 * @return {Promise} Promise for the api return value
 */
const getOneTransaction = function(req) {
    const query = reqToSqlOneTransaction(req);

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

    logger.debug("getOneTransaction query: " + query.query);

    // Execute query
    return pool.query(query.query).then(results => {
        return processDbQueryResponse(req, query, results, true);
    });
};

/**
 * Converts the /transactions/:id query to SQL
 * @param {Request} req HTTP request object
 * @return {Object} Valid flag, SQL query, the parser object, and bad params, if any
 */
const reqToSqlOneTransaction = function(req) {
    const parser = new Parser(req, "oneTransaction");
    const parsedReq = parser.getParsedReq();

    if (!parsedReq.isValid) {
        return parsedReq;
    }

    parser.limit = 1;
    parser.order = "asc";
    let innerQuery = getOneTransactionInnerQuery(parsedReq.queryIntents);

    if (innerQuery === null) {
        return {
            isValid: false,
            badParams: [{ message: `Could not create transaction query with provided parameters` }]
        };
    }

    let sqlQuery = getTransactionsOuterQuery(innerQuery, parser.order); // In case of duplicate transactions, only the first succeeds

    return {
        isValid: true,
        query: sqlQuery.toString(),
        parser: parser,
        badParams: []
    };
};

/**
 * Cryptotransfer transactions queries for one transaction are orgnaized as follows: First there's an
 * inner query that selects the the consensus_timestamp foor the transaction specified in the transaction-id.
 * And then an outer query is constructed that queries other tables to extract all relevant information for that transactions.
 * This function forms the inner query base based on the given transaction id
 * Also see: getTransactionsOuterQuery function
 * @param {Object} intents Intents from the rest api query
 * @return {String} innerQuery SQL query that filters transactions based on various types of queries
 */
const getOneTransactionInnerQuery = function(intents) {
    if (!intents.hasOwnProperty("account.id") || !intents.hasOwnProperty("validstart_ns")) {
        return null;
    }

    let innerQuery = knex({ t: "t_transactions" })
        .select({ consensus_timestamp: "t.consensus_ns" })
        .distinct()
        .join({ etrans: "t_entities" }, { "etrans.id": "t.fk_payer_acc_id" })
        .where("etrans.entity_shard", "=", intents["account.id"][0].val.shard)
        .where("etrans.entity_realm", "=", intents["account.id"][0].val.realm)
        .where("etrans.entity_num", "=", intents["account.id"][0].val.num)
        .where("t.valid_start_ns", "=", intents["validstart_ns"][0].val);

    return innerQuery;
};

module.exports = {
    getTransactions: getTransactions,
    getOneTransaction: getOneTransaction,
    reqToSql: reqToSql,
    reqToSqlOneTransaction: reqToSqlOneTransaction,
    getTransactionsInnerQuery: getTransactionsInnerQuery,
    getTransactionsOuterQuery: getTransactionsOuterQuery,
    processDbQueryResponse: processDbQueryResponse
};
