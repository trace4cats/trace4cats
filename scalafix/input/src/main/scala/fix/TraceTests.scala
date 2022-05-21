/*
rule = v0_14
 */

package fix

import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.model.AttributeValue.StringValue
import io.janstenpickle.trace4cats.attributes.HostAttributes
import io.janstenpickle.trace4cats.sampling.dynamic.HotSwapSpanSampler
import io.janstenpickle.trace4cats.`export`.HotswapSpanCompleter
import io.janstenpickle.trace4cats.`export`.HotswapSpanExporter
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.sampling.dynamic.config.SamplerConfig
import io.janstenpickle.trace4cats.log.LogSpanExporter
import io.janstenpickle.trace4cats.filtering.AttributeFilter
import io.janstenpickle.trace4cats.sampling.tail.TailSpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateTailSpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import io.janstenpickle.trace4cats.base.context.Ask
import io.janstenpickle.trace4cats.base.optics.Lens
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import io.janstenpickle.trace4cats.model.ArbitraryAttributeValues
import io.janstenpickle.trace4cats.meta.PipeTracer

object TraceTests {}
