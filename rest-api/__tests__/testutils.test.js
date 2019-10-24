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
const utils = require('../utils.js');

describe('Utils tests', () => {
    
    test('Verify getNullableNumber returns correct result for 0', () => {
        var val = utils.getNullableNumber(0);
        expect(val).toBe(0);
    });

    test('Verify getNullableNumber returns correct result for null', () => {
        var val = utils.getNullableNumber(null);
        expect(val).toBe(null);
    });

    test('Verify getNullableNumber returns correct result for undefined', () => {
        var val = utils.getNullableNumber(undefined);
        expect(val).toBe(null);
    });

    test('Verify getNullableNumber returns correct result for valid number', () => {
        var validNumber = 10;
        var val = utils.getNullableNumber(validNumber);
        expect(val).toBe(validNumber);
    });
});