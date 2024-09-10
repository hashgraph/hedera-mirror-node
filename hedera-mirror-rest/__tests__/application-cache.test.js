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

import {RedisContainer} from '@testcontainers/redis';

import {setupIntegrationTest} from './integrationUtils';
import integrationDomainOps from './integrationDomainOps';
import request from 'supertest';
import server from '../server';
import config from '../config.js';
import {defaultBeforeAllTimeoutMillis} from './integrationUtils.js';

const accountsUrl1 =
  '/api/v1/accounts?account.id=gte:0.1.18&account.id=lt:0.1.20&account.balance=gt:45&account.publicKey=3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be&order=desc&balance=true';
const accountsUrl2 =
  '/api/v1/accounts?account.id=gte:0.1.18&account.id=lt:0.1.20&account.balance=gt:45&account.publicKey=3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be&order=asc';

setupIntegrationTest();

let redisContainer;

beforeAll(async () => {
  config.redis.enabled = true;
  redisContainer = await new RedisContainer().withStartupTimeout(20000).start();
  logger.info('Started Redis container');
}, defaultBeforeAllTimeoutMillis);

afterAll(async () => {
  await redisContainer.stop({signal: 'SIGKILL', t: 5});
  logger.info('Stopped Redis container');
});

describe('Application cache tests', () => {
  beforeEach(async () => {
    await integrationDomainOps.loadAccounts([
      {
        balance: 70,
        num: 17,
        realm: 1,
        shard: 0,
        public_key: '6ceecd8bb224da4914d53f292e5624f6f4cf8c134c920e1cac8d06f879df5819',
        expiration_timestamp: 123456781,
        auto_renew_period: 11111,
        key: [1, 1, 1],
      },
      {
        balance: 80,
        num: 18,
        realm: 1,
        shard: 0,
        public_key: '519a008fabde4d28d68293c71fcdcdcca38d8fae6102a832b31e802f257fd1d9',
        expiration_timestamp: 123456782,
        auto_renew_period: 22222,
        key: [2, 2, 2],
      },
      {
        balance: 90,
        balance_timestamp: '2345',
        num: 19,
        realm: 1,
        shard: 0,
        public_key: '3c3d546321ff6f63d701d2ec5c277095874e19f4a235bee1e6bb19258bf362be',
        auto_renew_period: 33333,
        created_timestamp: '9999123456789',
        key: [3, 3, 3],
      },
      {
        balance: 100,
        num: 20,
        realm: 1,
        shard: 0,
        public_key: 'c7e81a0c1444c6e5b5c1bfb1a02ae5faae44c11e621f286d21242cc584280692',
        expiration_timestamp: 123456784,
        auto_renew_period: 44444,
        key: [4, 4, 4],
      },
      {
        balance: 110,
        num: 21,
        realm: 1,
        shard: 0,
        public_key: '5f58f33c65992676de86ac8f5b4b8b4b45c636f12cec8a274d820a3fe1778a3e',
        expiration_timestamp: 123456785,
        auto_renew_period: 55555,
        key: [5, 5, 5],
      },
    ]);

    await integrationDomainOps.loadTokenAccounts([
      {
        token_id: '0.0.99998',
        account_id: '0.1.17',
        balance: 17,
        created_timestamp: '2300',
      },
      {
        token_id: '0.0.99999',
        account_id: '0.1.17',
        balance: 1717,
        created_timestamp: '2300',
      },
      {
        token_id: '0.0.99999',
        account_id: '0.1.18',
        balance: 18,
        created_timestamp: '2300',
        associated: false,
      },
      {
        token_id: '0.0.99998',
        account_id: '0.1.19',
        balance: 19,
        created_timestamp: '2300',
      },
      {
        token_id: '0.0.99999',
        account_id: '0.1.19',
        balance: 1919,
        created_timestamp: '2300',
      },
    ]);
  });

  test('Get and then head', async () => {
    const getResponse = await request(server).get(accountsUrl1);
    expect(getResponse.status).toEqual(200);
    expect(getResponse.body).not.toBeEmpty();

    const etag = getResponse.headers['etag'];
    expect(etag).not.toBeEmpty();

    const head304Response = await request(server).head(accountsUrl1).set('if-none-match', etag);
    expect(head304Response.status).toEqual(304);
    expect(head304Response.body).toBeEmpty();

    const head200Response = await request(server).head(accountsUrl1).set('if-none-match', 'bogus');
    expect(head200Response.status).toEqual(200);
    expect(head200Response.body).toBeEmpty();
  });

  test('Just heads', async () => {
    const head200Response = await request(server).head(accountsUrl2);
    expect(head200Response.status).toEqual(200);
    expect(head200Response.body).toBeEmpty();

    const etag = head200Response.headers['etag'];
    expect(etag).not.toBeEmpty();

    const head304Response = await request(server).head(accountsUrl2).set('if-none-match', etag);
    expect(head304Response.status).toEqual(304);
    expect(head304Response.body).toBeEmpty();
  });
});
