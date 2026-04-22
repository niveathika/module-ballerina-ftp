/*
 * Copyright (c) 2026 WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.ftp.transport.ftps;

import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftp.FtpClientFactory;
import org.apache.commons.vfs2.provider.ftps.FtpsDataChannelProtectionLevel;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftps.FtpsMode;

import java.io.IOException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;

/**
 * Drop-in replacement for commons-vfs2's package-private {@code FtpsConnectionFactory}.
 * Creates an {@link FTPSClient}, configures its trust/key managers, and — the reason
 * this class exists — calls {@link FTPSClient#setEndpointCheckingEnabled(boolean)}
 * before the parent {@code ConnectionFactory} orchestration triggers the TLS handshake.
 * Enabling endpoint checking delegates to JDK's RFC 6125 endpoint identification
 * ({@code SSLParameters.setEndpointIdentificationAlgorithm("HTTPS")}), matching the
 * mechanism {@code ballerina/http} uses for its own hostname verification.
 */
final class HostnameVerifyingFtpsConnectionFactory
        extends FtpClientFactory.ConnectionFactory<FTPSClient, FtpsFileSystemConfigBuilder> {

    HostnameVerifyingFtpsConnectionFactory(FtpsFileSystemConfigBuilder builder) {
        super(builder);
    }

    @Override
    protected FTPSClient createClient(FileSystemOptions fileSystemOptions) throws FileSystemException {
        FTPSClient client = new FTPSClient(builder.getFtpsMode(fileSystemOptions) == FtpsMode.IMPLICIT);

        boolean verifyHostName = HostnameVerifyingFtpsConfigHelper.getInstance().getVerifyHostName(fileSystemOptions);
        client.setEndpointCheckingEnabled(verifyHostName);

        TrustManager trustManager = builder.getTrustManager(fileSystemOptions);
        if (trustManager != null) {
            client.setTrustManager(trustManager);
        }

        KeyManager keyManager = builder.getKeyManager(fileSystemOptions);
        if (keyManager != null) {
            client.setKeyManager(keyManager);
        }
        return client;
    }

    @Override
    protected void setupOpenConnection(FTPSClient client, FileSystemOptions fileSystemOptions) throws IOException {
        FtpsDataChannelProtectionLevel level = builder.getDataChannelProtectionLevel(fileSystemOptions);
        if (level != null) {
            try {
                client.execPBSZ(0);
                client.execPROT(level.name());
            } catch (SSLException e) {
                throw new FileSystemException("vfs.provider.ftps/data-channel.level", e, level.toString());
            }
        }
    }
}
