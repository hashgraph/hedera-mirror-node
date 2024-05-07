delete from file_data where consensus_timestamp=0 and entity_id=102;
delete from address_book where start_consensus_timestamp=1;
delete from address_book_entry where consensus_timestamp=1;