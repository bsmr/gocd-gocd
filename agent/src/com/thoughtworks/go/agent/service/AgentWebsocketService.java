/*
 * Copyright 2015 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.agent.service;

import com.thoughtworks.go.agent.AgentController;
import com.thoughtworks.go.server.websocket.Message;
import com.thoughtworks.go.util.URLService;
import org.apache.log4j.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

@Component
@WebSocket
public class AgentWebsocketService {
    private static final Logger LOGGER = Logger.getLogger(AgentWebsocketService.class);
    private AgentController controller;
    private Session session;
    private URLService urlService;
    private WebSocketClient client;

    @Autowired
    public AgentWebsocketService(URLService urlService) {
        this.urlService = urlService;
    }

    public synchronized void start() throws Exception {
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(SslInfrastructureService.AGENT_CERTIFICATE_FILE.getAbsolutePath());
        sslContextFactory.setKeyStorePassword(SslInfrastructureService.AGENT_STORE_PASSWORD);
        sslContextFactory.setKeyManagerPassword(SslInfrastructureService.AGENT_STORE_PASSWORD);
        sslContextFactory.setTrustStorePath(SslInfrastructureService.AGENT_TRUST_FILE.getAbsolutePath());
        sslContextFactory.setTrustStorePassword(SslInfrastructureService.AGENT_STORE_PASSWORD);
        sslContextFactory.setWantClientAuth(true);
        if (client == null || client.isStopped()) {
            client = new WebSocketClient(sslContextFactory);
            client.start();
        }
        if (session != null) {
            session.close();
        }
        session = client.connect(this, new URI(urlService.getAgentRemoteWebSocketUrl()), new ClientUpgradeRequest()).get();
    }

    public synchronized void stop() {
        if (isRunning()) {
            session.close();
            session = null;
        }
    }

    public synchronized boolean isRunning() {
        return session != null && session.isOpen();
    }

    public boolean send(Message message) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(sessionName() + " send message: " + message);
        }
        try {
            this.session.getRemote().sendString(Message.encode(message));
            return true;
        } catch (IOException e) {
            onError(e);
            return false;
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(sessionName() + " connected.");
        }
    }

    @OnWebSocketMessage
    public void onMessage(String raw) {
        Message msg = Message.decode(raw);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(sessionName() + " message: " + msg);
        }
        this.controller.process(msg);
    }

    @OnWebSocketClose
    public void onClose(int closeCode, String closeReason) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(sessionName() + " closed.");
        }
    }

    @OnWebSocketError
    public void onError(Throwable error) {
        LOGGER.error(sessionName() + " error", error);
    }

    private String sessionName() {
        return session == null ? "[No session initialized]" : "Session[" + session.getRemoteAddress() + "]";
    }

    public void setController(AgentController controller) {
        this.controller = controller;
    }

}
