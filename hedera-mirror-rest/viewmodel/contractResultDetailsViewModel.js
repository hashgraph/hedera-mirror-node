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
const ContractLogResultsViewModel = require('./contractResultLogViewModel');
const ContractResultStateChangeViewModel = require('./contractResultStateChangeViewModel');
const ContractResultViewModel = require('./contractResultViewModel');
const {TransactionResult, TransactionType} = require('../model');
const utils = require('../utils');
const EntityId = require('../entityId');
const long = require('long');

/**
 * Contract result details view model
 */
class ContractResultDetailsViewModel extends ContractResultViewModel {
  static _FAIL_PROTO_ID = Number.parseInt(TransactionResult.getSuccessProtoId());
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

    let txHash = transaction.transactionHash;

    this.block_hash = utils.addHexPrefix(recordFile.hash);
    this.block_number = recordFile.index;
    this.logs = contractLogs.map((contractLog) => new ContractLogResultsViewModel(contractLog));
    this.result = TransactionResult.getName(transaction.result);
    this.transaction_index = transaction.index;
    this.nonce = transaction.nonce;

    this.access_list = transaction.accessList ? utils.addHexPrefix(transaction.accessList) : null;
    this.chain_id = transaction.chainId ? utils.addHexPrefix(transaction.chainId) : null;
    this.gas_price = transaction.gasPrice ? utils.addHexPrefix(transaction.gasPrice) : null;
    this.max_fee_per_gas = transaction.maxFeePerGas ? utils.addHexPrefix(transaction.maxFeePerGas) : null;
    this.max_priority_fee_per_gas = transaction.maxPriorityFeePerGas
      ? utils.addHexPrefix(transaction.maxPriorityFeePerGas)
      : null;
    this.r = transaction.signatureR ? utils.addHexPrefix(transaction.signatureR) : null;
    this.s = transaction.signatureS ? utils.addHexPrefix(transaction.signatureS) : null;
    this.type = transaction.ethType || null;
    this.v = transaction.recoveryId;

    if (`${transaction.type}` === TransactionType.getProtoId('ETHEREUMTRANSACTION')) {
      txHash = _.isNil(transaction.ethHash) ? transaction.transactionHash : transaction.ethHash;

      if (!_.isNil(transaction.value)) {
        this.amount = long.fromValue(Buffer.from(transaction.value, 'utf8').toString()).toNumber();
      }

      if (!_.isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!_.isNil(transaction.callData)) {
        this.function_parameters = utils.addHexPrefix(transaction.callData);
      } else {
        if (!contractResult.functionParameters.length && !_.isNil(fileData)) {
          this.function_parameters = utils.toHexString(fileData.file_data, true);
        }
      }

      if (!_.isNil(transaction.gasLimit)) {
        this.gas_limit = transaction.gasLimit;
      }
    }

    this.hash = utils.toHexString(txHash, true);

    this.state_changes = contractStateChanges.map(
      (contractStateChange) => new ContractResultStateChangeViewModel(contractStateChange)
    );
    this.status =
      transaction.result === ContractResultDetailsViewModel._FAIL_PROTO_ID
        ? ContractResultDetailsViewModel._SUCCESS_RESULT
        : ContractResultDetailsViewModel._FAIL_RESULT;
  }
}

module.exports = ContractResultDetailsViewModel;
