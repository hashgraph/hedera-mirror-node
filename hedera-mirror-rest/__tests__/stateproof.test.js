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
const log4js = require('log4js');
const {mockRequest, mockResponse} = require('mock-req-res');
const {Readable} = require('stream');
const rewire = require('rewire');
const sinon = require('sinon');
const constants = require('../constants');
const config = require('../config');
const s3client = require('../s3client');
const stateproof = require('../stateproof');
const {CompositeRecordFile} = require('../stream');
const TransactionId = require('../transactionId');
const EntityId = require('../entityId');

const logger = log4js.getLogger();
// need to set the globals here so when __set__ them with rewire it won't throw ReferenceError 'xxx is not defined'
global.logger = logger;
global.pool = {};

afterEach(() => {
  global.pool = {};
  sinon.restore();
});

const emptyQueryResult = {
  rows: [],
};

/**
 * @param {sinon.fake} fake
 * @param {Number} expectedCount
 * @param {Array} expectedLastCallParams
 */
const verifyFakeCallCountAndLastCallParamsArg = (fake, expectedCount, expectedLastCallParams) => {
  expect(fake.callCount).toEqual(expectedCount);
  const actualParams = fake.args[fake.args.length - 1].slice(1);
  expect(actualParams).toEqual(expectedLastCallParams);
};

describe('getSuccessfulTransactionConsensusNs', () => {
  const expectedValidConsensusNs = '1234567891000000001';
  const validQueryResult = {
    rows: [{consensus_ns: expectedValidConsensusNs}],
  };
  const transactionId = TransactionId.fromString('0.0.1-1234567891-000111222');

  test('with transaction found in db table', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const consensusNs = await stateproof.getSuccessfulTransactionConsensusNs(transactionId);
    expect(consensusNs).toEqual(expectedValidConsensusNs);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      transactionId.getValidStartNs(),
    ]);
  });

  test('with transaction not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      transactionId.getValidStartNs(),
    ]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = {queryQuietly: fakeQuery};

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [
      transactionId.getEntityId().getEncodedId(),
      transactionId.getValidStartNs(),
    ]);
  });
});

