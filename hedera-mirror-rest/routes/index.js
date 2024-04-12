/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import {apiPrefix, requestPathLabel} from '../constants';

import AccountRoutes from './accountRoute';
import BlockRoutes from './blockRoute';
import ContractRoutes from './contractRoute';
import NetworkRoutes from './networkRoute';

/**
 * Router middleware to record the complete registered request path as res.locals[requestPathLabel]
 *
 * @param req
 * @param res
 * @returns {Promise<void>}
 */
const recordRequestPath = async (req, res) => {
  const path = req.route?.path;
  if (path && !path.startsWith(apiPrefix) && !res.locals[requestPathLabel]) {
    res.locals[requestPathLabel] = `${req.baseUrl}${req.route.path}`.replace(/\/+$/g, '');
  }
};

[AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes].forEach(({router}) => router.useAsync(recordRequestPath));

export {AccountRoutes, BlockRoutes, ContractRoutes, NetworkRoutes};
