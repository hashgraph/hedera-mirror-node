/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import AddressBookServiceEndpointViewModel from './addressBookServiceEndpointViewModel';
import EntityId from '../entityId';
import * as utils from '../utils';

/**
 * Network node view model
 */
class NetworkNodeViewModel {
  /**
   * Constructs network node view model
   *
   * @param {NetworkNode} networkNode
   */
  constructor(networkNode) {
    const {addressBookEntry, nodeStake} = networkNode;
    this.description = addressBookEntry.description;
    this.file_id = EntityId.parse(networkNode.addressBook.fileId).toString();
    this.max_stake = utils.asNullIfDefault(nodeStake.maxStake, -1);
    this.memo = addressBookEntry.memo;
    this.min_stake = utils.asNullIfDefault(nodeStake.minStake, -1);
    this.node_id = addressBookEntry.nodeId;
    this.node_account_id = EntityId.parse(addressBookEntry.nodeAccountId).toString();
    this.node_cert_hash = utils.addHexPrefix(utils.encodeUtf8(addressBookEntry.nodeCertHash), true);
    this.public_key = utils.addHexPrefix(addressBookEntry.publicKey, true);
    this.reward_rate_start = nodeStake.rewardRate;
    this.service_endpoints = networkNode.addressBookServiceEndpoints.map(
      (x) => new AddressBookServiceEndpointViewModel(x)
    );
    this.stake = nodeStake.stake;
    this.stake_not_rewarded = utils.asNullIfDefault(nodeStake.stakeNotRewarded, -1);
    this.stake_rewarded = nodeStake.stakeRewarded;
    this.staking_period = utils.getStakingPeriod(nodeStake.stakingPeriod);

    this.timestamp = {
      from: utils.nsToSecNs(networkNode.addressBook.startConsensusTimestamp),
      to: utils.nsToSecNs(networkNode.addressBook.endConsensusTimestamp),
    };
  }
}

export default NetworkNodeViewModel;