describe('getRCDFileInfoByConsensusNs', () => {
  const consensusNs = '1578342501111222333';
  const expectedRCDFileName = '2020-02-09T18_30_25.001721Z.rcd';
  const validQueryResult = {
    rows: [{bytes: null, name: expectedRCDFileName, node_account_id: '3', version: 5}],
  };
  const expectedRCDFileInfo = {
    bytes: null,
    name: expectedRCDFileName,
    nodeAccountId: '0.0.3',
    version: 5,
  };

  test('with record file found', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    const info = await stateproof.getRCDFileInfoByConsensusNs(consensusNs);
    expect(info).toEqual(expectedRCDFileInfo);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with record file not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = {queryQuietly: fakeQuery};

    await expect(stateproof.getRCDFileInfoByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = {queryQuietly: fakeQuery};

    await expect(stateproof.getRCDFileInfoByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });
});

describe('getAddressBooksAndNodeAccountIdsByConsensusNs', () => {
  const nodeAccountId3 = EntityId.fromString('0.0.3');
  const nodeAccountId4 = EntityId.fromString('0.0.4');
  const nodeAccountId5 = EntityId.fromString('0.0.5');
  const nodeAccountIds = [nodeAccountId3, nodeAccountId4, nodeAccountId5];
  const validNodeAccountIds = _.join(
    _.map(nodeAccountIds, (id) => id.getEncodedId()),
    ','
  );
  const validMemos = _.join(
    _.map(nodeAccountIds, (id) => id.toString()),
    ','
  );
  const transactionConsensusNs = '1234567899000000021';

  let queryResultWithNodeAccountIds;
  let queryResultWithMemos;

  beforeEach(() => {
    queryResultWithNodeAccountIds = {
      rows: [
        {
          file_data: 'address book 1 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
        {
          file_data: 'address book 2 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
      ],
    };

    queryResultWithMemos = {
      rows: [
        {
          file_data: 'address book 1 data',
          node_count: 3,
          memos: validMemos,
        },
        {
          file_data: 'address book 2 data',
          node_count: 3,
          memos: validMemos,
        },
      ],
    };
  });

  const expectPassOrToThrow = async (queryResultOrFakeQueryFunc, expectPass) => {
    let queryResult = {rows: []};
    const queryStub = sinon.stub();
    if (typeof queryResultOrFakeQueryFunc === 'function') {
      queryStub.callsFake(queryResultOrFakeQueryFunc);
    } else {
      queryResult = queryResultOrFakeQueryFunc;
      queryStub.returns(queryResultOrFakeQueryFunc);
    }
    global.pool = {queryQuietly: queryStub};

    if (expectPass) {
      const result = await stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs);
      expect(result.addressBooks).toEqual(
        _.map(queryResult.rows, (row) => Buffer.from(row.file_data).toString('base64'))
      );

      let expectedNodeAccountIds;
      const lastRow = _.last(queryResult.rows);
      if (lastRow.node_account_ids) {
        expectedNodeAccountIds = _.map(lastRow.node_account_ids.split(','), (id) => EntityId.fromString(id).toString());
      } else {
        expectedNodeAccountIds = lastRow.memos.split(',');
      }
      expect(result.nodeAccountIds.sort()).toEqual(expectedNodeAccountIds.sort());

      expect(queryStub.callCount).toEqual(1);
    } else {
      await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
      expect(queryStub.callCount).toEqual(1);
    }
  };

  const selectQueryResult = (hasNodeAccountIds) =>
    hasNodeAccountIds ? queryResultWithNodeAccountIds : queryResultWithMemos;

  const testSpecs = [
    {
      name: 'query result has node_address_ids',
      hasNodeAddressIds: true,
      queryResultName: 'queryResultWithNodeAccountIds',
    },
    {
      name: 'query result has memos',
      hasNodeAddressIds: false,
      queryResultName: 'queryResultWithMemos',
    },
  ];

  testSpecs.forEach((spec) => {
    test(`with matching address books and node account IDs found - ${spec.name}`, async () => {
      await expectPassOrToThrow(selectQueryResult(spec.hasNodeAddressIds), true);
    });

    test(`with node address not found - ${spec.name}`, async () => {
      const queryResult = selectQueryResult(spec.hasNodeAddressIds);
      for (const row of queryResult.rows) {
        if (spec.hasNodeAddressIds) {
          delete row.node_account_ids;
        } else {
          delete row.memos;
        }
      }
      await expectPassOrToThrow(queryResult, false);
    });

    test(`with node address count mismatch count in last address book - ${spec.name}`, async () => {
      const queryResult = selectQueryResult(spec.hasNodeAddressIds);
      const row = _.last(queryResult.rows);
      row.node_count += 1;
      await expectPassOrToThrow(queryResult, false);
    });
  });

  test('with address book not found', async () => {
    await expectPassOrToThrow(emptyQueryResult, false);
  });

  test('with db runtime error', async () => {
    await expectPassOrToThrow(async () => {
      throw new Error('db runtime error');
    }, false);
  });
});

describe('downloadRecordStreamFilesFromObjectStorage', () => {
  const partialFilePaths = _.map([3, 4, 5, 6], (num) => `0.0.${num}/2020-02-09T18_30_25.001721Z.rcd_sig`);
  const extraFileContent = '123456790123456789012345678901234';
  const sliceSize = 5;

  beforeEach(() => {
    config.stateproof = {
      streams: {
        bucketName: 'test-bucket-name',
      },
    };
  });

  const stubS3ClientGetObject = (stub) => {
    sinon.stub(s3client, 'createS3Client').returns({
      getObject: stub,
    });
  };

  const verifyGetObjectStubAndReturnedFileObjects = (getObjectStub, fileObjects, failedNodes) => {
    let succeededPartialFilePaths = partialFilePaths;
    if (failedNodes) {
      succeededPartialFilePaths = _.filter(partialFilePaths, (partialFilePath) =>
        _.every(failedNodes, (failedNode) => !partialFilePath.startsWith(failedNode))
      );
    }

    expect(_.map(fileObjects, (file) => file.partialFilePath).sort()).toEqual(succeededPartialFilePaths.sort());
    for (const fileObject of fileObjects) {
      const data = constants.recordStreamPrefix + fileObject.partialFilePath + extraFileContent;
      expect(fileObject.data).toEqual(Buffer.from(data));
    }
    expect(getObjectStub.callCount).toEqual(partialFilePaths.length);
    for (const args of getObjectStub.args) {
      const params = args[0];
      expect(params.Key.startsWith(constants.recordStreamPrefix)).toBeTruthy();
      expect(params.RequestPayer).toEqual('requester');
    }
  };

  test('with all files downloaded successfully', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        const stream = new Readable({
          objectMode: true,
        });
        stream._read = function (size) {
          this.push(params.Key);
          let start = 0;
          while (start < extraFileContent.length) {
            const end = start + sliceSize;
            this.push(extraFileContent.slice(start, end));
            start = end;
          }
          this.push(null);
        };
        return stream;
      },
    }));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects);
  });

  test('with all files failed to download', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        let handler;
        const stream = new Readable();
        stream._read = function (size) {
          if (!handler) {
            handler = setTimeout(() => {
              this.emit('error', new Error('oops'));
            }, 1000);
          }
        };
        return stream;
      },
    }));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    const failedNodes = _.map([3, 4, 5, 6], (num) => `0.0.${num}`);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, failedNodes);
  });

  test('with download failed for 0.0.3', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        let handler;
        const stream = new Readable();
        stream._read = function (size) {
          if (params.Key.search('0.0.3') !== -1) {
            if (!handler) {
              handler = setTimeout(() => {
                this.emit('error', new Error('oops'));
              }, 1000);
            }
          } else {
            this.push(params.Key);
            let start = 0;
            while (start < extraFileContent.length) {
              const end = start + sliceSize;
              this.push(extraFileContent.slice(start, end));
              start = end;
            }
            this.push(null);
          }
        };
        return stream;
      },
    }));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, ['0.0.3']);
  });
});

