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

package domain

import (
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

type NftBuilder struct {
	dbClient interfaces.DbClient
	nft      domain.Nft
}

func (b *NftBuilder) AccountId(accountId int64) *NftBuilder {
	account := domain.MustDecodeEntityId(accountId)
	b.nft.AccountId = &account
	return b
}

func (b *NftBuilder) Deleted(deleted bool) *NftBuilder {
	b.nft.Deleted = &deleted
	return b
}

func (b *NftBuilder) ModifiedTimestamp(timestamp int64) *NftBuilder {
	b.nft.ModifiedTimestamp = timestamp
	return b
}

func (b *NftBuilder) Persist() domain.Nft {
	b.dbClient.GetDb().Create(&b.nft)
	return b.nft
}

func NewNftBuilder(dbClient interfaces.DbClient, tokenId, serialNumber, timestamp int64) *NftBuilder {
	nft := domain.Nft{
		CreatedTimestamp:  &timestamp,
		ModifiedTimestamp: timestamp,
		SerialNumber:      serialNumber,
		TokenId:           domain.MustDecodeEntityId(tokenId),
	}
	return &NftBuilder{dbClient: dbClient, nft: nft}
}
