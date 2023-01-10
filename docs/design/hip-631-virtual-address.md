# HIP-631 Virtual Addresses Design

## Purpose

[HIP-631](https://hips.hedera.com/hip/hip-631) describes new mechanisms to support "EVM equivalency" (allowing Hedera
accounts to better interoperate with Ethereum Smart contracts than the existing "EVM compatibilty" (that may be difficult
for users creating a new Hedera account through a Ethereum Smart Contract mechanism)) by allowing a Hedera Account to have
multiple "virtual addresses" over just a single alias.  A Hedera account will have the ability to add, disable, or delete
virtual addresses (up to a maximum number of virtual addresses per account[*]), where any virtual address can be associated with
only one given Hedera account at a time.  When a virtual address is disabled, it is still locked to the Hedera Account it had been
active with.  It is not until that virtual address is deleted than it can be added to another Hedera account.

Note: The HIP proposes incremental capacity limits as 1, then 3, then 10, then possibly an umbounded number.  For now, the Mirror Node
implementation will initially support a limit of 10 virtual addresses.  If the maximum number of virtual addreses is to grow beyond that limit,
a seperate HIP will be proposed (and additional API endpoints to allow pagination through the additional virtual addresses will be included in
that newer HIP).

HIP 631 is very large and extensive -- it proposes changes to HAPI, the Hedera SDK, the Mirror Node, wallets,
and more.  The purpose of this design doc is to isolate the Mirror Node requirements from the overall HIP document.

## Goals

* Enhance the database schema to store an account's list of virtual addresses.
* Store the historical state of an account's virtual address list.
* Enhance the REST API to show an account's active virtual addresses list (only a list of ACTIVE virtual addresses).
* Enhance the REST API to show an account's virtual addresses list (including status (ACTIVE | DISABLED) for each virtual address.

## Non-Goals

* Enhance gRPC APIs with virtual address information
* Enhance Web3 APIs with virtual address information

## Architecture

### Database

#### Virtual Address Status

```sql
  create type virtual_address_status as enum ('ACTIVE', 'DISABLED');
```

While the HIP-631 document lists a third status, 'DELETED', this value would not be present in the Mirror Node database.  Instead, we would just
delete the row (the entry for a deleted virtual address) from the table holding them all.

#### Virtual Addresses

```sql
  create table if not exists entity_virtual_addresses (
     account_id          entity_id not null,
     evm_address         bytea null,
     status              virtual_address_status not null default 'ACTIVE',
     timestamp_range     int8range not null,
     primary key (account_id, evm_address)
 );

  create table if not exists entity_virtual_addresses_history (
  like entity_virtual_addresses including defaults,
     primary key (account_id, evm_address, timestamp_range)
 );
```

While the HIP-631 document calls this table `account_virtual_address`, since we will use it to hold virtual addresses for both accounts and contracts,
this table name is more in line with existing mirror node naming.

#### Existing entity table is unchanged.

We are choosing to use the existing `evm_address` field of the `entity` table to hold the default virtual address, rather than adding a new column for this purpose.

### Importer

#### CryptoUpdate Parsing

When parsing CryptoUpdate transactions,

* Heed new `CryptoUpdate` operations (`addVirtualAddress`, `disableVirtualAddress`, `removeVirtualAddress`, `setDefaultVirtualAddress`).
    - addVirtualAddress: allow if already on list or if too many already on list (just give warning).  Replace default if `setDefaultVirtualAddress(true)` specified.
    - disableVirtualAddress: allow if not on list (just give warning).  Do not allow if primary virtual address is getting disabled and other non-primary default addresses present.
    - removeVirtualAddress: Do not allow if primary virtual address is getting removed, and other non-primary default addresses present.

### REST API

#### getAccountByIdOrAliasOrEvmAddress

`api/v1/accounts/{idOrAliasOrEvmAddress}` changes:

```json
  {
    ...,
  
    hedera_address: '0x0...1', // evm_address (default virtual address) in `long-zero` format
    virtual_addresses: [
      {
        evm_address: '0x2...3',
        is_default: false
      },
      {
        evm_address: '0x4...5',
        is_default: true
      }
    ]
  }
```

In addition, add an additional parameter to that API address call, `includeDisabledVirtualAddresses`, defaulting to `false`.  If set to `true`,
also include the list of disabled virtual addresses in that array, and include `is_disabled: true | false` to each tuple within the `virtual_addresses` array.

Optional: Only include up to ten virtual addresses in the output, and log a warning if more than ten virtual addresses are found for the given id / alias / evm address.

## Non-Functional Requirements

* Not (currently) adding `api/v1/accounts/{idOrAliasOrEvmAddress}/virtualAddresses endpoint (for paging through a large number of virtual addresses), but reserve that endpoint
in case a future extension to this HIP decides to implement that.

## Open Questions

1) How far in advance will we know about needing to support very large (e.g. ore than 10) virtual addresses for one account? (That is, to implement the
`api/v1/accounts/{idOrAliasOrEvmAddress}/virtualAddresses` endpoint.)

## Answered Questions

1) Are the CryptoUpdate migrations detailed in the https://hips.hedera.com/hip/hip-631#alias-to-virtual-account-migration section of the HIP-631 document
   external "batched" cryptoupdates?

   yes, I think that we intend to externalize those CryptoUpdate transactions. I am not sure about the "batch" part, is this the way we currently externalize similar transactions?

2) What happens if we attempt to delete or disable the current default virtual address?  Does a different virtual address takeover as default?
   Which one and how does the mirror node know which?

   - Deleting/disabling of virtual addresses that are not set as the default one should be straightforward.

   - If we have only one virtual address (it will be default as well) then it may be possible to delete/disable it.

   - In general it *may not be possible to delete/disable* a virtual address that is the default one *unless before that* we update the default virtual address to be another one.
