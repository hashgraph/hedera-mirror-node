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

'use strict';

const EntityId = require('../entityId');
const constants = require('../constants');
const utils = require('../utils');

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
    const contractId = EntityId.parse(contractStateChange.contractId, constants.filterKeys.CONTRACTID);
    Object.assign(this, {
      address: contractId.toSolidityAddress(),
      contract_id: contractId.toString(),
      slot: utils.toHexString(contractStateChange.slot, true),
      value_read: utils.toHexString(contractStateChange.valueRead, true),
      value_written: utils.toHexString(contractStateChange.valueWritten, true),
    });
  }
}

module.exports = ContractResultStateChangeViewModel;
