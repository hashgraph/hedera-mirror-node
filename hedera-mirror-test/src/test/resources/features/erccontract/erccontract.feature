@contractbase @fullsuite
Feature: ERC Contract Base Coverage Feature

    @web3
    Scenario Outline: Validate ERC Contract
        Given I successfully create an erc contract from contract bytes with balance
        Then I create a new token with freeze status 2 and kyc status 1
        Then I create a new nft with supplyType <supplyType>
        Then I mint a serial number
        Then I call the erc contract via the mirror node REST API
        Then I approve <spenderName> for nft
        Then Verify allowance
        Examples:
            | supplyType | spenderName |
            | "INFINITE" | "BOB"      |
