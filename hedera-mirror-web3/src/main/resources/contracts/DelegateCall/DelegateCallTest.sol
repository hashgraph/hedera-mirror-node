// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./EthCall.sol";

contract DelegateCallTest {

    function writeToStorageSlotWithDelegateCall(address _addr, string memory _value) payable external {
        (bool success, bytes memory result) =
        _addr.delegatecall(
            abi.encodeWithSelector(EthCall.writeToStorageSlot.selector, _value));
        if (success == false) {
            // if there is a return reason string
            if (result.length > 0) {
                // bubble up any reason for revert
                assembly {
                    let result_size := mload(result)
                    revert(add(32, result), result_size)
                }
            } else {
                revert("Function call reverted");
            }
        }
    }
}
