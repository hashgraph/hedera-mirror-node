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

const requestLogger = function (req, res, next) {
  logger.debug(`Client: [ ${req.ip} ] URL: ${req.originalUrl}`);
  return next();
};

/**
 * Support case insensitive retrieval from request parameters
 * @param req
 * @param res
 * @param next
 * @returns Query param value
 */
const requestQueryKeyFormatter = function (req, res, next) {
  req.query = new Proxy(req.query, {
    get: (target, name) => target[Object.keys(target).find((key) => key.toLowerCase() === name.toLowerCase())],
  });

  return next();
};

module.exports = {
  requestLogger,
  requestQueryKeyFormatter,
};
