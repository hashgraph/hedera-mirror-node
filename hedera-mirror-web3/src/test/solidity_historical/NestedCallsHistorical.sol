pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract NestedCallsHistorical is HederaTokenService {

    function nestedGetTokenInfoAndHardcodedResult(address token) external returns (IHederaTokenService.TokenInfo memory) {
        (int responseCode, IHederaTokenService.TokenInfo memory retrievedTokenInfo) = HederaTokenService.getTokenInfo(token);
        return retrievedTokenInfo;
    }

    function nestedHtsGetApprovedAndHardcodedResult(address token, uint256 serialNumber) public returns (address) {
        (int _responseCode, address approved) = HederaTokenService.getApproved(token, serialNumber);
        return approved;
    }

    function nestedMintTokenAndHardcodedResult(address token, int64 amount, bytes[] memory metadata) public returns (int64) {
        (int responseCode, int64 newTotalSupply, int64[] memory serialNumbers) = HederaTokenService.mintToken(token, amount, metadata);
        return newTotalSupply;
    }
}
