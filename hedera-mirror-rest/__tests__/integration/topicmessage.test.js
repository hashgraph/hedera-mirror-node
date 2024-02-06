/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

import {Range} from 'pg-range';

import * as constants from '../../constants';
import integrationDomainOps from '../integrationDomainOps';
import {setupIntegrationTest} from '../integrationUtils';
import * as utils from '../../utils';

import topicMessage from '../../topicmessage';

setupIntegrationTest();

const {ASC, DESC} = constants.orderFilterValues;
const {SEQUENCE_NUMBER, TIMESTAMP} = constants.filterKeys;
const {eq, gt, gte, lt, lte, ne} = utils.opsMap;

describe('getTopicMessageTimestampRanges', () => {
  beforeEach(async () => {
    await integrationDomainOps.loadTopicMessageLookups([
      {partition: '2022_05', sequence_number_range: '[1, 30)', timestamp_range: '[1000, 2000)', topic_id: 123},
      {partition: '2022_06', sequence_number_range: '[30, 60)', timestamp_range: '[2000, 3000)', topic_id: 123},
      {partition: '2022_07', sequence_number_range: '[60, 90)', timestamp_range: '[3000, 4000)', topic_id: 123},
    ]);
  });

  const spec = [
    {name: 'empty filters', expected: [Range(1000, 2000, '[)'), Range(2000, 3000, '[)')]},
    {name: 'empty filters descending', order: DESC, expected: [Range(2000, 3000, '[)'), Range(3000, 4000, '[)')]},
    {
      name: 'descending with largest limit for two partitions',
      limit: 31,
      order: DESC,
      expected: [Range(2000, 3000, '[)'), Range(3000, 4000, '[)')],
    },
    {
      name: 'descending with smallest limit for three partitions',
      limit: 32,
      order: DESC,
      expected: [Range(1000, 2000, '[)'), Range(2000, 3000, '[)'), Range(3000, 4000, '[)')],
    },
    {
      name: 'sequence number upper bound and ascending expect first partition',
      sequenceNumberFilters: [{key: SEQUENCE_NUMBER, operator: lt, value: 30}],
      expected: [Range(1000, 2000, '[)')],
    },
    {
      name: 'sequence number lower bound and descending expect last partition',
      sequenceNumberFilters: [{key: SEQUENCE_NUMBER, operator: gte, value: 60}],
      expected: [Range(3000, 4000, '[)')],
    },
    {
      name: 'sequence number lower bound after largest in db',
      sequenceNumberFilters: [{key: SEQUENCE_NUMBER, operator: gte, value: 90}],
    },
    {
      name: 'sequence number lower bound after largest in db and descending',
      sequenceNumberFilters: [{key: SEQUENCE_NUMBER, operator: gte, value: 90}],
      order: DESC,
    },
    {
      name: 'sequence number eq filter',
      sequenceNumberFilters: [{key: SEQUENCE_NUMBER, operator: eq, value: 5}],
      expected: [Range(1000, 2000, '[)')],
    },
    {
      name: 'multiple sequence number eq filter',
      sequenceNumberFilters: [
        {key: SEQUENCE_NUMBER, operator: eq, value: 5},
        {key: SEQUENCE_NUMBER, operator: eq, value: 6},
      ],
    },
    {
      name: 'effectively empty sequence number filter',
      sequenceNumberFilters: [
        {key: SEQUENCE_NUMBER, operator: gte, value: 10},
        {key: SEQUENCE_NUMBER, operator: lt, value: 10},
      ],
    },
    {
      name: 'effectively empty timestamp filter',
      sequenceNumberFilters: [
        {key: TIMESTAMP, operator: gte, value: 2000},
        {key: TIMESTAMP, operator: lt, value: 2000},
      ],
    },
    {
      name: 'no intersection of sequence number and timestamp filters',
      sequenceNumberFilters: [
        {key: SEQUENCE_NUMBER, operator: gte, value: 1},
        {key: SEQUENCE_NUMBER, operator: lt, value: 60},
      ],
      timestampFilters: [
        {key: TIMESTAMP, operator: gte, value: 3000},
        {key: TIMESTAMP, operator: lt, value: 4000},
      ],
    },
    {name: `topic lookup for topic id doesn't exist`, topicId: 125},
  ];

  test.each(spec)(
    '$name',
    async ({
      topicId = 123,
      sequenceNumberFilters = [],
      timestampFilters = [],
      limit = 25,
      order = ASC,
      expected = [],
    }) => {
      await expect(
        topicMessage.getTopicMessageTimestampRanges(topicId, sequenceNumberFilters, timestampFilters, limit, order)
      ).resolves.toEqual(expected);
    }
  );
});
