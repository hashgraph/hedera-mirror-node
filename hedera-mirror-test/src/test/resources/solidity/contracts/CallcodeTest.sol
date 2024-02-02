// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.0;
pragma experimental ABIEncoderV2;

import "./IHederaTokenService.sol";

contract CallcodeTest {
    function callcodeMint(address tokenAddress, uint64 amount, bytes[] memory metadata) public payable {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("mintToken((address, uint64, bytes[] memory)", tokenAddress, amount, metadata));
        if (!success) {
            revert("Mint Callcode failed!");
        }
    }
}