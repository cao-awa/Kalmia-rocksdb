import com.github.kusa233.kalmia.rocksdb.db.KalmiaRocksDB

object Test {
    @JvmStatic
    fun entry() {
        val client = KalmiaRocksDB.open("test")
        // Set data to rocks db.
        client["test-key"] = "test"
        // Get data from rocks db.
        println(client.getString("test-key"))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        entry()
    }
}