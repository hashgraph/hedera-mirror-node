// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract StorageContract {
    mapping(uint256 => uint256) public storageMap;
    uint256 public storedValue;

    function updateStorage(uint256 key, uint256 value) public {
        storageMap[key] = value; // SSTORE operation
    }

    function updateSingleValue(uint256 value) public {
        storedValue = value; // SSTORE operation
    }
}