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

const {BlockService} = require('../../service');

const integrationDbOps = require('../integrationDbOps');
const integrationDomainOps = require('../integrationDomainOps');

const recordFiles = [
  {
    index: 16,
    count: 3,
    hapi_version_major: 0,
    hapi_version_minor: 22,
    hapi_version_patch: 3,
    name: '2022-04-27T12_09_24.499938763Z.rcd',
    prev_hash: '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000',
    consensus_start: '1676540001234390000',
    consensus_end: '1676540001234490000',
    hash: 'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
  },
  {
    index: 17,
    count: 5,
    hapi_version_major: 0,
    hapi_version_minor: 22,
    hapi_version_patch: 3,
    name: '2022-04-27T12_24_30.768994443Z.rcd',
    prev_hash: 'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c',
    consensus_start: '1676540001234500000',
    consensus_end: '1676540001234600000',
    hash: 'b0162e8a244dc05fbd6f321445b14dddf0e94b00eb169b58ff77b1b5206c12782457f7f1a2ae8cea890f378542ac7216',
  },
];

const {defaultMochaStatements} = require('./defaultMochaStatements');
defaultMochaStatements(jest, integrationDbOps, integrationDomainOps);

const expectToEqualId16 = (blockId16) => {
  expect(blockId16.index).toEqual('16');
  expect(blockId16.count).toEqual('3');
  expect(blockId16.name).toEqual('2022-04-27T12_09_24.499938763Z.rcd');
  expect(blockId16.hash).toEqual(
    'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c'
  );
  expect(blockId16.prevHash).toEqual(
    '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000'
  );
  expect(blockId16.hapiVersionMajor).toEqual(0);
  expect(blockId16.hapiVersionMinor).toEqual(22);
  expect(blockId16.hapiVersionPatch).toEqual(3);
  expect(blockId16.consensusStart).toEqual('1676540001234390000');
  expect(blockId16.consensusEnd).toEqual('1676540001234490000');
};

const expectToEqualId17 = (blockId17) => {
  expect(blockId17.index).toEqual('17');
  expect(blockId17.count).toEqual('5');
  expect(blockId17.name).toEqual('2022-04-27T12_24_30.768994443Z.rcd');
  expect(blockId17.hash).toEqual(
    'b0162e8a244dc05fbd6f321445b14dddf0e94b00eb169b58ff77b1b5206c12782457f7f1a2ae8cea890f378542ac7216'
  );
  expect(blockId17.prevHash).toEqual(
    'fbd921184e229e2051280d827ba3b31599117af7eafba65dc0e5a998b70c48c0492bf793a150769b1b4fb2c9b7cb4c1c'
  );
  expect(blockId17.hapiVersionMajor).toEqual(0);
  expect(blockId17.hapiVersionMinor).toEqual(22);
  expect(blockId17.hapiVersionPatch).toEqual(3);
  expect(blockId17.consensusStart).toEqual('1676540001234500000');
  expect(blockId17.consensusEnd).toEqual('1676540001234600000');
};

describe('BlockService tests', () => {
  test('Verify buildWhereSqlStatement with no params', async () => {
    const {where, params} = BlockService.buildWhereSqlStatement([]);
    expect(where).toEqual('where true=true');
    expect(params).toEqual([]);
  });

  test('Verify buildWhereSqlStatement with 1 query', async () => {
    const {where, params} = BlockService.buildWhereSqlStatement([{query: 'index >', param: '15'}]);

    expect(where).toEqual('where true=true and index > $1 ');
    expect(params).toEqual(['15']);
  });

  test('Verify buildWhereSqlStatement with 2 queries', async () => {
    const {where, params} = BlockService.buildWhereSqlStatement([
      {query: 'index <', param: '10'},
      {query: 'timestamp >', param: '1651064877.265800774'},
    ]);

    expect(where).toEqual('where true=true and index < $1  and timestamp > $2 ');
    expect(params).toEqual(['10', '1651064877.265800774']);
  });

  test('Verify getBlocks without filters', async () => {
    await integrationDomainOps.loadRecordFiles(recordFiles);

    const blocks = await BlockService.getBlocks({
      whereQuery: [],
      order: 'asc',
      limit: 25,
    });

    expect(blocks.length).toEqual(2);

    expectToEqualId16(blocks[0]);
    expectToEqualId17(blocks[1]);
  });

  test('Verify getBlocks with block.number filter and order desc', async () => {
    await integrationDomainOps.loadRecordFiles(recordFiles);

    const blocks = await BlockService.getBlocks({
      whereQuery: [{query: 'index <', param: '17'}],
      order: 'asc',
      limit: 25,
    });

    expect(blocks.length).toEqual(1);
    expectToEqualId16(blocks[0]);
  });

  test('Verify getBlocks with limit 1 and order desc', async () => {
    await integrationDomainOps.loadRecordFiles(recordFiles);

    const blocks = await BlockService.getBlocks({
      whereQuery: [],
      order: 'desc',
      limit: 1,
    });

    expect(blocks.length).toEqual(1);
    expectToEqualId17(blocks[0]);
  });
});
