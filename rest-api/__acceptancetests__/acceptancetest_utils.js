const math = require('mathjs');
const config = require('../config.js');

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

module.exports = {
    nsToSecNs: nsToSecNs,
    secNsToNs: secNsToNs,
    toAccNum: toAccNum,
    fromAccNum: fromAccNum,
    secNsToSeconds: secNsToSeconds
}
