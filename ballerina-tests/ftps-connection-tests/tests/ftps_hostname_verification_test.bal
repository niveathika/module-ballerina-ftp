// Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/ftp;
import ballerina/test;
import ballerina_tests/ftp_test_commons as commons;

// Hostname verification regression tests for wso2/product-integrator#829.
//
// Three coordinated tests form a control set:
//   1. testFtpsHostnameVerificationMatchingCert — verifyHostName:true vs the
//      standard FTPS server (matching cert). Proves the happy path still works.
//   2. testFtpsHostnameVerificationMismatch — verifyHostName:true vs the
//      mismatched-cert server on FTPS_MISMATCHED_PORT. Must fail at TLS
//      handshake with a hostname-related error.
//   3. testFtpsHostnameVerificationDisabled — verifyHostName:false vs the same
//      mismatched server. Must succeed (proves the server is reachable, the
//      mismatched cert loads, and the opt-out works).
//
// If (2) fails on a non-hostname error while (1) and (3) pass, infrastructure
// is fine and the failure is genuinely about hostname verification.

// Asserts an Error originated from TLS hostname/cert verification rather than
// unrelated failure (server down, bad creds). Matches the canonical JSSE
// hostname-mismatch phrases (stable across JDK 17–21+) plus broad TLS keywords
// as a safety net.
function assertHostnameVerificationFailure(ftp:Error err) {
    string message = err.message().toLowerAscii();
    boolean looksLikeTlsFailure = message.includes("subject alternative")
        || message.includes("no name matching")
        || message.includes("doesn't match")
        || message.includes("does not match")
        || message.includes("certificate")
        || message.includes("hostname")
        || message.includes("handshake")
        || message.includes("ssl")
        || message.includes("tls");
    test:assertTrue(looksLikeTlsFailure,
            string `Expected TLS/hostname verification failure, got: ${err.message()}`);
}

// Happy path: matching-CN/SAN server on FTPS_EXPLICIT_PORT must work with
// verifyHostName:true (the new default).
@test:Config {
    groups: ["ftps-connection", "hostname"]
}
function testFtpsHostnameVerificationMatchingCert() returns error? {
    ftp:Client ftpsClient = check new ({
        protocol: ftp:FTPS,
        host: commons:FTP_HOST,
        port: commons:FTPS_EXPLICIT_PORT,
        auth: {
            credentials: {username: commons:FTP_USERNAME, password: commons:FTP_PASSWORD},
            secureSocket: {
                cert: {path: commons:KEYSTORE_PATH, password: "changeit"},
                mode: ftp:EXPLICIT
            }
        }
    });
    boolean isDir = check ftpsClient->isDirectory("/");
    test:assertTrue(isDir, "isDirectory('/') should succeed against FTPS server with matching cert.");
    check ftpsClient->close();
}

// Mismatch path: cert presents CN=other.example.com against host 127.0.0.1.
// With verifyHostName:true (default), this must fail at the TLS handshake.
@test:Config {
    groups: ["ftps-connection", "hostname"]
}
function testFtpsHostnameVerificationMismatch() returns error? {
    ftp:ClientConfiguration mismatchedConfig = {
        protocol: ftp:FTPS,
        host: commons:FTP_HOST,
        port: commons:FTPS_MISMATCHED_PORT,
        auth: {
            credentials: {username: commons:FTP_USERNAME, password: commons:FTP_PASSWORD},
            secureSocket: {
                cert: {path: commons:MISMATCHED_KEYSTORE_PATH, password: "changeit"},
                mode: ftp:EXPLICIT
            }
        }
    };

    ftp:Client|ftp:Error ftpsClient = new (mismatchedConfig);
    if ftpsClient is ftp:Error {
        assertHostnameVerificationFailure(ftpsClient);
        return;
    }
    boolean|ftp:Error result = ftpsClient->isDirectory("/");
    if result is ftp:Error {
        assertHostnameVerificationFailure(result);
        check ftpsClient->close();
        return;
    }
    check ftpsClient->close();
    test:assertFail(string `Hostname verification not enforced: cert for other.example.com was accepted for host 127.0.0.1 (isDirectory returned ${result}). See wso2/product-integrator#829.`);
}

// Opt-out path: same mismatched server, but verifyHostName:false must skip the
// hostname check and allow the connection.
@test:Config {
    groups: ["ftps-connection", "hostname"]
}
function testFtpsHostnameVerificationDisabled() returns error? {
    ftp:Client ftpsClient = check new ({
        protocol: ftp:FTPS,
        host: commons:FTP_HOST,
        port: commons:FTPS_MISMATCHED_PORT,
        auth: {
            credentials: {username: commons:FTP_USERNAME, password: commons:FTP_PASSWORD},
            secureSocket: {
                cert: {path: commons:MISMATCHED_KEYSTORE_PATH, password: "changeit"},
                mode: ftp:EXPLICIT,
                verifyHostName: false
            }
        }
    });
    boolean isDir = check ftpsClient->isDirectory("/");
    test:assertTrue(isDir, "verifyHostName:false should allow connection to mismatched-cert server.");
    check ftpsClient->close();
}
