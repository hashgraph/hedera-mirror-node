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

    this.access_list = transaction.access_list ? utils.addHexPrefix(transaction.access_list) : null;
    this.chain_id = transaction.chain_id ? utils.addHexPrefix(transaction.chain_id) : null;
    this.gas_price = transaction.gas_price ? utils.addHexPrefix(transaction.gas_price) : null;
    this.max_fee_per_gas = transaction.max_fee_per_gas ? utils.addHexPrefix(transaction.max_fee_per_gas) : null;
    this.max_priority_fee_per_gas = transaction.max_priority_fee_per_gas
      ? utils.addHexPrefix(transaction.max_priority_fee_per_gas)
      : null;
    this.r = transaction.signature_r ? utils.addHexPrefix(transaction.signature_r) : null;
    this.s = transaction.signature_s ? utils.addHexPrefix(transaction.signature_s) : null;
    this.type = transaction.ethType || null;
    this.v = transaction.recovery_id;

    // TODO this.gas_price = ethereum_transation.gas_price

    if (`${transaction.type}` === TransactionType.getProtoId('ETHEREUMTRANSACTION')) {
      txHash = _.isNil(transaction.ethHash) ? transaction.transactionHash : transaction.ethHash;

      if (!_.isNil(transaction.value)) {
        this.amount = long.fromValue(Buffer.from(transaction.value, 'utf8').toString()).toNumber();
      }

      if (!_.isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!_.isNil(transaction.call_data)) {
        this.function_parameters = utils.addHexPrefix(transaction.call_data);
      } else {
        if (!contractResult.functionParameters.length && !_.isNil(fileData)) {
          this.function_parameters = utils.toHexString(fileData.fileData, true);
        }
      }

      if (!_.isNil(transaction.gas_limit)) {
        this.gas_limit = transaction.gas_limit;
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
