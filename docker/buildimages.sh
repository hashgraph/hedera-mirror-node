docker build -t hedera/mirrornode-db -f Postgres ../
docker build -t hedera/mirrornode-rcd-down -f RecordsDownloader ../
docker build -t hedera/mirrornode-rcd-parse -f RecordsParser ../
docker build -t hedera/mirrornode-bal-down -f BalanceDownloader ../
docker build -t hedera/mirrornode-bal-parse -f BalanceParser ../

