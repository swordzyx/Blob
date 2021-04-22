Java 1.8

//无参构造方法，保存负载因子，DEFAULT_LOAD_FACTOR 为 0.75
public HashMap() {
    this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
}


public V put(K key, V value) {
	//对 key 执行 hash 操作 
    return putVal(hash(key), key, value, false, true);
}

//HashMap 的底层是通过数组 + 链表，
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
                        treeifyBin(tab, hash);
                    break;
                }
                if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k))))
                    break;
                p = e;
            }
        }
        if (e != null) { // existing mapping for key
            V oldValue = e.value;
            if (!onlyIfAbsent || oldValue == null)
                e.value = value;
            afterNodeAccess(e);
            return oldValue;
        }
    }
    ++modCount;
    if (++size > threshold)
        resize();
    afterNodeInsertion(evict);
    return null;
}


static class Node<K,V> implements Map.Entry<K,V> {
	final int hash;
    final K key;
    V value;
    Node<K,V> next;
}