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

const applyResponseJsonMatrix = (spec, key) => {
  if (spec.responseJsonMatrix?.[key]) {
    spec.responseJson = {
      ...spec.responseJson,
      ...spec.responseJsonMatrix[key],
    };
  }

  if (spec.tests) {
    for (const test of spec.tests) {
      if (test.responseJsonMatrix?.[key]) {
        test.responseJson = {
          ...test.responseJson,
          ...test.responseJsonMatrix[key],
        };
      }
    }
  }

  return spec;
};

export {applyResponseJsonMatrix};
