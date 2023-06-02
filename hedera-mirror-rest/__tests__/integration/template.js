/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

/**
 * Integration tests for the rest-api and postgresql database.
 * Tests will be performed using either a docker postgres instance managed by the testContainers module or
 * some other database (running locally, instantiated in the CI environment, etc).
 * Tests instantiate the database schema via a flywaydb wrapper using the flyway CLI to clean and migrate the
 * schema (using sql files in the ../src/resources/db/migration directory).
 *
 * * Test data for rest-api tests is created by:
 * 1) reading account id, balance, expiration and crypto transfer information from specs/*.json
 * 2) applying account creations, balance sets and transfers to the integration DB

 * Tests are then run in code below and by comparing requests/responses from the server to data in the specs/ dir.
 */

// external libraries
import crypto from 'crypto';
import fs from 'fs';
import {jest} from '@jest/globals';
import _ from 'lodash';
import path from 'path';
import request from 'supertest';
import integrationDomainOps from '../integrationDomainOps';
import IntegrationS3Ops from '../integrationS3Ops';
import config from '../../config';
import {cloudProviders} from '../../constants';
import server from '../../server';
import {getModuleDirname} from '../testutils';
import {JSONParse} from '../../utils';
import {defaultBeforeAllTimeoutMillis, setupIntegrationTest} from '../integrationUtils';
import {CreateBucketCommand, PutObjectCommand, S3} from '@aws-sdk/client-s3';
import {Readable} from 'stream';
const groupSpecPath = $$GROUP_SPEC_PATH$$;

const walk = (dir, files = []) => {
  for (const f of fs.readdirSync(dir)) {
    const p = path.join(dir, f);
    const stat = fs.statSync(p);

    if (stat.isDirectory()) {
      walk(p, files);
    } else {
      files.push(p);
    }
  }

  return files;
};

const getSpecs = async () => {
  const modulePath = getModuleDirname(import.meta);
  const specPath = path.join(modulePath, '..', 'specs', groupSpecPath);
  const specMap = {};

  await Promise.all(
    walk(specPath)
      .filter((f) => f.endsWith('.json'))
      .map(async (f) => {
        const specText = fs.readFileSync(f, 'utf8');
        const spec = JSONParse(specText);
        spec.name = path.basename(f);
        const key = path.dirname(f).replace(specPath, '');
        const specs = specMap[key] || [];
        if (spec.matrix) {
          const apply = (await import(path.join(modulePath, spec.matrix))).default;
          specs.push(...apply(spec));
        } else {
          specs.push(spec);
        }
        specMap[key] = specs;
      })
  );

  return specMap;
};

setupIntegrationTest();

const specs = await getSpecs();

