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

class ErrorHandler extends Error {
  constructor(statusCode, message) {
    super();
    this.statusCode = statusCode;

    // support single error message or array of messages
    this.message = Array.isArray(message) ? message : [errorMessage(message)];
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
  NOT_FOUND: 'Not found',
  INTERNAL_ERROR: 'Internal error',
};

const errorMessage = (message) => {
  return {message: message};
};

const errorMessageFormat = (messages) => {
  return {
    _status: {
      messages: messages,
    },
  };
};

const createSingleErrorJsonResponse = (message) => {
  return errorMessageFormat([errorMessage(message)]);
};

const handleError = (err, req, res, next) => {
  if (process.env.NODE_ENV !== 'test') {
    logger.error(`Error processing ${req.originalUrl}: `, err);
  }

  const {statusCode, message} = err;

  if (/ECONNREFUSED/.test(message)) {
    res
      .status(httpStatusCodes.SERVICE_UNAVAILABLE)
      .json(createSingleErrorJsonResponse('Unable to connect to database. Please retry later'));
    return;
  }

  if (statusCode === httpStatusCodes.INTERNAL_ERROR) {
    res.status(statusCode).json(createSingleErrorJsonResponse('Internal error'));
  } else {
    res.status(statusCode).json(errorMessageFormat(message));
  }
};

module.exports = {
  createSingleErrorJsonResponse,
  ErrorHandler,
  handleError,
  httpErrorMessages,
  httpStatusCodes,
};
