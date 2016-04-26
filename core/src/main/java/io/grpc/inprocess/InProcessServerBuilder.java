/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.inprocess;

import com.google.common.base.Preconditions;

import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ExperimentalApi;
import io.grpc.HandlerRegistry;
import io.grpc.internal.AbstractServerImplBuilder;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.ServerImpl;
import io.grpc.internal.SharedResourceHolder;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * Builder for a server that services in-process requests. Clients identify the in-process server by
 * its name.
 *
 * <p>The server is intended to be fully-featured, high performance, and useful in testing.
 */
@ExperimentalApi("There is no plan to make this API stable.")
public final class InProcessServerBuilder
        extends AbstractServerImplBuilder<InProcessServerBuilder> {
  /**
   * Create a server builder that will bind with the given name.
   *
   * @param name the identity of the server for clients to connect to
   * @param registry the registry of handlers used for dispatching incoming calls
   * @return a new builder
   */
  public static InProcessServerBuilder forName(String name, HandlerRegistry registry) {
    return new InProcessServerBuilder(name, registry);
  }

  /**
   * Create a server builder that will bind with the given name.
   *
   * @param name the identity of the server for clients to connect to
   * @return a new builder
   */
  public static InProcessServerBuilder forName(String name) {
    return new InProcessServerBuilder(name);
  }

  /**
   * Builds an anonymous (e.g. unregistered) in-process server that exposes a given underlying
   * server. The returned in-process server will have already been started.
   *
   * @param server the server that the in-process server is exposing
   * @return an in-process form of the given server
   */
  static InProcessServer anonymous(ServerImpl server) {
    InProcessServerBuilder builder = new InProcessServerBuilder(server.handlerRegistry());
    Executor executor = server.executor();
    if (executor != SharedResourceHolder.get(GrpcUtil.SHARED_CHANNEL_EXECUTOR)) {
      if (executor == MoreExecutors.directExecutor()) {
        builder.directExecutor();
      } else {
        builder.executor(executor);
      }
    }
    ServerImpl inProcessServer = builder.compressorRegistry(server.compressorRegistry())
        .decompressorRegistry(server.decompressorRegistry())
        .build();
    // Since it's anonymous and in-process, starting the server doesn't actually do anything or
    // consume resources. Similarly, failing to subsequently shutdown the server would not leak
    // resources or memory. So this is safe. The server is not actually usable without being
    // started anyway.
    try {
      inProcessServer.start();
    } catch (Exception e) {
      throw new RuntimeException("Failed to start in-process server", e);
    }
    return (InProcessServer) inProcessServer.internalServer();
  }

  private final String name;

  private InProcessServerBuilder(String name, HandlerRegistry registry) {
    super(registry);
    this.name = Preconditions.checkNotNull(name, "name");
  }

  private InProcessServerBuilder(String name) {
    this.name = Preconditions.checkNotNull(name, "name");
  }

  private InProcessServerBuilder(HandlerRegistry registry) {
    super(registry);
    // used only for anonymous in-process servers
    this.name = null;
  }

  @Override
  protected InProcessServer buildTransportServer() {
    return new InProcessServer(name);
  }

  @Override
  public InProcessServerBuilder useTransportSecurity(File certChain, File privateKey) {
    throw new UnsupportedOperationException("TLS not supported in InProcessServer");
  }
}
