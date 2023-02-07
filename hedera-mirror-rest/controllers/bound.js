/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import _ from 'lodash';

import {InvalidArgumentError} from '../errors';
import * as utils from '../utils';

/**
 * Holds the filters for query parameters in a multi-column paging query. The parsing logic only allows single
 * occurrence of equal, lt/lte, and gt/gte filters, respectively.
 *
 * FilterKey may be referenced in validation errors.
 */
class Bound {
  constructor(filterKey, viewModelKey) {
    this.filterKey = filterKey;
    this.viewModelKey = !_.isNil(viewModelKey) ? viewModelKey : filterKey;
    this.equal = null;
    this.lower = null;
    this.upper = null;
  }

  hasBound() {
    return this.hasLower() || this.hasUpper();
  }

  hasEqual() {
    return !_.isNil(this.equal);
  }

  hasLower() {
    return !_.isNil(this.lower);
  }

  hasUpper() {
    return !_.isNil(this.upper);
  }

  isEmpty() {
    return !this.hasEqual() && !this.hasLower() && !this.hasUpper();
  }

  parse(filter) {
    const operator = filter.operator;
    if (operator === utils.opsMap.eq) {
      if (this.hasEqual()) {
        throw new InvalidArgumentError(`Only one equal (eq) operator is allowed for ${this.filterKey}`);
      }
      this.equal = filter;
    } else if (utils.gtGte.includes(operator)) {
      if (this.hasLower()) {
        throw new InvalidArgumentError(`Only one gt/gte operator is allowed for ${this.filterKey}`);
      }
      this.lower = filter;
    } else if (utils.ltLte.includes(operator)) {
      if (this.hasUpper()) {
        throw new InvalidArgumentError(`Only one lt/lte operator is allowed for ${this.filterKey}`);
      }
      this.upper = filter;
    } else {
      throw new InvalidArgumentError(`Not equal (ne) operator is not supported for ${this.filterKey}`);
    }
  }

  // for test only
  static create(properties) {
    return Object.assign(new Bound(), properties);
  }
}

export default Bound;
