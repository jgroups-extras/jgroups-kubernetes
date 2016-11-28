/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.jgroups.ping.common;

import static org.jgroups.ping.common.Utils.getSystemEnvInt;
import static org.jgroups.ping.common.Utils.trimToNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jgroups.Event;
import org.jgroups.Message;
import org.jgroups.annotations.Property;
import org.jgroups.ping.common.server.Server;
import org.jgroups.ping.common.server.ServerFactory;
import org.jgroups.ping.common.server.Servers;
import org.jgroups.protocols.PING;
import org.openshift.ping.common.compatibility.CompatibilityException;
import org.openshift.ping.common.compatibility.CompatibilityUtils;

public abstract class OpenshiftPing extends PING {

    private String clusterName;

    private final String _systemEnvPrefix;

    @Property
    private int connectTimeout = 5000;
    private int _connectTimeout;

    @Property
    private int readTimeout = 30000;
    private int _readTimeout;

    @Property
    private int operationAttempts = 3;
    private int _operationAttempts;

    @Property
    private long operationSleep = 1000;
    private long _operationSleep;

    private ServerFactory _serverFactory;
    private Server _server;
    private String _serverName;

    private static Method sendMethod; //handled via reflection due to JGroups 3/4 incompatibility

    public OpenshiftPing(String systemEnvPrefix) {
        super();
        _systemEnvPrefix = trimToNull(systemEnvPrefix);
        try {
            if(CompatibilityUtils.isJGroups4()) {
                sendMethod = this.getClass().getMethod("up", Message.class);
            } else {
                sendMethod = this.getClass().getMethod("up", Event.class);
            }
        } catch (Exception e) {
            throw new CompatibilityException("Could not find suitable 'up' method.", e);
        }
    }

