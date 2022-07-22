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

class RecordFile {
  /**
   * Parses record_file table columns into object
   */
  constructor(recordFile) {
    Object.assign(
      this,
      _.mapKeys(recordFile, (v, k) => _.camelCase(k))
    );
  }

  static tableAlias = 'rf';
  static tableName = 'record_file';

  static BYTES = 'bytes';
  static CONSENSUS_END = 'consensus_end';
  static CONSENSUS_START = 'consensus_start';
  static COUNT = 'count';
  static DIGEST_ALGORITHM = 'digest_algorithm';
  static FILE_HASH = 'file_hash';
  static GAS_USED = 'gas_used';
  static INDEX = 'index';
  static HAPI_VERSION_MAJOR = 'hapi_version_major';
  static HAPI_VERSION_MINOR = 'hapi_version_minor';
  static HAPI_VERSION_PATCH = 'hapi_version_patch';
  static HASH = 'hash';
  static LOAD_END = 'load_end';
  static LOAD_START = 'load_start';
  static LOGS_BLOOM = 'logs_bloom';
  static NAME = 'name';
  static NODE_ACCOUNT_ID = 'node_account_id';
  static PREV_HASH = 'prev_hash';
  static SIZE = 'size';
  static VERSION = 'version';

  /**
   * Gets full column name with table alias prepended.
   *
   * @param {string} columnName
   * @private
   */
  static getFullName(columnName) {
    return `${this.tableAlias}.${columnName}`;
  }

  getFullHapiVersion() {
    return `${this.hapiVersionMajor}.${this.hapiVersionMinor}.${this.hapiVersionPatch}`;
  }
}

export default RecordFile;
