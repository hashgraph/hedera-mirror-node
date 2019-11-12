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

var acceptanceTestsTransactions = (function () {

    const request = require('supertest');
    const math = require('mathjs');
    const server = process.env.TARGET;
    const acctestutils = require('./acceptancetest_utils.js');
    const config = require('../config.js');
    const maxLimit = config.api.maxLimit;

    beforeAll(async () => {
        moduleVars.verbose && console.log('Jest starting!');
        jest.setTimeout(20000);

        if (process.env.verbose != undefined && process.env.verbose == 1) {
            moduleVars.verbose = true;
        }
    ;
        const result = await setModuleVars();
        if (!result) {
            return(new Promise(function(resolve, reject) {
                reject('Failed to successfully initialize test with /transactions API\n' +
                    'All other tests in this test suit will fail as a result');
            }));
        }
    });

    afterAll(() => {});

    let moduleVars = {
        apiPrefix: '/api/v1',
        verbose: false
    };

    // Make a preliminary query to /transactions and get the list of accounts. The values
    // returned by this query are used to populate the subsequent queries for other tests in
    // this file. For example, an account number returned by this query is used to
    // make a subsequent query for a specific account (/transactions?account.id=xxx).
    // This allows the acceptance tests to run against any network (testnet, mainnet) by
    // dynamically discovering account numbers, balances, transactions, etc to avoid
    // hardcoding.
    const setModuleVars = async function () {
        const response = await request(server).get(moduleVars.apiPrefix + '/transactions');
        expect(response.status).toEqual(200);
        let transactions = JSON.parse(response.text).transactions;

        expect(transactions.length).toBe(maxLimit);
        if (transactions.length !== maxLimit) {
            return (false);
        }

        moduleVars.testAccounts = {
            num: null,
            highest: 0
        };

        for (let xfer of transactions[0].transfers) {
            if (xfer.amount > 0) {
                moduleVars.testAccounts.num = acctestutils.toAccNum(xfer.account);
                if (acctestutils.toAccNum(xfer.account) > moduleVars.testAccounts.highest) {
                    moduleVars.testAccounts.highest = acctestutils.toAccNum(xfer.account);
                }
            }
        }
        moduleVars.testTx = transactions;

        expect(moduleVars.testAccounts.num).toBeDefined();
        expect(moduleVars.testTx[0].consensus_timestamp).toBeDefined();

        return (true);
    }

    describe('Acceptance tests - transactions', () => {
        test('Get transactions with limit parameters', async () => {
            const response = await request(server).get(moduleVars.apiPrefix + '/transactions?limit=10');
            expect(response.status).toEqual(200);
            let transactions = JSON.parse(response.text).transactions;
            expect(transactions.length).toEqual(10);
        });

        test('Get transactions with account id parameters', async () => {
            const url = `${moduleVars.apiPrefix}/transactions` +
                `?account.id=${moduleVars.testAccounts.highest}&type=credit&limit=1`;
            moduleVars.verbose && console.log(url);
     console.log (url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let transactions = JSON.parse(response.text).transactions;
            expect(transactions.length).toEqual(1);
            let check = false;
            for (let xfer of transactions[0].transfers) {
                if (acctestutils.toAccNum(xfer.account) === moduleVars.testAccounts.highest) {
                    check = true;
                }
            }
            expect(check).toBeTruthy();
        });

        test('Get transactions with account id range parameters', async () => {
            let accLow = Math.max(0, Number(moduleVars.testAccounts.highest) - 100);
            let accHigh = Number(moduleVars.testAccounts.highest);
            const url = `${moduleVars.apiPrefix}/transactions` +
                `?account.id=gt:${accLow}&account.id=lt:${accHigh}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let transactions = JSON.parse(response.text).transactions;
            expect(transactions.length).toEqual(maxLimit);
            let check = false;
            for (let xfer of transactions[0].transfers) {
                let acc = acctestutils.toAccNum(xfer.account);
                if (acc > accLow && acc < accHigh) {
                    check = true;
                }
            }
            expect(check).toBeTruthy();

            let next = JSON.parse(response.text).links.next;
            expect(next).not.toBe(undefined);
        });

        test('Get transactions with order parameters', async () => {
            const url = `${moduleVars.apiPrefix}/transactions` +
                `?order=asc`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let transactions = JSON.parse(response.text).transactions;
            expect(transactions.length).toEqual(maxLimit);
            let check = true;
            let prevSeconds = 0;
            for (let txn of transactions) {
                if (acctestutils.secNsToSeconds(txn.seconds) < prevSeconds) {
                    check = false;
                }
                prevSeconds = acctestutils.secNsToSeconds(txn.seconds);
            }
            expect(check).toBeTruthy();
        });

        // TODO: Enable this test after the transactions table restructuring
        test.skip('Get transactions with result type = fail', async () => {
            const url = `${moduleVars.apiPrefix}/transactions` +
                `?result=fail`;
            moduleVars.verbose && console.log(url);
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
            let accLow = Math.max(0, Number(moduleVars.testAccounts.highest) - 100);
            let accHigh = Number(moduleVars.testAccounts.highest);
            let tsLow = moduleVars.testTx[moduleVars.testTx.length - 1].consensus_timestamp;
            let tsHigh = moduleVars.testTx[0].consensus_timestamp;
            const url = `${moduleVars.apiPrefix}/transactions` +
                `?timestamp=gte:${tsLow}&timestamp=lte:${tsHigh}` +
                `&account.id=gt:${accLow}&account.id=lt:${accHigh}` +
                `&result=success&type=credit&order=desc&limit=${maxLimit}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
        });

        test('Get transactions with pagination', async () => {
            // Validate that pagination works and that it doesn't have any gaps
            // In setModuleVars function, we did a /tranasctions query and stored the results of that query in the
            // moduleVars.testTx variable. This is expected to be RESPONSE_ROWS (currently 1000) long.
            // We will now try to fetch the same entries using five 200-entry pages using the 'next' links
            // After that, we will concatenate these 5 pages and compare the entries with the original response
            // of 1000 entries to see if there is any overlap or gaps in the returned responses.
            let paginatedEntries = [];
            const numPages = 5;
            const pageSize = maxLimit / numPages;
            const firstTs = moduleVars.testTx[0].consensus_timestamp;

            let next = null;
            for (let index = 0; index < numPages; index++) {
                const nextUrl = paginatedEntries.length === 0 ?
                    `${moduleVars.apiPrefix}/transactions?timestamp=lte:${firstTs}&limit=${pageSize}` :
                    next;
                moduleVars.verbose && console.log(`Nexturl: ${nextUrl}`);
                response = await request(server).get(nextUrl);
                expect(response.status).toEqual(200);
                let chunk = JSON.parse(response.text).transactions;
                expect(chunk.length).toEqual(pageSize);
                paginatedEntries = paginatedEntries.concat(chunk);

                next = JSON.parse(response.text).links.next;
                expect(next).not.toBe(null);
            }

            // We have concatenated set of pages obtained using the 'next' links
            // Check if the length matches the original response
            check = (paginatedEntries.length === moduleVars.testTx.length);
            expect(check).toBeTruthy();

            // Check if the transaction-ids and conesnsus-timestamps match for each entry
            check = true;
            for (i = 0; i < maxLimit; i++) {
                if (moduleVars.testTx[i].transaction_id !== paginatedEntries[i].transaction_id ||
                    moduleVars.testTx[i].consensus_timestamp !== paginatedEntries[i].consensus_timestamp) {
                    check = false;
                }
            }
            expect(check).toBeTruthy();
        });
    });
})();
