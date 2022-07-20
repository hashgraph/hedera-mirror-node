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

import {jest} from '@jest/globals';

import integrationDbOps from '../integrationDbOps';
import integrationDomainOps from '../integrationDomainOps';

export default function () {
  jest.setTimeout(40000);
  let dbConfig;
  // set a large timeout for beforeAll as downloading docker image if not exists can take quite some time. Note
  // it's 12 minutes for CI to workaround possible DockerHub rate limit.
  const defaultBeforeAllTimeoutMillis = process.env.CI ? 12 * 60 * 1000 : 4 * 60 * 1000;

  beforeAll(async () => {
    dbConfig = await integrationDbOps.instantiateDatabase();
    await integrationDomainOps.setUp({}, dbConfig.sqlConnection);
    global.pool = dbConfig.sqlConnection;
  }, defaultBeforeAllTimeoutMillis);

  afterAll(async () => {
    await integrationDbOps.closeConnection(dbConfig);
  });

  beforeEach(async () => {
    if (!dbConfig.sqlConnection) {
      logger.warn(`sqlConnection undefined, acquire new connection`);
      dbConfig.sqlConnection = integrationDbOps.getConnection(dbConfig.dbSessionConfig);
    }

    await integrationDbOps.cleanUp(dbConfig.sqlConnection);
  });
}
