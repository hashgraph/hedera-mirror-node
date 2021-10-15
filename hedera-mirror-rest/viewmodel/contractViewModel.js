/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const EntityId = require('../entityId');
const utils = require('../utils');

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
    const contractId = EntityId.fromEncodedId(contract.id);
    Object.assign(this, {
      admin_key: utils.encodeKey(contract.key),
      auto_renew_period: contract.autoRenewPeriod === null ? null : Number(contract.autoRenewPeriod),
      contract_id: contractId.toString(),
      created_timestamp: utils.nsToSecNs(contract.createdTimestamp),
      deleted: contract.deleted,
      expiration_timestamp:
        contract.expirationTimestamp === null ? null : utils.nsToSecNs(contract.expirationTimestamp),
      file_id: EntityId.fromEncodedId(contract.fileId, true).toString(),
      memo: contract.memo,
      obtainer_id: EntityId.fromEncodedId(contract.obtainerId, true).toString(),
      proxy_account_id: EntityId.fromEncodedId(contract.proxyAccountId, true).toString(),
      solidity_address: contractId.toSolidityAddress(),
      timestamp: {
        from: utils.nsToSecNs(contract.timestampRange.begin),
        to: contract.timestampRange.end === null ? null : utils.nsToSecNs(contract.timestampRange.end),
      },
    });

    if (contract.bytecode != null) {
      this.bytecode = utils.toHexString(contract.bytecode, true);
    }
  }
}

module.exports = ContractViewModel;
