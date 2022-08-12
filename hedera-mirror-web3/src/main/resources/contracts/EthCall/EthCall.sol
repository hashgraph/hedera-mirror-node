// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";

contract EthCall {

    string storageData = "test";

    // Pure function without arguments that multiplies two numbers (e.g. return 2*2)
    function multiplySimpleNumbers() public pure returns (uint) {
        return 2 * 2;
    }

    // External function that has an argument for a recipient account and the msg.sender transfers hbars
    function transferHbarsToAddress(address payable _recipient) payable external {
        _recipient.transfer(msg.value);
    }

    // External function that retrieves the hbar balance of a given account
    function getAccountBalance(address _owner) external view returns (uint) {
        return _owner.balance;
    }

    // External function that returns a storage field as a function result
    function returnStorageData() external view returns (string memory) {
        return storageData;
    }

    // External function that has an argument for a token address and using open zeppelin IERC20 interface as a wrapper, returns the token’s name
    function getTokenName(address _tokenAddress) external view returns (string memory) {
        return IERC20Metadata(_tokenAddress).name();
    }

    // External function that has an argument for a token address and using open zeppelin IERC20 interface as a wrapper, returns the token’s symbol
    function getTokenSymbol(address _tokenAddress) external view returns (string memory) {
        return IERC20Metadata(_tokenAddress).symbol();
    }
}
