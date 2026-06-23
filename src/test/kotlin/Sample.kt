import com.github.cao.awa.kora.rocksdb.db.KoraRocksDB

object Test {
    @JvmStatic
    fun entry() {
        val client = KoraRocksDB.open("test")
        // Set data to redis.
        client["test-key"] = "test"
        // Get data from redis.
        println(client.getString("test-key"))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        entry()
    }
}