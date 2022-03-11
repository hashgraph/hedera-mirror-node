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
	"fmt"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
)

var defaultNodeAccountId = domain.MustDecodeEntityId(3)

type AccountBalanceFileBuilder struct {
	accountBalances    []domain.AccountBalance
	consensusTimestamp int64
	dbClient           interfaces.DbClient
	timeOffset         int32
	tokenBalances      []domain.TokenBalance
}

func (b *AccountBalanceFileBuilder) AddAccountBalance(accountId, balance int64) *AccountBalanceFileBuilder {
	b.accountBalances = append(b.accountBalances, domain.AccountBalance{
		AccountId:          domain.MustDecodeEntityId(accountId),
		Balance:            balance,
		ConsensusTimestamp: b.consensusTimestamp,
	})
	return b
}

func (b *AccountBalanceFileBuilder) AddTokenBalance(accountId, tokenId, balance int64) *AccountBalanceFileBuilder {
	b.tokenBalances = append(b.tokenBalances, domain.TokenBalance{
		AccountId:          domain.MustDecodeEntityId(accountId),
		Balance:            balance,
		ConsensusTimestamp: b.consensusTimestamp,
		TokenId:            domain.MustDecodeEntityId(tokenId),
	})
	return b
}

func (b *AccountBalanceFileBuilder) TimeOffset(timeOffset int32) *AccountBalanceFileBuilder {
	b.timeOffset = timeOffset
	return b
}

func (b *AccountBalanceFileBuilder) Persist() {
	db := b.dbClient.GetDb()

	accountBalanceFile := domain.AccountBalanceFile{
		ConsensusTimestamp: b.consensusTimestamp,
		FileHash:           fmt.Sprintf("%d", b.consensusTimestamp),
		Name:               fmt.Sprintf("account_balance_file_%d", b.consensusTimestamp),
		NodeAccountId:      defaultNodeAccountId,
		TimeOffset:         b.timeOffset,
	}
	db.Create(&accountBalanceFile)
	if len(b.accountBalances) != 0 {
		db.Create(b.accountBalances)
	}
	if len(b.tokenBalances) != 0 {
		db.Create(b.tokenBalances)
	}
}

func NewAccountBalanceFileBuilder(dbClient interfaces.DbClient, consensusTimestamp int64) *AccountBalanceFileBuilder {
	return &AccountBalanceFileBuilder{
		accountBalances:    make([]domain.AccountBalance, 0),
		consensusTimestamp: consensusTimestamp,
		dbClient:           dbClient,
		tokenBalances:      make([]domain.TokenBalance, 0),
	}
}
