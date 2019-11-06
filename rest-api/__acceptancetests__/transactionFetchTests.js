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

const acctestutils = require('./acceptancetest_utils.js');
const config = require('../config.js');
const math = require('mathjs');
const transactionsPath= '/transactions';
const maxLimit = config.api.maxLimit;

let classResults = {
    startTime: null,
    testResults: [],
    numPassedTests: 0,
    numFailedTests: 0,
    success: false,
    message: ''
}

let testResult = {
    at: '',
    result: 'failed',
    url: '',
    message: '',
    failureMessages: []
}

const getTransactions = async function(pathandquery) {
    try {
        const json = await acctestutils.getAPIResponse(pathandquery);
        return json.transactions;
    } catch (error) {
        console.log(error);
    }
}

const addTestResult = function(res, passed) {
    classResults.testResults.push(res);
    passed ? classResults.numPassedTests++ : classResults.numFailedTests++;
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

const getTransactionsWithAccountCheck = async function() {
    var currentTestResult = acctestutils.cloneObject(testResult);
    currentTestResult.at = Date.now();
    
    let url = acctestutils.getUrl(transactionsPath);
    currentTestResult.url = url;
    let transactions = await getTransactions(url); 

    if (transactions.length !== maxLimit) {
        var message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let mandatoryParamCheck = checkMandatoryParams(transactions[0]);
    if (mandatoryParamCheck == false) {
        var message = `transaction object is missing some mandatory fields`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }
    
    var accNum = 0
    var highestAcc = 0
    for (let xfer of transactions[0].transfers) {
        if (xfer.amount > 0) {
            accNum = acctestutils.toAccNum(xfer.account);
            if (accNum > highestAcc) {
                highestAcc = accNum;
            }
        }
    }

    if (accNum === 0) {
        var message = `accNum is 0`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    if (undefined === transactions[0].consensus_timestamp) {
        var message = `transactions[0].consensus_timestamp is undefined`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    url = acctestutils.getUrl(`${transactionsPath}?account.id=${highestAcc}&type=credit&limit=1`);
    currentTestResult.url = url;

    let accTransactions = await getTransactions(url); 
    if (accTransactions.length !== 1) {
        var message = `accTransactions.length of ${transactions.length} is not 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let check = false;
    for (let xfer of accTransactions[0].transfers) {
        if (acctestutils.toAccNum(xfer.account) === highestAcc) {
            check = true;
        }
    }

    if (check == false) {
        var message = `Highest acc check was not found`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called transactions and performed account check`

    addTestResult(currentTestResult, true);
}

const getTransactionsWithOrderParam = async function() {
    var currentTestResult = acctestutils.cloneObject(testResult);
    currentTestResult.at = Date.now();
    
    let url = acctestutils.getUrl(`${transactionsPath}?order=asc`);
    currentTestResult.url = url;
    let transactions = await getTransactions(url); 
    
    if (transactions.length !== maxLimit) {
        var message = `transactions.length of ${transactions.length} is less than limit ${maxLimit}`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let prevSeconds = 0;
    for (let txn of transactions) {
        if (acctestutils.secNsToSeconds(txn.seconds) < prevSeconds) {
            check = false;
        }
        prevSeconds = acctestutils.secNsToSeconds(txn.seconds);
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called transactions with order params only`

    addTestResult(currentTestResult, true);
}

const getTransactionsWithLimitParams = async function () {
    var currentTestResult = acctestutils.cloneObject(testResult);
    currentTestResult.at = Date.now();

    let url = acctestutils.getUrl(`${transactionsPath}?limit=10`);
    currentTestResult.url = url;
    let transactions = await getTransactions(url); 
    
    if (transactions.length !== 10) {
        var message = `transactions.length of ${transactions.length} was expected to be 10`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called transactions with limit params only`

    addTestResult(currentTestResult, true);
}

const getTransactionsWithTimeAndLimitParams = async function (json) {
    var currentTestResult = acctestutils.cloneObject(testResult);
    currentTestResult.at = Date.now();

    let url = acctestutils.getUrl(`${transactionsPath}?limit=1`);
    currentTestResult.url = url;
    let transactions = await getTransactions(url);

    if (transactions.length !== 1) {
        var message = `transactions.length of ${transactions.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let plusOne = math.add(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
    let minusOne = math.subtract(math.bignumber(transactions[0].consensus_timestamp), math.bignumber(1));
    let paq = `${transactionsPath}?timestamp=gt:${minusOne.toString()}` +
                `&timestamp=lt:${plusOne.toString()}&limit=1`;

    url = acctestutils.getUrl(paq);
    transactions = await getTransactions(url);

    if (transactions.length !== 1) {
        var message = `transactions.length of ${transactions.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully called transactions with time and limit params`

    addTestResult(currentTestResult, true);
}

const getSingleTransactionsById = async function() {
    var currentTestResult = acctestutils.cloneObject(testResult);
    currentTestResult.at = Date.now();
    
    let url = acctestutils.getUrl(`${transactionsPath}?limit=1`);
    currentTestResult.url = url;
    let transactions = await getTransactions(url);

    if (transactions.length !== 1) {
        var message = `transactions.length of ${transactions.length} was expected to be 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    let mandatoryParamCheck = checkMandatoryParams(transactions[0]);
    if (mandatoryParamCheck == false) {
        var message = `transaction object is missing some mandatory fields`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    url = acctestutils.getUrl(`${transactionsPath}/${transactions[0].transaction_id}`);
    currentTestResult.url = url;

    let singleTransactions = await getTransactions(url); 
    if (singleTransactions.length !== 1) {
        var message = `singleTransactions.length of ${transactions.length} is not 1`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    mandatoryParamCheck = checkMandatoryParams(transactions[0]);
    if (mandatoryParamCheck == false) {
        var message = `single transaction object is missing some mandatory fields`;
        currentTestResult.failureMessages.push(message);
        addTestResult(currentTestResult, false);
        return;
    }

    currentTestResult.result = 'passed';
    currentTestResult.message = `Successfully retrieved single transactions by id`

    addTestResult(currentTestResult, true);
}

async function runTests() {
    var tests = [];
    tests.push(getTransactionsWithAccountCheck());
    tests.push(getTransactionsWithOrderParam());
    tests.push(getTransactionsWithLimitParams());
    tests.push(getTransactionsWithTimeAndLimitParams());
    tests.push(getSingleTransactionsById());

    await Promise.all(tests);

    if (classResults.numPassedTests == classResults.testResults.length) {
        classResults.success = true
    }
}

const runTransactionTests = function() {
    classResults.startTime = Date.now();

    return runTests().then(() => {
        return classResults;
    });
}

module.exports = {
    runTransactionTests: runTransactionTests
}