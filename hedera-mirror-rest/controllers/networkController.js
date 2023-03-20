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

import BaseController from './baseController';
import config from '../config';
import {
  DECIMALS_IN_HBARS,
  filterKeys,
  networkSupplyCurrencyFormatType,
  networkSupplyQuery,
  orderFilterValues,
  responseContentType,
  responseDataLabel,
} from '../constants';
import entityId from '../entityId';
import {InvalidArgumentError, NotFoundError} from '../errors';
import {AddressBookEntry, FileData} from '../model';
import {FileDataService, NetworkNodeService} from '../service';
import * as utils from '../utils';
import {
  ExchangeRateSetViewModel,
  FeeScheduleViewModel,
  NetworkNodeViewModel,
  NetworkStakeViewModel,
  NetworkSupplyViewModel,
} from '../viewmodel';

const networkNodesDefaultSize = 10;
const networkNodesMaxSize = 25;

class NetworkController extends BaseController {
  static contentTypeTextPlain = 'text/plain; charset=utf-8';

  /**
   * Extracts SQL where conditions, params, order, and limit
   *
   * @param {[]} filters parsed and validated filters
   */
  extractNetworkNodesQuery = (filters) => {
    let limit = networkNodesDefaultSize;
    let order = orderFilterValues.ASC;
    let fileId = '102'; // default fileId for mirror node
    const startPosition = 2; // 1st index is reserved for fileId
    const conditions = [];
    const params = [];
    const nodeInValues = [];
    let fileIdSpecified = false;

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      switch (filter.key) {
        case filterKeys.FILE_ID:
          if (fileIdSpecified) {
            throw new InvalidArgumentError(`Only a single instance is supported for ${filterKeys.FILE_ID}`);
          }
          if (utils.opsMap.eq !== filter.operator) {
            throw new InvalidArgumentError(
              `Only equals (eq) comparison operator is supported for ${filterKeys.FILE_ID}`
            );
          }
          fileId = filter.value;
          fileIdSpecified = true;
          break;
        case filterKeys.NODE_ID:
          this.updateConditionsAndParamsWithInValues(
            filter,
            nodeInValues,
            params,
            conditions,
            AddressBookEntry.getFullName(AddressBookEntry.NODE_ID),
            startPosition + conditions.length
          );
          break;
        case filterKeys.LIMIT:
          // response per address book node can be large so a reduced limit is enforced
          if (filter.value > networkNodesMaxSize) {
            throw new InvalidArgumentError(`Max value of ${networkNodesMaxSize} is supported for ${filterKeys.LIMIT}`);
          }
          limit = filter.value;
          break;
        case filterKeys.ORDER:
          order = filter.value;
          break;
        default:
          break;
      }
    }

    this.updateQueryFiltersWithInValues(
      params,
      conditions,
      nodeInValues,
      AddressBookEntry.getFullName(AddressBookEntry.NODE_ID),
      params.length + startPosition
    );

