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

var acceptanceTestsAccounts = (function () {
    const request = require('supertest');
    const math = require('mathjs');
    const server = process.env.TARGET;
    const acctestutils = require('./acceptancetest_utils.js');
    const config = require('../config.js');

    beforeAll(async () => {
        moduleVars.verbose && console.log('Jest starting!');
        jest.setTimeout(20000);

        if (process.env.verbose != undefined && process.env.verbose == 1) {
            moduleVars.verbose = true;
        }

        const result = await setModuleVars();
        if (!result) {
            return(new Promise(function(resolve, reject) {
                reject('Failed to successfully initialize test with /accounts API\n' +
                    'All other tests in this test suit will fail as a result');
            }));
        }
    });

    afterAll(() => {});

    let moduleVars = {
        apiPrefix: '/api/v1',
        verbose: false
    };

    // Make a preliminary query to /accounts and get the list of accounts. The values 
    // returned by this query are used to populate the subsequent queries for other tests in 
    // this file. For example, an account number returned by this query is used to 
    // make a subsequent query for a specific account (/accounts?account.id=xxx).
    // This allows the acceptance tests to run against any network (testnet, mainnet) by
    // dynamically discovering account numbers, balances, transactions, etc to avoid 
    // hardcoding.
    const setModuleVars = async function () {
        const response = await request(server).get(moduleVars.apiPrefix + '/accounts?order=desc');
        expect(response.status).toEqual(200);
        let accounts = JSON.parse(response.text).accounts;

        expect(accounts.length).toBe(config.api.limits.responseRows);

        if (accounts.length !== config.api.limits.responseRows) {
            return (false);
        }
        moduleVars.testAccounts = {
            num: acctestutils.toAccNum(accounts[0].account),
            highest: 0,
            nonZeroBalance: null,
            publicKey: null
        };

        for (let acc of accounts) {
            if (acctestutils.toAccNum(acc.account) > moduleVars.testAccounts.highest) {
                moduleVars.testAccounts.highest = acctestutils.toAccNum(acc.account);
            }
            if (acc.balance.balance > 0) {
                moduleVars.testAccounts.nonZeroBalance = acc;
            }
            if (acc.key != null && 
                acc.key._type == 'ED25519' &&
                acc.key.key != null) {
                moduleVars.publicKey = acc.key.key;
            }
        }
        moduleVars.testAcc = accounts;
        moduleVars.testTimestamp = accounts[0].balance.timestamp;

        expect(moduleVars.testAccounts.num).toBeDefined();
        expect(moduleVars.testTimestamp).toBeDefined();
        if (moduleVars.testAccounts.num == undefined ||
            moduleVars.testTimestamp == undefined) {
            return (false);
        }

        return (true);
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

    describe('Monitoring tests - accounts', () => {
        test('Get accounts with no parameters', async () => {
            const response = await request(server).get(moduleVars.apiPrefix + '/accounts');
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toBe(config.api.limits.responseRows);

            // Assert that all mandatory fields are present in the response
            let check = checkMandatoryParams(accounts[0]);
            expect(check).toBeTruthy();
        });

        test('Get accounts with timestamp & limit parameters', async () => {
            let plusOne = math.add(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            let minusOne = math.subtract(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            const url = `${moduleVars.apiPrefix}/accounts` +
                `?timestamp=gt:${minusOne.toString()}` +
                `&timestamp=lt:${plusOne.toString()}&limit=1`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toEqual(1);
        });

        test('Get accounts for a single account', async () => {
            const url = `${moduleVars.apiPrefix}/accounts` +
                `/${acctestutils.fromAccNum(moduleVars.testAccounts.highest)}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text);
            expect(accounts.account).toEqual(acctestutils.fromAccNum(moduleVars.testAccounts.highest));

            // Assert that all mandatory fields are present in the response
            let check = checkMandatoryParams(accounts);
            expect(check).toBeTruthy();
        });
    });

    describe('Acceptance tests - accounts', () => {
        test('Get accounts with limit parameters', async () => {
            const response = await request(server).get(moduleVars.apiPrefix + '/accounts?limit=10');
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toEqual(10);
        });

        test('Get accounts with account id parameters', async () => {
            const url = `${moduleVars.apiPrefix}/accounts` +
                `?account.id=${acctestutils.fromAccNum(moduleVars.testAccounts.highest)}` +
                `&limit=1`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toEqual(1);
            expect(moduleVars.testAccounts.highest).toBe(acctestutils.toAccNum(accounts[0].account));
        });

        test('Get accounts with account id range parameters', async () => {
            let accLow = Math.max(0, Number(moduleVars.testAccounts.highest) - 100);
            let accHigh = Number(moduleVars.testAccounts.highest);
            const url = `${moduleVars.apiPrefix}/accounts` +
                `?account.id=gt:${acctestutils.fromAccNum(accLow)}` +
                `&account.id=lt:${acctestutils.fromAccNum(accHigh)}`;
            moduleVars.verbose && console.log(url);
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
            let balLow = Number(moduleVars.testAccounts.nonZeroBalance.balance.balance) - 1;
            let balHigh = Number(moduleVars.testAccounts.nonZeroBalance.balance.balance) + 1;
            const url = `${moduleVars.apiPrefix}/accounts` +
                `?account.balance=gt:${balLow}&account.balance=lt:${balHigh}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toBeGreaterThanOrEqual(1);
        });

        test('Get accounts with account key parameters', async () => {
            if (moduleVars.publicKey != null) {
                const url = `${moduleVars.apiPrefix}/accounts` +
                    `?account.publickey=${moduleVars.publicKey}`;
                moduleVars.verbose && console.log(url);
                const response = await request(server).get(url);
                expect(response.status).toEqual(200);
                let accounts = JSON.parse(response.text).accounts;
                expect(accounts.length).toBeGreaterThanOrEqual(1);
            }
        });

        test('Get accounts with order parameters', async () => {
            const url = `${moduleVars.apiPrefix}/accounts` +
                `?order=desc`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let accounts = JSON.parse(response.text).accounts;
            expect(accounts.length).toEqual(config.api.limits.responseRows);
            let check = true;
            let prevAcc = Number.MAX_SAFE_INTEGER;
            for (let acc of accounts) {
                if (acctestutils.toAccNum(acc.account) >= prevAcc) {
                    check = false;
                }
                prevAcc = acctestutils.toAccNum(acc.account);
            }
            expect(check).toBeTruthy();
        });

        test('Get accounts with pagination', async () => {
            // Validate that pagination works and that it doesn't have any gaps
            // In setModuleVars function, we did an /accounts query and stored the results of that query in the 
            // moduleVars.testAcc variable. This is expected to be RESPONSE_ROWS (currently 1000) long.
            // We will now try to fetch the same entries using five 200-entry pages using the 'next' links
            // After that, we will concatenate these 5 pages and compare the entries with the original response
            // of 1000 entries to see if there is any overlap or gaps in the returned responses.           
            let paginatedEntries = [];
            const numPages = 5;
            const pageSize = config.api.limits.responseRows / numPages;
            let next = null;
            for (let index = 0; index < numPages; index++) {
                const nextUrl = paginatedEntries.length === 0 ?
                    `${moduleVars.apiPrefix}/accounts?timestamp=lte:${moduleVars.testTimestamp}&order=desc&limit=${pageSize}` :
                    next;
                moduleVars.verbose && console.log(`Nexturl: ${nextUrl}`);
                response = await request(server).get(nextUrl);
                expect(response.status).toEqual(200);
                let chunk = JSON.parse(response.text).accounts;
                expect(chunk.length).toEqual(pageSize);
                paginatedEntries = paginatedEntries.concat(chunk);

                next = JSON.parse(response.text).links.next;
                expect(next).not.toBe(null);
            }

            // We have concatenated set of pages obtained using the 'next' links
            // Check if the length matches the original response 
            check = (paginatedEntries.length === moduleVars.testAcc.length);
            expect(check).toBeTruthy();

            // Check if the accounts numbers match for each entry
            check = true;
            for (i = 0; i < config.api.limits.responseRows; i++) {
                if (moduleVars.testAcc[i].account !== paginatedEntries[i].account) {
                    check = false;
                }
            }
            expect(check).toBeTruthy();
        });
    });
})();
