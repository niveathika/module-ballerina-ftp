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

import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileSystem;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.DefaultFileSystemManager;
import org.apache.commons.vfs2.provider.GenericFileName;
import org.apache.commons.vfs2.provider.ftps.FtpsFileProvider;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VFS file provider registered under the {@code ftps} scheme that substitutes our
 * {@link HostnameVerifyingFtpClientWrapper} for vfs2's package-private
 * {@code FtpsClientWrapper}. Call {@link #ensureRegistered(FileSystemManager)} once
 * per manager before opening FTPS connections.
 */
public final class HostnameVerifyingFtpsFileProvider extends FtpsFileProvider {

    private static final Logger log = LoggerFactory.getLogger(HostnameVerifyingFtpsFileProvider.class);
    private static final String SCHEME_FTPS = "ftps";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    @Override
    protected FileSystem doCreateFileSystem(FileName name, FileSystemOptions fileSystemOptions)
            throws FileSystemException {
        GenericFileName rootName = (GenericFileName) name;
        HostnameVerifyingFtpClientWrapper ftpClient =
                new HostnameVerifyingFtpClientWrapper(rootName, fileSystemOptions);
        return new FtpsFileSystem(rootName, ftpClient, fileSystemOptions);
    }

    /**
     * Idempotently swaps vfs2's default {@code ftps} provider for this one on the given manager.
     * Safe to call from multiple initialization sites — the second call is a no-op.
     *
     * @param manager the VFS manager obtained from {@code VFS.getManager()}.
     * @throws FileSystemException if re-registration fails.
     */
    public static void ensureRegistered(FileSystemManager manager) throws FileSystemException {
        if (REGISTERED.get()) {
            return;
        }
        if (!(manager instanceof DefaultFileSystemManager dfsm)) {
            log.warn("VFS FileSystemManager is of unexpected type {}; FTPS hostname and trust-chain "
                    + "verification will not be installed. The permissive default provider remains active.",
                    manager.getClass().getName());
            return;
        }
        synchronized (HostnameVerifyingFtpsFileProvider.class) {
            if (REGISTERED.get()) {
                return;
            }
            if (dfsm.hasProvider(SCHEME_FTPS)) {
                dfsm.removeProvider(SCHEME_FTPS);
            }
            dfsm.addProvider(SCHEME_FTPS, new HostnameVerifyingFtpsFileProvider());
            REGISTERED.set(true);
        }
    }
}
