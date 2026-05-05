package one.wangwei.blockchain.cli;

import lombok.extern.slf4j.Slf4j;
import one.wangwei.blockchain.block.Block;
import one.wangwei.blockchain.block.Blockchain;
import one.wangwei.blockchain.pow.ProofOfWork;
import one.wangwei.blockchain.store.RocksDBUtils;
import one.wangwei.blockchain.transaction.Mempool;
import one.wangwei.blockchain.transaction.TXInput;
import one.wangwei.blockchain.transaction.TXOutput;
import one.wangwei.blockchain.transaction.Transaction;
import one.wangwei.blockchain.transaction.UTXOSet;
import one.wangwei.blockchain.util.Base58Check;
import one.wangwei.blockchain.wallet.Wallet;
import one.wangwei.blockchain.wallet.WalletUtils;
import org.apache.commons.cli.*;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import one.wangwei.blockchain.transaction.MerkleTree;


import java.util.Arrays;
import java.util.Set;
import java.util.List;
/**
 * 命令行解析器
 *
 * @author wangwei
 * @date 2018/03/08
 */
@Slf4j
public class CLI {

    private String[] args;
    private Options options = new Options();

    public CLI(String[] args) {
        this.args = args;

        Option helpCmd = Option.builder("h").desc("show help").build();
        options.addOption(helpCmd);

        Option address = Option.builder("address").hasArg(true).desc("Source wallet address").build();
        Option sendFrom = Option.builder("from").hasArg(true).desc("Source wallet address").build();
        Option sendTo = Option.builder("to").hasArg(true).desc("Destination wallet address").build();
        Option sendAmount = Option.builder("amount").hasArg(true).desc("Amount to send").build();

        // 新增：矿工地址参数
        Option miner = Option.builder("miner").hasArg(true).desc("Miner wallet address").build();
        Option block = Option.builder("block").hasArg(true).desc("Block hash").build();
        Option txid = Option.builder("txid").hasArg(true).desc("Transaction id").build();
        Option miner1 = Option.builder("miner1").hasArg(true).desc("Fork branch 1 miner address").build();
        Option miner2 = Option.builder("miner2").hasArg(true).desc("Fork branch 2 miner address").build();

        options.addOption(address);
        options.addOption(sendFrom);
        options.addOption(sendTo);
        options.addOption(sendAmount);
        options.addOption(miner);
        options.addOption(block);
        options.addOption(txid);
        options.addOption(miner1);
        options.addOption(miner2);


    }

    /**
     * 命令行解析入口
     */
    public void parse() {
        this.validateArgs(args);
        try {
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            switch (args[0]) {
                case "createblockchain":
                    String createblockchainAddress = cmd.getOptionValue("address");
                    if (StringUtils.isBlank(createblockchainAddress)) {
                        help();
                    }
                    this.createBlockchain(createblockchainAddress);
                    break;

                case "getbalance":
                    String getBalanceAddress = cmd.getOptionValue("address");
                    if (StringUtils.isBlank(getBalanceAddress)) {
                        help();
                    }
                    this.getBalance(getBalanceAddress);
                    break;

                case "send":
                    String sendFrom = cmd.getOptionValue("from");
                    String sendTo = cmd.getOptionValue("to");
                    String sendAmount = cmd.getOptionValue("amount");

                    if (StringUtils.isBlank(sendFrom) ||
                            StringUtils.isBlank(sendTo) ||
                            !NumberUtils.isDigits(sendAmount)) {
                        help();
                    }

                    this.send(sendFrom, sendTo, Integer.valueOf(sendAmount));
                    break;

                // 新增：矿工手动挖矿命令
                case "mine":
                    String minerAddress = cmd.getOptionValue("miner");
                    if (StringUtils.isBlank(minerAddress)) {
                        help();
                    }
                    this.mine(minerAddress);
                    break;

                case "forktest":
                    String forkMiner1 = cmd.getOptionValue("miner1");
                    String forkMiner2 = cmd.getOptionValue("miner2");

                    if (StringUtils.isBlank(forkMiner1) || StringUtils.isBlank(forkMiner2)) {
                        help();
                    }

                    this.forkTest(forkMiner1, forkMiner2);
                    break;

                case "verifytx":
                    String blockHash = cmd.getOptionValue("block");
                    String txId = cmd.getOptionValue("txid");

                    if (StringUtils.isBlank(blockHash) || StringUtils.isBlank(txId)) {
                        help();
                    }

                    this.verifyTx(blockHash, txId);
                    break;


                // 新增：打印交易池
                case "printmempool":
                    this.printMempool();
                    break;

                // 新增：清空交易池
                case "clearmempool":
                    this.clearMempool();
                    break;

                case "createwallet":
                    this.createWallet();
                    break;

                case "printaddresses":
                    this.printAddresses();
                    break;

                case "printbalances":
                    this.printBalances();
                    break;

                case "printchain":
                    this.printChain();
                    break;

                case "h":
                    this.help();
                    break;

                default:
                    this.help();
            }
        } catch (Exception e) {
            log.error("Fail to parse cli command ! ", e);
        } finally {
            RocksDBUtils.getInstance().closeDB();
        }
    }

