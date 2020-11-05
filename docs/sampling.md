# Sampling

There are two types of sampling in tracing; head and tail. 

- Head sampling is performed at the source, the choice is made within the traced component as to whether the trace 
should be sampled.
- Tail sampling is performed in infrastructure downstream from the traced component. This could be in a [collector] or
the tracing system itself.

## Head Sampling

As mentioned in the [readme](../README.md), there are three kinds of head sampler provided out of the box, although you
are welcome to create other implementations of the `SpanSampler` interface:

- Always
- Never
- Probabilistic

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
  - A local cache decision store with
    - TTL per trace of 10 minutes
    - Maximum number of trace sample decision entries of 500000

```yaml
sampling:
  sample-probability: 0.05 # Optional - must be between 0 and 0.1. 0.1 being always sample, and 0.0 being never
  span-names: # Optional - name of spans to sample (may be partial match)
    - healthcheck
    - readiness
    - metrics
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
        port: 6378
      - host: redis2
```

[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[collector]: components.md#collector