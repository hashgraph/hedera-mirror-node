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
   * @param {ContractResult} contract
   */
  constructor(contractResult, recordFile = undefined, transaction = undefined) {
    Object.assign(this, {
      amount: Number(contractResult.amount),
      bloom: utils.toHexString(contractResult.bloom, true),
      call_result: utils.toHexString(contractResult.callResult, true),
      contract_id: EntityId.parse(contractResult.contractId).toString(),
      created_contract_ids: _.toArray(contractResult.createdContractIds).map((id) => EntityId.parse(id).toString()),
      error_message: contractResult.errorMessage,
      from: EntityId.parse(contractResult.payerAccountId).toSolidityAddress(),
      function_parameters: utils.toHexString(contractResult.functionParameters, true),
      gas_limit: Number(contractResult.gasLimit),
      gas_used: Number(contractResult.gasUsed),
      timestamp: utils.nsToSecNs(contractResult.consensusTimestamp),
      to: EntityId.parse(contractResult.contractId, true).toSolidityAddress(),
    });

    if (!_.isUndefined(recordFile)) {
      this.block_hash = utils.addHexPrefix(recordFile.hash);
      this.block_number = Number(recordFile.index);
    }

    if (!_.isUndefined(transaction)) {
      this.hash = utils.toHexString(transaction.transactionHash, true);
    }
  }
}

module.exports = ContractResultViewModel;
