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

const recordStreamsPath = path.join(appRoot.toString(), '__tests__', 'data', 'recordstreams');
const v2RecordStreamsPath = path.join(recordStreamsPath, 'v2');
const v5RecordStreamsPath = path.join(recordStreamsPath, 'v5');

const v5CompactObject = {
  head: Buffer.from('AAAABQAAAAAAAAALAAAAAAAAAAE=', 'base64'),
  startRunningHashObject: Buffer.from(
    '9CLag6JRdB4AAAABWP+BGwAAADCud7M8enNR1VnLI6uc1Ln222yhqC8R8FfNFiq21' + 'nQfl/q/scOcEsOYkwfEI8BRfJY=',
    'base64'
  ),
  hashesBefore: [Buffer.from('INTQVAfkXFE7r5lamB6cMSYHk7M/vo4tOWJuEMhGQtyAx4Wpo4IJZN8WrbtuwQ5k', 'base64')],
  recordStreamObject: Buffer.from(
    '43CSm6VCnYsAAAABAAAApAooCBYqJAoQCLDqARDf0hsaBgiwxZSCBhIQCLDqARDsvhsaB' +
      'gjA4ZSCBhIw7vNajFsqgp9b8TTqjKAxfxitKWgolfilwaWsfWQxW6dO+vnYekUA2atxFHCs7incGgsIoLOUggYQmK6iQiITCgsIk7' +
      'OUggYQpsymfxIEGPOlFjC5igRSIAoICgIYBhDCpCkKCAoCGGIQltUHCgoKBBjzpRYQ1/kwAAAAqSqmAQo8ChMKCwiTs5SCBhCmzKZ' +
      '/EgQY86UWEgIYBhiAwtcvIgIIeHIYChYKCgoEGPOlFhDl5CgKCAoCGAYQ5uQoEmYKZAog0eqX8yNwOga24Q+NhFG70E6wdKKlmnO6' +
      'HsIoJ7ummVEaQIdN4PDVgJ4YvwocjDUPr/x5YjVXLD0xozvGX6PksQMTo8FvDGfkdOFAevnUxj5ATnSGEMRh/9JZ5kdQeJVVKQI=',
    'base64'
  ),
  hashesAfter: [
    Buffer.from('xmnu4v4hvSyASngNeo1UNQlihHkLmXTpML2+KQZQ1815J0KGa9eVRBNK29YmDUHZ', 'base64'),
    Buffer.from('kYqZBpNgMXbbCiDoGlq4TrOY6unqQPaXeNCeFAmhsxMsMewIdkTTJvHezXAuOAKS', 'base64'),
    Buffer.from('V1jWi0tiFxgBZzWeir2NHmS4b/HCvd9Zr+DEMaHti0uUJrNAq6Nz4xZ8VO2EyygI', 'base64'),
    Buffer.from('yxNUNITQnYcKV78iPnsS62aYYwxjwIL2yzGzo3zcRw2R3+DwKMeXXFeRmDHsA7m5', 'base64'),
    Buffer.from('Se83t98i5KUcD8jItkXxehdQte2u4QepQ0GXuQufHiKPf+NdsoWFTkYHpFs3JRyF', 'base64'),
  ],
  endRunningHashObject: Buffer.from(
    '9CLag6JRdB4AAAABWP+BGwAAADCnPUjHQKqq+l6HnYdlD17Zt15RTB5ZCLaGD8+37c3' + 'gCTVQ+ENgVbpWAY9G1yAAGjE=',
    'base64'
  ),
};

