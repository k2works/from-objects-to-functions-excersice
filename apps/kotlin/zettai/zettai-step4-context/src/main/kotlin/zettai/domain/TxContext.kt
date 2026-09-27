package zettai.domain

/**
 * ハブの操作が必要とする文脈。
 *
 * **中身は空である。** ドメインは「文脈がある」ことだけを知り、それが何かを知らない。
 * 接続なのかインメモリなのかを決めるのはアダプタ側。
 *
 * 最初は connection(): Connection を持たせようとしたが、
 * DomainBoundaryTest が「ドメインが JDBC を import した」と検出した。
 * 文脈の中身をドメインに持ち込んではいけない。
 */
interface TxContext

/** 接続を持たない文脈。インメモリの実装が使う。 */
object InMemoryContext : TxContext
