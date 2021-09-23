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

package domain

const tableNameNftTransfer = "nft_transfer"

type NftTransfer struct {
	ConsensusTimestamp int64     `json:"consensus_timestamp"`
	ReceiverAccountId  *EntityId `json:"receiver_account_id"`
	SenderAccountId    *EntityId `json:"sender_account_id"`
	SerialNumber       int64     `json:"serial_number"`
	TokenId            EntityId  `json:"token_id"`
}

func (NftTransfer) TableName() string {
	return tableNameNftTransfer
}
