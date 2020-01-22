@Accounts
Feature: Account Coverage Feature

    Scenario Outline: Validate account balance check scenario
        Given I provided an account string of <accountId>
        When I request balance info for this account
        Then the result should be grater than or equal to <threshold>
        Examples:
            | accountId | threshold |
            | "0.0.1"   | 0         |
            | "0.0.2"   | 1000000   |
