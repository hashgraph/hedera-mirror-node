/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

const _ = require('lodash');

const BaseController = require('./baseController');
const {BlockService} = require('../service');
const {BlockViewModel} = require('../viewmodel');
const utils = require('../utils');
const constants = require('../constants');
const {InvalidArgumentError} = require('../errors/invalidArgumentError');

const extractOrderFromFilters = (filters, defaultOrder) => {
  if (filters.hasOwnProperty('order')) {
    return constants.orderFilterValues[filters.order.toUpperCase()];
  }

  return defaultOrder;
};

const extractLimitFromFilters = (filters, defaultLimit) => {
  if (filters.hasOwnProperty('limit')) {
    const limit = parseInt(filters.limit);
    if (limit > 100) {
      throw new InvalidArgumentError(`Invalid limit param value, must be between 1 and 100`);
    }

    return limit;
  }

  return defaultLimit;
};

const extractSqlFromBlockFilters = async (filters) => {
  const filterQuery = {
    order: extractOrderFromFilters(filters, constants.orderFilterValues.DESC),
    limit: extractLimitFromFilters(filters, 25),
    whereQuery: [],
  };

  if (filters && filters.length === 0) {
    return filterQuery;
  }

  if (filters.hasOwnProperty('block.number')) {
    filterQuery.whereQuery.push(
      utils.parseParams(
        filters['block.number'],
        (value) => value,
        (op, value) => [`index ${op} ?`, [value]],
        false
      )
    );
  }

  if (filters.hasOwnProperty('timestamp')) {
    filterQuery.whereQuery.push(
      utils.parseParams(
        filters['timestamp'],
        (value) => utils.parseTimestampParam(value),
        (op, value) => [`consensus_end ${op} ?`, [value]],
        false
      )
    );
  }

  return filterQuery;
};

const generateNextLink = (req, blocks, filters) => {
  return blocks.length
    ? utils.getPaginationLink(
        req,
        blocks.length !== filters.limit,
        {
          [constants.filterKeys.BLOCK_NUMBER]: blocks[0].index,
        },
        filters.order
      )
    : null;
};

class BlockController extends BaseController {
  getBlocks = async (req, res) => {
    utils.validateReq(req);
    const filters = await extractSqlFromBlockFilters(req.query);
    const blocks = await BlockService.getBlocks(filters);

    res.send({
      blocks: blocks.map((model) => new BlockViewModel(model)),
      links: {
        next: generateNextLink(req, blocks, filters),
      },
    });
  };
}

module.exports = new BlockController();
