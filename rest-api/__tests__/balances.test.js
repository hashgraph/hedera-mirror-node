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

const balances = require("../balances.js");
const config = require("../config.js");
const utils = require("../utils.js");

const moduleVars = {
    timeNow: parseInt(new Date().getTime() / 1000),
    timeOneHourAgo: parseInt(new Date().getTime() / 1000) - 60 * 60
};

function normalizeSql(str) {
    return str
        .replace(/\s+/g, " ")
        .replace(/,\s*/g, ",")
        .replace(/\s*,/g, ",")
        .replace(/\s+$/, "");
}

const queryPrefix = function(condition) {
    return (
        `select "ab"."consensus_timestamp", "ab"."account_realm_num" as "realm_num", "ab"."account_num" as "entity_num", ` +
        `"ab"."balance" from "account_balances" as "ab" where "consensus_timestamp" = (` +
        `select "consensus_timestamp" from "account_balances" as "ab" ${condition} order by "consensus_timestamp" desc limit 1) `
    );
};

const querySuffix = function(order = "desc", limit = config.limits.RESPONSE_ROWS) {
    return `order by "consensus_timestamp" desc, "account_realm_num" ${order}, "account_num" ${order} limit ${limit}`;
};

// Start of tests

// SQL generation (reqToSql) tests
describe("Balances tests - SQL generation", () => {
    let balParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        balance: { low: 0, high: 10000 },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc"
    };

    test("Balances test: SQL generation - timestamp", async () => {
        let params = balParams;
        const req = { query: { timestamp: [`gte:${params.timestamp.low}`, `lte:${params.timestamp.high}`] } };
        const sql = balances.reqToSql(req);
        const prefix = queryPrefix(
            `where "ab"."consensus_timestamp" >= '${utils.secNsToNs(
                params.timestamp.low
            )}' and "ab"."consensus_timestamp" <= '${utils.secNsToNs(params.timestamp.high)}'`
        );
        const expected = normalizeSql(prefix + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Balances test: SQL generation - account.id", async () => {
        let params = Object.assign({}, balParams, { accountNum: { low: 11111, high: 22222 } });
        const req = {
            query: { "account.id": [`gte:0.1.${params.accountNum.low}`, `lte:0.1.${params.accountNum.high}`] }
        };
        const sql = balances.reqToSql(req);
        const prefix = queryPrefix("");
        const sqlCheckStr = `and "ab"."account_realm_num" = '1' and "ab"."account_num" >= '${params.accountNum.low}' and "ab"."account_realm_num" = '1' and "ab"."account_num" <= '${params.accountNum.high}' `;
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Balances test: SQL generation - limit", async () => {
        let params = Object.assign({}, balParams, { limit: 99 });
        const req = { query: { limit: params.limit } };
        const sql = balances.reqToSql(req);
        const prefix = queryPrefix("");
        const sqlCheckStr = "";
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Balances test: SQL generation - order", async () => {
        let params = Object.assign({}, balParams, { order: "asc" });
        const req = { query: { order: params.order } };
        const sql = balances.reqToSql(req);
        const prefix = queryPrefix("");
        const sqlCheckStr = "";
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });
});

