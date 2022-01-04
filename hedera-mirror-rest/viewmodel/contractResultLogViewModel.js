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
const constants = require('../constants');
const utils = require('../utils');

/**
 * Contract results log view model
 */
class ContractResultLogViewModel {
  /**
   * Constructs contractResultLogs view model
   *
   * @param {ContractLog} contractLog
   */
  constructor(contractLog) {
    const contractId = EntityId.parse(contractLog.contractId, constants.filterKeys.CONTRACTID);
    Object.assign(this, {
      address: contractId.toSolidityAddress(),
      contract_id: contractId.toString(),
      data: utils.toHexString(contractLog.data, true),
      index: contractLog.index,
      topics: this._formatTopics([contractLog.topic0, contractLog.topic1, contractLog.topic2, contractLog.topic3]),
    });
  }

  _formatTopics(topics) {
    return topics.filter((topic) => topic !== null).map((topic) => utils.toHexString(topic, true, 64));
  }
}

module.exports = ContractResultLogViewModel;
