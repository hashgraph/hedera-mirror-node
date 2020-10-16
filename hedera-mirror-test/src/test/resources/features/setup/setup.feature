@Setup
Feature: Setup entities for various features

    @SetupTokenAccounts
    Scenario Outline: Setup 2 new accounts for token transfers
        Given I successfully create a new token <symbol>
        When I associate a new account with token
        And I transfer <amount> tokens to recipient
        Then I associate a new account with token
        Then I transfer <amount> tokens to recipient
        Examples:
            | symbol  | amount |
            | "NECTK" | 100000 |

    @FundAccount
    Scenario Outline: Fund account
        When I send <amount> tℏ to account <account>
        Then the new balance should reflect cryptotransfer of <amount>
        Examples:
            | amount     | account |
            | 1000000000 | 1562    |
