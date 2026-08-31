package com.github.cao.awa.kalmia.rocksdb.entrypoint

import com.github.cao.awa.kalmia.rocksdb.db.KalmiaRocksDB
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object RocksDBPluginBootstrap {
    private val LOGGER: Logger = LogManager.getLogger("RocksDBPluginBootstrap")

    @JvmStatic
    fun init() {
        LOGGER.info("Initializing rocks DB")

        KalmiaRocksDB.init()
    }
}
