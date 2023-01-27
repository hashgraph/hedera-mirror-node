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

const protoToName = {
  7: 'CONTRACTCALL',
  8: 'CONTRACTCREATEINSTANCE',
  9: 'CONTRACTUPDATEINSTANCE',
  10: 'CRYPTOADDLIVEHASH',
  11: 'CRYPTOCREATEACCOUNT',
  12: 'CRYPTODELETE',
  13: 'CRYPTODELETELIVEHASH',
  14: 'CRYPTOTRANSFER',
  15: 'CRYPTOUPDATEACCOUNT',
  16: 'FILEAPPEND',
  17: 'FILECREATE',
  18: 'FILEDELETE',
  19: 'FILEUPDATE',
  20: 'SYSTEMDELETE',
  21: 'SYSTEMUNDELETE',
  22: 'CONTRACTDELETEINSTANCE',
  23: 'FREEZE',
  24: 'CONSENSUSCREATETOPIC',
  25: 'CONSENSUSUPDATETOPIC',
  26: 'CONSENSUSDELETETOPIC',
  27: 'CONSENSUSSUBMITMESSAGE',
  28: 'UNCHECKEDSUBMIT',
  29: 'TOKENCREATION',
  31: 'TOKENFREEZE',
  32: 'TOKENUNFREEZE',
  33: 'TOKENGRANTKYC',
  34: 'TOKENREVOKEKYC',
  35: 'TOKENDELETION',
  36: 'TOKENUPDATE',
  37: 'TOKENMINT',
  38: 'TOKENBURN',
  39: 'TOKENWIPE',
  40: 'TOKENASSOCIATE',
  41: 'TOKENDISSOCIATE',
  42: 'SCHEDULECREATE',
  43: 'SCHEDULEDELETE',
  44: 'SCHEDULESIGN',
  45: 'TOKENFEESCHEDULEUPDATE',
  46: 'TOKENPAUSE',
  47: 'TOKENUNPAUSE',
  48: 'CRYPTOAPPROVEALLOWANCE',
  49: 'CRYPTODELETEALLOWANCE',
  50: 'ETHEREUMTRANSACTION',
  51: 'NODESTAKEUPDATE',
  52: 'PRNG',
};

// Temporary extra mappings until we can update the hashgraph proto dependency with the latest types
const custom = {
  UTILPRNG: 52,
};

const UNKNOWN = 'UNKNOWN';

const protoToCustom = _.invert(custom);
const nameToProto = _.invert(protoToName);

const getName = (protoId) => {
  return protoToCustom[protoId] || protoToName[protoId] || UNKNOWN;
};

const getProtoId = (name) => {
  if (!_.isString(name)) {
    throw new InvalidArgumentError(`Invalid argument ${name} is not a string`);
  }

  const nameUpper = name.toUpperCase();
  let type = nameToProto[nameUpper];

  if (!type) {
    type = custom[nameUpper];

    if (!type) {
      throw new InvalidArgumentError(`Invalid transaction type ${nameUpper}`);
    }
  }

  return type;
};

const isValid = (name) => {
  if (!_.isString(name)) {
    return false;
  }

  const nameUpper = name.toUpperCase();
  return nameToProto[nameUpper] !== undefined || custom[nameUpper] !== undefined;
};

export default {
  isValid,
  getName,
  getProtoId,
};