describe('canReachConsensus', () => {
  test('with exactly 1/3 count', () => {
    expect(stateproof.canReachConsensus(1, 3)).toBeTruthy();
  });

  test('with more than 1/3 when rounded up', () => {
    expect(stateproof.canReachConsensus(2, 4)).toBeTruthy();
  });

  test('with less than 1/3', () => {
    expect(stateproof.canReachConsensus(1, 4)).toBeFalsy();
  });
});

describe('getStateProofForTransaction', () => {
  let stateproofRewired;
  let req;
  let res;

  const allNodeAccountIds = ['0.0.3', '0.0.4', '0.0.5', '0.0.6'];
  const defaultTransactionIdStr = '0.0.1-1234567891-111222333';
  const defaultTransactionConsensusNs = '1234567898111222333';
  const defaultRecordFilename = '2020-02-09T18_30_25.001721Z.rcd';
  const defaultRCDFileInfo = {bytes: null, name: defaultRecordFilename, nodeAccountId: '0.0.3', version: 5};
  const defaultAddressBooksAndNodeAccountIdsResult = {
    addressBooks: [
      Buffer.from('address book 1 data').toString('base64'),
      Buffer.from('address book 2 data').toString('base64'),
    ],
    nodeAccountIds: allNodeAccountIds,
  };

  const makeFileObjectFromPartialFilePath = (partialFilePath) => ({
    partialFilePath,
    data: Buffer.from(partialFilePath),
  });

  let defaultGetSuccessfulTransactionConsensusNsStub;
  let defaultGetRCDFileInfoByConsensusNsStub;
  let defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub;
  let defaultDownloadRecordStreamFilesFromObjectStorageStub;
  let alwaysThrowErrorStub;

  beforeEach(() => {
    stateproofRewired = rewire('../stateproof');
    req = mockRequest();
    res = mockResponse();

    defaultGetSuccessfulTransactionConsensusNsStub = sinon.stub().resolves(defaultTransactionConsensusNs);
    defaultGetRCDFileInfoByConsensusNsStub = sinon
      .stub(stateproof, 'getRCDFileInfoByConsensusNs')
      .resolves(defaultRCDFileInfo);
    defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub = sinon
      .stub()
      .resolves(defaultAddressBooksAndNodeAccountIdsResult);
    defaultDownloadRecordStreamFilesFromObjectStorageStub = sinon
      .stub()
      .callsFake(async (...partialFilePaths) =>
        _.map(partialFilePaths, (path) => makeFileObjectFromPartialFilePath(path))
      );
    alwaysThrowErrorStub = sinon.stub().throws(new Error('always throw error'));
  });

  const rewireAllDependency = (
    getSuccessfulTransactionConsensusNsStub,
    getRCDFileInfoByConsensusNsStub,
    getAddressBooksAndNodeAccountIdsByConsensusNsStub,
    downloadRecordStreamFilesFromObjectStorageStub
  ) => {
    stateproofRewired.__set__({
      getSuccessfulTransactionConsensusNs: getSuccessfulTransactionConsensusNsStub,
      getRCDFileInfoByConsensusNs: getRCDFileInfoByConsensusNsStub,
      getAddressBooksAndNodeAccountIdsByConsensusNs: getAddressBooksAndNodeAccountIdsByConsensusNsStub,
      downloadRecordStreamFilesFromObjectStorage: downloadRecordStreamFilesFromObjectStorageStub,
    });
  };

  const verifyResponseData = (responseData, rcdFileInfo, addressBooks, nodeAccountIds) => {
    expect(responseData).toBeTruthy();
    expect(responseData.record_file).toEqual(
      Buffer.from(`${rcdFileInfo.nodeAccountId}/${rcdFileInfo.name}`).toString('base64')
    );

    expect(Object.keys(responseData.signature_files).sort()).toEqual(nodeAccountIds.sort());
    for (const nodeAccountId of nodeAccountIds) {
      expect(responseData.signature_files[nodeAccountId]).toEqual(
        Buffer.from(`${nodeAccountId}/${rcdFileInfo.name}_sig`).toString('base64')
      );
    }

    expect(responseData.address_books.sort()).toEqual(addressBooks.sort());
  };

  test('with valid transaction ID and all data successfully retrieved', async () => {
    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    verifyResponseData(
      res.locals[constants.responseDataLabel],
      defaultRCDFileInfo,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks,
      defaultAddressBooksAndNodeAccountIdsResult.nodeAccountIds
    );

    expect(defaultGetSuccessfulTransactionConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetSuccessfulTransactionConsensusNsStub.args[0][0].toString()).toEqual(defaultTransactionIdStr);

    expect(defaultGetRCDFileInfoByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetRCDFileInfoByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toBeGreaterThanOrEqual(1);
    const {nodeAccountIds} = defaultAddressBooksAndNodeAccountIdsResult;
    const expectedPartialFilePaths = [
      `${nodeAccountIds[0]}/${defaultRecordFilename}`,
      ..._.map(nodeAccountIds, (nodeAccountId) => `${nodeAccountId}/${defaultRecordFilename}_sig`),
    ].sort();
    expect(_.flatten(defaultDownloadRecordStreamFilesFromObjectStorageStub.args).sort()).toEqual(
      expectedPartialFilePaths
    );
  });

  test('with invalid transaction ID', async () => {
    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.transactionId = '0.0.a-abcd-ddfff';
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getSuccessfulTransactionConsensusNs throw error', async () => {
    rewireAllDependency(
      alwaysThrowErrorStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getRCDFileInfoByConsensusNs throws error', async () => {
    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      alwaysThrowErrorStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getAddressBooksAndNodeAccountIdsByConsensusNs throws error', async () => {
    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      alwaysThrowErrorStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with downloadRecordStreamFilesFromObjectStorage throws error', async () => {
    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      alwaysThrowErrorStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
  });

  test('with downloadRecordStreamFilesFromObjectStorage fail to download all record stream files', async () => {
    const failAllRecordFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      return _.reject(partialFilePaths, (path) => path.endsWith('.rcd')).map((path) =>
        makeFileObjectFromPartialFilePath(path)
      );
    });

    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      failAllRecordFileDownloadStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failAllRecordFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });

  test('with downloadRecordStreamFilesFromObjectStorage fail to download one signature file', async () => {
    let failedNodeAccountId = '';
    const failOneSignatureFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      return _.reject(partialFilePaths, (path) => {
        if (!failedNodeAccountId && path.endsWith('_sig')) {
          failedNodeAccountId = _.first(path.split('/'));
          return true;
        }
        return false;
      }).map((path) => makeFileObjectFromPartialFilePath(path));
    });

    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      failOneSignatureFileDownloadStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    let {nodeAccountIds} = defaultAddressBooksAndNodeAccountIdsResult;
    nodeAccountIds = _.reject(nodeAccountIds, (nodeAccountId) => nodeAccountId === failedNodeAccountId);
    verifyResponseData(
      res.locals[constants.responseDataLabel],
      defaultRCDFileInfo,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks,
      nodeAccountIds
    );

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failOneSignatureFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });

  test('with not enough signature files to prove consensus', async () => {
    const nodesToFail = allNodeAccountIds.slice(0, Math.ceil((allNodeAccountIds.length * 2.0) / 3.0));
    const failSignatureFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      return _.reject(partialFilePaths, (path) => {
        const nodeAccountId = _.first(path.split('/'));
        return path.endsWith('_sig') && nodesToFail.includes(nodeAccountId);
      }).map((path) => makeFileObjectFromPartialFilePath(path));
    });

    rewireAllDependency(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileInfoByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      failSignatureFileDownloadStub
    );

    req.params.transactionId = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileInfoByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failSignatureFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });
});

