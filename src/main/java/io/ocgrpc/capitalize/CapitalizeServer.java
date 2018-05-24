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

public class CapitalizeServer {
    private final int serverPort;
    private Server server;

    public CapitalizeServer(int serverPort) {
        this.serverPort = serverPort;
    }

    static class FetchImpl extends FetchGrpc.FetchImplBase {
        @Override
        public void capitalize(Payload req, StreamObserver<Payload> responseObserver) {
            try {
                String capitalized = req.getData().toString("UTF8").toUpperCase();
                ByteString bs = ByteString.copyFrom(capitalized.getBytes("UTF8"));
                Payload resp = Payload.newBuilder().setData(bs).build();
                responseObserver.onNext(resp);
            } catch(UnsupportedEncodingException e) {
            } finally {
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
}
