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

const {DbError} = require('./dbError');
const {FormattedError} = require('./formattedError');
const {httpStatusCodes, HttpError} = require('./httpError');

const handleError = (err, req, res, next) => {
  // only logs in non test environment
  if (process.env.NODE_ENV !== 'test') {
    logger.error(`Error processing ${req.originalUrl}: `, err);
  }

  const {statusCode, message} = err;
  // catch DB errors
  if (DbError.isDbConnectionError(message)) {
    const dbError = new DbError();
    res.status(httpStatusCodes.SERVICE_UNAVAILABLE).json(dbError.message);
    return;
  }

  if (err instanceof HttpError) {
    if (statusCode === undefined || statusCode == httpStatusCodes.INTERNAL_ERROR) {
      logger.trace('HttpError error with undefined status code');
      res.status(httpStatusCodes.INTERNAL_ERROR).json(err.message);
    } else {
      res.status(statusCode).json(message);
    }
  } else {
    const unknownError = new FormattedError(err.message);
    res.status(httpStatusCodes.INTERNAL_ERROR).json(unknownError.message);
  }
};

module.exports = {
  handleError,
};
