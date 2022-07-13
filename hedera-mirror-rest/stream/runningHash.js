/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import crypto from 'crypto';

/**
 * Calculates the next running hash from running hash object and next hash object using the specified digest algorithm.
 *
 * @param {object} runningHashObject
 * @param {object} nextHashObject
 * @param {string} algorithm
 * @returns {Buffer} the running hash
 */
const calculateRunningHash = (runningHashObject, nextHashObject, algorithm) => {
  return crypto
    .createHash(algorithm)
    .update(runningHashObject.header)
    .update(runningHashObject.hash)
    .update(nextHashObject.header)
    .update(nextHashObject.hash)
    .digest();
};

export default calculateRunningHash;
