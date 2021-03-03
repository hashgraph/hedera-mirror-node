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

'use strict';

// external libraries
const _ = require('lodash');
const crypto = require('crypto');
const {SHA_384} = require('./constants');

/**
 * Verifies given hash was signed with the provided public key
 * Formats public key into acceptable format for usage
 * @param publicKeyHex
 * @param hash
 * @param signature
 * @returns {boolean}
 */
const verifySignature = (publicKeyHex, hash, signature) => {
  const verify = crypto.createVerify('RSA-SHA384');
  verify.update(hash);

  const publicKey = `-----BEGIN PUBLIC KEY-----\n${Buffer.from(publicKeyHex, 'hex').toString(
    'base64'
  )}\n-----END PUBLIC KEY-----`;
  return verify.verify(publicKey, signature);
};

/**
 * given map of addressBook node -> public Key & map of signatureFiles node -> Signature, verifySignature()
 * for every signature from a file against the appropriate public key from the it's match node in the address book
 * ensure the number of validations is at least 1/3 on the number of nodes
 * @param nodePublicKeyMap
 * @param signatureFilesMap
 * @returns consensus hash
 */
const verifySignatures = (nodePublicKeyMap, signatureFilesMap) => {
  const validatedSignatureFilesMap = {};
  const consensusHashMap = {hash: null, count: 0};
  let maxHashCount = 0;

  // create a map of hash -> nodeId to show alignment
  _.forEach(signatureFilesMap, (sigMapItem) => {
    console.info(`Verify signatures passed for node ${sigMapItem.nodeId}`);
    const {publicKey} = nodePublicKeyMap[sigMapItem.nodeId];
    const sigMapItemHashHex = sigMapItem.fileHash.toString(SHA_384.encoding);

    if (!verifySignature(publicKey, sigMapItem.fileHash, sigMapItem.fileHashSignature)) {
      console.error(`Failed to verify fileHash signature for node ${sigMapItem.nodeId}!`);
      return;
    }

    if (
      sigMapItem.metadataHash &&
      !verifySignature(publicKey, sigMapItem.metadataHash, sigMapItem.metadataHashSignature)
    ) {
      console.error(`Failed to verify metadataHash signature for node ${sigMapItem.nodeId}!`);
      return;
    }

    if (_.isEmpty(validatedSignatureFilesMap[sigMapItemHashHex])) {
      validatedSignatureFilesMap[sigMapItemHashHex] = [sigMapItem.nodeId];
    } else {
      validatedSignatureFilesMap[sigMapItemHashHex].push(sigMapItem.nodeId);
      const nodeCount = validatedSignatureFilesMap[sigMapItemHashHex].length;

      // update max. Sufficient to do here as you'd never want the max to occur in the if where the count would be 1
      if (nodeCount > maxHashCount) {
        maxHashCount = nodeCount;
        consensusHashMap.hash = sigMapItemHashHex;
        consensusHashMap.count = maxHashCount;
      }
    }
  });

  // return hash if it was observed by at least 1/3 of nodes
  return maxHashCount >= Math.ceil(signatureFilesMap.length / 3.0) ? consensusHashMap.hash : null;
};

/**
 * compare the hash of data file with Hash which has been agreed on by valid signatures
 * @param recordFileHash
 * @param consensusValidatedHash
 * @returns {boolean}
 */
const validateRecordFileHash = (recordFileHash, consensusValidatedHash) => {
  if (recordFileHash !== consensusValidatedHash) {
    console.error(
      `Hash mismatch between recordFileHash: ${recordFileHash} and consensus validated signature files hash: ${consensusValidatedHash}!`
    );
    return false;
  }
  console.info(`Record file hash was successfully matched with signature files`);

  return true;
};

/**
 * For signature files with the same file name:
 * (1) verify that the signature files are signed by corresponding node's PublicKey provided by addressBook
 * (2) For valid signature files, we compare their Hashes to see if at least 1/3 of hashes match.
 * (3) We compare the hash of data file with Hash which has been agreed on by valid signatures,
 * if match return true otherwise false for stateProof
 */
const performStateProof = (nodePublicKeyMap, signatureFilesMap, recordFileHash) => {
  const consensusValidatedHash = verifySignatures(nodePublicKeyMap, signatureFilesMap);
  if (_.isNull(consensusValidatedHash)) {
    console.error(`Unable to validate signature files!`);
    return false;
  }

  return validateRecordFileHash(recordFileHash, consensusValidatedHash);
};

module.exports = {
  performStateProof,
  verifySignatures,
  validateRecordFileHash,
  verifySignature,
};
