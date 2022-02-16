/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

type NftTransferBuilder struct {
	dbClient    interfaces.DbClient
	nftTransfer domain.NftTransfer
}

func (b *NftTransferBuilder) ReceiverAccountId(receiver int64) *NftTransferBuilder {
	accountId := domain.MustDecodeEntityId(receiver)
	b.nftTransfer.ReceiverAccountId = &accountId
	return b
}

func (b *NftTransferBuilder) SenderAccountId(sender int64) *NftTransferBuilder {
	accountId := domain.MustDecodeEntityId(sender)
	b.nftTransfer.SenderAccountId = &accountId
	return b
}

func (b *NftTransferBuilder) SerialNumber(serialNumber int64) *NftTransferBuilder {
	b.nftTransfer.SerialNumber = serialNumber
	return b
}

func (b *NftTransferBuilder) Timestamp(timestamp int64) *NftTransferBuilder {
	b.nftTransfer.ConsensusTimestamp = timestamp
	return b
}

func (b *NftTransferBuilder) TokenId(tokenId int64) *NftTransferBuilder {
	b.nftTransfer.TokenId = domain.MustDecodeEntityId(tokenId)
	return b
}

func (b *NftTransferBuilder) Persist() {
	b.dbClient.GetDb().Create(&b.nftTransfer)
}

func NewNftTransferBuilder(dbClient interfaces.DbClient) *NftTransferBuilder {
	return &NftTransferBuilder{
		dbClient:    dbClient,
		nftTransfer: domain.NftTransfer{PayerAccountId: defaultPayer},
	}
}
