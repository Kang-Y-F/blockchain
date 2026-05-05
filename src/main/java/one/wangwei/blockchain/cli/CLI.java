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

import java.util.Arrays;
import java.util.Set;

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

        options.addOption(address);
        options.addOption(sendFrom);
        options.addOption(sendTo);
        options.addOption(sendAmount);
        options.addOption(miner);
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
