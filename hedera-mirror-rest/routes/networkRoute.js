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

'use strict';

// external libraries
const {Router} = require('@awaitjs/express');
const {NetworkController} = require('../controllers');

const router = Router();

const resource = 'network';
router.getAsync('/exchangerate', NetworkController.getExchangeRate);
router.getAsync('/nodes', NetworkController.getNetworkNodes);
router.getAsync('/supply', NetworkController.getSupply);

module.exports = {
  resource,
  router,
};