const testRecordFiles = {
  v2: [
    {
      buffer: fs.readFileSync(path.join(v2RecordStreamsPath, '2021-01-26T18_05_00.032280Z.rcd')),
      checks: [
        {
          func: 'containsTransaction',
          args: ['0.0.123128-1611684290-402410035'],
          expected: true,
        },
        {
          func: 'containsTransaction',
          args: ['0.0.1-123-123456789'],
          expected: false,
        },
        {
          func: 'getFileHash',
          args: [],
          expected: Buffer.from('HBbDl2r1C9Uojbe0Gkb9FYrkMt7IYLl+tFpEFmUD1gb2oosKGeHRjO+kCgWIH9sw', 'base64'),
        },
        {
          func: 'getMetadataHash',
          args: [],
          expected: null,
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {
            '0.0.123128-1611684289-109863383-false': 0,
            '0.0.123128-1611684290-402410035-false': 1,
            '0.0.123128-1611684291-632478870-false': 2,
            '0.0.123128-1611684289-402138954-false': 3,
            '0.0.123128-1611684291-498208118-false': 4,
          },
        },
        {
          func: 'getVersion',
          args: [],
          expected: 2,
        },
        {
          func: 'toCompactObject',
          args: ['0.0.365299-1615141267-266970662'],
          expectErr: true,
        },
      ],
    },
  ],
  v5: [
    {
      buffer: fs.readFileSync(path.join(v5RecordStreamsPath, '2021-03-07T18_21_20.041164000Z.rcd')),
      checks: [
        {
          func: 'containsTransaction',
          args: ['0.0.365299-1615141267-604167594'],
          expected: true,
        },
        {
          func: 'containsTransaction',
          args: ['0.0.1-123-123456789'],
          expected: false,
        },
        {
          func: 'getFileHash',
          args: [],
          expected: null,
        },
        {
          func: 'getMetadataHash',
          args: [],
          expected: null,
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {
            '0.0.365299-1615141267-604167594-false': 0,
            '0.0.365299-1615141267-266970662-false': 1,
            '0.0.365299-1615141269-24812188-false': 2,
            '0.0.365299-1615141271-650221876-false': 3,
            '0.0.365299-1615141267-669386363-false': 4,
            '0.0.365299-1615141269-430266882-false': 5,
            '0.0.88-1615141270-697933000-false': 6,
          },
        },
        {
          func: 'getVersion',
          args: [],
          expected: 5,
        },
        {
          func: 'toCompactObject',
          args: ['0.0.365299-1615141267-266970662'],
          expected: v5CompactObject,
        },
        {
          func: 'toCompactObject',
          args: ['0.0.1-123-123456789'],
          expectErr: true,
        },
      ],
    },
    {
      obj: v5CompactObject,
      checks: [
        {
          func: 'containsTransaction',
          args: ['0.0.365299-1615141267-266970662'],
          expected: true,
        },
        {
          func: 'containsTransaction',
          args: ['0.0.365299-1615141267-604167594'],
          expected: false,
        },
        {
          func: 'getFileHash',
          args: [],
          expected: null,
        },
        {
          func: 'getMetadataHash',
          args: [],
          expected: Buffer.from('c0tZ6aeUL5VcHp2h/cCbJf4K5YdSGylCfxo4l1sinKU1e5XUqUKStZL84yBFg41l', 'base64'),
        },
        {
          func: 'getTransactionMap',
          args: [],
          expected: {'0.0.365299-1615141267-266970662': undefined},
        },
        {
          func: 'getVersion',
          args: [],
          expected: 5,
        },
        {
          func: 'toCompactObject',
          args: ['0.0.365299-1615141267-266970662'],
          expected: v5CompactObject,
        },
      ],
    },
  ],
};

const testSignatureFiles = {
  v2: {
    path: path.join(v2RecordStreamsPath, '2021-01-26T18_05_00.032280Z.rcd_sig'),
    expected: {},
  },
  v5: {
    path: path.join(v2RecordStreamsPath, '2021-03-07T18_21_20.041164000Z.rcd_sig'),
    expected: {},
  },
};

const commonRecordFileTests = (version, clazz, hasRunningHash = false) => {
  const getTestRecordFiles = (version) => testRecordFiles[`v${version}`];

  describe('check individual field', () => {
    getTestRecordFiles(version).forEach((testSpec) => {
      const {buffer, obj, checks} = testSpec;
      const bufferOrObj = buffer || obj;
      const name = `from v${version} ${buffer ? 'buffer' : 'compact object'}`;

      checks.forEach((check) => {
        test(`${name} - ${check.func} - ${JSON.stringify(check.args)}`, () => {
          const recordFile = new clazz(bufferOrObj);
          const fn = recordFile[check.func];
          if (!check.expectErr) {
            const actual = fn.apply(recordFile, check.args);
            expect(actual).toEqual(check.expected);
          } else {
            expect(() => fn.apply(recordFile, check.args)).toThrowErrorMatchingSnapshot();
          }
        });
      });
    });
  });

  test(`v${version} buffer with extra data`, () => {
    const buffer = Buffer.concat([getTestRecordFiles(version)[0].buffer, Buffer.from([0])]);
    expect(() => new clazz(buffer)).toThrowErrorMatchingSnapshot();
  });

  test(`truncated v${version} buffer`, () => {
    const buffer = getTestRecordFiles(version)[0].buffer;
    expect(() => new clazz(buffer.slice(0, buffer.length - 1))).toThrowErrorMatchingSnapshot();
  });

  if (hasRunningHash) {
    test('end running hash mismatch', () => {
      // make a shallow copy, change the last byte of the end running hash object
      const obj = {...getTestRecordFiles(version)[1].obj};
      const badEndRunningHashObject = Buffer.from(obj.endRunningHashObject);
      const lastIndex = badEndRunningHashObject.length - 1;
      badEndRunningHashObject[lastIndex] = badEndRunningHashObject[lastIndex] ^ 0xff;
      obj.endRunningHashObject = badEndRunningHashObject;

      expect(() => new clazz(obj)).toThrowErrorMatchingSnapshot();
    });
  }
};

const copyRecordFileAndSetVersion = (buffer, version) => {
  const copy = Buffer.from(buffer);
  copy.writeInt32BE(version);
  return copy;
};

module.exports = {
  testRecordFiles,
  testSignatureFiles,
  commonRecordFileTests,
  copyRecordFileAndSetVersion,
};
