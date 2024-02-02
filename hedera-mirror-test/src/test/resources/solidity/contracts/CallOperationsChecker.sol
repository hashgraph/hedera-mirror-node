// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.0;
pragma experimental ABIEncoderV2;

contract CallOperationsChecker {

//    function call(address _address) payable {
//        _address.call.value(msg.value)(bytes4(keccak256("storeValue(uint256)")));
//    }

//    function callCode(address _address) payable {
//        _address.callcode.value(msg.value)(bytes4(keccak256("storeValue(uint256)")));
//    }

//    function delegateCall(address _address) payable {
//        _address.delegatecall(bytes4(keccak256("storeValue(uint256)")));
//    }

    function callcodeMint(address tokenAddress, uint64 amount, bytes[] memory metadata) public payable {
        (bool success, bytes memory result) = address(0x167).call(abi.encodeWithSignature("mintToken((address, uint64, bytes[] memory)", tokenAddress, amount, metadata));
        if (!success) {
            revert("Mint Callcode failed!");
        }
    }

//    function mintTokenCallCode(uint64 amount, address tokenAddress, bytes[] memory metadata) public
//    returns (bool success, bytes memory result) {
//        (success, result) = precompileAddress.callcode(
//            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
//                tokenAddress, amount, metadata));
//
//        int mintResponse = success
//            ? abi.decode(result, (int32))
//            : (HederaResponseCodes.UNKNOWN);
//
//        if (mintResponse != HederaResponseCodes.SUCCESS) {
//            revert ("Token mint failed");
//        }
//    }

//    function staticcall(address _address) payable {
//        bool callSuccess;
//        bytes memory callData = abi.encodeWithSelector(bytes4(keccak256("storeValue(uint256)")));
//        assembly {
//            callSuccess := staticcall(gas, _address, add(callData, 0x20), mload(callData), callData, 0x20)
//        }
//    }
}
