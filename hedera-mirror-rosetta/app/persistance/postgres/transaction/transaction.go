package transaction

import "github.com/jinzhu/gorm"

type TransactionRepository struct {
	dbClient *gorm.DB
}

func NewTransactionRepository(dbClient *gorm.DB) *TransactionRepository {
	return &TransactionRepository{dbClient: dbClient}
}
