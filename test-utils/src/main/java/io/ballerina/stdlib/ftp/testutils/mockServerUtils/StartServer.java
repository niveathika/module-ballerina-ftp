/*
 * Copyright (c) 2023, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.ftp.testutils.mockServerUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartServer {

    private static final Logger logger = LoggerFactory.getLogger(StartServer.class);

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new Exception("Please specify the resources directory as an argument");
            }
            MockFtpServer.initAnonymousFtpServer();
            MockFtpServer.initFtpServer();
            MockFtpServer.initSftpServer(args[0]);
        } catch (Exception ex) {
            logger.error(ex.getMessage());
            System.exit(1);
        }
    }
}