    /**
     * 验证入参
     *
     * @param args
     */
    private void validateArgs(String[] args) {
        if (args == null || args.length < 1) {
            help();
        }
    }

    /**
     * 创建区块链
     *
     * @param address
     */
    private void createBlockchain(String address) {
        Blockchain blockchain = Blockchain.createBlockchain(address);
        UTXOSet utxoSet = new UTXOSet(blockchain);
        utxoSet.reIndex();
        log.info("Done ! ");
    }

    /**
     * 创建钱包
     *
     * @throws Exception
     */
    private void createWallet() throws Exception {
        Wallet wallet = WalletUtils.getInstance().createWallet();
        log.info("wallet address : " + wallet.getAddress());
    }

    /**
     * 打印钱包地址
     */
    private void printAddresses() {
        Set<String> addresses = WalletUtils.getInstance().getAddresses();
        if (addresses == null || addresses.isEmpty()) {
            log.info("There isn't address");
            return;
        }

        for (String address : addresses) {
            log.info("Wallet address: " + address);
        }
    }

    /**
     * 打印所有本地钱包地址的余额
     */
    private void printBalances() {
        Set<String> addresses = WalletUtils.getInstance().getAddresses();

        if (addresses == null || addresses.isEmpty()) {
            log.info("There isn't address");
            return;
        }

        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        UTXOSet utxoSet = new UTXOSet(blockchain);

        System.out.println("==================================================");
        System.out.println("All Wallet Balances");
        System.out.println("==================================================");

        for (String address : addresses) {
            try {
                byte[] versionedPayload = Base58Check.base58ToBytes(address);
                byte[] pubKeyHash = Arrays.copyOfRange(versionedPayload, 1, versionedPayload.length);

                TXOutput[] txOutputs = utxoSet.findUTXOs(pubKeyHash);

                int balance = 0;
                if (txOutputs != null && txOutputs.length > 0) {
                    for (TXOutput txOutput : txOutputs) {
                        balance += txOutput.getValue();
                    }
                }

                System.out.println("Address: " + address);
                System.out.println("Balance: " + balance);
                System.out.println("--------------------------------------------------");

            } catch (Exception e) {
                System.out.println("Address: " + address);
                System.out.println("Balance: ERROR");
                System.out.println("Reason: invalid address or query failed");
                System.out.println("--------------------------------------------------");
            }
        }
    }

    /**
     * 查询钱包余额
     *
     * @param address 钱包地址
     */
    private void getBalance(String address) {
        // 检查钱包地址是否合法
        try {
            Base58Check.base58ToBytes(address);
        } catch (Exception e) {
            log.error("ERROR: invalid wallet address", e);
            throw new RuntimeException("ERROR: invalid wallet address", e);
        }

        // 得到公钥Hash值
        byte[] versionedPayload = Base58Check.base58ToBytes(address);
        byte[] pubKeyHash = Arrays.copyOfRange(versionedPayload, 1, versionedPayload.length);

        // 查询余额时不应该重新创建区块链，应该从数据库读取已有区块链
        Blockchain blockchain = Blockchain.initBlockchainFromDB();
        UTXOSet utxoSet = new UTXOSet(blockchain);

        TXOutput[] txOutputs = utxoSet.findUTXOs(pubKeyHash);

        int balance = 0;
        if (txOutputs != null && txOutputs.length > 0) {
            for (TXOutput txOutput : txOutputs) {
                balance += txOutput.getValue();
            }
        }

        log.info("Balance of '{}': {}\n", new Object[]{address, balance});
    }

