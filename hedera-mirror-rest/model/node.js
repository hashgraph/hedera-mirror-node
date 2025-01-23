/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

class Node {
  /**
   * Parses node table columns into object
   */
  constructor(node) {
    this.adminKey = node.admin_key;
    this.createdTimestamp = node.created_timestamp;
    this.deleted = node.deleted;
    this.nodeId = node.node_id;
    this.timestampRange = node.timestamp_range;
  }

  static tableAlias = 'n';
  static tableName = 'node';

  static ADMIN_KEY = `admin_key`;
  static CREATED_TIMESTAMP = `created_timestamp`;
  static DELETED = `deleted`;
  static NODE_ID = `node_id`;
  static TIMESTAMP_RANGE = `timestamp_range`;

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

export default Node;
