@customfees
Feature: HTS Custom Fees Base Coverage Feature

    @acceptance
    Scenario Outline: Validate Base Token Flow with Custom Fees Schedule - Create, Associate, Fund, Transfer
        # create the first token for fixed fee paid in token
        Given I successfully create a new token
        And the mirror node REST API should return status <httpStatusCode>
        # create the second token with empty custom fees so we can associate recipient accounts
        Given I successfully create a new token
        And the mirror node REST API should return status <httpStatusCode>
        # create 4 recipients, 0 as hbar fee collector, 1 as fixed fee token collector, 2 as fractional fee collector,
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
        # A custom fees schedule entry is in the format of "amount,recipient account index,denominating token index,max,min"
        # For a fractional fee, amount is presented as, e.g., "1/10", empty string is used for an unset optional field.
        # fees are separated by ";". Same rules apply to assessed custom fees in the format of
        # "amount,recipient account index,sender(payer) account index,denominating token index"
            | fundAmount | httpStatusCode | customFeesSchedule     | transferAmount | assessedCustomFees         |
            | 3000       | 200            | 100,0,;10,1,0;1/10,2,, | 200            | 100,0,0,;10,1,0,0;20,2,0,1 |

    @acceptance
    Scenario Outline: Validate Base Token Creation with Updated Custom Fees Schedule - Create, UpdateFeeSchedule
        Given I successfully create a new token
        And I associate a new recipient account with token 0
        And I successfully create a new token with custom fees schedule <initialCustomFeesSchedule>
        Then the mirror node REST API should confirm token 1 with custom fees schedule <initialCustomFeesSchedule>
        Given I associate an existing recipient account 0 with token 1
        And I update token 1 with new custom fees schedule "<newCustomFeesSchedule>"
        Then the mirror node REST API should confirm token 1 with custom fees schedule <newCustomFeesSchedule>

        Examples:
            | initialCustomFeesSchedule | newCustomFeesSchedule  |
            | 50,0,;20,0,0              | 7,0,;13,0,0;1/20,0,8,1 |
