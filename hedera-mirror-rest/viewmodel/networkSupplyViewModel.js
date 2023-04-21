/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import {nsToSecNs} from '../utils';

/**
 * Network supply view model
 */
class NetworkSupplyViewModel {
  static totalSupply = 5000000000000000000n;

  /**
   * Constructs network supply view model
   *
   * @param {Object} networkSupply
   */
  constructor(networkSupply) {
    const unreleasedSupply = BigInt(networkSupply.unreleased_supply);
    const releasedSupply = NetworkSupplyViewModel.totalSupply - unreleasedSupply;

    // Convert numbers to string since Express doesn't support BigInt
    this.released_supply = `${releasedSupply}`;
    this.timestamp = nsToSecNs(networkSupply.consensus_timestamp);
    this.total_supply = `${NetworkSupplyViewModel.totalSupply}`;
  }
}

export default NetworkSupplyViewModel;
