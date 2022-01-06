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

const EntityId = require('../entityId');
const utils = require('../utils');

/**
 * Topic message view model
 */
class TopicMessageViewModel {
  /**
   * Constructs topicMessage view model
   *
   * @param {TopicMessage} topicMessage
   * @param {String} messageEncoding the encoding to display the message in
   */
  constructor(topicMessage, messageEncoding) {
    Object.assign(this, {
      chunk_number: topicMessage.chunkNum,
      chunk_total: topicMessage.chunkTotal,
      consensus_timestamp: utils.nsToSecNs(topicMessage.consensusTimestamp),
      topic_id: EntityId.parse(topicMessage.topicId).toString(),
      message: utils.encodeBinary(topicMessage.message, messageEncoding),
      payer_account_id: EntityId.parse(topicMessage.payerAccountId).toString(),
      running_hash: utils.encodeBase64(topicMessage.runningHash),
      running_hash_version: parseInt(topicMessage.runningHashVersion),
      sequence_number: parseInt(topicMessage.sequenceNumber),
      valid_start_timestamp: utils.nsToSecNs(topicMessage.validStartTimestamp),
    });
  }
}

module.exports = TopicMessageViewModel;
