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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import io.ocgrpc.capitalize.Defs.Payload;
import io.ocgrpc.capitalize.FetchGrpc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.TimeUnit;

import java.io.IOException;
import com.google.protobuf.ByteString;

import io.opencensus.contrib.grpc.metrics.RpcViews;
import io.opencensus.common.Duration;
import io.opencensus.trace.config.TraceConfig;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracing;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;

public class CapitalizeClient {
    private final ManagedChannel channel;
    private final FetchGrpc.FetchBlockingStub stub;

    public CapitalizeClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext(true)
            .build();
        this.stub = FetchGrpc.newBlockingStub(this.channel);
    }

    public void shutdown() throws InterruptedException {
        this.channel.shutdown().awaitTermination(4, TimeUnit.SECONDS);
    }

    public String capitalize(String data) {
        Payload out;

        try {
            ByteString bs = ByteString.copyFrom(data.getBytes("UTF8"));
            Payload in = Payload.newBuilder().setData(bs).build();
            out = this.stub.capitalize(in);
            return out.getData().toString("UTF8");
        } catch(UnsupportedEncodingException e) {
            return "";
        } catch (StatusRuntimeException e) {
            return "";
        }
    }

    public static void main(String []args) {
        CapitalizeClient cc = new CapitalizeClient("0.0.0.0", 9876);

        // Next step is to setup OpenCensus and its exporters
        try {
            setupOpenCensusAndExporters();
        } catch (IOException e) {
            System.err.println("Failed to setup OpenCensus exporters: " + e + " so proceeding without it");
        }

        try {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print("> ");
                System.out.flush();
                String in = stdin.readLine();

                String out = cc.capitalize(in);
                System.out.println("< " + out.toString() + "\n");
            }
        } catch (Exception e) {
            System.err.println("Encountered exception: " + e);
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
            .setExportInterval(Duration.create(60, 0))
            .build());

        // Next create the Stackdriver tracing exporter
        StackdriverTraceExporter.createAndRegister(
            StackdriverTraceConfiguration.builder()
            .setProjectId(gcpProjectId)
            .build());
    }
}