// DB response processing (processDbQueryResponse) tests
describe("Balances tests - DB response processing", () => {
    let balParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        balance: { low: 0, high: 10000 },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc"
    };

    // DB response with 1000 entries
    test(`Balances test: DB response processing - ${config.limits.RESPONSE_ROWS} entries`, async () => {
        const params = balParams;
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = balances.reqToSql(req);

        const results = dbResponse(params);
        const ret = balances.processDbQueryResponse(req, sql, results);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateFields(ret.contents)).toBeTruthy();
        expect(validateTsRange(ret.contents, params.timestamp.low, params.timestamp.high)).toBeTruthy();
        expect(validateAccNumRange(ret.contents, params.accountNum.low, params.accountNum.high)).toBeTruthy();
        expect(validateOrder(ret.contents, params.order)).toBeTruthy();
        expect(validateNextLink(ret.contents, false, req.path));
    });

    // DB response with 0 entries
    test(`Balances test: DB response processing - 0 entries`, async () => {
        const params = Object.assign({}, balParams, { limit: 0 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = balances.reqToSql(req);
        const results = dbResponse(params);
        const ret = balances.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });

    // DB response with 1 entry
    test(`Balances test: DB response processing - 1 entry`, async () => {
        const params = Object.assign({}, balParams, { limit: 1 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = balances.reqToSql(req);
        const results = dbResponse(params);
        const ret = balances.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });
});

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
        row.consensus_timestamp = utils.secNsToNs(Math.floor((txParam.timestamp.low + txParam.timestamp.high) / 2));
        row.realm_num = 0;
        row.entity_num =
            txParam.accountNum.low +
            (txParam.accountNum.high == txParam.accountNum.low
                ? 0
                : i % (txParam.accountNum.high - txParam.accountNum.low));
        row.balance = txParam.balance.low + Math.floor((txParam.balance.high - txParam.balance.low) / txParam.limit);
        rows.push(row);
    }
    if (["asc", "ASC"].includes(txParam.order)) {
        rows = rows.reverse();
    }
    return { rows: rows };
};

// Validation functions
/**
 * Validate length of the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function(balances, len) {
    return balances.balances.length === len;
};

/**
 * Validate the range of timestamps in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} low Expected low limit of the timestamps
 * @param {Number} high Expected high limit of the timestamps
 * @return {Boolean}  Result of the check
 */
const validateTsRange = function(balances, low, high) {
    let ret = balances.timestamp >= low && balances.timestamp <= high;

    if (!ret) {
        console.log(`validateTsRange check failed: ${balances.timestamp} is not between ${low} and  ${high}`);
    }
    return ret;
};

/**
 * Validate the range of account ids in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function(balances, low, high) {
    let ret = true;
    let offender = null;
    for (const bal of balances.balances) {
        const accNum = bal.account.split(".")[2];
        if (accNum < low || accNum > high) {
            offender = accNum;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateAccNumRange check failed: ${offender} is not between ${low} and  ${high}`);
    }
    return ret;
};

/**
 * Validate the range of account balances in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {Number} low Expected low limit of the balances
 * @param {Number} high Expected high limit of the balances
 * @return {Boolean}  Result of the check
 */
const validateBalanceRange = function(balances, low, high) {
    let ret = true;
    let offender = null;
    for (const bal of balances.balances) {
        if (bal.balance < low || bal.balance > high) {
            offender = bal.balance;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateBalanceRange check failed: ${offender} is not between ${low} and  ${high}`);
    }
    return ret;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} balances Array of balances returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function(balances) {
    let ret = true;

    // Assert that the balances is an array
    ret = ret && Array.isArray(balances.balances);

    // Assert that all mandatory fields are present in the response
    ["timestamp", "balances"].forEach(field => {
        ret = ret && balances.hasOwnProperty(field);
    });

    // Assert that the balances array has the mandatory fields
    if (ret) {
        ["account", "balance"].forEach(field => {
            ret = ret && balances.balances[0].hasOwnProperty(field);
        });
    }

    if (!ret) {
        console.log(`validateFields check failed: A mandatory parameter is missing`);
    }
    return ret;
};

/**
 * Validate the order of timestamps in the balances returned by the api
 * @param {Array} balances Array of balances returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function(balances, order) {
    let ret = true;
    let offenderAcc = null;
    let offenderVal = null;
    let direction = order === "desc" ? -1 : 1;
    const toAccNum = acc => acc.split(".")[2];
    let val = toAccNum(balances.balances[0].account) - direction;
    for (const bal of balances.balances) {
        if (val * direction > toAccNum(bal.account) * direction) {
            offenderAcc = toAccNum(bal);
            offenderVal = toAccNum(val);
            ret = false;
        }
        val = bal;
    }
    if (!ret) {
        console.log(
            `validateOrder check failed: ${offenderAcc} - previous account number ${offenderVal} Order  ${order}`
        );
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
