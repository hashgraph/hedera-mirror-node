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

const _ = require('lodash');

const EntityId = require('../entityId');
const utils = require('../utils');
const {TransactionID} = require('@hashgraph/proto');

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
    this.chunk_info = _.isNil(topicMessage.chunkNum) ? null : new ChunkInfoViewModel(topicMessage);
    this.consensus_timestamp = utils.nsToSecNs(topicMessage.consensusTimestamp);
    this.message = utils.encodeBinary(topicMessage.message, messageEncoding);
    this.payer_account_id = EntityId.parse(topicMessage.payerAccountId).toString();
    this.running_hash = utils.encodeBase64(topicMessage.runningHash);
    this.running_hash_version = parseInt(topicMessage.runningHashVersion);
    this.sequence_number = parseInt(topicMessage.sequenceNumber);
    this.topic_id = EntityId.parse(topicMessage.topicId).toString();
  }
}

class ChunkInfoViewModel {
  constructor(topicMessage) {
    let initial_transaction_id, nonce, scheduled;
    if (!_.isNil(topicMessage.initialTransactionId)) {
      const transactionId = TransactionID.decode(topicMessage.initialTransactionId);
      initial_transaction_id = utils.createTransactionIdFromProto(transactionId);
      nonce = transactionId.nonce;
      scheduled = transactionId.scheduled;
    } else {
      initial_transaction_id = utils.createTransactionId(
        EntityId.parse(topicMessage.payerAccountId).toString(),
        topicMessage.validStartTimestamp
      );
      nonce = null;
      scheduled = null;
    }
    this.initial_transaction_id = initial_transaction_id;
    this.nonce = nonce;
    this.number = topicMessage.chunkNum;
    this.scheduled = scheduled;
    this.total = topicMessage.chunkTotal;
  }
}

module.exports = TopicMessageViewModel;
