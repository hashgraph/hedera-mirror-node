/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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
const utils = require('./utils.js');

const getTopicMessage = (req, res) => {
  logger.debug('Client: [' + req.ip + '] URL: ' + req.originalUrl + ', params: ' + JSON.stringify(req.params));
  const topicId = req.params.id;
  const seqNum = req.params.seqnum;

  const sqlParams = [topicId, seqNum];
  const messageQuery = getTopicMessageQuery();

  logger.debug('getTopicMessage query: ' + req.query + JSON.stringify(req.params));

  // Execute query
  return pool.query(messageQuery, sqlParams).then(results => {
    let ret = {
      topicmessages: null,
      links: {
        next: null
      }
    };

    ret.topicmessages = processResults(results);

    if (ret.topicmessages === null) {
      res.status(404).send('Not found');
      return;
    }

    if (process.env.NODE_ENV === 'test') {
      ret.sqlQuery = results.sqlQuery;
    }

    res.json(ret);
  });
};

const getTopicMessageQuery = () => {
  return (
    'select consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number' +
    ' from topic_message where topic_num = $1 and sequence_number = $2 limit 1'
  );
};

const processResults = results => {
  logger.info('getTopicMessage returned: ' + JSON.stringify(results));

  if (results.rowCount > 1) {
    logger.warn('getTopicMessage returned more than one message: ' + JSON.stringify(results));
  }

  let message = results.rows[0];

  // format consensus_timestamp, message and running_hash
  message.consensus_timestamp = utils.nsToSecNs(message['consensus_timestamp']);
  message.message = utils.encodeBase64(message['message']);
  message.running_hash = utils.encodeBase64(message['running_hash']);

  return message;
};

module.exports = {
  getTopicMessage: getTopicMessage
};
