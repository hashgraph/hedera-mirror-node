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

class ContractTransactionHash {
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static ENTITY_ID = 'entity_id';
  static HASH = 'hash';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static TRANSACTION_RESULT = 'transaction_result';
  static tableName = 'contract_transaction_hash';

  /**
   * Parses contract_transaction_hash table columns into object
   */
  constructor(transactionHash) {
    this.consensusTimestamp = transactionHash[ContractTransactionHash.CONSENSUS_TIMESTAMP];
    this.entityId = transactionHash[ContractTransactionHash.ENTITY_ID];
    this.hash = transactionHash[ContractTransactionHash.HASH];
    this.payerAccountId = transactionHash[ContractTransactionHash.PAYER_ACCOUNT_ID];
  }
}

export default ContractTransactionHash;
