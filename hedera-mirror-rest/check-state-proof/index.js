#!/usr/bin/env node --experimental-specifier-resolution=node
/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import log4js from 'log4js';

import startUpScreen from './startUpScreen';
import StateProofHandler from './stateProofHandler';
import {getAPIResponse, readJSONFile} from './utils';

const logger = log4js
  .configure({
    appenders: {
      console: {
        layout: {
          pattern: '%d{yyyy-MM-ddThh:mm:ss.SSSO} %p %m',
          type: 'pattern',
        },
        type: 'stdout',
      },
    },
    categories: {
      default: {
        appenders: ['console'],
        level: 'debug',
      },
    },
  })
  .getLogger();

// get user input
const {transactionId, nonce, scheduled, url, storedFile} = startUpScreen();

const getStateProofJson = async (url, storedFile) => {
  return storedFile ? readJSONFile(storedFile) : getAPIResponse(url);
};

getStateProofJson(url, storedFile)
  .then((stateProofJson) => {
    const missingFilesPrefix = 'Mirror node StateProof API returned insufficient number of files.';
    if (stateProofJson.address_books.length < 1) {
      logger.error(`${missingFilesPrefix} At least 1 addressBook is expected`);
      return false;
    }

    if (!stateProofJson.record_file) {
      logger.error(`${missingFilesPrefix} No record file in response`);
      return false;
    }

    if (stateProofJson.signature_files.length < 2) {
      logger.error(`${missingFilesPrefix} At least 2 signature files are expected`);
      return false;
    }

    // instantiate stateProofHandler which will parse files and extract needed data
    const stateProofHandler = new StateProofHandler(stateProofJson, transactionId, nonce, scheduled);

    // kick off stateProof flow
    const validated = stateProofHandler.runStateProof();
    const result = validated ? 'valid' : 'invalid';

    logger.info(`-----------------------------------------------`);
    logger.info(`The state proof is cryptographically ${result}`);
    logger.info(`-----------------------------------------------`);
    return validated;
  })
  .catch((e) => logger.error(e));
