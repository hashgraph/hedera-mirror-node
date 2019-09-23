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
    const response = await request(server).get(globals.apiPrefix + '/transactions');
    expect(response.status).toEqual(200);
    let transactions = JSON.parse(response.text).transactions;

    globals.testAccounts = {
        num: null,
        highest: 0
    };

    for (let xfer of transactions[0].transfers) {
        if (xfer.amount > 0) {
            globals.testAccounts.num = testutils.toAccNum(xfer.account);
            if (testutils.toAccNum(xfer.account) > globals.testAccounts.highest) {
                globals.testAccounts.highest = testutils.toAccNum(xfer.account);
            }
        }
    }
    globals.testTx = transactions;

    expect(transactions.length).toBe(config.limits.RESPONSE_ROWS);
    expect(globals.testAccounts.num).toBeDefined();
    expect(globals.testTx[0].consensus_timestamp).toBeDefined();
}

const checkMandatoryParams = function (entry) {
    let check = true;
    ['consensus_timestamp', 'valid_start_timestamp', 'charged_tx_fee', 'transaction_id',
        'memo_base64', 'result', 'name', 'node', 'transfers'
    ].forEach((field) => {
        check = check && entry.hasOwnProperty(field);
    });
    return (check);
}

const debuglog = (msg) => globals.verbose && console.log(msg);

describe('Monitoring tests - transactions', () => {
    test('Get transactions with no parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/transactions');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toBe(config.limits.RESPONSE_ROWS);

        // Assert that all mandatory fields are present in the response
        let check = checkMandatoryParams(transactions[0]);
        expect(check).toBeTruthy();

        // Check for freshness of data
        const txSec = transactions[0].consensus_timestamp.split('.')[0];
        const currSec = Math.floor(new Date().getTime() / 1000);
        const delta = currSec - txSec
        check = delta < (2 * config.fileUpdateRefreshTimes.records)
        expect(check).toBeTruthy();
    });

    test('Get transactions with timestamp & limit parameters', async () => {
        let plusOne = math.add(math.bignumber(globals.testTx[0].consensus_timestamp), math.bignumber(1));
        let minusOne = math.subtract(math.bignumber(globals.testTx[0].consensus_timestamp), math.bignumber(1));
        const url = `${globals.apiPrefix}/transactions` +
            `?timestamp=gt:${minusOne.toString()}` +
            `&timestamp=lt:${plusOne.toString()}&limit=1`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
    });

    test('Get transactions for a single transaction', async () => {
        const url = `${globals.apiPrefix}/transactions` +
            `/${globals.testTx[0].transaction_id}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
        expect(transactions[0].transaction_id).toEqual(globals.testTx[0].transaction_id);

        // Assert that all mandatory fields are present in the response
        let check = checkMandatoryParams(transactions[0]);
        expect(check).toBeTruthy();
    });
});

describe('Acceptance tests - transactions', () => {
    test('Get transactions with limit parameters', async () => {
        const response = await request(server).get(globals.apiPrefix + '/transactions?limit=10');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(10);
    });

    test('Get transactions with account id parameters', async () => {
        const url = `${globals.apiPrefix}/transactions` +
            `?account.id=${globals.testAccounts.highest}&type=credit&limit=1`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(1);
        let check = false;
        for (let xfer of transactions[0].transfers) {
            if (xfer.account.split('.')[2] === globals.testAccounts.highest) {
                check = true;
            }
        }
        expect(check).toBeTruthy();
    });

    test('Get transactions with account id range parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        const url = `${globals.apiPrefix}/transactions` +
            `?account.id=gt:${accLow}&account.id=lt:${accHigh}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(config.limits.RESPONSE_ROWS);
        let check = false;
        for (let xfer of transactions[0].transfers) {
            let acc = testutils.toAccNum(xfer.account);
            if (acc > accLow && acc < accHigh) {
                check = true;
            }
        }
        expect(check).toBeTruthy();

        let next = JSON.parse(response.text).links.next;
        expect(next).not.toBe(undefined);
    });

    test('Get transactions with order parameters', async () => {
        const url = `${globals.apiPrefix}/transactions` +
            `?order=asc`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        expect(transactions.length).toEqual(config.limits.RESPONSE_ROWS);
        let check = true;
        let prevSeconds = 0;
        for (let txn of transactions) {
            if (testutils.secNsToSeconds(txn.seconds) < prevSeconds) {
                check = false;
            }
            prevSeconds = testutils.secNsToSeconds(txn.seconds);
        }
        expect(check).toBeTruthy();
    });

    test('Get transactions with result type = fail', async () => {
        const url = `${globals.apiPrefix}/transactions` +
            `?result=fail`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;
        let check = true;
        for (let txn of transactions) {
            if (txn.result == 'SUCCESS') {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });

    test('Get transactions with all parameters', async () => {
        let accLow = Math.max(0, Number(globals.testAccounts.highest) - 100);
        let accHigh = Number(globals.testAccounts.highest);
        let tsLow = globals.testTx[globals.testTx.length - 1].consensus_timestamp;
        let tsHigh = globals.testTx[0].consensus_timestamp;
        const url = `${globals.apiPrefix}/transactions` +
            `?timestamp=gte:${tsLow}&timestamp=lte:${tsHigh}` +
            `&account.id=gt:${accLow}&account.id=lt:${accHigh}` +
            `&result=success&type=credit&order=desc&limit=${config.limits.RESPONSE_ROWS}`;
        debuglog(url);
        const response = await request(server).get(url);
        expect(response.status).toEqual(200);
    });

    test('Get transactions with pagination', async () => {
        // Validate that pagination works and that it doesn't have any gaps
        let paginatedEntries = [];
        const numPages = 5;
        const pageSize = config.limits.RESPONSE_ROWS / numPages;
        const firstTs = globals.testTx[0].consensus_timestamp;

        let next = null;
        for (let index = 0; index < numPages; index++) {
            const nextUrl = paginatedEntries.length === 0 ?
                `${globals.apiPrefix}/transactions?timestamp=lte:${firstTs}&limit=${pageSize}` :
                next;
            debuglog("Nexturl: " + nextUrl);
            response = await request(server).get(nextUrl);
            expect(response.status).toEqual(200);
            let chunk = JSON.parse(response.text).transactions;
            expect(chunk.length).toEqual(pageSize);
            paginatedEntries = paginatedEntries.concat(chunk);

            next = JSON.parse(response.text).links.next;
            expect(next).not.toBe(null);
        }

        check = (paginatedEntries.length === globals.testTx.length);
        expect(check).toBeTruthy();

        check = true;
        for (i = 0; i < config.limits.RESPONSE_ROWS; i++) {
            if (globals.testTx[i].transaction_id !== paginatedEntries[i].transaction_id ||
                globals.testTx[i].consensus_timestamp !== paginatedEntries[i].consensus_timestamp) {
                check = false;
            }
        }
        expect(check).toBeTruthy();
    });
});