    return {
      conditions,
      params: [fileId].concat(params),
      order,
      limit,
    };
  };

  extractFileDataQuery = (filters) => {
    // get the latest rate only. Since logic pulls most recent items order and limit are omitted in filterQuery
    const filterQuery = {
      whereQuery: [],
    };
    let order = orderFilterValues.ASC;

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      if (filter.key === filterKeys.TIMESTAMP) {
        if (utils.opsMap.ne === filter.operator) {
          throw new InvalidArgumentError(`Not equals (ne) operator is not supported for ${filterKeys.TIMESTAMP}`);
        }

        // to ensure most recent occurrence is found convert eq to lte
        if (utils.opsMap.eq === filter.operator) {
          filter.operator = utils.opsMap.lte;
        }

        filterQuery.whereQuery.push(FileDataService.getFilterWhereCondition(FileData.CONSENSUS_TIMESTAMP, filter));
      }

      if (filter.key === filterKeys.ORDER) {
        order = filter.value;
      }
    }

    return {filterQuery, order};
  };

  extractSupplyQuery = (filters) => {
    const conditions = [];
    const params = [];
    let q;

    for (const filter of filters) {
      if (_.isNil(filter)) {
        continue;
      }

      if (filter.key === filterKeys.TIMESTAMP) {
        if (filter.operator === utils.opsMap.ne) {
          throw InvalidArgumentError.forParams(`Not equals (ne) operator is not supported for ${filterKeys.TIMESTAMP}`);
        }

        // to ensure most recent occurrence is found convert eq to lte
        if (utils.opsMap.eq === filter.operator) {
          filter.operator = utils.opsMap.lte;
        }

        this.updateConditionsAndParamsWithInValues(
          filter,
          null,
          params,
          conditions,
          'abf.consensus_timestamp',
          params.length + 1
        );
      } else if (filter.key === filterKeys.Q) {
        q = filter.value;
      }
    }

    return {
      params,
      conditions,
      q,
    };
  };

  /**
   * Handler function for /network/exchangerate API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getExchangeRate = async (req, res) => {
    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query, this.acceptedExchangeRateParameters);

    const {filterQuery} = this.extractFileDataQuery(filters);

    const exchangeRate = await FileDataService.getExchangeRate(filterQuery);

    if (_.isNil(exchangeRate)) {
      throw new NotFoundError('Not found');
    }

    res.locals[responseDataLabel] = new ExchangeRateSetViewModel(exchangeRate);
  };

  /**
   * Handler function for /network/nodes API
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @returns {Promise<void>}
   */
  getNetworkNodes = async (req, res) => {
    // extract filters from query param
    const filters = utils.buildAndValidateFilters(req.query, this.acceptedNodeParameters);

    const {conditions, params, order, limit} = this.extractNetworkNodesQuery(filters);
    const nodes = await NetworkNodeService.getNetworkNodes(conditions, params, order, limit);

    const response = {
      nodes: nodes.map((node) => new NetworkNodeViewModel(node)),
      links: {
        next: null,
      },
    };

    if (response.nodes.length === limit) {
      const lastRow = _.last(response.nodes);
      const last = {
        [filterKeys.NODE_ID]: lastRow.node_id,
      };
      response.links.next = utils.getPaginationLink(req, false, last, order);
    }

    res.locals[responseDataLabel] = response;
  };

  /**
   * Handler function for /network/stake API.
   * @param {Request} _req HTTP request object
   * @param {Response} res HTTP response object
   * @return {Promise<void>}
   */
  getNetworkStake = async (_req, res) => {
    utils.validateReq(_req);
    const networkStake = await NetworkNodeService.getNetworkStake();
    if (networkStake === null) {
      throw new NotFoundError();
    }

    res.locals[responseDataLabel] = new NetworkStakeViewModel(networkStake);
  };

  /**
   * Helper function for getSupply method.
   * @param {string} valueInTinyCoins a number of tinycoin
   * @param {networkSupplyCurrencyFormatType} currencyFormat desired output format
   * @return {string}
   * @throws {InvalidArgumentError}
   */
  convertToCurrencyFormat = (valueInTinyCoins = '0', currencyFormat = config.network.currencyFormat) => {
    switch (currencyFormat) {
      case networkSupplyCurrencyFormatType.TINYBARS:
        return valueInTinyCoins;
      case networkSupplyCurrencyFormatType.HBARS:
        // emulate integer division via substring
        if (valueInTinyCoins.length <= DECIMALS_IN_HBARS) {
          return '0';
        }
        return valueInTinyCoins.substring(0, valueInTinyCoins.length - DECIMALS_IN_HBARS);
      case networkSupplyCurrencyFormatType.BOTH:
      default:
        // emulate floating point division via adding leading zeroes or substring/slice
        if (valueInTinyCoins.length <= DECIMALS_IN_HBARS) {
          return '0.' + valueInTinyCoins.padStart(DECIMALS_IN_HBARS, '0');
        }
        return (
          valueInTinyCoins.substring(0, valueInTinyCoins.length - DECIMALS_IN_HBARS) +
          '.' +
          valueInTinyCoins.slice(-DECIMALS_IN_HBARS)
        );
    }
  };

  /**
   * Handler function for /network/supply API.
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @return {Promise<void>}
   */
  getSupply = async (req, res) => {
    const filters = utils.buildAndValidateFilters(req.query, this.acceptedSupplyParameters);
    const {conditions, params, q} = this.extractSupplyQuery(filters);
    const networkSupply = await NetworkNodeService.getSupply(conditions, params);

    if (networkSupply === null || !networkSupply.consensus_timestamp) {
      throw new NotFoundError();
    }

    const viewModel = new NetworkSupplyViewModel(networkSupply);

    if (q) {
      const valueInTinyCoins = q === networkSupplyQuery.TOTALCOINS ? viewModel.total_supply : viewModel.released_supply;
      const valueInCurrencyFormat = this.convertToCurrencyFormat(valueInTinyCoins, config.network.currencyFormat);
      res.locals[responseDataLabel] = valueInCurrencyFormat;
      res.locals[responseContentType] = NetworkController.contentTypeTextPlain;
    } else {
      res.locals[responseDataLabel] = viewModel;
    }
  };

  /**
   * Handler function for /network/fees API.
   * @param {Request} req HTTP request object
   * @param {Response} res HTTP response object
   * @return {Promise<void>}
   */
  getFees = async (req, res) => {
    const filters = utils.buildAndValidateFilters(req.query, this.acceptedFeesParameters);
    const {filterQuery, order} = this.extractFileDataQuery(filters);

    const [exchangeRate, feeSchedule] = await Promise.all([
      FileDataService.getExchangeRate(filterQuery),
      FileDataService.getFeeSchedule(filterQuery),
    ]);

    if (_.isNil(exchangeRate) || _.isNil(feeSchedule)) {
      throw new NotFoundError('Not found');
    }

    res.locals[responseDataLabel] = new FeeScheduleViewModel(feeSchedule, exchangeRate, order);
  };

  acceptedExchangeRateParameters = new Set([filterKeys.TIMESTAMP]);
  acceptedFeesParameters = new Set([filterKeys.ORDER, filterKeys.TIMESTAMP]);

  acceptedNodeParameters = new Set([
    filterKeys.FILE_ID,
    filterKeys.LIMIT,
    filterKeys.NODE_ID,
    filterKeys.ORDER
  ]);

  acceptedSupplyParameters = new Set([filterKeys.TIMESTAMP, filterKeys.Q]);
}

export default new NetworkController();
