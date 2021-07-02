/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

const {EntityId} = require('../entityId');
const utils = require('../utils');

class Nft {
  /**
   * Parses nft table columns into object
   */
  constructor(
    autoRenewAccountId,
    autoRenewPeriod,
    createdTimestamp,
    deleted,
    expirationTimestamp,
    id,
    memo,
    modifiedTimestamp,
    num,
    publicKey,
    proxyAccountId,
    realm,
    shard,
    submitKey,
    type
  ) {
    this.auto_renew_account_id = EntityId.fromEncodedId(autoRenewAccountId).toString();
    this.auto_renew_period = autoRenewPeriod === null ? null : Number(autoRenewPeriod);
    this.created_timestamp = createdTimestamp === null ? null : utils.nsToSecNs(createdTimestamp);
    this.deleted = deleted;
    this.expiration_timestamp = expirationTimestamp === null ? null : utils.nsToSecNs(expirationTimestamp);
    this.id = EntityId.fromEncodedId(id).toString();
    // exclude key
    this.memo = memo;
    this.modified_timestamp = modifiedTimestamp === null ? null : utils.nsToSecNs(modifiedTimestamp);
    this.num = num;
    this.public_key = publicKey === null ? null : utils.encodeKey(publicKey); // base64 encode
    this.proxy_account_id = EntityId.fromEncodedId(proxyAccountId).toString();
    this.realm = realm;
    this.shard = shard;
    this.submit_key = submitKey === null ? null : utils.encodeKey(submitKey); // base64 encode
    this.type = type;
  }

  static tableAlias = 'e';
  static tableName = 'entity';
  static nftQueryColumns = {
    AUTO_RENEW_ACCOUNT_ID: `${this.tableAlias}.auto_renew_account_id`,
    AUTO_RENEW_PERIOD: `${this.tableAlias}.auto_renew_period`,
    CREATED_TIMESTAMP: `${this.tableAlias}.created_timestamp`,
    DELETED: `${this.tableAlias}.deleted`,
    EXPIRATION_TIMESTAMP: `${this.tableAlias}.expiration_timestamp`,
    ID: `${this.tableAlias}.id`,
    MEMO: `${this.tableAlias}.memo`,
    MODIFIED_TIMESTAMP: `${this.tableAlias}.modified_timestamp`,
    NUM: `${this.tableAlias}.num`,
    PUBLIC_KEY: `${this.tableAlias}.public_key`,
    PROXY_ACCOUNT_ID: `${this.tableAlias}.proxy_account_id`,
    REALM: `${this.tableAlias}.realm`,
    SHARD: `${this.tableAlias}.shard`,
    SUBMIT_KEY: `${this.tableAlias}.submit_key`,
    TYPE: `${this.tableAlias}.type`,
  };
}

module.exports = Nft;
