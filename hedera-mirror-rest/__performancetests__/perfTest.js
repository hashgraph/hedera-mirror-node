#!/usr/bin/env node
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

// See testing.md for documentation

const fetch = require('node-fetch');
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
const getAvgAndStdDev = (arr) => {
  const n = arr.length;
  const average = arr.reduce((a, b) => a + b) / n;
  const stddev = Math.sqrt(arr.map((x) => Math.pow(x - average, 2)).reduce((a, b) => a + b) / n);
  return {average: parseInt(average), stddev: parseInt(stddev)};
};

/**
 * Executes single query set.
 * For each query set:
 * 1. Executes queries (sequentially) by iterating over paramValues.
 * 2. Collects stats: time taken, response size
 */
const executeQuerySet = async (querySet) => {
  console.log(`Running query set: ${querySet.name}`);
  let url = config.apiServer + '/api/v1' + querySet.query;
  if (!url.startsWith('http://')) {
    url = 'http://' + url;
  }
  let querySetResult = {elapsedTimesMs: [], responseSizes: []};
  for (let i = 0; i < querySet.paramValues.length; i++) {
    // TODO: support concurrent requests. That will make it possible to test throughput vs latency for system.
    let query = vsprintf(url, querySet.paramValues[i]);
    let hrstart = process.hrtime();
    let response = await fetch(query)
      .then((response) => {
        return response.text();
      })
      .catch((error) => {
        console.log(`Error when querying ${query} : ${error}`);
      });
    let hrend = process.hrtime(hrstart);
    querySetResult.elapsedTimesMs.push(hrend[0] * 1000 + hrend[1] / 1000000);
    querySetResult.responseSizes.push(response.length);
  }
  return {
    name: querySet.name,
    query: querySet.query,
    count: querySet.paramValues.length,
    timeTakeMs: getAvgAndStdDev(querySetResult.elapsedTimesMs),
    responseSize: getAvgAndStdDev(querySetResult.responseSizes),
  };
};

/**
 * Sends queries to API Server.
 * Query sets are run sequentially.
 * In the end, dumps the results into a file.
 */
const executeQueries = async () => {
  let hrstart = process.hrtime();
  console.log('Executing queries');
  console.log(`Loading query sets from ${config.querySetsFile}`);
  let querySets = utils.mustLoadYaml(config.querySetsFile);
  let results = [];
  for (let i = 0; i < querySets.querySets.length; i++) {
    results.push(await executeQuerySet(querySets.querySets[i]));
  }
  console.log(results);
  let hrend = process.hrtime(hrstart);
  console.log(`Finished executing queries. Time : ${hrend[0]}s ${parseInt(hrend[1] / 1000000)}ms `);
  writeResultToFile(results);
};

/**
 * If hedera.mirror.rest.resultsDir is set to a valid path, then output results to a file.
 */
const writeResultToFile = (results) => {
  let resultsDir = config.resultsDir;
  if (resultsDir === undefined || resultsDir === '') {
    console.log(`hedera.mirror.rest.resultsDir not set or empty. Skipping writing results to file.`);
    return;
  }
  let dateTime = new Date().toJSON().substring(0, 19);
  let resultFileName = path.join(resultsDir, 'apiResults-' + dateTime + '.yml');
  try {
    if (!fs.existsSync(resultsDir)) {
      fs.mkdirSync(resultsDir);
    }
    console.log(`Writing results to ${resultFileName}`);
    fs.writeFileSync(resultFileName, yaml.safeDump(results));
  } catch (err) {
    console.log(`Failed to write results to ${resultFileName}: ${err}`);
  }
};

const generateQuerySetsIfNeeded = async () => {
  // If querySetsFile already exists, no need to generate queries again.
  if (fs.existsSync(config.querySetsFile)) {
    console.log(`Using existing query sets from '${config.querySetsFile}'`);
  } else {
    console.log(`Generating new query sets`);
    await generateQuerySets();
  }
};

generateQuerySetsIfNeeded().then(executeQueries);
