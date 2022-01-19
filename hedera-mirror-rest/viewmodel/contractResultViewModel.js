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
    const contractId = EntityId.parse(contractResult.contractId, true);
    Object.assign(this, {
      amount: contractResult.amount === null ? null : Number(contractResult.amount),
      call_result: utils.toHexString(contractResult.callResult, true),
      contract_id: contractId.toString(),
      created_contract_ids: _.toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString()),
      error_message: contractResult.errorMessage,
      from: EntityId.parse(contractResult.payerAccountId).toSolidityAddress(),
      function_parameters: utils.toHexString(contractResult.functionParameters, true),
      gas_limit: Number(contractResult.gasLimit),
      gas_used: Number(contractResult.gasUsed),
      timestamp: utils.nsToSecNs(contractResult.consensusTimestamp),
      to: contractId.toSolidityAddress(),
    });
  }
}

module.exports = ContractResultViewModel;
