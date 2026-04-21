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

import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemConfigBuilder;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystem;

/**
 * Stashes the {@code verifyHostname} flag on a {@link FileSystemOptions} so our
 * custom connection factory can read it when constructing the {@code FTPSClient}.
 * Uses the standard VFS {@link FileSystemConfigBuilder} parameter mechanism so the
 * value is namespaced under this class and won't collide with vfs2's own keys.
 */
public final class HostnameVerifyingFtpsConfigHelper extends FileSystemConfigBuilder {

    private static final HostnameVerifyingFtpsConfigHelper INSTANCE = new HostnameVerifyingFtpsConfigHelper();
    private static final String VERIFY_HOSTNAME = "verifyHostname";

    private HostnameVerifyingFtpsConfigHelper() {
    }

    public static HostnameVerifyingFtpsConfigHelper getInstance() {
        return INSTANCE;
    }

    public void setVerifyHostname(FileSystemOptions opts, boolean value) {
        setParam(opts, VERIFY_HOSTNAME, value);
    }

    public boolean getVerifyHostname(FileSystemOptions opts) {
        return getBoolean(opts, VERIFY_HOSTNAME, true);
    }

    @Override
    protected Class<? extends FileSystem> getConfigClass() {
        return FtpsFileSystem.class;
    }
}
