package one.wangwei.blockchain.transaction;

import com.google.common.collect.Lists;
import lombok.Data;
import one.wangwei.blockchain.util.ByteUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 默克尔树
 *
 * @author wangwei
 * @date 2018/04/15
 */
@Data
public class MerkleTree {

    /**
     * 根节点
     */
    private Node root;

    /**
     * 叶子节点Hash
     */
    private byte[][] leafHashes;

    public MerkleTree(byte[][] leafHashes) {
        constructTree(leafHashes);
    }

    /**
     * 从底部叶子节点开始往上构建整个Merkle Tree
     *
     * @param leafHashes
     */
    private void constructTree(byte[][] leafHashes) {
        if (leafHashes == null || leafHashes.length < 1) {
            throw new RuntimeException("ERROR: Fail to construct merkle tree ! leafHashes data invalid ! ");
        }

        this.leafHashes = leafHashes;

        List<Node> parents = bottomLevel(leafHashes);

        while (parents.size() > 1) {
            parents = internalLevel(parents);
        }

        root = parents.get(0);
    }

    /**
     * 构建一个层级节点
     *
     * @param children
     * @return
     */
    private List<Node> internalLevel(List<Node> children) {
        List<Node> parents = Lists.newArrayListWithCapacity(children.size() / 2);

        for (int i = 0; i < children.size() - 1; i += 2) {
            Node child1 = children.get(i);
            Node child2 = children.get(i + 1);

            Node parent = constructInternalNode(child1, child2);
            parents.add(parent);
        }

        // 内部节点奇数个，只对left节点进行计算
        if (children.size() % 2 != 0) {
            Node child = children.get(children.size() - 1);
            Node parent = constructInternalNode(child, null);
            parents.add(parent);
        }

        return parents;
    }

    /**
     * 底部节点构建
     *
     * @param hashes
     * @return
     */
    private List<Node> bottomLevel(byte[][] hashes) {
        List<Node> parents = Lists.newArrayListWithCapacity(hashes.length / 2);

        for (int i = 0; i < hashes.length - 1; i += 2) {
            Node leaf1 = constructLeafNode(hashes[i]);
            Node leaf2 = constructLeafNode(hashes[i + 1]);

            Node parent = constructInternalNode(leaf1, leaf2);
            parents.add(parent);
        }

        if (hashes.length % 2 != 0) {
            Node leaf = constructLeafNode(hashes[hashes.length - 1]);

            // 奇数个叶子节点时，复制最后一个节点
            Node parent = constructInternalNode(leaf, leaf);
            parents.add(parent);
        }

        return parents;
    }

    /**
     * 构建叶子节点
     *
     * @param hash
     * @return
     */
    private static Node constructLeafNode(byte[] hash) {
        Node leaf = new Node();
        leaf.hash = hash;
        return leaf;
    }

    /**
     * 构建内部节点
     *
     * @param leftChild
     * @param rightChild
     * @return
     */
    private Node constructInternalNode(Node leftChild, Node rightChild) {
        Node parent = new Node();

        if (rightChild == null) {
            parent.hash = leftChild.hash;
        } else {
            parent.hash = internalHash(leftChild.hash, rightChild.hash);
        }

        parent.left = leftChild;
        parent.right = rightChild;
        return parent;
    }

    /**
     * 计算内部节点Hash
     *
     * @param leftChildHash
     * @param rightChildHash
     * @return
     */
    private static byte[] internalHash(byte[] leftChildHash, byte[] rightChildHash) {
        byte[] mergedBytes = ByteUtils.merge(leftChildHash, rightChildHash);
        return DigestUtils.sha256(mergedBytes);
    }

    /**
     * 新增：获取Merkle Root
     *
     * @return
     */
    public byte[] getRootHash() {
        if (this.root == null) {
            return new byte[]{};
        }
        return this.root.getHash();
    }

    /**
     * 新增：生成某个交易Hash的Merkle Proof
     *
     * @param targetHash 目标交易Hash
     * @return 证明路径
     */
    public List<ProofNode> getProof(byte[] targetHash) {
        if (targetHash == null || targetHash.length == 0) {
            throw new RuntimeException("ERROR: target hash is empty !");
        }

        List<ProofNode> proof = Lists.newArrayList();

        boolean found = buildProof(this.root, targetHash, proof);

        if (!found) {
            throw new RuntimeException("ERROR: target hash not found in merkle tree ! targetHash="
                    + Hex.encodeHexString(targetHash));
        }

        return proof;
    }

    /**
     * 新增：递归生成证明路径
     *
     * proof中的节点顺序为：从叶子节点到根节点
     *
     * @param current
     * @param targetHash
     * @param proof
     * @return
     */
    private boolean buildProof(Node current, byte[] targetHash, List<ProofNode> proof) {
        if (current == null) {
            return false;
        }

        // 叶子节点
        if (current.left == null && current.right == null) {
            return Arrays.equals(current.hash, targetHash);
        }

        // 目标在左子树
        if (buildProof(current.left, targetHash, proof)) {
            if (current.right != null) {
                // 兄弟节点在右边
                proof.add(new ProofNode(current.right.hash, false));
            }
            return true;
        }

        // 目标在右子树
        if (buildProof(current.right, targetHash, proof)) {
            if (current.left != null) {
                // 兄弟节点在左边
                proof.add(new ProofNode(current.left.hash, true));
            }
            return true;
        }

        return false;
    }

    /**
     * 新增：验证Merkle Proof
     *
     * @param targetHash 目标交易Hash
     * @param proof      证明路径
     * @param rootHash   Merkle Root
     * @return
     */
    public static boolean verifyProof(byte[] targetHash, List<ProofNode> proof, byte[] rootHash) {
        if (targetHash == null || targetHash.length == 0) {
            return false;
        }

        if (rootHash == null || rootHash.length == 0) {
            return false;
        }

        byte[] currentHash = targetHash;

        if (proof != null && proof.size() > 0) {
            for (ProofNode proofNode : proof) {
                if (proofNode.isLeftSibling()) {
                    // 兄弟节点在左边：Hash(sibling + current)
                    currentHash = internalHash(proofNode.getHash(), currentHash);
                } else {
                    // 兄弟节点在右边：Hash(current + sibling)
                    currentHash = internalHash(currentHash, proofNode.getHash());
                }
            }
        }

        return Arrays.equals(currentHash, rootHash);
    }

    /**
     * Merkle Tree节点
     */
    @Data
    public static class Node {
        private byte[] hash;
        private Node left;
        private Node right;
    }

    /**
     * 新增：Merkle Proof路径节点
     */
    @Data
    public static class ProofNode {

        /**
         * 兄弟节点Hash
         */
        private byte[] hash;

        /**
         * true：兄弟节点在左边
         * false：兄弟节点在右边
         */
        private boolean leftSibling;

        public ProofNode(byte[] hash, boolean leftSibling) {
            this.hash = hash;
            this.leftSibling = leftSibling;
        }
    }
}
