/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

'use strict';

const glob = require('glob');
const path = require('path');
const {readJSONFile} = require('../utils');

const loadStateProofSamples = () => {
  const getVersionFromPath = (filepath) => {
    const segments = path.parse(filepath).dir.split(path.sep);
    return parseInt(segments[segments.length - 1][1]);
  };

  const jsonFiles = glob.sync(`${__dirname}/../sample/v*/*.json`);
  return jsonFiles.map((jsonFile) => {
    return {
      data: readJSONFile(jsonFile),
      filepath: jsonFile,
      version: getVersionFromPath(jsonFile),
    };
  });
};

module.exports = {
  loadStateProofSamples,
};
