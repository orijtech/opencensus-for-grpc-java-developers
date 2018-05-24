// Copyright 2018, OpenCensus Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.ocgrpc.capitalize;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import io.ocgrpc.capitalize.Defs.Payload;
import io.ocgrpc.capitalize.FetchGrpc;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.InterruptedException;

import io.opencensus.common.Duration;
import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.exporter.stats.prometheus.PrometheusStatsCollector;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.prometheus.client.exporter.HTTPServer;

public class CapitalizeServer {
    private final int serverPort;
    private Server server;
    private static final Tracer tracer = Tracing.getTracer();

    public CapitalizeServer(int serverPort) {
        this.serverPort = serverPort;
    }

    static class FetchImpl extends FetchGrpc.FetchImplBase {
        @Override
        public void capitalize(Payload req, StreamObserver<Payload> responseObserver) {
            // For compatibility with earlier than Java 8, instead of Scoped spans
            // we'll use the finally construct to end spans.
            Span span = CapitalizeServer.tracer.spanBuilder("(*FetchImpl).capitalize").setRecordEvents(true).startSpan();
            
            try {
                String capitalized = req.getData().toString("UTF8").toUpperCase();
                ByteString bs = ByteString.copyFrom(capitalized.getBytes("UTF8"));
                Payload resp = Payload.newBuilder().setData(bs).build();
                responseObserver.onNext(resp);
            } catch(UnsupportedEncodingException e) {
            } finally {
                span.end();
                responseObserver.onCompleted();
            }
        }
    }

    public void listenAndServe() throws IOException, InterruptedException {
        this.start();
        this.server.awaitTermination();
    }

    private void start() throws IOException {
        this.server = ServerBuilder.forPort(this.serverPort).addService(new FetchImpl()).build().start();
        System.out.println("Server listening on " + this.serverPort);

        Server theServer = this.server;
        Runtime.getRuntime()
            .addShutdownHook(
                    new Thread() {
                        public void run() {
                            theServer.shutdown();
                        }
                    });
    }

    public static void main(String []args) {
        int port = 9876;

        CapitalizeServer csrv = new CapitalizeServer(port);

        // Next step is to setup OpenCensus and its exporters
        try {
            setupOpenCensusAndExporters();
        } catch (IOException e) {
            System.err.println("Failed to setup OpenCensus exporters: " + e + " so proceeding without it");
        }

        try {
            csrv.listenAndServe();
        } catch (IOException e) {
            System.err.println("Exception encountered while serving: " + e);
        } catch(InterruptedException e) {
            System.out.println("Caught an interrupt: " + e);
        } catch(Exception e) {
            System.err.println("Unhandled exception: " + e);
        } finally {
        }
    }

    private static void setupOpenCensusAndExporters() throws IOException {
        // Change the sampling rate
        TraceConfig traceConfig = Tracing.getTraceConfig();
        traceConfig.updateActiveTraceParams(
            traceConfig.getActiveTraceParams().toBuilder().setSampler(Samplers.alwaysSample()).build());

        // Register all the gRPC views and enable stats
        RpcViews.registerAllViews();

        String gcpProjectId = System.getenv().get("OCGRPC_GCP_PROJECTID");
        if (gcpProjectId == "") {
            gcpProjectId = "census-demos";
        }

        // Create the Stackdriver stats exporter
        StackdriverStatsExporter.createAndRegister(
            StackdriverStatsConfiguration.builder()
            .setProjectId(gcpProjectId)
            .setExportInterval(Duration.create(10, 0))
            .build());

        // Next create the Stackdriver tracing exporter
        StackdriverTraceExporter.createAndRegister(
            StackdriverTraceConfiguration.builder()
            .setProjectId(gcpProjectId)
            .build());

        if (false) {
            // And then the Prometheus exporter too
            PrometheusStatsCollector.createAndRegister();
            // Start the Prometheus server
            HTTPServer prometheusServer = new HTTPServer(9821, true);
        }

        // Create the Jaeger exporter
        JaegerTraceExporter.createAndRegister("http://localhost:14268/api/traces", "java_capitalize");
    }
}
