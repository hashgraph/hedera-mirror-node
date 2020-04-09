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
const {HttpError, httpErrorMessages, httpStatusCodes} = require('./httpError');

class InvalidArgumentError extends HttpError {
  constructor(messages) {
    super(httpStatusCodes.BAD_REQUEST, undefined);
    this.message = this.errorMessageFormat(this.invalidMessages(messages));
  }

  invalidMessage(message) {
    return this.errorMessage(`${httpErrorMessages.INVALID_ARGUMENT}${message}`);
  }

  invalidMessages(messages) {
    let invalidMessages = [];

    // support single error message or array of messages
    if (Array.isArray(messages)) {
      for (let message of messages) {
        invalidMessages.push(this.invalidMessage(message));
      }
    } else {
      invalidMessages.push(this.invalidMessage(messages));
    }

    return invalidMessages;
  }
}

module.exports = {
  InvalidArgumentError,
};
