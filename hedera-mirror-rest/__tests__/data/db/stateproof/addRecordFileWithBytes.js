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

const appRoot = require('app-root-path');
const fs = require('fs');
const path = require('path');

const recordFilePath = path.join(
  appRoot.toString(),
  '__tests__',
  'data',
  'db',
  'stateproof',
  './2021-03-06T22_31_12.056022000Z.rcd'
);

module.exports = async (sqlConnection) => {
  const data = fs.readFileSync(recordFilePath);
  const fields = [
    'name',
    'load_start',
    'load_end',
    'hash',
    'prev_hash',
    'consensus_start',
    'consensus_end',
    'node_account_id',
    'count',
    'digest_algorithm',
    'hapi_version_major',
    'hapi_version_minor',
    'hapi_version_patch',
    'version',
    'file_hash',
    'bytes',
    'index',
  ];
  const query = [
    `insert into record_file (${fields.join(',')})`,
    `values (${fields.map((_, index) => `$${index + 1}`).join(',')})`,
  ].join('\n');
  const params = [
    '2021-03-06T22_31_12.056022000Z.rcd',
    1615069877,
    1615069877,
    'f2a928ff2f3b1217910d2d4469fea1a603f19e42b6e2088462f17fc6299b597cadedbd8c264049b360d34fca9fa92578',
    '0e23db92250641deb65d2c6a18c18e693e61e3d9ba6d1336f018041a6c5a4b501a57de651743192fc2e2f8dfc657faec',
    '1615069872056022000',
    '1615069873560485000',
    3,
    9,
    0,
    0,
    11,
    0,
    5,
    'c3032392cccfebc9669f995a93a43142b933bdaaf676877c67947f3341e5ecf9b65d5560f5aada1eb67bfae0e968deea',
    data,
    5397027,
  ];
  await sqlConnection.query(query, params);
};
