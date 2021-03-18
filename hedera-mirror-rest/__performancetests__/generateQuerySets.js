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

const fs = require('fs');
const path = require('path');
const pg = require('pg');
const yaml = require('js-yaml');
const utils = require('./utils.js');

const {config} = utils;

// Postgres pool
const pool = new pg.Pool({
  user: config.db.username,
  host: config.db.host,
  database: config.db.name,
  password: config.db.password,
  port: config.db.port,
});

const loadTests = () => {
  const fileName = path.join(__dirname, 'tests.yml');
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
  return pool.query('select entity_num as value from t_entities order by RANDOM() limit $1', [n]).then((result) => {
    return result.rows.map(getRowValueAsInt);
  });
};

/**
 * Gets 'n' randomly sampled consensus timestamps from transaction table.
 */
const sampleConsensusTimestamps = (n) => {
  return pool.query('select consensus_ns as value from transaction order by RANDOM() limit $1', [n]).then((result) => {
    return result.rows.map(getRowValueAsInt);
  });
};

/**
 * Gets 'n' randomly sampled balances from account_balance table.
 */
const sampleBalanceValues = (n) => {
  return pool.query('select balance as value from account_balance order by RANDOM() limit $1', [n]).then((result) => {
    return result.rows.map(getRowValueAsInt);
  });
};

/**
 * Gets 'n' randomly sampled token_id from token table.
 */
const sampleTokenIds = (n) => {
  return pool.query('select token_id as value from token order by RANDOM() limit $1', [n]).then((result) => {
    return result.rows.map(getRowValueAsInt);
  });
};

/**
 * Converts integer timestamp format accepted by query param. For eg. 1577904152141445600 -> '1577904152.141445600'
 */
const timestampToParamValue = (timestamp) => {
  const timestampStr = `${timestamp}`;
  return `${timestampStr.substr(0, 10)}.${timestampStr.substr(10)}`;
};

const populateParamValues = async (test, paramName, rangeFieldName, getSamples, convertToParam) => {
  const paramValues = [];
  if (test.multipleFilters) {
    for (let i = 0; i < test.numberOfQueries; i++) {
      const samples = await getSamples(test.count);
      const allValues = samples.map((sample) => convertToParam(sample));
      paramValues.push(allValues);
    }
  } else {
    const isRangeQuery = rangeFieldName in test;
    const samples = await getSamples(test.count);
    samples.forEach((sample) => {
      if (isRangeQuery) {
        paramValues.push([convertToParam(sample), convertToParam(sample + test[rangeFieldName])]);
      } else {
        paramValues.push([convertToParam(sample)]);
      }
    });
  }

  return paramValues;
};

const populateIdValues = async (test, getSamples, convertToParam) => {
  const idValues = [];
  const samples = await getSamples(test.count);
  samples.forEach((sample) => {
    idValues.push([convertToParam(sample)]);
  });
  return idValues;
};

const makeQueryParamsQuerySet = async (test) => {
  let paramValues = [];
  let paramName;
  let querySuffix;
  const isRangeQuery = false;
  let {query} = test;
  if (test.filterAxis === 'BALANCE') {
    paramName = 'account.balance';
    paramValues = await populateParamValues(test, paramName, 'rangeTinyHbars', sampleBalanceValues, (sample) => {
      return `${sample}`;
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
      return `${sample}`;
    });
  } else if (test.filterAxis === 'TOKENID') {
    paramName = 'token.id';
    paramValues = await populateParamValues(test, 'token.id', 'rangeNumTokens', sampleTokenIds, (sample) => {
      return `${sample}`;
    });
  } else if (test.filterAxis === 'NA') {
  } else {
    throw `Unexpected filterAxis '${test.filterAxis}'`;
  }
  if (isRangeQuery) {
    querySuffix = `${paramName}=gt:%s&${paramName}=lt:%s`;
  } else {
    querySuffix = `${paramName}=%d`;
  }
  if (query.lastIndexOf('?') === -1) {
    query += `?${querySuffix}`;
  } else {
    query += `&${querySuffix}`;
  }

  if (test.multipleFilters) {
    query += `&${querySuffix}`.repeat(test.count - 1);
  }
  return {
    name: test.name,
    query,
    paramValues,
  };
};

const makeIdsQuerySet = async (test) => {
  let idValues;
  if (test.idAxis === 'TOKENID') {
    idValues = await populateIdValues(test, sampleTokenIds, (sample) => {
      return `${sample}`;
    });
  } else if (test.idAxis === 'ACCOUNTID') {
    idValues = await populateIdValues(test, sampleEntityIds, (sample) => {
      return `${sample}`;
    });
  }
  return {
    name: test.name,
    query: test.query,
    paramValues: idValues,
  };
};

/**
 * Given a test, generates and returns query set which contains query, values for url params, etc.
 */
const makeQuerySet = async (test) => {
  if (test.filterAxis) {
    return await makeQueryParamsQuerySet(test);
  }
  if (test.idAxis) {
    return await makeIdsQuerySet(test);
  }
  throw `Neither filterAxis nor idAxis specified, test requires one of these.'`;
};

const generateQuerySets = () => {
  const promises = [];
  const tests = loadTests();
  const querySets = {
    timeoutInSec: tests.timeoutInSec,
    querySets: [],
  };
  tests.tests.forEach((test) => {
    const promise = makeQuerySet(test).then((t) => {
      querySets.querySets.push(t);
    });
    promises.push(promise);
  });
  return Promise.all(promises).then(() => {
    const {querySetsFile} = config;
    try {
      console.log(`Writing query sets to ${querySetsFile}`);
      const parentDir = path.dirname(querySetsFile);
      if (!fs.existsSync(parentDir)) {
        fs.mkdirSync(parentDir);
      }
      fs.writeFileSync(querySetsFile, yaml.dump(querySets));
    } catch (err) {
      console.log(`Failed to write query sets to ${querySetsFile}: ${err}`);
      process.exit(1);
    }
  });
};

module.exports = generateQuerySets;
