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

/**
 * Integration tests for the rest-api and postgresql database.
 * Tests will be performed using either a docker postgres instance managed by the testContainers module or
 * some other database (running locally, instantiated in the CI environment, etc).
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * * Test data for rest-api tests is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from *.spec.json
 * 2) applying account creations, balance sets and transfers to the integration DB
 *
 * Test data for database tests is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from integration_test_data.json
 * 2) storing those accounts in integration DB
 * 3) creating 3 balances records per account at timestamp 1000, 2000, 3000 in the integration DB
 * 4) apply transfers (from integration_test_data.json) to the integration DB
 *
 * Tests are then run in code below (find TESTS all caps) and by comparing requests/responses from the server to data
 * in the specs/ dir.
 */
// external libraries
const S3 = require('aws-sdk/clients/s3');
const crypto = require('crypto');
const fs = require('fs');
const _ = require('lodash');
const path = require('path');
const request = require('supertest');

const integrationDbOps = require('./integrationDbOps');
const integrationDomainOps = require('./integrationDomainOps');
const {S3Ops} = require('./integrationS3Ops');
const config = require('../config');
const {cloudProviders, filterKeys} = require('../constants');
const EntityId = require('../entityId');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const server = require('../server');
const transactions = require('../transactions');
const {TransactionResultService, TransactionTypeService} = require('../service');
const utils = require('../utils');

jest.setTimeout(40000);

let sqlConnection;

// set timeout for beforeAll to 2 minutes as downloading docker image if not exists can take quite some time
const defaultBeforeAllTimeoutMillis = 240 * 1000;

beforeAll(async () => {
  sqlConnection = await integrationDbOps.instantiateDatabase();

  // set items that required db connection but weren't available due to integration db setup logic
  await TransactionTypeService.loadTransactionTypes();
  await TransactionResultService.loadTransactionResults();
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await integrationDbOps.closeConnection();
});

beforeEach(async () => {
  if (!sqlConnection) {
    logger.warn(`sqlConnection undefined, acquire new connection`);
    sqlConnection = integrationDbOps.getConnection();
  }

  await integrationDbOps.cleanUp();
  await setupData();
});

//
// TEST DATA
// shard 0, realm 15, accounts 1-10
// 3 balances per account
// several transactions
const defaultShard = 0;
const defaultRealm = 15;

/**
 * Setup test data in the postgres instance.
 */
const setupData = async () => {
  const testDataPath = path.join(__dirname, 'integration_test_data.json');
  const testData = fs.readFileSync(testDataPath);
  const testDataJson = JSON.parse(testData);

  await integrationDomainOps.setUp(testDataJson.setup, sqlConnection);

  logger.info('Finished initializing DB data');
};

/**
 * Add a crypto transfer from A to B with A also paying 1 tinybar to account number 2 (fee)
 * @param consensusTimestamp
 * @param payerAccountId
 * @param recipientAccountId
 * @param amount
 * @param validDurationSeconds
 * @param maxFee
 * @param result
 * @param type
 * @param nodeAccountId
 * @param treasuryAccountId
 * @return {Promise<void>}
 */
const addCryptoTransferTransaction = async (
  consensusTimestamp,
  payerAccountId,
  recipientAccountId,
  amount,
  validDurationSeconds,
  maxFee,
  result = 22,
  type = 14,
  nodeAccountId = '0.15.3',
  treasuryAccountId = '0.15.98'
) => {
  await integrationDomainOps.addCryptoTransaction({
    consensus_timestamp: consensusTimestamp,
    payerAccountId,
    recipientAccountId,
    amount,
    valid_duration_seconds: validDurationSeconds,
    max_fee: maxFee,
    result,
    type,
    nodeAccountId,
    treasuryAccountId,
  });
};

const createAndPopulateNewAccount = async (id, realm, ts, bal) => {
  await integrationDomainOps.addAccount({
    num: id,
    realm: realm,
  });

  await integrationDomainOps.setAccountBalance({
    timestamp: ts,
    id,
    realm_num: realm,
    balance: bal,
  });
};

/**
 * Map a DB transaction/cryptotransfer result to something easily comparable in a test assert/expect.
 * @param rows
 * @returns {*}
 */
const mapTransactionResults = (rows) => {
  return rows.map((v) => {
    const cryptoTransfers = v.crypto_transfer_list.map((transfer) => {
      return {
        amount: transfer.amount,
        account: EntityId.fromEncodedId(transfer.entity_id).toString(),
      };
    });
    return {
      consensusNs: v.consensus_ns,
      cryptoTransfers,
    };
  });
};

const extractDurationAndMaxFeeFromTransactionResults = (rows) => {
  return rows.map((v) => `${v.valid_duration_seconds}, ${v.max_fee}`);
};

