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

import EntityId from '../entityId.js';
import * as utils from '../utils.js';

/**
 * Contract view model
 */
class ContractViewModel {
  /**
   * Constructs contract view model
   *
   * @param {Contract} contract
   */
  constructor(contract) {
    const contractId = EntityId.parse(contract.id);
    this.admin_key = utils.encodeKey(contract.key);
    this.auto_renew_account = EntityId.parse(contract.autoRenewAccountId, {isNullable: true}).toString();
    this.auto_renew_period = contract.autoRenewPeriod;
    this.contract_id = contractId.toString();
    this.created_timestamp = utils.nsToSecNs(contract.createdTimestamp);
    this.deleted = contract.deleted;
    this.evm_address =
      contract.evmAddress !== null ? utils.toHexString(contract.evmAddress, true) : contractId.toEvmAddress();
    this.expiration_timestamp = utils.nsToSecNs(contract.expirationTimestamp);
    this.file_id = EntityId.parse(contract.fileId, {isNullable: true}).toString();
    this.max_automatic_token_associations = contract.maxAutomaticTokenAssociations;
    this.memo = contract.memo;
    this.obtainer_id = EntityId.parse(contract.obtainerId, {isNullable: true}).toString();
    this.permanent_removal = contract.permanentRemoval;
    this.proxy_account_id = EntityId.parse(contract.proxyAccountId, {isNullable: true}).toString();
    this.timestamp = {
      from: utils.nsToSecNs(contract.timestampRange.begin),
      to: utils.nsToSecNs(contract.timestampRange.end),
    };

    if (contract.bytecode !== undefined) {
      this.bytecode = utils.addHexPrefix(contract.bytecode, true);
    }
  }
}

export default ContractViewModel;
