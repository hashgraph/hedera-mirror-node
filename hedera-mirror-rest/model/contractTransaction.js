/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
  static ENTITY_ID = 'entity_id';
  static CONTRACT_IDS = 'contract_ids';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static tableName = 'contract_transaction';

  /**
   * Parses contract_transaction table columns into object
   */
  constructor(contractTransaction) {
    this.consensusTimestamp = contractTransaction[ContractTransaction.CONSENSUS_TIMESTAMP];
    this.entityId = contractTransaction[ContractTransaction.ENTITY_ID];
    this.contractIds = contractTransaction[ContractTransaction.CONTRACT_IDS] || [];
    this.payerAccountId = contractTransaction[ContractTransaction.PAYER_ACCOUNT_ID];
  }
}
export default ContractTransaction;
