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
"use strict";

const utils = require("./utils.js");
const config = require("./config.js");
const ed25519 = require("./ed25519.js");

/**
 * Parser for query parameters for mirror node rest apis
 */
class Parser {
    /**
     * Constructor
     * @param {HTTPRequest} req HTTP request object
     * @param {String} type Type of the request ('transactions', 'accounts', etc)
     * @return {Object} Parser instance
     */
    constructor(req, type) {
        this.req = req;

        this.limit = config.limits.RESPONSE_ROWS;
        this.order = "desc";

        if (["transactions", "balances", "accounts"].includes(type)) {
            this.parsedReq = this.validateAndParse();
        } else if ("oneTransaction" === type) {
            this.parsedReq = this.parseOneTransaction();
        } else if ("oneAccount" === type) {
            this.parsedReq = this.parseOneAccount();
        } else {
            logger.debug(`Parser: Invalid request type: ${type}`);
            return null;
        }
    }

    /**
     * Validate input http request object
     * @return {Object} result of validity check, and return http code/contents
     */
    validateAndParse() {
        let results = {
            isValid: true,
            code: utils.httpStatusCodes.OK,
            queryIntents: {},
            badParams: []
        };
        // Check the validity of every query parameter
        for (const key in this.req.query) {
            if (Array.isArray(this.req.query[key])) {
                for (const val of this.req.query[key]) {
                    const validResult = this.paramValidityChecks(key, val);
                    if (validResult.isValid) {
                        if (!results.queryIntents.hasOwnProperty(key)) {
                            results.queryIntents[key] = [];
                        }
                        results.queryIntents[key].push(validResult.queryIntents[key]);
                    } else {
                        results.isValid = false;
                        results.badParams.push({ message: `Invalid parameter: ${key}` });
                    }
                }
            } else {
                const validResult = this.paramValidityChecks(key, this.req.query[key]);
                if (validResult.isValid) {
                    results.queryIntents[key] = [validResult.queryIntents[key]];
                } else {
                    results.isValid = false;
                    results.badParams.push({ message: `Invalid parameter: ${key}` });
                }
            }
        }

        return results;
    }

    /**
     * Validate input parameters for the rest apis
     * @param {String} param Parameter to be validated
     * @param {String} opAndVal operator:value to be validated
     * @return {Object} The result of check (validity and the query intent)
     */
    paramValidityChecks(param, opAndVal) {
        let val = null;
        let op = null;
        let isValid = false;
        let originalVal = null;
        let result = {
            isValid: false,
            queryIntents: {}
        };

        if (opAndVal === undefined) {
            result.isValid = false;
            return result;
        }

        const splitVal = String(opAndVal).split(":");

        if (splitVal.length == 1) {
            op = "eq";
            val = splitVal[0];
        } else if (splitVal.length == 2) {
            op = splitVal[0];
            val = splitVal[1];
        } else {
            result.isValid = false;
            return result;
        }

        // Validate operator
        if (!/^(gte?|lte?|eq|ne)$/.test(op)) {
            result.isValid = false;
            return result;
        }

        // Validate the value
        switch (param) {
            case "account.id":
                // Accepted forms: shard.realm.num or num
                isValid = /^\d{1,10}\.\d{1,10}\.\d{1,10}$/.test(val) || /^\d{1,10}$/.test(val);
                if (isValid) {
                    originalVal = val;
                    val = this.parseEntityId(val);
                }
                break;
            case "timestamp":
                // Accepted forms: seconds or seconds.upto 9 digits
                isValid = /^\d{1,10}$/.test(val) || /^\d{1,10}\.\d{1,9}$/.test(val);
                if (isValid) {
                    originalVal = val;
                    val = this.parseTimestamp(val);
                }
                break;
            case "account.balance":
                // Accepted forms: Upto 50 billion
                isValid = /^\d{1,19}$/.test(val);
                break;
            case "account.publickey":
                // Acceptable forms: exactly 64 characters or +12 bytes (DER encoded)
                isValid = /^[0-9a-fA-F]{64}$/.test(val) || /^[0-9a-fA-F]{88}$/.test(val);
                isValid = isValid && op == "eq";
                if (isValid) {
                    // If the supplied key is DER encoded, decode it
                    const decodedKey = ed25519.derToEd25519(val);
                    if (decodedKey != null) {
                        val = decodedKey;
                    }
                    val = val.toLowerCase();
                }
                break;
            case "limit":
                // Acceptable forms: upto 4 digits
                isValid = /^\d{1,4}$/.test(val);
                if (isValid) {
                    this.limit = Number(val);
                }
                break;
            case "order":
                // Acceptable words: asc or desc
                isValid = ["asc", "desc"].includes(val);
                if (isValid) {
                    this.order = val;
                }
                break;
            case "type":
                // Acceptable words: credit or debig
                isValid = ["credit", "debit"].includes(val);
                break;
            case "result":
                // Acceptable words: success or fail
                isValid = ["success", "fail"].includes(val);
                break;
            default:
                // Every parameter should be included here. Otherwise, it will not be accepted.
                isValid = false;
        }
        result.isValid = isValid;

        if (isValid) {
            if (["limit", "account.balances"].includes(param)) {
                result.queryIntents[param] = { op: op, val: Number(val) };
            } else {
                result.queryIntents[param] = { op: op, val: val };
                if (originalVal) {
                    result.queryIntents[param].originalVal = originalVal;
                }
            }
        }
        return result;
    }

