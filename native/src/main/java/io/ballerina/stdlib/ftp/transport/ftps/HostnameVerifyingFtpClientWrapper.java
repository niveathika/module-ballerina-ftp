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

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticationData;
import org.apache.commons.vfs2.provider.GenericFileName;
import org.apache.commons.vfs2.provider.ftp.FTPClientWrapper;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.util.UserAuthenticatorUtils;

/**
 * Stand-in for vfs2's package-private {@code FtpsClientWrapper}. Builds an {@code FTPSClient}
 * via {@link HostnameVerifyingFtpsConnectionFactory} so endpoint checking is enabled before
 * the TLS handshake.
 */
final class HostnameVerifyingFtpClientWrapper extends FTPClientWrapper {

    HostnameVerifyingFtpClientWrapper(GenericFileName root, FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        super(root, fileSystemOptions);
    }

    @Override
    protected FTPClient createClient(GenericFileName rootName, UserAuthenticationData authData)
            throws FileSystemException {
        HostnameVerifyingFtpsConnectionFactory factory = new HostnameVerifyingFtpsConnectionFactory(
                FtpsFileSystemConfigBuilder.getInstance());
        return factory.createConnection(
                rootName.getHostName(), rootName.getPort(),
                UserAuthenticatorUtils.getData(authData, UserAuthenticationData.USERNAME,
                        UserAuthenticatorUtils.toChar(rootName.getUserName())),
                UserAuthenticatorUtils.getData(authData, UserAuthenticationData.PASSWORD,
                        UserAuthenticatorUtils.toChar(rootName.getPassword())),
                rootName.getPath(), getFileSystemOptions());
    }
}
