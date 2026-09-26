# Kalmia-rocksdb
A Rocks DB plugin for Kalmia webserver.

## Usage
Add dependencies first: 
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
    }
}

dependencies {
    implementation 'com.github.cao-awa:Kalmia-rocksdb:{version}'
}
```

For the versions, see [JitPack](https://jitpack.io/#cao-awa/Kalmia-rocksdb).

And use Rocks DB in your code:
```kotlin
import com.github.cao.awa.kalmia.rocksdb.db.KalmiaRocksDB

object Test {
    @JvmStatic
    fun entry() {
        val db = KalmiaRocksDB.open(File("db-name"))
        // Set data to rocks db.
        db["test-key"] = "test"
        // Get data from rocks db.
        println(db.getString("test-key"))
    }
}
```

In produce environment, you need put the ``kalmia-rocksdb`` jar to ``libs/`` directory and declare entrypoint:
```json
{
    "entrypoint": [
        "kalmia-rocksdb",
        "com.yourservice.xxx.ServiceEntrypoint#entry"
    ]
}
```

And put [RocksDB jar](https://mvnrepository.com/artifact/org.rocksdb/rocksdbjni/10.10.1.1) to ``libs/`` directory too.

For entrypoint, please see [Kalmia's document](https://github.com/cao-awa/Kalmia/tree/main/docs/entrypoint).