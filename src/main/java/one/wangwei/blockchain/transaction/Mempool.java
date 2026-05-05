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

    /**
     * 添加交易到交易池
     *
     * 加入前会检查该交易是否和交易池中已有交易使用了同一个UTXO。
     * 如果出现相同的 txId:index，说明存在双花风险，直接拒绝加入。
     *
     * @param tx
     */
    public static void addTransaction(Transaction tx) {
        Transaction[] transactions = getTransactions();

        String conflictInput = findConflictInput(tx, transactions);
        if (conflictInput != null) {
            throw new RuntimeException(
                    "ERROR: Double spending detected in mempool ! Conflict UTXO=" + conflictInput
            );
        }

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

    /**
     * 查找新交易是否与交易池中的已有交易发生输入冲突
     *
     * @param newTx      新交易
     * @param pendingTxs 交易池中已有交易
     * @return 如果存在冲突，返回冲突UTXO；否则返回null
     */
    public static String findConflictInput(Transaction newTx, Transaction[] pendingTxs) {
        if (newTx == null || newTx.isCoinbase()) {
            return null;
        }
        if (pendingTxs == null || pendingTxs.length == 0) {
            return null;
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
                        return newInputKey;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 将交易输入转成 txId:index 的形式
     *
     * 例如：
     * 20d5be1e23d261c96b6084b8e39c4e884c6d6287dceef1d25e1e8e237e5c2ecb:0
     *
     * @param input
     * @return
     */
    public static String inputKey(TXInput input) {
        if (input == null || input.getTxId() == null || input.getTxId().length == 0) {
            return "";
        }

        return Hex.encodeHexString(input.getTxId()) + ":" + input.getTxOutputIndex();
    }
}