describe('formatCompactableRecordFile', () => {
  test('format', () => {
    const stub = sinon.createStubInstance(CompositeRecordFile, {
      toCompactObject: {
        head: Buffer.from([1]),
        startRunningHashObject: Buffer.from([2]),
        hashesBefore: [Buffer.from([3])],
        recordStreamObject: Buffer.from([4]),
        hashesAfter: [Buffer.from([5])],
        endRunningHashObject: Buffer.from([6]),
      },
    });
    const expected = {
      head: Buffer.from([1]).toString('base64'),
      start_running_hash_object: Buffer.from([2]).toString('base64'),
      hashes_before: [Buffer.from([3]).toString('base64')],
      record_stream_object: Buffer.from([4]).toString('base64'),
      hashes_after: [Buffer.from([5]).toString('base64')],
      end_running_hash_object: Buffer.from([6]).toString('base64'),
    };

    const actual = stateproof.formatCompactableRecordFile(stub, '0.0.1-123-12345678', false);
    expect(actual).toEqual(expected);
  });

  test('error', () => {
    const stub = sinon.createStubInstance(CompositeRecordFile, {
      toCompactObject: sinon.stub().throws(new Error('oops')),
    });

    expect(() =>
      stateproof.formatCompactableRecordFile(stub, '0.0.1-123-12345678', false)
    ).toThrowErrorMatchingSnapshot();
  });
});
