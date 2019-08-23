package com.hedera.mirrorservice;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

@Log4j2
public class CryptoServiceMirror extends CryptoServiceGrpc.CryptoServiceImplBase {

	/**
	 * The mirror node provides the same service interface as Hedera node to clients;
	 * Clients can build a channel to the mirror node and call service methods remotely;
	 * The mirror node accepts Transactions, extract the nodeAccountID in each Transaction, build a channel to that Hedera node, call its service methods, get the TransactionResponse, and return to clients.
	 * @param request
	 * @param responseObserver
	 * @param methodName
	 */
	static void rpc_CryptoService(Transaction request,
			StreamObserver<TransactionResponse> responseObserver,
			String methodName) {
		try {
			AccountID nodeAccountID = ServiceAgent.extractNodeAccountID(request);
			Pair<CryptoServiceGrpc.CryptoServiceBlockingStub, ManagedChannel> pair = ServiceAgent.getCryptoServiceStub(nodeAccountID);
			ServiceAgent.rpcHelper_Tx(pair.getLeft(), pair.getRight(),
					request, responseObserver, methodName);
		} catch (InvalidProtocolBufferException ex) {
			log.error("Error parsing transaction body", ex);
		}
	}

	/**
	 * The mirror node provides the same service interface as Hedera node to clients;
	 * Clients can build a channel to the mirror node and call service methods remotely;
	 * The mirror node accepts Queries, extract the AccountID in payment in the QueryHeader of each Query, build a channel to that Hedera node, call its service methods, get the Response, and return to clients.
	 * If the Query doesn't contain any payment, we send it to the default node
	 * @param request
	 * @param responseObserver
	 * @param methodName
	 */
	static void rpc_CryptoService(Query request,
			StreamObserver<Response> responseObserver,
			String methodName) {
		AccountID accountID = ServiceAgent.extractNodeAccountID(request);
		if (accountID == null) {
			log.error("Missing nodeAccountID, Query = {}", request);
			return;
		}
		Pair<CryptoServiceGrpc.CryptoServiceBlockingStub, ManagedChannel> pair = ServiceAgent.getCryptoServiceStub(accountID);
		ServiceAgent.rpcHelper_Query(pair.getLeft(), pair.getRight(),
				request, responseObserver, methodName);
	}

	/**
	 * <pre>
	 * Creates a new account by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void createAccount(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "createAccount");
	}

	/**
	 * <pre>
	 * Updates an account by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void updateAccount(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "updateAccount");
	}

	/**
	 * <pre>
	 * Initiates a transfer by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void cryptoTransfer(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "cryptoTransfer");
	}

	/**
	 * <pre>
	 * Deletes and account by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void cryptoDelete(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "cryptoDelete");
	}

	/**
	 * <pre>
	 * Adds a claim by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void addClaim(Transaction request, StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "addClaim");
	}

	/**
	 * <pre>
	 * Deletes a claim by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	@Override
	public void deleteClaim(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_CryptoService(request, responseObserver, "deleteClaim");
	}

	/**
	 * <pre>
	 * Retrieves the record(fetch by AccountID ID) for an account by submitting the query.
	 * </pre>
	 */
	@Override
	public void getAccountRecords(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getAccountRecords");
	}

	/**
	 * <pre>
	 * Retrieves the balance for an account by submitting the query.
	 * </pre>
	 */
	@Override
	public void cryptoGetBalance(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "cryptoGetBalance");
	}

	/**
	 * <pre>
	 * Retrieves the account information for an account by submitting the query.
	 * </pre>
	 */
	@Override
	public void getAccountInfo(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getAccountInfo");
	}

	/**
	 * <pre>
	 * Retrieves the transaction receipts for an account by TxId which last for 180sec only for no fee.
	 * </pre>
	 */
	@Override
	public void getTransactionReceipts(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getTransactionReceipts");
	}

	/**
	 * <pre>
	 * Retrieves the transaction record by TxID which last for 180sec only for no fee.
	 * </pre>
	 */
	@Override
	public void getFastTransactionRecord(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getFastTransactionRecord");
	}

	/**
	 * <pre>
	 * Retrieves the transactions record(fetch by Transaction ID) for an account by submitting the query.
	 * </pre>
	 */
	@Override
	public void getTxRecordByTxID(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getTxRecordByTxID");
	}

	/**
	 * <pre>
	 * Retrieves the stakers for a node by account ID by submitting the query.
	 * </pre>
	 */
	@Override
	public void getStakersByAccountID(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getStakersByAccountID");
	}

	/**
	 * <pre>
	 * Retrieves the claim for an account by submitting the query.
	 * </pre>
	 */
	@Override
	public void getClaim(Query request, StreamObserver<Response> responseObserver) {
		rpc_CryptoService(request, responseObserver, "getClaim");
	}
}
