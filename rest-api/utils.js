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
const math = require("mathjs");
const config = require("./config.js");
const ed25519 = require("./ed25519.js");
const server = require("./server");

const ENTITY_TYPE_FILE = 3;

const httpStatusCodes = {
    OK: 200,
    BAD_REQUEST: 400,
    NOT_FOUND: 404,
    INTERNAL_ERROR: 500
};

const opsMap = {
    lt: "<",
    lte: "<=",
    gt: ">",
    gte: ">=",
    eq: "=",
    ne: "!="
};

/**
 * Create pagination (next) link
 * @param {String} path Path in the HTTP query request
 * @param {Object} parser Parser object for this query
 * @param {Boolean} isEnd Is the next link valid or not
 * @param {String} field The query parameter field name
 * @param {Any} lastValue THe last val for the 'next' queries in the pagination.
 * @param {String} order Order of sorting the results
 * @return {String} next Fully formed link to the next page
 */
const getPaginationLink = function(path, parser, isEnd, field, lastValue, order) {
    let next = "";
    let queryIntents = parser.getParsedReq().queryIntents;

    if (!isEnd) {
        const pattern = order === "asc" ? /gt[e]?/ : /lt[e]?/;
        const insertedPattern = order === "asc" ? "gt" : "lt";

        // If there is a 'field=gt:xxxx' (asc order) or 'field=lt:xxxx' (desc order) query parameter,
        // then remove that, as it will be replaced by the new continuation value
        if (queryIntents.hasOwnProperty(field)) {
            queryIntents[field] = queryIntents[field].filter((value, index, arr) => !pattern.test(value.op));
        } else {
            queryIntents[field] = [];
        }

        // Now, add the continuation value as 'field=gt:x' (asc order) or 'field=lt:x' (desc order)
        queryIntents[field].push({
            op: insertedPattern,
            val: lastValue
        });

        // Reconstruct the query string
        let queryStrings = [];
        for (const [field, intents] of Object.entries(queryIntents)) {
            for (const intent of intents) {
                queryStrings.push(`${field}=${intent.op}:${intent.originalVal || intent.val}`);
            }
        }
        next = `${path}?${queryStrings.join("&")}`;
    }
    return next === "" ? null : next;
};

/**
 * Converts nanoseconds since epoch to seconds.nnnnnnnnn format
 * @param {String} ns Nanoseconds since epoch
 * @return {String} Seconds since epoch (seconds.nnnnnnnnn format)
 */
const nsToSecNs = function(ns) {
    return math
        .divide(math.bignumber(ns), math.bignumber(1e9))
        .toFixed(9)
        .toString();
};

/**
 * Converts seconds/nanoseconds since epoch (seconds.nnnnnnnnn format) to  nanoseconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format)
 * @return {String} ns Nanoseconds since epoch
 */
const secNsToNs = function(secNs) {
    return math.multiply(math.bignumber(secNs), math.bignumber(1e9)).toString();
};

/**
 * Converts seconds/nanoseconds since epoch (seconds.nnnnnnnnn format) to seconds
 * @param {String} Seconds since epoch (seconds.nnnnnnnnn format)
 * @return {Number} seconds Seconds since epoch
 */
const secNsToSeconds = function(secNs) {
    return math.floor(Number(secNs));
};

/**
 * Converts the byte array returned by SQL queries into hex string
 * @param {ByteArray} byteArray Array of bytes to be converted to hex string
 * @return {hexString} Converted hex string
 */
const toHexString = function(byteArray) {
    return byteArray === null
        ? null
        : byteArray.reduce((output, elem) => output + ("0" + elem.toString(16)).slice(-2), "");
};

/**
 * Converts a key for returning in JSON output
 * @param {Array} key Byte array representing the key
 * @return {Object} Key object - with type decoration for ED25519, if detected
 */
const encodeKey = function(key) {
    let ret;
    if (key === null) {
        return null;
    }
    try {
        let hs = toHexString(key);
        const pattern = /^1220([A-Fa-f0-9]*)$/;
        const replacement = "$1";
        if (pattern.test(hs)) {
            ret = {
                _type: "ED25519",
                key: hs.replace(pattern, replacement)
            };
        } else {
            ret = {
                _type: "ProtobufEncoded",
                key: hs
            };
        }
        return ret;
    } catch (err) {
        return null;
    }
};

/**
 * Base64 encoding of a byte array for returning in JSON output
 * @param {Array} key Byte array to be encoded
 * @return {String} base64 encoded string
 */
const encodeBase64 = function(buffer) {
    return null === buffer ? null : buffer.toString("base64");
};

module.exports = {
    opsMap: opsMap,
    getPaginationLink: getPaginationLink,
    nsToSecNs: nsToSecNs,
    secNsToNs: secNsToNs,
    secNsToSeconds: secNsToSeconds,
    toHexString: toHexString,
    encodeKey: encodeKey,
    encodeBase64: encodeBase64,
    httpStatusCodes: httpStatusCodes,
    ENTITY_TYPE_FILE: ENTITY_TYPE_FILE
};
