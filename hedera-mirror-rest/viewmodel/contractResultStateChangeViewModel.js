/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import {filterKeys} from '../constants';
import EntityId from '../entityId';
import {toHexString, toUint256} from '../utils';

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
    this.address = contractStateChange?.evmAddress?.length
      ? toHexString(contractStateChange.evmAddress, true)
      : contractId.toEvmAddress();
    this.contract_id = contractId.toString();
    this.slot = toUint256(contractStateChange.slot);
    this.value_read = toUint256(contractStateChange.valueRead);
    this.value_written = toUint256(contractStateChange.valueWritten);
  }
}

export default ContractResultStateChangeViewModel;
