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

import {filterKeys} from '../constants';
import EntityId from '../entityId';
import {toHexString, nsToSecNs, toUint256} from '../utils';

/**
 * Contract result state view model
 */
class ContractStateViewModel {
  /**
   * Constructs contractResultStates view model
   *
   * @param {ContractState} contractState
   */
  constructor(contractState) {
    const contractId = EntityId.parse(contractState.contractId, {paramName: filterKeys.CONTRACTID});
    this.address = contractState?.evmAddress?.length
      ? toHexString(contractState.evmAddress, true)
      : contractId.toEvmAddress();
    this.contract_id = contractId.toString();
    this.timestamp = nsToSecNs(contractState.modifiedTimestamp);
    this.slot = toUint256(contractState.slot);
    this.value = toUint256(contractState.value);
  }
}

export default ContractStateViewModel;
