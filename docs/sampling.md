# Sampling

  * [Head Sampling](#head-sampling)
    + [Always](#always)
    + [Never](#never)
    + [Probabilistic](#probabilistic)
    + [Rate](#rate)
  * [Tail Sampling](#tail-sampling)
    + [Why Tail Sampling?](#why-tail-sampling-)
    + [Sample Decision Store](#sample-decision-store)
    + [Collector Tail Sampling](#collector-tail-sampling)

There are two types of sampling in tracing; head and tail.

- Head sampling is performed at the source, the choice is made within the traced component as to whether the trace
should be sampled.
- Tail sampling is performed in infrastructure downstream from the traced component. This could be in a [collector] or
the tracing system itself.

As mentioned in the [design document](design.md), there are three kinds of head sampler provided out of the box,
although you are welcome to create other implementations of the `SpanSampler` or `TailSpanSampler` interfaces:

- Always
- Never
- Probabilistic
- Rate

## Head Sampling

### Always

This sampler ensures **all** spans will be sent to downstream components. You can use the sampler with the code block
below:

```scala
import io.janstenpickle.trace4cats.kernel.SpanSampler

SpanSampler.always
```

### Never

This sampler ensures **no** spans will be sent to downstream components. You can use the sampler with the code block
below:

```scala
import io.janstenpickle.trace4cats.kernel.SpanSampler

SpanSampler.never
```

### Probabilistic

This sampler uses the trace ID to decide whether a trace should be sampled, based on a probability parameter, which must
be between `0.0` and `1.0`, where `0.0` cause no spans to be sent downstream and `1.0` will cause all spans to be
forwarded.

The parameter `rootSpansOnly`, which defaults to `true`, configures whether to apply the sampler to spans which have
parents. You may want to set this to `false` if upstream systems are making many requests, having a negative
performance impact on the traced application.

You can use the sampler with the code block below:

```scala
import io.janstenpickle.trace4cats.kernel.SpanSampler

SpanSampler.probabilistic(probability = 0.05, rootSpansOnly = false)
```

### Rate

The rate span sampler uses a [token bucket] to control rate and ensure bursts of spans don't overwhelm downstream
components. There are two parameters required to configure this sampler;

- Bucket size: maximum number of tokens that spans that may be allocated in one go
- Token rate: how often tokens are added back to the bucket

In order to use the rate sampler, add the following dependency to your `build.sbt`:

```
"io.janstenpickle" %% "trace4cats-rate-sampling" % "0.10.0"
```

You can use the sampler with the code block below. For a more complete example see
[here](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AdvancedExample.scala).

```scala
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import scala.concurrent.duration._

RateSpanSampler.create[IO](bucketSize = 100, tokenRate = 10.millis)
```

## Tail Sampling

Tail sampling is slightly more complicated than head sampling, as sample decisions have to be applied post-hoc and
therefore remembered for when subsequent spans for a trace arrive after the initial decision has been made.

### Why Tail Sampling?

Tail sampling allows you to centrally control the rate or span production and even which traces get exported to
downstream systems. Depending on your [topology](topologies.md), you may be exporting spans to a cloud tracing system
such as [Stackdriver Trace] or [Datadog] where you are charged by the number of spans stored.

### Sample Decision Store

Your internal [topology](topologies.md) and configuration will influence the kind of decision store you use and whether
it can be internal to the Collector, or external.

If you just use a probability based sampler, decisions are based on the trace ID and will always result in the same
decision for the same trace ID. So if you are just using the probability sampler you will not need a sample decision
store.

If you use [Kafka](topologies.md#kafka), you can guarantee all spans for a trace will end up at the same [collector] and
use the default cache based decision store. This is because the trace ID is used as the message key and the producer is
set up to send all spans for the same trace ID to the same partition.

If you do not use Kafka, you should use an external sample store so that if you have more than one [collector] that
can receive spans for the same trace, the sample decision may be shared between all instances. If you enable sampling
in this kind of [topology](topologies.md), then you could end up with incomplete traces in you tracing system. At
present the only external sample store implementation available is Redis.

### Collector Tail Sampling

By using the tail sampling config on the Trace4Cats Collector you can centrally configure sampling for all of your
traces.

The configuration fragment below sets up the following:
  - A probability sampler, with a 50% chance the trace will be sampled
  - A span name sampler, which filters traces whose name contain the configured strings
    - This sampler only applies to root spans, subsequent spans will look up decisions for the trace against the
      decision store
    - *Note that the greater number of names, the more performance of the [collector] may be negatively impacted*
  - A [rate sampler](#rate), which ensures the number of spans passing through the [collector] doesn't exceed a defined
    rate
    - Take care ensure that the configuration does not conflict with batching config
  - A local cache decision store with
    - TTL per trace of 10 minutes
    - Maximum number of trace sample decision entries of 500000

```yaml
sampling:
  sample-probability: 0.05 # Optional - must be between 0 and 1.0. 1.0 being always sample, and 0.0 being never
  drop-span-names: # Optional - name of spans to drop (may be partial match)
    - healthcheck
    - readiness
    - metrics
  rate: # Optional - rate sampling
    max-batch-size: 1000
    token-rate-millis: 10
  cache-ttl-minutes: 10 # Cache duration for sample decision, defaults to 2 mins
  max-cache-size: 500000 # Max number of entries in the sample decision cache, defaults to 1000000
  redis: # Optional - use redis as a sample decision store
    host: redis-host
    port: 6378 # defaults to 6379
```

Alternatively you can configure a connection to a Redis cluster

```yaml
sampling:
  redis:
    cluster:
      - host: redis1
        port: 6378  # defaults to 6379
      - host: redis2
```

[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[collector]: components.md#collector
[token bucket]: https://en.wikipedia.org/wiki/Token_bucket
