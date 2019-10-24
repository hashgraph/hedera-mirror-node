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
var acceptanceTestsBalances = (function () {
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
                reject('Failed to successfully initialize test with /balances API\n' +
                    'All other tests in this test suit will fail as a result');
            }));
        }
    });

    afterAll(() => {});

    let moduleVars = {
        apiPrefix: '/api/v1',
        verbose: false
    };

    // Make a preliminary query to /balances and get the list of accounts. The values 
    // returned by this query are used to populate the subsequent queries for other tests in 
    // this file. For example, an account number returned by this query is used to 
    // make a subsequent query for a specific account (/balances?account.id=xxx).
    // This allows the acceptance tests to run against any network (testnet, mainnet) by
    // dynamically discovering account numbers, balances, transactions, etc to avoid 
    // hardcoding.
    const setModuleVars = async function () {
        const response = await request(server).get(moduleVars.apiPrefix + '/balances');
        expect(response.status).toEqual(200);
        let balances = JSON.parse(response.text).balances;

        expect(balances.length).toBe(config.api.limits.responseRows);
        if (balances.length !== config.api.limits.responseRows) {
            return (false);
        }

        moduleVars.testAccounts = {
            num: acctestutils.toAccNum(balances[0].account),
            highest: 0,
            nonZeroBalance: null
        };
        for (let bal of balances) {
            if (acctestutils.toAccNum(bal.account) > moduleVars.testAccounts.highest) {
                moduleVars.testAccounts.highest = acctestutils.toAccNum(bal.account);
            }
            if (Number(bal.balance) > 0) {
                moduleVars.testAccounts.nonZeroBalance = bal;
            }
        }
        moduleVars.testBalances = balances;
        moduleVars.testTimestamp = JSON.parse(response.text).timestamp;

        expect(moduleVars.testAccounts.num).toBeDefined();
        expect(moduleVars.testTimestamp).toBeDefined();

        return (true);
    }

    const checkMandatoryParams = function (entry) {
        let check = true;
        ['account', 'balance'].forEach((field) => {
            check = check && entry.hasOwnProperty(field);
        });

        return (check);
    }

    describe('Monitoring tests - balances', () => {
        test('Get balances with no parameters', async () => {
            const response = await request(server).get(moduleVars.apiPrefix + '/balances');
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toBe(config.api.limits.responseRows);

            // Assert that all mandatory fields are present in the response
            let check = checkMandatoryParams(balances[0]);
            expect(check).toBeTruthy();

            // Check for freshness of data
            const balancesSec = JSON.parse(response.text).timestamp.split('.')[0];
            const currSec = Math.floor(new Date().getTime() / 1000);
            const delta = currSec - balancesSec;
            check = delta < (2 * config.api.fileUpdateRefreshTimes.balances)
            expect(check).toBeTruthy();
        });

        test('Get balances with timestamp & limit parameters', async () => {
            let plusOne = math.add(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            let minusOne = math.subtract(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            const url = `${moduleVars.apiPrefix}/balances` +
                `?timestamp=gt:${minusOne.toString()}` +
                `&timestamp=lt:${plusOne.toString()}&limit=1`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toEqual(1);
        });

        test('Get balances with account id parameters', async () => {
            const url = `${moduleVars.apiPrefix}/balances` +
                `?account.id=${acctestutils.fromAccNum(moduleVars.testAccounts.highest)}` +
                `&limit=1`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toEqual(1);
            expect(moduleVars.testAccounts.highest).toBe(acctestutils.toAccNum(balances[0].account));
        });
    });

    describe('Acceptance tests - balances', () => {
        test('Get balances with limit parameters', async () => {
            const response = await request(server).get(moduleVars.apiPrefix + '/balances?limit=10');
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toEqual(10);
        });

        test('Get balances with account id range parameters', async () => {
            let accLow = Math.max(0, Number(moduleVars.testAccounts.highest) - 100);
            let accHigh = Number(moduleVars.testAccounts.highest);
            const url = `${moduleVars.apiPrefix}/balances` +
                `?account.id=gt:${acctestutils.fromAccNum(accLow)}` +
                `&account.id=lt:${acctestutils.fromAccNum(accHigh)}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toBeGreaterThanOrEqual(1);
            let check = true;
            for (let bal of balances) {
                let acc = acctestutils.toAccNum(bal.account);
                if (acc < accLow || acc > accHigh) {
                    check = false;
                }
            }
            expect(check).toBeTruthy();
        });

        test('Get balances with account balance range parameters', async () => {
            let balLow = Number(moduleVars.testAccounts.nonZeroBalance.balance) - 1;
            let balHigh = Number(moduleVars.testAccounts.nonZeroBalance.balance) + 1;
            const url = `${moduleVars.apiPrefix}/balances` +
                `?account.balance=gt:${balLow}&account.balance=lt:${balHigh}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toBeGreaterThanOrEqual(1);
        });

        test('Get balances with account key parameters', async () => {
            const url = `${moduleVars.apiPrefix}/balances` +
                `?account.publickey=1234567890123456789012345678901234567890123456789012345678901234`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toBe(0);
        });

        test('Get balances with order parameters', async () => {
            const url = `${moduleVars.apiPrefix}/balances` +
                `?order=asc`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
            let balances = JSON.parse(response.text).balances;
            expect(balances.length).toEqual(config.api.limits.responseRows);
            let check = true;
            let prevAcc = 0;
            for (let bal of balances) {
                if (acctestutils.toAccNum(bal.account) <= prevAcc) {
                    check = false;
                }
                prevAcc = acctestutils.toAccNum(bal.account);
            }
            expect(check).toBeTruthy();
        });

        test('Get balances with all parameters', async () => {
            let accLow = Math.max(0, Number(moduleVars.testAccounts.highest) - 100);
            let accHigh = Number(moduleVars.testAccounts.highest);
            let plusOne = math.add(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            let minusOne = math.subtract(math.bignumber(moduleVars.testTimestamp), math.bignumber(1));
            let balLow = Number(moduleVars.testAccounts.nonZeroBalance.balance) - 1;
            let balHigh = Number(moduleVars.testAccounts.nonZeroBalance.balance) + 1;
            const url = `${moduleVars.apiPrefix}/balances` +
                `?timestamp=gte:${minusOne}&timestamp=lte:${plusOne}` +
                `&account.id=gt:${accLow}&account.id=lt:${accHigh}` +
                `&account.balance=gt:${balLow}&account.balance=lt:${balHigh}` +
                `&account.publickey=1234567890123456789012345678901234567890123456789012345678901234` +
                `&order=desc&limit=${config.api.limits.responseRows}`;
            moduleVars.verbose && console.log(url);
            const response = await request(server).get(url);
            expect(response.status).toEqual(200);
        });

        test('Get balances with pagination', async () => {
            // Validate that pagination works and that it doesn't have any gaps
            // In setModuleVars function, we did a /balances query and stored the results of that query in the 
            // moduleVars.testBalances variable. This is expected to be RESPONSE_ROWS (currently 1000) long.
            // We will now try to fetch the same entries using five 200-entry pages using the 'next' links
            // After that, we will concatenate these 5 pages and compare the entries with the original response
            // of 1000 entries to see if there is any overlap or gaps in the returned responses.   
            let paginatedEntries = [];
            const numPages = 5;
            const pageSize = config.api.limits.responseRows / numPages;
            let next = null;
            // Fetch pages using 'next' links and concatenate the results
            for (let index = 0; index < numPages; index++) {
                const nextUrl = paginatedEntries.length === 0 ?
                    `${moduleVars.apiPrefix}/balances?timestamp=lte:${moduleVars.testTimestamp}&limit=${pageSize}` :
                    next;
                moduleVars.verbose && console.log("Nexturl: " + nextUrl);
                response = await request(server).get(nextUrl);
                expect(response.status).toEqual(200);
                let chunk = JSON.parse(response.text).balances;
                expect(chunk.length).toEqual(pageSize);
                paginatedEntries = paginatedEntries.concat(chunk);

                next = JSON.parse(response.text).links.next;
                expect(next).not.toBe(null);
            }

            // We have concatenated set of pages obtained using the 'next' links
            // Check if the length matches the original response 
            check = (paginatedEntries.length === moduleVars.testBalances.length);
            expect(check).toBeTruthy();

            // Check if the accounts numbers match for each entry
            check = true;
            for (i = 0; i < config.api.limits.responseRows; i++) {
                if (moduleVars.testBalances[i].account !== paginatedEntries[i].account) {
                    check = false;
                }
            }
            expect(check).toBeTruthy();
        });
    });

})();
