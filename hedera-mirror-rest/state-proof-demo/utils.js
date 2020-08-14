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
'uses strict';

// external libraries
const _ = require('lodash');
const fs = require('fs');

const base64StringToBuffer = (base64String) => {
  return Buffer.from(base64String, 'base64');
};

const replaceFullStopsWithUnderScrores = (stringToFormat) => {
  return stringToFormat.replace(/\./g, '_');
};

const makeStateProofDir = (transactionId) => {
  let dirPath = replaceFullStopsWithUnderScrores(transactionId);
  fs.mkdir(dirPath, {recursive: true}, (err, path) => {
    if (err) {
      console.error(`Error encountered creating directory ${path}: ${err}`);
      throw err;
    }
  });

  fs.writeFile(
    `${dirPath}/notes.txt`,
    `Supporting files for the state proof of '${transactionId}' can be found in this directory`,
    (err) => {
      if (err) throw err;
    }
  );

  return dirPath;
};

const storeFile = (data, file, ext) => {
  const newFilePath = `${replaceFullStopsWithUnderScrores(file)}.${ext}`;
  console.log(`Storing contents at ${newFilePath}`);
  fs.writeFile(`${newFilePath}`, data, (err) => {
    if (err) throw err;
    console.log(`File ${newFilePath} has been saved!`);
  });
};

const getAPIResponse = (url) => {
  if (url.indexOf('/') === 0) {
    // if url is path get full url including host
    url = getUrl(url);
  }

  console.log(`Requesting stateproof files from ${url} for ${transactionId}`);
  const controller = new AbortController();
  const timeout = setTimeout(
    () => {
      controller.abort();
    },
    60 * 1000 // in ms
  );

  return fetch(url, {signal: controller.signal})
    .then((response) => {
      if (!response.ok) {
        console.log(`Non success response for call to '${url}'`);
        throw Error(response.statusText);
      }

      return response.json();
    })
    .catch((error) => {
      var message = `Fetch error, url : ${url}, error : ${error}`;
      console.log(message);
      throw message;
    })
    .finally(() => {
      clearTimeout(timeout);
    });
};

const readJSONFile = (filePath) => {
  let rawData = fs.readFileSync(filePath);
  return JSON.parse(rawData);
};

module.exports = {
  base64StringToBuffer,
  getAPIResponse,
  makeStateProofDir,
  readJSONFile,
  storeFile,
};
