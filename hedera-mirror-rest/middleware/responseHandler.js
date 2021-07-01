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

const constants = require('../constants.js');
const {NotFoundError} = require('../errors/notFoundError');

// response middleware that pulls response data passed through request and sets in json response
// next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const responseHandler = async (req, res, next) => {
  const responseData = res.locals[constants.responseDataLabel];
  if (responseData === undefined) {
    // unmatched route will have no response data, pass NotFoundError to next middleware
    throw new NotFoundError();
  } else {
    // set response json
    res.status(res.locals.statusCode).json(res.locals[constants.responseDataLabel]);
  }
};

module.exports = {
  responseHandler,
};
