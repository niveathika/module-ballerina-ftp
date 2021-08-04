// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/lang.runtime as runtime;
import ballerina/log;
import ballerina/test;

int addedFileCount = 0;
int deletedFileCount = 0;
boolean watchEventReceived = false;

listener Listener remoteServer = new({
    protocol: FTP,
    host: "127.0.0.1",
    auth: {
        credentials: {
            username: "wso2",
            password: "wso2123"
        }
    },
    port: 21212,
    path: "/home/in",
    pollingInterval: 2,
    fileNamePattern: "(.*).txt"
});

service "ftpServerConnector" on remoteServer {
    function onFileChange(WatchEvent event) {
        addedFileCount = event.addedFiles.length();
        deletedFileCount = event.deletedFiles.length();
        watchEventReceived = true;

        foreach FileInfo addedFile in event.addedFiles {
            log:printInfo("Added file path: " + addedFile.path);
        }
        foreach string deletedFile in event.deletedFiles {
            log:printInfo("Deleted file path: " + deletedFile);
        }
    }
}

@test:Config{
}
public function testAddedFileCount() {
    int timeoutInSeconds = 300;
    // Test fails in 5 minutes if failed to receive watchEvent
    while timeoutInSeconds > 0 {
        if watchEventReceived {
            log:printInfo("Added file count: " + addedFileCount.toString());
            test:assertEquals(3, addedFileCount);
            break;
        } else {
            runtime:sleep(1);
            timeoutInSeconds = timeoutInSeconds - 1;
        }
    }
    if timeoutInSeconds == 0 {
        test:assertFail("Failed to receive WatchEvent for 5 minuetes.");
    }
}

listener Listener detachFtpServer = new({
    protocol: FTP,
    host: "127.0.0.1",
    auth: {
        credentials: {
            username: "wso2",
            password: "wso2123"
        }
    },
    port: 21212,
    path: "/home/in",
    pollingInterval: 2,
    fileNamePattern: "(.*).txt"
});

@test:Config {}
public function testFtpServerDeregistration() {
    service object {} detachService = service object { function onFileChange(WatchEvent event) {} };
    error? result1 = detachFtpServer.attach(detachService, "remote-server");
    if result1 is error {
        test:assertFail("Failed to attach to the FTP server: " + result1.message());
    } else {
        error? result2 = detachFtpServer.detach(detachService);
        if result2 is error {
            test:assertFail("Failed to detach from the FTP server: " + result2.message());
        }
    }
}

listener Listener emptyPasswordServer = new({
    protocol: FTP,
    host: "127.0.0.1",
    auth: {
        credentials: {
            username: "wso2",
            password: ""
        }
    },
    port: 21212,
    path: "/home/in",
    pollingInterval: 2,
    fileNamePattern: "(.*).txt"
});

@test:Config {}
public function testServerRegisterFailureEmptyPassword() {
    error? result = emptyPasswordServer.attach(service object {
          function onFileChange(WatchEvent event) {}
    }, "remote-server");
    if result is error {
        test:assertEquals(result.message(), "Failed to initialize File server connector for Service: remote-server");
    } else {
        test:assertFail("Empty password set to basic auth. Test should fail");
    }
}

listener Listener emptyUsernameServer = new({
    protocol: FTP,
    host: "127.0.0.1",
    auth: {
        credentials: {
            username: "",
            password: "wso2"
        }
    },
    port: 21212,
    path: "/home/in",
    pollingInterval: 2,
    fileNamePattern: "(.*).txt"
});

@test:Config {}
public function testServerRegisterFailureEmptyUsername() {
    error? result = emptyUsernameServer.attach(service object {
          function onFileChange(WatchEvent event) {}
    }, "remote-server");
    if result is error {
        test:assertEquals(result.message(), "Username cannot be empty");
    } else {
        test:assertFail("Empty username set to basic auth. Test should fail");
    }
}