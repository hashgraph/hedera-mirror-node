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
'uses strict';

// responsible for validating files. Call object methods for certain operations

// external libraries
const _ = require('lodash');
const NodeRSA = require('node-rsa');

/**
 * For signature files with the same file name:
 * (1) verify that the signature files are signed by corresponding node's PublicKey provided by addressBook
 * (2) For valid signature files, we compare their Hashes to see if at least 1/3 of hashes match.
 * (3) We compare the hash of data file with Hash which has been agreed on by valid signatures,
 * if match return true otherwise false for stateProof
 */

const performStateProof = (nodePublicKeyMap, signatureFilesMap, signedDataFile) => {
  let validated = false;
  const validatedSignatureFilesMap = verifySignatures(nodePublicKeyMap, signatureFilesMap);
  if (_.isNull(validatedSignatureFilesMap)) {
    console.error(`Signature files signature were not valid`);
    return validated;
  }

  return true;
};

// given map of addressBook node -> public Key & map of signatureFiles node -> Signature can do verifySignature()
// for every signature from a file verify the signature against the appropriate public key form address book
// ensure # of validations match node count or at least is consensus by 1/3
const verifySignatures = (nodePublicKeyMap, signatureFilesMap) => {
  // console.log(`verifySignatures, nodePublicKeyMap: ${JSON.stringify(nodePublicKeyMap)}, signatureFilesMap: ${JSON.stringify(signatureFilesMap)}`);
  let validatedSignatureFilesMap = {};

  // create a map of hash -> nodeId to show alignment
  let failCount = 1;
  _.forEach(signatureFilesMap, (sigMapItem) => {
    // _.forEach(nodePublicKeyMap, (mapItem, key) => {
    //   const publicKeyBuffer = mapItem.publicKey;
    const publicKeyBuffer = nodePublicKeyMap[sigMapItem.nodeId].publicKey;
    // console.log(`** publicKeyBuffer for ${sigMapItem.nodeId} isUndefined ${_.isUndefined(publicKeyBuffer)} -> ${JSON.stringify(publicKeyBuffer)}`)
    if (verifySignature(publicKeyBuffer, sigMapItem.hash, sigMapItem.signature)) {
      if (_.isEmpty(validatedSignatureFilesMap[sigMapItem.hash.toString('hex')])) {
        validatedSignatureFilesMap[sigMapItem.hash.toString('hex')] = [sigMapItem.nodeId];
      } else {
        validatedSignatureFilesMap[sigMapItem.hash.toString('hex')].push(sigMapItem.nodeId);
      }

      console.info(
        `** verifySignatures passed for node ${sigMapItem.nodeId}, publicKey: ${publicKeyBuffer}, hash: ${sigMapItem.hash}, signature ${sigMapItem.signature}`
      );
    } else {
      // console.error(`** verifySignatures failed for node ${sigMapItem.nodeId}, publicKey: ${publicKeyBuffer}, hash: ${sigMapItem.hash}, signature ${sigMapItem.signature}`)
      // console.info(`** ${failCount++} verifySignatures failed for node ${sigMapItem.nodeId} and addressBookNode: ${key}, publicKey: ${publicKeyBuffer}, hash: ${sigMapItem.hash}, signature ${sigMapItem.signature}`)
    }
    // });
  });

  console.log(
    `** verifySignatures validatedSignatureFilesMap -> ${JSON.stringify(
      validatedSignatureFilesMap
    )}. isEmpty -> ${_.isEmpty(validatedSignatureFilesMap)}`
  );
  return validatedSignatureFilesMap;
};

const getConsensusSignatureFileHash = (signatureFileHashes, nodeCount) => {
  let consensusHash = null;

  return consensusHash;
};

//compare the hash of data file with Hash which has been agreed on by valid signatures
const validateRecordFileHash = (signedDataFile, signatureHash) => {
  let valid = true;

  return valid;
};

const verifySignature = (hexEncodedPublicKey, hash, signature) => {
  let key = new NodeRSA();
  key.importKey(
    {
      n: Buffer.from(hexEncodedPublicKey, 'hex'),
      e: 65537, // public exponent. 65537 by default.
    },
    'components-public'
  );

  return key.verify(hash, signature, 'buffer', 'buffer');
};

module.exports = {
  getConsensusSignatureFileHash,
  performStateProof,
  verifySignatures,
  validateRecordFileHash,
  verifySignature,
};
