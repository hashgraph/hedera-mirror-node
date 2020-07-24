/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
const config = require('../config');
const constants = require('../constants');
const log4js = require('log4js');
const {mockRequest, mockResponse} = require('mock-req-res');
const Readable = require('stream').Readable;
const rewire = require('rewire');
const sinon = require('sinon');
const {FileDownloadError} = require('../errors/fileDownloadError');
const {InvalidConfigError} = require('../errors/invalidConfigError');
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
  rows: []
};

/**
 * @param {sinon.fake} fake
 * @param {Number} expectedCount
 * @param {Array} expectedLastCallParams
 */
const verifyFakeCallCountAndLastCallParamsArg = (fake, expectedCount, expectedLastCallParams) => {
  expect(fake.callCount).toEqual(expectedCount);
  expect(fake.lastArg.sort()).toEqual(expectedLastCallParams.sort());
}

describe('getSuccessfulTransactionConsensusNs', () => {
  const expectedValidConsensusNs = '1234567891000000001';
  const validQueryResult = {
    rows: [{consensus_ns: expectedValidConsensusNs}]
  };
  const transactionId = TransactionId.fromString('0.0.1-1234567891-000111222');

  test('with transaction found in db table', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {query: fakeQuery};

    const consensusNs = await stateproof.getSuccessfulTransactionConsensusNs(transactionId);
    expect(consensusNs).toEqual(expectedValidConsensusNs);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });

  test('with transaction not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = {query: fakeQuery};

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = {query: fakeQuery};

    await expect(stateproof.getSuccessfulTransactionConsensusNs(transactionId)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1,
      [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()]);
  });
});

describe('getRCDFileNameByConsensusNs', () => {
  const consensusNs = '1578342501111222333';
  const expectedRCDFileName = '2020-02-09T18_30_25.001721Z.rcd';
  const validQueryResult = {
    rows: [{name: expectedRCDFileName}]
  };

  test('with record file found', async () => {
    const fakeQuery = sinon.fake.resolves(validQueryResult);
    global.pool = {query: fakeQuery};

    const fileName = await stateproof.getRCDFileNameByConsensusNs(consensusNs);
    expect(fileName).toEqual(expectedRCDFileName);
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with record file not found', async () => {
    const fakeQuery = sinon.fake.resolves(emptyQueryResult);
    global.pool = {query: fakeQuery};

    await expect(stateproof.getRCDFileNameByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });

  test('with db query error', async () => {
    const fakeQuery = sinon.fake.rejects(new Error('db runtime error'));
    global.pool = {query: fakeQuery};

    await expect(stateproof.getRCDFileNameByConsensusNs(consensusNs)).rejects.toThrow();
    verifyFakeCallCountAndLastCallParamsArg(fakeQuery, 1, [consensusNs]);
  });
});

describe('getAddressBooksAndNodeAccountIdsByConsensusNs', () => {
  const makeFakeQueryFn = (addressBookQueryResult, nodeAddressQueryResult) => {
    return async (sqlQuery, params) => {
      sqlQuery = sqlQuery.toLowerCase().replace(/[\r\n\t]/gm, ' ');
      const matches = sqlQuery.match(/select.*\bfrom\s+(\S+).*/is);
      const tableName = matches[1];

      if (tableName === 'address_book') {
        return addressBookQueryResult;
      } else if (tableName === 'node_address') {
        return nodeAddressQueryResult;
      } else {
        throw new Error(`Invalid table name ${tableName}`);
      }
    }
  };
  const nodeAccountId3 = EntityId.fromString('0.0.3');
  const nodeAccountId4 = EntityId.fromString('0.0.4');
  const nodeAccountId5 = EntityId.fromString('0.0.5');
  const validAddressBookQueryResult = {
    rows: [
      {
        consensus_timestamp: '1234567891000000001',
        file_data: 'address book 1 data',
        node_count: 3
      },
      {
        consensus_timestamp: '1234567899000000001',
        file_data: 'address book 2 data',
        node_count: 3,
      }
    ]
  };
  const validNodeAddressQueryResult = {
    rows: [
      {node_account_id: nodeAccountId3.getEncodedId()},
      {node_account_id: nodeAccountId4.getEncodedId()},
      {node_account_id: nodeAccountId5.getEncodedId()},]
  };
  const transactionConsensusNs = '1234567899000000021';

  test('with matching address books and node account IDs found', async () => {
    const queryStub = sinon.stub().callsFake(makeFakeQueryFn(validAddressBookQueryResult, validNodeAddressQueryResult));
    global.pool = {query: queryStub};

    const result = await stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs);
    expect(result.addressBooks).toEqual(_.map(validAddressBookQueryResult.rows, row => row.file_data));
    expect(result.nodeAccountIds.sort()).toEqual(
      _.map(validNodeAddressQueryResult.rows, row => EntityId.fromEncodedId(row.node_account_id).toString()).sort());

    expect(queryStub.callCount).toEqual(2);
  });

  test('with address book not found', async () => {
    const queryStub = sinon.stub().callsFake(makeFakeQueryFn(emptyQueryResult, validNodeAddressQueryResult));
    global.pool = {query: queryStub};

    await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
    expect(queryStub.callCount).toEqual(1);
  });

  test('with node address not found', async () => {
    const queryStub = sinon.stub().callsFake(makeFakeQueryFn(validAddressBookQueryResult, emptyQueryResult));
    global.pool = {query: queryStub};

    await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
    expect(queryStub.callCount).toEqual(2);
  });

  test('with node address count mismatch count in last adddress book', async () => {
    const queryStub = sinon.stub().callsFake(makeFakeQueryFn(validAddressBookQueryResult, {
      rows: validNodeAddressQueryResult.rows.slice(1)
    }));
    global.pool = {query: queryStub};

    await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
    expect(queryStub.callCount).toEqual(2);
  });

  test('with db runtime error', async () => {
    const queryStub = sinon.stub().callsFake(async (sqlQuery, params) => {
      throw new Error('db runtime error');
    });
    global.pool = {query: queryStub};

    await expect(stateproof.getAddressBooksAndNodeAccountIdsByConsensusNs(transactionConsensusNs)).rejects.toThrow();
    expect(queryStub.callCount).toEqual(1);
  });
});

