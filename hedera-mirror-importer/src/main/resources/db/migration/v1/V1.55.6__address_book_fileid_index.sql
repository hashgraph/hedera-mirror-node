-- fill the missing evm address due to the value not merged into the partial contract domain object in importer


create index if not exists address_book__fileid on address_book (file_id);
