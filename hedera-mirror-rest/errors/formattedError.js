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

class FormattedError extends Error {
  constructor(message) {
    super();

    this.message = this.createSingleErrorJsonResponse(message);
  }

  /**
   * Application error message format
   * @param array of messages
   * @returns {{_status: {messages: *}}}
   */
  errorMessageFormat(messages) {
    return {
      _status: {
        messages: messages,
      },
    };
  }

  /**
   * Create single message as part of error response messages
   * @param message
   * @returns {{message: *}}
   */
  errorMessage(message) {
    return {message: message};
  }

  /**
   * Create error message response from single string message
   * @param message
   * @returns {{_status: {messages: *}}}
   */
  createSingleErrorJsonResponse(message) {
    return this.errorMessageFormat([this.errorMessage(message)]);
  }
}

module.exports = {
  FormattedError,
};
