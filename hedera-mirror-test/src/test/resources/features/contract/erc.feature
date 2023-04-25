@contractbase @fullsuite
Feature: ERC Contract Base Coverage Feature

    @web3 @erc
    Scenario Outline: Validate ERC Contract
        Given I successfully create an erc contract from contract bytes with balance 0
        Then I create a new token with freeze status 2 and kyc status 1
        Then I create a new nft with supplyType <supplyType>
        Then I mint a serial number
        Then the mirror node REST API should return status 200 for the mint transaction
        And I call the erc contract via the mirror node REST API for token name
        And I call the erc contract via the mirror node REST API for token symbol
        And I call the erc contract via the mirror node REST API for token decimals
        And I call the erc contract via the mirror node REST API for token totalSupply
        And I call the erc contract via the mirror node REST API for token ownerOf
        And I call the erc contract via the mirror node REST API for token tokenUri
        And I call the erc contract via the mirror node REST API for token getApproved
        And I call the erc contract via the mirror node REST API for token allowance
        And I call the erc contract via the mirror node REST API for token isApprovedForAll
        And I call the erc contract via the mirror node REST API for token balance
        Then I approve <spenderName> for nft
        And I call the erc contract via the mirror node REST API for token getApproved with response BOB
        Then I approve <approvedForAllSpenderName> for nft all serials
        And I call the erc contract via the mirror node REST API for token isApprovedForAll with response true
        Then I approve <tokenAllowanceSpender> with <allowances>
        And I call the erc contract via the mirror node REST API for token allowance with allowances
        Examples:
            | supplyType | spenderName | approvedForAllSpenderName | tokenAllowanceSpender | allowances |
            | "INFINITE" | "BOB"       | "ALICE"                   | "ALICE"               | 2          |