    /**
     * 转账
     *
     * 创新点：
     * 原来 send 会立即创建交易、挖矿、生成新区块。
     * 现在 send 只创建交易并放入交易池，不再立即挖矿。
     *
     * @param from
     * @param to
     * @param amount
     * @throws Exception
     */
    private void send(String from, String to, int amount) throws Exception {
        // 检查发送方钱包地址是否合法
        try {
            Base58Check.base58ToBytes(from);
        } catch (Exception e) {
            log.error("ERROR: sender address invalid ! address=" + from, e);
            throw new RuntimeException("ERROR: sender address invalid ! address=" + from, e);
        }

        // 检查接收方钱包地址是否合法
        try {
            Base58Check.base58ToBytes(to);
        } catch (Exception e) {
            log.error("ERROR: receiver address invalid ! address=" + to, e);
            throw new RuntimeException("ERROR: receiver address invalid ! address=" + to, e);
        }

        if (amount < 1) {
            log.error("ERROR: amount invalid ! amount=" + amount);
            throw new RuntimeException("ERROR: amount invalid ! amount=" + amount);
        }

        // 从数据库恢复已有区块链
        Blockchain blockchain = Blockchain.initBlockchainFromDB();

        // 创建普通UTXO交易
        Transaction transaction = Transaction.newUTXOTransaction(from, to, amount, blockchain);

        // 加入交易池，不立即挖矿
        Mempool.addTransaction(transaction);

        log.info("Transaction added to mempool. txId={}", Hex.encodeHexString(transaction.getTxId()));
        log.info("This transaction is not on-chain yet.");
        log.info("Please run: mine -miner <MINER_ADDRESS> to pack pending transactions into a block.");
    }

    /**
     * 手动挖矿
     *
     * 创新点：
     * 矿工从交易池中取出交易进行打包，并通过Coinbase交易获得奖励。
     *
     * @param minerAddress 矿工地址
     * @throws Exception
     */
    private void mine(String minerAddress) throws Exception {
        // 检查矿工地址是否合法
        try {
            Base58Check.base58ToBytes(minerAddress);
        } catch (Exception e) {
            log.error("ERROR: miner address invalid ! address=" + minerAddress, e);
            throw new RuntimeException("ERROR: miner address invalid ! address=" + minerAddress, e);
        }

        // 读取交易池中的待打包交易
        Transaction[] pendingTxs = Mempool.getTransactions();

        if (pendingTxs == null || pendingTxs.length == 0) {
            log.info("Mempool is empty. No transactions to mine.");
            return;
        }

        Blockchain blockchain = Blockchain.initBlockchainFromDB();

        // 创建Coinbase奖励交易，奖励给矿工，而不是奖励给转账发起者
        Transaction rewardTx = Transaction.newCoinbaseTX(minerAddress, "");

        // 将交易池中的普通交易 + Coinbase奖励交易一起打包
        Transaction[] transactions = Arrays.copyOf(pendingTxs, pendingTxs.length + 1);
        transactions[pendingTxs.length] = rewardTx;

        // 挖矿生成新区块
        Block newBlock = blockchain.mineBlock(transactions);

        // 更新UTXO集合
        new UTXOSet(blockchain).update(newBlock);

        // 挖矿成功后清空交易池
        Mempool.clear();

        log.info("Mining success!");
        log.info("Packed normal transactions: {}", pendingTxs.length);
        log.info("Coinbase reward: 10");
        log.info("Reward to miner: {}", minerAddress);
        log.info("New block hash: {}", newBlock.getHash());
    }

