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
    globals.verbose && console.log('Jest starting!');
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
    const response = await request(server).get(globals.apiPrefix + '/balances');
    expect(response.status).toEqual(200);
    let balances = JSON.parse(response.text).balances;

    globals.testAccounts = {
        num: testutils.toAccNum(balances[0].account),
        highest: 0,
        nonZeroBalance: null
    };
    for (let bal of balances) {
        if (Number(testutils.toAccNum(bal.account)) > globals.testAccounts.highest) {
            globals.testAccounts.highest = Number(testutils.toAccNum(bal.account));
        }
        if (Number(bal.balance) > 0) {
            globals.testAccounts.nonZeroBalance = bal;
        }
    }
    globals.testBalances = balances;
    globals.testTimestamp = JSON.parse(response.text).timestamp;

    expect(balances.length).toBe(config.limits.RESPONSE_ROWS);
    expect(globals.testAccounts.num).toBeDefined();
    expect(globals.testTimestamp).toBeDefined();
}

const checkMandatoryParams = function (entry) {
    let check = true;
    ['account', 'balance'].forEach((field) => {
        check = check && entry.hasOwnProperty(field);
    });

    return (check);
}

const debuglog = (msg) => globals.verbose && console.log(msg);

describe('Monitoring tests - balances', () => {
    test('Get balances with no parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/balances');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toBe(config.limits.RESPONSE_ROWS);

        // Assert that all mandatory fields are present in the response
        let check = checkMandatoryParams(balances[0]);
        expect(check).toBeTruthy();

        // Check for freshness of data
        const balancesSec = JSON.parse(response.text).timestamp.split('.')[0];
        const currSec = Math.floor(new Date().getTime() / 1000);
        const delta = currSec - balancesSec;
        check = delta < (2 * config.fileUpdateRefreshTimes.balances)
        expect(check).toBeTruthy();
    });

    test('Get balances with timestamp & limit parameters', async () => {
        let plusOne = math.add(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(globals.testTimestamp), math.bignumber(1));
        const url = `${globals.apiPrefix}/balances` +
            `?timestamp=gt:${minusOne.toString()}` +
            `&timestamp=lt:${plusOne.toString()}&limit=1`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toEqual(1);
    });

    test('Get balances with account id parameters', async () => {
        const url = `${globals.apiPrefix}/balances` +
            `?account.id=0.0.${globals.testAccounts.highest}&limit=1`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toEqual(1);
        expect(globals.testAccounts.highest).toBe(Number(testutils.toAccNum(balances[0].account)));
    });
});

describe('Acceptance tests - balances', () => {
    test('Get balances with limit parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/balances?limit=10');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toEqual(10);
    });

    test('Get balances with account id range parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        const url = `${globals.apiPrefix}/balances` +
            `?account.id=gt:0.0.${accLow}&account.id=lt:0.0.${accHigh}`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toBeGreaterThanOrEqual(1);
        let check = true;
        for (let bal of balances) {
            let acc = testutils.toAccNum(bal.account);
            if (acc < accLow || acc > accHigh) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });

    test('Get balances with account balance range parameters', async () => {
        let balLow = Number(globals.testAccounts.nonZeroBalance.balance) - 1;
        let balHigh = Number(globals.testAccounts.nonZeroBalance.balance) + 1;
        const url = `${globals.apiPrefix}/balances` +
            `?account.balance=gt:${balLow}&account.balance=lt:${balHigh}`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toBeGreaterThanOrEqual(1);
    });

    test('Get balances with account key parameters', async () => {
        const url = `${globals.apiPrefix}/balances` +
            `?account.publickey=1234567890123456789012345678901234567890123456789012345678901234`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toBe(0);
    });

    test('Get balances with order parameters', async () => {
        const url = `${globals.apiPrefix}/balances` +
            `?order=asc`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;
        expect(balances.length).toEqual(config.limits.RESPONSE_ROWS);
        let check = true;
        let prevAcc = 0;
        for (let bal of balances) {
            if (Number(testutils.toAccNum(bal.account)) <= prevAcc) {
                check = false;
            }
            prevAcc = testutils.toAccNum(bal.account);
        }
        expect(check).toBeTruthy();
    });

    test('Get balances with all parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        let plusOne = math.add(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(globals.testTimestamp), math.bignumber(1));
        let balLow = Number(globals.testAccounts.nonZeroBalance.balance) - 1;
        let balHigh = Number(globals.testAccounts.nonZeroBalance.balance) + 1;
        const url = `${globals.apiPrefix}/balances` +
            `?timestamp=gte:${minusOne}&timestamp=lte:${plusOne}` +
            `&account.id=gt:${accLow}&account.id=lt:${accHigh}` +
            `&account.balance=gt:${balLow}&account.balance=lt:${balHigh}` +
            `&account.publickey=1234567890123456789012345678901234567890123456789012345678901234` +
            `&order=desc&limit=${config.limits.RESPONSE_ROWS}`;
        globals.verbose && console.log(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
    });

    test('Get balances with pagination', async () => {
        // Validate that pagination works and that it doesn't have any gaps
        let paginatedEntries = [];
        const numPages = 5;
        const pageSize = config.limits.RESPONSE_ROWS / numPages;
        let next = null;
        for (let index = 0; index < numPages; index++) {
            const nextUrl = paginatedEntries.length === 0 ?
                `${globals.apiPrefix}/balances?timestamp=lte:${globals.testTimestamp}&limit=${pageSize}` :
                next;
            globals.verbose && console.log("Nexturl: " + nextUrl);
            response = await request(server).get(nextUrl);
            expect(response.status).toEqual(200);
            let chunk = JSON.parse(response.text).balances;
            expect(chunk.length).toEqual(pageSize);
            paginatedEntries = paginatedEntries.concat(chunk);

            next = JSON.parse(response.text).links.next;
            expect(next).not.toBe(null);
        }

        check = (paginatedEntries.length === globals.testBalances.length);
        expect(check).toBeTruthy();

        check = true;
        for (i = 0; i < config.limits.RESPONSE_ROWS; i++) {
            if (globals.testBalances[i].account !== paginatedEntries[i].account) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });
});