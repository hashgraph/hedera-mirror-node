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

package persistence

import (
	"context"
	"database/sql"
	"encoding/json"
	"sort"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	log "github.com/sirupsen/logrus"
)

const (
	balanceChangeBetween = `select
                              coalesce((
                                select sum(amount) from crypto_transfer
                                where
                                  consensus_timestamp > @start and
                                  consensus_timestamp <= @end and
                                  entity_id = @account_id
                              ), 0) as value,
                              coalesce((
                                select json_agg(change)
                                from (
                                  select json_build_object(
                                      'token_id', tt.token_id,
                                      'decimals', t.decimals,
                                      'type', t.type,
                                      'value', sum(tt.amount)
                                  ) change
                                  from token_transfer tt
                                  join token t
                                    on t.token_id = tt.token_id
                                  where
                                    consensus_timestamp > @start and
                                    consensus_timestamp <= @end and
                                    account_id = @account_id and
                                    t.type = 'FUNGIBLE_COMMON'
                                  group by tt.account_id, tt.token_id, t.decimals, t.type
                                ) token_change
                              ), '[]') as token_values`
	latestBalanceBeforeConsensus = `with abm as (
                                      select max(consensus_timestamp)
                                      from account_balance_file where consensus_timestamp <= @timestamp
                                    )
                                    select
                                      abm.max consensus_timestamp,
                                      coalesce(ab.balance, 0) balance,
                                      coalesce((
                                        select json_agg(json_build_object(
                                          'decimals', t.decimals,
                                          'token_id', tb.token_id,
                                          'type', t.type,
                                          'value', tb.balance
                                        ))
                                        from token_balance tb
                                        join token t on t.token_id = tb.token_id
                                        where tb.consensus_timestamp = abm.max and
                                          tb.account_id = @account_id and
                                          t.type = 'FUNGIBLE_COMMON'
                                      ), '[]') token_balances
                                    from abm
                                    left join account_balance ab
                                      on ab.consensus_timestamp = abm.max and ab.account_id = @account_id`
	// #nosec
	selectEverOwnedTokensByBlockAfter = `with next_rf as (
                                          select consensus_end
                                          from record_file
                                          where consensus_end > @consensus_timestamp
                                          order by consensus_end
                                          limit 1
                                        )
                                        select distinct on (t.token_id) t.decimals, t.token_id, t.type
                                        from token_account ta
                                        join next_rf on ta.modified_timestamp <= consensus_end
                                        join token t on t.token_id = ta.token_id
                                        where account_id = @account_id`
	selectNftTransfersForAccount = `select *
                                    from nft_transfer
                                    where consensus_timestamp <= @consensus_end and
                                      (receiver_account_id = @account_id or sender_account_id = @account_id)
                                    order by consensus_timestamp desc`
)

type combinedAccountBalance struct {
	ConsensusTimestamp int64
	Balance            int64
	TokenBalances      string
}

type accountBalanceChange struct {
	Value       int64
	TokenValues string
}

type nftId struct {
	tokenId      int64
	serialNumber int64
}

// accountRepository struct that has connection to the Database
type accountRepository struct {
	dbClient interfaces.DbClient
}

// NewAccountRepository creates an instance of a accountRepository struct
func NewAccountRepository(dbClient interfaces.DbClient) interfaces.AccountRepository {
	return &accountRepository{dbClient}
}

func (ar *accountRepository) RetrieveBalanceAtBlock(
	ctx context.Context,
	accountId int64,
	consensusEnd int64,
) ([]types.Amount, *rTypes.Error) {
	snapshotTimestamp, hbarAmount, tokenAmountMap, err := ar.getLatestBalanceSnapshot(ctx, accountId, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarValue, tokenValues, err := ar.getBalanceChange(ctx, accountId, snapshotTimestamp, consensusEnd)
	if err != nil {
		return nil, err
	}

	hbarAmount.Value += hbarValue
	tokenAmounts := getUpdatedTokenAmounts(tokenAmountMap, tokenValues)

	nftAmounts, err := ar.getNftBalance(ctx, accountId, consensusEnd)
	if err != nil {
		return nil, err
	}

	amounts := make([]types.Amount, 0, 1+len(tokenAmounts)+len(nftAmounts))
	amounts = append(amounts, hbarAmount)
	amounts = append(amounts, tokenAmounts...)
	amounts = append(amounts, nftAmounts...)

	return amounts, nil
}

func (ar *accountRepository) RetrieveEverOwnedTokensByBlockAfter(
	ctx context.Context,
	accountId int64,
	consensusEnd int64,
) ([]domain.Token, *rTypes.Error) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	if cancel != nil {
		defer cancel()
	}

	tokens := make([]domain.Token, 0)
	if err := db.Raw(
		selectEverOwnedTokensByBlockAfter,
		sql.Named("account_id", accountId),
		sql.Named("consensus_timestamp", consensusEnd),
	).Scan(&tokens).Error; err != nil {
		log.Errorf(databaseErrorFormat, errors.ErrDatabaseError.Message, err)
		return nil, errors.ErrDatabaseError
	}

	return tokens, nil
}

