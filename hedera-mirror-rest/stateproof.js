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
import {gunzipSync} from 'zlib';

import config from './config';
import * as constants from './constants';
import EntityId from './entityId';
import {DbError, FileDownloadError, NotFoundError} from './errors';
import s3client from './s3client';
import {CompositeRecordFile} from './stream';
import TransactionId from './transactionId';
import * as utils from './utils';

const recordFileSuffixRegex = /\.rcd(\.gz)?$/;

/**
 * Get the consensus_timestamp of the transaction. Throws exception if no such successful transaction found or multiple such
 * transactions found.
 * @param {TransactionId} transactionId
 * @param {Number} nonce
 * @param {Boolean} scheduled
 * @returns {Promise<String>} consensus_timestamp of the successful transaction if found
 */
const getSuccessfulTransactionConsensusNs = async (transactionId, nonce, scheduled) => {
  const sqlParams = [transactionId.getEntityId().getEncodedId(), transactionId.getValidStartNs(), nonce, scheduled];
  const sqlQuery = `SELECT consensus_timestamp
       FROM transaction
       WHERE payer_account_id = $1
         AND valid_start_ns = $2
         AND nonce = $3
         AND scheduled = $4
         AND result = 22`; // only the successful transaction
  if (logger.isTraceEnabled()) {
    logger.trace(`getSuccessfulTransactionConsensusNs: ${sqlQuery}, ${utils.JSONStringify(sqlParams)}`);
  }

  const {rows} = await pool.queryQuietly(sqlQuery, sqlParams);
  if (_.isEmpty(rows)) {
    throw new NotFoundError('Transaction not found');
  } else if (rows.length > 1) {
    throw new DbError('Invalid state, more than one transaction found');
  }

  return _.first(rows).consensus_timestamp;
};

/**
 * Get the RCD file name, raw bytes if available, and the account ID of the node it was downloaded from, where
 * consensusNs is in the range [consensus_start, consensus_end]. Throws exception if no such RCD file found.
 *
 * @param {string} consensusNs consensus timestamp within the range of the RCD file to search
 * @returns {Promise<{Buffer, String, String}>} RCD file name, raw bytes, and the account ID of the node the file was
 *                                              downloaded from
 */
const getRCDFileInfoByConsensusNs = async (consensusNs) => {
  const sqlQuery = `select bytes, name, node_account_id, version
       from record_file rf
       join address_book ab
         on (ab.end_consensus_timestamp is null and ab.start_consensus_timestamp < rf.consensus_start) or
            (ab.start_consensus_timestamp < rf.consensus_start and rf.consensus_start <= ab.end_consensus_timestamp)
       join address_book_entry abe on rf.node_id = abe.node_id and abe.consensus_timestamp = ab.start_consensus_timestamp
       where consensus_end >= $1
       order by consensus_end
       limit 1`;
  if (logger.isTraceEnabled()) {
    logger.trace(`getRCDFileNameByConsensusNs: ${sqlQuery}, ${consensusNs}`);
  }

  const {rows} = await pool.queryQuietly(sqlQuery, consensusNs);
  if (_.isEmpty(rows)) {
    throw new NotFoundError(`No matching RCD file found with ${consensusNs} in the range`);
  }

  const info = rows[0];
  logger.debug(`Found RCD file ${info.name} for consensus timestamp ${consensusNs}`);
  return {
    bytes: info.bytes,
    name: info.name,
    nodeAccountId: EntityId.parse(info.node_account_id).toString(),
    version: info.version,
  };
};

/**
 * Get the chain of address books and node account IDs at or before consensusNs.
 * @param {String} consensusNs
 * @returns {Promise<Object>} List of base64 address book data in chronological order and list of node account IDs.
 */
