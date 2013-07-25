LittleProxy for Android

Please see [original readme](https://github.com/adamfisk/LittleProxy/blob/master/README.md)

# build

```
$ git clone git@github.com:arielnetworks/LittleProxy.git
$ cd LittleProxy
$ mvn package -DskipTests=true
```

# run

```
$ cp ${LittleProxy}/target/littleproxy-0.6.0-SNAPSHOT-littleproxy-shade.jar ${my_android_project}/libs/
```

```java
new android.os.AsyncTask<Object, Object, Object>() {
    @Override
    protected Void doInBackground(Object... params) {
        final org.littleshoot.proxy.HttpProxyServer server
            = new org.littleshoot.proxy.DefaultHttpProxyServer(9999);
        server.start();
        return null;
    }
}.execute();

```
