/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import EntityId from '../entityId.js';
import {TokenFreezeStatus, TokenKycStatus} from '../model';
import {nsToSecNs} from '../utils';

/**
 * TokenRelationship view model
 */
class TokenRelationshipViewModel {
  /**
   * Constructs tokenRelationship view model
   *
   * @param {TokenRelationship} tokenRelationship
   */
  constructor(tokenRelationship) {
    this.automatic_association = tokenRelationship.automaticAssociation;
    this.balance = tokenRelationship.balance;
    this.created_timestamp = nsToSecNs(tokenRelationship.createdTimestamp);
    this.freeze_status = new TokenFreezeStatus(tokenRelationship.freezeStatus);
    this.kyc_status = new TokenKycStatus(tokenRelationship.kycStatus);
    this.token_id = EntityId.parse(tokenRelationship.tokenId).toString();
  }
}

export default TokenRelationshipViewModel;
