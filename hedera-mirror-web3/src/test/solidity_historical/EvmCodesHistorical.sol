// SPDX-License-Identifier: Apache-2.0
// Example for precompiles used: https://github.com/jstoxrocky/zksnarks_example

// post 0.8.19 versions of solidity use opcode PUSH0, which is not available for pre-v0.38 EVM
// and the historical calls would fail
pragma solidity 0.8.7;

contract EvmCodesHistorical {

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }
}