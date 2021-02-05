/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

'use strict';

const MAX_RECORD_LENGTH = 64 * 1024;

const MAX_TRANSACTION_LENGTH = 64 * 1024;

const SHA_384_LENGTH = 48;

const SHA_384_WITH_RSA = {
  type: 1,
  maxLength: 384,
};

const SIMPLE_SUM = 101;

module.exports = {
  MAX_RECORD_LENGTH,
  MAX_TRANSACTION_LENGTH,
  SHA_384_LENGTH,
  SHA_384_WITH_RSA,
  SIMPLE_SUM,
};
