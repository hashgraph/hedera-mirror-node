// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

contract EvmCodes {

    function chainId() public view returns (uint256) {
        return block.chainid;
    }
}