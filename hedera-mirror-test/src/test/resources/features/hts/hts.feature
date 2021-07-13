@tokenbase @fullsuite
Feature: HTS Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Base Token Flow - Create, Associate, Fund
        Given I successfully create a new token
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        Then the mirror node REST API should return status <httpStatusCode>
        Then I transfer <amount> tokens to recipient
        Then the mirror node REST API should return status <httpStatusCode> for token fund flow
        Examples:
            | amount | httpStatusCode |
            | 2350   | 200            |

    @release @acceptance
    Scenario Outline: Validate Freeze and KYC Flow - Create, Unfreeze, GrantKyc
        Given I successfully create a new token with freeze status <initialFreezeStatus> and kyc status <initialKycStatus>
        When I associate a new recipient account with token
        And I set new account freeze status to <newFreezeStatus>
        Then the mirror node REST API should return status <httpStatusCode>
        And I set new account kyc status to <newKycStatus>
        Then the mirror node REST API should return status <httpStatusCode>
        Examples:
            | initialFreezeStatus | initialKycStatus | newFreezeStatus | newKycStatus | httpStatusCode |
            | 1                   | 2                | 2               | 1            | 200            |

    @acceptance
    Scenario Outline: Validate Token Modification Flow - Create, Associate, Transfer, Update, Burn, Mint and Wipe
        Given I successfully create a new token
        Then the mirror node REST API should return status <httpStatusCode>
        When I associate a new recipient account with token
        And I transfer <amount> tokens to recipient
        Then the mirror node REST API should return status <httpStatusCode> for token fund flow
        Then I update the token
        And the mirror node REST API should confirm token update
        Then I burn <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I mint <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I wipe <modifySupplyAmount> from the token
        And the mirror node REST API should return status <httpStatusCode>
        Examples:
            | amount | httpStatusCode | modifySupplyAmount |
            | 2350   | 200            | 100                |

    @acceptance
    Scenario Outline: Validate Token ramp down Flow - Create, Associate, Dissociate, Delete
        Given I successfully create a new token
        When I associate a new recipient account with token
        And the mirror node REST API should return status <httpStatusCode>
        Then I dissociate the account from the token
        And the mirror node REST API should return status <httpStatusCode>
        Then I delete the token
        And the mirror node REST API should return status <httpStatusCode>
        Examples:
            | httpStatusCode |
            | 200            |

    @acceptance
    Scenario Outline: Validate Base Token Flow with Custom Fees Schedule - Create, Associate, Fund, Transfer
        # create the first token for fixed fee paid in token
        Given I successfully create a new token
        And the mirror node REST API should return status <httpStatusCode>
        # create the second token with empty custom fees so we can associate recipient accounts
        Given I successfully create a new token
        And the mirror node REST API should return status <httpStatusCode>
        # create 4 recipients, 0 as hbar fee collector, 1 as fixed fee token collector, 2 as fraction fee collector,
        # and the last as token transfer recipient
        Given I associate a new recipient account with token 0
        And the mirror node REST API should return status <httpStatusCode>
        Given I associate a new recipient account with token 0
        And the mirror node REST API should return status <httpStatusCode>
        Given I associate a new recipient account with token 1
        And the mirror node REST API should return status <httpStatusCode>
        Given I associate a new recipient account with token 1
        And the mirror node REST API should return status <httpStatusCode>
        # create a sender account, the sender account needs to associate with both tokens
        Given I associate a new sender account with token 0
        And the mirror node REST API should return status <httpStatusCode>
        Given I associate an existing sender account 0 with token 1
        And the mirror node REST API should return status <httpStatusCode>
        # fund the sender account with both tokens
        Given I transfer <fundAmount> tokens 0 to sender
        And the mirror node REST API should return status <httpStatusCode> for token 0 fund flow
        Given I transfer <fundAmount> tokens 1 to sender
        And the mirror node REST API should return status <httpStatusCode> for token 1 fund flow
        # update token 1's custom fees schedule
        Given I update token 1 with new custom fees schedule "<customFeesSchedule>"
        And the mirror node REST API should return status <httpStatusCode>
        # make a transfer from sender 0 to the last recipient and verify the assessed custom fees
        When Sender 0 transfers <transferAmount> tokens 1 to recipient 3
        Then the mirror node REST API should return status <httpStatusCode> for token 1 fund flow with assessed custom fees <assessedCustomFees>

        Examples:
            | fundAmount | httpStatusCode | customFeesSchedule     | transferAmount | assessedCustomFees         |
            | 3000       | 200            | 100,0,;10,1,0;1/10,2,, | 200            | 100,0,0,;10,1,0,0;20,2,0,1 |
