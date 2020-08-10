package errors

import (
	"github.com/coinbase/rosetta-sdk-go/types"
	"net/http"
)

var Errors = map[string]*types.Error{
	BlockNotFound:        New(BlockNotFound, http.StatusBadRequest, true),
	StartMustBeBeforeEnd: New(StartMustBeBeforeEnd, http.StatusBadRequest, false),
}

const (
	BlockNotFound        string = "Block not found"
	StartMustBeBeforeEnd string = "Start must be before end"
)

func New(message string, statusCode int32, retriable bool) *types.Error {
	return &types.Error{
		Message:   message,
		Code:      statusCode,
		Retriable: retriable,
		Details:   nil,
	}
}
