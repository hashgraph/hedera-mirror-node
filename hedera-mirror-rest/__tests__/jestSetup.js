/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import {jest} from '@jest/globals';
import matchers from 'jest-extended';
import log4js from 'log4js';

global.logger = log4js.getLogger();

expect.extend(matchers); // add matchers from jest-extended
jest.setTimeout(4000);

// set test configuration file path
process.env.CONFIG_PATH = '__tests__';

beforeEach(() => {
  logger.info(expect.getState().currentTestName);
});
