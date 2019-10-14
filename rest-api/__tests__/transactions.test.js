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

const transactions = require("../transactions.js");
const config = require("../config.js");
const utils = require("../utils.js");

function normalizeSql(str) {
    return str
        .replace(/\s+/g, " ")
        .replace(/,\s*/g, ",")
        .replace(/\s*,/g, ",")
        .replace(/\s+$/, "");
}

const queryPrefix =
    `select "etrans"."entity_shard", "etrans"."entity_realm", "etrans"."entity_num", "t"."consensus_ns", "valid_start_ns", ` +
    `"t"."memo", "t"."fk_trans_type_id", "t"."fk_node_acc_id", "enode"."entity_shard" as "node_shard", ` +
    `"enode"."entity_realm" as "node_realm", "enode"."entity_num" as "node_num", "ttr"."result", "ttt"."name", ` +
    `"account_id", "amount", "eaccount"."entity_shard" as "account_shard", "eaccount"."entity_realm" as "account_realm", ` +
    `"eaccount"."entity_num" as "account_num", "t"."charged_tx_fee" from ` +
    `(select distinct "ctl"."consensus_timestamp" from "t_cryptotransferlists" as "ctl" ` +
    `inner join "t_transactions" as "t" on "t"."consensus_ns" = "ctl"."consensus_timestamp" ` +
    `inner join "t_transaction_results" as "tr" on "t"."fk_result_id" = "tr"."id" ` +
    `inner join "t_entities" as "eaccount" on "eaccount"."id" = "ctl"."account_id" `;

const querySuffix =
    ` inner join "t_transactions" as "t" on "tlist"."consensus_timestamp" = "t"."consensus_ns" ` +
    `inner join "t_transaction_results" as "ttr" on "ttr"."id" = "t"."fk_result_id" ` +
    `inner join "t_entities" as "enode" on "enode"."id" = "t"."fk_node_acc_id" ` +
    `inner join "t_entities" as "etrans" on "etrans"."id" = "t"."fk_payer_acc_id" ` +
    `inner join "t_transaction_types" as "ttt" on "ttt"."id" = "t"."fk_trans_type_id" ` +
    `left outer join "t_cryptotransferlists" as "ctl" on "tlist"."consensus_timestamp" = "ctl"."consensus_timestamp" ` +
    `inner join "t_entities" as "eaccount" on "eaccount"."id" = "ctl"."account_id" order by "t"."consensus_ns" desc`;

const moduleVars = {
    timeNow: parseInt(new Date().getTime() / 1000),
    timeOneHourAgo: parseInt(new Date().getTime() / 1000) - 60 * 60
};

// Start of tests

