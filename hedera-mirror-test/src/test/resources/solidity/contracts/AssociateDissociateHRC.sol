// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

interface IHRC {
    function associate() external returns (uint256 responseCode);

    function dissociate() external returns (uint256 responseCode);
}

contract HRC is IHRC {
    function associate() public returns (uint256 responseCode) {
        return IHRC(this).associate();
    }

    function dissociate() public returns (uint256 responseCode) {
        return IHRC(this).dissociate();
    }
}

contract AssociateDissociateHRC is HRC, HederaResponseCodes {
    function tokenAssociate(address tokenAddress) public {
        uint256 response = IHRC(tokenAddress).associate();

        if (int32(int256(response)) != HederaResponseCodes.SUCCESS) {
            revert ("Associate Failed");
        }
    }

    function tokenDissociate(address tokenAddress) public {
        uint256 response = IHRC(tokenAddress).dissociate();

        if (int32(int256(response)) != HederaResponseCodes.SUCCESS) {
            revert ("Dissociate Failed");
        }
    }
}