package services

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/hex"
)

// Commons - Struct implementing common functionalities used by more than 1 service
type Commons struct {
	blockRepo       repositories.BlockRepository
	transactionRepo repositories.TransactionRepository
}

// NewCommons - Service containing common functions that are shared between other services
func NewCommons(blockRepo repositories.BlockRepository, transactionRepo repositories.TransactionRepository) Commons {
	return Commons{
		blockRepo:       blockRepo,
		transactionRepo: transactionRepo,
	}
}

func (c *Commons) RetrieveBlock(bIdentifier *rTypes.PartialBlockIdentifier) (*types.Block, *rTypes.Error) {
	if bIdentifier.Hash != nil && bIdentifier.Index != nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByIdentifier(*bIdentifier.Index, h)
	} else if bIdentifier.Hash == nil {
		return c.blockRepo.FindByIndex(*bIdentifier.Index)
	} else if bIdentifier.Index == nil {
		h := hex.SafeRemoveHexPrefix(*bIdentifier.Hash)
		return c.blockRepo.FindByHash(h)
	} else {
		return c.blockRepo.RetrieveLatest()
	}
}
