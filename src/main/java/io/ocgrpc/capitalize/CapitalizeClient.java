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

import com.google.protobuf.ByteString;

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

        try {
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print("> ");
                System.out.flush();
                String in = stdin.readLine();
                String out = cc.capitalize(in);
                System.out.println("\n< " + out.toString());
            }
        } catch (Exception e) {
            System.err.println("Encountered exception: " + e);
        }
    }
}
