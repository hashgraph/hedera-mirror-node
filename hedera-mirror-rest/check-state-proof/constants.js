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

// properties of SHA-384 hash algorithm
const SHA_384 = {
  encoding: 'hex',
  length: 48,
  name: 'sha384',
};

const BYTE_SIZE = 1;
const INT_SIZE = 4;
const LONG_SIZE = 8;

module.exports = {
  BYTE_SIZE,
  INT_SIZE,
  LONG_SIZE,
  SHA_384,
};
