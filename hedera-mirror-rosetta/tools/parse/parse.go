package parse

import "strconv"

func ToInt64(value string) (int64, error) {
	return strconv.ParseInt(value, 10, 64)
}
