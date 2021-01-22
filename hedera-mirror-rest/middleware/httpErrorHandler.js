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

const statusCodes = require('http').STATUS_CODES;
const defaultStatusCode = 500; // Internal server error

const errorMap = {
  DbError: 503, // Service unavailable
  InvalidArgumentError: 400, // Bad request
  NotFoundError: 404, // Not found
  InvalidConfigError: 503, // Service unavailable
};

// Error middleware which formats thrown errors and maps them to appropriate http status codes
// next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const handleError = async (err, req, res, next) => {
  const statusCode = errorMap[err.constructor.name] || defaultStatusCode;
  let errorMessage;

  if (isClientError(statusCode)) {
    errorMessage = err.message;
    logger.warn(
      `${statusCode} ${statusCodes[statusCode]} processing ${req.originalUrl}: ${err.constructor.name} ${errorMessage}`
    );
  } else {
    errorMessage = statusCodes[statusCode];
    logger.error(`${statusCode} ${statusCodes[statusCode]} processing ${req.originalUrl}: `, err);
  }

  res.status(statusCode).json(errorMessageFormat(errorMessage));
  return next;
};

const isClientError = (statusCode) => {
  return statusCode >= 400 && statusCode < 500;
};

/**
 * Application error message format
 * @param errorMessages array of messages
 * @returns {{_status: {messages: *}}}
 */
const errorMessageFormat = (errorMessages) => {
  if (!Array.isArray(errorMessages)) {
    errorMessages = [errorMessages];
  }

  return {
    _status: {
      messages: errorMessages.map((m) => {
        return {message: m};
      }),
    },
  };
};

module.exports = {
  handleError,
};
