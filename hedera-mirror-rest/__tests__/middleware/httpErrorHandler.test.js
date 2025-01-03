/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import {
  DbError,
  FileDecodeError,
  FileDownloadError,
  InvalidArgumentError,
  InvalidClauseError,
  InvalidConfigError,
  NotFoundError,
} from '../../errors';

import {handleUncaughtException} from '../../middleware/httpErrorHandler';

describe('Server error handler', () => {
  test('Throws Error for non rest error', () => {
    const exception = () => handleUncaughtException(new InvalidConfigError('Bad Config'));
    expect(exception).toThrow(InvalidConfigError);
  });

  test('Does not throw error for rest error', () => {
    const exception = () => {
      handleUncaughtException(new DbError());
      handleUncaughtException(new FileDecodeError());
      handleUncaughtException(new FileDownloadError());
      handleUncaughtException(new InvalidArgumentError());
      handleUncaughtException(new InvalidClauseError());
      handleUncaughtException(new NotFoundError());
    };

    expect(exception).not.toThrow(Error);
  });
});
