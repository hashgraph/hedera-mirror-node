#!/usr/bin/env node
/*-
 *
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
 *
 */

// See testing.md for documentation

const axios = require('axios');
const fs = require('fs');
const generateQuerySets = require('./generateQuerySets');
const path = require('path');
const utils = require('./utils.js');
const vsprintf = require('sprintf-js').vsprintf;
const yaml = require('js-yaml');

let config = utils.config;

/**
 * Returns average and standard deviation of numerical values in the given array.
 */
const getAvgAndStdDev = function(arr) {
  const n = arr.length;
  const average = arr.reduce((a, b) => a + b) / n;
  const stddev = Math.sqrt(arr.map(x => Math.pow(x - average, 2)).reduce((a, b) => a + b) / n);
  return {average: parseInt(average), stddev: parseInt(stddev)};
};

/**
 * Sends queries to API Server.
 * Query sets are run sequentially.
 * For each query set:
 * 1. Executes queries (sequentially) by iterating over paramValues.
 * 2. Collects stats: time taken, response size
 * In the end, dumps the results into a file.
 */
const executeQueries = async function() {
  let hrstart = process.hrtime();
  console.log('Executing queries');
  console.log(`Loading query sets from ${config.querySetsFile}`);
  let querySets = utils.mustLoadYaml(config.querySetsFile);
  let allResults = [];
  axios.defaults.transformResponse = undefined; // Get response as text rather than json object.
  for (let i = 0; i < querySets.querySets.length; i++) {
    let querySet = querySets.querySets[i];
    console.log(`Running query set: ${querySet.name}`);
    let url = config.apiServer + '/api/v1' + querySet.query;
    if (!url.startsWith('http://')) {
      url = 'http://' + url;
    }
    let querySetResult = {elapsedTimesMs: [], responseSizes: []};
    for (let j = 0; j < querySet.paramValues.length; j++) {
      // TODO: support concurrent requests. That will make it possible to test throughput vs latency for system.
      let query = vsprintf(url, querySet.paramValues[j]);
      let hrstart = process.hrtime();
      await axios
        .get(query)
        .then(function(response) {
          let hrend = process.hrtime(hrstart);
          querySetResult.elapsedTimesMs.push(hrend[0] * 1000 + hrend[1] / 1000000);
          querySetResult.responseSizes.push(response.data.length);
        })
        .catch(function(error) {
          console.log(`Error when querying ${query} : ${error}`);
        });
    }
    allResults.push({
      name: querySet.name,
      query: querySet.query,
      count: querySet.paramValues.length,
      timeTakeMs: getAvgAndStdDev(querySetResult.elapsedTimesMs),
      responseSize: getAvgAndStdDev(querySetResult.responseSizes)
    });
  }
  console.log(allResults);
  let hrend = process.hrtime(hrstart);
  console.log(`Finished executing queries. Time : ${hrend[0]}s ${parseInt(hrend[1] / 1000000)}ms `);
  let resultsFile = config.resultsFile;
  if (resultsFile !== '') {
    try {
      let parentDir = path.dirname(resultsFile);
      if (!fs.existsSync(parentDir)) {
        fs.mkdirSync(parentDir);
      }
      console.log(`Writing results to ${resultsFile}`);
      fs.writeFileSync(resultsFile, yaml.safeDump(allResults));
    } catch (err) {
      console.log(`Failed to write results to ${resultsFile}: ${err}`);
    }
  }
};

const generateQuerySetsIfNeeded = async function() {
  // If querySetsFile already exists, no need to generate queries again.
  if (fs.existsSync(config.querySetsFile)) {
    console.log(`Using existing query sets from '${config.querySetsFile}'`);
  } else {
    console.log(`Generating new query sets`);
    await generateQuerySets();
  }
};

generateQuerySetsIfNeeded().then(executeQueries);
