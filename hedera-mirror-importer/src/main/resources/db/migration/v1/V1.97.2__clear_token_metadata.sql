-- clear token metadata and metadata_key accidentally set prior to services 0.49.0
with last_hapi_version as (
  select coalesce((
    select (hapi_version_major * 2^32)::bigint + hapi_version_minor
    from record_file
    order by consensus_end desc
    limit 1
  ), 0::bigint) as version
), lower_bound as (
  -- The exclusive lower bound when last version >= 0.47. It's not 0.48 because services release 0.48 reports HAPI
  -- version 0.47 in record files. The value is either the last consensus timestamp of services release 0.46 or 1ns
  -- before the first consensus timestamp in case the min version in record_file table is > 0.46
  select coalesce((
    select consensus_end
    from record_file
    where hapi_version_major = 0 and hapi_version_minor = 46
    order by consensus_end desc
    limit 1), (
    select consensus_start - 1
    from record_file
    order by consensus_end
    limit 1
  )) as timestamp
), upper_bound as (
  -- exclusive upper bound when last version >= 47. The value is either 1ns after the last 0.47 record file
  -- or the first consensus timestamp of the first record file. Note the fallback of the first consensus timestamp
  -- of the first record file effectively makes the range empty, since the exclusive upper bound will be either
  -- before the exclusive lower bound or equal to 1ns after the exclusive lower bound.
  select coalesce((
    select consensus_end + 1
    from record_file
    where hapi_version_major = 0 and hapi_version_minor = 47
    order by consensus_end desc
    limit 1), (
    select consensus_start
    from record_file
    order by consensus_end
    limit 1
  )) as timestamp
), timestamp_info as (
  select case when version < 47 then 'empty'::int8range
              else int8range((select timestamp from lower_bound), (select timestamp from upper_bound), '()')
         end as affected_range
  from last_hapi_version
), clear_token_history as (
  update token_history
  set metadata = null,
      metadata_key = null
  from timestamp_info
  -- the two ranges overlap and the token timestamp range does not extend to the left of the affected range, i.e.
  -- the lower bound of the token timestamp range (when the token was created / updated), is in affected_range.
  -- this is faster since it can utilize the gist index on timestamp_range
  where timestamp_range && affected_range and timestamp_range &> affected_range
)
update token
set metadata = null,
    metadata_key = null
from timestamp_info
where affected_range @> lower(timestamp_range);
