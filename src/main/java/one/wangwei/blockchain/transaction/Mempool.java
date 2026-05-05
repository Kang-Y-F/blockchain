package one.wangwei.blockchain.transaction;

import one.wangwei.blockchain.util.SerializeUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Mempool {

    private static final String MEMPOOL_FILE = "mempool.dat";

    public static Transaction[] getTransactions() {
        try {
            Path path = Paths.get(MEMPOOL_FILE);
            if (!Files.exists(path)) {
                return new Transaction[]{};
            }

            byte[] bytes = Files.readAllBytes(path);
            if (bytes == null || bytes.length == 0) {
                return new Transaction[]{};
            }

            Object obj = SerializeUtils.deserialize(bytes);
            if (obj == null) {
                return new Transaction[]{};
            }

            return (Transaction[]) obj;
        } catch (Exception e) {
            throw new RuntimeException("ERROR: Fail to load mempool !", e);
        }
    }

    public static void addTransaction(Transaction tx) {
        Transaction[] transactions = getTransactions();

        checkDoubleSpend(tx, transactions);

        transactions = ArrayUtils.add(transactions, tx);
        save(transactions);
    }

    public static boolean isEmpty() {
        return getTransactions().length == 0;
    }

    public static void clear() {
        try {
            Files.deleteIfExists(Paths.get(MEMPOOL_FILE));
        } catch (Exception e) {
            throw new RuntimeException("ERROR: Fail to clear mempool !", e);
        }
    }

    private static void save(Transaction[] transactions) {
        try {
            byte[] bytes = SerializeUtils.serialize(transactions);
            Files.write(Paths.get(MEMPOOL_FILE), bytes);
        } catch (Exception e) {
            throw new RuntimeException("ERROR: Fail to save mempool !", e);
        }
    }

    private static void checkDoubleSpend(Transaction newTx, Transaction[] pendingTxs) {
        if (newTx == null || newTx.isCoinbase()) {
            return;
        }

        for (TXInput newInput : newTx.getInputs()) {
            String newInputKey = inputKey(newInput);

            for (Transaction pendingTx : pendingTxs) {
                if (pendingTx == null || pendingTx.isCoinbase()) {
                    continue;
                }

                for (TXInput pendingInput : pendingTx.getInputs()) {
                    String pendingInputKey = inputKey(pendingInput);

                    if (newInputKey.equals(pendingInputKey)) {
                        throw new RuntimeException(
                                "ERROR: Double spending detected in mempool ! UTXO=" + newInputKey
                        );
                    }
                }
            }
        }
    }

    private static String inputKey(TXInput input) {
        if (input == null || input.getTxId() == null || input.getTxId().length == 0) {
            return "";
        }

        return Hex.encodeHexString(input.getTxId()) + ":" + input.getTxOutputIndex();
    }
}