// SQL generation (reqToSql) tests
describe("Transaction tests - SQL generation", () => {
    let txParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc",
        result: "SUCCESS"
    };

    test("Transactions test: SQL generation - timestamp", async () => {
        let params = txParams;
        const req = { query: { timestamp: [`gte:${params.timestamp.low}`, `lte:${params.timestamp.high}`] } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeTruthy();
        const sqlCheckStr =
            `where "t"."consensus_ns" >= '${utils.secNsToNs(params.timestamp.low)}' and ` +
            `"t"."consensus_ns" <= '${utils.secNsToNs(params.timestamp.high)}' ` +
            `order by "ctl"."consensus_timestamp" desc limit 1000) as "tlist" `;
        const expected = normalizeSql(queryPrefix + sqlCheckStr + querySuffix);
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad timestamp", async () => {
        const req = { query: { timestamp: [`gte:12345678901`, `lte:1234567890.0.1`] } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.badParams).toEqual([
            { message: "Invalid parameter: timestamp" },
            { message: "Invalid parameter: timestamp" }
        ]);
        expect(sql.query).toBeUndefined();
    });

    test("Transactions test:  SQL generation - good account.id", async () => {
        let params = Object.assign({}, txParams, { accountNum: { low: 11111, high: 22222 } });
        const req = {
            query: { "account.id": [`gte:0.1.${params.accountNum.low}`, `lte:0.1.${params.accountNum.high}`] }
        };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeTruthy();
        const sqlCheckStr =
            `where "ctl"."account_id" in (select "id" from "t_entities" where ` +
            `"entity_shard" = '0' and "entity_realm" = '1' and "entity_num" >= '${params.accountNum.low}' and ` +
            `"entity_shard" = '0' and "entity_realm" = '1' and "entity_num" <= '${params.accountNum.high}' ` +
            `and "fk_entity_type_id" < 3 limit 1000) order by "ctl"."consensus_timestamp" desc limit 1000) as "tlist" `;
        const expected = normalizeSql(queryPrefix + sqlCheckStr + querySuffix);
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad account.id", async () => {
        const req = { query: { "account.id": [`gte:0.1a.1111`, `lte:0.1.1.1`] } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.badParams).toEqual([
            { message: "Invalid parameter: account.id" },
            { message: "Invalid parameter: account.id" }
        ]);
        expect(sql.query).toBeUndefined();
    });

    test("Transactions test:  SQL generation - limit", async () => {
        let params = Object.assign({}, txParams, { limit: 99 });
        const req = { query: { limit: params.limit } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeTruthy();
        const sqlCheckStr = ` order by "ctl"."consensus_timestamp" desc limit ${params.limit}) as "tlist" `;
        const expected = normalizeSql(queryPrefix + sqlCheckStr + querySuffix);
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad limit", async () => {
        const req = { query: { limit: 11111 } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.badParams).toEqual([{ message: "Invalid parameter: limit" }]);
        expect(sql.query).toBeUndefined();
    });

    test("Transactions test:  SQL generation - order", async () => {
        let params = Object.assign({}, txParams, { order: "asc" });
        const req = { query: { order: params.order } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeTruthy();
        const sqlCheckStr = ` order by "ctl"."consensus_timestamp" ${params.order} limit 1000) as "tlist" `;
        const expected = normalizeSql(queryPrefix + sqlCheckStr + querySuffix.replace("desc", params.order));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad order", async () => {
        const req = { query: { order: "linear" } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.badParams).toEqual([{ message: "Invalid parameter: order" }]);
        expect(sql.query).toBeUndefined();
    });

    test("Transactions test:  SQL generation - result", async () => {
        let params = Object.assign({}, txParams, { result: "fail" });
        const req = { query: { result: params.result } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeTruthy();
        const sqlCheckStr = `where "result" != 'SUCCESS' order by "ctl"."consensus_timestamp" desc limit 1000) as "tlist" `;
        const expected = normalizeSql(queryPrefix + sqlCheckStr + querySuffix.replace("desc", params.order));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad result", async () => {
        const req = { query: { result: "ok" } };
        const sql = transactions.reqToSql(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.badParams).toEqual([{ message: "Invalid parameter: result" }]);
        expect(sql.query).toBeUndefined();
    });

    test("Transactions test:  SQL generation - one transaction", async () => {
        const req = { params: { id: "1.2.34567-1234567890-111222333" } };
        const sql = transactions.reqToSqlOneTransaction(req);
        expect(sql.isValid).toBeTruthy();
        const expectedStr =
            `select "etrans"."entity_shard","etrans"."entity_realm","etrans"."entity_num",` +
            `"t"."consensus_ns","valid_start_ns","t"."memo","t"."fk_trans_type_id","t"."fk_node_acc_id",` +
            `"enode"."entity_shard" as "node_shard","enode"."entity_realm" as "node_realm","enode"."entity_num" ` +
            `as "node_num","ttr"."result","ttt"."name","account_id","amount","eaccount"."entity_shard" as "account_shard",` +
            `"eaccount"."entity_realm" as "account_realm","eaccount"."entity_num" as "account_num","t"."charged_tx_fee" ` +
            `from (select distinct "t"."consensus_ns" as "consensus_timestamp" from "t_transactions" as "t" ` +
            `inner join "t_entities" as "etrans" on "etrans"."id" = "t"."fk_payer_acc_id" where "etrans"."entity_shard" = '1' ` +
            `and "etrans"."entity_realm" = '2' and "etrans"."entity_num" = '34567' and "t"."valid_start_ns" = ` +
            `'1234567890111222333') as "tlist" inner join "t_transactions" as "t" on "tlist"."consensus_timestamp" = ` +
            `"t"."consensus_ns" inner join "t_transaction_results" as "ttr" on "ttr"."id" = "t"."fk_result_id" ` +
            `inner join "t_entities" as "enode" on "enode"."id" = "t"."fk_node_acc_id" inner join "t_entities" as "etrans" ` +
            `on "etrans"."id" = "t"."fk_payer_acc_id" inner join "t_transaction_types" as "ttt" on "ttt"."id" = ` +
            `"t"."fk_trans_type_id" left outer join "t_cryptotransferlists" as "ctl" on "tlist"."consensus_timestamp" = ` +
            `"ctl"."consensus_timestamp" inner join "t_entities" as "eaccount" on "eaccount"."id" = "ctl"."account_id" ` +
            `order by "t"."consensus_ns" asc`;
        const expected = normalizeSql(expectedStr);
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Transactions test:  SQL generation - bad one transaction (account.id)", async () => {
        const req = { params: { id: "1.2.34567.9-1234567890-111222333" } };
        const sql = transactions.reqToSqlOneTransaction(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.code).toEqual(utils.httpStatusCodes.BAD_REQUEST);

        expect(sql.badParams).toEqual([
            {
                message:
                    'Invalid Transaction id. Please use "shard.realm.num-ssssssssss.nnnnnnnnn" format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds'
            }
        ]);
        expect(sql.query).toBeUndefined();
    });
    test("Transactions test:  SQL generation - bad one transaction (valid start)", async () => {
        const req = { params: { id: "1.2.34567-1234567890.111222333" } };
        const sql = transactions.reqToSqlOneTransaction(req);
        expect(sql.isValid).toBeFalsy();
        expect(sql.code).toEqual(utils.httpStatusCodes.BAD_REQUEST);
        expect(sql.badParams).toEqual([
            {
                message:
                    'Invalid Transaction id. Please use "shard.realm.num-ssssssssss.nnnnnnnnn" format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds'
            }
        ]);
        expect(sql.query).toBeUndefined();
    });
});

// DB response processing (processDbQueryResponse) tests
describe("Transaction tests - DB response processing", () => {
    let txParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc",
        result: "SUCCESS"
    };

    // DB response with 1000 entries
    test(`Transactions test: DB response processing - ${config.limits.RESPONSE_ROWS} entries`, async () => {
        const params = txParams;
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = transactions.reqToSql(req);
        const results = dbResponse(params);
        const ret = transactions.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents.transactions, params.limit)).toBeTruthy();
        expect(validateFields(ret.contents.transactions)).toBeTruthy();
        expect(validateTsRange(ret.contents.transactions, params.timestamp.low, params.timestamp.high)).toBeTruthy();
        expect(
            validateAccNumRange(ret.contents.transactions, params.accountNum.low, params.accountNum.high)
        ).toBeTruthy();
        expect(validateOrder(ret.contents.transactions, params.order)).toBeTruthy();
        expect(validateResult(ret.contents.transactions, params.result)).toBeTruthy();
        expect(validateNextLink(ret.contents, false, req.path));
    });

    // DB response with 0 entries
    test(`Transactions test: DB response processing - 0 entries`, async () => {
        const params = Object.assign({}, txParams, { limit: 0 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = transactions.reqToSql(req);
        const results = dbResponse(params);
        const ret = transactions.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents.transactions, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });

    // DB response with 1 entry
    test(`Transactions test: DB response processing - 1 entry`, async () => {
        const params = Object.assign({}, txParams, { limit: 1 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = transactions.reqToSql(req);
        const results = dbResponse(params);
        const ret = transactions.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents.transactions, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });
});

// Utility functions for the tests

/**
 * Create mock data
 * @param {Object} txParam Parameters (e.g. timestamp, accoount numbers, etc.)
 * @return {Object}  response object filled with mock data
 */
const dbResponse = function(txParam) {
    // Create a mock response based on the sql query parameters
    let rows = [];
    for (let i = 0; i < txParam.limit; i++) {
        let row = {};
        row.entity_shard = 0;
        row.entity_realm = 0;
        row.entity_num = i;
        row.memo = Buffer.from(`Test memo ${i}`);
        (row.consensus_ns = utils.secNsToNs(txParam.timestamp.high - i)),
            (row.valid_start_ns = utils.secNsToNs(txParam.timestamp.high - i - 1)),
            (row.result = txParam.result);
        row.fk_trans_type_id = 1;
        row.name = "CRYPTOTRANSFER";
        row.node_shard = 0;
        row.node_realm = 0;
        row.node_num = 1;
        row.account_shard = 0;
        row.account_realm = 0;
        row.account_num =
            txParam.accountNum.low +
            (txParam.accountNum.high == txParam.accountNum.low
                ? 0
                : i % (txParam.accountNum.high - txParam.accountNum.low));
        row.amount = i * 1000;
        row.charged_tx_fee = i + 100;
        rows.push(row);
    }
    if (["asc", "ASC"].includes(txParam.order)) {
        rows = rows.reverse();
    }
    return { rows: rows };
};

// Validation functions
/**
 * Validate length of the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function(transactions, len) {
    return transactions.length === len;
};

/**
 * Validate the range of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the timestamps
 * @param {Number} high Expected high limit of the timestamps
 * @return {Boolean}  Result of the check
 */
const validateTsRange = function(transactions, low, high) {
    let ret = true;
    let offender = null;
    for (const tx of transactions) {
        if (tx.consensus_timestamp < low || tx.consensus_timestamp > high) {
            offender = tx;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateTsRange check failed: ${offender.consensus_timestamp} is not between ${low} and  ${high}`);
    }
    return ret;
};

/**
 * Validate the range of account ids in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function(transactions, low, high) {
    let ret = true;
    let offender = null;
    for (const tx of transactions) {
        for (const xfer of tx.transfers) {
            const accNum = xfer.account.split(".")[2];
            if (accNum < low || accNum > high) {
                offender = accNum;
                ret = false;
            }
        }
    }
    if (!ret) {
        console.log(`validateAccNumRange check failed: ${offender} is not between ${low} and  ${high}`);
    }
    return ret;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} transactions Array of transactions returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function(transactions) {
    let ret = true;

    // Assert that all mandatory fields are present in the response
    [
        "consensus_timestamp",
        "valid_start_timestamp",
        "charged_tx_fee",
        "transaction_id",
        "memo_base64",
        "result",
        "name",
        "node",
        "transfers"
    ].forEach(field => {
        ret = ret && transactions[0].hasOwnProperty(field);
    });

    // Assert that the transfers is an array
    ret = ret && Array.isArray(transactions[0].transfers);

    // Assert that the transfers array has the mandatory fields
    if (ret) {
        ["account", "amount"].forEach(field => {
            ret = ret && transactions[0].transfers[0].hasOwnProperty(field);
        });
    }
    if (!ret) {
        console.log(`validateFields check failed: A mandatory parameter is missing`);
    }
    return ret;
};

/**
 * Validate the order of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function(transactions, order) {
    let ret = true;
    let offenderTx = null;
    let offenderVal = null;
    let direction = order === "desc" ? -1 : 1;
    let val = transactions[0].consensus_timestamp - direction;
    for (const tx of transactions) {
        if (val * direction > tx.consensus_timestamp * direction) {
            offenderTx = tx;
            offenderVal = val;
            ret = false;
        }
        val = tx.consensus_timestamp;
    }
    if (!ret) {
        console.log(
            `validateOrder check failed: ${offenderTx.consensus_timestamp} - previous timestamp ${offenderVal} Order  ${order}`
        );
    }
    return ret;
};

/**
 * Validate the order of timestamps in the transactions returned by the api
 * @param {Array} transactions Array of transactions returned by the rest api
 * @param {String} result Expected result type ('success' or 'fail')
 * @return {Boolean}  Result of the check
 */
const validateResult = function(transactions, result) {
    let ret = true;
    let offenderTx = null;
    let offenderVal = null;
    for (const tx of transactions) {
        if (tx.result != result) {
            offenderTx = tx;
            offenderVal = tx.result;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateResult check failed: ${offenderVal} does not match  ${result}`);
    }
    return ret;
};

/**
 * Validate the presence of absence of the 'next' link  in the response returned by the api
 * @param {Object} contents Contents returned by the rest api
 * @param {Boolean} shouldBeNull if the result should be null or not
 * @param {String} path the path in the next link
 * @return {Boolean}  Result of the check
 */
const validateNextLink = function(contents, shouldBeNull, path) {
    let ret = true;

    if (!contents.hasOwnProperty("links") || contents.links.next === undefined) {
        console.log(`validateNextLink check failed: the next property not present in the response`);
        ret = false;
    } else if (shouldBeNull) {
        if (contents.links.next != null) {
            console.log(`validateNextLink check failed: the next link ${contents.links.next} is not null as expected`);
            ret = false;
        }
    } else {
        if (!contents.links.next.startsWith(path)) {
            console.log(
                `validateNextLink check failed: the next link ${contents.links.next} does not start with ${path}`
            );
            ret = false;
        }
    }

    if (!ret) {
        console.log(`validateNextLink check failed: the next link is invalid`);
    }
    return ret;
};
