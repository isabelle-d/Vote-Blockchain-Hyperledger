/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.Hash;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;

public final class App {
	private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
	private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
	private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

	// Path to crypto materials.
	private static final Path CRYPTO_PATH = Paths.get("../../test-network/organizations/peerOrganizations/org1.example.com");
	// Path to user certificate.
	private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
	// Path to user private key directory.
	private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
	// Path to peer tls certificate.
	private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

	// Gateway peer end point.
	private static final String PEER_ENDPOINT = "localhost:7051";
	private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

	private final Contract contract;
	private final String voteId = "Vote" + Instant.now().toEpochMilli();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static void main(final String[] args) throws Exception {
		// The gRPC client connection should be shared by all Gateway connections to
		// this endpoint.
		var channel = newGrpcConnection();

		var builder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .hash(Hash.SHA256)
                .connection(channel)
				// Default timeouts for different gRPC calls
				.evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
				.submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

		try (var gateway = builder.connect()) {
			new App(gateway).run();
		} finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private static ManagedChannel newGrpcConnection() throws IOException {
		var credentials = TlsChannelCredentials.newBuilder()
				.trustManager(TLS_CERT_PATH.toFile())
				.build();
		return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
				.overrideAuthority(OVERRIDE_AUTH)
				.build();
	}

	private static Identity newIdentity() throws IOException, CertificateException {
		try (var certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
			var certificate = Identities.readX509Certificate(certReader);
			return new X509Identity(MSP_ID, certificate);
		}
	}

	private static Signer newSigner() throws IOException, InvalidKeyException {
		try (var keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
			var privateKey = Identities.readPrivateKey(keyReader);
			return Signers.newPrivateKeySigner(privateKey);
		}
	}

	private static Path getFirstFilePath(Path dirPath) throws IOException {
		try (var keyFiles = Files.list(dirPath)) {
			return keyFiles.findFirst().orElseThrow();
		}
	}

	public App(final Gateway gateway) {
		// Get a network instance representing the channel where the smart contract is
		// deployed.
		var network = gateway.getNetwork(CHANNEL_NAME);

		// Get the smart contract from the network.
		contract = network.getContract(CHAINCODE_NAME);
	}

	public void run() throws GatewayException, CommitException {
		contract.submitTransaction("DeleteAllData");
        try {
            contract.submitTransaction("InitLedger");
        } catch (Exception e) {}

        System.out.println("starting clean bulk injection...");
        long startTime = System.currentTimeMillis();

        try {
            // exactly one synchronous call so mvcc tally keys cannot lock or conflict
            contract.submitTransaction("BulkInjectVotes", "20000");

            System.out.println("injection successful");
        } catch (EndorseException e) {
			e.printStackTrace();

			for (var detail : e.getDetails()) {
				System.out.println(detail);
			}
		}

        long endTime = System.currentTimeMillis();
        System.out.println("total injection time: " + ((endTime - startTime) / 1000.0) + " seconds!");

        // --- calculate and print total votes across all tallies ---
        int totalVotesInLedger = 0;
        String[] allCandidates = {"15", "5", "blue-team", "red-team", "green-candidate"};
        for (String candidate : allCandidates) {
            try {
                byte[] tallyBytes = contract.evaluateTransaction("ReadTally", candidate);
                if (tallyBytes != null && tallyBytes.length > 0) {
                    totalVotesInLedger += Integer.parseInt(new String(tallyBytes));
                }
            } catch (Exception e) {
                // if a specific tally asset doesn't exist yet, skip it
            }
        }
        System.out.println("total votes currently on ledger: " + totalVotesInLedger);

        System.out.println("\n--- speed comparison ---");

        try {
			contract.submitTransaction("InitLedger");
		} catch (Exception e) {
			// ledger already populated
		}

		// 1. benchmark the fast method
		long sf = System.nanoTime();
		votesRun(); // remove InitLedger from inside this method now!
		long ef = System.nanoTime();
		double fastTime = (ef - sf) / 1000000.0;
		System.out.println(" " + fastTime);

		// 2. benchmark the slow method
		long ss = System.nanoTime();
		votesRun2();
		long es = System.nanoTime();
		double slowTime = (es - ss) / 1000000.0;
		System.out.println(" " + slowTime);

        double timesFaster = slowTime / fastTime;
        System.out.println("votesRun method was faster than votesRun2 method by " + String.format("%.2f", timesFaster) + " times");
    }

	/**
	 * This type of transaction would typically only be run once by an application
	 * the first time it was started after its initial deployment. A new version of
	 * the chaincode deployed later would likely not need to run an "init" function.
	 */
	private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: InitLedger, function creates the initial set of Votes on the ledger");

		contract.submitTransaction("InitLedger");

		System.out.println("*** Transaction committed successfully");
	}

	/**
	 * Evaluate a transaction to query ledger state.
	 */
	private void GetAllVotes() throws GatewayException {
		System.out.println("\n--> Evaluate Transaction: GetAllVotes, function returns all the current Votes on the ledger");

		var result = contract.evaluateTransaction("GetAllVotes");

		System.out.println("*** Result: " + prettyJson(result));
	}

	private String prettyJson(final byte[] json) {
		return prettyJson(new String(json, StandardCharsets.UTF_8));
	}

	private String prettyJson(final String json) {
		var parsedJson = JsonParser.parseString(json);
		return gson.toJson(parsedJson);
	}

	/**
	 * Submit a transaction synchronously, blocking until it has been committed to
	 * the ledger.
	 */
	private void createVote() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: CreateVote, creates new Vote with arguments");
		contract.submitTransaction("CreateVote", voteId, "voter", "voted", "2026-07-07 17:59:10.0");

		System.out.println("*** Transaction committed successfully");
	}



	private void readVoteById() throws GatewayException {
		System.out.println("\n--> Evaluate Transaction: ReadVote, function returns Vote attributes");

		var evaluateResult = contract.evaluateTransaction("ReadVote", voteId);

		System.out.println("*** Result:" + prettyJson(evaluateResult));
	}

	/**
	 * submitTransaction() will throw an error containing details of any error
	 * responses from the smart contract.
	 */
	private void updateNonExistentVote() {
		try {
			System.out.println("\n--> Submit Transaction: UpdateVote Vote70, Vote70 does not exist and should return an error");

			contract.submitTransaction("UpdateVote", "Vote70", "blue", "5", "Tomoko", "300");

			System.out.println("******** FAILED to return an error");
		} catch (EndorseException | SubmitException | CommitStatusException e) {
			System.out.println("*** Successfully caught the error:");
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
		} catch (CommitException e) {
			System.out.println("*** Successfully caught the error:");
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
			System.out.println("Status code: " + e.getCode());
		}
	}
	public void votesRun() throws GatewayException, CommitException {

        byte[] winnerBytes = contract.evaluateTransaction("GetWinner");
        System.out.println(new String(winnerBytes, StandardCharsets.UTF_8));
    }
	public void votesRun2() throws GatewayException {
        byte[] winnerBytes = contract.evaluateTransaction("GetWinnerSlow");
        System.out.println(new String(winnerBytes, StandardCharsets.UTF_8));
    }
}
