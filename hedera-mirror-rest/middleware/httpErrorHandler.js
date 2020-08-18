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

const {DbError} = require('../errors/dbError');
const {FileDownloadError} = require('../errors/fileDownloadError');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');
const {InvalidConfigError} = require('../errors/invalidConfigError');
const {NotFoundError} = require('../errors/notFoundError');

const httpStatusCodes = {
  OK: 200,
  BAD_REQUEST: 400,
  NOT_FOUND: 404,
  INTERNAL_ERROR: 500,
  SERVICE_UNAVAILABLE: 503,
};

const httpErrorMessages = {
  INTERNAL_ERROR: 'Internal error',
};

// Error middleware which formats thrown errors and maps them to appropriate http status codes
// next param is required to ensure express maps to this middleware and can also be used to pass onto future middleware
const handleError = async (err, req, res, next) => {
  // only logs in non test environment
  if (process.env.NODE_ENV !== 'test') {
    logger.error(`Error processing ${req.originalUrl}: `, err);
  }

  // get application error message format
  const errorMessage = errorMessageFormat(err.message);

  // map errors to desired http status codes
  switch (err.constructor) {
    case DbError:
      logger.debug(`DB error: ${err.dbErrorMessage}`);
      res.status(httpStatusCodes.SERVICE_UNAVAILABLE).json(errorMessage);
      return;
    case InvalidArgumentError:
      res.status(httpStatusCodes.BAD_REQUEST).json(errorMessage);
      return;
    case NotFoundError:
      res.status(httpStatusCodes.NOT_FOUND).json(errorMessage);
      return;
    case FileDownloadError:
      res.status(httpStatusCodes.SERVICE_UNAVAILABLE).json(errorMessage);
      return;
    case InvalidConfigError:
      res.status(httpStatusCodes.INTERNAL_ERROR).json(errorMessage);
      return;
    default:
      logger.trace(`Unhandled error encountered: ${err.message}`);
      res.status(httpStatusCodes.INTERNAL_ERROR).json(errorMessageFormat(httpErrorMessages.INTERNAL_ERROR));
  }

  next();
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