    protected final String getSystemEnvName(String systemEnvSuffix) {
        StringBuilder sb = new StringBuilder();
        String suffix = trimToNull(systemEnvSuffix);
        if (suffix != null) {
            if (_systemEnvPrefix != null) {
                sb.append(_systemEnvPrefix);
            }
            sb.append(suffix);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    protected final int getConnectTimeout() {
        return _connectTimeout;
    }

    protected final int getReadTimeout() {
        return _readTimeout;
    }

    protected final int getOperationAttempts() {
        return _operationAttempts;
    }

    protected final long getOperationSleep() {
        return _operationSleep;
    }

    protected abstract boolean isClusteringEnabled();

    protected abstract int getServerPort();

    public final void setServerFactory(ServerFactory serverFactory) {
        _serverFactory = serverFactory;
    }

    @Override
    public void init() throws Exception {
        super.init();
        _connectTimeout = getSystemEnvInt(getSystemEnvName("CONNECT_TIMEOUT"), connectTimeout);
        _readTimeout = getSystemEnvInt(getSystemEnvName("READ_TIMEOUT"), readTimeout);
        _operationAttempts = getSystemEnvInt(getSystemEnvName("OPERATION_ATTEMPTS"), operationAttempts);
        _operationSleep = (long) getSystemEnvInt(getSystemEnvName("OPERATION_SLEEP"), (int) operationSleep);
    }

    @Override
    public void destroy() {
        _connectTimeout = 0;
        _readTimeout = 0;
        _operationAttempts = 0;
        _operationSleep = 0l;
        super.destroy();
    }

    @Override
    public void start() throws Exception {
        if (isClusteringEnabled()) {
            int serverPort = getServerPort();
            if (_serverFactory != null) {
                _server = _serverFactory.getServer(serverPort);
            } else {
                _server = Servers.getServer(serverPort);
            }
            _serverName = _server.getClass().getSimpleName();
            if (log.isInfoEnabled()) {
                log.info(String.format("Starting %s on port %s for channel address: %s", _serverName, serverPort, stack
                        .getChannel().getAddress()));
            }
            boolean started = _server.start(stack.getChannel());
            if (log.isInfoEnabled()) {
                log.info(String.format("%s %s.", _serverName, started ? "started" : "reused (pre-existing)"));
            }
        }
        super.start();
    }

    @Override
    public void stop() {
        try {
            if (_server != null) {
                if (log.isInfoEnabled()) {
                    log.info(String.format("Stopping server: %s", _serverName));
                }
                boolean stopped = _server.stop(stack.getChannel());
                if (log.isInfoEnabled()) {
                    log.info(String.format("%s %s.", _serverName, stopped ? "stopped" : "not stopped (still in use)"));
                }
            }
        } finally {
            super.stop();
        }
    }

    public Object down(Event evt) {
        switch (evt.getType()) {
        case Event.CONNECT:
        case Event.CONNECT_WITH_STATE_TRANSFER:
        case Event.CONNECT_USE_FLUSH:
        case Event.CONNECT_WITH_STATE_TRANSFER_USE_FLUSH:
            clusterName = (String) evt.getArg();
            break;
        }
        return super.down(evt);
    }

    @Override
    protected void sendMcastDiscoveryRequest(Message msg) {
        List<InetSocketAddress> nodes = readAll();
        if (nodes == null) {
            return;
        }
        if (msg.getSrc() == null) {
            msg.setSrc(local_addr);
        }
        for (InetSocketAddress node : nodes) {
            // forward the request to each node
            timer.execute(new SendDiscoveryRequest(node, msg));
        }
    }

    public void handlePingRequest(InputStream stream) throws Exception {
        DataInputStream dataInput = new DataInputStream(stream);
        Message msg = new Message();
        msg.readFrom(dataInput);
        try {
            sendUp(msg);
        } catch (Exception e) {
            log.error("Error processing GET_MBRS_REQ.", e);
        }
    }

    private void sendUp(Message msg) {
        try {
            if(CompatibilityUtils.isJGroups4()) {
                sendMethod.invoke(this, msg);
            } else {
                sendMethod.invoke(this, new Event(1, msg));
            }
        } catch (Exception e) {
            throw new CompatibilityException("Could not invoke 'up' method.", e);
        }
    }

    private List<InetSocketAddress> readAll() {
        if (isClusteringEnabled()) {
            return doReadAll(clusterName);
        } else {
            return Collections.emptyList();
        }
    }

    protected abstract List<InetSocketAddress> doReadAll(String clusterName);

    private final class SendDiscoveryRequest implements Runnable {
        private final InetSocketAddress node;
        private final Message msg;
        private int attempts;

        private SendDiscoveryRequest(InetSocketAddress node, Message msg) {
            this.node = node;
            this.msg = msg;
        }

        @Override
        public void run() {
            ++attempts;
            final String url = String.format("http://%s:%s", node.getHostString(), node.getPort());
            if (log.isTraceEnabled()) {
                log.trace(String.format(
                        "%s opening connection: url [%s], clusterName [%s], connectTimeout [%s], readTimeout [%s]",
                        getClass().getSimpleName(), url, clusterName, _connectTimeout, _readTimeout));
            }
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.addRequestProperty(Server.CLUSTER_NAME, clusterName);
                if (_connectTimeout < 0 || _readTimeout < 0) {
                    throw new IllegalArgumentException(String.format(
                            "Neither connectTimeout [%s] nor readTimeout [%s] can be less than 0 for URLConnection.",
                            _connectTimeout, _readTimeout));
                }
                connection.setConnectTimeout(_connectTimeout);
                connection.setReadTimeout(_readTimeout);
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                msg.writeTo(out);
                out.flush();
                String responseMessage = connection.getResponseMessage();
                if (log.isTraceEnabled()) {
                    log.trace(String.format(
                            "%s received response from server: url [%s], clusterName [%s], response [%s]", getClass()
                                    .getSimpleName(), url, clusterName, responseMessage));
                }
            } catch (Exception e) {
                log.warn(String.format("Error sending ping request: url [%s], clusterName [%s], attempts[%d]: %s", url,
                        clusterName, attempts, e.getLocalizedMessage()));
                if (attempts < _operationAttempts) {
                    timer.schedule(this, _operationSleep, TimeUnit.MILLISECONDS);
                }
            } finally {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                }
            }
        }

    }

}