const getAddressBooksAndNodeAccountIdsByConsensusNs = async (consensusNs) => {
  // Get the chain of address books whose start_consensus_timestamp <= consensusNs, also aggregate the corresponding
  // memo and node account ids from table address_book_entry
  let sqlQuery = `SELECT
         file_data,
         node_count,
         string_agg(memo, ',') AS memos,
         string_agg(cast(abe.node_account_id AS VARCHAR), ',') AS node_account_ids
       FROM address_book ab
       LEFT JOIN address_book_entry abe
         ON ab.start_consensus_timestamp = abe.consensus_timestamp
       WHERE start_consensus_timestamp <= $1
         AND file_id = 102
       GROUP BY start_consensus_timestamp`;
  if (config.stateproof.addressBookHistory) {
    sqlQuery += `
      ORDER BY start_consensus_timestamp`;
  } else {
    sqlQuery += `
      ORDER BY start_consensus_timestamp DESC
      LIMIT 1`;
  }

  if (logger.isTraceEnabled()) {
    logger.trace(`getAddressBooksAndNodeAccountIDsByConsensusNs: ${sqlQuery}, ${consensusNs}`);
  }

  const {rows} = await pool.queryQuietly(sqlQuery, consensusNs);
  if (_.isEmpty(rows)) {
    throw new NotFoundError('No address book found');
  }

  const lastAddressBook = _.last(rows);
  let nodeAccountIds = [];
  if (lastAddressBook.node_account_ids) {
    nodeAccountIds = _.map(lastAddressBook.node_account_ids.split(','), (id) => EntityId.parse(id).toString());
  } else if (lastAddressBook.memos) {
    nodeAccountIds = lastAddressBook.memos.split(',');
  }

  if (nodeAccountIds.length !== parseInt(lastAddressBook.node_count, 10)) {
    throw new DbError('Number of nodes found mismatch node_count in latest address book');
  }

  logger.debug(`Found the list of nodes "${nodeAccountIds}" for consensus timestamp ${consensusNs}`);
  const addressBooks = _.map(rows, (row) => Buffer.from(row.file_data).toString('base64'));
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
const downloadRecordStreamFilesFromObjectStorage = async (...partialFilePaths) => {
  const {bucketName} = config.stateproof.streams;
  const s3Client = s3client.createS3Client();

  const fileObjects = await Promise.all(
    _.map(partialFilePaths, async (partialFilePath) => {
      const params = {
        Bucket: bucketName,
        Key: `${constants.recordStreamPrefix}${partialFilePath}`,
        RequestPayer: 'requester',
      };

      return new Promise((resolve) => {
        const buffers = [];
        s3Client
          .getObject(params)
          .createReadStream()
          .on('data', (chunk) => {
            buffers.push(Buffer.from(chunk));
          })
          .on('end', () => {
            resolve({
              partialFilePath,
              data: Buffer.concat(buffers),
            });
          })
          // error may happen for a couple of reasons: 1. the node does not have the requested file, 2. s3 transient
          // error. so capture the error and return it, otherwise Promise.all will fail
          .on('error', (err) => {
            logger.error(`Failed to download ${utils.JSONStringify(params)}`, err);
            resolve({
              partialFilePath,
              err,
            });
          });
      });
    })
  );

  const downloaded = _.filter(fileObjects, (fileObject) => !fileObject.err);
  logger.debug(
    `Downloaded ${downloaded.length} file objects from bucket ${bucketName}: ${_.map(
      downloaded,
      (fileObj) => fileObj.partialFilePath
    )}`
  );
  return downloaded;
};

/**
 * Check if consensus can be reached given actualCount and totalCount.
 * @param {Number} actualCount
 * @param {Number} totalCount
 * @returns {boolean} if consensus can be reached
 */
const canReachConsensus = (actualCount, totalCount) => actualCount >= Math.ceil(totalCount / 3.0);

/**
 * Get the value of nonce and scheduled from query filters.
 * @param {Array} filters
 * @returns {{nonce: number, scheduled: boolean}}
 */
const getQueryParamValues = (filters) => {
  const ret = {nonce: 0, scheduled: false}; // the default
  for (const filter of filters) {
    switch (filter.key) {
      case constants.filterKeys.NONCE:
        ret.nonce = filter.value;
        break;
      case constants.filterKeys.SCHEDULED:
        ret.scheduled = filter.value;
        break;
      default:
        break;
    }
  }

  return ret;
};

/**
 * Formats the compactable record file. The compact object's keys are in snake case, and values are base64 encoded
 * strings.
 *
 * @param recordFile
 * @param transactionId
 * @param nonce
 * @param scheduled
 * @return {{}}
 */
const formatCompactableRecordFile = (recordFile, transactionId, nonce, scheduled) => {
  const base64Encode = (obj) => {
    for (const [k, v] of Object.entries(obj)) {
      if (Buffer.isBuffer(v)) {
        obj[k] = v.toString('base64');
      } else if (Array.isArray(v)) {
        obj[k] = v.map((b) => b.toString('base64'));
      }
    }

    return obj;
  };

  const compactObject = recordFile.toCompactObject(transactionId, nonce, scheduled);
  return _.mapKeys(base64Encode(compactObject), (v, k) => _.snakeCase(k));
};

/**
 * Formats the record file in either the full format or the compact format depending on its version.
 *
 * @param {Buffer} data
 * @param {TransactionId} transactionId
 * @param {Number} nonce
 * @param {boolean} scheduled
 * @returns {string|Object}
 */
const formatRecordFile = (data, transactionId, nonce, scheduled) => {
  if (!CompositeRecordFile.canCompact(data)) {
    return data.toString('base64');
  }

  return formatCompactableRecordFile(new CompositeRecordFile(data), transactionId, nonce, scheduled);
};

/**
 * Handler function for /transactions/:transactionId/stateproof API.
 * @param {Request} req HTTP request object
 * @param {Response} res HTTP response object
 * @returns none
 */
const getStateProofForTransaction = async (req, res) => {
  const filters = utils.buildAndValidateFilters(req.query, acceptedStateProofParameters);

  const transactionId = TransactionId.fromString(req.params.transactionId);
  const {nonce, scheduled} = getQueryParamValues(filters);
  const consensusNs = await getSuccessfulTransactionConsensusNs(transactionId, nonce, scheduled);
  const rcdFileInfo = await getRCDFileInfoByConsensusNs(consensusNs);
  const {addressBooks, nodeAccountIds} = await getAddressBooksAndNodeAccountIdsByConsensusNs(consensusNs);

  const sigFilename = rcdFileInfo.name.replace(recordFileSuffixRegex, '.rcd_sig');
  const sigFileObjects = await downloadRecordStreamFilesFromObjectStorage(
    ..._.map(nodeAccountIds, (accountId) => `${accountId}/${sigFilename}`)
  );

  if (!canReachConsensus(sigFileObjects.length, nodeAccountIds.length)) {
    throw new FileDownloadError(
      `Require at least 1/3 signature files to prove consensus, got ${sigFileObjects.length}` +
        ` out of ${nodeAccountIds.length} for file ${sigFilename}`
    );
  }

  // download the record file from the stored node if it's not in db
  let fileData = rcdFileInfo.bytes;
  if (!fileData) {
    const partialPath = `${rcdFileInfo.nodeAccountId}/${rcdFileInfo.name}`;
    const rcdFileObjects = await downloadRecordStreamFilesFromObjectStorage(partialPath);
    if (_.isEmpty(rcdFileObjects)) {
      throw new FileDownloadError(
        `Failed to download record file ${rcdFileInfo.name} from node ${rcdFileInfo.nodeAccountId}`
      );
    }
    fileData = _.first(rcdFileObjects).data;
  }

  const rcdFile = rcdFileInfo.version !== 6 ? fileData : gunzipSync(fileData);

  const sigFilesMap = {};
  _.forEach(sigFileObjects, (sigFileObject) => {
    const nodeAccountIdStr = _.first(sigFileObject.partialFilePath.split('/'));
    sigFilesMap[nodeAccountIdStr] = sigFileObject.data.toString('base64');
  });

  res.locals[constants.responseDataLabel] = {
    address_books: addressBooks,
    record_file: formatRecordFile(rcdFile, transactionId, nonce, scheduled),
    signature_files: sigFilesMap,
    version: rcdFileInfo.version,
  };
};

const stateproof = {
  getStateProofForTransaction,
};

const acceptedStateProofParameters = new Set([
  constants.filterKeys.NONCE,
  constants.filterKeys.SCHEDULED
]);

if (utils.isTestEnv()) {
  Object.assign(stateproof, {
    canReachConsensus,
    downloadRecordStreamFilesFromObjectStorage,
    formatCompactableRecordFile,
    getAddressBooksAndNodeAccountIdsByConsensusNs,
    getQueryParamValues,
    getRCDFileInfoByConsensusNs,
    getSuccessfulTransactionConsensusNs,
  });
}

export default stateproof;
