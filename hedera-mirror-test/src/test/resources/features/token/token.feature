@tokenbase @fullsuite
Feature: HTS Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Token Flow - Create, Associate, Freeze, GrantKyc, Fund, Update, Burn, Mint, Wipe, Pause, Unpause, Dissociate, Delete
        Given I successfully create a new token with freeze status 2 and kyc status 1
        Then the mirror node REST API should return the transaction
        When I associate a new recipient account with token
        Then the mirror node REST API should return the transaction
        Then the mirror node REST API should return the token relationship for token
        And I set new account freeze status to <freezeStatus>
        Then the mirror node REST API should return the transaction
        And I set new account kyc status to <kycStatus>
        Then the mirror node REST API should return the transaction
        Then I transfer <amount> tokens to recipient
        Then the mirror node REST API should return the transaction for token fund flow
        Then I update the token
        And the mirror node REST API should confirm token update
        Then I burn <modifySupplyAmount> from the token
        And the mirror node REST API should return the transaction
        Then I mint <modifySupplyAmount> from the token
        And the mirror node REST API should return the transaction
        Then I wipe <amount> from the token
        And the mirror node REST API should return the transaction
        Then I pause the token
        And the mirror node REST API should return the transaction
        And the mirror node Token Info REST API should return pause status "PAUSED"
        Then I unpause the token
        And the mirror node REST API should return the transaction
        And the mirror node Token Info REST API should return pause status "UNPAUSED"
        Then I dissociate the account from the token
        And the mirror node REST API should return the transaction
        Then I delete the token
        And the mirror node REST API should return the transaction
        Examples:
            | amount | freezeStatus | kycStatus | modifySupplyAmount |
            | 2350   | 2            | 1         | 100                |

    @acceptance @nft @critical @release
    Scenario Outline: Validate Full NFT Flow - Create, Associate, Mint, Transfer, Burn, Wipe, Update Treasury, Delete
        Given I successfully create a new nft with supplyType <supplyType>
        Then the mirror node REST API should return the transaction
        When I associate a new recipient account with token
        And the mirror node REST API should return the transaction
        Then the mirror node REST API should return the token relationship for nft
        Then I mint a serial number from the token
        And the mirror node REST API should return the transaction for token 0 serial number 0 transaction flow
        Then I transfer serial number of token to recipient
        And the mirror node REST API should return the transaction for token 0 serial number 0 full flow
        Then I wipe serial number 0 from token 0
        And the mirror node REST API should return the transaction for token 0 serial number 0 transaction flow
        Then I mint a serial number from the token
        And the mirror node REST API should return the transaction for token 0 serial number 1 transaction flow
        Then I burn serial number 1 from token 0
        And the mirror node REST API should return the transaction for token 0 serial number 1 transaction flow
        #TODO This test should be updated when services enables the ability to change NFT treasury accounts.
        Then I update the treasury of token 0 to recipient 0
        And the mirror node REST API should return the transaction
        Then I delete the token
        And the mirror node REST API should return the transaction for token 0 serial number 1 transaction flow
        Examples:
            | supplyType |
            | "INFINITE" |

    @acceptance @customfees
    Scenario Outline: Validate Base Token Flow with Custom Fees Schedule - Create, Associate, Fund, Transfer
        # create the first token for fixed fee paid in token
        Given I successfully create a new token
        And the mirror node REST API should return the transaction
        Given I associate a new recipient account with token 0
        And the mirror node REST API should return the transaction
        # create the second token with empty custom fees so we can associate recipient accounts
        And I successfully create a new token with custom fees schedule
            | amount | numerator | denominator | collector | maximum | minimum | token |
            | 50     |           |             | 0         |         |         |       |
            | 20     |           |             | 0         |         |         | 0     |
        Then the mirror node REST API should confirm token 1 with custom fees schedule
        # create 4 recipients, 0 as hbar fee collector, 1 as fixed fee token collector, 2 as fractional fee collector,
        # and the last as token transfer recipient
        Given I associate a new recipient account with token 0
        And the mirror node REST API should return the transaction
        Given I associate a new recipient account with token 1
        And the mirror node REST API should return the transaction
        Given I associate a new recipient account with token 1
        And the mirror node REST API should return the transaction
        # create a sender account, the sender account needs to associate with both tokens
        Given I associate a new sender account with token 0
        And the mirror node REST API should return the transaction
        Given I associate an existing sender account 0 with token 1
        And the mirror node REST API should return the transaction
        # fund the sender account with both tokens
        Given I transfer <fundAmount> tokens 0 to sender
        And the mirror node REST API should return the transaction for token 0 fund flow
        Given I transfer <fundAmount> tokens 1 to sender
        And the mirror node REST API should return the transaction for token 1 fund flow
        # update token 1's custom fees schedule
        Given I update token 1 with new custom fees schedule
            | amount | numerator | denominator | collector | maximum | minimum | token |
            | 100    |           |             | 0         |         |         |       |
            | 10     |           |             | 1         |         |         | 0     |
            |        | 1         | 10          | 2         |         |         |       |
        And the mirror node REST API should return the transaction
        # make a transfer from sender 0 to the last recipient and verify the assessed custom fees
        When Sender 0 transfers 200 tokens 1 to recipient 3 with fractional fee 20
        Then the mirror node REST API should return the transaction for token 1 fund flow with assessed custom fees
            | amount | collector | token |
            | 100    | 0         |       |
            | 10     | 1         | 0     |
            | 20     | 2         | 1     |

        Examples:
            | fundAmount |
            | 3000       |
