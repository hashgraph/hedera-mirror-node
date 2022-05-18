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

const _ = require('lodash');
const EntityId = require('../entityId');
const utils = require('../utils');

/**
 * Contract results view model
 */
class ContractResultViewModel {
  /**
   * Constructs contractResult view model
   *
   * @param {ContractResult} contractResult
   */
  constructor(contractResult) {
    const contractId = EntityId.parse(contractResult.contractId, {isNullable: true});
    this.amount = contractResult.amount;
    this.bloom = utils.toHexString(contractResult.bloom, true);
    this.call_result = utils.toHexString(contractResult.callResult, true);
    this.contract_id = contractId.toString();
    this.created_contract_ids = _.toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString());
    this.error_message = _.isEmpty(contractResult.errorMessage) ? null : contractResult.errorMessage;
    this.from = EntityId.parse(contractResult.payerAccountId).toEvmAddress();
    this.function_parameters = utils.toHexString(contractResult.functionParameters, true);
    this.gas_limit = contractResult.gasLimit;
    this.gas_used = contractResult.gasUsed;
    this.timestamp = utils.nsToSecNs(contractResult.consensusTimestamp);
    this.to = contractId.toEvmAddress();
  }
}

module.exports = ContractResultViewModel;
