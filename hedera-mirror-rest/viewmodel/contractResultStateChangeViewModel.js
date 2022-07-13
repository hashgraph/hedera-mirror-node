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

import {filterKeys} from '../constants.js';
import EntityId from '../entityId.js';
import {toHexString} from '../utils.js';

/**
 * Contract result state change view model
 */
class ContractResultStateChangeViewModel {
  /**
   * Constructs contractResultStateChanges view model
   *
   * @param {ContractStateChange} contractStateChange
   */
  constructor(contractStateChange) {
    const contractId = EntityId.parse(contractStateChange.contractId, {paramName: filterKeys.CONTRACTID});
    this.address = contractId.toEvmAddress();
    this.contract_id = contractId.toString();
    this.slot = toHexString(contractStateChange.slot, true, 64);
    this.value_read = toHexString(contractStateChange.valueRead, true, 64);
    this.value_written = toHexString(contractStateChange.valueWritten, true, 64);
  }
}

export default ContractResultStateChangeViewModel;