    /**
     * 打印交易池
     */
    private void printMempool() {
        Transaction[] transactions = Mempool.getTransactions();

        log.info("Mempool transactions: {}", transactions.length);

        if (transactions.length == 0) {
            return;
        }

        for (int i = 0; i < transactions.length; i++) {
            Transaction tx = transactions[i];

            System.out.println("--------------------------------------------------");
            System.out.println("Pending TX #" + (i + 1));
            System.out.println("TX ID: " + Hex.encodeHexString(tx.getTxId()));
            System.out.println("Inputs: " + tx.getInputs().length);
            System.out.println("Outputs: " + tx.getOutputs().length);

            System.out.println("  Inputs:");
            for (int j = 0; j < tx.getInputs().length; j++) {
                TXInput input = tx.getInputs()[j];

                String inputTxId = input.getTxId() == null || input.getTxId().length == 0
                        ? ""
                        : Hex.encodeHexString(input.getTxId());

                System.out.println("    Input #" + j);
                System.out.println("      TxId: " + inputTxId);
                System.out.println("      Index: " + input.getTxOutputIndex());
            }

            System.out.println("  Outputs:");
            for (int j = 0; j < tx.getOutputs().length; j++) {
                TXOutput output = tx.getOutputs()[j];

                System.out.println("    Output #" + j);
                System.out.println("      Value: " + output.getValue());
                System.out.println("      PubKeyHash: " + Hex.encodeHexString(output.getPubKeyHash()));
            }
        }
    }

    /**
     * 清空交易池
     */
    private void clearMempool() {
        Mempool.clear();
        log.info("Mempool cleared.");
    }

    /**
     * 打印帮助信息
     */
    private void help() {
        System.out.println("Usage:");
        System.out.println("  createwallet - Generates a new key-pair and saves it into the wallet file");
        System.out.println("  printaddresses - print all wallet address");
        System.out.println("  printbalances - Print balances of all local wallet addresses");
        System.out.println("  getbalance -address ADDRESS - Get balance of ADDRESS");
        System.out.println("  createblockchain -address ADDRESS - Create a blockchain and send genesis block reward to ADDRESS");
        System.out.println("  printchain - Print all the blocks of the blockchain");

        // 修改send说明
        System.out.println("  send -from FROM -to TO -amount AMOUNT - Create a transaction and add it to mempool");

        // 新增命令说明
        System.out.println("  mine -miner ADDRESS - Mine pending transactions from mempool and reward miner");
        System.out.println("  printmempool - Print all pending transactions in mempool");
        System.out.println("  clearmempool - Clear all pending transactions in mempool");
        System.out.println("  verifytx -block BLOCK_HASH -txid TX_ID - Verify transaction existence by Merkle Proof");
        System.out.println("  forktest -miner1 ADDRESS -miner2 ADDRESS - Simulate fork and longest chain rule");

        System.exit(0);
    }

    /**
     * 打印出区块链中的所有区块
     */
    private void printChain() {
        Blockchain blockchain = Blockchain.initBlockchainFromDB();

        for (Blockchain.BlockchainIterator iterator = blockchain.getBlockchainIterator(); iterator.hashNext(); ) {
            Block block = iterator.next();
            if (block != null) {
                printBlock(block);
            }
        }
    }

    /**
     * 验证某笔交易是否存在于指定区块中，并验证Merkle Proof
     *
     * @param blockHash 区块Hash
     * @param txIdHex   交易ID
     */
    private void verifyTx(String blockHash, String txIdHex) {
        Blockchain blockchain = Blockchain.initBlockchainFromDB();

        Block targetBlock = null;

        for (Blockchain.BlockchainIterator iterator = blockchain.getBlockchainIterator(); iterator.hashNext(); ) {
            Block block = iterator.next();

            if (block != null && block.getHash().equalsIgnoreCase(blockHash)) {
                targetBlock = block;
                break;
            }
        }

        if (targetBlock == null) {
            log.error("ERROR: Block not found ! blockHash={}", blockHash);
            return;
        }

        Transaction targetTx = null;

        for (Transaction tx : targetBlock.getTransactions()) {
            String currentTxId = Hex.encodeHexString(tx.getTxId());

            if (currentTxId.equalsIgnoreCase(txIdHex)) {
                targetTx = tx;
                break;
            }
        }

        if (targetTx == null) {
            log.error("ERROR: Transaction not found in block ! txId={}", txIdHex);
            return;
        }

        byte[][] txHashes = new byte[targetBlock.getTransactions().length][];

        for (int i = 0; i < targetBlock.getTransactions().length; i++) {
            txHashes[i] = targetBlock.getTransactions()[i].hash();
        }

        MerkleTree merkleTree = new MerkleTree(txHashes);

        byte[] targetTxHash = targetTx.hash();
        byte[] merkleRoot = merkleTree.getRootHash();

        List<MerkleTree.ProofNode> proof = merkleTree.getProof(targetTxHash);

        boolean proofValid = MerkleTree.verifyProof(targetTxHash, proof, merkleRoot);

        System.out.println("==================================================");
        System.out.println("Merkle Transaction Verification");
        System.out.println("==================================================");
        System.out.println("Block Hash:      " + targetBlock.getHash());
        System.out.println("TX ID:           " + Hex.encodeHexString(targetTx.getTxId()));
        System.out.println("TX Hash:         " + Hex.encodeHexString(targetTxHash));
        System.out.println("Merkle Root:     " + Hex.encodeHexString(merkleRoot));
        System.out.println("Proof Nodes:     " + proof.size());

        for (int i = 0; i < proof.size(); i++) {
            MerkleTree.ProofNode proofNode = proof.get(i);

            System.out.println("  Proof Node #" + i);
            System.out.println("    Hash:        " + Hex.encodeHexString(proofNode.getHash()));
            System.out.println("    Position:    " + (proofNode.isLeftSibling() ? "LEFT" : "RIGHT"));
        }

        System.out.println("Proof Valid:     " + proofValid);

        if (proofValid) {
            System.out.println("Result:          Transaction exists in this block.");
        } else {
            System.out.println("Result:          Transaction proof invalid.");
        }
    }

