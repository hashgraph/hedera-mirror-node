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

const fs = require('fs');
const path = require('path');
const pg = require('pg');
const utils = require('./utils.js');
const yaml = require('js-yaml');

let config = utils.config;

// Postgres pool
const pool = new pg.Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});

const loadTests = () => {
  let fileName = path.join(__dirname, 'tests.yml');
  console.log(`Loading tests from ${fileName}`);
  return utils.mustLoadYaml(fileName);
};

const getRowValueAsInt = (row) => {
  return parseInt(row.value);
};

/**
 * Gets 'n' randomly sampled entity_num from t_entities table.
 */
const sampleEntityIds = (n) => {
  return pool.query(`select entity_num as value from t_entities order by RANDOM() limit ${n};`, null).then((result) => {
    return result.rows.map(getRowValueAsInt);
  });
};

/**
 * Gets 'n' randomly sampled consensus timestamps from transaction table.
 */
const sampleConsensusTimestamps = (n) => {
  return pool
    .query(`select consensus_ns as value from transaction order by RANDOM() limit ${n};`, null)
    .then((result) => {
      return result.rows.map(getRowValueAsInt);
    });
};

/**
 * Gets 'n' randomly sampled balances from account_balance table.
 */
const sampleBalanceValues = (n) => {
  return pool
    .query(`select balance as value from account_balance order by RANDOM() limit ${n};`, null)
    .then((result) => {
      return result.rows.map(getRowValueAsInt);
    });
};

/**
 * Converts integer timestamp format accepted by query param. For eg. 1577904152141445600 -> '1577904152.141445600'
 */
const timestampToParamValue = (timestamp) => {
  let timestampStr = '' + timestamp;
  return timestampStr.substr(0, 10) + '.' + timestampStr.substr(10);
};

const populateParamValues = async (test, paramName, rangeFieldName, getSamples, convertToParam) => {
  let paramValues = [];
  let isRangeQuery = rangeFieldName in test;
  let samples = await getSamples(test.count);
  samples.forEach((sample) => {
    if (isRangeQuery) {
      paramValues.push([convertToParam(sample), convertToParam(sample + test[rangeFieldName])]);
    } else {
      paramValues.push([convertToParam(sample)]);
    }
  });
  return paramValues;
};

/**
 * Given a test, generates and returns query set which contains query, values for url params, etc.
 */
const makeQuerySet = async (test) => {
  let paramValues = [];
  let paramName;
  let isRangeQuery = false;
  if (test.filterAxis === 'BALANCE') {
    paramName = 'account.balance';
    paramValues = await populateParamValues(test, paramName, 'rangeTinyHbars', sampleBalanceValues, (sample) => {
      return '' + sample;
    });
  } else if (test.filterAxis === 'CONSENSUS_TIMESTAMP') {
    paramName = 'timestamp';
    paramValues = await populateParamValues(
      test,
      paramName,
      'rangeDurationNanos',
      sampleConsensusTimestamps,
      (sample) => {
        return timestampToParamValue(sample);
      }
    );
  } else if (test.filterAxis === 'ACCOUNTID') {
    paramName = 'account.id';
    paramValues = await populateParamValues(test, 'account.id', 'rangeNumAccounts', sampleEntityIds, (sample) => {
      return '' + sample;
    });
  } else {
    throw `Unexpected filterAxis '${test.filterAxis}'`;
  }

  let querySuffix;
  if (isRangeQuery) {
    querySuffix = paramName + '=gt:%s&' + paramName + '=lt:%s';
  } else {
    querySuffix = paramName + '=%d';
  }

  let query = test.query;
  if (query.lastIndexOf('?') === -1) {
    query += '?' + querySuffix;
  } else {
    query += '&' + querySuffix;
  }
  return {
    name: test.name,
    query: query,
    paramValues: paramValues,
  };
};

const generateQuerySets = () => {
  let promises = [];
  let tests = loadTests();
  let querySets = {
    timeoutInSec: tests.timeoutInSec,
    querySets: [],
  };
  tests.tests.forEach((test) => {
    let promise = makeQuerySet(test).then((t) => {
      querySets.querySets.push(t);
    });
    promises.push(promise);
  });
  return Promise.all(promises).then(() => {
    let querySetsFile = config.querySetsFile;
    try {
      console.log(`Writing query sets to ${querySetsFile}`);
      let parentDir = path.dirname(querySetsFile);
      if (!fs.existsSync(parentDir)) {
        fs.mkdirSync(parentDir);
      }
      fs.writeFileSync(querySetsFile, yaml.safeDump(querySets));
    } catch (err) {
      console.log(`Failed to write query sets to ${querySetsFile}: ${err}`);
      process.exit(1);
    }
  });
};

module.exports = generateQuerySets;
