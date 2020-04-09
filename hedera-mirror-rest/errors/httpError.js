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
const {FormattedError} = require('./formattedError');

class HttpError extends FormattedError {
  constructor(statusCode, errorMessage) {
    super(errorMessage);
    this.statusCode = statusCode;
  }
}

const httpStatusCodes = {
  OK: 200,
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  INTERNAL_ERROR: 500,
  SERVICE_UNAVAILABLE: 503,
};

const httpErrorMessages = {
  BAD_REQUEST: 'Bad request',
  INTERNAL_ERROR: 'Internal error',
  INVALID_ARGUMENT: 'Invalid parameter: ',
  NOT_FOUND: 'Not found',
};

module.exports = {
  HttpError: HttpError,
  httpErrorMessages,
  httpStatusCodes,
};
