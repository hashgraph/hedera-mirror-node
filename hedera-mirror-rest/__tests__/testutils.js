/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import path from 'path';
import {fileURLToPath} from 'url';

const invalidBase32Strs = [
  // A base32 group without padding can have 2, 4, 5, 7 or 8 characters from its alphabet
  '',
  'A',
  'AAA',
  'AAAAAA',
  // non-base32 characters
  '00',
  '11',
  '88',
  '99',
  'aa',
  'AA======', // padding not accepted
];

const assertSqlQueryEqual = (actual, expected) => {
  expect(formatSqlQueryString(actual)).toEqual(formatSqlQueryString(expected));
};

const formatSqlQueryString = (query) => {
  return query
    .trim()
    .replace(/\n/g, ' ')
    .replace(/\(\s+/g, '(')
    .replace(/\s+\)/g, ')')
    .replace(/\s+/g, ' ')
    .replace(/\s*,\s+/g, ',')
    .toLowerCase();
};

const getAllAccountAliases = (alias) => [alias, `0.${alias}`, `0.0.${alias}`];

const getModuleDirname = (importMeta) => path.dirname(fileURLToPath(importMeta.url));

const isV2Schema = () => process.env.MIRROR_NODE_SCHEMA === 'v2';

const hexRegex = /^(0x)?[0-9A-Fa-f]+$/;

const valueToBuffer = (value) => {
  if (value === null) {
    return value;
  }

  if (typeof value === 'string') {
    if (hexRegex.test(value)) {
      return Buffer.from(value.replace(/^0x/, '').padStart(2, '0'), 'hex');
    }

    // base64
    return Buffer.from(value, 'base64');
  } else if (Array.isArray(value)) {
    return Buffer.from(value);
  }

  return value;
};

export {
  assertSqlQueryEqual,
  formatSqlQueryString,
  getAllAccountAliases,
  getModuleDirname,
  invalidBase32Strs,
  isV2Schema,
  valueToBuffer,
};
