# opencensus-for-grpc-java-developers
OpenCensus for gRPC Java developers

## Installing dependencies

Requires maven and Java 6+

```shell
mvn install
```

### Environment variables

Name|Notes
---|---
GOOGLE_APPLICATION_CREDENTIALS|A file containing Google Cloud Platform project credentials. If you don't have one yet installed, please visit https://cloud.google.com/docs/authentication/production

## Running the setup

Assuming you successfully ran the `Installing dependencies` step
```shell
mvn exec:java -Dexec.mainClass=io.ocgrpc.capitalize.CapitalizeServer
```

In another terminal
```shell
mvn exec:java -Dexec.mainClass=io.ocgrpc.capitalize.CapitalizeClient

> truly
< TRULY

> gist
< GIST

> first
< FIRST

> one
< ONE

> there
< THERE

> 🚀 a1 a4
< 🚀 A1 A4
```
