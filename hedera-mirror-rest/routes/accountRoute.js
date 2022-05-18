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

const {filterKeys} = require('../constants');
const {AccountController, CryptoAllowanceController, TokenAllowanceController} = require('../controllers');

const router = Router();

const getPath = (path) => `/:${filterKeys.ID_OR_ALIAS_OR_EVM_ADDRESS}/${path}`;

const resource = 'accounts';
router.getAsync(getPath('nfts'), AccountController.getNftsByAccountId);
router.getAsync(getPath('allowances/crypto'), CryptoAllowanceController.getAccountCryptoAllowances);
router.getAsync(getPath('allowances/tokens'), TokenAllowanceController.getAccountTokenAllowances);

module.exports = {
  resource,
  router,
};
