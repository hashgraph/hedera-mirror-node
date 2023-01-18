# HIP-631 Virtual Addresses Design

## Purpose

[HIP-631](https://hips.hedera.com/hip/hip-631) describes new mechanisms to support "EVM equivalency" (allowing Hedera
accounts to better interoperate with Ethereum Smart contracts than the existing "EVM compatibilty" by allowing a Hedera Account to have
multiple "virtual addresses" over just a single alias.  A Hedera account will have the ability to add, disable, or delete
virtual addresses where any virtual address can be associated with only one given Hedera account at a time.
 When a virtual address is disabled, it is still locked to the Hedera Account it had been actuve with.
It is not until that virtual address is deleted than it can be added to another Hedera account.


HIP 631 is very large and extensive -- it proposes changes to HAPI, the Hedera SDK, the Mirror Node, wallets,
and more.  The purpose of this design doc is to isolate the Mirror Node requirements from the overall HIP document.

## Goals

* Enhance the database schema to store an account's list of virtual addresses.
* Store the historical state of an account's virtual address list.
* Enhance the REST API to show an account's active virtual addresses list (only a list of `ACTIVE` virtual addresses).

## Non-Goals

* Enhance gRPC APIs with virtual address information
* Enhance Web3 APIs with virtual address information
* Enhance the REST API to show an account's `DISABLED` or `DELETED` virtual addresses.
* Any sort of behavior based on the number of active/disabled virtual addresses.  (See `maximum virtual address discussion in non-functional requirements section` of the HIP-631 document.)

## Architecture

### Database

#### Virtual Address Status

```sql
  create type virtual_address_status as enum ('ACTIVE', 'DELETED', 'DISABLED');
```

We will have an index that only holds the non-'DELETED' values, so skipping over the DELETED rows will be fast.

#### Virtual Addresses

```sql
  create table if not exists entity_virtual_address (
     entity_id           bigint not null,
     evm_address         bytea not null,
     status              virtual_address_status not null default 'ACTIVE',
     timestamp_range     int8range not null,
     primary key (entity_id, evm_address)
 );

  create table if not exists entity_virtual_address_history (
  like entity_virtual_address including defaults,
     primary key (entity_id, evm_address, timestamp_range)
 );

  create index if not exists entity_virtual_address__evm_address on entity_virtual_address (evm_address) where status <> 'DELETED';
```

While the HIP-631 document calls this table `account_virtual_address`, we will use it to hold virtual addresses for both accounts and contracts,
and so we are using "entity" in the table name.  The HIP-631 document should be updated to reflect this.

#### Migration of `entity.evm_address` data into new `entity_virtual_address` table

The existing mirror node database, supporting one evm_address per entity, stores it in a column named `evm_address` in the `entity` table.
With HIP-631, We now support multiple virtual address per entity, with one being labelled the default virtual address for that entity.
We will continue to use the `evm_address` column of the `entity` table, but now this value is used for the *default* virtual address.

* We will need to perform a database migration to:
    - create the new `entity_virtual_address` table as specified in the previous section.
    - for every existing `entity` with a non-null `evm_address` field, add a row to that table with corresponding `entity_id` and `evm_address` fields, `is_default` set to `true`.
    - We are not going to delete the `evm_address` column from the `entity` table at this time, but that may be a later migration.

### Importer

#### CryptoUpdate Parsing

When parsing CryptoUpdate transactions,

* Support new `CryptoUpdate` operations (`addVirtualAddress`, `disableVirtualAddress`, `removeVirtualAddress`, `setDefaultVirtualAddress`).
    - Add: The mirror node won't easily be able to see how many other virtual addresses already exist for that account, so we probably won't be able to issue a warning if too many virtual addresses are already assigned to that account.  In all cases, we should replace default the virtual address only if `setDefaultVirtualAddress(true)` is specified in the `CryptoUpdate`.
    - Disable: Allow if not on list (just give warning).  Log a warning if primary virtual address is getting disabled and other non-primary default addresses present.
    - Remove: Log a warning if primary virtual address is getting removed, and other non-primary default addresses present.

### REST API

#### getAccountByIdOrAliasOrEvmAddress

* `/api/v1/accounts/{idOrAliasOrEvmAddress}` changes:
    - start by checking the `entity_virtual_address` table for an `evm_address` matching the `{idOrAliasOrEvmAddress}` value; if so, the entity to look up is the `entity_id` of that row.
    - Add the following two fields to the output: (one string and one tuple array).  For consistency, we will output each virtual address with the label `virtual_address` (even though the column name in the database will be `evm_address`):

```json
  {
    "hedera_address": "0x0000000000000000000000000000000000001001",    // we calculate this field by expressing the account's entity_id shard.realm.num in "long-zero" format.  This is *not* a new column on the `entity` table.
    "virtual_addresses": [
      {
        "virtual_address": "0x2000000000000000000000000000000000000003",
        "is_default": false
      },
      {
        "virtual_address": "0x4000000000000000000000000000000000000005",
        "is_default": true
      }
    ]
  }
```
(existing fields are omitted for brevity)

#### getAccountByIdOrAliasOrEvmAddress Non-Gaols

* Adding `/api/v1/accounts/{idOrAliasOrEvmAddress}/virtualAddresses` endpoint (for paging through a large number of virtual addresses), but we will reserve that endpoint
in case a future extension to this HIP decides to implement that.

* Returning any of the disabled (or deleted) virtual addresses for an account (or contract).  The only ones we return are the `ACTIVE` ones, and which (of those) is the default virtual address.

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
