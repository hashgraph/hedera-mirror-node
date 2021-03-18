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

// external libraries
const AbortController = require('abort-controller');
const fs = require('fs');
const log4js = require('log4js');
const fetch = require('node-fetch');
const path = require('path');

const logger = log4js.getLogger();

const makeStateProofDir = (transactionId, stateProofJson) => {
  const outputDir = path.join('output', transactionId);
  fs.mkdirSync(outputDir, {recursive: true});
  fs.writeFileSync(path.join(outputDir, 'apiResponse.json'), JSON.stringify(stateProofJson));
  logger.info(`Supporting files and API response for the state proof will be stored in the directory ${outputDir}`);
  return outputDir;
};

const storeFile = (data, file, ext) => {
  if (!Buffer.isBuffer(data) && typeof data !== 'string') {
    logger.info(`Skip saving file "${file}" since the data is neither a Buffer nor a string`);
    return;
  }

  const filename = `${file}.${ext}`;
  fs.writeFileSync(`${filename}`, data, (err) => {
    if (err) throw err;
  });
};

const getAPIResponse = async (url) => {
  const controller = new AbortController();
  const timeout = setTimeout(
    () => {
      controller.abort();
    },
    60 * 1000 // in ms
  );

  logger.info(`Requesting state proof files from ${url}...`);
  return fetch(url, {signal: controller.signal})
    .then(async (response) => {
      if (!response.ok) {
        throw Error(response.statusText);
      }
      return response.json();
    })
    .catch((error) => {
      throw Error(`Error fetching ${url}: ${error}`);
    })
    .finally(() => {
      clearTimeout(timeout);
    });
};

const readJSONFile = (filePath) => JSON.parse(fs.readFileSync(filePath, 'utf-8'));

module.exports = {
  getAPIResponse,
  makeStateProofDir,
  readJSONFile,
  storeFile,
};
