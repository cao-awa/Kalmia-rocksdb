# Kora-rocksdb
A Rocks DB plugin for Kora webserver.

## Usage
Add dependencies first: 
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
    }
}

dependencies {
    implementation 'com.github.cao-awa:Kora-rocksdb:{version}'
}
```

For the versions, see [JitPack](https://jitpack.io/#cao-awa/Kora-rocksdb).

And use Rocks DB in your code:
```kotlin
import com.github.cao.awa.kora.rocksdb.db.KoraRocksDB

object Test {
    @JvmStatic
    fun entry() {
        val db = KoraRocksDB.open(File("db-name"))
        // Set data to rocks db.
        db["test-key"] = "test"
        // Get data from rocks db.
        println(db.getString("test-key"))
    }
}
```

In produce environment, you need put the ``kora-rocksdb`` jar to ``libs/`` directory and declare entrypoint:
```json
{
    "entrypoint": [
        "kora-rocksdb",
        "com.yourservice.xxx.ServiceEntrypoint#entry"
    ]
}
```

For entrypoint, please see [Kora's document](https://github.com/cao-awa/Kora/tree/main/docs/entrypoint).