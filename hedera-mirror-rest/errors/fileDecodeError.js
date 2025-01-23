/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

import RestError from './restError';

const FileDecodeErrorMessage =
  'Failed to decode file contents. Ensure timestamp filters cover the complete file create/update and append transactions';

class FileDecodeError extends RestError {
  constructor(errorMessage) {
    let message = FileDecodeErrorMessage;
    if (errorMessage !== undefined) {
      message += `. Error: '${errorMessage}'`;
    }

    super(message);
  }
}

export default FileDecodeError;
