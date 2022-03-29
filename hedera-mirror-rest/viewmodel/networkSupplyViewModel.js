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

'use strict';

const utils = require('../utils');

/**
 * Network supply view model
 */
class NetworkSupplyViewModel {
  /**
   * Constructs network supply view model
   *
   * @param {Object} networkSupply
   * @param {BigInt} totalSupply
   */
  constructor(networkSupply, totalSupply) {
    const unreleasedSupply = BigInt(networkSupply.unreleased_supply);
    const releasedSupply = totalSupply - unreleasedSupply;

    // Convert numbers to string since Express doesn't support BigInt
    this.released_supply = `${releasedSupply}`;
    this.timestamp = utils.nsToSecNs(networkSupply.consensus_timestamp);
    this.total_supply = `${totalSupply}`;
  }
}

module.exports = NetworkSupplyViewModel;
