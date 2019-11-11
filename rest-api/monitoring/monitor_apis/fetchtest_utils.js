const math = require('mathjs');
const config = require('../../config.js');
const fetch = require('node-fetch');

const apiPrefix = '/api/v1';

// monitoring class results template
const classResults = {
    startTime: null,
    testResults: [],
    numPassedTests: 0,
    numFailedTests: 0,
    success: false,
    message: ''
}

// monitoring single test result template
const testResult = {
    at: '', // start time of test in millis since epoch
    result: 'failed', // result of test
    url: '', // last rest-api endpoint call made in test
    message: '', // test message
    failureMessages: [] // failure messages
}

/**
 * Converts nanoseconds since epoch to seconds.nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds.nnnnnnnnn format) 
 */
const nsToSecNs = function (ns) {
    return (math.divide(math.bignumber(ns), math.bignumber(1e9)).toFixed(9).toString());
}

/**
 * Converts seconds since epoch (seconds.nnnnnnnnn format) to  nanoseconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format) 
 * @return {String} ns Nanoseconds since epoch
 */
const secNsToNs = function (secNs) {
    return (math.multiply(math.bignumber(secNs), math.bignumber(1e9)).toString());
}

/**
 * Converts seconds.nanoseconds to seconds (floor)
 * @param {String} secNs Seconds.Nanoseconds
 * @return {Number} Seconds 
 */
const secNsToSeconds = function (secNs) {
    return (math.floor(Number(secNs)));
}

/**
 * Converts shard.realm.accountNumber to accountNumber
 * @param {String} shard.realm.accountNumber 
 * @return {Number} accountNumber 
 */
const toAccNum = (accId => Number(accId.split('.')[2]))

/**
 * Converts accountNumber to shard.realm.accountNumber string
 * @param {Number} accountNumber
 * @return {String} shard.realm.accountNumber
 */
const fromAccNum = (accNum => `${config.shard}.0.${accNum}`)

/**
 * Return a deep clone of a json object
 * @param {Object} obj 
 */
const cloneObject = function(obj) {
    return JSON.parse(JSON.stringify(obj));
}

/**
 * Create and return the url for a rest api call
 * If running on a local server http is employed over https 
 * @param {String} pathandquery rest-api endpoint path
 * @return {String} rest-api endpoint url
 */
const getUrl = function(server, pathandquery) {
    var endpoint = server;
    if (server.includes('localhost') || server.includes('127.0.0.1')) {
        endpoint = server.replace('https', 'http')
    }

    let url = `${endpoint}${apiPrefix}${pathandquery}`;
    return url;
}

/**
 * Make an http request to mirror-node api
 * Host info is prepended to if only path is provided
 * @param {*} url rest-api endpoint
 * @return {Object} JSON object representing api response
 */
const getAPIResponse = async function(url) {
    try {
        if (url.indexOf('/') === 0) {
            // if url is path get full url including host
            url = getUrl(url);
        }

        const response = await fetch(url);
        const json = await response.json();
        return json;
    } catch (error) {
        var message = `Fetch error, url : ${url}, error : ${error}`
        console.log(message);
        throw message;
    }
}

/**
 * Retrieve a new instance of the monitoring class results object
 */
const getMonitorClassResult = function() {
    var newClassResult =  cloneObject(classResults);
    newClassResult.startTime = Date.now();
    return newClassResult;
}

/**
 * Retrieve a new instance of the monitoring single test result object
 */
const getMonitorTestResult = function() {
    var newTestResult =  cloneObject(testResult);
    newTestResult.at = Date.now();
    return newTestResult;
}

module.exports = {
    nsToSecNs: nsToSecNs,
    secNsToNs: secNsToNs,
    toAccNum: toAccNum,
    fromAccNum: fromAccNum,
    secNsToSeconds: secNsToSeconds,
    getUrl: getUrl,
    cloneObject: cloneObject,
    getAPIResponse: getAPIResponse,
    getMonitorClassResult: getMonitorClassResult,
    getMonitorTestResult: getMonitorTestResult
}
