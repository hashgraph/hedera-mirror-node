/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

'use strict';

const _ = require('lodash');
const log4js = require('log4js');
const { mockRequest, mockResponse } = require('mock-req-res');
const { Readable } = require('stream');
const rewire = require('rewire');
const sinon = require('sinon');
const constants = require('../constants');
const config = require('../config');
const { FileDownloadError } = require('../errors/fileDownloadError');
const s3client = require('../s3client');
const stateproof = require('../stateproof');
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
  expect(fake.lastArg.sort()).toEqual(expectedLastCallParams.sort());
};

describe('getSuccessfulTransactionConsensusNs', () => {
  const expectedValidConsensusNs = '1234567891000000001';
  const validQueryResult = {
    rows: [{ consensus_ns: expectedValidConsensusNs }],
  };
  const transactionId = TransactionId.fromString('0.0.1-1234567891-000111222');

  test('with transaction found in db table', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = { query: fakeQuery };

    const consensusNs = await stateproof.getSuccessfulTransactionConsensusNs(transactionId);
    expect(consensusNs).toEqual(expectedValidConsensusNs);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });

  test('with transaction not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = { query: fakeQuery };

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = { query: fakeQuery };

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });
});

describe('getRCDFileNameByConsensusNs', () => {
  const consensusNs = '1578342501111222333';
  const expectedRCDFileName = '2020-02-09T18_30_25.001721Z.rcd';
  const validQueryResult = {
    rows: [{ name: expectedRCDFileName }],
  };

  test('with record file found', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = { query: fakeQuery };

    const fileName = await stateproof.getRCDFileNameByConsensusNs(consensusNs);
    expect(fileName).toEqual(expectedRCDFileName);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with record file not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = { query: fakeQuery };

    await expect(stateproof.getRCDFileNameByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = { query: fakeQuery };

    await expect(stateproof.getRCDFileNameByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });
});

describe('getAddressBooksAndNodeAccountIdsByConsensusNs', () => {
  const nodeAccountId3 = EntityId.fromString('0.0.3');
  const nodeAccountId4 = EntityId.fromString('0.0.4');
  const nodeAccountId5 = EntityId.fromString('0.0.5');
  const validNodeAccountIds = _.join([nodeAccountId3.getEncodedId(), nodeAccountId4.getEncodedId(),
    nodeAccountId5.getEncodedId()], ',');
  const transactionConsensusNs = '1234567899000000021';

  let queryResult;

  beforeEach(() => {
    queryResult = {
      rows: [
        {
          consensus_timestamp: '1234567891000000001',
          file_data: 'address book 1 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
        {
          consensus_timestamp: '1234567899000000001',
          file_data: 'address book 2 data',
          node_count: 3,
          node_account_ids: validNodeAccountIds,
        },
      ],
    };
  });

  const expectPassOrToThrow = async (queryResultOrFakeQueryFunc, expectPass) => {
    const queryStub = sinon.stub();
    if (typeof queryResultOrFakeQueryFunc === 'function') {
      queryStub.callsFake(queryResultOrFakeQueryFunc);
    } else {
      queryStub.returns(queryResultOrFakeQueryFunc);
    }
    global.pool = { query: queryStub };

    if (expectPass) {
      const result = await stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs);
      expect(result.addressBooks).toEqual(_.map(queryResult.rows,
        (row) => Buffer.from(row.file_data).toString('base64')));
      expect(result.nodeAccountIds.sort()).toEqual(
        _.map(validNodeAccountIds.split(','), (id) => EntityId.fromEncodedId(id).toString()).sort(),
      );

      expect(queryStub.callCount).toEqual(1);
    } else {
      await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
      expect(queryStub.callCount).toEqual(1);
    }
  };

  test('with matching address books and node account IDs found', async () => {
    await expectPassOrToThrow(queryResult, true);
  });

  test('with address book not found', async () => {
    await expectPassOrToThrow(emptyQueryResult, false);
  });

  test('with node address not found', async () => {
    for (const row of queryResult.rows) {
      delete row.node_account_ids;
    }
    await expectPassOrToThrow(queryResult, false);
  });

  test('with node address count mismatch count in last address book', async () => {
    const row = _.last(queryResult.rows);
    row.node_count += 1;
    await expectPassOrToThrow(queryResult, false);
  });

  test('with db runtime error', async () => {
    await expectPassOrToThrow(async () => {
      throw new Error('db runtime error');
    }, false);
  });
});

describe('downloadRecordStreamFilesFromObjectStorage', () => {
  const partialFilePaths = _.map([3, 4, 5, 6], (num) => `0.0.${num}/2020-02-09T18_30_25.001721Z.rcd_sig`);

  beforeEach(() => {
    config.stateproof = {
      streams: {
        bucketName: 'test-bucket-name',
        record: {
          prefix: 'recordstreams/record',
        },
      },
    };
  });

  const stubS3ClientGetObject = (stub) => {
    sinon.stub(s3client, 'createS3Client').returns({
      getObject: stub,
    });
  };

  const verifyGetObjectStubAndReturnedFileObjects = (getObjectStub, fileObjects, partialFilePaths, failedNodes) => {
    let succeededPartialFilePaths = partialFilePaths;
    if (failedNodes) {
      succeededPartialFilePaths = _.filter(partialFilePaths,
        (partialFilePath) => _.every(failedNodes, (failedNode) => !partialFilePath.startsWith(failedNode)));
    }

    expect(_.map(fileObjects, (file) => file.partialFilePath).sort()).toEqual(succeededPartialFilePaths.sort());
    for (const fileObject of fileObjects) {
      expect(fileObject.base64Data)
        .toEqual(Buffer.from(config.stateproof.streams.record.prefix + fileObject.partialFilePath).toString('base64'));
    }
    expect(getObjectStub.callCount).toEqual(partialFilePaths.length);
  };

  test('with all files downloaded successfully', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        const stream = new Readable({
          objectMode: true,
        });
        stream._read = function(size) {
          this.push(params.Key);
          this.push(null);
        };
        return stream;
      },
    }));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, partialFilePaths);
  });

  test('with all files failed to download', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        let handler;
        const stream = new Readable();
        stream._read = function(size) {
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
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, partialFilePaths,
      _.map([3, 4, 5, 6], (num) => `0.0.${num}`));
  });

  test('with download failed for 0.0.3', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => ({
      createReadStream: () => {
        let handler;
        const stream = new Readable();
        stream._read = function(size) {
          if (params.Key.search('0.0.3') !== -1) {
            if (!handler) {
              handler = setTimeout(() => {
                this.emit('error', new Error('oops'));
              }, 1000);
            }
          } else {
            this.push(params.Key);
            this.push(null);
          }
        };
        return stream;
      },
    }));
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, partialFilePaths, ['0.0.3']);
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

  const defaultTransactionIdStr = '0.0.1-1234567891-111222333';
  const defaultTransactionConsensusNs = '1234567898111222333';
  const defaultRecordFilename = '2020-02-09T18_30_25.001721Z.rcd';
  const defaultAddressBooksAndNodeAccountIdsResult = {
    addressBooks: [
      Buffer.from('address book 1 data').toString('base64'),
      Buffer.from('address book 2 data').toString('base64'),
    ],
    nodeAccountIds: [
      '0.0.3',
      '0.0.4',
      '0.0.5',
    ],
  };

  const makeFileObjectFromPartialFilePath = (partialFilePath) => ({
    partialFilePath,
    base64Data: Buffer.from(partialFilePath).toString('base64'),
  });

  let defaultGetSuccessfulTransactionConsensusNsStub;
  let defaultGetRCDFileNameByConsensusNsStub;
  let defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub;
  let defaultDownloadRecordStreamFilesFromObjectStorageStub;
  let alwaysThrowErrorStub;

  beforeEach(() => {
    stateproofRewired = rewire('../stateproof');
    req = mockRequest();
    res = mockResponse();

    defaultGetSuccessfulTransactionConsensusNsStub = sinon.stub().resolves(defaultTransactionConsensusNs);
    defaultGetRCDFileNameByConsensusNsStub = sinon
      .stub(stateproof, 'getRCDFileNameByConsensusNs').resolves(defaultRecordFilename);
    defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub = sinon
      .stub().resolves(defaultAddressBooksAndNodeAccountIdsResult);
    defaultDownloadRecordStreamFilesFromObjectStorageStub = sinon.stub()
      .callsFake(async (...partialFilePaths) => _.map(
        partialFilePaths,
        (partialFilePath) => makeFileObjectFromPartialFilePath(partialFilePath),
      ));
    alwaysThrowErrorStub = sinon.stub().throws(new Error('always throw error'));
  });

  const rewireAllDependencyFunctions = (
    getSuccessfulTransactionConsensusNsStub,
    getRCDFileNameByConsensusNsStub,
    getAddressBooksAndNodeAccountIdsByConsensusNsStub,
    downloadRecordStreamFilesFromObjectStorageStub,
  ) => {
    stateproofRewired.__set__({
      getSuccessfulTransactionConsensusNs: getSuccessfulTransactionConsensusNsStub,
      getRCDFileNameByConsensusNs: getRCDFileNameByConsensusNsStub,
      getAddressBooksAndNodeAccountIdsByConsensusNs: getAddressBooksAndNodeAccountIdsByConsensusNsStub,
      downloadRecordStreamFilesFromObjectStorage: downloadRecordStreamFilesFromObjectStorageStub,
    });
  };

  const verifyResponseData = (responseData, recordFileName, addressBooks, nodeAccountIds) => {
    expect(responseData).toBeTruthy();
    expect(responseData.record_file)
      .toEqual(Buffer.from(`0.0.3/${recordFileName}`).toString('base64'));

    expect(Object.keys(responseData.signature_files).sort()).toEqual(nodeAccountIds.sort());
    for (const nodeAccountId of nodeAccountIds) {
      expect(responseData.signature_files[nodeAccountId])
        .toEqual(Buffer.from(`${nodeAccountId}/${recordFileName}_sig`).toString('base64'));
    }

    expect(responseData.address_books.sort())
      .toEqual(addressBooks.sort());
  };

  test('with valid transaction ID and all data successfully retrieved', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub,
    );

    req.params.id = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    verifyResponseData(res.locals[constants.responseDataLabel], defaultRecordFilename,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks,
      defaultAddressBooksAndNodeAccountIdsResult.nodeAccountIds);

    expect(defaultGetSuccessfulTransactionConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetSuccessfulTransactionConsensusNsStub.args[0][0].toString()).toEqual(defaultTransactionIdStr);

    expect(defaultGetRCDFileNameByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetRCDFileNameByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toBeGreaterThanOrEqual(1);
    const { nodeAccountIds } = defaultAddressBooksAndNodeAccountIdsResult;
    const expectedPartialFilePaths = [
      `${nodeAccountIds[0]}/${defaultRecordFilename}`,
      ..._.map(nodeAccountIds, (nodeAccountId) => `${nodeAccountId}/${defaultRecordFilename}_sig`),
    ].sort();
    expect(_.flatten(defaultDownloadRecordStreamFilesFromObjectStorageStub.args).sort())
      .toEqual(expectedPartialFilePaths);
  });

  test('with invalid transaction ID', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub,
    );

    req.params.id = '0.0.a-abcd-ddfff';
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getSuccessfulTransactionConsensusNs throw error', async () => {
    rewireAllDependencyFunctions(
      alwaysThrowErrorStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub,
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(0);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getRCDFileNameByConsensusNs throws error', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      alwaysThrowErrorStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub,
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(0);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with getAddressBooksAndNodeAccountIdsByConsensusNs throws error', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      alwaysThrowErrorStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub,
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toEqual(0);
  });

  test('with downloadRecordStreamFilesFromObjectStorage throws error', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      alwaysThrowErrorStub,
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(alwaysThrowErrorStub.callCount).toEqual(1);
  });

  test('with downloadRecordStreamFilesFromObjectStorage fail to download all record stream files', async () => {
    const failAllRecordFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      const result = [];
      for (const partialFilePath of partialFilePaths) {
        if (!partialFilePath.endsWith('.rcd')) {
          result.push(makeFileObjectFromPartialFilePath(partialFilePath));
        }
      }

      if (!result) {
        throw new FileDownloadError('Unable to download all files');
      }

      return result;
    });

    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      failAllRecordFileDownloadStub,
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failAllRecordFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });

  test('with downloadRecordStreamFilesFromObjectStorage fail to download one signature file', async () => {
    let failedNodeAccountId = '';
    const failOneSignatureFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      const result = [];

      for (const partialFilePath of partialFilePaths) {
        if (!failedNodeAccountId && partialFilePath.endsWith('_sig')) {
          failedNodeAccountId = _.first(partialFilePath.split('/'));
        } else {
          result.push(makeFileObjectFromPartialFilePath(partialFilePath));
        }
      }

      if (!result) {
        throw new FileDownloadError('Unable to download all files');
      }

      return result;
    });

    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      failOneSignatureFileDownloadStub,
    );

    req.params.id = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    let { nodeAccountIds } = defaultAddressBooksAndNodeAccountIdsResult;
    nodeAccountIds = _.reject(nodeAccountIds, (nodeAccountId) => nodeAccountId === failedNodeAccountId);
    verifyResponseData(res.locals[constants.responseDataLabel], defaultRecordFilename,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks, nodeAccountIds);

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failOneSignatureFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });
});