    /**
     * 分叉与最长链规则模拟
     *
     * 模拟过程：
     * 1. 获取当前主链末端作为分叉点；
     * 2. 在同一个父区块后挖出两个不同分支；
     * 3. 分支1只挖1个新区块；
     * 4. 分支2连续挖2个新区块；
     * 5. 比较两个分支高度；
     * 6. 将主链切换到更长的分支；
     * 7. 重建UTXO集合。
     *
     * @param miner1 分支1矿工地址
     * @param miner2 分支2矿工地址
     * @throws Exception
     */
    private void forkTest(String miner1, String miner2) throws Exception {
        validateWalletAddress(miner1, "miner1");
        validateWalletAddress(miner2, "miner2");

        Blockchain blockchain = Blockchain.initBlockchainFromDB();

        String forkBaseHash = blockchain.getLastBlockHash();
        int baseHeight = blockchain.getHeight(forkBaseHash);

        System.out.println("==================================================");
        System.out.println("Fork And Longest Chain Test");
        System.out.println("==================================================");
        System.out.println("Fork Base Hash:  " + forkBaseHash);
        System.out.println("Base Height:     " + baseHeight);

        /*
         * 分支1：
         * forkBase -> branch1Block
         */
        Transaction branch1RewardTx = Transaction.newCoinbaseTX(
                miner1,
                "Fork branch 1 reward to " + miner1
        );

        Block branch1Block = blockchain.mineBlockOn(
                forkBaseHash,
                new Transaction[]{branch1RewardTx},
                false
        );

        int branch1Height = blockchain.getHeight(branch1Block.getHash());

        System.out.println();
        System.out.println("Branch 1 created:");
        System.out.println("  Miner:         " + miner1);
        System.out.println("  Block Hash:    " + branch1Block.getHash());
        System.out.println("  Prev Hash:     " + branch1Block.getPrevBlockHash());
        System.out.println("  Height:        " + branch1Height);

        /*
         * 分支2：
         * forkBase -> branch2Block1 -> branch2Block2
         */
        Transaction branch2RewardTx1 = Transaction.newCoinbaseTX(
                miner2,
                "Fork branch 2 block 1 reward to " + miner2
        );

        Block branch2Block1 = blockchain.mineBlockOn(
                forkBaseHash,
                new Transaction[]{branch2RewardTx1},
                false
        );

        Transaction branch2RewardTx2 = Transaction.newCoinbaseTX(
                miner2,
                "Fork branch 2 block 2 reward to " + miner2
        );

        Block branch2Block2 = blockchain.mineBlockOn(
                branch2Block1.getHash(),
                new Transaction[]{branch2RewardTx2},
                false
        );

        int branch2Height = blockchain.getHeight(branch2Block2.getHash());

        System.out.println();
        System.out.println("Branch 2 created:");
        System.out.println("  Miner:         " + miner2);
        System.out.println("  Block 1 Hash:  " + branch2Block1.getHash());
        System.out.println("  Block 1 Prev:  " + branch2Block1.getPrevBlockHash());
        System.out.println("  Block 2 Hash:  " + branch2Block2.getHash());
        System.out.println("  Block 2 Prev:  " + branch2Block2.getPrevBlockHash());
        System.out.println("  Height:        " + branch2Height);

        System.out.println();
        System.out.println("Fork detected:");
        System.out.println("  Branch 1 Height: " + branch1Height);
        System.out.println("  Branch 2 Height: " + branch2Height);

        if (branch2Height > branch1Height) {
            blockchain.switchToBlock(branch2Block2.getHash());

            /*
             * 主链切换后，需要重建UTXO集合。
             * 因为UTXO集合只应该反映当前主链，而不是所有分叉区块。
             */
            new UTXOSet(blockchain).reIndex();

            System.out.println();
            System.out.println("Longest chain selected.");
            System.out.println("Main chain switched to Branch 2.");
            System.out.println("New Main Tip:   " + branch2Block2.getHash());
            System.out.println("New Height:     " + blockchain.getHeight(branch2Block2.getHash()));
        } else {
            blockchain.switchToBlock(branch1Block.getHash());
            new UTXOSet(blockchain).reIndex();

            System.out.println();
            System.out.println("Branch 1 selected.");
            System.out.println("New Main Tip:   " + branch1Block.getHash());
            System.out.println("New Height:     " + blockchain.getHeight(branch1Block.getHash()));
        }

        System.out.println();
        System.out.println("Tip after switch: " + blockchain.getLastBlockHash());
        System.out.println("Please run printchain to view the selected main chain.");
    }
    /**
     * 校验钱包地址
     *
     * @param address 地址
     * @param name    参数名
     */
    private void validateWalletAddress(String address, String name) {
        try {
            Base58Check.base58ToBytes(address);
        } catch (Exception e) {
            log.error("ERROR: {} address invalid ! address={}", name, address, e);
            throw new RuntimeException("ERROR: " + name + " address invalid ! address=" + address, e);
        }
    }