func (ar *accountRepository) getLatestBalanceSnapshot(ctx context.Context, accountId, consensusEnd int64) (
	int64,
	*types.HbarAmount,
	map[int64]*types.TokenAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	// gets the most recent balance at or before consensusEnd
	cb := &combinedAccountBalance{}
	if err := db.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", accountId),
		sql.Named("timestamp", consensusEnd),
	).First(cb).Error; err != nil {
		log.Errorf(databaseErrorFormat, errors.ErrDatabaseError.Message, err)
		return 0, nil, nil, errors.ErrDatabaseError
	}

	if cb.ConsensusTimestamp == 0 {
		return 0, nil, nil, errors.ErrNodeIsStarting
	}

	hbarAmount := types.HbarAmount{Value: cb.Balance}

	var tokenAmounts []*types.TokenAmount
	if err := json.Unmarshal([]byte(cb.TokenBalances), &tokenAmounts); err != nil {
		return 0, nil, nil, errors.ErrInvalidToken
	}

	tokenAmountMap := make(map[int64]*types.TokenAmount, len(tokenAmounts))
	for _, tokenAmount := range tokenAmounts {
		tokenAmountMap[tokenAmount.TokenId.EncodedId] = tokenAmount
	}

	return cb.ConsensusTimestamp, &hbarAmount, tokenAmountMap, nil
}

func (ar *accountRepository) getBalanceChange(ctx context.Context, accountId, consensusStart, consensusEnd int64) (
	int64,
	[]*types.TokenAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	change := &accountBalanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	if err := db.Raw(
		balanceChangeBetween,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).First(change).Error; err != nil {
		log.Errorf(databaseErrorFormat, errors.ErrDatabaseError.Message, err)
		return 0, nil, errors.ErrDatabaseError
	}

	// fungible token values
	var tokenValues []*types.TokenAmount
	if err := json.Unmarshal([]byte(change.TokenValues), &tokenValues); err != nil {
		return 0, nil, errors.ErrInvalidToken
	}

	return change.Value, tokenValues, nil
}

func (ar *accountRepository) getNftBalance(ctx context.Context, accountId, consensusEnd int64) (
	[]types.Amount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	nftTransfers := make([]domain.NftTransfer, 0)
	if err := db.Raw(
		selectNftTransfersForAccount,
		sql.Named("account_id", accountId),
		sql.Named("consensus_end", consensusEnd),
	).Scan(&nftTransfers).Error; err != nil {
		log.Errorf(databaseErrorFormat, errors.ErrDatabaseError.Message, err)
		return nil, errors.ErrDatabaseError
	}

	allNfts := make(map[nftId]bool)
	ownedNfts := make(map[int64][]int64)
	tokenIdMap := make(map[int64]domain.EntityId)
	// nftTransfers are ordered by consensus timestamp in descending order
	for _, nftTransfer := range nftTransfers {
		// the latest record of a nft supersedes past records
		id := nftId{tokenId: nftTransfer.TokenId.EncodedId, serialNumber: nftTransfer.SerialNumber}
		if allNfts[id] {
			continue
		}
		allNfts[id] = true

		// if accountId is the receiver, the nft instance is owned by the account at consensus_end; any previous
		// transfer of this nft instance will be ignored
		if nftTransfer.ReceiverAccountId != nil && nftTransfer.ReceiverAccountId.EncodedId == accountId {
			tokenIdMap[id.tokenId] = nftTransfer.TokenId
			ownedNfts[id.tokenId] = append(ownedNfts[id.tokenId], id.serialNumber)
		}
	}

	tokenAmounts := make([]types.Amount, 0, len(ownedNfts))
	for tokenId, serialNumbers := range ownedNfts {
		// sort the serial numbers in natural order
		sort.Slice(serialNumbers, func(i, j int) bool { return serialNumbers[i] < serialNumbers[j] })
		tokenAmount := &types.TokenAmount{
			SerialNumbers: serialNumbers,
			TokenId:       tokenIdMap[tokenId],
			Type:          domain.TokenTypeNonFungibleUnique,
			Value:         int64(len(serialNumbers)),
		}
		tokenAmounts = append(tokenAmounts, tokenAmount)
	}

	return tokenAmounts, nil
}

func getUpdatedTokenAmounts(
	tokenAmountMap map[int64]*types.TokenAmount,
	tokenValues []*types.TokenAmount,
) []types.Amount {
	for _, tokenValue := range tokenValues {
		encodedId := tokenValue.TokenId.EncodedId
		if _, ok := tokenAmountMap[encodedId]; ok {
			tokenAmountMap[encodedId].Value += tokenValue.Value
		} else {
			tokenAmountMap[encodedId] = tokenValue
		}
	}

	amounts := make([]types.Amount, 0, len(tokenAmountMap))
	for _, tokenAmount := range tokenAmountMap {
		amounts = append(amounts, tokenAmount)
	}

	return amounts
}
