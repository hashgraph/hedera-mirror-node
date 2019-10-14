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

const Parser = require("../Parser.js");
const utils = require("../utils.js");

/**
 * Unit test for HTTP request parameter parsing (validateReq and paramValidityChecks functions)
 */
describe("HTTP request parameter parsing tests", () => {
    test(`Valid account.id:`, () => {
        const checklist = [
            {
                req: { query: { "account.id": ["1.2.3456"] } },
                exp: {
                    "account.id": [{ op: "eq", val: { shard: "1", realm: "2", num: "3456" }, originalVal: "1.2.3456" }]
                }
            },
            {
                req: { query: { "account.id": ["123"] } },
                exp: { "account.id": [{ op: "eq", val: { shard: "0", realm: "0", num: "123" }, originalVal: "123" }] }
            },
            {
                req: { query: { "account.id": ["eq:1.2.3456"] } },
                exp: {
                    "account.id": [{ op: "eq", val: { shard: "1", realm: "2", num: "3456" }, originalVal: "1.2.3456" }]
                }
            },
            {
                req: { query: { "account.id": ["gt:1.2.3456", "lt:9.8.7654"] } },
                exp: {
                    "account.id": [
                        { op: "gt", val: { shard: "1", realm: "2", num: "3456" }, originalVal: "1.2.3456" },
                        { op: "lt", val: { shard: "9", realm: "8", num: "7654" }, originalVal: "9.8.7654" }
                    ]
                }
            },
            {
                req: { query: { "account.id": ["gte:1.2.3456", "lte:9.8.7654"] } },
                exp: {
                    "account.id": [
                        { op: "gte", val: { shard: "1", realm: "2", num: "3456" }, originalVal: "1.2.3456" },
                        { op: "lte", val: { shard: "9", realm: "8", num: "7654" }, originalVal: "9.8.7654" }
                    ]
                }
            },
            {
                req: { query: { "account.id": ["ne:1.2.3456"] } },
                exp: {
                    "account.id": [{ op: "ne", val: { shard: "1", realm: "2", num: "3456" }, originalVal: "1.2.3456" }]
                }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid timestamp:`, () => {
        const checklist = [
            {
                req: { query: { timestamp: ["1234567890.123456789"] } },
                exp: { timestamp: [{ op: "eq", val: "1234567890123456789", originalVal: "1234567890.123456789" }] }
            },
            {
                req: { query: { timestamp: ["1234567890"] } },
                exp: { timestamp: [{ op: "eq", val: "1234567890000000000", originalVal: "1234567890" }] }
            },
            {
                req: { query: { timestamp: ["eq:1234567890.456"] } },
                exp: { timestamp: [{ op: "eq", val: "1234567890456000000", originalVal: "1234567890.456" }] }
            },
            {
                req: { query: { timestamp: ["gt:1234567890", "lt:9876543210"] } },
                exp: {
                    timestamp: [
                        { op: "gt", val: "1234567890000000000", originalVal: "1234567890" },
                        { op: "lt", val: "9876543210000000000", originalVal: "9876543210" }
                    ]
                }
            },
            {
                req: { query: { timestamp: ["gte:1234567890", "lte:9876543210"] } },
                exp: {
                    timestamp: [
                        { op: "gte", val: "1234567890000000000", originalVal: "1234567890" },
                        { op: "lte", val: "9876543210000000000", originalVal: "9876543210" }
                    ]
                }
            },
            {
                req: { query: { timestamp: ["ne:1234567890.123456789"] } },
                exp: { timestamp: [{ op: "ne", val: "1234567890123456789", originalVal: "1234567890.123456789" }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid account.balance:`, () => {
        const checklist = [
            {
                req: { query: { "account.balance": ["1234567890"] } },
                exp: { "account.balance": [{ op: "eq", val: "1234567890" }] }
            },
            {
                req: { query: { "account.balance": ["eq:1234567890"] } },
                exp: { "account.balance": [{ op: "eq", val: "1234567890" }] }
            },
            {
                req: { query: { "account.balance": ["gt:1234567890", "lt:9876543210"] } },
                exp: { "account.balance": [{ op: "gt", val: "1234567890" }, { op: "lt", val: "9876543210" }] }
            },
            {
                req: { query: { "account.balance": ["gte:1234567890", "lte:9876543210"] } },
                exp: { "account.balance": [{ op: "gte", val: "1234567890" }, { op: "lte", val: "9876543210" }] }
            },
            {
                req: { query: { "account.balance": ["ne:1234567890"] } },
                exp: { "account.balance": [{ op: "ne", val: "1234567890" }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid account.publickey:`, () => {
        const checklist = [
            {
                req: {
                    query: { "account.publickey": ["01234567890123456789abcdef0123456789ABCDEF0123456789abcdef012345"] }
                },
                exp: {
                    "account.publickey": [
                        { op: "eq", val: "01234567890123456789abcdef0123456789abcdef0123456789abcdef012345" }
                    ]
                }
            },
            {
                req: {
                    query: {
                        "account.publickey": ["eq:01234567890123456789abcdef0123456789ABCDEF0123456789abcdef012345"]
                    }
                },
                exp: {
                    "account.publickey": [
                        { op: "eq", val: "01234567890123456789abcdef0123456789abcdef0123456789abcdef012345" }
                    ]
                }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid result:`, () => {
        const checklist = [
            {
                req: { query: { result: ["success"] } },
                exp: { result: [{ op: "eq", val: "success" }] }
            },
            {
                req: { query: { result: ["fail"] } },
                exp: { result: [{ op: "eq", val: "fail" }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid type:`, () => {
        const checklist = [
            {
                req: { query: { type: ["credit"] } },
                exp: { type: [{ op: "eq", val: "credit" }] }
            },
            {
                req: { query: { type: ["debit"] } },
                exp: { type: [{ op: "eq", val: "debit" }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid order:`, () => {
        const checklist = [
            {
                req: { query: { order: ["asc"] } },
                exp: { order: [{ op: "eq", val: "asc" }] }
            },
            {
                req: { query: { order: ["desc"] } },
                exp: { order: [{ op: "eq", val: "desc" }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Valid limit:`, () => {
        const checklist = [
            {
                req: { query: { limit: ["123"] } },
                exp: { limit: [{ op: "eq", val: 123 }] }
            },
            {
                req: { query: { limit: ["1"] } },
                exp: { limit: [{ op: "eq", val: 1 }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeTruthy();
            expect(response.code).toBe(200);
            expect(check.exp).toEqual(response.queryIntents);
        }
    });

    test(`Invalid values:`, () => {
        const checklist = [
            {
                req: { query: { "account.id": ["1.2.abcd"] } },
                exp: { badParams: [{ message: `Invalid parameter: account.id` }] }
            },
            {
                req: { query: { "account.id": ["1.2.4.3456"] } },
                exp: { badParams: [{ message: `Invalid parameter: account.id` }] }
            },
            {
                req: { query: { timestamp: ["1234-5678"] } },
                exp: { badParams: [{ message: `Invalid parameter: timestamp` }] }
            },
            {
                req: { query: { "account.balance": ["abcd"] } },
                exp: { badParams: [{ message: `Invalid parameter: account.balance` }] }
            },
            {
                req: { query: { type: ["abcd"] } },
                exp: { badParams: [{ message: `Invalid parameter: type` }] }
            },
            {
                req: { query: { result: ["abcd"] } },
                exp: { badParams: [{ message: `Invalid parameter: result` }] }
            },
            {
                req: { query: { order: ["abcd"] } },
                exp: { badParams: [{ message: `Invalid parameter: order` }] }
            },
            {
                req: { query: { limit: ["9999999"] } },
                exp: { badParams: [{ message: `Invalid parameter: limit` }] }
            }
        ];

        for (const check of checklist) {
            const parser = new Parser(check.req, "transactions");
            const response = parser.getParsedReq();
            expect(response.isValid).toBeFalsy();
            expect(response.code).toBe(utils.httpStatusCodes.OK);
            expect(check.exp.badParams).toEqual(response.badParams);
        }
    });
});
