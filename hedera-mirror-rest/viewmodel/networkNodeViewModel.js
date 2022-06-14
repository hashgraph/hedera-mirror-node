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

const _ = require('lodash');

const AddressBookServiceEndpointViewModel = require('./addressBookServiceEndpointViewModel');
const EntityId = require('../entityId');
const utils = require('../utils');

/**
 * Network node view model
 */
class NetworkNodeViewModel {
  /**
   * Constructs network node view model
   *
   * @param {AddressBook} addressBook
   */
  constructor(networkNode) {
    this.description = networkNode.addressBookEntry.description;
    this.file_id = EntityId.parse(networkNode.addressBook.fileId).toString();
    this.memo = networkNode.addressBookEntry.memo;
    this.node_id = networkNode.addressBookEntry.nodeId;
    this.node_account_id = EntityId.parse(networkNode.addressBookEntry.nodeAccountId).toString();
    this.node_cert_hash = utils.addHexPrefix(utils.encodeUtf8(networkNode.addressBookEntry.nodeCertHash), true);
    this.public_key = utils.addHexPrefix(networkNode.addressBookEntry.publicKey, true);
    this.service_endpoints = networkNode.addressBookServiceEndpoints.map(
      (x) => new AddressBookServiceEndpointViewModel(x)
    );
    this.stake = networkNode.nodeStake.stake;
    this.stake_rewarded = networkNode.nodeStake.stakeRewarded;
    this.stake_total = networkNode.nodeStake.stakeTotal;
    this.staking_period = {
      from: utils.decrementTimestampByOneDay(networkNode.nodeStake.stakingPeriod),
      to: utils.nsToSecNs(networkNode.nodeStake.stakingPeriod),
    };
    this.timestamp = {
      from: utils.nsToSecNs(networkNode.addressBook.startConsensusTimestamp),
      to: utils.nsToSecNs(networkNode.addressBook.endConsensusTimestamp),
    };
  }
}

module.exports = NetworkNodeViewModel;
