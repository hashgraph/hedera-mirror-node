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

const acctestutils = require('./fetchtest_utils.js');
const config = require('../../config.js');
const math = require('mathjs');
const accountsPath= '/accounts';
const maxLimit = config.api.maxLimit;

let classResults = null;
let server = undefined;

/**
 * Makes a call to the rest-api and returns the accounts object from the response
 * @param {String} pathandquery 
 * @return {Object} Accounts object from response
 */
const getAccounts = async function(pathandquery) {
    try {
        const json = await acctestutils.getAPIResponse(pathandquery);
        return json.accounts;
    } catch (error) {
        console.log(error);
    }
}

/**
 * Add provided result to list of class results
 * Also increment passed and failed count based
 * @param {Object} res Test result
 * @param {Boolean} passed Test passed flag
 */
const addTestResult = function(res, passed) {
    classResults.testResults.push(res);
    passed ? classResults.numPassedTests++ : classResults.numFailedTests++;
}

/**
 * Check the required fields exist in the response object
 * @param {Object} entry Account JSON object
 */
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

/**
 * Verify base accounts call 
 * Also ensure an account mentioned in the accounts can be confirmed as exisitng
 */
const getAccountsWithAccountCheck = async function() {
    var currentTestResult = acctestutils.getMonitorTestResult();
    
    let url = acctestutils.getUrl(server, accountsPath);
    currentTestResult.url = url;
    let accounts = await getAccounts(url); 

    if (accounts.length !== maxLimit) {
        var message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
        currentTestResult.failureMessages.push(message)
        return currentTestResult;
    }

    let mandatoryParamCheck = checkMandatoryParams(accounts[0]);
    if (mandatoryParamCheck == false) {
        var message = `account object is missing some mandatory fields`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }
    
    var highestAcc = 0
    for (let acc of accounts) {
        var accnum = acctestutils.toAccNum(acc.account)
        if (accnum > highestAcc) {
            highestAcc = accnum;
        }
    }

    url = acctestutils.getUrl(server, `${accountsPath}?account.id=${highestAcc}&type=credit&limit=1`);
    currentTestResult.url = url;

    let singleAccount = await getAccounts(url); 

    if (singleAccount.length !== 1) {
        var message = `singleAccount.length of ${singleAccount.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return
    }

    let check = false;
    if (singleAccount[0].account === acctestutils.fromAccNum(highestAcc)) {
        check = true;
    }

    if (check == false) {
        var message = `Highest acc check was not found`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called accounts and performed account check`

    addTestResult(currentTestResult, true);
}

/**
 * Verify accounts call with time and limit query params provided
 */
const getAccountsWithTimeAndLimitParams = async function (json) {
    var currentTestResult = acctestutils.getMonitorTestResult();

    let url = acctestutils.getUrl(server, `${accountsPath}?limit=1`);
    currentTestResult.url = url;
    let accounts = await getAccounts(url); 

    if (accounts.length !== 1) {
        var message = `accounts.length of ${accounts.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let plusOne = math.add(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
    let minusOne = math.subtract(math.bignumber(accounts[0].balance.timestamp), math.bignumber(1));
    let paq = `${accountsPath}?timestamp=gt:${minusOne.toString()}` +
                `&timestamp=lt:${plusOne.toString()}&limit=1`;

    url = acctestutils.getUrl(server, paq);
    currentTestResult.url = url;
    accounts = await getAccounts(url);

    if (accounts.length !== 1) {
        var message = `accounts.length of ${accounts.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called accounts with time and limit params`

    addTestResult(currentTestResult, true);
}

/**
 * Verify single account can be retrieved
 */
const getSingleAccount = async function() {
    var currentTestResult = acctestutils.getMonitorTestResult();
    
    let url = acctestutils.getUrl(server, `${accountsPath}`);
    currentTestResult.url = url;
    let accounts = await getAccounts(url);  

    if (accounts.length !== maxLimit) {
        var message = `accounts.length of ${accounts.length} is less than limit ${maxLimit}`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let mandatoryParamCheck = checkMandatoryParams(accounts[0]);
    if (mandatoryParamCheck == false) {
        var message = `account object is missing some mandatory fields`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }
    
    var highestAcc = 0
    for (let acc of accounts) {
        var accnum = acctestutils.toAccNum(acc.account)
        if (accnum > highestAcc) {
            highestAcc = accnum;
        }
    }

    url = acctestutils.getUrl(server, `${accountsPath}/${acctestutils.fromAccNum(highestAcc)}`);
    currentTestResult.url = url;

    let singleAccount = await acctestutils.getAPIResponse(url); 

    let check = false;
    if (singleAccount.account === acctestutils.fromAccNum(highestAcc)) {
        check = true;
    }

    if (check == false) {
        var message = `Highest acc check was not found`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called accounts and performed account check`

    addTestResult(currentTestResult, true);
}

/**
 * Run all tests in an asynchronous fashion waiting for all tests to complete before calculating class success
 */
async function runTests() {
    var tests = [];
    tests.push(getAccountsWithAccountCheck());
    tests.push(getAccountsWithTimeAndLimitParams());
    tests.push(getSingleAccount());

    await Promise.all(tests);

    if (classResults.numPassedTests == classResults.testResults.length) {
        classResults.success = true
    }
}

/**
 * Coordinates tests run. 
 * Creating and returning a new classresults object representings accounts tests
 */
const runAccountTests = function(svr) {
    server = svr;
    classResults = acctestutils.getMonitorClassResult();

    return runTests().then(() => {
        return classResults;
    });
}

module.exports = {
    runAccountTests: runAccountTests
}