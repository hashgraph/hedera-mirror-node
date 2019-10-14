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

const accounts = require("../accounts.js");
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

const queryPrefix = function() {
    return (
        `select "ab"."balance" as "account_balance", "ab"."consensus_timestamp" as "consensus_timestamp", ` +
        `'0' as "entity_shard", coalesce(ab.account_realm_num, e.entity_realm) as "entity_realm", ` +
        `coalesce(ab.account_num, e.entity_num) as "entity_num", "e"."exp_time_ns", "e"."auto_renew_period", ` +
        `"e"."key", "e"."deleted" from "account_balances" as "ab" full outer join "t_entities" as "e" ` +
        `on '0' = "e"."entity_shard" and "ab"."account_realm_num" = "e"."entity_realm" and ` +
        `"ab"."account_num" = "e"."entity_num" and "e"."fk_entity_type_id" < 3 where ` +
        `"ab"."consensus_timestamp" = (select max("consensus_timestamp") from "account_balances") `
    );
};

const querySuffix = function(order = "desc", limit = config.limits.RESPONSE_ROWS) {
    return `order by coalesce(ab.account_num, e.entity_num) ${order} limit ${limit}`;
};

// Start of tests
describe("Accounts tests", () => {
    let accParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        balance: { low: 0, high: 10000 },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc"
    };

    test("Accounts test: account.id", async () => {
        let params = Object.assign({}, accParams, { accountNum: { low: 11111, high: 22222 } });
        const req = {
            query: { "account.id": [`gte:0.1.${params.accountNum.low}`, `lte:0.1.${params.accountNum.high}`] }
        };
        const sql = accounts.reqToSql(req);
        const prefix = queryPrefix();
        const sqlCheckStr = ` and (("ab"."account_realm_num" = '1' and "ab"."account_num" >= '${params.accountNum.low}') or ("e"."entity_realm" = '1' and "e"."entity_num" >= '${params.accountNum.low}')) and (("ab"."account_realm_num" = '1' and "ab"."account_num" <= '${params.accountNum.high}') or ("e"."entity_realm" = '1' and "e"."entity_num" <= '${params.accountNum.high}')) `;
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Accounts test: account.balance", async () => {
        let params = Object.assign({}, accParams, { balance: { low: 1234, high: 5678 } });
        const req = { query: { "account.balance": [`gte:${params.balance.low}`, `lte:${params.balance.high}`] } };
        const sql = accounts.reqToSql(req);
        const prefix = queryPrefix();
        const sqlCheckStr = ` and "ab"."balance" >= '${params.balance.low}' and "ab"."balance" <= '${params.balance.high}' `;
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Accounts test: limit", async () => {
        let params = Object.assign({}, accParams, { limit: 99 });
        const req = { query: { limit: params.limit } };
        const sql = accounts.reqToSql(req);
        const prefix = queryPrefix();
        const sqlCheckStr = "";
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Accounts test: order", async () => {
        let params = Object.assign({}, accParams, { order: "asc" });
        const req = { query: { order: params.order } };
        const sql = accounts.reqToSql(req);
        const prefix = queryPrefix("");
        const sqlCheckStr = "";
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });

    test("Accounts test: one account", async () => {
        let params = { id: "0.1.234567", limit: 1, order: "asc" };
        const req = { params: params };
        const sql = accounts.reqToSqlOneAccount(req);
        const prefix = queryPrefix("");
        const sqlCheckStr = ` and (("ab"."account_realm_num" = '1' and "ab"."account_num" = '234567') or ("e"."entity_realm" = '1' and "e"."entity_num" = '234567')) `;
        const expected = normalizeSql(prefix + sqlCheckStr + querySuffix(params.order, params.limit));
        expect(normalizeSql(sql.query)).toEqual(expected);
    });
});

