/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

'uses strict';

// addressBook object. Parse string to object, provide methods to pull info

// external libraries
const _ = require('lodash');
const {NodeAddressBook} = require('@hashgraph/sdk/lib/generated/BasicTypes_pb');

class addressBook {
  constructor(buffer) {
    console.log(`Parsing address book`);
    let addressBook = NodeAddressBook.deserializeBinary(buffer);
    console.log(`${addressBook.getNodeaddressList().length} node(s) found in address book`);
    this.nodeList = addressBook.getNodeaddressList();
    this.nodeIdPublicKeyPairs = this.getNodeIdPublicKeyPairs();
  }

  getNodeIdPublicKeyPairs() {
    return _.map(this.nodeList, (nodeAddress) => {
      console.log(`Public key of node ${nodeAddress.getNodeid()} retrieved`);
      return {nodeId: nodeAddress.getNodeid(), publicKey: nodeAddress.getRsaPubkey};
    });
  }

  getNodeIds() {
    return _.map(this.nodeList, (nodeAddress) => {
      return nodeAddress.getMemo();
    });
  }
}

module.exports = {
  addressBook,
};
