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
class ContractResultViewModel {
  /**
   * Constructs contractResult view model
   *
   * @param {ContractResult} contract
   */
  constructor(contractResult, transactionModel, displayAdditionalInfo = false) {
    Object.assign(this, {
      amount: contractResult.amount,
      bloom: utils.toHexString(contractResult.bloom, true),
      call_result: utils.toHexString(contractResult.callResult, true),
      child_transactions: null,
      created_contract_ids: contractResult.createdContractIds,
      error_message: contractResult.errorMessage,
      from: EntityId.parse(contractResult.payerAccountId).toSolidityAddress(),
      function_parameters: utils.toHexString(contractResult.functionParameters, true),
      gas_limit: contractResult.gasLimit,
      gas_used: contractResult.gasUsed,
      hash: transactionModel ? transactionModel.transactionHash : null,
      timestamp: utils.nsToSecNs(contractResult.consensusTimestamp),
      to: EntityId.parse(contractResult.to, true).toSolidityAddress(),
    });

    // format created contract ids
    if (contractResult.createdContractIds != null) {
      this.created_contract_ids = contractResult.createdContractIds.map((id) => EntityId.parse(id).toString());
    } else {
      this.created_contract_ids = [];
    }

    if (displayAdditionalInfo) {
      this.access_list = null;
      this.block_hash = null;
      this.block_number = null;
      this.child_transactions = null;
      this.evm_internal_transactions = null;
      this.logs = null;
      this.state_changes = null;
    }
  }
}

module.exports = ContractResultViewModel;