const extractNameAndResultFromTransactionResults = (rows) => rows.map((v) => `${v.name}, ${v.result}`);

//
// TESTS
//
describe('DB integration test - utils.getTransactionTypeQuery', () => {
  test('DB integration test - utils.getTransactionTypeQuery - Verify null query params', () => {
    expect(utils.getTransactionTypeQuery(null)).toBe('');
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify undefined query params', () => {
    expect(utils.getTransactionTypeQuery(undefined)).toBe('');
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify empty query params', () => {
    expect(utils.getTransactionTypeQuery({})).toBe('');
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify empty transaction type query', () => {
    expect(() => utils.getTransactionTypeQuery({[filterKeys.TRANSACTION_TYPE]: ''})).toThrowError(InvalidArgumentError);
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify non applicable transaction type query', () => {
    expect(() => utils.getTransactionTypeQuery({[filterKeys.TRANSACTION_TYPE]: 'newtransaction'})).toThrowError(
      InvalidArgumentError
    );
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify applicable TOKENCREATION transaction type query', () => {
    expect(utils.getTransactionTypeQuery({[filterKeys.TRANSACTION_TYPE]: 'TOKENCREATION'})).toBe(
      `type = ${TransactionTypeService.getProtoId('TOKENCREATION')}`
    );
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify applicable TOKENASSOCIATE transaction type query', () => {
    expect(utils.getTransactionTypeQuery({[filterKeys.TRANSACTION_TYPE]: 'TOKENASSOCIATE'})).toBe(
      `type = ${TransactionTypeService.getProtoId('TOKENASSOCIATE')}`
    );
  });
  test('DB integration test - utils.getTransactionTypeQuery - Verify applicable consensussubmitmessage transaction type query', () => {
    expect(utils.getTransactionTypeQuery({[filterKeys.TRANSACTION_TYPE]: 'consensussubmitmessage'})).toBe(
      `type = ${TransactionTypeService.getProtoId('CONSENSUSSUBMITMESSAGE')}`
    );
  });
});

describe('DB integration test -  utils.isValidTransactionType', () => {
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for null', () => {
    expect(utils.isValidTransactionType(null)).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for empty input', () => {
    expect(utils.isValidTransactionType('')).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for invalid input', () => {
    expect(utils.isValidTransactionType('1234567890.000000001')).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for entity format shard', () => {
    expect(utils.isValidTransactionType('1.0.1')).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for negative num', () => {
    expect(utils.isValidTransactionType(-10)).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for 0', () => {
    expect(utils.isValidTransactionType(0)).toBe(false);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify valid for valid CONSENSUSSUBMITMESSAGE transaction type', () => {
    expect(utils.isValidTransactionType('CONSENSUSSUBMITMESSAGE')).toBe(true);
  });
  test('DB integration test -  utils.isValidTransactionType - Verify invalid for former TOKENTRANSFERS transaction type', () => {
    expect(utils.isValidTransactionType('TOKENTRANSFERS')).toBe(false);
  });
});

// expected transaction rows order by consensus_ns desc, only check fields consensus_ns and crypto_transfer_list
const expectedTransactionRowsDesc = [
  {
    consensusNs: '1052',
    cryptoTransfers: [
      {account: '0.15.8', amount: -31},
      {account: '0.15.9', amount: 30},
      {account: '0.15.98', amount: 1},
    ],
  },
  {
    consensusNs: '1051',
    cryptoTransfers: [
      {account: '0.15.9', amount: 20},
      {account: '0.15.10', amount: -21},
      {account: '0.15.98', amount: 1},
    ],
  },
  {
    consensusNs: '1050',
    cryptoTransfers: [
      {account: '0.15.9', amount: 10},
      {account: '0.15.10', amount: -11},
      {account: '0.15.98', amount: 1},
    ],
  },
];
const expectedTransactionRowsMap = expectedTransactionRowsDesc.reduce((m, row) => {
  m[row.consensusNs] = row;
  return m;
}, {});

