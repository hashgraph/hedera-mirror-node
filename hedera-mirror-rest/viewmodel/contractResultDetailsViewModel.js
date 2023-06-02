/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

import _ from 'lodash';
import {toBigIntBE} from '@trufflesuite/bigint-buffer';

import ContractLogResultsViewModel from './contractResultLogViewModel';
import ContractResultStateChangeViewModel from './contractResultStateChangeViewModel';
import ContractResultViewModel from './contractResultViewModel';
import EntityId from '../entityId';
import {TransactionResult} from '../model';
import * as utils from '../utils';

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
   * @param {EthereumTransaction} ethTransaction
   * @param {ContractLog[]} contractLogs
   * @param {ContractStateChange[]} contractStateChanges
   * @param {FileData} fileData
   */
  constructor(contractResult, recordFile, ethTransaction, contractLogs, contractStateChanges, fileData) {
    super(contractResult);

    this.block_hash = utils.addHexPrefix(recordFile.hash);
    this.block_number = recordFile.index;
    this.hash = utils.toHexStringNonQuantity(contractResult.transactionHash);
    this.logs = contractLogs.map((contractLog) => new ContractLogResultsViewModel(contractLog));
    this.result = TransactionResult.getName(contractResult.transactionResult);
    this.transaction_index = contractResult.transactionIndex;

    this.state_changes = contractStateChanges.map(
      (contractStateChange) => new ContractResultStateChangeViewModel(contractStateChange)
    );
    const isTransactionSuccessful =
      contractResult.transactionResult === ContractResultDetailsViewModel._SUCCESS_PROTO_ID;
    this.status = isTransactionSuccessful
      ? ContractResultDetailsViewModel._SUCCESS_RESULT
      : ContractResultDetailsViewModel._FAIL_RESULT;
    if (!_.isEmpty(contractResult.failedInitcode)) {
      this.failed_initcode = utils.toHexStringNonQuantity(contractResult.failedInitcode);
    } else if (
      this.status === ContractResultDetailsViewModel._FAIL_RESULT &&
      !_.isNil(ethTransaction) &&
      !_.isEmpty(ethTransaction.callData)
    ) {
      this.failed_initcode = utils.toHexStringNonQuantity(ethTransaction.callData);
    } else {
      this.failed_initcode = null;
    }

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

    if (!_.isNil(ethTransaction)) {
      this.access_list = utils.toHexStringNonQuantity(ethTransaction.accessList);
      this.amount = ethTransaction.value && toBigIntBE(ethTransaction.value);
      this.chain_id = utils.toHexStringQuantity(ethTransaction.chainId);

      if (!isTransactionSuccessful && _.isEmpty(contractResult.errorMessage)) {
        this.error_message = this.result;
      }

      if (!_.isNil(contractResult.senderId)) {
        this.from = EntityId.parse(contractResult.senderId).toEvmAddress();
      }

      if (!_.isNil(ethTransaction.gasLimit)) {
        this.gas_limit = ethTransaction.gasLimit;
      }
      this.gas_price = utils.toHexStringQuantity(ethTransaction.gasPrice);
      this.max_fee_per_gas = utils.toHexStringQuantity(ethTransaction.maxFeePerGas);
      this.max_priority_fee_per_gas = utils.toHexStringQuantity(ethTransaction.maxPriorityFeePerGas);
      this.nonce = ethTransaction.nonce;
      this.r = utils.toHexStringNonQuantity(ethTransaction.signatureR);
      this.s = utils.toHexStringNonQuantity(ethTransaction.signatureS);
      this.type = ethTransaction.type;
      this.v = ethTransaction.recoveryId;

      if (!_.isEmpty(ethTransaction.callData)) {
        this.function_parameters = utils.toHexStringNonQuantity(ethTransaction.callData);
      } else if (!contractResult.functionParameters.length && !_.isNil(fileData)) {
        this.function_parameters = utils.toHexStringNonQuantity(fileData.file_data);
      }
    }
  }
}

export default ContractResultDetailsViewModel;
