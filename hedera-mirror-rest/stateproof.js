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
'use strict';

const _ = require('lodash');
const utils = require('./utils');
const config = require('./config');
const constants = require('./constants');
const EntityId = require('./entityId');
const TransactionId = require('./transactionId');
const s3client = require('./s3client');
const {DbError} = require('./errors/dbError');
const {NotFoundError} = require('./errors/notFoundError');
const {FileDownloadError} = require('./errors/fileDownloadError');
const {InvalidConfigError} = require("./errors/invalidConfigError");

/**
 * Handler function for /transactions/:transaction_id/stateproof API.
 * @param {Request} req HTTP request object
 * @param {} res HTTP response object
 * @returns {} none
 */
const getStateProofForTransaction = async (req, res) => {
  const transactionId = TransactionId.fromString(req.params.id);
  const consensusNs = await getSuccessfulTransactionConsensusNs(transactionId);
  const rcdFileName = await getRCDFileNameByConsensusNs(consensusNs);
  const {addressBooks, nodeAccountIds} = await getAddressBooksAndNodeAccountIdsByConsensusNs(consensusNs);

  const sigFileObjects = await downloadRecordStreamFilesFromObjectStorage(
    ..._.map(nodeAccountIds, nodeAccountId => `${nodeAccountId}/${rcdFileName}_sig`)
  );

  // try to download the record stream file from the nodes which have signature files successfully downloaded
  let rcdFileBase64Data = '';
  for (const sigFileObject of sigFileObjects) {
    const nodeAccountIdStr = _.first(sigFileObject.partialFilePath.split('/'));
    const rcdFilePartialPath = nodeAccountIdStr + '/' + rcdFileName;
    try {
      const rcdFileObjects = await downloadRecordStreamFilesFromObjectStorage(rcdFilePartialPath);
      rcdFileBase64Data = _.first(rcdFileObjects).base64Data;
      break;
    } catch (err) {
      log.error(`Failed to download ${rcdFilePartialPath}: ${err.message}`);
    }
  }

  if (!rcdFileBase64Data) {
    throw new FileDownloadError(`Failed to download record file ${rcdFileName}`);
  }

  let sigFilesMap = {};
  _.forEach(sigFileObjects, sigFileObject => {
    const nodeAccountIdStr = _.first(sigFileObject.partialFilePath.split('/'));
    sigFilesMap[nodeAccountIdStr] = sigFileObject.base64Data;
  });

  res.locals[constants.responseDataLabel] = {
    'record_file': rcdFileBase64Data,
    'signature_files': sigFilesMap,
    'address_books': _.map(addressBooks, addressBook => {
      return Buffer.from(addressBook).toString('base64');
    })
  };
};

/**
 * Get the consensus_ns of the transaction. Throws exception if no such successful transaction found or multiple such
 * transactions found.
 * @param {TransactionId} transactionId
 * @returns {Promise<String>} consensus_ns of the successful transaction if found
 */
let getSuccessfulTransactionConsensusNs = async (transactionId) => {
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs()];
  const sqlQuery =
      `SELECT consensus_ns
       FROM transaction
       WHERE payer_account_id = ?
         AND valid_start_ns = ?
         AND result = 0`; // only the successful transaction
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace('getSuccessfulTransactionConsensusNs: ' + pgSqlQuery + JSON.stringify(sqlParams));
  }

  let result;
  try {
    result = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  const rows = result.rows;
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
  const sqlParams = [consensusNs,];
  const sqlQuery =
      `SELECT name
       FROM t_record_files
       WHERE consensus_start <= ?
         AND consensus_end >= ?`;
  const pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace('getRCDFileNameByConsensusNs: ' + pgSqlQuery + JSON.stringify(sqlParams));
  }

  let result;
  try {
    result = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  if (_.isEmpty(result.rows)) {
    throw new NotFoundError('No matching RCD file found');
  } else if (result.rows.length > 1) {
    throw new DbError('Invalid state, more than one RCD files found');
  }

  return _.first(result.rows).name;
};

/**
 * Get the chain of address books and node account IDs at or before consensusNs.
 * @param {String} consensusNs
 * @returns {Promise<Object>} List of address book data in chronological order and list of node account IDs.
 */
