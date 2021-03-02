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

const InvalidArgumentErrorMessageFormat = 'Invalid parameter: ';
const ParameterExceedsMaxErrorMessageFormat = 'Parameter values count exceeds maximum number allowed: ';

class InvalidArgumentError extends Error {

  static INVALID_ERROR_CODE = "invalidArgument";
  static PARAM_COUNT_EXCEEDS_MAX_CODE = "paramCountExceedsMax";

  constructor(errorMessage) {
    super();

    this.message = errorMessage;
  }

  // factory method to help common case
  static forParams(badParams) {
    if (!Array.isArray(badParams)) {
      badParams = [badParams];
    }
    return new InvalidArgumentError(badParams.map((message) => `${InvalidArgumentErrorMessageFormat}${message}`));
  }

  static forRequestValidation(badParams) {
    if (!Array.isArray(badParams)) {
      badParams = [badParams];
    }

    return new InvalidArgumentError(badParams.map((message) => {
      if (message.code === this.PARAM_COUNT_EXCEEDS_MAX_CODE) {
        return `${ParameterExceedsMaxErrorMessageFormat}${message.key} count: ${message.count} max: ${message.max}`;

      } else {
        return `${InvalidArgumentErrorMessageFormat}${message.key}`
      }
    }));
  }
}

module.exports = {
  InvalidArgumentError,
};
