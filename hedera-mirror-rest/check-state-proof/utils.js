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
const fs = require('fs');
const fetch = require('node-fetch');
const AbortController = require('abort-controller');

const base64StringToBuffer = (base64String) => {
  return Buffer.from(base64String, 'base64');
};

const replaceSpecialCharsWithUnderScores = (stringToFormat) => {
  return stringToFormat.replace(/[.@\-]/g, '_');
};

const makeStateProofDir = (transactionId, stateProofJson) => {
  const newDirPath = replaceSpecialCharsWithUnderScores(transactionId);
  fs.mkdirSync(newDirPath, {recursive: true});
  fs.writeFileSync(`${newDirPath}/apiResponse.json`, JSON.stringify(stateProofJson));
  console.log(`Supporting files and API response for the state proof will be stored in the directory ${newDirPath}`);
  return newDirPath;
};

const storeFile = (data, file, ext) => {
  const newFilePath = `${replaceSpecialCharsWithUnderScores(file)}.${ext}`;
  fs.writeFileSync(`${newFilePath}`, data, (err) => {
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

  console.log(`Requesting state proof files from ${url}...`);
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

const readJSONFile = (filePath) => {
  const rawData = fs.readFileSync(filePath, 'utf8');
  return JSON.parse(rawData);
};

module.exports = {
  base64StringToBuffer,
  getAPIResponse,
  makeStateProofDir,
  readJSONFile,
  storeFile,
  replaceSpecialCharsWithUnderScores,
};
