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
const request = require('supertest');
const math = require('mathjs');
const server = process.env.TARGET;
const testutils = require('./tutils.js');
const config = require('../config.js');

beforeAll(async () => {
    debuglog('Jest starting!');
    jest.setTimeout(20000);

    if (process.env.verbose != undefined && process.env.verbose == 1) {
        globals.verbose = true;
    }

    await setGlobals();
});

afterAll(() => {});

let globals = {
    apiPrefix: '/api/v1',
    verbose: false
};

const setGlobals = async function () {
    const response = await request(server).get(globals.apiPrefix + '/accounts');
    expect(response.status).toEqual(200);
    let accounts = JSON.parse(response.text).accounts;

    globals.testAccounts = {
        num: testutils.toAccNum(accounts[0].account),
        highest: 0,
        nonZeroBalance: null
    };

    for (let acc of accounts) {
        if (Number(testutils.toAccNum(acc.account)) > globals.testAccounts.highest) {
            globals.testAccounts.highest = Number(testutils.toAccNum(acc.account));
        }
        if (acc.balance.balance > 0) {
            globals.testAccounts.nonZeroBalance = acc;
        }
    }
    globals.testAcc = accounts;
    globals.testTimestamp = accounts[0].balance.timestamp;

    expect(accounts.length).toBe(config.limits.RESPONSE_ROWS);
    expect(globals.testAccounts.num).toBeDefined();
    expect(globals.testTimestamp).toBeDefined();
}

const checkMandatoryParams = function (entry) {
    let check = true;
    ['balance', 'account', 'expiry_timestamp', 'auto_renew_period',
        'key', 'deleted'
    ].forEach((field) => {
        check = check && entry.hasOwnProperty(field);
    });

    ['timestamp', 'balance'].forEach((field) => {
        check = check && entry.hasOwnProperty('balance') && entry.balance.hasOwnProperty(field);
    });

    return (check);
}

const debuglog = (msg) => globals.verbose && console.log(msg);

describe('Monitoring tests - accounts', () => {
    test('Get accounts with no parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/accounts');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toBe(config.limits.RESPONSE_ROWS);

        // Assert that all mandatory fields are present in the response
        let check = checkMandatoryParams(accounts[0]);
        expect(check).toBeTruthy();
    });

    test('Get accounts with timestamp & limit parameters', async () => {
        let plusOne = math.add(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(globals.testTimestamp), math.bignumber(1));
        const url = `${globals.apiPrefix}/accounts` +
            `?timestamp=gt:${minusOne.toString()}` +
            `&timestamp=lt:${plusOne.toString()}&limit=1`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toEqual(1);
    });

    test('Get accounts for a single account', async () => {
        const url = `${globals.apiPrefix}/accounts` +
            `/0.0.${globals.testAccounts.highest}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text);
        expect(accounts.account).toEqual('0.0.' + globals.testAccounts.highest);

        // Assert that all mandatory fields are present in the response
        let check = checkMandatoryParams(accounts);
        expect(check).toBeTruthy();
    });
});

describe('Acceptance tests - accounts', () => {
    test('Get accounts with limit parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/accounts?limit=10');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toEqual(10);
    });

    test('Get accounts with account id parameters', async () => {
        const url = `${globals.apiPrefix}/accounts` +
            `?account.id=0.0.${globals.testAccounts.highest}&limit=1`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toEqual(1);
        expect(globals.testAccounts.highest).toBe(Number(testutils.toAccNum(accounts[0].account)));
    });

    test('Get accounts with account id range parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        const url = `${globals.apiPrefix}/accounts` +
            `?account.id=gt:0.0.${accLow}&account.id=lt:0.0.${accHigh}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toBeGreaterThanOrEqual(1);
        let check = true;
        for (let acc of accounts) {
            if (acc < accLow || acc > accHigh) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });

    test('Get accounts with account balance range parameters', async () => {
        let balLow = Number(globals.testAccounts.nonZeroBalance.balance.balance) - 1;
        let balHigh = Number(globals.testAccounts.nonZeroBalance.balance.balance) + 1;
        const url = `${globals.apiPrefix}/accounts` +
            `?account.balance=gt:${balLow}&account.balance=lt:${balHigh}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toBeGreaterThanOrEqual(1);
    });

    test('Get accounts with account key parameters', async () => {
        const url = `${globals.apiPrefix}/accounts` +
            `?account.publickey=1234567890123456789012345678901234567890123456789012345678901234`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toBe(0);
    });

    test('Get accounts with order parameters', async () => {
        const url = `${globals.apiPrefix}/accounts` +
            `?order=desc`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;
        expect(accounts.length).toEqual(config.limits.RESPONSE_ROWS);
        let check = true;
        let prevAcc = Number.MAX_SAFE_INTEGER;
        for (let acc of accounts) {
            if (Number(testutils.toAccNum(acc.account)) >= prevAcc) {
                check = false;
            }
            prevAcc = testutils.toAccNum(acc.account);
        }
        expect(check).toBeTruthy();
    });

    test('Get accounts with all parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        let plusOne = math.add(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let balLow = Number(globals.testAccounts.nonZeroBalance.balance.balance) - 1;
        let balHigh = Number(globals.testAccounts.nonZeroBalance.balance.balance) + 1;
        const url = `${globals.apiPrefix}/accounts` +
            `?timestamp=gte:${minusOne}&timestamp=lte:${plusOne}` +
            `&account.id=gt:${accLow}&account.id=lt:${accHigh}` +
            `&account.balance=gt:${balLow}&account.balance=lt:${balHigh}` +
            `&account.publickey=1234567890123456789012345678901234567890123456789012345678901234` +
            `&order=desc&limit=${config.limits.RESPONSE_ROWS}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
    });

    test('Get accounts with pagination', async () => {
        // Validate that pagination works and that it doesn't have any gaps
        let paginatedEntries = [];
        const numPages = 5;
        const pageSize = config.limits.RESPONSE_ROWS / numPages;
        let next = null;
        for (let index = 0; index < numPages; index++) {
            const nextUrl = paginatedEntries.length === 0 ?
                `${globals.apiPrefix}/accounts?timestamp=lte:${globals.testTimestamp}&limit=${pageSize}` :
                next;
            debuglog("Nexturl: " + nextUrl);
            response = await request(server).get(nextUrl);
            expect(response.status).toEqual(200);
            let chunk = JSON.parse(response.text).accounts;
            expect(chunk.length).toEqual(pageSize);
            paginatedEntries = paginatedEntries.concat(chunk);

            next = JSON.parse(response.text).links.next;
            expect(next).not.toBe(null);
        }

        check = (paginatedEntries.length === globals.testAcc.length);
        expect(check).toBeTruthy();

        check = true;
        for (i = 0; i < config.limits.RESPONSE_ROWS; i++) {
            if (globals.testAcc[i].account !== paginatedEntries[i].account) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });
});