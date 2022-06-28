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
const {toBigIntBE} = require('bigint-buffer');

const ContractLogResultsViewModel = require('./contractResultLogViewModel');
const ContractResultStateChangeViewModel = require('./contractResultStateChangeViewModel');
const ContractResultViewModel = require('./contractResultViewModel');
const {TransactionResult} = require('../model');
const utils = require('../utils');
const EntityId = require('../entityId');

/**
 * Contract result details view model
 */
class ContractResultDetailsViewModel extends ContractResultViewModel {
  static _SUCCESS_PROTO_ID = Number.parseInt(TransactionResult.getSuccessProtoId());
  static _SUCCESS_RESULT = '0x1';
  static _FAIL_RESULT = '0x0';

  /**
   * Constructs contractResultDetails view model
   *
   * @param {ContractResult} contractResult
   * @param {RecordFile} recordFile
   * @param {Transaction} transaction
   * @param {ContractLog[]} contractLogs
   * @param {ContractStateChange[]} contractStateChanges
   * @param {FileData} fileData
   */
  constructor(contractResult, recordFile, transaction, contractLogs, contractStateChanges, fileData) {
    super(contractResult);

    this.block_hash = utils.addHexPrefix(recordFile.hash);
    this.block_number = recordFile.index;
    this.hash = utils.toHexStringNonQuantity(transaction.transactionHash);
    this.logs = contractLogs.map((contractLog) => new ContractLogResultsViewModel(contractLog));
    this.result = TransactionResult.getName(transaction.result);
    this.transaction_index = transaction.index;

    this.state_changes = contractStateChanges.map(
      (contractStateChange) => new ContractResultStateChangeViewModel(contractStateChange)
    );
    this.status =
      transaction.result === ContractResultDetailsViewModel._SUCCESS_PROTO_ID
        ? ContractResultDetailsViewModel._SUCCESS_RESULT
        : ContractResultDetailsViewModel._FAIL_RESULT;

    // default eth related values
    this.access_list = null;
    this.block_gas_used = recordFile.gasUsed !== null && recordFile.gasUsed !== -1 ? recordFile.gasUsed : null;
    this.chain_id = null;
    this.gas_price = null;
    this.max_fee_per_gas = null;
    this.max_priority_fee_per_gas = null;
    this.r = null;
    this.s = null;
    this.type = null;
    this.v = null;
    this.nonce = null;

    if (!_.isNil(transaction.type)) {
      this.access_list = utils.toHexStringNonQuantity(transaction.accessList);
      this.amount = transaction.value && toBigIntBE(transaction.value);
      this.chain_id = utils.toHexStringQuantity(transaction.chainId);

      if (!_.isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!_.isNil(transaction.gasLimit)) {
        this.gas_limit = transaction.gasLimit;
      }
      this.gas_price = utils.toHexStringQuantity(transaction.gasPrice);
      this.max_fee_per_gas = utils.toHexStringQuantity(transaction.maxFeePerGas);
      this.max_priority_fee_per_gas = utils.toHexStringQuantity(transaction.maxPriorityFeePerGas);
      this.nonce = transaction.nonce;
      this.r = utils.toHexStringNonQuantity(transaction.signatureR);
      this.s = utils.toHexStringNonQuantity(transaction.signatureS);
      this.type = transaction.type;
      this.v = transaction.recoveryId;

      if (!_.isNil(transaction.callData)) {
        this.function_parameters = utils.toHexStringNonQuantity(transaction.callData);
      } else if (!contractResult.functionParameters.length && !_.isNil(fileData)) {
        this.function_parameters = utils.toHexStringNonQuantity(fileData.file_data);
      }
    }
  }
}

module.exports = ContractResultDetailsViewModel;
