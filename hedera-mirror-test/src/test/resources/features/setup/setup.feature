@Setup
Feature: Setup entities for various features

    @SetupTokenAccounts
    Scenario Outline: Setup 2 new accounts for token transfers
        Given I successfully onboard a new token account
        When I associate a new recipient account with token
        And I transfer <amount> tokens to recipient
        Examples:
            | amount |
            | 250000 |

    @FundAccount
    Scenario Outline: Fund account
        When I send <amount> ℏ to account <account>
        Then the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount | account |
            | 1000   | 1043    |
