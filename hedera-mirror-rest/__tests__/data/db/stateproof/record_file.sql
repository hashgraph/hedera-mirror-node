--
-- PostgreSQL database dump
--

-- Dumped from database version 9.6.18
-- Dumped by pg_dump version 12.3

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
-- SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: record_file; Type: TABLE DATA; Schema: public; Owner: mirror_node
--

INSERT INTO public.record_file (id, name, load_start, load_end, file_hash, prev_hash, consensus_start, consensus_end) VALUES
	(1, '2019-10-11T13_32_41.443132Z.rcd', 1596476690, 1596476690, '495dc50d6323d4d5263d34dbefe7c20a0657a2fa5fe0d8c011a01f4a27757a259987dad210fb9596bc5a8cc7fbdbba33', '000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000', 1570800761443132000, 1570800761443132000),
	(2, '2019-10-11T13_33_22.783669054Z.rcd', 1596476690, 1596476690, '4cc4903c679553400ffc167129d5da4db64957e9ade5f69ee5a365c85e768cb94e9974cc493f079746514c8aa3db7e4a', '495dc50d6323d4d5263d34dbefe7c20a0657a2fa5fe0d8c011a01f4a27757a259987dad210fb9596bc5a8cc7fbdbba33', 1570800802783669054, 1570800804212804002),
	(3, '2019-10-11T13_33_25.526889Z.rcd', 1596476690, 1596476691, 'ccf9e7decb917b809782183a32888df56c93c48aa9d0cbcac93d47d0e93a39ed346746f67d73042e7f518a41ca9bc58c', '4cc4903c679553400ffc167129d5da4db64957e9ade5f69ee5a365c85e768cb94e9974cc493f079746514c8aa3db7e4a', 1570800805526889000, 1570800809699397001);


--
-- PostgreSQL database dump complete
--

