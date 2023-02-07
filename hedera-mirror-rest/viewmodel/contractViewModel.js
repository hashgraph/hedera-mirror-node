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

import EntityId from '../entityId';
import * as utils from '../utils';

/**
 * Contract view model
 */
class ContractViewModel {
  /**
   * Constructs contract view model
   *
   * @param {Contract} contract
   * @param {Entity} entity
   */
  constructor(contract, entity) {
    const contractId = EntityId.parse(entity.id);
    this.admin_key = utils.encodeKey(entity.key);
    this.auto_renew_account = EntityId.parse(entity.autoRenewAccountId, {isNullable: true}).toString();
    this.auto_renew_period = entity.autoRenewPeriod;
    this.contract_id = contractId.toString();
    this.created_timestamp = utils.nsToSecNs(entity.createdTimestamp);
    this.deleted = entity.deleted;
    this.evm_address =
      entity.evmAddress !== null ? utils.toHexString(entity.evmAddress, true) : contractId.toEvmAddress();
    this.expiration_timestamp = utils.nsToSecNs(utils.calculateExpiryTimestamp(
      entity.autoRenewPeriod,
      entity.createdTimestamp,
      entity.expirationTimestamp
    ));
    this.file_id = EntityId.parse(contract.fileId, {isNullable: true}).toString();
    this.max_automatic_token_associations = entity.maxAutomaticTokenAssociations;
    this.memo = entity.memo;
    this.obtainer_id = EntityId.parse(entity.obtainerId, {isNullable: true}).toString();
    this.permanent_removal = entity.permanentRemoval;
    this.proxy_account_id = EntityId.parse(entity.proxyAccountId, {isNullable: true}).toString();
    this.timestamp = {
      from: utils.nsToSecNs(entity.timestampRange.begin),
      to: utils.nsToSecNs(entity.timestampRange.end),
    };
  }
}

export default ContractViewModel;
