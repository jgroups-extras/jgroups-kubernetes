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

package org.openshift.ping.common;

import static org.openshift.ping.common.Utils.getSystemEnvInt;
import static org.openshift.ping.common.Utils.openStream;
import static org.openshift.ping.common.Utils.trimToNull;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jgroups.Address;
import org.jgroups.annotations.Property;
import org.jgroups.protocols.FILE_PING;
import org.jgroups.protocols.PingData;
import org.openshift.ping.common.server.AbstractServer;
import org.openshift.ping.common.server.Server;
import org.openshift.ping.common.server.ServerFactory;
import org.openshift.ping.common.server.Servers;

public abstract class OpenshiftPing extends FILE_PING {

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

    public OpenshiftPing(String systemEnvPrefix) {
        super();
        _systemEnvPrefix = trimToNull(systemEnvPrefix);
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
        _operationSleep = (long)getSystemEnvInt(getSystemEnvName("OPERATION_SLEEP"), (int)operationSleep);
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
                log.info(String.format("Starting %s on port %s for channel address: %s", _serverName, serverPort, stack.getChannel().getAddress()));
            }
            boolean started = _server.start(stack.getChannel());
            if (log.isInfoEnabled()) {
                log.info(String.format("%s %s.", _serverName, started ? "started" : "reused (pre-existing)"));
            }
        }
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

    @Override
    protected final List<PingData> readAll(String clusterName) {
        if (isClusteringEnabled()) {
            return doReadAll(clusterName);
        } else {
            PingData pingData = AbstractServer.createPingData(stack.getChannel());
            return Collections.<PingData>singletonList(pingData);
        }
    }

    protected abstract List<PingData> doReadAll(String clusterName);

    protected final PingData getPingData(String targetHost, int targetPort, String clusterName) throws Exception {
        String pingUrl = String.format("http://%s:%s", targetHost, targetPort);
        PingData pingData = new PingData();
        Map<String, String> pingHeaders = Collections.singletonMap(Server.CLUSTER_NAME, clusterName);
        try (InputStream pingStream = openStream(pingUrl, pingHeaders, _connectTimeout, _readTimeout, _operationAttempts, _operationSleep)) {
            pingData.readFrom(new DataInputStream(pingStream));
        }
        return pingData;
    }

    @Override
    protected final void createRootDir() {
        // empty on purpose to prevent dir from being created in the local file system
    }

    @Override
    protected final void writeToFile(PingData data, String clustername) {
        // prevent writing to file in jgroups 3.2.x
    }

    protected final void write(List<PingData> list, String clustername) {
        // prevent writing to file in jgroups 3.6.x
    }

    @Override
    protected final void remove(String clustername, Address addr) {
        // empty on purpose
    }

}
