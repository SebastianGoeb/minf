MInf Project (Part 2) - Server
========================


This server generates HTTP responses of arbitrary length at `http://<serverip>:8080/<length>`. It accepts a human readable length with any of these units: `1234 | 1k | 2K | 3M | 4G | 5T | 6P`. Only integers are allowed and unitless numbers are intepreted as bytes.

--------------------------------


Build and Run
--------------------

```
#!/bin/bash
mvn clean install
java -jar target/server-jar-with-dependencies.jar
```

