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

import {httpStatusCodes, requestStartTime} from '../constants';

const defaultStatusCode = httpStatusCodes.INTERNAL_ERROR;

const errorMap = {
  DbError: httpStatusCodes.SERVICE_UNAVAILABLE,
  FileDownloadError: httpStatusCodes.BAD_GATEWAY,
  InvalidArgumentError: httpStatusCodes.BAD_REQUEST,
  NotFoundError: httpStatusCodes.NOT_FOUND,
};

// Error middleware which formats thrown errors and maps them to appropriate http status codes
// next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const handleError = async (err, req, res, next) => {
  const statusCode = errorMap[err.constructor.name] || defaultStatusCode;
  let errorMessage;
  const startTime = res.locals[requestStartTime];
  const elapsed = startTime ? Date.now() - startTime : 0;

  if (shouldReturnMessage(statusCode)) {
    errorMessage = err.message;
    logger.warn(
      `${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${statusCode} ${err.constructor.name} ${errorMessage}`
    );
  } else {
    errorMessage = statusCode.message;
    logger.error(`${req.ip} ${req.method} ${req.originalUrl} in ${elapsed} ms: ${statusCode}`, err);
  }

  res.status(statusCode.code).json(errorMessageFormat(errorMessage));
};

const shouldReturnMessage = (statusCode) => {
  return statusCode.isClientError() || statusCode === httpStatusCodes.BAD_GATEWAY;
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

export default handleError;