    /**
     * 结构化打印区块
     *
     * @param block
     */
    private void printBlock(Block block) {
        boolean validate = ProofOfWork.newProofOfWork(block).validate();

        System.out.println("==================================================");
        System.out.println("Block Hash:      " + block.getHash());
        System.out.println("Prev Hash:       " + block.getPrevBlockHash());
        System.out.println("Timestamp:       " + block.getTimeStamp());
        System.out.println("Nonce:           " + block.getNonce());
        System.out.println("POW Valid:       " + validate);
        System.out.println("Merkle Root:     " + block.getMerkleRootHex());
        System.out.println("Transactions:    " + block.getTransactions().length);

        for (Transaction tx : block.getTransactions()) {
            printTransaction(tx);
        }

        System.out.println();
    }

    /**
     * 结构化打印交易
     *
     * @param tx
     */
    private void printTransaction(Transaction tx) {
        System.out.println("  TX ID: " + Hex.encodeHexString(tx.getTxId()));

        System.out.println("    Inputs:");
        for (TXInput input : tx.getInputs()) {
            String txId = input.getTxId() == null || input.getTxId().length == 0
                    ? ""
                    : Hex.encodeHexString(input.getTxId());

            String scriptSig;

            if (input.getTxId() == null || input.getTxId().length == 0) {
                // Coinbase交易：pubKey中保存的是奖励说明，可以按字符串输出
                scriptSig = input.getPubKey() == null ? "" : new String(input.getPubKey());
            } else {
                // 普通交易：pubKey是二进制公钥，必须转成十六进制输出
                scriptSig = input.getPubKey() == null ? "" : Hex.encodeHexString(input.getPubKey());
            }

            System.out.println("      TxId: " + txId
                    + ", Index: " + input.getTxOutputIndex()
                    + ", ScriptSig: " + scriptSig);
        }

        System.out.println("    Outputs:");
        for (TXOutput output : tx.getOutputs()) {
            System.out.println("      Value: " + output.getValue()
                    + ", ScriptPubKey: " + Hex.encodeHexString(output.getPubKeyHash()));
        }
    }
}
