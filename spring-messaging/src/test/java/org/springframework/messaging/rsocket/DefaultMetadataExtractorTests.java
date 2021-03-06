/*
 * Copyright 2002-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.messaging.rsocket;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import io.netty.buffer.PooledByteBufAllocator;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import reactor.core.publisher.Mono;

import org.springframework.core.codec.ByteArrayDecoder;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.messaging.rsocket.MetadataExtractor.COMPOSITE_METADATA;
import static org.springframework.messaging.rsocket.MetadataExtractor.ROUTE_KEY;
import static org.springframework.messaging.rsocket.MetadataExtractor.ROUTING;
import static org.springframework.util.MimeTypeUtils.TEXT_HTML;
import static org.springframework.util.MimeTypeUtils.TEXT_PLAIN;
import static org.springframework.util.MimeTypeUtils.TEXT_XML;


/**
 * Unit tests for {@link DefaultMetadataExtractor}.
 * @author Rossen Stoyanchev
 */
public class DefaultMetadataExtractorTests {

	private RSocketStrategies strategies;

	private ArgumentCaptor<Payload> captor;

	private RSocket rsocket;

	private DefaultMetadataExtractor extractor;


	@Before
	public void setUp() {
		this.strategies = RSocketStrategies.builder()
				.dataBufferFactory(new LeakAwareNettyDataBufferFactory(PooledByteBufAllocator.DEFAULT))
				.build();

		this.rsocket = BDDMockito.mock(RSocket.class);
		this.captor = ArgumentCaptor.forClass(Payload.class);
		BDDMockito.when(this.rsocket.fireAndForget(captor.capture())).thenReturn(Mono.empty());

		this.extractor = new DefaultMetadataExtractor();
		this.extractor.setDecoders(Collections.singletonList(StringDecoder.allMimeTypes()));
	}

	@After
	public void tearDown() throws InterruptedException {
		DataBufferFactory bufferFactory = this.strategies.dataBufferFactory();
		((LeakAwareNettyDataBufferFactory) bufferFactory).checkForLeaks(Duration.ofSeconds(5));
	}


	@Test
	public void compositeMetadataWithDefaultSettings() {
		requester(COMPOSITE_METADATA).route("toA")
				.metadata("text data", TEXT_PLAIN)
				.metadata("html data", TEXT_HTML)
				.metadata("xml data", TEXT_XML)
				.data("data")
				.send().block();

		Payload payload = this.captor.getValue();
		Map<String, Object> result = this.extractor.extract(payload, COMPOSITE_METADATA);
		payload.release();

		assertThat(result).hasSize(1).containsEntry(ROUTE_KEY, "toA");
	}

	@Test
	public void compositeMetadataWithMimeTypeRegistrations() {
		this.extractor.metadataToExtract(TEXT_PLAIN, String.class, "text-entry");
		this.extractor.metadataToExtract(TEXT_HTML, String.class, "html-entry");
		this.extractor.metadataToExtract(TEXT_XML, String.class, "xml-entry");

		requester(COMPOSITE_METADATA).route("toA")
				.metadata("text data", TEXT_PLAIN)
				.metadata("html data", TEXT_HTML)
				.metadata("xml data", TEXT_XML)
				.data("data")
				.send()
				.block();

		Payload payload = this.captor.getValue();
		Map<String, Object> result = this.extractor.extract(payload, COMPOSITE_METADATA);
		payload.release();

		assertThat(result).hasSize(4)
				.containsEntry(ROUTE_KEY, "toA")
				.containsEntry("text-entry", "text data")
				.containsEntry("html-entry", "html data")
				.containsEntry("xml-entry", "xml data");
	}

	@Test
	public void route() {
		requester(ROUTING).route("toA").data("data").send().block();
		Payload payload = this.captor.getValue();
		Map<String, Object> result = this.extractor.extract(payload, ROUTING);
		payload.release();

		assertThat(result).hasSize(1).containsEntry(ROUTE_KEY, "toA");
	}

	@Test
	public void routeAsText() {
		this.extractor.metadataToExtract(TEXT_PLAIN, String.class, ROUTE_KEY);

		requester(TEXT_PLAIN).route("toA").data("data").send().block();
		Payload payload = this.captor.getValue();
		Map<String, Object> result = this.extractor.extract(payload, TEXT_PLAIN);
		payload.release();

		assertThat(result).hasSize(1).containsEntry(ROUTE_KEY, "toA");
	}

	@Test
	public void routeWithCustomFormatting() {
		this.extractor.metadataToExtract(TEXT_PLAIN, String.class, (text, result) -> {
			String[] items = text.split(":");
			Assert.isTrue(items.length == 2, "Expected two items");
			result.put(ROUTE_KEY, items[0]);
			result.put("entry1", items[1]);
		});

		requester(TEXT_PLAIN).metadata("toA:text data", null).data("data").send().block();
		Payload payload = this.captor.getValue();
		Map<String, Object> result = this.extractor.extract(payload, TEXT_PLAIN);
		payload.release();

		assertThat(result).hasSize(2)
				.containsEntry(ROUTE_KEY, "toA")
				.containsEntry("entry1", "text data");
	}

	@Test
	public void addMetadataToExtractBeforeDecoders() {
		DefaultMetadataExtractor extractor = new DefaultMetadataExtractor();
		extractor.metadataToExtract(TEXT_PLAIN, String.class, "key");
		extractor.setDecoders(Collections.singletonList(StringDecoder.allMimeTypes()));

		requester(TEXT_PLAIN).metadata("meta entry", null).data("data").send().block();
		Payload payload = this.captor.getValue();
		Map<String, Object> result = extractor.extract(payload, TEXT_PLAIN);
		payload.release();

		assertThat(result).hasSize(1).containsEntry("key", "meta entry");
	}

	@Test
	public void noDecoderExceptionWhenSettingDecoders() {
		DefaultMetadataExtractor extractor = new DefaultMetadataExtractor();
		extractor.metadataToExtract(TEXT_PLAIN, String.class, "key");

		assertThatIllegalArgumentException()
				.isThrownBy(() -> extractor.setDecoders(Collections.singletonList(new ByteArrayDecoder())))
				.withMessage("No decoder for MetadataProcessor mimeType=text/plain, targetType=java.lang.String");
	}

	@Test
	public void noDecoderExceptionWhenRegisteringMetadataToExtract() {
		DefaultMetadataExtractor extractor = new DefaultMetadataExtractor();
		extractor.setDecoders(Collections.singletonList(new ByteArrayDecoder()));

		assertThatIllegalArgumentException()
				.isThrownBy(() -> extractor.metadataToExtract(TEXT_PLAIN, String.class, "key"))
				.withMessage("No decoder for text/plain");
	}

	@Test
	public void decodersNotSet() {
		DefaultMetadataExtractor extractor = new DefaultMetadataExtractor();
		extractor.metadataToExtract(TEXT_PLAIN, String.class, "key");

		assertThatIllegalStateException()
				.isThrownBy(() -> {
					requester(TEXT_PLAIN).metadata("meta entry", null).data("data").send().block();
					Payload payload = this.captor.getValue();
					try {
						extractor.extract(payload, TEXT_PLAIN);
					}
					finally {
						payload.release();
					}
				})
				.withMessage("No decoder for MetadataProcessor mimeType=text/plain, targetType=java.lang.String");
	}


	private RSocketRequester requester(MimeType metadataMimeType) {
		return RSocketRequester.wrap(this.rsocket, TEXT_PLAIN, metadataMimeType, this.strategies);
	}

}
