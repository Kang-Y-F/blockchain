package one.wangwei.blockchain.block;

import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import one.wangwei.blockchain.store.RocksDBUtils;
import one.wangwei.blockchain.transaction.TXInput;
import one.wangwei.blockchain.transaction.TXOutput;
import one.wangwei.blockchain.transaction.Transaction;
import one.wangwei.blockchain.util.ByteUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;

import java.util.Arrays;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class Blockchain {

    private String lastBlockHash;

    public static Blockchain initBlockchainFromDB() {
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (StringUtils.isBlank(lastBlockHash)) {
            throw new RuntimeException("ERROR: Fail to init blockchain from db. ");
        }
        return new Blockchain(lastBlockHash);
    }

    public static Blockchain createBlockchain(String address) {
        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (StringUtils.isBlank(lastBlockHash)) {
            String genesisCoinbaseData = "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks";
            Transaction coinbaseTX = Transaction.newCoinbaseTX(address, genesisCoinbaseData);
            Block genesisBlock = Block.newGenesisBlock(coinbaseTX);
            lastBlockHash = genesisBlock.getHash();
            RocksDBUtils.getInstance().putBlock(genesisBlock);
            RocksDBUtils.getInstance().putLastBlockHash(lastBlockHash);
        }
        return new Blockchain(lastBlockHash);
    }

    /**
     * 正常挖矿：新区块接在当前主链末端
     *
     * @param transactions
     * @return
     */
    public Block mineBlock(Transaction[] transactions) {
        for (Transaction tx : transactions) {
            if (!this.verifyTransactions(tx)) {
                log.error("ERROR: Fail to mine block ! Invalid transaction ! tx=" + tx.toString());
                throw new RuntimeException("ERROR: Fail to mine block ! Invalid transaction ! ");
            }
        }

        String lastBlockHash = RocksDBUtils.getInstance().getLastBlockHash();
        if (StringUtils.isBlank(lastBlockHash)) {
            throw new RuntimeException("ERROR: Fail to get last block hash ! ");
        }

        Block block = Block.newBlock(lastBlockHash, transactions);
        this.addBlock(block);
        return block;
    }

    /**
     * 创新点：在指定父区块后挖新区块
     *
     * 该方法用于分叉模拟。
     * updateMainTip=false 时，只保存区块，但不更新主链末端；
     * updateMainTip=true 时，保存区块并将其设置为主链末端。
     *
     * @param previousHash  父区块Hash
     * @param transactions  交易数组
     * @param updateMainTip 是否更新主链tip
     * @return
     */
    public Block mineBlockOn(String previousHash, Transaction[] transactions, boolean updateMainTip) {
        if (StringUtils.isBlank(previousHash)) {
            throw new RuntimeException("ERROR: previousHash is blank !");
        }
        Block previousBlock = RocksDBUtils.getInstance().getBlock(previousHash);
        if (previousBlock == null) {
            throw new RuntimeException("ERROR: previous block not found ! previousHash=" + previousHash);
        }
        for (Transaction tx : transactions) {
            if (!this.verifyTransactions(tx)) {
                log.error("ERROR: Fail to mine fork block ! Invalid transaction ! tx=" + tx.toString());
                throw new RuntimeException("ERROR: Fail to mine fork block ! Invalid transaction ! ");
            }
        }
        Block block = Block.newBlock(previousHash, transactions);
        RocksDBUtils.getInstance().putBlock(block);

        if (updateMainTip) {
            RocksDBUtils.getInstance().putLastBlockHash(block.getHash());
            this.lastBlockHash = block.getHash();
        }
        return block;
    }

    /**
     * 添加区块到主链
     *
     * @param block
     */
    private void addBlock(Block block) {
        RocksDBUtils.getInstance().putLastBlockHash(block.getHash());
        RocksDBUtils.getInstance().putBlock(block);
        this.lastBlockHash = block.getHash();
    }

    /**
     * 创新点：切换主链末端
     *
     * 最长链规则本质上是更新 lastBlockHash，
     * 让系统从新的 tip 开始向前遍历主链。
     *
     * @param blockHash
     */
    public void switchToBlock(String blockHash) {
        if (StringUtils.isBlank(blockHash)) {
            throw new RuntimeException("ERROR: blockHash is blank !");
        }

        Block block = RocksDBUtils.getInstance().getBlock(blockHash);
        if (block == null) {
            throw new RuntimeException("ERROR: target block not found ! blockHash=" + blockHash);
        }

        RocksDBUtils.getInstance().putLastBlockHash(blockHash);
        this.lastBlockHash = blockHash;
    }

    /**
     * 创新点：计算某个区块所在分支的高度
     *
     * 高度计算方式：
     * 从目标区块开始不断沿 prevBlockHash 回溯到创世区块。
     *
     * @param blockHash
     * @return
     */
    public int getHeight(String blockHash) {
        if (StringUtils.isBlank(blockHash)) {
            return 0;
        }
        int height = 0;
        String currentHash = blockHash;
        while (StringUtils.isNotBlank(currentHash)) {
            Block block = RocksDBUtils.getInstance().getBlock(currentHash);
            if (block == null) {
                break;
            }
            height++;
            String prevHash = block.getPrevBlockHash();
            if (StringUtils.isBlank(prevHash) || ByteUtils.ZERO_HASH.equals(prevHash)) {
                break;
            }
            currentHash = prevHash;
        }
        return height;
    }

    /**
     * 根据Hash获取区块
     *
     * @param blockHash
     * @return
     */
    public Block getBlockByHash(String blockHash) {
        if (StringUtils.isBlank(blockHash)) {
            return null;
        }
        return RocksDBUtils.getInstance().getBlock(blockHash);
    }

    /**
     * 区块链迭代器
     */
    public class BlockchainIterator {

        private String currentBlockHash;

        private BlockchainIterator(String currentBlockHash) {
            this.currentBlockHash = currentBlockHash;
        }

        public boolean hashNext() {
            if (StringUtils.isBlank(currentBlockHash)) {
                return false;
            }
            return RocksDBUtils.getInstance().getBlock(currentBlockHash) != null;
        }

        public Block next() {
            Block currentBlock = RocksDBUtils.getInstance().getBlock(currentBlockHash);
            if (currentBlock == null) {
                return null;
            }

            String prevBlockHash = currentBlock.getPrevBlockHash();
            if (StringUtils.isBlank(prevBlockHash) || ByteUtils.ZERO_HASH.equals(prevBlockHash)) {
                this.currentBlockHash = null;
            } else {
                this.currentBlockHash = prevBlockHash;
            }

            return currentBlock;
        }
    }

    public BlockchainIterator getBlockchainIterator() {
        return new BlockchainIterator(lastBlockHash);
    }

    public Map<String, TXOutput[]> findAllUTXOs() {
        Map<String, int[]> allSpentTXOs = this.getAllSpentTXOs();
        Map<String, TXOutput[]> allUTXOs = Maps.newHashMap();

        BlockchainIterator iterator = this.getBlockchainIterator();

        while (iterator.hashNext()) {
            Block block = iterator.next();
            if (block == null) {
                break;
            }

            for (Transaction transaction : block.getTransactions()) {
                String txId = Hex.encodeHexString(transaction.getTxId());
                int[] spentOutIndexArray = allSpentTXOs.get(txId);
                TXOutput[] txOutputs = transaction.getOutputs();

                for (int outIndex = 0; outIndex < txOutputs.length; outIndex++) {
                    if (spentOutIndexArray != null && ArrayUtils.contains(spentOutIndexArray, outIndex)) {
                        continue;
                    }

                    TXOutput[] UTXOArray = allUTXOs.get(txId);
                    if (UTXOArray == null) {
                        UTXOArray = new TXOutput[]{txOutputs[outIndex]};
                    } else {
                        UTXOArray = ArrayUtils.add(UTXOArray, txOutputs[outIndex]);
                    }

                    allUTXOs.put(txId, UTXOArray);
                }
            }
        }

        return allUTXOs;
    }

    private Map<String, int[]> getAllSpentTXOs() {
        Map<String, int[]> spentTXOs = Maps.newHashMap();

        BlockchainIterator iterator = this.getBlockchainIterator();

        while (iterator.hashNext()) {
            Block block = iterator.next();
            if (block == null) {
                break;
            }

            for (Transaction transaction : block.getTransactions()) {
                if (transaction.isCoinbase()) {
                    continue;
                }

                for (TXInput txInput : transaction.getInputs()) {
                    String inTxId = Hex.encodeHexString(txInput.getTxId());
                    int[] spentOutIndexArray = spentTXOs.get(inTxId);

                    if (spentOutIndexArray == null) {
                        spentOutIndexArray = new int[]{txInput.getTxOutputIndex()};
                    } else {
                        spentOutIndexArray = ArrayUtils.add(spentOutIndexArray, txInput.getTxOutputIndex());
                    }

                    spentTXOs.put(inTxId, spentOutIndexArray);
                }
            }
        }

        return spentTXOs;
    }

    private Transaction findTransaction(byte[] txId) {
        BlockchainIterator iterator = this.getBlockchainIterator();

        while (iterator.hashNext()) {
            Block block = iterator.next();
            if (block == null) {
                break;
            }

            for (Transaction tx : block.getTransactions()) {
                if (Arrays.equals(tx.getTxId(), txId)) {
                    return tx;
                }
            }
        }

        throw new RuntimeException("ERROR: Can not found tx by txId ! ");
    }

    public void signTransaction(Transaction tx, BCECPrivateKey privateKey) throws Exception {
        Map<String, Transaction> prevTxMap = Maps.newHashMap();

        for (TXInput txInput : tx.getInputs()) {
            Transaction prevTx = this.findTransaction(txInput.getTxId());
            prevTxMap.put(Hex.encodeHexString(txInput.getTxId()), prevTx);
        }

        tx.sign(privateKey, prevTxMap);
    }

    private boolean verifyTransactions(Transaction tx) {
        if (tx.isCoinbase()) {
            return true;
        }

        Map<String, Transaction> prevTx = Maps.newHashMap();

        for (TXInput txInput : tx.getInputs()) {
            Transaction transaction = this.findTransaction(txInput.getTxId());
            prevTx.put(Hex.encodeHexString(txInput.getTxId()), transaction);
        }

        try {
            return tx.verify(prevTx);
        } catch (Exception e) {
            log.error("Fail to verify transaction ! transaction invalid ! ", e);
            throw new RuntimeException("Fail to verify transaction ! transaction invalid ! ", e);
        }
    }
}
