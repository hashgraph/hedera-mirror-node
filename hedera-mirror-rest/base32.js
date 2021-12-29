/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const base32Decode = require('base32-decode');
const base32Encode = require('base32-encode');

const variant = 'RFC4648';

/**
 * Decodes the rfc4648 base32 string into a buffer. If the input string is null, returns null.
 * @param str the base32 string.
 * @return {Buffer}
 */
const decode = (str) => {
  return str && Buffer.from(base32Decode(str, variant));
};

/**
 * Encodes the byte array into a rfc4648 base32 string without padding. If the input is null, returns null.
 * @param {Buffer} data
 * @return {string}
 */
const encode = (data) => {
  return data && base32Encode(data, variant, {padding: false});
};

// regex for RFC4648 base32 string without padding. The base32 alphabet is [A-Z2-7] and '=' is the padding.
// Input is split into 40-bit groups. For every group, each 5-bit input is encoded to one of the 32 characters.
// Fewer than 40 bits groups are treated as 0-bit appended to the next multiples of 5 bits , so
// 1. if the final group is 8 bits, the output will be 2 characters
// 2. if the final group is 16 bits, the output will be 4 characters
// 3. if the final group is 24 bits, the output will be 5 characters
// 4. if the final group is 32 bits, the output will be 7 characters
const base32Regex = /^(?:[A-Z2-7]{8})*(?:[A-Z2-7]{2}|[A-Z2-7]{4,5}|[A-Z2-7]{7,8})$/;

/**
 * Checks if the input is a valid rfc4648 base32 string.
 * @param {string} str
 * @return {boolean}
 */
const isValidBase32Str = (str) => {
  return typeof str === 'string' && base32Regex.test(str);
};

module.exports = {
  decode,
  encode,
  isValidBase32Str,
};
