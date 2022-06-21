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

'use strict';

const AddressBook = require('./addressBook');
const AddressBookEntry = require('./addressBookEntry');
const AddressBookServiceEndpoint = require('./addressBookServiceEndpoint');
const NodeStake = require('./nodeStake');

class NetworkNode {
  /**
   * Parses address book related table columns into object
   */
  constructor(networkNodeDb) {
    this.addressBook = new AddressBook(networkNodeDb);
    this.addressBookEntry = new AddressBookEntry(networkNodeDb);
    this.addressBookServiceEndpoints = networkNodeDb.service_endpoints.map((x) => new AddressBookServiceEndpoint(x));
    this.nodeStake = new NodeStake(networkNodeDb);
  }
}

module.exports = NetworkNode;
