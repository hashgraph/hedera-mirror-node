package com.hedera.mirrorservice;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;

@Log4j2
public class SmartContractServiceMirror extends SmartContractServiceGrpc.SmartContractServiceImplBase {

	/**
	 * The mirror node provides the same service interface as Hedera node to clients;
	 * Clients can build a channel to the mirror node and call service methods remotely;
	 * The mirror node accepts Transactions, extract the nodeAccountID in each Transaction, build a channel to that Hedera node, call its service methods, get the TransactionResponse, and return to clients.
	 * @param request
	 * @param responseObserver
	 * @param methodName
	 */
	static void rpc_ContractService(Transaction request,
			StreamObserver<TransactionResponse> responseObserver,
			String methodName) {
		try {
			AccountID nodeAccountID = ServiceAgent.extractNodeAccountID(request);
			Pair<SmartContractServiceGrpc.SmartContractServiceBlockingStub, ManagedChannel> pair = ServiceAgent.getContractServiceStub(nodeAccountID);
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
	static void rpc_ContractService(Query request,
			StreamObserver<Response> responseObserver,
			String methodName) {
		AccountID accountID = ServiceAgent.extractNodeAccountID(request);
		if (accountID == null) {
			log.error("Missing nodeAccountID, Query = {}", request);
			return;
		}
		Pair<SmartContractServiceGrpc.SmartContractServiceBlockingStub, ManagedChannel> pair = ServiceAgent.getContractServiceStub(accountID);
		ServiceAgent.rpcHelper_Query(pair.getLeft(), pair.getRight(),
				request, responseObserver, methodName);
	}

	/**
	 * <pre>
	 * Creates a contract by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	public void createContract(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_ContractService(request, responseObserver, "createContract");
	}

	/**
	 * <pre>
	 * Updates a contract with the content by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	public void updateContract(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_ContractService(request, responseObserver, "updateContract");
	}

	/**
	 * <pre>
	 * Calls a contract by submitting the transaction. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	public void contractCallMethod(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_ContractService(request, responseObserver, "contractCallMethod");
	}

	/**
	 * <pre>
	 * Retrieves the contract information by submitting the query. The grpc server returns the Response
	 * </pre>
	 */
	public void getContractInfo(Query request,
			StreamObserver<Response> responseObserver) {
		rpc_ContractService(request, responseObserver, "getContractInfo");
	}

	/**
	 * <pre>
	 * Calls a smart contract by submitting the query. The grpc server returns the Response
	 * </pre>
	 */
	public void contractCallLocalMethod(Query request,
			StreamObserver<Response> responseObserver) {
		rpc_ContractService(request, responseObserver, "contractCallLocalMethod");
	}

	/**
	 * <pre>
	 * Retrieves the byte code of a contract by submitting the query. The grpc server returns the Response
	 * </pre>
	 */
	public void contractGetBytecode(Query request,
			StreamObserver<Response> responseObserver) {
		rpc_ContractService(request, responseObserver, "contractGetBytecode");
	}

	/**
	 * <pre>
	 * Retrieves a contract(using Solidity ID) by submitting the query. The grpc server returns the Response
	 * </pre>
	 */
	public void getBySolidityID(Query request,
			StreamObserver<Response> responseObserver) {
		rpc_ContractService(request, responseObserver, "getBySolidityID");
	}

	/**
	 * <pre>
	 * Retrieves a contract(using contract ID) by submitting the query. The grpc server returns the Response
	 * </pre>
	 */
	public void getTxRecordByContractID(Query request,
			StreamObserver<Response> responseObserver) {
		rpc_ContractService(request, responseObserver, "getTxRecordByContractID");
	}

	/**
	 * <pre>
	 *Delete a contract instance(mark as deleted until it expires), and transfer hbars to the specified account. The grpc server returns the TransactionResponse
	 * </pre>
	 */
	public void deleteContract(Transaction request,
			StreamObserver<TransactionResponse> responseObserver) {
		rpc_ContractService(request, responseObserver, "deleteContract");
	}
}
