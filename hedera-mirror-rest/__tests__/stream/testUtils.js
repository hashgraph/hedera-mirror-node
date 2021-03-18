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
          expected: {'0.0.365299-1615141267-266970662-false': null},
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
    buffer: fs.readFileSync(path.join(v2RecordStreamsPath, '2021-01-26T18_05_00.032280Z.rcd_sig')),
    expected: {
      fileHash: Buffer.from('HBbDl2r1C9Uojbe0Gkb9FYrkMt7IYLl+tFpEFmUD1gb2oosKGeHRjO+kCgWIH9sw', 'base64'),
      fileHashSignature: Buffer.from(
        'UZyy+eKQPYVKtpdtqA4CIRm0L2QhwNhBj3k5Uw8QO82Kp4tv4B2kePRhLQ7dJ6H9epHtxKLywLjOxb+Mt2E7KI2GB1/7rGEfm' +
          'MaO6uqoN4hwkaAHN7Cyu5IH5cVJklHB/RGAIPLKASF7lhrPU4g+FpQZBRJCPe9dqMQFNfiORIioZ3DlS05XS7aLnC5PtWQzg3' +
          'azU5+j30qQva0tiOfqPN57d0c4iqptJGO55/WHSS3FAe4JRdcksm/WLXrxMdGb6JUqGeOKAd2z7eib+HiHrv5gmj1iC5XBZS1' +
          'Nadd7G1QLe404RN2Afeger6eUIvLoaliaUegDh04syaFoXa+ufCuNfV5LeHWSjM+n+5WhKY2D675CTcnY1dwpoF5pcS7yCAOL' +
          'ha7qZqCfKw66JVVOFRL/IkNpWCMkdphEr7BpRypne8oeZfOYFTdYaTllsAhr2YvpgvxFSUG0L3+XeTqhYxBOApzQ+Ew9Ze4Wz' +
          '87rkBv4yzux8aeZ4f2gx364KpM/',
        'base64'
      ),
      version: 2,
    },
  },
  v5: {
    buffer: fs.readFileSync(path.join(v5RecordStreamsPath, '2021-03-07T18_21_20.041164000Z.rcd_sig')),
    expected: {
      fileHash: Buffer.from('67uxdUQ8Q2k/h3NEYLtIoN/Pi4Qh8G30hNOy01uOOEaNdApIF7LG2W+ph8lrnv+j', 'base64'),
      fileHashSignature: Buffer.from(
        'N5oKZ7MiEYM7VP9v9W80jivv175reKdS1KVhkZ25H4PVaAqnZw8ZctNfu/kPP4uOott8MuWVAX+ZQ54SQnsa5fCu0mRmziVxF' +
          '3p+i90IdRqykEzeMd18Mf/tvkxA8SVCGkfL35g6rKg/wnAhpz59nrhZwF1L1wD/i24GNnUcKxaRtRuPKLc0SNnTzh96kKaAu6' +
          'HLk2cODmYOaSkMU9U/k/k9U0a8qwrBP7cvhsvGJL/J+m+na6bdMuYBzRrdF5G+5K3fGGFiEp1TZXOXdWEvH7NqvtHgx3JhtXr' +
          'M2FhBITzaeg9xOeoSG5ZZiO5X6xJ8xg+tq7k9vOIz9CkBcqSIT+zeHKX6ep0GD5vXTbN/7gUVk1jDAZVJ3Z+SzxudTUi3M7/g' +
          'ALzyZ5Y3Uc/DjqaSG4tWlm+1u73GKzz8MLntp+yQratu5atHC2QW10VIGefz8yyr+A31ne8toLi35u+kImEdviqdBsyldtHCs' +
          '25VcC1ZI4vT6IUQHwqEsO7EbX1n',
        'base64'
      ),
      metadataHash: Buffer.from('c0tZ6aeUL5VcHp2h/cCbJf4K5YdSGylCfxo4l1sinKU1e5XUqUKStZL84yBFg41l', 'base64'),
      metadataHashSignature: Buffer.from(
        'Qy4YQ+aZZB+EawoT3Ysc60+uvUQ6jjaCSAXzx3TtHJelkUK5fJW5l0MkqJlxL2M9Naw8qoRfBI210mD7UbXgs9Q3lmDk/FWDF' +
          'OG/F1sTUQvlpteh4DpkaOo2i+l4JL+gOYAZgFGRxNdsK4jrnU2TcJVR6WEQ9p4f6rRWs0q4b+PN9CBqvvXdBgQ0OQB8iQ3YlD' +
          '6LGWlCD98usHKExRUcWyciQTnhg7RVxp/e0qUMj2pa5jv7BbkX3zCSNbPH6F3qdlT44WIAKY/ygygC2hagrJc9yvrwREG36Aj' +
          'UYGcnktaN3ZsQL15XNPP0tAYddiilngRGGF9xuDqnn7gIJc1Ovv3lVwe+77PZpjDhGgQ3+OBf5Y7v2Iy9I0ZBZKuXyVZHvpwa' +
          'qjaIPY2dFzhi0HHL7nyo7tR46uTnYI6sNu8d7KEcFylWmaMVlVUVTp5hceecr7VKrJ7S+wlELDOfoLcwYIzt+iY4j4Tapp9Q8' +
          'dektc9/PkqtiysCM84v7Pvq3tvx',
        'base64'
      ),
      version: 5,
    },
  },
};

const copyRecordFileAndSetVersion = (buffer, version) => {
  const copy = Buffer.from(buffer);
  copy.writeInt32BE(version);
  return copy;
};

const testRecordFileUnsupportedVersion = (versions, clazz) => {
  const v2Buffer = testRecordFiles.v2[0].buffer;
  const testSpecs = versions.map((version) => [version, copyRecordFileAndSetVersion(v2Buffer, version)]);

  testSpecs.forEach((testSpec) => {
    const [version, buffer] = testSpec;
    test(`create object from version ${version}`, () => {
      expect(() => new clazz(buffer)).toThrowErrorMatchingSnapshot();
    });
  });
};

const testRecordFileCanCompact = (testSpecs, clazz) => {
  const v2Buffer = testRecordFiles.v2[0].buffer;
  testSpecs
    .map(([version, expected]) => {
      return {
        version,
        buffer: copyRecordFileAndSetVersion(v2Buffer, version),
        expected,
      };
    })
    .forEach((testSpec) => {
      const {version, buffer, expected} = testSpec;
      test(`version ${version} - ${expected ? 'can compact' : 'cannot compact'}`, () => {
        expect(clazz.canCompact(buffer)).toEqual(expected);
      });
    });
};

const testRecordFileFromBufferOrObj = (version, clazz, supportObj = false, hasRunningHash = false) => {
  const getTestRecordFiles = (ver) => testRecordFiles[`v${ver}`];

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
    const {buffer} = getTestRecordFiles(version)[0];
    expect(() => new clazz(buffer.slice(0, buffer.length - 1))).toThrowErrorMatchingSnapshot();
  });

  if (!supportObj) {
    test('from non-Buffer obj', () => {
      expect(() => new clazz({})).toThrowErrorMatchingSnapshot();
    });
  }

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

module.exports = {
  testSignatureFiles,
  testRecordFileUnsupportedVersion,
  testRecordFileCanCompact,
  testRecordFileFromBufferOrObj,
};
