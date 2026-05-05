package one.wangwei.blockchain.block;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import one.wangwei.blockchain.pow.PowResult;
import one.wangwei.blockchain.pow.ProofOfWork;
import one.wangwei.blockchain.transaction.MerkleTree;
import one.wangwei.blockchain.transaction.Transaction;
import one.wangwei.blockchain.util.ByteUtils;
import org.apache.commons.codec.binary.Hex;

import java.time.Instant;

/**
 * 区块
 *
 * @author wangwei
 * @date 2018/02/02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Block {

    /**
     * 区块hash值
     */
    private String hash;

    /**
     * 前一个区块的hash值
     */
    private String prevBlockHash;

    /**
     * 交易信息
     */
    private Transaction[] transactions;

    /**
     * 区块创建时间(单位:秒)
     */
    private long timeStamp;

    /**
     * 工作量证明计数器
     */
    private long nonce;

    /**
     * 创建创世区块
     *
     * @param coinbase
     * @return
     */
    public static Block newGenesisBlock(Transaction coinbase) {
        return Block.newBlock(ByteUtils.ZERO_HASH, new Transaction[]{coinbase});
    }

    /**
     * 创建新区块
     *
     * @param previousHash
     * @param transactions
     * @return
     */
    public static Block newBlock(String previousHash, Transaction[] transactions) {
        Block block = new Block("", previousHash, transactions, Instant.now().getEpochSecond(), 0);

        ProofOfWork pow = ProofOfWork.newProofOfWork(block);
        PowResult powResult = pow.run();

        block.setHash(powResult.getHash());
        block.setNonce(powResult.getNonce());

        return block;
    }

    /**
     * 对区块中的交易信息进行Hash计算
     *
     * 实际返回的是Merkle Root
     *
     * @return
     */
    public byte[] hashTransaction() {
        if (this.getTransactions() == null || this.getTransactions().length == 0) {
            return new byte[]{};
        }

        byte[][] txIdArrays = new byte[this.getTransactions().length][];

        for (int i = 0; i < this.getTransactions().length; i++) {
            txIdArrays[i] = this.getTransactions()[i].hash();
        }

        return new MerkleTree(txIdArrays).getRoot().getHash();
    }

    /**
     * 新增：获取Merkle Root
     *
     * @return
     */
    public byte[] getMerkleRoot() {
        return this.hashTransaction();
    }

    /**
     * 新增：获取Merkle Root十六进制字符串
     *
     * @return
     */
    public String getMerkleRootHex() {
        return Hex.encodeHexString(this.getMerkleRoot());
    }
}
