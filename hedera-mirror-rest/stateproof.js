/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

'use strict';

const _ = require('lodash');
const utils = require('./utils');
const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const TransactionId = require('./transactionId');
const s3client = require('./s3client');
const { DbError } = require('./errors/dbError');
const { NotFoundError } = require('./errors/notFoundError');
const { FileDownloadError } = require('./errors/fileDownloadError');

/**
 * Get the consensus_ns of the transaction. Throws exception if no such successful transaction found or multiple such
 * transactions found.
 * @param {TransactionId} transactionId
 * @returns {Promise<String>} consensus_ns of the successful transaction if found
 */
let getSuccessfulTransactionConsensusNs = async (transactionId) => {
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];
  const sqlQuery = `SELECT consensus_ns
       FROM transaction
       WHERE payer_account_id = ?
         AND valid_start_ns = ?
         AND result = 0`; // only the successful transaction
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace(`getSuccessfulTransactionConsensusNs: ${pgSqlQuery}, ${JSON.stringify(sqlParams)}`);
  }

  let result;
  try {
    result = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  const { rows } = result;
  if (_.isEmpty(rows)) {
    throw new NotFoundError('Transaction not found');
  } else if (rows.length > 1) {
    throw new DbError('Invalid state, more than one transactions found');
  }

  return _.first(rows).consensus_ns;
};

/**
 * Get the RCD file name where consensusNs is in the range [consensus_start, consensus_end]. Throws exception if no
 * such RCD file found.
 * @param {string} consensusNs consensus timestamp within the range of the RCD file to search
 * @returns {Promise<String>} RCD file name
 */
let getRCDFileNameByConsensusNs = async (consensusNs) => {
  const sqlParams = [consensusNs];
  const sqlQuery = `SELECT name
       FROM record_file
       WHERE consensus_start <= ?
         AND consensus_end >= ?`;
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace(`getRCDFileNameByConsensusNs: ${pgSqlQuery}, ${JSON.stringify(sqlParams)}`);
  }

  let result;
  try {
    result = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  const { rows } = result;
  if (_.isEmpty(rows)) {
    throw new NotFoundError('No matching RCD file found');
  } else if (rows.length > 1) {
    throw new DbError('Invalid state, more than one RCD files found');
  }

  return _.first(rows).name;
};

/**
 * Get the chain of address books and node account IDs at or before consensusNs.
 * @param {String} consensusNs
 * @returns {Promise<Object>} List of base64 address book data in chronological order and list of node account IDs.
 */
let getAddressBooksAndNodeAccountIdsByConsensusNs = async (consensusNs) => {
  // Get the chain of address books whose consensus_timestamp <= consensusNs, also aggregate the corresponding
  // node account ids from table address_book_entry
  const sqlParams = [consensusNs];
  const sqlQuery = `SELECT
         ab.consensus_timestampfile_data as consensus_timestamp,
         file_data,
         node_count,
         string_agg(cast (abe.node_account_id as varchar), ',') as node_account_ids
       FROM address_book ab
         LEFT JOIN address_book_entry abe
           on ab.consensus_timestamp = abe.consensus_timestamp
       WHERE consensus_timestamp <= ?
         AND file_id = 102
       GROUP by consensus_timestamp
       ORDER BY consensus_timestamp`;
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace(`getAddressBooksAndNodeAccountIDsByConsensusNs: ${pgSqlQuery}, ${JSON.stringify(sqlParams)}`);
  }

  let addressBookQueryResult;
  try {
    addressBookQueryResult = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  if (_.isEmpty(addressBookQueryResult.rows)) {
    throw new NotFoundError('No address book found');
  }

  // Get the node addresses at the moment of the last address book's consensus_timestamp
  const { rows } = addressBookQueryResult;
  const lastAddressBook = _.last(rows);
  if (!lastAddressBook.node_account_ids) {
    throw new DbError('No node account IDs found by the join query');
  }

  const addressBooks = _.map(rows, (row) => Buffer.from(row.file_data).toString('base64'));
  const nodeAccountIds = _.map(lastAddressBook.node_account_ids.split(','), (nodeAccountId) => {
    return EntityId.fromEncodedId(nodeAccountId).toString();
  });

  if (nodeAccountIds.length !== parseInt(lastAddressBook.node_count, 10)) {
    throw new DbError('Number of nodes found mismatch node_count in address book');
  }

  return {
    addressBooks,
    nodeAccountIds,
  };
};

