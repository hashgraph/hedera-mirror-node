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
 * Unit test for helper functions in utils.js
 */
describe("Utils tests", () => {
    test(`Pagination links - valid next link`, () => {
        let params = {
            accountNum: { low: 1, high: 10000 },
            timestamp: { low: 1111111111, high: 9999999999 },
            limit: 100,
            order: "desc",
            result: "SUCCESS"
        };
        const req = { query: { myfield: "1234567890" } };
        const path = "/path/to/api";
        const parser = new Parser(req, "transactions");

        const nextlink = utils.getPaginationLink(path, parser, false, "myfield", 5555555555, "desc");
        expect(nextlink).toBeDefined();
        expect(nextlink).not.toBeNull();
        expect(nextlink.startsWith(path)).toBeTruthy();
    });

    test(`Pagination links - null next link`, () => {
        let params = {
            accountNum: { low: 1, high: 10000 },
            timestamp: { low: 1111111111, high: 9999999999 },
            limit: 100,
            order: "desc",
            result: "SUCCESS"
        };
        const req = { query: { myfield: "1234567890" } };
        const path = "/path/to/api";
        const parser = new Parser(req, "transactions");

        const nextlink = utils.getPaginationLink(path, parser, true, "myfield", 5555555555, "desc");
        expect(nextlink).toBeDefined();
        expect(nextlink).toBeNull();
    });

    test(`seconds <--> nanoseconds conversion tests`, () => {
        const orig = "1234567890111222333";
        const secNs = utils.nsToSecNs(orig);
        expect(secNs).toEqual("1234567890.111222333");

        const back = utils.secNsToNs(secNs);
        expect(back).toEqual(orig);

        const seconds = utils.secNsToSeconds(secNs);
        expect(seconds).toEqual(1234567890);
    });

    test(`hexstring conversion tests`, () => {
        const bytes = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20];
        const hex = utils.toHexString(bytes);
        expect(hex).toEqual("0102030405060708090a0b0c0d0e0f1011121314");
    });

    test(`hexstring conversion tests - null`, () => {
        const bytes = null;
        const hex = utils.toHexString(bytes);
        expect(hex).toEqual(null);
    });

    test(`key encoding test`, () => {
        const bytes = [18, 32, 37, 245, 193, 216, 80, 35, 199, 225, 191, 217, 32, 177, 103, 94, 11, 
            86, 195, 233, 114, 94, 48, 239, 16, 142, 152, 127, 35, 248, 220, 134, 152, 32];
        const encoded = utils.encodeKey(bytes);
        expect(encoded._type).toEqual("ED25519");
        expect(encoded.key).toEqual("25f5c1d85023c7e1bfd920b1675e0b56c3e9725e30ef108e987f23f8dc869820");
    });

    test(`key encoding test - null`, () => {
        const bytes = null;
        const encoded = utils.encodeKey(bytes);
        expect(encoded).toEqual(null);
    });

    test(`base64 encoding test`, () => {
        const buf = new Buffer.from("This is a test", "utf8");
        const encoded = utils.encodeBase64(buf);
        expect(encoded).toEqual("VGhpcyBpcyBhIHRlc3Q=");
    });
});
