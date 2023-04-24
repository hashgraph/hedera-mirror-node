/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import BaseController from './baseController';
import {filterKeys, orderFilterValues, responseDataLabel} from '../constants';
import {getResponseLimit} from '../config';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {RecordFile} from '../model';
import {RecordFileService} from '../service';
import * as utils from '../utils';
import {BlockViewModel} from '../viewmodel';

const {default: defaultLimit, max: maxLimit} = getResponseLimit();

const blockWhereFilters = [filterKeys.BLOCK_NUMBER, filterKeys.TIMESTAMP];

const acceptedBlockParameters = new Set([
  filterKeys.BLOCK_NUMBER,
  filterKeys.LIMIT,
  filterKeys.ORDER,
  filterKeys.TIMESTAMP,
]);

const validateHashOrNumber = (hashOrNumber) => {
  if (utils.isValidBlockHash(hashOrNumber)) {
    return {hash: hashOrNumber.replace('0x', ''), number: null};
  }

  if (utils.isPositiveLong(hashOrNumber, true)) {
    return {hash: null, number: hashOrNumber};
  }

  throw InvalidArgumentError.forParams(filterKeys.HASH_OR_NUMBER);
};

class BlockController extends BaseController {
  extractOrderFromFilters = (filters) => {
    const order = _.findLast(filters, {key: filterKeys.ORDER});

    return order ? orderFilterValues[order.value.toUpperCase()] : orderFilterValues.DESC;
  };

  extractOrderByFromFilters = (filters) => {
    const orderBy = filters
      .filter((f) => blockWhereFilters.includes(f.key))
      .map((f) => {
        return f.key === filterKeys.BLOCK_NUMBER ? RecordFile.INDEX : RecordFile.CONSENSUS_END;
      })[0];

    return _.isEmpty(orderBy) ? RecordFile.CONSENSUS_END : orderBy;
  };

  extractLimitFromFilters = (filters) => {
    const limit = _.findLast(filters, {key: filterKeys.LIMIT});
    return limit ? (limit.value > maxLimit ? defaultLimit : limit.value) : defaultLimit;
  };

  extractSqlFromBlockFilters = (filters) => {
    const filterQuery = {
      order: this.extractOrderFromFilters(filters),
      orderBy: this.extractOrderByFromFilters(filters),
      limit: this.extractLimitFromFilters(filters),
      whereQuery: [],
    };

    if (filters && filters.length === 0) {
      return filterQuery;
    }

    filterQuery.whereQuery = filters
      .filter((f) => blockWhereFilters.includes(f.key))
      .map((f) => {
        switch (f.key) {
          case filterKeys.BLOCK_NUMBER:
            return this.getFilterWhereCondition(RecordFile.INDEX, f);

          case filterKeys.TIMESTAMP:
            return this.getFilterWhereCondition(RecordFile.CONSENSUS_END, f);
        }
      });

    return filterQuery;
  };

  generateNextLink = (req, blocks, filters) => {
    return blocks.length
      ? utils.getPaginationLink(
          req,
          blocks.length !== filters.limit,
          {[filterKeys.BLOCK_NUMBER]: _.last(blocks).index},
          filters.order
        )
      : null;
  };

  getBlocks = async (req, res) => {
    const filters = utils.buildAndValidateFilters(req.query, acceptedBlockParameters);
    const formattedFilters = this.extractSqlFromBlockFilters(filters);
    const blocks = await RecordFileService.getBlocks(formattedFilters);

    res.locals[responseDataLabel] = {
      blocks: blocks.map((model) => new BlockViewModel(model)),
      links: {
        next: this.generateNextLink(req, blocks, formattedFilters),
      },
    };
  };

  getByHashOrNumber = async (req, res) => {
    utils.validateReq(req);
    const {hash, number} = validateHashOrNumber(req.params.hashOrNumber);
    const block = await RecordFileService.getByHashOrNumber(hash, number);

    if (!block) {
      throw new NotFoundError();
    }

    res.locals[responseDataLabel] = new BlockViewModel(block);
  };
}

export default new BlockController();
