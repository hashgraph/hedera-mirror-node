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

import _ from 'lodash';

class TopicMessage {
  /**
   * Parses topic_message table columns into object
   */
  constructor(topicMessage) {
    Object.assign(
      this,
      _.mapKeys(topicMessage, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'tm';
  static tableName = 'topic_message';

  static CHUNK_NUM = 'chunk_num';
  static CHUNK_TOTAL = 'chunk_total';
  static CONSENSUS_TIMESTAMP = 'consensus_timestamp';
  static INITIAL_TRANSACTION_ID = 'initial_transaction_id';
  static MESSAGE = 'message';
  static PAYER_ACCOUNT_ID = 'payer_account_id';
  static RUNNING_HASH = 'running_hash';
  static RUNNING_HASH_VERSION = 'running_hash_version';
  static SEQUENCE_NUMBER = 'sequence_number';
  static TOPIC_ID = 'topic_id';
  static VALID_START_TIMESTAMP = 'valid_start_timestamp';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }
}

export default TopicMessage;