describe(`API specification tests - ${groupSpecPath}`, () => {
  const bucketName = 'hedera-demo-streams';
  const s3TestDataRoot = path.join(getModuleDirname(import.meta), '..', 'data', 's3');

  let configOverridden = false;
  let configClone;
  let s3Ops;

  const configS3ForStateProof = (endpoint) => {
    config.stateproof = _.merge(config.stateproof, {
      addressBookHistory: false,
      enabled: true,
      streams: {
        network: 'OTHER',
        cloudProvider: cloudProviders.S3,
        endpointOverride: endpoint,
        region: 'us-east-1',
        bucketName,
      },
    });
  };

  const getTests = (spec) => {
    const tests = spec.tests || [
      {
        url: spec.url,
        urls: spec.urls,
        responseContentType: spec.responseContentType,
        responseJson: spec.responseJson,
        responseStatus: spec.responseStatus,
      },
    ];
    return _.flatten(
      tests.map((test) => {
        const urls = test.urls || [test.url];
        const {responseContentType, responseJson, responseStatus} = test;
        return urls.map((url) => ({url, responseContentType, responseJson, responseStatus}));
      })
    );
  };

  const hasher = (data) => crypto.createHash('sha256').update(data).digest('hex');

  const loadSqlScripts = async (pathPrefix, sqlScripts) => {
    if (!sqlScripts) {
      return;
    }

    for (const sqlScript of sqlScripts) {
      const sqlScriptPath = path.join(getModuleDirname(import.meta), '..', pathPrefix || '', sqlScript);
      const script = fs.readFileSync(sqlScriptPath, 'utf8');
      logger.debug(`loading sql script ${sqlScript}`);
      await pool.query(script);
    }
  };

  const needsS3 = (specs) => Object.keys(specs).some((dir) => dir.includes('stateproof'));

  const overrideConfig = (override) => {
    if (!override) {
      return;
    }

    _.merge(config, override);
    configOverridden = true;
  };

  const restoreConfig = () => {
    if (configOverridden) {
      _.merge(config, configClone);
      configOverridden = false;
    }
  };

  const runSqlFuncs = async (pathPrefix, sqlFuncs) => {
    if (!sqlFuncs) {
      return;
    }

    for (const sqlFunc of sqlFuncs) {
      // path.join returns normalized path, the sqlFunc is a local js file so add './'
      const func = (await import(`./${path.join('..', pathPrefix || '', sqlFunc)}`)).default;
      logger.debug(`running sql func in ${sqlFunc}`);
      await func.apply(null);
    }
  };

  const specSetupSteps = async (spec) => {
    await integrationDomainOps.setup(spec);
    if (spec.sql) {
      await loadSqlScripts(spec.sql.pathprefix, spec.sql.scripts);
      await runSqlFuncs(spec.sql.pathprefix, spec.sql.funcs);
    }
    overrideConfig(spec.config);
  };

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

  const uploadFilesToS3 = async (endpoint) => {
    const dataPath = path.join(s3TestDataRoot, bucketName);
    // use fake accessKeyId and secretAccessKey, otherwise upload will fail
    const s3client = new S3({
      credentials: {
        accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
        secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',
      },
      endpoint,
      forcePathStyle: true,
      region: 'us-east-1',
    });

    logger.debug(`creating s3 bucket ${bucketName}`);
    await s3client.send(new CreateBucketCommand({Bucket: bucketName}));

    logger.debug('uploading file objects to mock s3 service');
    const s3ObjectKeys = [];
    for (const filePath of walk(dataPath)) {
      const s3ObjectKey = path.relative(dataPath, filePath);
      const fileStream = fs.createReadStream(filePath);
      await s3client.send(
        new PutObjectCommand({
          Bucket: bucketName,
          Key: s3ObjectKey,
          Body: Readable.from(fileStream),
          ACL: 'public-read',
        })
      );
      s3ObjectKeys.push(s3ObjectKey);
    }
    logger.debug(`uploaded ${s3ObjectKeys.length} file objects: ${s3ObjectKeys}`);
  };

  jest.setTimeout(60000);

  beforeAll(async () => {
    if (needsS3(specs)) {
      s3Ops = new IntegrationS3Ops();
      await s3Ops.start();
      configS3ForStateProof(s3Ops.getEndpointUrl());
      await uploadFilesToS3(s3Ops.getEndpointUrl());
    }

    configClone = _.cloneDeep(config);
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => {
    if (s3Ops) {
      await s3Ops.stop();
    }
  });

  afterEach(() => {
    restoreConfig();
  });

  Object.entries(specs).forEach(([dir, specs]) => {
    describe(`${dir}`, () => {
      specs.forEach((spec) => {
        describe(`${spec.name}`, () => {
          getTests(spec).forEach((tt) => {
            test(`${tt.url}`, async () => {
              await specSetupSteps(spec.setup);
              if (spec.postSetup) {
                await spec.postSetup();
              }

              const response = await request(server).get(tt.url);

              expect(response.status).toEqual(tt.responseStatus);
              const contentType = response.get('Content-Type');
              expect(contentType).not.toBeNull();

              if (contentType.includes('application/json')) {
                let jsonObj = response.text === '' ? {} : JSONParse(response.text);
                if (response.status === 200 && dir.endsWith('stateproof')) {
                  jsonObj = transformStateProofResponse(jsonObj);
                }
                expect(jsonObj).toEqual(tt.responseJson);
              } else {
                expect(contentType).toEqual(tt.responseContentType);
                expect(response.text).toEqual(tt.responseJson);
              }
            });
          });
        });
      });
    });
  });
});
