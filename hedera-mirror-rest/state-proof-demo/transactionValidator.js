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
const crypto = require('crypto');

/**
 * For signature files with the same file name:
 * (1) verify that the signature files are signed by corresponding node's PublicKey provided by addressBook
 * (2) For valid signature files, we compare their Hashes to see if at least 1/3 of hashes match.
 * (3) We compare the hash of data file with Hash which has been agreed on by valid signatures,
 * if match return true otherwise false for stateProof
 */

const performStateProof = (nodePublicKeyMap, signatureFilesMap, recordFileHash) => {
  let validated = false;
  const consensusValidatedHash = verifySignatures(nodePublicKeyMap, signatureFilesMap);
  if (_.isNull(consensusValidatedHash)) {
    console.error(`Unable to validate signature files!`);
    return validated;
  }

  return validateRecordFileHash(recordFileHash, consensusValidatedHash);
};

// given map of addressBook node -> public Key & map of signatureFiles node -> Signature can do verifySignature()
// for every signature from a file verify the signature against the appropriate public key form address book
// ensure # of validations match node count or at least is consensus by 1/3
const verifySignatures = (nodePublicKeyMap, signatureFilesMap) => {
  let validatedSignatureFilesMap = {};
  let consensusHashMap = {hash: null, count: 0};
  let maxHashCount = 0;

  // create a map of hash -> nodeId to show alignment
  _.forEach(signatureFilesMap, (sigMapItem) => {
    console.info(`Verify signatures passed for node ${sigMapItem.nodeId}`);
    const publicKeyBuffer = nodePublicKeyMap[sigMapItem.nodeId].publicKey;
    const sigMapItemHashHex = sigMapItem.hash.toString('hex');
    if (verifySignature(publicKeyBuffer, sigMapItem.hash, sigMapItem.signature)) {
      if (_.isEmpty(validatedSignatureFilesMap[sigMapItemHashHex])) {
        validatedSignatureFilesMap[sigMapItemHashHex] = [sigMapItem.nodeId];
      } else {
        validatedSignatureFilesMap[sigMapItemHashHex].push(sigMapItem.nodeId);
        let nodeCount = validatedSignatureFilesMap[sigMapItemHashHex].length;

        // determine max. Sufficient to do here as you'd never want the max to occur in the if where the count would be 1
        if (nodeCount > maxHashCount) {
          maxHashCount = nodeCount;
          consensusHashMap.hash = sigMapItemHashHex;
          consensusHashMap.count = maxHashCount;
        }
      }
    } else {
      console.error(`Failed to verify signatures for node ${sigMapItem.nodeId}!`);
    }
  });

  // return hash if it was observed by a super majority
  return maxHashCount >= Math.ceil(signatureFilesMap.length / 3.0) ? consensusHashMap.hash : null;
};

//compare the hash of data file with Hash which has been agreed on by valid signatures
const validateRecordFileHash = (recordFileHash, consensusValidatedHash) => {
  let valid = true;

  if (recordFileHash !== consensusValidatedHash) {
    valid = false;
    console.error(
      `Hash mismatch between recordFileHash: ${recordFileHash} and consensus validated signature files hash: ${consensusValidatedHash}!`
    );
  }

  return valid;
};

const verifyRSASignature = (hexEncodedPublicKey, hash, signature) => {
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

const verifySignature = (publicKeyHex, hash, signature) => {
  const verify = crypto.createVerify('RSA-SHA384');
  verify.update(hash);

  const publicKey =
    '-----BEGIN PUBLIC KEY-----\n' +
    Buffer.from(publicKeyHex, 'hex').toString('base64') +
    '\n' +
    '-----END PUBLIC KEY-----';
  return verify.verify(publicKey, signature);
};

module.exports = {
  performStateProof,
  verifySignatures,
  validateRecordFileHash,
  verifySignature,
};
