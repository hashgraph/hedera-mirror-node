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

const Transaction = require('./transaction');

class TransactionWithEthData extends Transaction {
  /**
   * Parses ethereumTransaction table columns into the transaction object
   */
  constructor(txWithEthData) {
    super(txWithEthData);
    this.accessList = txWithEthData.access_list;
    this.callData = txWithEthData.call_data;
    this.callDataId = txWithEthData.call_data_id;
    this.chainId = txWithEthData.chain_id;
    this.gasPrice = txWithEthData.gas_price;
    this.gasLimit = txWithEthData.gas_limit;
    this.maxFeePerGas = txWithEthData.max_fee_per_gas;
    this.maxPriorityFeePerGas = txWithEthData.max_priority_fee_per_gas;
    this.nonce = txWithEthData.nonce;
    this.signatureR = txWithEthData.signature_r;
    this.signatureS = txWithEthData.signature_s;
    this.recoveryId = txWithEthData.recovery_id;
    this.value = txWithEthData.value;
  }
}

module.exports = TransactionWithEthData;
