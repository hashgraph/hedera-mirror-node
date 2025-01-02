/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

const TINYBARS_PER_HBAR = 100000000n;
const TINYBARS_LENGTH = TINYBARS_PER_HBAR.toString().length - 1;
const ZERO = '0';

const abs = (n) => (n === -0 || n < 0n) ? -n : n;

const padZeros = (str, length) => {
  const padLength = Math.max(0, length - str.length);
  return ZERO.repeat(padLength) + str;
};

const toHbars = tinybars => {
  if (typeof tinybars != 'bigint') {
    return '';
  }

  const sign = tinybars < 0 ? '-' : '';
  const hbars = abs(tinybars) / TINYBARS_PER_HBAR;
  const fraction = abs(tinybars) % TINYBARS_PER_HBAR;
  const paddedFraction = fraction === 0n ? ZERO : padZeros(fraction.toString(), TINYBARS_LENGTH);
  return `${sign}${hbars}.${paddedFraction}`;
};

const toIsoString = timestamp => {
  if (!timestamp) {
    return '';
  }

  const parts = timestamp.split('.');
  const seconds = parts.length >= 1 ? parseInt(parts[0]) : 0;
  const nanos = parts.length > 1 ? padZeros(parts[1], 9) : '000000000';
  const date = new Date(seconds * 1000).toISOString().slice(0, 20);
  return `${date}${nanos}Z`;
};

export {toHbars, toIsoString};
