/**
 * Copyright (C) Telicent Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.telicent.jena.fuseki.config.yaml;

import io.telicent.smart.cache.sources.Event;
import io.telicent.smart.cache.sources.TelicentHeaders;
import io.telicent.smart.cache.sources.kafka.BasicKafkaTestCluster;
import io.telicent.smart.cache.sources.kafka.KafkaEventSource;
import io.telicent.smart.cache.sources.kafka.KafkaTestCluster;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.kafka.FKLib;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.http.HttpOp;
import org.apache.jena.kafka.KafkaConnectorAssembler;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.http.QueryExecHTTP;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.system.G;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.BytesDeserializer;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.apache.kafka.common.utils.Bytes;
import org.eclipse.jetty.util.StringUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static io.telicent.jena.fuseki.config.yaml.ConfigConstants.log;

/**
 * Tests for servers using Fuseki Kafka connectors.
 */
public class DockerTestIntegrationConnector {
    static {
        FusekiLogging.setLogging();
    }

    private static final String TOPIC = "TEST";
    private static final String DSG = "/ds";
    private static KafkaTestCluster kafka;
    private static String DIR = "src/test/files/";
    private static String STATE_DIR = "target/state";

    public final String sparqlUpdate = """
             PREFIX : <http://example/>
             INSERT DATA {
                 :s :p4 12355 .
                 :s :p5 45655 .
            }""";

    Properties producerProps() {
        Properties producerProps = new Properties();
        producerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        return producerProps;
    }

    @BeforeAll
    public static void before() {
        JenaSystem.init();

        // Set up Kafka Test Cluster
        kafka = new BasicKafkaTestCluster();
        kafka.setup();

        // Create the necessary topics
        kafka.createTopic("RDF0");
        kafka.createTopic("RDF0.dlq");
        kafka.createTopic("RDF1");
        kafka.createTopic("RDF1.dlq");
        kafka.createTopic("RDF2");
        kafka.createTopic("RDF_Patch");
    }

    @AfterAll
    public static void after() {
        kafka.teardown();
    }

    @Test
    public void connectorIntegrationTest1() {
        // Given
        YAMLConfigParser ycp = new YAMLConfigParser();
        RDFConfigGenerator rcg = new RDFConfigGenerator();
        try {
            ConfigStruct config =
                    ycp.runYAMLParser("src/test/files/yaml/correct/connector/config-connector-integration-test-1.yaml");
            Model model = rcg.createRDFModel(config);
            model.write(System.out, "TTL");
            try (FileOutputStream out = new FileOutputStream(
                    DIR + "rdf/connector/config-connector-integration-test-1.ttl")) {
                model.write(out, "TTL");
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }

        String TOPIC = "RDF0";
        Graph graph = configuration(DIR + "rdf/connector/config-connector-integration-test-1.ttl",
                                    kafka.getBootstrapServers());
        FileOps.ensureDir(STATE_DIR);
        FileOps.clearDirectory(STATE_DIR);

        // When
        FusekiServer server = FusekiServer.create()
                                          .port(0)
                                          .verbose(true)
                                          .parseConfig(ModelFactory.createModelForGraph(graph))
                                          .build();
        FKLib.sendFiles(producerProps(), TOPIC, List.of("src/main/files/abac/data-no-labels.trig"));
        server.start();
        try {
            // Then
            String URL = "http://localhost:" + server.getHttpPort() + "/ds";
            RowSet rowSet = QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
            int count = ((Number) rowSet.next().get("C").getLiteralValue()).intValue();
            Assertions.assertEquals(6, count);
            HttpOp.httpPost(URL + "/update", WebContent.contentTypeSPARQLUpdate, sparqlUpdate);
            RowSet rowSet2 = QueryExecHTTP.service(URL).query("SELECT (count(*) AS ?C) {?s ?p ?o}").select();
            int count2 = ((Number) rowSet2.next().get("C").getLiteralValue()).intValue();
            Assertions.assertEquals(8, count2);

            // And
            FKLib.sendFiles(producerProps(), TOPIC, List.of("src/test/files/rdf/invalid.ttl"));
            KafkaEventSource<Bytes, Bytes> dlqSource = KafkaEventSource.<Bytes, Bytes>create()
                                                                       .bootstrapServers(
                                                                               kafka.getBootstrapServers())
                                                                       .topic(TOPIC + ".dlq")
                                                                       .consumerGroup("dlq-test")
                                                                       .keyDeserializer(BytesDeserializer.class)
                                                                       .valueDeserializer(BytesDeserializer.class)
                                                                       .build();
            Event<Bytes, Bytes> dlqEvent = dlqSource.poll(Duration.ofSeconds(5));
            Assertions.assertNotNull(dlqEvent);
            Assertions.assertFalse(StringUtil.isBlank(dlqEvent.lastHeader(TelicentHeaders.DEAD_LETTER_REASON)));

            dlqSource.close();
        } finally {
            server.stop();
        }
    }

    private Graph configuration(String filename, String bootstrapServers) {
        Graph graph = RDFParser.source(filename).toGraph();
        List<Triple> triplesBootstrapServers = G.find(graph,
                                                      null,
                                                      KafkaConnectorAssembler.pKafkaBootstrapServers,
                                                      null).toList();
        triplesBootstrapServers.forEach(t -> {
            graph.delete(t);
            graph.add(t.getSubject(), t.getPredicate(), NodeFactory.createLiteralString(bootstrapServers));
        });

        FileOps.ensureDir("target/state");
        List<Triple> triplesStateFile = G.find(graph, null, KafkaConnectorAssembler.pStateFile, null).toList();
        triplesStateFile.forEach(t -> {
            graph.delete(t);
            String fn = t.getObject().getLiteralLexicalForm();
            graph.add(t.getSubject(), t.getPredicate(), NodeFactory.createLiteralString(STATE_DIR + "/" + fn));
        });

        return graph;
    }


}
