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

class ContractTransaction {
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static CONTRACT_ID = 'contract_id';
  static INVOLVED_CONTRACT_IDS = 'involved_contract_ids';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static tableAlias = 'ct';
  static tableName = 'contract_transaction';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
  /**
   * Parses contract_transaction table columns into object
   */
  constructor(contractTransaction, idColumn = 'contract_id') {
    this.consensusTimestamp = contractTransaction[ContractTransaction.CONSENSUS_TIMESTAMP];
    this.contractId = contractTransaction[ContractTransaction.CONTRACT_ID];
    this.involvedContractIds = contractTransaction[ContractTransaction.INVOLVED_CONTRACT_IDS] || [];
    this.payerAccountId = contractTransaction[ContractTransaction.PAYER_ACCOUNT_ID];
    this.query = `${idColumn} in (${this.involvedContractIds.join(',')})`;
  }
}
export default ContractTransaction;
