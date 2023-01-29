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

import ContractLogResultsViewModel from './contractResultLogViewModel';
import EntityId from '../entityId';
import {addHexPrefix, nsToSecNs, toHexStringNonQuantity} from '../utils';

/**
 * Contract log view model
 */
class ContractLogViewModel extends ContractLogResultsViewModel {
  /**
   * Constructs contractLog view model
   *
   * @param {ContractLog} contractLog
   */
  constructor(contractLog) {
    super(contractLog);
    Object.assign(this, {
      block_hash: addHexPrefix(contractLog.blockHash),
      block_number: contractLog.blockNumber,
      root_contract_id: EntityId.parse(contractLog.rootContractId, {isNullable: true}).toString(),
      timestamp: nsToSecNs(contractLog.consensusTimestamp),
      transaction_hash: toHexStringNonQuantity(contractLog.transactionHash),
      transaction_index: contractLog.transactionIndex,
    });
  }
}

export default ContractLogViewModel;
