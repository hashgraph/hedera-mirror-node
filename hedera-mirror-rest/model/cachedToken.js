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

import TokenFreezeStatus from './tokenFreezeStatus';
import TokenKycStatus from './tokenKycStatus';

const tokenFreezeValues = TokenFreezeStatus.VALUES;
const tokenKycValues = TokenKycStatus.VALUES;

/**
 * Cached token object to store token's decimals, freeze default and kyc default. Note freeze_default is not the same as
 * the freeze_default column in the token table. freeze_default / kyc_default is either copied from the SQL query result
 * directly or determined based on the freeze_default & freeze_key / kyc_key columns in the token table.
 */
class CachedToken {
  constructor(token) {
    this.decimals = token.decimals;

    if (token.freeze_key !== undefined) {
      if (token.freeze_key !== null) {
        this.freezeDefault = token.freeze_default ? tokenFreezeValues['FROZEN'] : tokenFreezeValues['UNFROZEN'];
      } else {
        this.freezeDefault = tokenFreezeValues['NOT_APPLICABLE'];
      }
    } else {
      this.freezeDefault = token.freeze_default;
    }

    if (token.kyc_key !== undefined) {
      this.kycDefault = token.kyc_key ? tokenKycValues['REVOKED'] : tokenKycValues['NOT_APPLICABLE'];
    } else {
      this.kycDefault = token.kyc_default;
    }

    this.tokenId = token.token_id;
  }
}

export default CachedToken;
