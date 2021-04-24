Java 1.8

//无参构造方法，保存负载因子，DEFAULT_LOAD_FACTOR 为 0.75
public HashMap() {
    this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
}

static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;
    final K key;
    V value;
    Node<K,V> next;
}



public V put(K key, V value) {
	//对 key 执行 hash 操作 
    return putVal(hash(key), key, value, false, true);
}

//HashMap 的底层是通过数组 + 链表，key -> hash -> index，将新的节点插入到 index 处，如果 index 处已经有一个
//key 和 hash 相同的值，则直接替换。如果 key 和 hash 不同，则发生了 hash 碰撞，将新的节点插入到 index 处的链表的末尾。
final V putVal(int hash, K key, V value, boolean onlyIfAbsent, boolean evict) {
    Node<K,V>[] tab; //一个 Node 数组，Node 里面包含了 hash 值，key，value，以及一个 next 指针
    Node<K,V> p; 
    int n, i;
    //如果当前 Map 还没有元素，则将 Map 的容量初始化为 DEFAULT_INITIAL_CAPACITY（16）
    if ((tab = table) == null || (n = tab.length) == 0)
        n = (tab = resize()).length;//HashMap 为 null 时，resize() 会执行初始化操作。
    if ((p = tab[i = (n - 1) & hash]) == null)//(n - 1) & hash 会将 hash 转成数组的下标。返回数组中该下标对应的 Node ，如果返回 null ，表示插入的是一个新的 key
        tab[i] = newNode(hash, key, value, null);//如果是一个新的 key，则创建一个新的 Node ，存放到数组中
    else {//如果数组中在 key 对应的下标处已经存在一个节点
        Node<K,V> e; K k;
        //如果数组中原始的那个节点的 key 以及 hash 与当前要插入的节点相同，说明是同一个 key，将 e 指向旧的节点
        if (p.hash == hash && ((k = p.key) == key || (key != null && key.equals(k))))
            e = p;
        else if (p instanceof TreeNode) //如果数组中原始节点是一个红黑树的节点，则执行插入红黑树节点的逻辑
            e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
        else {//如果数组中的原始节点就是一个链表节点，且原始节点的 key 和要插入的 key 不一样，说明发生了哈希碰撞
            for (int binCount = 0; ; ++binCount) {
                if ((e = p.next) == null) {//遍历到链表的最后一个节点
                    p.next = newNode(hash, key, value, null);//将新的节点插入到链表的末尾
                    //TREEIFY_THRESHOLD 的值为 8，链表的长度超过了 8 个，会执行链表转红黑树的操作。
                    if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                        treeifyBin(tab, hash);//执行链表转树
                    break;
                }
                if (e.hash == hash && ((k = e.key) == key || (key != null && key.equals(k))))//要插入的 key 在链表中已存在
                    break;
                p = e; //将 p 指向下一个节点。也就是继续遍历
            }
        }
        if (e != null) { //在链表中找到了一个节点 e，它的 key 与要插入的 key 相同
            V oldValue = e.value;
            //onlyIfAbsent 为 false 或者旧值为 null 时，才将新的值替换进去。onlyIfAbsent 表示仅在原始值为 null（即没有原始值）时才用新值替换旧的值。
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);//该方法在 LinkedHashMap 中实现，用于将 e 移到链表的末尾处
            return oldValue;//返回旧值
        }
    }
    ++modCount;
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict);
    return null;
}

//创建一个 Node 实例，表示 Map 中的节点
Node<K,V> newNode(int hash, K key, V value, Node<K,V> next) {
    return new Node<>(hash, key, value, next);
}


/**
 * 插入一个红黑树节点。
 */
final TreeNode<K,V> putTreeVal(HashMap<K,V> map, Node<K,V>[] tab, int h, K k, V v) {
    Class<?> kc = null;
    boolean searched = false;
    //获取树的根节点
    TreeNode<K,V> root = (parent != null) ? root() : this;
    //从根节点开始遍历，如果遍历到尾节点，依然没有找到 key 为 k，hash 为 h 的节点，则插入一个新的节点到末尾
    for (TreeNode<K,V> p = root;;) {
        int dir, ph; K pk;
        //根节点的 hash 大于要插入的节点的 hash
        if ((ph = p.hash) > h)
            dir = -1;
        else if (ph < h)
            dir = 1;
        //当前节点的 hash 等于要插入的节点的 hash，且 key 也相等，将当前节点作为原始节点返回
        else if ((pk = p.key) == k || (k != null && k.equals(pk)))
            return p;
        //如果 k 实现了 Comparable<C> 接口，comparableClassFor 则会返回它的 Class 实例
        //如果 k 是 kc（class 对象）的对象，则 compareComparables 会将 k 和 pk 进行比较，如果 k 和 pk 相等，则进入以下分支
        else if ((kc == null && (kc = comparableClassFor(k)) == null) || (dir = compareComparables(kc, k, pk)) == 0) {
            if (!searched) {//search 为 false
                TreeNode<K,V> q, ch;
                searched = true;
                //从当前节点出发，查找子节点中是否存在 key 为 k，hash 为 h ，key 的 class 类型为 kc 的子节点，如果找到了，说明要插入的节点在树中是存在的，直接返回原始节点
                if (((ch = p.left) != null && (q = ch.find(h, k, kc)) != null) || ((ch = p.right) != null && (q = ch.find(h, k, kc)) != null))
                    return q;
            }
            //走到这一步表示，要插入的子节点的 key 和当前节点的 key 相等，但它们的类是不可比较的。
            //tieBreakOrder 则是比较 k 和 pk 的 hash，如果 k 的 hash 小于等于 pk，返回 -1，否则返回 1
            dir = tieBreakOrder(k, pk);
        }

        TreeNode<K,V> xp = p;
        //dir 为 -1 ，则将 p 指向其左子节点，否则将其指向其右子节点
        if ((p = (dir <= 0) ? p.left : p.right) == null) {
            Node<K,V> xpn = xp.next;
            TreeNode<K,V> x = map.newTreeNode(h, k, v, xpn);
            if (dir <= 0)
                xp.left = x;
            else
                xp.right = x;
            xp.next = x;
            x.parent = x.prev = xp;
            if (xpn != null)
                ((TreeNode<K,V>)xpn).prev = x;
            moveRootToFront(tab, balanceInsertion(root, x));
            return null;
        }
    }
}

static final class TreeNode<K,V> extends LinkedHashMap.LinkedHashMapEntry<K,V> {
    TreeNode<K,V> parent;  // red-black tree links
    TreeNode<K,V> left;
    TreeNode<K,V> right;
    TreeNode<K,V> prev;    // needed to unlink next upon deletion
    boolean red;

    TreeNode(int hash, K key, V val, Node<K,V> next) {
        super(hash, key, val, next);
    }
}







































