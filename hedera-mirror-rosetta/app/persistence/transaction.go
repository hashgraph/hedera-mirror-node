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

package persistence

import (
	"context"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                                                 = 2000
	transactionResultFeeScheduleFilePartUploaded        int32 = 104
	transactionResultSuccess                            int32 = 22
	transactionResultSuccessButMissingExpectedOperation int32 = 220
)

const (
	andTransactionHashFilter  = " and transaction_hash = @hash"
	orderByConsensusTimestamp = " order by consensus_timestamp"
	// selectDissociateTokenTransfersInTimestampRange selects the token transfers and nft transfers for successful token
	// dissociate which dissociates an account from tokens which are already deleted
	selectDissociateTokenTransfersInTimestampRange = "with" + genesisTimestampCte + `
      , success_dissociate as (
        select entity_id as account_id, consensus_timestamp
        from transaction
        where type = 41 and result = 22
           and consensus_timestamp >= @start and consensus_timestamp <= @end
      ), dissociated_token as (
        select ta.account_id, ta.token_id, t.type, t.decimals, sd.consensus_timestamp
        from token_account ta
        join success_dissociate sd
          on ta.account_id = sd.account_id and lower(ta.timestamp_range) = sd.consensus_timestamp
        join token t
          on t.token_id = ta.token_id
        join entity e
          on e.id = t.token_id and e.deleted is true
            and sd.consensus_timestamp > lower(e.timestamp_range)
        join genesis
          on t.created_timestamp > timestamp
      ), ft_balance as (
        select
          d.*,
          (
            with snapshot as (
              select abf.consensus_timestamp + abf.time_offset as timestamp
              from account_balance_file as abf
              where abf.consensus_timestamp + abf.time_offset < d.consensus_timestamp
              order by abf.consensus_timestamp desc
              limit 1
            )
            select
              coalesce((
                select balance
                from token_balance as tb
                where tb.account_id = d.account_id and tb.token_id = d.token_id
                  and tb.consensus_timestamp = snapshot.timestamp
              ), 0) + (
                select coalesce(sum(amount), 0)
                from token_transfer as tt
                where tt.account_id = d.account_id and tt.token_id = d.token_id
                  and tt.consensus_timestamp > snapshot.timestamp
                  and tt.consensus_timestamp < d.consensus_timestamp
              )
            from snapshot
          ) balance
        from (select * from dissociated_token where type = 'FUNGIBLE_COMMON') as d
      ), ft_xfer as  (
        select
          consensus_timestamp,
          json_agg(json_build_object(
            'account_id', account_id,
            'amount', -balance,
            'decimals', decimals,
            'token_id', token_id,
            'type', type
          ) order by token_id) as token_transfers
        from ft_balance
        where balance <> 0
        group by consensus_timestamp
      ), nft_xfer as (
        select
          consensus_timestamp,
          json_agg(json_build_object(
            'receiver_account_id', null,
            'sender_account_id', nft.account_id,
            'serial_number', nft.serial_number,
            'token_id', nft.token_id
          ) order by nft.token_id, nft.serial_number) as nft_transfers
        from (select * from dissociated_token where type = 'NON_FUNGIBLE_UNIQUE') as d
        join nft on nft.account_id = d.account_id and nft.token_id = d.token_id
        where nft.deleted is null or nft.deleted is false or nft.modified_timestamp = d.consensus_timestamp
        group by consensus_timestamp
      )
      select
        coalesce(fx.consensus_timestamp, nx.consensus_timestamp) as consensus_timestamp,
        coalesce(fx.token_transfers, '[]') as token_transfers,
        coalesce(nx.nft_transfers, '[]') as nft_transfers
      from ft_xfer fx
      full outer join nft_xfer nx
        on fx.consensus_timestamp = nx.consensus_timestamp
      order by coalesce(fx.consensus_timestamp, nx.consensus_timestamp)`
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json, non-fee transfers
	// in json, token transfers in json, and optionally the token information when the transaction is token create,
	// token delete, or token update. Note the three token transactions are the ones the entity_id in the transaction
	// table is its related token id and require an extra rosetta operation
	selectTransactionsInTimestampRange = "with" + genesisTimestampCte + `select
                                            t.consensus_timestamp,
                                            t.entity_id,
                                            t.memo,
                                            t.payer_account_id,
                                            t.result,
                                            t.transaction_hash as hash,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount) order by entity_id)
                                              from crypto_transfer
                                              where consensus_timestamp = t.consensus_timestamp and
                                                (errata is null or errata <> 'DELETE')
                                            ), '[]') as crypto_transfers,
                                            coalesce((
                                                  select json_agg(json_build_object(
                                                      'account_id', entity_id,
                                                      'amount', amount
                                                    ) order by entity_id)
                                                  from non_fee_transfer
                                                  where consensus_timestamp = t.consensus_timestamp
                                                    and entity_id is not null
                                            ), '[]') as non_fee_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'account_id', account_id,
                                                  'amount', amount,
                                                  'decimals', tk.decimals,
                                                  'token_id', tkt.token_id,
                                                  'type', tk.type
                                                ) order by account_id, tk.token_id)
                                              from token_transfer tkt
                                              join token tk on tk.token_id = tkt.token_id
                                              join genesis on tk.created_timestamp > genesis.timestamp
                                              where tkt.consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as token_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'receiver_account_id', receiver_account_id,
                                                  'sender_account_id', sender_account_id,
                                                  'serial_number', serial_number,
                                                  'token_id', tk.token_id
                                                ) order by tk.token_id, serial_number)
                                              from nft_transfer nftt
                                              join token tk on tk.token_id = nftt.token_id
                                              join genesis on tk.created_timestamp > genesis.timestamp
                                              where nftt.consensus_timestamp = t.consensus_timestamp and serial_number <> -1
                                            ), '[]') as nft_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', account_id,
                                                'amount', amount) order by account_id)
                                              from staking_reward_transfer
                                              where consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as staking_reward_payouts,
                                            case
                                              when t.type in (29, 35, 36) then coalesce((
                                                  select json_build_object(
                                                    'decimals', decimals,
                                                    'freeze_default', freeze_default,
                                                    'initial_supply', initial_supply,
                                                    'token_id', token_id,
                                                    'type', type
                                                  )
                                                  from token
                                                  join genesis on created_timestamp > genesis.timestamp
                                                  where token_id = t.entity_id
                                                ), '{}')
                                              else '{}'
                                            end as token
                                          from transaction t
                                          where consensus_timestamp >= @start and consensus_timestamp <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusTimestamp
)

var stakingRewardAccountId = domain.MustDecodeEntityId(800)

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// NonFeeTransfers json string, TokenTransfers json string, and Token definition json string
type transaction struct {
	ConsensusTimestamp   int64
	EntityId             *domain.EntityId
	Hash                 []byte
	Memo                 []byte
	PayerAccountId       domain.EntityId
	Result               int16
	Type                 int16
	CryptoTransfers      string
	NftTransfers         string
	NonFeeTransfers      string
	StakingRewardPayouts string
	TokenTransfers       string
	Token                string
}

func (t transaction) getHashString() string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(t.Hash))
}

type transfer interface {
	getAccountId() domain.EntityId
	getAmount() types.Amount
}

type hbarTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
}

func (t hbarTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t hbarTransfer) getAmount() types.Amount {
	return &types.HbarAmount{Value: t.Amount}
}

type singleNftTransfer struct {
	accountId    domain.EntityId
	receiver     bool
	serialNumber int64
	tokenId      domain.EntityId
}

func (n singleNftTransfer) getAccountId() domain.EntityId {
	return n.accountId
}

func (n singleNftTransfer) getAmount() types.Amount {
	amount := int64(1)
	if !n.receiver {
		amount = -1
	}

	return &types.TokenAmount{
		SerialNumbers: []int64{n.serialNumber},
		TokenId:       n.tokenId,
		Type:          domain.TokenTypeNonFungibleUnique,
		Value:         amount,
	}
}

type tokenTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
	Decimals  int64           `json:"decimals"`
	TokenId   domain.EntityId `json:"token_id"`
	Type      string          `json:"type"`
}

func (t tokenTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t tokenTransfer) getAmount() types.Amount {
	return &types.TokenAmount{
		Decimals: t.Decimals,
		TokenId:  t.TokenId,
		Type:     t.Type,
		Value:    t.Amount,
	}
}

// transactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient interfaces.DbClient
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient interfaces.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

func (tr *transactionRepository) FindBetween(ctx context.Context, start, end int64) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	transactions := make([]*transaction, 0)
	for start <= end {
		transactionsBatch := make([]*transaction, 0)
		err := db.
			Raw(
				selectTransactionsInTimestampRangeOrdered,
				sql.Named("start", start),
				sql.Named("end", end),
			).
			Limit(batchSize).
			Find(&transactionsBatch).
			Error
		if err != nil {
			log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
			return nil, hErrors.ErrDatabaseError
		}

		transactions = append(transactions, transactionsBatch...)

		if len(transactionsBatch) < batchSize {
			break
		}

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusTimestamp + 1
	}

	if err := tr.processSuccessTokenDissociates(ctx, transactions, start, end); err != nil {
		return nil, err
	}

	hashes := make([]string, 0)
	sameHashMap := make(map[string][]*transaction)
	for _, t := range transactions {
		h := t.getHashString()
		if _, ok := sameHashMap[h]; !ok {
			// save the unique hashes in chronological order
			hashes = append(hashes, h)
		}

		sameHashMap[h] = append(sameHashMap[h], t)
	}

	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, hash := range hashes {
		sameHashTransactions := sameHashMap[hash]
		transaction, err := tr.constructTransaction(sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

func (tr *transactionRepository) FindByHashInBlock(
	ctx context.Context,
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []*transaction
	transactionHash, err := hex.DecodeString(tools.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.Raw(
		selectTransactionsByHashInTimestampRange,
		sql.Named("hash", transactionHash),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Find(&transactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, rErr := tr.constructTransaction(transactions)
	if rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func (tr *transactionRepository) constructTransaction(sameHashTransactions []*transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	allFailed := true
	firstTransaction := sameHashTransactions[0]
	operations := make(types.OperationSlice, 0)
	result := &types.Transaction{Hash: firstTransaction.getHashString(), Memo: firstTransaction.Memo}
	success := types.GetTransactionResult(transactionResultSuccess)
	transactionType := types.TransactionTypes[int32(firstTransaction.Type)]

	for _, transaction := range sameHashTransactions {
		cryptoTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.CryptoTransfers), &cryptoTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nftTransfers := make([]domain.NftTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NftTransfers), &nftTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nonFeeTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NonFeeTransfers), &nonFeeTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		stakingRewardPayouts := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.StakingRewardPayouts), &stakingRewardPayouts); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		tokenTransfers := make([]tokenTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.TokenTransfers), &tokenTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		token := domain.Token{}
		if err := json.Unmarshal([]byte(transaction.Token), &token); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		var feeHbarTransfers []hbarTransfer
		var stakingRewardTransfers []hbarTransfer
		feeHbarTransfers, nonFeeTransfers, stakingRewardTransfers = categorizeHbarTransfers(
			cryptoTransfers,
			nonFeeTransfers,
			stakingRewardPayouts,
		)

		transactionResult := types.GetTransactionResult(int32(transaction.Result))

		operations = tr.appendHbarTransferOperations(transactionResult, transactionType, nonFeeTransfers, operations)
		// fee transfers are always successful regardless of the transaction result
		operations = tr.appendHbarTransferOperations(success, types.OperationTypeFee, feeHbarTransfers, operations)
		// staking reward transfers (both credit and debit) are always successful and marked as crypto transfer
		operations = tr.appendHbarTransferOperations(success, types.OperationTypeCryptoTransfer,
			stakingRewardTransfers, operations)
		operations = tr.appendTokenTransferOperations(transactionResult, transactionType, tokenTransfers, operations)
		operations = tr.appendNftTransferOperations(transactionResult, transactionType, nftTransfers, operations)

		if !token.TokenId.IsZero() {
			// only for TokenCreate, TokenDeletion, and TokenUpdate, TokenId is non-zero
			operation := getTokenOperation(len(operations), token, transaction, transactionResult, transactionType)
			operations = append(operations, operation)
		}

		if IsTransactionResultSuccessful(int32(transaction.Result)) {
			allFailed = false
			result.EntityId = transaction.EntityId
		}
	}

	if allFailed {
		// add an 0-amount hbar transfer with the failed status to indicate the transaction has failed
		operations = tr.appendHbarTransferOperations(
			types.GetTransactionResult(int32(firstTransaction.Result)),
			transactionType,
			[]hbarTransfer{{AccountId: firstTransaction.PayerAccountId}},
			operations,
		)
	}

	result.Operations = operations
	return result, nil
}

func (tr *transactionRepository) appendHbarTransferOperations(
	transactionResult string,
	operationType string,
	hbarTransfers []hbarTransfer,
	operations types.OperationSlice,
) types.OperationSlice {
	transfers := make([]transfer, 0, len(hbarTransfers))
	for _, hbarTransfer := range hbarTransfers {
		transfers = append(transfers, hbarTransfer)
	}

	return tr.appendTransferOperations(transactionResult, operationType, transfers, operations)
}

func (tr *transactionRepository) appendNftTransferOperations(
	transactionResult string,
	operationType string,
	nftTransfers []domain.NftTransfer,
	operations types.OperationSlice,
) types.OperationSlice {
	transfers := make([]transfer, 0, 2*len(nftTransfers))
	for _, nftTransfer := range nftTransfers {
		transfers = append(transfers, getSingleNftTransfers(nftTransfer)...)
	}

	return tr.appendTransferOperations(transactionResult, operationType, transfers, operations)
}

func (tr *transactionRepository) appendTokenTransferOperations(
	transactionResult string,
	operationType string,
	tokenTransfers []tokenTransfer,
	operations types.OperationSlice,
) types.OperationSlice {
	transfers := make([]transfer, 0, len(tokenTransfers))
	for _, tokenTransfer := range tokenTransfers {
		// The wiped amount of a deleted NFT class by a TokenDissociate is presented as tokenTransferList and
		// saved to token_transfer table, filter it
		if tokenTransfer.Type != domain.TokenTypeFungibleCommon {
			continue
		}

		transfers = append(transfers, tokenTransfer)
	}

	return tr.appendTransferOperations(transactionResult, operationType, transfers, operations)
}

func (tr *transactionRepository) appendTransferOperations(
	transactionResult string,
	operationType string,
	transfers []transfer,
	operations types.OperationSlice,
) types.OperationSlice {
	for _, transfer := range transfers {
		operations = append(operations, types.Operation{
			AccountId: types.NewAccountIdFromEntityId(transfer.getAccountId()),
			Amount:    transfer.getAmount(),
			Index:     int64(len(operations)),
			Status:    transactionResult,
			Type:      operationType,
		})
	}
	return operations
}

func categorizeHbarTransfers(hbarTransfers, nonFeeTransfers, stakingRewardPayouts []hbarTransfer) (
	feeHbarTransfers, adjustedNonFeeTransfers, stakingRewardTransfers []hbarTransfer,
) {
	entityIds := make(map[int64]struct{})
	for _, transfer := range hbarTransfers {
		entityIds[transfer.AccountId.EncodedId] = struct{}{}
	}

	adjustedNonFeeTransfers = make([]hbarTransfer, 0, len(nonFeeTransfers))
	for _, nonFeeTransfer := range nonFeeTransfers {
		entityId := nonFeeTransfer.AccountId.EncodedId
		// skip non fee transfer whose entity id is not in the transaction record's transfer list. One exception is
		// we always add non fee transfers to the staking reward account if there are staking reward payouts
		_, exists := entityIds[entityId]
		if exists || (entityId == stakingRewardAccountId.EncodedId && len(stakingRewardPayouts) != 0) {
			adjustedNonFeeTransfers = append(adjustedNonFeeTransfers, nonFeeTransfer)
		}
	}

	// add a crypto transfer which debits the total payout from account 0.0.800 if any
	rewardPayoutTotal := int64(0)
	stakingRewardTransfers = make([]hbarTransfer, 0)
	for _, stakingRewardPayout := range stakingRewardPayouts {
		rewardPayoutTotal -= stakingRewardPayout.Amount
		stakingRewardTransfers = append(stakingRewardTransfers, stakingRewardPayout)
	}

	if rewardPayoutTotal != 0 {
		stakingRewardTransfers = append(stakingRewardTransfers, hbarTransfer{
			AccountId: stakingRewardAccountId,
			Amount:    rewardPayoutTotal,
		})
	}

	nonFeeTransferMap := aggregateHbarTransfers(append(nonFeeTransfers, stakingRewardTransfers...))
	return getFeeHbarTransfers(hbarTransfers, nonFeeTransferMap), adjustedNonFeeTransfers, stakingRewardTransfers
}

func (tr *transactionRepository) processSuccessTokenDissociates(
	ctx context.Context,
	transactions []*transaction,
	start int64,
	end int64,
) *rTypes.Error {
	hasSuccessTokenDissociate := false
	for _, txn := range transactions {
		if txn.Type == domain.TransactionTypeTokenDissociate && IsTransactionResultSuccessful(int32(txn.Result)) {
			hasSuccessTokenDissociate = true
			break
		}
	}
	if !hasSuccessTokenDissociate {
		return nil
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	tokenDissociateTransactions := make([]*transaction, 0)
	if err := db.Raw(
		selectDissociateTokenTransfersInTimestampRange,
		sql.Named("start", start),
		sql.Named("end", end),
	).Scan(&tokenDissociateTransactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return hErrors.ErrDatabaseError
	}

	// replace NftTransfers and TokenTransfers for any matching transaction by consensus timestamp
	// both transactions and tokenDissociateTransactions are sorted by consensus timestamp,
	// and tokenDissociateTransactions is a subset of transactions
	for t, d := 0, 0; t < len(transactions) && d < len(tokenDissociateTransactions); t++ {
		if transactions[t].ConsensusTimestamp == tokenDissociateTransactions[d].ConsensusTimestamp {
			transactions[t].NftTransfers = tokenDissociateTransactions[d].NftTransfers
			transactions[t].TokenTransfers = tokenDissociateTransactions[d].TokenTransfers
			d++
		}
	}

	return nil
}

func IsTransactionResultSuccessful(result int32) bool {
	return result == transactionResultFeeScheduleFilePartUploaded ||
		result == transactionResultSuccess ||
		result == transactionResultSuccessButMissingExpectedOperation
}

func aggregateHbarTransfers(hbarTransfers []hbarTransfer) map[int64]int64 {
	transferMap := make(map[int64]int64)

	for _, transfer := range hbarTransfers {
		// in case there are multiple transfers for the same entity, accumulate it
		transferMap[transfer.AccountId.EncodedId] += transfer.Amount
	}

	return transferMap
}

func getFeeHbarTransfers(cryptoTransfers []hbarTransfer, nonFeeTransferMap map[int64]int64) []hbarTransfer {
	cryptoTransferMap := make(map[int64]hbarTransfer)
	accountIds := make([]int64, 0)
	for _, transfer := range cryptoTransfers {
		accountId := transfer.AccountId.EncodedId
		if _, ok := cryptoTransferMap[accountId]; !ok {
			accountIds = append(accountIds, accountId)
		}
		cryptoTransferMap[accountId] = hbarTransfer{
			AccountId: transfer.AccountId,
			Amount:    transfer.Amount + cryptoTransferMap[accountId].Amount,
		}
	}

	adjusted := make([]hbarTransfer, 0, len(cryptoTransfers))
	for _, accountId := range accountIds {
		aggregated := cryptoTransferMap[accountId]
		amount := aggregated.Amount - nonFeeTransferMap[accountId]
		if amount != 0 {
			adjusted = append(adjusted, hbarTransfer{
				AccountId: aggregated.AccountId,
				Amount:    amount,
			})
		}
	}

	return adjusted
}

func getSingleNftTransfers(nftTransfer domain.NftTransfer) []transfer {
	transfers := make([]transfer, 0)
	if nftTransfer.ReceiverAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.ReceiverAccountId,
			receiver:     true,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}

	if nftTransfer.SenderAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.SenderAccountId,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}
	return transfers
}

func getTokenOperation(
	index int,
	token domain.Token,
	transaction *transaction,
	transactionResult string,
	operationType string,
) types.Operation {
	operation := types.Operation{
		AccountId: types.NewAccountIdFromEntityId(transaction.PayerAccountId),
		Index:     int64(index),
		Status:    transactionResult,
		Type:      operationType,
	}

	metadata := make(map[string]interface{})
	metadata["currency"] = types.Token{Token: token}.ToRosettaCurrency()
	metadata["freeze_default"] = token.FreezeDefault
	metadata["initial_supply"] = token.InitialSupply
	operation.Metadata = metadata

	return operation
}
