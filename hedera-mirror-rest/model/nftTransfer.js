/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

class NftTransfer {
  /**
   * Parses nft_transfer from element in transaction.nft_transfer jsonb column
   */
  constructor(nftTransfer) {
    this.isApproval = nftTransfer.is_approval;
    this.receiverAccountId = nftTransfer.receiver_account_id;
    this.senderAccountId = nftTransfer.sender_account_id;
    this.serialNumber = nftTransfer.serial_number;
    this.tokenId = nftTransfer.token_id;
  }

  static IS_APPROVAL = `is_approval`;
  static RECEIVER_ACCOUNT_ID = `receiver_account_id`;
  static SENDER_ACCOUNT_ID = `sender_account_id`;
  static SERIAL_NUMBER = `serial_number`;
  static TOKEN_ID = `token_id`;
}

export default NftTransfer;