    /**
     * Getter for parsedReq
     * @return {Object} Output of the parsing of the http request
     */
    getParsedReq() {
        return this.parsedReq;
    }

    /**
     * Getter for limit
     * @return {Number} Limit
     */
    getLimit() {
        return this.limit;
    }

    /**
     * Getter for order
     * @return {String} Order
     */
    getOrder() {
        return limit.order;
    }

    /**
     * Parser for one transaction (/transactions/:id) api
     * @return {Object} result of validity check, and return http code/contents
     */
    parseOneTransaction() {
        let results = {
            isValid: true,
            code: utils.httpStatusCodes.OK,
            queryIntents: {},
            badParams: []
        };

        const txIdMatches = this.req.params.id.match(/^(\d+)\.(\d+)\.(\d+)-(\d{10})-(\d{9})/);
        if (txIdMatches === null || txIdMatches.length != 6) {
            logger.info(`parseOneTransaction: Invalid transaction id ${this.req.params.id}`);
            results.isValid = false;
            results.badParams.push({
                message:
                    'Invalid Transaction id. Please use "shard.realm.num-ssssssssss.nnnnnnnnn" ' +
                    "format where ssss are 10 digits seconds and nnn are 9 digits nanoseconds"
            });
            results.code = utils.httpStatusCodes.BAD_REQUEST;
        } else {
            results.queryIntents["account.id"] = [
                {
                    op: "=",
                    val: this.parseEntityId(`${txIdMatches[1]}.${txIdMatches[2]}.${txIdMatches[3]}`)
                }
            ];

            results.queryIntents["validstart_ns"] = [
                {
                    op: "=",
                    val: `${txIdMatches[4]}${txIdMatches[5]}`
                }
            ];
        }
        return results;
    }

    /**
     * Parser for one account (/account/:id) api
     * @return {Object} result of validity check, and return http code/contents
     */
    parseOneAccount() {
        let results = {
            isValid: true,
            code: utils.httpStatusCodes.OK,
            queryIntents: {},
            badParams: []
        };

        const id = this.req.params.id;
        const isValid = /^\d{1,10}\.\d{1,10}\.\d{1,10}$/.test(id) || /^\d{1,10}$/.test(id);
        if (isValid) {
            results.queryIntents["account.id"] = [
                {
                    op: "eq",
                    val: this.parseEntityId(id)
                }
            ];
        } else {
            logger.info(`parseOneAccount: Invalid account id ${this.req.params.id}`);
            results.isValid = false;
            results.badParams.push({ message: "Invalid Account id" });
            results.code = utils.httpStatusCodes.BAD_REQUEST;
        }
        return results;
    }

    /**
     * Check if the given number is numeric
     * @param {String} n Number to test
     * @return {Boolean} true if n is numeric, false otherwise
     */
    isNumeric(n) {
        return !isNaN(parseFloat(n)) && isFinite(n);
    }

    /**
     * Split the account number into shard, realm and num fields.
     * @param {String} acc Either 0.0.1234 or just 1234
     * @return {Object} {accShard, accRealm, accNum} Parsed account number
     */
    parseEntityId(acc) {
        let entity = {
            shard: "0",
            realm: "0",
            num: "0"
        };

        const aSplit = acc.split(".");
        if (aSplit.length == 3) {
            if (this.isNumeric(aSplit[0]) && this.isNumeric(aSplit[1]) && this.isNumeric(aSplit[2])) {
                entity.shard = aSplit[0];
                entity.realm = aSplit[1];
                entity.num = aSplit[2];
            }
        } else if (aSplit.length == 1) {
            if (this.isNumeric(acc)) {
                entity.num = acc;
            }
        }
        return entity;
    }

    /**
     * Convert a {shard:x, realm:y, num:z} object into an account.id string
     * @param {Object} Entity object in {accShard, accRealm, accNum} form
     * @return {String} acc Account.id (e.g. '0.0.1234')
     */
    getEntityString(entity) {
        if (["shard", "realm", "num"].every(key => Object.keys(entity).includes(key))) {
            return `${entity.shard}.${entity.realm}.${entity.num}`;
        } else {
            return "0.0.0";
        }
    }

    /**
     * Parser for timestamp
     * @param {String} ts Timestamp
     * @return {String} Timestamp in ssssssssss.nnnnnnnnn format
     */
    parseTimestamp(ts) {
        let tsSplit = ts.split(".");
        let seconds = /^(\d)+$/.test(tsSplit[0]) ? tsSplit[0] : 0;
        let nanos = tsSplit.length == 2 && /^(\d)+$/.test(tsSplit[1]) ? tsSplit[1] : 0;
        let ret = "" + seconds + (nanos + "000000000").substring(0, 9);
        return ret;
    }
}

module.exports = Parser;
