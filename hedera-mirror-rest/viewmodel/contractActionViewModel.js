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

/**
 * Contract actions view model
 */
class ContractActionViewModel {
  /**
   * Constructs contractAction view model
   *
   * @param {ContractAction} contractAction
   */
  constructor(contractAction) {
    const recipientIsAccount = !!contractAction.recipient_account;

    this.call_depth = contractAction.call_depth;
    this.call_type = contractAction.call_type;
    this.caller = contractAction.caller;
    this.caller_type = contractAction.caller_type;
    this.from = EntityId.parse(contractAction.caller).toEvmAddress();
    this.gas = contractAction.gas;
    this.gas_used = contractAction.gas_used;
    this.index = contractAction.index;
    this.input = contractAction.input;
    this.recipient = recipientIsAccount ? contractAction.recipient_account : contractAction.recipient_contract;
    this.recipient_type = recipientIsAccount ? 'ACCOUNT' : 'CONTRACT';
    this.result_data = contractAction.result_data;
    this.result_data_type = contractAction.result_data_type;
    this.timestamp = contractAction.consensus_timestamp;
    this.to = contractAction.recipient_address;
    this.value = contractAction.value;
  }
}

export default ContractActionViewModel;