/**
 * Download the file objects (record stream file and signature files) from object storage service
 * @param partialFilePaths list of partial file path to download. partial file path is the path with bucket name and
 *                         record stream prefix stripped.
 * @returns {Promise<Array>} Array of file buffers
 */
let downloadRecordStreamFilesFromObjectStorage = async (...partialFilePaths) => {
  const streamsConfig = config.stateproof.streams;
  const s3Client = s3client.createS3Client();

  const fileObjects = await Promise.all(_.map(partialFilePaths, async (partialFilePath) => {
    const params = {
      Bucket: streamsConfig.bucketName,
      Key: `${streamsConfig.record.prefix}${partialFilePath}`,
    };

    return new Promise((resolve) => {
      let base64Data = '';
      s3Client.getObject(params)
        .createReadStream()
        .on('data', (chunk) => {
          base64Data += Buffer.from(chunk).toString('base64');
        })
        .on('end', () => {
          resolve({
            partialFilePath,
            base64Data,
          });
        })
        // error may happen for a couple of reasons: 1. the node does not have the requested file, 2. s3 transient error
        // so capture the error and return it, otherwise Promise.all will fail
        .on('error', (err) => {
          logger.error(`Failed to download ${partialFilePath}`, err);
          resolve({
            partialFilePath,
            err,
          });
        });
    });
  }));

  return _.filter(fileObjects, (fileObject) => !fileObject.err);
};

/**
 * Check if consensus can be reached given actualCount and expectedCount.
 * @param {Number} actualCount
 * @param {Number} expectedCount
 * @returns {boolean} if consensus can be reached
 */
let canReachConsensus = (actualCount, expectedCount) => actualCount >= Math.ceil(expectedCount / 3.0);

/**
 * Handler function for /transactions/:transaction_id/stateproof API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns none
 */
const getStateProofForTransaction = async (req, res) => {
  const transactionId = TransactionId.fromString(req.params.id);
  const consensusNs = await getSuccessfulTransactionConsensusNs(transactionId);
  const rcdFileName = await getRCDFileNameByConsensusNs(consensusNs);
  const { addressBooks, nodeAccountIds } = await getAddressBooksAndNodeAccountIdsByConsensusNs(consensusNs);

  const sigFileObjects = await downloadRecordStreamFilesFromObjectStorage(
    ..._.map(nodeAccountIds, (nodeAccountId) => `${nodeAccountId}/${rcdFileName}_sig`),
  );

  if (!canReachConsensus(sigFileObjects.length, nodeAccountIds.length)) {
    throw new FileDownloadError(`Require at least 1/3 signature files to prove consensus, got ${sigFileObjects.length} `
      + `out of ${nodeAccountIds.length} for file ${rcdFileName}_sig`);
  }

  // always download the record file from node 0.0.3
  const rcdFileObjects = await downloadRecordStreamFilesFromObjectStorage(`0.0.3/${rcdFileName}`);
  if (_.isEmpty(rcdFileObjects)) {
    throw new FileDownloadError(`Failed to download record file ${rcdFileName} from node 0.0.3`);
  }

  const sigFilesMap = {};
  _.forEach(sigFileObjects, (sigFileObject) => {
    const nodeAccountIdStr = _.first(sigFileObject.partialFilePath.split('/'));
    sigFilesMap[nodeAccountIdStr] = sigFileObject.base64Data;
  });

  res.locals[constants.responseDataLabel] = {
    record_file: _.first(rcdFileObjects).base64Data,
    signature_files: sigFilesMap,
    address_books: addressBooks,
  };
};

module.exports = {
  getStateProofForTransaction,
};

if (process.env.NODE_ENV === 'test') {
  module.exports = Object.assign(module.exports, {
    getSuccessfulTransactionConsensusNs,
    getRCDFileNameByConsensusNs,
    getAddressBooksAndNodeAccountIdsByConsensusNs,
    downloadRecordStreamFilesFromObjectStorage,
    canReachConsensus,
  });
}
