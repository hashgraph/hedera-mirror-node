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

const transactions = require('./transactions.js');

/**
 * Function to determine readiness of application.
 * @return {} None.
 */
const readinessCheck = async () => {
  return transactions.getOneTransactionForHealthCheck();
};

/**
 * Function to determine liveness of application.
 * @return {} None.
 */
const livenessCheck = async () => {
  return;
};

/**
 * Allows for a graceful shutdown.
 * @return {} None.
 */

function beforeDown() {
  // given your readiness probes run every 5 second
  // may be worth using a bigger number so you won't
  // run into any race conditions
  console.log('Test of system');
  return new Promise((resolve) => {
    setTimeout(resolve, 20000);
  });
}

module.exports = {
  readinessCheck: readinessCheck,
  livenessCheck: livenessCheck,
  beforeDown: beforeDown,
};
