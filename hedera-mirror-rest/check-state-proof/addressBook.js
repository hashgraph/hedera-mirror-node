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

// addressBook object. Parse string to object, provide methods to pull info

// external libraries
const log4js = require('log4js');
const {proto} = require('@hashgraph/proto/lib/proto');

const logger = log4js.getLogger();

class AddressBook {
  /**
   * Parses address book file storing map of node account id -> rsa public key
   */
  constructor(addressBook) {
    this.parseAddressBookBuffer(addressBook);
    this.setNodeAccountIdPublicKeyPairs();
  }

  parseAddressBookBuffer(addressBookBuffer) {
    const addressBook = proto.NodeAddressBook.decode(addressBookBuffer);
    logger.info(`${addressBook.nodeAddress.length} node(s) found in address book`);
    this.nodeList = addressBook.nodeAddress;
  }

  setNodeAccountIdPublicKeyPairs() {
    this.nodeAccountIdPublicKeyPairs = Object.fromEntries(
      this.nodeList.map((nodeAddress) => {
        const {memo, nodeAccountId, RSA_PubKey} = nodeAddress;
        // For some address books nodeAccountId does not exist, in those cases retrieve id from memo field
        const nodeAccountIdStr = nodeAccountId
          ? [nodeAccountId.shardNum, nodeAccountId.realmNum, nodeAccountId.accountNum].join('.')
          : memo.toString('utf-8');
        return [nodeAccountIdStr, RSA_PubKey];
      })
    );
  }
}

module.exports = AddressBook;