let getAddressBooksAndNodeAccountIdsByConsensusNs = async (consensusNs) => {
  // Get the chain of address books whose consensus_timestamp <= consensusNs
  let sqlParams = [consensusNs,];
  let sqlQuery =
      `SELECT consensus_timestamp, file_data, node_count
       FROM address_book
       WHERE consensus_timestamp <= ?
         AND is_complete = TRUE
         AND file_id = 102
       ORDER BY consensus_timestamp`;
  let pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace('getAddressBooksAndNodeAccountIDsByConsensusNs: ' + pgSqlQuery + JSON.stringify(sqlParams));
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
  const lastAddressBook = _.last(addressBookQueryResult.rows);
  sqlParams = [lastAddressBook.consensus_timestamp,];
  sqlQuery =
      `SELECT node_account_id
       FROM node_address
       WHERE consensus_timestamp = ?`;
  pgSqlQuery = utils.convertMySqlStyleQueryToPostgres(sqlQuery, sqlParams);
  if (logger.isTraceEnabled()) {
    logger.trace('getAddressBooksAndNodeAccountIDsByConsensusNs: ' + pgSqlQuery + JSON.stringify(sqlParams));
  }

  let nodeAddressQueryResult;
  try {
    nodeAddressQueryResult = await pool.query(pgSqlQuery, sqlParams);
  } catch (err) {
    throw new DbError(err.message);
  }

  if (_.isEmpty(nodeAddressQueryResult.rows)) {
    throw new NotFoundError('No node address found');
  } else if (nodeAddressQueryResult.rows.length !== parseInt(lastAddressBook.node_count)) {
    throw new DbError('Number of nodes found mismatch node_count in address book');
  }

  return {
    addressBooks: _.map(addressBookQueryResult.rows, row => row.file_data),
    nodeAccountIds: _.map(nodeAddressQueryResult.rows, row => EntityId.fromEncodedId(row.node_account_id).toString())
  }
};

/**
 * Download the file objects (record stream file and signature files) from object storage service
 * @param partialFilePaths list of partial file path to download. partial file path is the path with bucket name and
 *                         record stream prefix stripped.
 * @returns {Promise<Array>} Array of file buffers, match the order of partialFilePaths
 */
let downloadRecordStreamFilesFromObjectStorage = async (...partialFilePaths) => {
  const streamsConfig = config.stateproof.streams;
  if (!streamsConfig ||
    !streamsConfig.bucketName ||
    !streamsConfig.record ||
    !streamsConfig.record.prefix) {
    throw new InvalidConfigError("Invalid config, can't download file");
  }

  const s3Client = s3client.createS3Client();

  const fileObjects = await Promise.all(_.map(partialFilePaths, async partialFilePath => {
    const params = {
      Bucket: streamsConfig.bucketName,
      Key: `${streamsConfig.record.prefix}${partialFilePath}`
    }
    let base64Data = '';

    return new Promise((resolve, reject) => {
      s3Client.getObject(params)
        .createReadStream()
        .on('data', chunk => {
          base64Data += Buffer.from(chunk).toString('base64');
        })
        .on('end', () => {
          resolve({
            partialFilePath,
            base64Data
          });
        })
        // it's possible some nodes may not have the record stream file / record stream signature file
        // so capture the error and return it, otherwise Promise.all will fail
        .on('error', err => {
          resolve({
            partialFilePath,
            err
          });
        });
    });
  }));

  if (_.every(fileObjects, fileObject => !!fileObject.err)) {
    logger.error('Failed to download all files:', _.map(fileObjects, fileObject => fileObject.err.message).join(','));
    throw new FileDownloadError('Failed to download all files');
  }

  return _.filter(fileObjects, fileObject => !fileObject.err);
};

module.exports = {
  getStateProofForTransaction
};

if (process.env.NODE_ENV === 'test') {
  module.exports = Object.assign(module.exports, {
    getSuccessfulTransactionConsensusNs,
    getRCDFileNameByConsensusNs,
    getAddressBooksAndNodeAccountIdsByConsensusNs,
    downloadRecordStreamFilesFromObjectStorage
  });
}
