// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";

contract EthCall is HederaTokenService {

    uint256 salt = 1234;
    string constant storageData = "test";
    string public emptyStorageData = "";

    // Public pure function without arguments that multiplies two numbers (e.g. return 2*2)
    function multiplySimpleNumbers() public pure returns (uint) {
        return 2 * 2;
    }

    // External function that has an argument for a recipient account and the msg.sender transfers hbars
    function transferHbarsToAddress(address payable _recipient) payable external {
        _recipient.transfer(msg.value);
    }

    // External function that has an argument for test value that will be written to contract storage slot
    function writeToStorageSlot(string memory _value) payable external returns (string memory){
        emptyStorageData = _value;
        return emptyStorageData;
    }

    // External view function that retrieves the hbar balance of a given account
    function getAccountBalance(address _owner) external view returns (uint) {
        return _owner.balance;
    }

    // External pure function that returns a storage field as a function result
    function returnStorageData() external pure returns (string memory) {
        return storageData;
    }

    // External function that freezes a given token for the message sender
    function freezeToken(address _tokenAddress) external {
        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.freezeToken.selector, _tokenAddress, msg.sender));
        require(success, "Freeze token failed");
    }

    function testRevert() external pure {
        revert('Custom revert message');
    }

    function nestedCall(string memory s, address _state) external returns (string memory) {
        return State(_state).changeState(s);
    }

    function deployContract(string memory s) external returns (string memory) {
        State deployedContract = new State();
        string memory newState = deployedContract.changeState(s);
        deployedContract.changeCallerState(newState);
        return emptyStorageData;
    }

    function deployViaCreate2() external returns (address) {
        State newContract = new State{salt: bytes32(salt)}();

        return address(newContract);
    }
}

contract State {
    string public state = "";

    function changeState(string memory s) external returns (string memory) {
        state = s;
        return state;
    }

    function changeCallerState(string memory s) external returns (string memory) {
        EthCall caller = EthCall(msg.sender);
        caller.writeToStorageSlot(string(abi.encodePacked(s, state)));
        return s;
    }
}