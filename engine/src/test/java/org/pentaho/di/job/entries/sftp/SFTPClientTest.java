/*! ******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2018 by Hitachi Vantara : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.pentaho.di.job.entries.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mockito;
import org.pentaho.di.junit.rules.RestorePDIEngineEnvironment;

import java.net.InetAddress;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SFTPClientTest {
  @ClassRule public static RestorePDIEngineEnvironment env = new RestorePDIEngineEnvironment();
  private int serverPort;
  private String userName;
  private String password;
  private Session session;
  private ChannelSftp channel;
  private InetAddress server;
  private JSch jSch;

  @Before
  public void setUp() throws JSchException {
    System.clearProperty( SFTPClient.ENV_PARAM_USERAUTH_GSSAPI );

    String serverIp = "serverIp";
    serverPort = 1;
    userName = "userName";
    password = "password";
    session = mock( Session.class );
    server = mock( InetAddress.class );
    channel = mock( ChannelSftp.class );
    when( server.getHostAddress() ).thenReturn( serverIp );
    jSch = mock( JSch.class );
    when( jSch.getSession( userName, serverIp, serverPort ) ).thenReturn( session );


  }

  @After
  public void tearDown() {
    System.clearProperty( SFTPClient.ENV_PARAM_USERAUTH_GSSAPI );
  }

  /**
   * Given SFTP connection configuration, and -Duserauth.gssapi.enabled param was NOT passed on application start.
   * <br/>
   * When SFTP Client is instantiated, then preferred authentications list should not contain
   * GSS API Authentication.
   */
  @Test
  public void shouldExcludeGssapiFromPreferredAuthenticationsByDefault() throws Exception {
    new SFTPClient( server, serverPort, userName ) {
      @Override
      JSch createJSch() {
        return jSch;
      }
    };

    verify( session )
      .setConfig( "PreferredAuthentications", "publickey,keyboard-interactive,password" );
  }

  /**
   * Given SFTP connection configuration, and -Duserauth.gssapi.enabled param
   * was passed on application start with correct value.
   * <br/>
   * When SFTP Client is instantiated, then preferred authentications list should contain
   * GSS API Authentication as the last one.
   */
  @Test
  public void shouldIncludeGssapiToPreferredAuthenticationsIfSpecified() throws Exception {
    System.setProperty( SFTPClient.ENV_PARAM_USERAUTH_GSSAPI, "true" );

    new SFTPClient( server, serverPort, userName ) {
      @Override
      JSch createJSch() {
        return jSch;
      }
    };

    verify( session )
      .setConfig( "PreferredAuthentications", "publickey,keyboard-interactive,password,gssapi-with-mic" );
  }

  /**
   * Given SFTP connection configuration, and -Duserauth.gssapi.enabled param
   * was passed on application start with incorrect value.
   * <br/>
   * When SFTP Client is instantiated, then preferred authentications list should not contain
   * GSS API Authentication.
   */
  @Test
  public void shouldIncludeGssapiToPreferredAuthenticationsIfOnlySpecifiedCorrectly() throws Exception {
    System.setProperty( SFTPClient.ENV_PARAM_USERAUTH_GSSAPI, "yes" );

    new SFTPClient( server, serverPort, userName ) {
      @Override
      JSch createJSch() {
        return jSch;
      }
    };

    verify( session )
      .setConfig( "PreferredAuthentications", "publickey,keyboard-interactive,password" );
  }

  private SFTPClient setupFolderCreationTest() throws Exception {
    when( session.openChannel( "sftp" ) ).thenReturn( channel );

    System.setProperty( SFTPClient.ENV_PARAM_USERAUTH_GSSAPI, "yes" );
    SFTPClient client = new SFTPClient( server, serverPort, userName ) {
      @Override
      JSch createJSch() {
        return jSch;
      }
    };

    client.login( password );
    return client;
  }

  @Test
  public void folderCreationEmptyTest() throws Exception {
    SFTPClient client = setupFolderCreationTest();
    Mockito.doNothing().when( channel ).cd( anyString() );

    client.createFolder( "//" );
    verify( channel, times( 0 ) ).cd( anyString() );
  }

  @Test
  public void folderCreationSimpleTest() throws Exception {
    SFTPClient client = setupFolderCreationTest();

    Mockito.doThrow( new SftpException( 1, "Exception" ) ).doNothing().when( channel ).cd( anyString() );
    Mockito.doNothing().when( channel ).mkdir( anyString() );

    client.createFolder( "/test1/" );
    verify( channel, times( 2 ) ).cd( anyString() );
    verify( channel, times( 1 ) ).mkdir( anyString() );
  }

  @Test
  public void folderCreationComplexTest() throws Exception {
    SFTPClient client = setupFolderCreationTest();
    Mockito.doNothing().when( channel ).cd( anyString() );

    client.createFolder( "/test1/test2\\test3\\\\test4/" );
    verify( channel, times( 4 ) ).cd( anyString() );
  }
}
