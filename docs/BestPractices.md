#  AWS Java SDK 2.x Best Practices

Here are the best practices of using AWS Java SDK 2.x.

### Reuse SDK client if possible

Each SDK client maintains many resources such as connections and threads, so it is recommended to share a single instance of the client to avoid the overhead of resources management. All SDK clients are thread safe. 

If it is not desirable to share a client instance, call `client.close()` to release the resources once the client is not needed.

### Close input stream after read

For streaming operations such as `S3Client#getObject`,  if you are working with `ResponseInputStream` directly, we recommend closing the input stream as soon as possible so that the underlying HTTP connection can be reused.

### Tune HTTP configurations based on performance tests

SDK provides a set of [default http configurations] that apply to general use cases. Customers are recommended to tune the configurations for their applications based on their use cases.

### Use OpenSSL for `NettyAsynHttpClient`

By default, `NettyAsynHttpClient` uses JDK as the SslProvider. In our local tests, we found that using `OpenSSL`
is more performant than JDK. Using OpenSSL is also the recommended approach by Netty community, see [Netty TLS with OpenSSL].

### Utilize `ApiCallAttemptTimeout` and `ApiCallTimeout` configurations

SDK provides timeout configurations on requests out of box and they can be configured via `ClientOverrideConfiguration#apiCallAttemptTimeout` and `ClientOverrideConfiguration#ApiCallTimeout`. See [example]

- `ApiCallAttemptTimeout` tracks the amount of time for a single http attempt and the request can be retried if timed out on api call attempt. 
- `ApiCallTimeout` configures the amount of time for the entire execution including all retry attempts. 

Using them together is helpful to set a hard limit on total time spent on all attempts across retries and each individual HTTP request to fail fast on one slow request.

### Turn on wire logging for debugging

Wire logs are helpful to determine the root cause when there is an error. See [how to enable logging](https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/java-dg-logging.html)

### Prefer high level libraries

High level libraries are separate libraries built on top of the generated SDK clients, such as DynamoDBMapper, TransferManager in v1. They provide many benefits over low level clients such as better abstractions of low level apis and additional features. 
It is preferred to use high level libraries. Currently, high level libraries for v2 are under development.

[default http configurations]: https://github.com/aws/aws-sdk-java-v2/blob/master/http-client-spi/src/main/java/software/amazon/awssdk/http/SdkHttpConfigurationOption.java
[Netty TLS with OpenSSL]: https://netty.io/wiki/requirements-for-4.x.html#tls-with-openssl
[example]: https://github.com/aws/aws-sdk-java-v2/blob/master/test/protocol-tests/src/test/java/software/amazon/awssdk/protocol/tests/timeout/sync/SyncApiCallTimeoutTest.java#L62