// DB response processing (processDbQueryResponse) tests
describe("Accounts tests - DB response processing", () => {
    let accParams = {
        accountNum: { low: 1, high: 10000 },
        timestamp: { low: moduleVars.timeOneHourAgo, high: moduleVars.timeNow },
        balance: { low: 0, high: 10000 },
        limit: config.limits.RESPONSE_ROWS,
        order: "desc"
    };

    // DB response with 1000 entries
    test(`Accounts test: DB response processing - ${config.limits.RESPONSE_ROWS} entries`, async () => {
        const params = accParams;
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = accounts.reqToSql(req);

        const results = dbResponse(params);
        const ret = accounts.processDbQueryResponse(req, sql, results);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateFields(ret.contents)).toBeTruthy();
        expect(validateAccNumRange(ret.contents, params.accountNum.low, params.accountNum.high)).toBeTruthy();
        expect(validateOrder(ret.contents, params.order)).toBeTruthy();
        expect(validateNextLink(ret.contents, false, req.path));
    });

    // DB response with 0 entries
    test(`Accounts test: DB response processing - 0 entries`, async () => {
        const params = Object.assign({}, accParams, { limit: 0 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = accounts.reqToSql(req);
        const results = dbResponse(params);
        const ret = accounts.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });

    // DB response with 1 entry
    test(`Accounts test: DB response processing - 1 entry`, async () => {
        const params = Object.assign({}, accParams, { limit: 1 });
        const req = { query: "", path: "/path/to/api/endpoint" };
        const sql = accounts.reqToSql(req);
        const results = dbResponse(params);
        const ret = accounts.processDbQueryResponse(req, sql, results, false);
        expect(ret.code).toBe(utils.httpStatusCodes.OK);
        expect(validateLen(ret.contents, params.limit)).toBeTruthy();
        expect(validateNextLink(ret.contents, true, req.path));
    });
});

/**
 * Create mock data
 * @param {Object} accParams Parameters (e.g. timestamp, accoount numbers, etc.)
 * @return {Object}  response object filled with mock data
 */
const dbResponse = function(accParams) {
    // Create a mock response based on the sql query parameters
    let rows = [];
    for (let i = 0; i < accParams.limit; i++) {
        let row = {};

        row.account_balance =
            accParams.balance.low + Math.floor((accParams.balance.high - accParams.balance.low) / accParams.limit.high);
        row.consensus_timestamp = utils.secNsToNs(Math.floor((accParams.timestamp.low + accParams.timestamp.high) / 2));
        row.entity_shard = 0;
        row.entity_realm = 0;
        row.entity_num =
            accParams.accountNum.low +
            (accParams.accountNum.high == accParams.accountNum.low
                ? 0
                : i % (accParams.accountNum.high - accParams.accountNum.low));

        row.exp_time_ns = row.consensus_timestamp;
        row.auto_renew_period = i * 1000;
        row.key = Buffer.from(`Key for row ${i}`);
        row.deleted = false;
        row.entity_type = "Account";

        rows.push(row);
    }

    if (["asc", "ASC"].includes(accParams.order)) {
        rows = rows.reverse();
    }
    return { rows: rows };
};

// Validation functions
/**
 * Validate length of the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Number} len Expected length
 * @return {Boolean}  Result of the check
 */
const validateLen = function(accounts, len) {
    return accounts.accounts.length === len;
};

/**
 * Validate the range of account ids in the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {Number} low Expected low limit of the account ids
 * @param {Number} high Expected high limit of the account ids
 * @return {Boolean}  Result of the check
 */
const validateAccNumRange = function(accounts, low, high) {
    let ret = true;
    let offender = null;
    for (const acc of accounts.accounts) {
        const accNum = acc.account.split(".")[2];
        if (accNum < low || accNum > high) {
            offender = accNum;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateAccNumRange check failed: ${offender} is not between ${low} and ${high}`);
    }
    return ret;
};

/**
 * Validate the range of account balances in the accounts returned by the api
 * @param {Array} balances Array of accounts returned by the rest api
 * @param {Number} low Expected low limit of the balances
 * @param {Number} high Expected high limit of the balances
 * @return {Boolean}  Result of the check
 */
const validateBalanceRange = function(accounts, low, high) {
    let ret = true;
    let offender = null;
    for (const acc of accounts.accounts) {
        if (acc.balance.balance < low || acc.balance.balance > high) {
            offender = acc.balance.balance;
            ret = false;
        }
    }
    if (!ret) {
        console.log(`validateBalanceRange check failed: ${offender} is not between ${low} and ${high}`);
    }
    return ret;
};

/**
 * Validate that all required fields are present in the response
 * @param {Array} accounts Array of accounts returned by the rest api
 * @return {Boolean}  Result of the check
 */
const validateFields = function(accounts) {
    let ret = true;

    // Assert that the accounts is an array
    ret = ret && Array.isArray(accounts.accounts);

    // Assert that all mandatory fields are present in the response
    ["balance", "account", "expiry_timestamp", "auto_renew_period", "key", "deleted"].forEach(field => {
        ret = ret && accounts.accounts[0].hasOwnProperty(field);
    });

    // Assert that the balances object has the mandatory fields
    if (ret) {
        ["timestamp", "balance"].forEach(field => {
            ret = ret && accounts.accounts[0].balance.hasOwnProperty(field);
        });
    }

    if (!ret) {
        console.log(`validateFields check failed: A mandatory parameter is missing`);
    }
    return ret;
};

/**
 * Validate the order of timestamps in the accounts returned by the api
 * @param {Array} accounts Array of accounts returned by the rest api
 * @param {String} order Expected order ('asc' or 'desc')
 * @return {Boolean}  Result of the check
 */
const validateOrder = function(accounts, order) {
    let ret = true;
    let offenderAcc = null;
    let offenderVal = null;
    let direction = order === "desc" ? -1 : 1;
    const toAccNum = acc => acc.num;

    let val = toAccNum(accounts.accounts[0].account) - direction;
    for (const acc of accounts.accounts) {
        if (val * direction > toAccNum(acc.account) * direction) {
            offenderAcc = toAccNum(acc);
            offenderVal = val;
            ret = false;
        }
        val = toAccNum(acc.account);
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
