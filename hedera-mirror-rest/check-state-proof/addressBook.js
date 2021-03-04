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
const _ = require('lodash');
const {NodeAddressBook} = require('@hashgraph/sdk/lib/generated/BasicTypes_pb');

class AddressBook {
  /**
   * Parses address book file storing map of nodeid -> rsa public key
   */
  constructor(addressBook) {
    this.parseAddressBookBuffer(addressBook);
    this.setNodeIdPublicKeyPairs();
  }

  parseAddressBookBuffer(addressBookBuffer) {
    const addressBookObject = NodeAddressBook.deserializeBinary(addressBookBuffer);
    console.log(`${addressBookObject.getNodeaddressList().length} node(s) found in address book`);
    this.nodeList = addressBookObject.getNodeaddressList();
  }

  setNodeIdPublicKeyPairs() {
    this.nodeIdPublicKeyPairs = {};
    _.forEach(this.nodeList, (nodeAddress) => {
      let node;
      // For some address books node id does not contain node id. In those cases retrieve id from memo field
      if (_.isUndefined(nodeAddress.getNodeid()) || nodeAddress.getNodeid().indexOf('.') < 1) {
        node = Buffer.from(nodeAddress.getMemo()).toString('utf-8');
      } else {
        node = nodeAddress.getNodeid();
      }

      this.nodeIdPublicKeyPairs[node] = {publicKey: nodeAddress.getRsaPubkey()};
    });
  }
}

module.exports = {
  AddressBook,
};
