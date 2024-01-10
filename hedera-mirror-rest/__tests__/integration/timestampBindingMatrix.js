/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import _ from 'lodash';

const applyMatrix = (spec) => {
  /*
   * Process any matrix per value spec level configuration to be applied to the cloned specs. This is a top-level
   * property to the spec:
   *
   *   "matrixSetup": {
   *     "true": {
   *       "features": {
   *         "fakeTime": "2019-08-14Z 10:50:00"
   *       },
   *       "config": {
   *         "query": {
   *           "maxRepeatedQueryParameters": 50
   *         }
   *       }
   *     }
   *   }
   *
   * Setup properties such as "features" and "config" are merged with the top-level "setup" property for each defined
   * matrix value.
   */
  const matrixSetupMap = typeof spec.matrixSetup == 'object' ? new Map(Object.entries(spec.matrixSetup)) : new Map();

  return [false, true].map((value) => {
    const clone = _.cloneDeep(spec);
    clone.name = `${spec.name} with bindTimestampRange=${value}`;
    clone.setup.config = _.merge(clone.setup.config, {
      query: {
        bindTimestampRange: value,
      },
    });

    /*
     * Apply any setup for this matrix value.
     */
    const matrixSetup = matrixSetupMap.get(value.toString());
    if (!_.isNil(matrixSetup)) {
      clone.setup = _.merge(clone.setup, matrixSetup);
    }

    /*
     * A spec may contain multiple test cases (urls/response) via the "test" array, or not define that
     * and contain a single test case within the spec. Each test case may define a "matrixResponse" object, keyed
     * by the matrix value just like the setup above, which can be used to replace the test case "responseStatus"
     * and "responseJson" properties per matrix value.
     */
    const testCases = Array.isArray(clone.tests) ? clone.tests : [clone];
    testCases.forEach((tc) => {
      const matrixResponseMap =
        typeof tc.matrixResponse == 'object' ? new Map(Object.entries(tc.matrixResponse)) : new Map();

      const matrixResponse = matrixResponseMap.get(value.toString());
      if (!_.isNil(matrixResponse)) {
        tc.responseStatus = matrixResponse.responseStatus ?? tc.responseStatus;
        tc.responseJson = matrixResponse.responseJson ?? tc.responseJson;
      }
    });

    return clone;
  });
};

export default applyMatrix;
