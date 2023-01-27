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

type TokenTransferBuilder struct {
	dbClient      interfaces.DbClient
	tokenTransfer domain.TokenTransfer
}

func (b *TokenTransferBuilder) AccountId(accountId int64) *TokenTransferBuilder {
	b.tokenTransfer.AccountId = domain.MustDecodeEntityId(accountId)
	return b
}

func (b *TokenTransferBuilder) Amount(amount int64) *TokenTransferBuilder {
	b.tokenTransfer.Amount = amount
	return b
}

func (b *TokenTransferBuilder) Timestamp(timestamp int64) *TokenTransferBuilder {
	b.tokenTransfer.ConsensusTimestamp = timestamp
	return b
}

func (b *TokenTransferBuilder) TokenId(tokenId int64) *TokenTransferBuilder {
	b.tokenTransfer.TokenId = domain.MustDecodeEntityId(tokenId)
	return b
}

func (b *TokenTransferBuilder) Persist() {
	b.dbClient.GetDb().Create(&b.tokenTransfer)
}

func NewTokenTransferBuilder(dbClient interfaces.DbClient) *TokenTransferBuilder {
	return &TokenTransferBuilder{
		dbClient:      dbClient,
		tokenTransfer: domain.TokenTransfer{PayerAccountId: defaultPayer},
	}
}
