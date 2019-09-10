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
'use strict';

const request = require('supertest');
const server = require('../server');
const testutils = require('./testutils.js');

beforeAll(async () => {
    jest.setTimeout(1000);
});

afterAll(() => {
});

/**
 * This is the list of individual tests. Each test validates one query parameter
 * such as timestamp=1234 or account.id=gt:5678.
 * Definition of each test consists of the url string that is used in the query, and an
 * array of checks to be performed on the resultant SQL query. 
 * These individual tests can be combined to form complex combinations as shown in the
 * definition of combinedtests below.
 * NOTE: To add more tests, just give it a unique name, specifiy the url query string, and
 * a set of checks you would like to perform on the resultant SQL query.
 */
const singletests = {
    timestamp_lowerlimit: {
        urlparam: 'timestamp=gte:1234',
        checks: [
            {field: 'consensus_ns', operator: '>=', value: 1234 +'000000000'}
        ]
    },
    timestamp_higherlimit: {
        urlparam: 'timestamp=lt:5678',
        checks: [
            {field: 'consensus_ns', operator: '<', value: 5678 +'000000000'}
        ]
    },
    accountid_owerlimit: {
        urlparam: 'account.id=gte:0.0.1111',
        checks: [
            {field: 'entity_num', operator: '>=', value: 1111}
        ]
    },
    accountid_higherlimit: {
        urlparam: 'account.id=lt:0.0.2222',
        checks: [
            {field: 'entity_num', operator: '<', value: 2222},
        ]
    },
    accountid_equal: {
        urlparam: 'account.id=0.0.3333',
        checks: [
            {field: 'entity_num', operator: '=', value: 3333}
        ]
    },
    limit: {
        urlparam: 'limit=99',
        checks: [
            {field: 'limit', operator: '=', value: 99}
        ]
    },    
    order_asc: {
        urlparam: 'order=asc',
        checks: [
            {field: 'order', operator: '=', value: 'asc'}
        ]
    },
    result_fail: {
        urlparam: 'result=fail',
        checks: [
            {field: 'result', operator: '!=', value: 'SUCCESS'}
        ]
    },
    result_success: {
        urlparam: 'result=success',
        checks: [
            {field: 'result', operator: '=', value: 'SUCCESS'}
        ]
    }
};

/**
 * This list allows creation of combinations of individual tests to exercise presence
 * of mulitple query parameters. The combined query string is created by adding the query 
 * strings of each of the individual tests, and all checks from all of the individual tests
 * are performed on the resultant SQL query
 * NOTE: To add more combined tests, just add an entry to following array using the 
 * individual (single) tests in the object above.
 */
const combinedtests = [
    ['timestamp_lowerlimit', 'timestamp_higherlimit'],
    ['accountid_lowerlimit', 'accountid_higherlimit'],
    ['timestamp_lowerlimit', 'timestamp_higherlimit', 
        'accountid-lowerlimit', 'accountid_higherlimit'],
    ['timestamp_lowerlimit', 'accountid_higherlimit', 'limit'],
    ['timestamp_higherlimit', 'accountid_lowerlimit', 'result_fail'],
    ['limit', 'result_success', 'order_asc']
];

describe('Transaction tests', () => {
    let api = '/api/v1/transactions';

    // First, execute the single tests
    for (const [name, item] of Object.entries(singletests)) {
        test(`Transactions single test: ${name} - URL: ${item.urlparam}`, async () => {
            let response = await request(server).get([api, item.urlparam].join('?'));

            expect(response.status).toEqual(200);
            const parsedparams = JSON.parse(response.text).sqlQuery.parsedparams;

            // Verify the sql query against each of the specified checks
            let check = true;
            for (const checkitem of item.checks) {
                check = check && testutils.checkSql (parsedparams, checkitem);
            }
            expect (check).toBeTruthy();
        })
    }

    // And now, execute the combined tests
    for (const combination of combinedtests) {
        // Combine the individual (single) checks as specified in the combinedtests array
        let combtest = {urls: [], checks: [], names: ''};
        for (const testname of combination) {
            if (testname in singletests) {
                combtest.names += testname + ' ';
                combtest.urls.push(singletests[testname].urlparam);
                combtest.checks = combtest.checks.concat(singletests[testname].checks);
            }
        }
        const comburl = combtest.urls.join('&');

        test(`Transactions combinationn test: ${combtest.names} - URL: ${comburl}`, async () => {
            let response = await request(server).get([api, comburl].join('?'));
            expect(response.status).toEqual(200);
            const parsedparams = JSON.parse(response.text).sqlQuery.parsedparams;

            // Verify the sql query against each of the specified checks
            let check = true;
            for (const checkitem of combtest.checks) {
                check = check && testutils.checkSql (parsedparams, checkitem);
            }
            expect (check).toBeTruthy();
        })
    }
});