test('DB integration test - transactions.reqToSql - no query string - 3 txn 9 xfers', async () => {
  const sql = await transactions.reqToSql({query: {}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(mapTransactionResults(res.rows)).toEqual(expectedTransactionRowsDesc);
});

test('DB integration test - transactions.reqToSql - single valid account - 1 txn 3 xfers', async () => {
  const sql = await transactions.reqToSql({query: {'account.id': `${defaultShard}.${defaultRealm}.8`}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(mapTransactionResults(res.rows)).toEqual([expectedTransactionRowsMap['1052']]);
});

test('DB integration test - transactions.reqToSql - invalid account', async () => {
  const sql = await transactions.reqToSql({query: {'account.id': '0.17.666'}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(res.rowCount).toEqual(0);
});

test('DB integration test - transactions.reqToSql - null validDurationSeconds and maxFee inserts', async () => {
  await addCryptoTransferTransaction(1062, '0.15.5', '0.15.4', 50, 5, null); // null maxFee
  await addCryptoTransferTransaction(1063, '0.15.5', '0.15.4', 70, null, 777); // null validDurationSeconds
  await addCryptoTransferTransaction(1064, '0.15.5', '0.15.4', 70, null, null); // valid validDurationSeconds and maxFee

  const sql = await transactions.reqToSql({query: {'account.id': '0.15.5'}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(extractDurationAndMaxFeeFromTransactionResults(res.rows)).toEqual(['null, null', 'null, 777', '5, null']);
});

test('DB integration test - transactions.reqToSql - Unknown transaction result and type', async () => {
  await addCryptoTransferTransaction(1070, '0.15.7', '0.15.1', 2, 11, 33, -1, -1);

  const sql = await transactions.reqToSql({query: {timestamp: '0.000001070'}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);
  expect(extractNameAndResultFromTransactionResults(res.rows)).toEqual(['UNKNOWN, UNKNOWN']);
});

test('DB integration test - transactions.reqToSql - Account range filtered transactions', async () => {
  await createAndPopulateNewAccount(13, 15, 5, 10);
  await createAndPopulateNewAccount(63, 15, 6, 50);
  await createAndPopulateNewAccount(82, 15, 7, 100);

  // create 3 transactions - 9 transfers
  await addCryptoTransferTransaction(2062, '0.15.13', '0.15.63', 50, 5000, 50);
  await addCryptoTransferTransaction(2063, '0.15.63', '0.15.82', 70, 7000, 777);
  await addCryptoTransferTransaction(2064, '0.15.82', '0.15.63', 20, 8000, -80);

  const expected = [
    {
      consensusNs: '2064',
      cryptoTransfers: [
        {account: '0.15.63', amount: 20},
        {account: '0.15.82', amount: -21},
        {account: '0.15.98', amount: 1},
      ],
    },
    {
      consensusNs: '2063',
      cryptoTransfers: [
        {account: '0.15.63', amount: -71},
        {account: '0.15.82', amount: 70},
        {account: '0.15.98', amount: 1},
      ],
    },
  ];

  const sql = await transactions.reqToSql({query: {'account.id': ['gte:0.15.70', 'lte:0.15.97']}});
  const res = await integrationDbOps.runSqlQuery(sql.query, sql.params);

  // 2 transactions, each with 3 transfers, are applicable. For each transfer negative amount from self, amount to
  // recipient and fee to bank. Note bank is out of desired range but is expected in query result
  expect(mapTransactionResults(res.rows).sort()).toEqual(expected);
});

describe('DB integration test - transactionTypes.getId', () => {
  test('DB integration test -  transactionTypes.getId - Verify valid transaction type returns value', () => {
    expect(TransactionTypeService.getProtoId('CRYPTOTRANSFER')).toBe(14);
    expect(TransactionTypeService.getProtoId('cryptotransfer')).toBe(14);
    expect(TransactionTypeService.getProtoId('TOKENWIPE')).toBe(39);
    expect(TransactionTypeService.getProtoId('tokenWipe')).toBe(39);
  });
  test('DB integration test -  transactionTypes.getId - Verify invalid transaction type throws error', () => {
    expect(() => TransactionTypeService.getProtoId('TEST')).toThrowError(InvalidArgumentError);
    expect(() => TransactionTypeService.getProtoId(1)).toThrowError(InvalidArgumentError);
  });
});

describe('DB integration test - spec based', () => {
  const bucketName = 'hedera-demo-streams';
  const s3TestDataRoot = path.join(__dirname, 'data', 's3');

  let configOverriden = false;
  let configClone;
  let s3Ops;

  const configS3ForStateProof = (endpoint) => {
    config.stateproof = {
      addressBookHistory: false,
      enabled: true,
      streams: {
        network: 'OTHER',
        cloudProvider: cloudProviders.S3,
        endpointOverride: endpoint,
        region: 'us-east-1',
        bucketName,
      },
    };
  };

  const walk = async (dir) => {
    let files = await fs.promises.readdir(dir);
    files = await Promise.all(
      files.map(async (file) => {
        const filePath = path.join(dir, file);
        const stats = await fs.promises.stat(filePath);
        if (stats.isDirectory()) {
          return walk(filePath);
        }
        if (stats.isFile()) {
          return filePath;
        }
      })
    );

    return files.reduce((all, folderContents) => all.concat(folderContents), []);
  };

  const uploadFilesToS3 = async (endpoint) => {
    const dataPath = path.join(s3TestDataRoot, bucketName);
    // use fake accessKeyId and secreteAccessKey, otherwise upload will fail
    const s3client = new S3({
      endpoint,
      region: 'us-east-1',
      accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
      secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
      s3ForcePathStyle: true,
    });

    logger.debug(`creating s3 bucket ${bucketName}`);
    await s3client
      .makeUnauthenticatedRequest('createBucket', {
        Bucket: bucketName,
      })
      .promise();

    logger.debug('uploading file objects to mock s3 service');
    const s3ObjectKeys = [];
    for (const filePath of await walk(dataPath)) {
      const s3ObjectKey = path.relative(dataPath, filePath);
      const fileStream = fs.createReadStream(filePath);
      await s3client
        .upload({
          Bucket: bucketName,
          Key: s3ObjectKey,
          Body: fileStream,
          ACL: 'public-read',
        })
        .promise();
      s3ObjectKeys.push(s3ObjectKey);
    }
    logger.debug(`uploaded ${s3ObjectKeys.length} file objects: ${s3ObjectKeys}`);
  };

  jest.setTimeout(40000);

  beforeAll(async () => {
    s3Ops = new S3Ops();
    await s3Ops.start();
    configS3ForStateProof(s3Ops.getEndpointUrl());
    await uploadFilesToS3(s3Ops.getEndpointUrl());
    configClone = _.cloneDeep(config);
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => {
    await s3Ops.stop();
  });

  const loadSqlScripts = async (pathPrefix, sqlScripts) => {
    if (!sqlScripts) {
      return;
    }

    for (const sqlScript of sqlScripts) {
      const sqlScriptPath = path.join(__dirname, pathPrefix || '', sqlScript);
      const script = fs.readFileSync(sqlScriptPath, 'utf8');
      logger.debug(`loading sql script ${sqlScript}`);
      await integrationDbOps.runSqlQuery(script);
    }
  };

  const runSqlFuncs = async (pathPrefix, sqlFuncs) => {
    if (!sqlFuncs) {
      return;
    }

    for (const sqlFunc of sqlFuncs) {
      // path.join returns normalized path, the sqlFunc is a local js file so add './'
      const func = require(`./${path.join(pathPrefix || '', sqlFunc)}`);
      logger.debug(`running sql func in ${sqlFunc}`);
      await func.apply(null, [sqlConnection]);
    }
  };

  const overrideConfig = (override) => {
    if (!override) {
      return;
    }

    _.merge(config, override);
    configOverriden = true;
  };

  const restoreConfig = () => {
    if (configOverriden) {
      Object.assign(config, configClone);
      Object.keys(config).forEach((key) => {
        if (!(key in configClone)) {
          delete config[key];
        }
      });
      configOverriden = false;
    }
  };

  const specSetupSteps = async (spec) => {
    await integrationDbOps.cleanUp();
    await integrationDomainOps.setUp(spec, sqlConnection);
    if (spec.sql) {
      await loadSqlScripts(spec.sql.pathprefix, spec.sql.scripts);
      await runSqlFuncs(spec.sql.pathprefix, spec.sql.funcs);
    }
    overrideConfig(spec.config);
  };

  const hasher = (data) => crypto.createHash('sha256').update(data).digest('hex');

  const transformStateProofResponse = (jsonObj) => {
    const deepBase64Encode = (obj) => {
      if (typeof obj === 'string') {
        return hasher(obj);
      }

      const result = {};
      for (const [k, v] of Object.entries(obj)) {
        if (typeof v === 'string') {
          result[k] = hasher(v);
        } else if (Array.isArray(v)) {
          result[k] = v.map((val) => deepBase64Encode(val));
        } else if (_.isPlainObject(v)) {
          result[k] = deepBase64Encode(v);
        } else {
          result[k] = v;
        }
      }
      return result;
    };

    return deepBase64Encode(jsonObj);
  };

  afterEach(() => {
    restoreConfig();
  });

  const specPath = path.join(__dirname, 'specs');
  fs.readdirSync(specPath).forEach((file) => {
    const p = path.join(specPath, file);
    const specText = fs.readFileSync(p, 'utf8');
    const spec = JSON.parse(specText);
    const urls = spec.urls || [spec.url];
    urls.forEach((url) =>
      test(`DB integration test - ${file} - ${url}`, async () => {
        await specSetupSteps(spec.setup);
        const response = await request(server).get(url);

        expect(response.status).toEqual(spec.responseStatus);
        let jsonObj = response.text === '' ? {} : JSON.parse(response.text);
        if (response.status === 200 && file.startsWith('stateproof')) {
          jsonObj = transformStateProofResponse(jsonObj);
        }
        expect(jsonObj).toEqual(spec.responseJson);
      })
    );
  });
});
