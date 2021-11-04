// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/**
 * Mirror node contract receives parsed transactions of given shard and stores for querying.
 * Serves up transaction info for a query fee
 */
contract MirrorNodeShard {
    address payable private owner;

    mapping(uint256 => string) transactions;

    MirrorNode private parentContract;

    uint32 private shard;
    uint256 private queryFee;
    uint256 private totalFeesCollected;
    uint256 private totalQueriesReceived;
    uint256 private transactionStoredCount;
    uint256 private maxTransactionsCount;

    constructor(
        address _owner,
        address _parent,
        uint256 _queryFee,
        uint32 _shard,
        uint256 _maxTransactionsCount
    ) {
        parentContract = MirrorNode(_parent);
        queryFee = _queryFee;
        shard = _shard;
        owner = payable(_owner);
        maxTransactionsCount = _maxTransactionsCount;
        totalFeesCollected = 0;
        totalQueriesReceived = 0;
    }

    function getTotalFeesCollected() public view returns (uint256) {
        require(
            msg.sender == owner,
            "MirrorNodeShard: getTotalFeesCollected non owner checking fees collected"
        );
        return totalFeesCollected;
    }

    function getShard() public view returns (uint32) {
        return shard;
    }

    function getTotalQueriesReceived() public view returns (uint256) {
        return totalQueriesReceived;
    }

    function getTransactionStoredCount() public view returns (uint256) {
        return transactionStoredCount;
    }

    function storeTransaction(uint256 timestamp, string memory transaction)
        external
        payable
        returns (bool)
    {
        require(
            transactionStoredCount + 1 <= maxTransactionsCount,
            "MirrorNodeShard: storeTransaction max transaction count"
        );

        // update counters and mappings
        transactions[timestamp] = transaction;
        transactionStoredCount += 1;

        emit TransactionStorage(shard, timestamp, address(this));
        return true;
    }

    function getTransactionInfo(uint256 timestamp)
        public
        payable
        returns (string memory)
    {
        require(
            msg.value >= queryFee,
            "MirrorNodeShard: getTransactionInfo gas exceeds networkFee"
        );

        string memory transaction = transactions[timestamp];
        require(
            bytes(transaction).length > 0,
            "MirrorNodeShard: transaction contents exceed 0 bytes"
        );

        // update counters
        totalFeesCollected += msg.value;
        totalQueriesReceived += 1;

        emit TransactionRequest(shard, timestamp, address(this));
        return transaction;
    }

    // allow mirror node mainter to withdraw fees paid to them for storing and serving up transactions
    function withdraw(uint256 amount) public {
        require(msg.sender == owner, "Not owner");
        owner.transfer(amount);
    }

    receive() external payable {}

    event TransactionStorage(
        uint32 shard,
        uint256 timestamp,
        address indexed mirror
    );
    event TransactionRequest(
        uint32 shard,
        uint256 timestamp,
        address indexed mirror
    );
}

/**
 * Receives network transactions, parses them and passes them on to enrolled mirror node.
 * Recieves an enrollment fee when mirror node enroll as well as a percentage of paid fees for mirror node query payment
 *
 * Note: Contract does not yet implement full ERC 20 logic, allowance and approval logic is missing
 */
contract MirrorNode {
    address payable private owner;

    mapping(uint32 => address) private shardAddresses;
    mapping(uint256 => uint32) private timestampShards;

    uint256 private apiFee;
    uint32 private maxShardCount;
    uint256 private maxTransactionsPerShard;
    uint32 private shardCount;
    uint256 private transactionCount;
    uint256 private storageFee;

    constructor(
        uint32 _maxShardCount,
        uint256 _maxTransactionsPerShard,
        uint256 _storageFee,
        uint256 _apiFee
    ) {
        maxShardCount = _maxShardCount;
        maxTransactionsPerShard = _maxTransactionsPerShard;
        storageFee = _storageFee;
        apiFee = _apiFee;
        transactionCount = 0;
        shardCount = 0;
        owner = payable(msg.sender);
    }

    function getMaxShardCount() public view returns (uint256) {
        return maxShardCount;
    }

    function getMaxTransactionsPerShard() public view returns (uint256) {
        return maxTransactionsPerShard;
    }

    function getShardCount() public view returns (uint256) {
        return shardCount;
    }

    function getShardAddress(uint8 shard) public view returns (address) {
        require(
            shard <= shardCount,
            "MirrorNode: getShardAddress with out of range shard integer"
        );

        return shardAddresses[shard];
    }

    function getTransactionShard(uint256 timestamp)
        public
        view
        returns (uint32)
    {
        require(
            timestamp >= 0,
            "MirrorNode: getTransactionShard with out of range timestamp"
        );

        return timestampShards[timestamp];
    }

    function getTransactionCount() public view returns (uint256) {
        return transactionCount;
    }

    function getStorageFee() public view returns (uint256) {
        return storageFee;
    }

    function getApiFee() public view returns (uint256) {
        return apiFee;
    }

    /**
     * Simple storage function.
     * A few edge cases remain, such as duplicate transction handling, ensuring timestampt increase with time etc
     */
    function submitTransaction(
        uint256 timestamp,
        string memory transaction,
        uint32 shard
    ) public payable returns (bool) {
        require(
            msg.value >= storageFee,
            "MirrorNode: submitTransaction with inadequate storageFee payment"
        );
        require(
            msg.sender != address(0),
            "MirrorNode: submitTransaction from the zero address"
        );
        require(
            timestamp > 0,
            "MirrorNode: timestamp should be greater than 0"
        );

        // check if shard contract exist, if not create and cache address
        if (shardAddresses[shard] == address(0)) {
            shardAddresses[shard] = address(
                new MirrorNodeShard(
                    owner,
                    address(this),
                    apiFee,
                    shard,
                    maxTransactionsPerShard
                )
            );

            shardCount += 1;
            emit NewMirrorNodeShard(shard, timestamp);
        }

        // pass transaction to appropriate shard
        address payable mirrorAddress = payable(shardAddresses[shard]);
        MirrorNodeShard(mirrorAddress).storeTransaction{value: msg.value / 2}(
            timestamp,
            transaction
        );

        // update counters and mappings
        timestampShards[timestamp] = shard;
        transactionCount += 1;

        // share half the fee with shard for storage maintenance
        payable(mirrorAddress).transfer(msg.value / 2);

        emit TransactionParsed(timestamp, shard);
        return true;
    }

    // allow mirror node mainter to withdraw fees paid to them for parsing and storing transactions
    function withdraw(uint256 amount) public {
        require(msg.sender == owner, "Not owner");
        owner.transfer(amount);
    }

    event TransactionParsed(uint256 timestamp, uint32 shard);
    event NewMirrorNodeShard(uint32 shard, uint256 timestamp);
}
