-------------------
-- Support HIP-573 custom fee enhancement: Blanket exemptions for custom fee collectors
-------------------

alter table if exists custom_fee
    add column all_collectors_are_exempt boolean not null default false;

-- set net_of_transfers of historical fractional fees to false
update custom_fee set all_collectors_are_exempt = false;
