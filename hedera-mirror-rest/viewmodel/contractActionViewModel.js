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

import EntityId from '../entityId';
import * as utils from '../utils.js';

/**
 * Contract actions view model
 */
class ContractActionViewModel {
  static resultDataTypes = {
    11: 'OUTPUT',
    12: 'REVERT_REASON',
    13: 'ERROR',
  };

  /**
   * Constructs contractAction view model
   *
   * @param {ContractAction} contractAction
   */
  constructor(contractAction) {
    const recipientIsAccount = !!contractAction.recipient_account;

    this.call_depth = contractAction.callDepth;
    this.call_type = contractAction.callType;
    this.caller = contractAction.caller;
    this.caller_type = contractAction.callerType;
    this.from = contractAction.caller ? EntityId.parse(contractAction.caller).toEvmAddress() : null;
    this.gas = contractAction.gas;
    this.gas_used = contractAction.gasUsed;
    this.index = contractAction.index;
    this.input = contractAction.input ? utils.addHexPrefix(Buffer.from(contractAction.input).toString('hex')) : null;
    this.recipient = recipientIsAccount
      ? EntityId.parse(contractAction.recipientAccount).toString()
      : EntityId.parse(contractAction.recipientContract).toString();
    this.recipient_type = recipientIsAccount ? 'ACCOUNT' : 'CONTRACT';
    this.result_data = contractAction.resultData
      ? utils.addHexPrefix(Buffer.from(contractAction.resultData).toString('hex'))
      : null;
    this.result_data_type = ContractActionViewModel.resultDataTypes[contractAction.resultDataType];
    this.timestamp = contractAction.consensusTimestamp.toString();
    this.to = contractAction.recipientAddress
      ? utils.addHexPrefix(Buffer.from(contractAction.recipientAddress).toString('hex'))
      : null;
    this.value = contractAction.value;
  }
}

export default ContractActionViewModel;