describe('downloadRecordStreamFilesFromObjectStorage', () => {
  const partialFilePaths = _.map([3, 4, 5, 6], num => `0.0.${num}/2020-02-09T18_30_25.001721Z.rcd_sig`);

  beforeEach(() => {
    config.stateproof = {
      streams: {
        bucketName: 'test-bucket-name',
        record: {
          prefix: 'recordstreams/record'
        }
      }};
  });

  const stubS3ClientGetObject = (stub) => {
    sinon.stub(s3client, 'createS3Client').returns({
      getObject: stub
    });
  }

  const setStreamsConfigAttribute = (name, value) => {
    config.stateproof.streams[name] = value;
  }

  const verifyGetObjectStubAndReturnedFileObjects = (getObjectStub, fileObjects, partialFilePaths, failedNodes) => {
    let succeededPartialFilePaths = partialFilePaths;
    if (!!failedNodes) {
      succeededPartialFilePaths = _.filter(partialFilePaths,
          partialFilePath => _.every(failedNodes, failedNode => !partialFilePath.startsWith(failedNode)));
    }

    expect(_.map(fileObjects, fileObject => fileObject.partialFilePath).sort()).toEqual(succeededPartialFilePaths.sort());
    for (const fileObject of fileObjects) {
      expect(fileObject.base64Data)
        .toEqual(Buffer.from(config.stateproof.streams.record.prefix + fileObject.partialFilePath).toString('base64'));
    }
    expect(getObjectStub.callCount).toEqual(partialFilePaths.length);
  }

  test('with all files downloaded successfully', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => {
      return {
        createReadStream: function() {
          const stream = new Readable({
            objectMode: true,
          });
          stream._read = function(size) {
            this.push(params.Key);
            this.push(null);
          };
          return stream;
        }
      };
    });
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, partialFilePaths);
  });

  test('with all files failed to download', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => {
      return {
        createReadStream: function() {
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
        }
      };
    });
    stubS3ClientGetObject(getObjectStub);

    await expect(stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths)).rejects.toThrow();
    expect(getObjectStub.callCount).toEqual(partialFilePaths.length);
  });

  test('with download failed for 0.0.3', async () => {
    const getObjectStub = sinon.stub().callsFake((params, callback) => {
      return {
        createReadStream: function() {
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
        }
      };
    });
    stubS3ClientGetObject(getObjectStub);

    const fileObjects = await stateproof.downloadRecordStreamFilesFromObjectStorage(...partialFilePaths);
    // const filteredPartialFilePaths = _.reject(partialFilePaths, p => p.startsWith('0.0.3'));
    verifyGetObjectStubAndReturnedFileObjects(getObjectStub, fileObjects, partialFilePaths, ['0.0.3']);
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
      'address book 1 data',
      'address book 2 data'
    ],
    nodeAccountIds: [
      '0.0.3',
      '0.0.4',
      '0.0.5'
    ]
  };

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
    defaultGetRCDFileNameByConsensusNsStub = sinon.stub(stateproof, 'getRCDFileNameByConsensusNs').resolves(defaultRecordFilename);
    defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub = sinon.stub().resolves(defaultAddressBooksAndNodeAccountIdsResult);
    defaultDownloadRecordStreamFilesFromObjectStorageStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      return _.map(partialFilePaths, partialFilePath => {
        return makeFileObjectFromPartialFilePath(partialFilePath);
      });
    });

    alwaysThrowErrorStub = sinon.stub().throws(new Error('always throw error'));
  })

  const rewireAllDependencyFunctions = (
    getSuccessfulTransactionConsensusNsStub,
    getRCDFileNameByConsensusNsStub,
    getAddressBooksAndNodeAccountIdsByConsensusNsStub,
    downloadRecordStreamFilesFromObjectStorageStub) => {
    stateproofRewired.__set__({
      'getSuccessfulTransactionConsensusNs': getSuccessfulTransactionConsensusNsStub,
      'getRCDFileNameByConsensusNs': getRCDFileNameByConsensusNsStub,
      'getAddressBooksAndNodeAccountIdsByConsensusNs': getAddressBooksAndNodeAccountIdsByConsensusNsStub,
      'downloadRecordStreamFilesFromObjectStorage': downloadRecordStreamFilesFromObjectStorageStub
    });
  };

  const makeFileObjectFromPartialFilePath = (partialFilePath) => {
    return {
      partialFilePath,
      base64Data: Buffer.from(partialFilePath).toString('base64')
    };
  }

  const verifyResponseData = (responseData, recordFileName, addressBooks, nodeAccountIds) => {
    expect(responseData).toBeTruthy();
    expect(responseData.record_file)
      .toEqual(Buffer.from(`${nodeAccountIds[0]}/${recordFileName}`).toString('base64'));

    expect(Object.keys(responseData.signature_files).sort()).toEqual(nodeAccountIds.sort());
    for(const nodeAccountId of nodeAccountIds) {
      expect(responseData.signature_files[nodeAccountId])
        .toEqual(Buffer.from(`${nodeAccountId}/${recordFileName}_sig`).toString('base64'));
    }

    expect(responseData.address_books.sort())
      .toEqual(_.map(addressBooks, addressBook => Buffer.from(addressBook).toString('base64')).sort());
  };

  test('with valid transaction ID and all data successfully retrieved', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
    );

    req.params.id = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    verifyResponseData(res.locals[constants.responseDataLabel], defaultRecordFilename,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks, defaultAddressBooksAndNodeAccountIdsResult.nodeAccountIds);

    expect(defaultGetSuccessfulTransactionConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetSuccessfulTransactionConsensusNsStub.args[0][0].toString()).toEqual(defaultTransactionIdStr);

    expect(defaultGetRCDFileNameByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetRCDFileNameByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.calledOnce).toBeTruthy();
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.args[0][0]).toEqual(defaultTransactionConsensusNs);

    expect(defaultDownloadRecordStreamFilesFromObjectStorageStub.callCount).toBeGreaterThanOrEqual(1);
    const nodeAccountIds = defaultAddressBooksAndNodeAccountIdsResult.nodeAccountIds;
    const expectedPartialFilePaths = [
      `${nodeAccountIds[0]}/${defaultRecordFilename}`,
      ..._.map(nodeAccountIds, nodeAccountId => `${nodeAccountId}/${defaultRecordFilename}_sig`)
    ].sort();
    expect(_.flatten(defaultDownloadRecordStreamFilesFromObjectStorageStub.args).sort()).toEqual(expectedPartialFilePaths);
  });

  test('with invalid transaction ID', async () => {
    rewireAllDependencyFunctions(
      defaultGetSuccessfulTransactionConsensusNsStub,
      defaultGetRCDFileNameByConsensusNsStub,
      defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub,
      defaultDownloadRecordStreamFilesFromObjectStorageStub
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
      defaultDownloadRecordStreamFilesFromObjectStorageStub
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
      defaultDownloadRecordStreamFilesFromObjectStorageStub
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
      defaultDownloadRecordStreamFilesFromObjectStorageStub
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
      alwaysThrowErrorStub
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
      let result = [];
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
      failAllRecordFileDownloadStub
    );

    req.params.id = defaultTransactionIdStr;
    await expect(stateproofRewired.getStateProofForTransaction(req, res)).rejects.toThrow();

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failAllRecordFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });

  test('with downloadRecordStreamFilesFromObjectStorage fail to download one signature file', async() => {
    let failedNodeAccountId = '';
    const failOneSignatureFileDownloadStub = sinon.stub().callsFake(async (...partialFilePaths) => {
      let result = [];

      for (const partialFilePath of partialFilePaths) {
        if (!failedNodeAccountId && partialFilePath.endsWith('_sig')) {
          failedNodeAccountId = _.first(partialFilePath.split('/'));
          continue;
        }

        result.push(makeFileObjectFromPartialFilePath(partialFilePath));
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
      failOneSignatureFileDownloadStub
    );

    req.params.id = defaultTransactionIdStr;
    await stateproofRewired.getStateProofForTransaction(req, res);

    let nodeAccountIds = defaultAddressBooksAndNodeAccountIdsResult.nodeAccountIds;
    nodeAccountIds = _.reject(nodeAccountIds, nodeAccountId => nodeAccountId === failedNodeAccountId);
    verifyResponseData(res.locals[constants.responseDataLabel], defaultRecordFilename,
      defaultAddressBooksAndNodeAccountIdsResult.addressBooks, nodeAccountIds);

    expect(defaultGetSuccessfulTransactionConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetRCDFileNameByConsensusNsStub.callCount).toEqual(1);
    expect(defaultGetAddressBooksAndNodeAccountIdsByConsensusNsStub.callCount).toEqual(1);
    expect(failOneSignatureFileDownloadStub.callCount).toBeGreaterThanOrEqual(1);
  });
});
